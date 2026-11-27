#version 330

// GLITCH shell: turns the "marker black" player model into an unstable digital figure.
//
// The body is the vanilla player model drawn with a texture that is black to the eye but has r = g = 0 and a tiny blue
// value. This shader finds those pixels on the finished picture and builds the figure from them:
//   * a ragged, constantly wobbling silhouette (the mask is looked up at jittered positions, so the black grows and tears)
//   * purple / blue / cold-cyan colour fringes (the same mask looked up at shifted positions = chromatic aberration)
//   * ghost copies, slice displacement bands, static speckle
//   * two glowing eyes, drawn around the projected 3D eye positions (core + bloom), hidden behind walls
// The look cycles between a calm state (almost the plain silhouette with a thin fringe) and violent bursts.
// Only pixels inside the on-screen rectangles of the figures are touched; everything else is returned unchanged.

uniform sampler2D InSampler;
uniform sampler2D InDepthSampler;

in vec2 texCoord;

// std140 block, must match GlitchUniforms.java and glitch.json (29 x vec4)
layout(std140) uniform GlitchConfig {
    vec4 Time;        // x time(s), y reverse-Z flag, z number of figures
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

// 1 if the pixel at uv belongs to a GLITCH body (marker colour: r = g = 0, small blue), else 0. Exact texel lookup.
float isFigure(vec2 uv) {
    if (uv.x < 0.0 || uv.x > 1.0 || uv.y < 0.0 || uv.y > 1.0) {
        return 0.0;
    }
    vec3 c = floor(textureLod(InSampler, uv, 0.0).rgb * 255.0 + 0.5);
    return (c.r < 0.5 && c.g < 0.5 && c.b > 1.5 && c.b < 70.0) ? 1.0 : 0.0;
}

bool insideAnyRect(vec2 uv) {
    int n = int(Time.z + 0.5);
    for (int i = 0; i < 8; i++) {
        if (i >= n) {
            break;
        }
        vec4 r = Rects[i];
        if (uv.x >= r.x && uv.x <= r.z && uv.y >= r.y && uv.y <= r.w) {
            return true;
        }
    }
    return false;
}

void main() {
    vec4 base = texture(InSampler, texCoord);
    fragColor = vec4(base.rgb, 1.0);

    bool inRect = insideAnyRect(texCoord);
    // eyes can be slightly outside the figure rectangle (bloom), they are handled below for every pixel near an eye
    vec2 res = vec2(textureSize(InSampler, 0));
    vec2 px = 1.0 / res;
    float aspect = res.x / res.y;
    float t = Time.x;

    vec3 col = base.rgb;

    if (inRect) {
        // ---------------- how unstable is the figure right now: calm (0.28) .. burst (1.0) ----------------
        float slow = t * 1.3;
        float n0 = hash11(floor(slow));
        float n1 = hash11(floor(slow) + 1.0);
        float f = fract(slow);
        f = f * f * (3.0 - 2.0 * f);
        float wave = mix(n0, n1, f);
        float intensity = 0.28 + 0.72 * smoothstep(0.5, 0.92, wave);
        float spike = step(0.93, hash11(floor(t * 6.0) + 91.0));
        intensity = max(intensity, 0.95 * spike);

        // ---------------- ragged, wobbling silhouette ----------------
        float tick = floor(t * 18.0);
        vec2 cell = floor(texCoord * res / 5.0);
        vec2 jit = vec2(hash21(cell + tick), hash21(cell.yx + tick * 1.7)) - 0.5;
        vec2 jitter = jit * 2.0 * px * (2.0 + 9.0 * intensity);
        jitter += vec2(sin(texCoord.y * 40.0 + t * 6.0), cos(texCoord.x * 30.0 + t * 5.0)) * px * (1.0 + 3.0 * intensity);

        // slice displacement: some horizontal bands slide sideways
        float band = floor(texCoord.y * res.y / 14.0);
        float bandRand = hash21(vec2(band, floor(t * 9.0)));
        float slide = bandRand > (0.88 - 0.22 * intensity)
                ? (hash21(vec2(band * 3.1, floor(t * 9.0))) - 0.5) * 2.0 * px.x * (10.0 + 40.0 * intensity)
                : 0.0;
        vec2 d = jitter + vec2(slide, 0.0);

        float real = isFigure(texCoord);
        float body = max(real, max(isFigure(texCoord + d), isFigure(texCoord + d * 0.5)));

        // ---------------- chromatic aberration: the same mask at shifted positions ----------------
        float fr = 3.0 + 10.0 * intensity;                       // pixels
        float mPurple = isFigure(texCoord + d - vec2(fr, -fr * 0.35) * px);
        float mBlue   = isFigure(texCoord + d + vec2(fr * 0.8, fr * 0.15) * px);
        float mCyan   = isFigure(texCoord + d + vec2(fr * 1.5, -fr * 0.55) * px);
        vec3 purple = vec3(0.52, 0.16, 1.00);
        vec3 blue   = vec3(0.16, 0.30, 1.00);
        vec3 cyan   = vec3(0.60, 0.92, 1.00);

        // ghost copy: a faint second silhouette that jumps around
        float gseed = hash11(floor(t * 5.0));
        vec2 goff = vec2((gseed - 0.5) * 0.09, (hash11(gseed * 7.7) - 0.5) * 0.02);
        float ghostOn = step(0.45, hash11(floor(t * 3.0) + 13.0));
        float ghost = isFigure(texCoord - goff) * ghostOn;

        float outside = 1.0 - body;
        col = mix(col, vec3(0.06, 0.08, 0.20), ghost * outside * 0.40 * intensity);
        col = mix(col, purple, mPurple * outside * 0.92);
        col = mix(col, blue,   mBlue   * outside * 0.85);
        col = mix(col, cyan,   mCyan   * outside * 0.80);

        // radio interference: bright speckle and scan lines on the fringes
        float fringe = max(max(mPurple, mBlue), mCyan) * outside;
        float speckle = step(0.92, hash21(floor(texCoord * res / 2.0) + tick));
        float scan = step(0.97, hash21(vec2(floor(texCoord.y * res.y / 2.0), tick)));
        col = mix(col, vec3(0.75, 0.92, 1.0), fringe * speckle * 0.85 * intensity);
        col += vec3(0.20, 0.30, 0.55) * fringe * scan * intensity;

        // the body itself: pure black
        col = mix(col, vec3(0.0), body);
    }

    // ---------------- glowing eyes ----------------
    for (int i = 0; i < 16; i++) {
        float level = EyeInt[i / 4][i % 4];
        if (level <= 0.001) {
            continue;
        }
        vec4 e = EyePos[i];
        vec2 dv = (texCoord - e.xy) * vec2(aspect, 1.0);
        float dist = length(dv) / max(e.w, 1.0e-5);
        if (dist > 7.0) {
            continue;
        }
        // hidden when something is in front of the eye (wall, another player): compare with the depth buffer at the eye
        float sceneDepth = texture(InDepthSampler, clamp(e.xy, 0.0, 1.0)).r;
        bool occluded = Time.y > 0.5 ? (sceneDepth > e.z * 1.04 + 1.0e-6) : (sceneDepth < e.z * 0.96);
        if (occluded) {
            continue;
        }
        float core = smoothstep(1.0, 0.40, dist);
        float halo = exp(-dist * dist * 0.20) * 0.55;
        col += (vec3(1.0) * core * 1.25 + vec3(0.78, 0.90, 1.0) * halo) * level;
    }

    fragColor = vec4(clamp(col, 0.0, 1.0), 1.0);
}
