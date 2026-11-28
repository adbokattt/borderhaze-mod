#version 330

// GLITCH shell, version 3: a permanent dense cloud of unstable black smoke in the shape of a person.
//
// The figure is NOT searched for on the picture. The mod describes the pose analytically (six capsules: head, torso,
// two arms, two legs) and projects it to the screen; this shader turns that description into:
//   * a SMOKE CLOUD    solid black around the body, then fading into tendrils whose length is eaten by animated noise,
//                      drifting upwards, sliding in torn bands; always present, thick enough to bury the blocky model
//   * ABERRATION       red / green / blue see the cloud at slightly different positions (a lens does this): black smoke
//                      multiplies the picture per channel, and the fringes are ALSO added as light (red, cyan, blue,
//                      yellow) so they stay clearly coloured at night, on dark backgrounds and in caves
//   * INTERFERENCE     lots of cold static dust and scan lines in and around the cloud, torn blocks, ghost copies
//   * EYES             small bright cores with a short bloom, partly veiled by smoke, so they look like light seen
//                      through the darkness
// Everything is measured in BLOCKS (not pixels), so it looks the same near and far, by day and by night.
// The cloud is hidden behind walls and other geometry using the depth buffer.

uniform sampler2D InSampler;
uniform sampler2D InDepthSampler;

in vec2 texCoord;

// std140 block, must match GlitchUniforms.java and glitch.json (54 x vec4)
layout(std140) uniform GlitchConfig {
    vec4 Time;         // x time(s), y reverse-Z flag, z number of figures, w debug flag
    vec4 Cam;          // x near plane, y projection scale (m11)
    vec4 FigRect[4];   // minU, minV, maxU, maxV
    vec4 FigInfo[4];   // x view depth of the figure, y seed
    vec4 PrimAB[24];   // 6 capsules per figure: Ax, Ay, Bx, By (uv)
    vec4 PrimR[8];     // 2 vec4 per figure: capsule radii (screen-height fractions), negative = missing
    vec4 EyePos[8];    // 2 per figure: u, v, depth, radius
    vec4 EyeInt[4];    // per figure: x, y = eye brightness
};

out vec4 fragColor;

float aspect = 1.0;

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

float segDist(vec2 p, vec2 a, vec2 b) {
    vec2 pa = p - a;
    vec2 ba = b - a;
    float h = clamp(dot(pa, ba) / max(dot(ba, ba), 1.0e-9), 0.0, 1.0);
    return length(pa - ba * h);
}

// signed distance from a screen position to the figure, in screen-height units (negative = inside the body)
float figureSdf(int fig, vec2 uv) {
    vec2 p = vec2(uv.x * aspect, uv.y);
    float d = 1.0e9;
    for (int k = 0; k < 6; k++) {
        float r = PrimR[fig * 2 + k / 4][k % 4];
        if (r < 0.0) {
            continue;
        }
        vec4 ab = PrimAB[fig * 6 + k];
        d = min(d, segDist(p, vec2(ab.x * aspect, ab.y), vec2(ab.z * aspect, ab.w)) - r);
    }
    return d;
}

// smoke density 0..1 at a screen position: solid close to the body, then fading; the noise changes the fade speed per place,
// which makes long tendrils where it is low and short stubs where it is high
float smokeAlpha(vec2 uv, int fig, float pxb, float reach, float noiseVal) {
    float d = figureSdf(fig, uv) / pxb;                 // blocks
    float eff = max(d, 0.0) * (0.6 + 0.8 * noiseVal);
    return 1.0 - smoothstep(0.14, reach, eff);
}

void main() {
    vec4 base = texture(InSampler, texCoord);
    fragColor = vec4(base.rgb, 1.0);

    vec2 res = vec2(textureSize(InSampler, 0));
    aspect = res.x / res.y;
    float t = Time.x;
    int n = int(Time.z + 0.5);

    vec3 col = base.rgb;

    // ---- which figure's rectangle are we in? ----
    int fig = -1;
    for (int i = 0; i < 4; i++) {
        if (i >= n) {
            break;
        }
        vec4 r = FigRect[i];
        if (texCoord.x >= r.x && texCoord.x <= r.z && texCoord.y >= r.y && texCoord.y <= r.w) {
            fig = i;
            break;
        }
    }

    if (fig >= 0) {
        float figZ = FigInfo[fig].x;
        float ts = t + FigInfo[fig].y * 17.0;
        float pxb = 0.5 * Cam.y / figZ;                  // screen-height fraction covered by one block at the figure

        // ---- hidden behind walls / trees / other players? (depth buffer) ----
        float depth = texture(InDepthSampler, texCoord).r;
        float sceneZ = (Time.y > 0.5) ? (depth > 1.0e-7 ? Cam.x / depth : 1.0e9) : 1.0e9;
        float occ = smoothstep(figZ - 1.1, figZ - 0.5, sceneZ);

        // ---- how unstable: always at least half strength, with violent bursts ----
        float slow = ts * 1.1;
        float w0 = hash11(floor(slow));
        float w1 = hash11(floor(slow) + 1.0);
        float f = fract(slow);
        f = f * f * (3.0 - 2.0 * f);
        float intensity = 0.50 + 0.50 * smoothstep(0.35, 0.90, mix(w0, w1, f));
        intensity = max(intensity, step(0.92, hash11(floor(ts * 6.0) + 91.0)));
        float pulse = 0.5 + 0.5 * sin(ts * 0.9 + 1.3 * sin(ts * 0.37));
        float reach = 0.40 + 0.32 * pulse + 0.22 * intensity;        // blocks: how far the cloud reaches
        float tick = floor(ts * 14.0);

        // ---- position in blocks (so everything has a constant world size) ----
        vec2 pb = vec2(texCoord.x * aspect, texCoord.y) / pxb;
        float noiseVal = fbm(pb * 3.0 + vec2(ts * 0.6, -ts * 1.2));

        // ---- torn bands and corruption blocks slide sideways; the cloud wobbles and rises ----
        float band = floor(pb.y / 0.16);
        float bandRand = hash21(vec2(band, floor(ts * 8.0)));
        float slideBlocks = bandRand > (0.90 - 0.18 * intensity)
                ? (hash21(vec2(band * 3.1, floor(ts * 8.0))) - 0.5) * 2.0 * (0.12 + 0.50 * intensity)
                : 0.0;
        vec2 blockCell = floor(pb / vec2(0.45, 0.10));
        float corrupt = hash21(blockCell + floor(ts * 9.0));
        if (corrupt > 0.95 - 0.04 * intensity) {
            slideBlocks += (hash21(blockCell * 1.3 + 5.0) - 0.5) * 0.55;
        }
        vec2 wobble = vec2(sin(pb.y * 7.0 + ts * 5.0), cos(pb.x * 6.0 + ts * 4.0)) * (0.02 + 0.05 * intensity) * pxb;
        float drift = (0.05 + 0.12 * intensity) * pxb;
        vec2 uvq = texCoord + vec2(slideBlocks * pxb / aspect, -drift) + wobble;

        if (Time.w > 0.5) {
            // debug view: magenta = body capsules, cyan ring = how far the cloud can reach
            float sd = figureSdf(fig, texCoord) / pxb;
            col = mix(col, vec3(1.0, 0.0, 1.0), (sd < 0.0 ? 0.55 : 0.0));
            col = mix(col, vec3(0.0, 1.0, 1.0), (abs(sd - reach) < 0.02 ? 0.9 : 0.0));
            fragColor = vec4(col, 1.0);
            return;
        }

        // ---- realistic lateral chromatic aberration: red, green, blue see the cloud at different places ----
        float splitUV = max((0.020 + 0.050 * intensity) * pxb, 2.0 / res.y);
        vec2 dir = normalize(vec2(1.0, 0.0) + 1.2 * (texCoord - 0.5) * vec2(aspect, 1.0));
        vec2 o = vec2(dir.x / aspect, dir.y) * splitUV;

        float aR = smokeAlpha(uvq - o, fig, pxb, reach, noiseVal) * occ;
        float aG = smokeAlpha(uvq, fig, pxb, reach, noiseVal) * occ;
        float aB = smokeAlpha(uvq + o * 1.35, fig, pxb, reach, noiseVal) * occ;

        // ghost copy: a faint second cloud that jumps around
        float gs = hash11(floor(ts * 5.0));
        vec2 goff = vec2((gs - 0.5) * 0.7, (hash11(gs * 7.7) - 0.5) * 0.15) * pxb;
        float ghost = smokeAlpha(uvq - vec2(goff.x / aspect, goff.y), fig, pxb, reach * 0.8, noiseVal) * occ
                * step(0.45, hash11(floor(ts * 3.0) + 13.0));
        col = mix(col, vec3(0.04, 0.06, 0.14), ghost * (1.0 - aG) * 0.45);

        // black smoke over the scene, per channel
        col *= (1.0 - vec3(aR, aG, aB));

        // the same fringes added as LIGHT, so they stay coloured on a dark background (night, caves)
        float lum = dot(base.rgb, vec3(0.299, 0.587, 0.114));
        float K = (0.55 + 0.75 * (1.0 - lum)) * (0.55 + 0.45 * intensity);
        float dR = aR - aG;
        float dB = aB - aG;
        col += vec3(1.00, 0.12, 0.18) * max(-dR, 0.0) * K;
        col += vec3(0.10, 0.95, 1.00) * max(dR, 0.0) * K;
        col += vec3(0.30, 0.22, 1.00) * max(-dB, 0.0) * K;
        col += vec3(1.00, 0.85, 0.25) * max(dB, 0.0) * K * 0.45;
        // a faint cold glow along the outer edge of the cloud
        col += vec3(0.25, 0.35, 0.70) * (aG * (1.0 - aG) * 4.0) * 0.22 * intensity * (1.0 - lum * 0.7);

        // ---- radio interference: lots of static dust, scan lines ----
        float cellPx = max(2.0, 0.025 * pxb * res.y);
        vec2 dc = floor(texCoord * res / cellPx);
        float dust = step(0.86 - 0.07 * intensity, hash21(dc + tick));
        vec3 dustCol = mix(vec3(0.70, 0.90, 1.00), vec3(0.72, 0.42, 1.00), step(0.5, hash21(dc * 1.7 + tick)));
        float zone = aG * (1.0 - aG) * 4.0 * 0.85 + aG * 0.12;
        col += dustCol * dust * zone * (0.55 + 0.45 * intensity);

        float rowH = max(2.0, 0.012 * pxb * res.y);
        float scan = step(0.93, hash21(vec2(floor(texCoord.y * res.y / rowH), tick)));
        col += vec3(0.22, 0.32, 0.60) * scan * aG * 0.55 * intensity;
    }

    // ---------------- eyes: small and bright, seen through the darkness ----------------
    vec3 glow = vec3(0.0);
    for (int f = 0; f < 4; f++) {
        if (f >= n) {
            break;
        }
        float tsf = t + FigInfo[f].y * 17.0;
        for (int e = 0; e < 2; e++) {
            float level = EyeInt[f][e];
            if (level <= 0.001) {
                continue;
            }
            vec4 ep = EyePos[f * 2 + e];
            vec2 dv = (texCoord - ep.xy) * vec2(aspect, 1.0);
            float dist = length(dv) / max(ep.w, 1.0e-5);
            if (dist > 4.0) {
                continue;
            }
            float sceneDepth = texture(InDepthSampler, clamp(ep.xy, 0.0, 1.0)).r;
            bool occluded = Time.y > 0.5 ? (sceneDepth > ep.z * 1.04 + 1.0e-6) : (sceneDepth < ep.z * 0.96);
            if (occluded) {
                continue;
            }
            float core = smoothstep(1.0, 0.45, dist);
            float bloom = exp(-dist * dist * 0.9) * 0.40;
            // drifting wisps of smoke cross the eye, and the eye slowly breathes: it shines through the darkness, not on top of it
            vec2 q = dv / max(ep.w, 1.0e-5);
            float veil = smoothstep(0.30, 0.85, fbm(q * 0.9 + vec2(tsf * 0.7, -tsf * 0.5) + vec2(float(f) * 3.1 + float(e) * 1.7))) * 0.80;
            float breathe = mix(0.60, 1.0, smoothstep(0.2, 0.8, vnoise(vec2(tsf * 1.3, float(f * 2 + e) * 5.0))));
            glow += (vec3(1.0) * core * 1.15 + vec3(0.80, 0.90, 1.0) * bloom) * level * (1.0 - veil) * breathe;
        }
    }
    col += glow;

    fragColor = vec4(clamp(col, 0.0, 1.0), 1.0);
}
