#version 330

// GLITCH shell, version 4: Steve's own silhouette, but unstable.
//
// The black player model is the body. This shader finds its pixels (dark + at the figure's distance + near the analytic
// pose that the mod projected to the screen) and turns them into the figure:
//   * RAGGED EDGE   the silhouette is dilated by a small, noise-driven amount, so the outline is fuzzy / hairy and covers the
//                   straight blocky edges of the model, while the overall shape stays clearly the player model;
//                   it wobbles, and now and then thin bands slide sideways
//   * COLOUR FRINGES purple, blue and pale cyan copies of that same ragged silhouette, shifted to the sides and drawn behind
//                   it (purple/blue on the left, cyan on the right), smooth, visible in the dark as well
//   * EYES           two small white cores with a soft bloom at the projected eye positions, hidden behind walls,
//                   veiled by a faint drifting haze
// Everything is measured in BLOCKS, so it looks the same near and far. Only pixels near a figure are touched.

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
int gFig = 0;
float gFigZ = 1.0;
float gPxb = 0.1;
float gPhase = 0.0;

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

// signed distance from a screen position to the analytic pose, in screen-height units (negative = inside)
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

// 1 if the pixel at uv is a pixel of the black player model of the current figure
float isFigure(vec2 uv) {
    if (uv.x < 0.0 || uv.x > 1.0 || uv.y < 0.0 || uv.y > 1.0) {
        return 0.0;
    }
    vec3 c = textureLod(InSampler, uv, 0.0).rgb;
    if (max(c.r, max(c.g, c.b)) > 0.25) {
        return 0.0;                                   // the model is black: anything brighter is background
    }
    if (Time.y > 0.5) {
        float d = textureLod(InDepthSampler, uv, 0.0).r;
        float z = d > 1.0e-7 ? Cam.x / d : 1.0e9;
        if (abs(z - gFigZ) > 1.0) {
            return 0.0;                               // not at the figure's distance (dark sky, far trees, ...)
        }
    }
    if (figureSdf(gFig, uv) / gPxb > 0.14) {
        return 0.0;                                   // not near the analytic body
    }
    return 1.0;
}

// the mask averaged over a small disc (radius in pixels)
float coverage(vec2 uv, float radiusPx, int taps) {
    float sum = isFigure(uv) * 1.5;
    float norm = 1.5;
    for (int i = 0; i < 8; i++) {
        if (i >= taps) {
            break;
        }
        float a = float(i) * 2.39996323 + gPhase;
        float r = sqrt((float(i) + 0.5) / float(taps)) * radiusPx;
        sum += isFigure(uv + vec2(cos(a), sin(a)) * r / vec2(aspect * 1.0, 1.0) / vec2(textureSize(InSampler, 0).y, textureSize(InSampler, 0).y));
        norm += 1.0;
    }
    return sum / norm;
}

// ragged silhouette: coverage compared with fine noise. The threshold stays below the model's own edge, so the visible edge is
// always the noisy outer boundary (never the straight blocky edge of the model): hairy spikes and dents, size set by the disc radius
float raggedAlpha(vec2 uv, float radiusPx, float noiseVal, int taps) {
    float cov = coverage(uv, radiusPx, taps);
    float thr = 0.03 + 0.27 * noiseVal;
    return clamp((cov - thr) * 6.0 + 0.5, 0.0, 1.0);
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
        gFig = fig;
        gFigZ = figZ;
        gPxb = pxb;

        // only pixels near the body can change (the effect reaches ~0.5 block): everything else is skipped
        float sd0 = figureSdf(fig, texCoord) / pxb;
        if (sd0 < 0.60) {
            // ---- how unstable: calm .. violent bursts ----
            float slow = ts * 1.1;
            float w0 = hash11(floor(slow));
            float w1 = hash11(floor(slow) + 1.0);
            float f = fract(slow);
            f = f * f * (3.0 - 2.0 * f);
            float intensity = 0.28 + 0.72 * smoothstep(0.50, 0.92, mix(w0, w1, f));
            intensity = max(intensity, 0.95 * step(0.93, hash11(floor(ts * 6.0) + 91.0)));
            gPhase = floor(ts * 14.0) * 1.7;

            // ---- depth: is something in front of the figure here (wall, tree)? ----
            float depth = texture(InDepthSampler, texCoord).r;
            float sceneZ = (Time.y > 0.5) ? (depth > 1.0e-7 ? Cam.x / depth : 1.0e9) : 1.0e9;
            float occ = smoothstep(figZ - 1.1, figZ - 0.5, sceneZ);

            // ---- position in blocks, wobble, sliding bands ----
            vec2 pb = vec2(texCoord.x * aspect, texCoord.y) / pxb;
            float noiseVal = fbm(pb * 12.0 + vec2(ts * 1.7, -ts * 2.3));          // fine, hairy noise (~0.08 block)

            float band = floor(pb.y / 0.14);
            float bandRand = hash21(vec2(band, floor(ts * 8.0)));
            float slideBlocks = bandRand > (0.95 - 0.10 * intensity)
                    ? (hash21(vec2(band * 3.1, floor(ts * 8.0))) - 0.5) * 2.0 * (0.03 + 0.12 * intensity)
                    : 0.0;
            vec2 wobble = vec2(sin(pb.y * 9.0 + ts * 5.0), cos(pb.x * 8.0 + ts * 4.0)) * (0.004 + 0.012 * intensity) * pxb;
            vec2 uvq = texCoord + vec2(slideBlocks * pxb / aspect, 0.0) + wobble;

            float radBlocks = 0.035 + 0.060 * intensity;                            // how far the hair reaches
            float radPx = max(1.5, radBlocks * pxb * res.y);

            if (Time.w > 0.5) {
                // debug view: magenta = recognised as the model, cyan = analytic hint region
                col = mix(col, vec3(0.0, 1.0, 1.0), (figureSdf(fig, texCoord) / pxb < 0.14 ? 0.18 : 0.0));
                col = mix(col, vec3(1.0, 0.0, 1.0), isFigure(texCoord) * 0.7);
                fragColor = vec4(col, 1.0);
                return;
            }

            // ---- the ragged body and its colour fringes (shifted copies of the same silhouette) ----
            float real = isFigure(texCoord);
            float aG = raggedAlpha(uvq, radPx, noiseVal, 8);

            float k = 0.6 + 0.9 * intensity;
            vec2 oP = vec2(-0.048 * k * pxb / aspect, 0.012 * k * pxb);       // purple: left, slightly up
            vec2 oB = vec2(-0.026 * k * pxb / aspect, -0.006 * k * pxb);      // blue: left, slightly down
            vec2 oC = vec2(0.042 * k * pxb / aspect, -0.010 * k * pxb);       // pale cyan: right
            float aP = raggedAlpha(uvq - oP, radPx, noiseVal, 5);
            float aB = raggedAlpha(uvq - oB, radPx, noiseVal, 5);
            float aC = raggedAlpha(uvq - oC, radPx, noiseVal, 5);

            vec3 purple = vec3(0.52, 0.16, 1.00);
            vec3 blue   = vec3(0.16, 0.30, 1.00);
            vec3 cyan   = vec3(0.62, 0.93, 1.00);

            // a faint ghost copy that jumps around now and then
            float gs = hash11(floor(ts * 4.0));
            vec2 goff = vec2((gs - 0.5) * 0.45 * pxb / aspect, (hash11(gs * 7.7) - 0.5) * 0.08 * pxb);
            float ghost = raggedAlpha(uvq - goff, radPx, noiseVal, 5) * step(0.55, hash11(floor(ts * 2.5) + 13.0));
            col = mix(col, vec3(0.05, 0.07, 0.16), ghost * 0.40 * intensity * occ);

            col = mix(col, purple, aP * occ * 0.90);
            col = mix(col, blue,   aB * occ * 0.88);
            col = mix(col, cyan,   aC * occ * 0.88);

            // the body on top: pure black (the real model pixels always, plus the ragged extension)
            col = mix(col, vec3(0.0), max(aG * occ, real));
        }
    }

    // ---------------- eyes: small, bright, seen through the darkness ----------------
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
            float bloom = exp(-dist * dist * 0.9) * 0.45;
            vec2 q = dv / max(ep.w, 1.0e-5);
            float veil = smoothstep(0.35, 0.90, fbm(q * 0.9 + vec2(tsf * 0.7, -tsf * 0.5) + vec2(float(f) * 3.1 + float(e) * 1.7))) * 0.55;
            float breathe = mix(0.70, 1.0, smoothstep(0.2, 0.8, vnoise(vec2(tsf * 1.3, float(f * 2 + e) * 5.0))));
            glow += (vec3(1.0) * core * 1.15 + vec3(0.80, 0.90, 1.0) * bloom) * level * (1.0 - veil) * breathe;
        }
    }
    col += glow;

    fragColor = vec4(clamp(col, 0.0, 1.0), 1.0);
}
