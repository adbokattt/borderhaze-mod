#version 330

// GLITCH shell, version 2: the figure is a cloud of unstable black smoke with a realistic chromatic aberration,
// and two small eyes that peek out of the darkness.
//
// How the figure is found: the body is the vanilla player model drawn with a near-black texture whose alpha is 252/255
// (alpha is never changed by fog or lighting) and whose blue is a tiny bit above zero (fallback). Everything below is
// built from that mask.
//
//   * SMOKE BODY   the mask is averaged over a small disc and compared with animated noise: the edge becomes fuzzy, torn,
//                  made of drifting patches and tendrils (like black particles / a clump of dark smoke); it rises slowly
//                  and now and then swells into a big soft cloud
//   * ABERRATION   real lateral chromatic aberration: the red, green and blue channels see the figure at slightly
//                  different positions (red one way, blue the other, growing with the distance from the screen centre),
//                  so edges get red/yellow and cyan/blue fringes, exactly like a lens does
//   * INTERFERENCE ghost copies, sliding bands, cold static dust and scan lines inside the cloud
//   * EYES         small bright cores with a soft bloom, drawn at the projected 3D eye positions, hidden behind walls,
//                  and partly veiled by moving wisps of smoke so they look like light at the end of a dark tunnel
// The effect scales with the on-screen size of the figure, so it looks the same near and far.
// Only pixels inside the on-screen rectangles of the figures are touched.

uniform sampler2D InSampler;
uniform sampler2D InDepthSampler;

in vec2 texCoord;

// std140 block, must match GlitchUniforms.java and glitch.json (29 x vec4)
layout(std140) uniform GlitchConfig {
    vec4 Time;        // x time(s), y reverse-Z flag, z number of figures, w debug flag (1 = show the detected mask)
    vec4 Rects[8];    // minU, minV, maxU, maxV
    vec4 EyePos[16];  // u, v, depth, radius (fraction of the screen height)
    vec4 EyeInt[4];   // 16 eye brightnesses, packed
};

out vec4 fragColor;

float hash11(float p) {
    p = fract(p * 0.1031);
    p *= p + 33.33;
    p *= p + p;
    return fract(p);
}

float hash21(vec2 p) {
    vec3 p3 = fract(vec3(p.xyx) * 0.1031);
    p3 += dot(p3, p3.yzx + 33.33);
    return fract((p3.x + p3.y) * p3.z);
}

float vnoise(vec2 p) {
    vec2 i = floor(p);
    vec2 f = fract(p);
    f = f * f * (3.0 - 2.0 * f);
    float a = hash21(i);
    float b = hash21(i + vec2(1.0, 0.0));
    float c = hash21(i + vec2(0.0, 1.0));
    float d = hash21(i + vec2(1.0, 1.0));
    return mix(mix(a, b, f.x), mix(c, d, f.x), f.y);
}

float fbm(vec2 p) {
    return 0.55 * vnoise(p) + 0.30 * vnoise(p * 2.1 + 7.3) + 0.15 * vnoise(p * 4.3 + 3.1);
}

// 1 if the pixel at uv belongs to a GLITCH body. Primary marker: alpha 252/255 (fog cannot touch alpha) on a dark pixel;
// fallback marker: very dark, clearly bluish colour. Exact texel lookup.
float isFigure(vec2 uv) {
    if (uv.x < 0.0 || uv.x > 1.0 || uv.y < 0.0 || uv.y > 1.0) {
        return 0.0;
    }
    vec4 s = textureLod(InSampler, uv, 0.0);
    vec3 c = s.rgb * 255.0;
    float a = s.a * 255.0;
    bool alphaMark = abs(a - 252.0) < 0.75 && max(c.r, max(c.g, c.b)) < 72.0;
    bool colourMark = c.b >= 4.0 && c.b <= 90.0 && c.r <= 0.25 * c.b + 0.5 && c.g <= 0.25 * c.b + 0.5;
    return (alphaMark || colourMark) ? 1.0 : 0.0;
}

float gPhase = 0.0;
vec2 px = vec2(0.0);

// soft coverage of the figure around uv: the mask averaged over a small disc (radius in pixels)
float coverage(vec2 uv, float radius) {
    float sum = isFigure(uv) * 1.5;
    for (int i = 0; i < 8; i++) {
        float a = float(i) * 2.39996323 + gPhase;
        float r = sqrt((float(i) + 0.5) / 8.0) * radius;
        sum += isFigure(uv + vec2(cos(a), sin(a)) * r * px);
    }
    return sum / 9.5;
}

// the smoky body: coverage compared with noise -> fuzzy, torn edge made of patches (0 = nothing, 1 = solid black)
float smokeBody(vec2 uv, float radius, float noise, float looseness) {
    float cov = coverage(uv, radius);
    float v = cov - (0.10 + 0.62 * noise) * looseness;
    return clamp(v * 5.0 + 0.5, 0.0, 1.0);
}

void main() {
    vec4 base = texture(InSampler, texCoord);
    fragColor = vec4(base.rgb, 1.0);

    vec2 res = vec2(textureSize(InSampler, 0));
    px = 1.0 / res;
    float aspect = res.x / res.y;
    float t = Time.x;

    // ---- which figure rectangle are we in? (also gives the on-screen size of that figure) ----
    bool inRect = false;
    float figScale = 1.0;
    int n = int(Time.z + 0.5);
    for (int i = 0; i < 8; i++) {
        if (i >= n) {
            break;
        }
        vec4 r = Rects[i];
        if (texCoord.x >= r.x && texCoord.x <= r.z && texCoord.y >= r.y && texCoord.y <= r.w) {
            inRect = true;
            figScale = clamp((r.w - r.y) * res.y / 420.0, 0.28, 1.6);
            break;
        }
    }

    vec3 col = base.rgb;

    if (inRect) {
        if (Time.w > 0.5) {
            // debug view: magenta = recognised as GLITCH body
            col = mix(col, vec3(1.0, 0.0, 1.0), isFigure(texCoord) * 0.8);
            fragColor = vec4(col, 1.0);
            return;
        }

        // ---------------- how unstable is the figure right now: calm (0.28) .. violent (1.0) ----------------
        float slow = t * 1.3;
        float n0 = hash11(floor(slow));
        float n1 = hash11(floor(slow) + 1.0);
        float f = fract(slow);
        f = f * f * (3.0 - 2.0 * f);
        float intensity = 0.28 + 0.72 * smoothstep(0.5, 0.92, mix(n0, n1, f));
        intensity = max(intensity, 0.95 * step(0.93, hash11(floor(t * 6.0) + 91.0)));

        // now and then the smoke swells into a big soft cloud around the figure
        float c0 = hash11(floor(t * 0.5) + 41.0);
        float c1 = hash11(floor(t * 0.5) + 42.0);
        float cf = fract(t * 0.5);
        cf = cf * cf * (3.0 - 2.0 * cf);
        float cloud = smoothstep(0.62, 0.95, mix(c0, c1, cf));

        float tick = floor(t * 14.0);
        gPhase = tick * 1.7;

        // ---------------- smoke field: wobble, sliding bands, upward drift, animated noise ----------------
        vec2 cell = floor(texCoord * res / (6.0 * figScale + 1.0));
        vec2 jit = vec2(hash21(cell + tick), hash21(cell.yx + tick * 1.7)) - 0.5;
        vec2 wobble = jit * 2.0 * px * (1.5 + 5.0 * intensity) * figScale;
        wobble += vec2(sin(texCoord.y * 40.0 + t * 5.0), cos(texCoord.x * 30.0 + t * 4.0)) * px * (1.0 + 2.5 * intensity) * figScale;

        float band = floor(texCoord.y * res.y / (14.0 * figScale + 2.0));
        float bandRand = hash21(vec2(band, floor(t * 8.0)));
        float slide = bandRand > (0.90 - 0.20 * intensity)
                ? (hash21(vec2(band * 3.1, floor(t * 8.0))) - 0.5) * 2.0 * px.x * (8.0 + 30.0 * intensity) * figScale
                : 0.0;

        float drift = (2.0 + 6.0 * intensity + 10.0 * cloud) * figScale;           // pixels the smoke rises
        vec2 uvq = texCoord + wobble + vec2(slide, -drift * px.y);

        float noise = fbm(texCoord * res / (9.0 * figScale + 2.0) + vec2(t * 0.9, -t * 1.4));
        float radius = (3.0 + 6.0 * intensity + 20.0 * cloud) * figScale;           // pixels: how far the smoke reaches
        float looseness = 1.0 - 0.45 * cloud;                                          // clouds are denser/softer

        // ---------------- realistic lateral chromatic aberration ----------------
        float split = (1.5 + 4.5 * intensity + 3.0 * cloud) * figScale;               // pixels
        vec2 dir = normalize(vec2(1.0, 0.0) + 1.2 * (texCoord - 0.5) * vec2(aspect, 1.0));
        vec2 o = dir * split * px;

        float bodyR = smokeBody(uvq - o, radius, noise, looseness);
        float bodyG = smokeBody(uvq, radius, noise, looseness);
        float bodyB = smokeBody(uvq + o * 1.35, radius, noise, looseness);

        // the real figure pixels are always solid black in every channel
        float core = isFigure(texCoord);
        vec3 body = max(vec3(bodyR, bodyG, bodyB), vec3(core));

        // ghost copy: a faint second smoke figure that jumps around
        float gseed = hash11(floor(t * 5.0));
        vec2 goff = vec2((gseed - 0.5) * 0.09, (hash11(gseed * 7.7) - 0.5) * 0.02);
        float ghost = coverage(texCoord - goff, radius * 0.6) * step(0.45, hash11(floor(t * 3.0) + 13.0));
        col = mix(col, vec3(0.05, 0.07, 0.15), ghost * (1.0 - bodyG) * 0.30 * intensity);

        // black smoke over the scene, channel by channel (this is what makes the colour fringes)
        col *= (1.0 - body);

        // radio interference inside the cloud: cold static dust and scan lines
        float zone = bodyG * (1.0 - core);
        float dust = step(0.94 - 0.04 * intensity, hash21(floor(texCoord * res / (2.0 * figScale + 1.0)) + tick));
        float scan = step(0.975, hash21(vec2(floor(texCoord.y * res.y / (2.0 * figScale + 1.0)), tick)));
        col += vec3(0.62, 0.80, 1.0) * zone * dust * (0.30 + 0.50 * intensity);
        col += vec3(0.20, 0.30, 0.55) * zone * scan * 0.5 * intensity;
    }

    // ---------------- eyes: small, bright, peeking out of the darkness ----------------
    vec3 glow = vec3(0.0);
    for (int i = 0; i < 16; i++) {
        float level = EyeInt[i / 4][i % 4];
        if (level <= 0.001) {
            continue;
        }
        vec4 e = EyePos[i];
        vec2 dv = (texCoord - e.xy) * vec2(aspect, 1.0);
        float dist = length(dv) / max(e.w, 1.0e-5);
        if (dist > 5.0) {
            continue;
        }
        float sceneDepth = texture(InDepthSampler, clamp(e.xy, 0.0, 1.0)).r;
        bool occluded = Time.y > 0.5 ? (sceneDepth > e.z * 1.04 + 1.0e-6) : (sceneDepth < e.z * 0.96);
        if (occluded) {
            continue;
        }
        float core = smoothstep(1.0, 0.50, dist);
        float bloom = exp(-dist * dist * 0.55) * 0.45;
        glow += (vec3(1.0) * core * 1.2 + vec3(0.80, 0.90, 1.0) * bloom) * level;
    }
    if (glow.r + glow.g + glow.b > 0.0) {
        // wisps of dark smoke drift across the eyes: they shine through the darkness instead of lying on top of it
        float veil = smoothstep(0.38, 0.88, fbm(texCoord * res / 11.0 + vec2(t * 0.8, -t * 0.55))) * 0.55;
        col += glow * (1.0 - veil);
    }

    fragColor = vec4(clamp(col, 0.0, 1.0), 1.0);
}
