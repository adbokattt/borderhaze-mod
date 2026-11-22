#version 330

// Border Haze: blur + heat-haze ripple for everything OUTSIDE the vanilla world border.
//
// For every pixel: depth -> camera-relative world position -> distance outside the border square.
// The effect starts exactly at the border (distance 0) and fades in over `falloff` blocks, so there is
// no hard wall. Pixels inside the border are returned untouched (sharp).

uniform sampler2D InSampler;
uniform sampler2D InDepthSampler;

in vec2 texCoord;

// std140 block, must match BorderUniforms.java and border_haze.json (8 x vec4)
layout(std140) uniform BorderConfig {
    vec4 InvViewProj0;
    vec4 InvViewProj1;
    vec4 InvViewProj2;
    vec4 InvViewProj3;
    vec4 Bounds;   // camera-relative: minX, maxX, minZ, maxZ
    vec4 Params;   // x time(s), y falloff(blocks), z debug(0/1), w reverse-Z flag (1 = reverse-Z: sky depth 0; 0 = forward: sky depth 1)
    vec4 Effect;   // x blur radius(px), y ripple amplitude(uv), z ripple speed, w ripple scale
    vec4 Style;    // x style (0 = blur+ripple, 1 = pixel), y pixel block size(px), z pixel softness, w unused
};

out vec4 fragColor;

// Distance (in blocks) of the surface seen at `uv` OUTSIDE the border. <= 0 means inside / not applicable.
// 26.2 stores REVERSE-Z depth (depth = near / distance, sky = 0). In reverse and forward "zero to one"
// setups the NDC z equals the stored depth, so the inverse view-projection matrix unprojects it directly.
float outsideDistance(vec2 uv) {
    float depth = texture(InDepthSampler, uv).r;
    bool sky = Params.w > 0.5 ? depth <= 1.0e-7 : depth >= 0.99999;
    if (sky) {
        return -1.0; // nothing drawn here: leave untouched
    }
    vec4 clip = vec4(uv * 2.0 - 1.0, depth, 1.0);
    vec4 world = mat4(InvViewProj0, InvViewProj1, InvViewProj2, InvViewProj3) * clip;
    vec3 rel = world.xyz / world.w; // camera-relative world position

    float inside = min(min(rel.x - Bounds.x, Bounds.y - rel.x),
                       min(rel.z - Bounds.z, Bounds.w - rel.z));
    return -inside;
}

void main() {
    vec4 base = texture(InSampler, texCoord);
    float dist = outsideDistance(texCoord);
    float m = smoothstep(0.0, max(Params.y, 0.001), dist);

    // ---- DEBUG VIEW: red tint outside, yellow line exactly on the border ----
    if (Params.z > 0.5) {
        vec3 c = mix(base.rgb, vec3(1.0, 0.1, 0.1), m * 0.7);
        float line = 1.0 - smoothstep(0.0, 0.3, abs(dist));
        c = mix(c, vec3(1.0, 1.0, 0.0), line * 0.85);
        fragColor = vec4(c, 1.0);
        return;
    }

    // ---- inside the border: untouched ----
    if (m <= 0.0) {
        fragColor = vec4(base.rgb, 1.0);
        return;
    }

    // ---- STYLE 1: pixelated mosaic with a slight blur ----
    // Each screen block of Style.y pixels gets one colour: the average of a 3x3 tap pattern around the block
    // centre. Style.z widens the averaged area beyond the block, which softens the blocks (slight blur).
    if (Style.x > 0.5) {
        vec2 screen = vec2(textureSize(InSampler, 0));
        float px = max(Style.y, 1.0);
        vec2 center = (floor(texCoord * screen / px) + 0.5) * px / screen;
        float spacing = px * (1.0 + Style.z) / 3.0;
        vec3 sum = vec3(0.0);
        for (int i = -1; i <= 1; i++) {
            for (int j = -1; j <= 1; j++) {
                sum += texture(InSampler, center + vec2(float(i), float(j)) * spacing / screen).rgb;
            }
        }
        fragColor = vec4(mix(base.rgb, sum / 9.0, m), 1.0);
        return;
    }

    // ---- ripple: smooth animated displacement, strength grows with m ----
    float t = Params.x * Effect.z;
    float s = Effect.w;
    vec2 wobble = vec2(
        sin(texCoord.y * s + t) + 0.5 * sin(texCoord.y * s * 2.3 - t * 1.7),
        cos(texCoord.x * s * 0.8 + t * 1.3) + 0.5 * sin(texCoord.x * s * 1.9 + t * 0.7));
    vec2 uv = texCoord + wobble * Effect.y * m;

    // ---- blur: spiral kernel (golden angle), radius grows with m ----
    vec2 texel = 1.0 / vec2(textureSize(InSampler, 0));
    float radius = Effect.x * m; // pixels
    vec3 acc = vec3(0.0);
    const int TAPS = 24;
    for (int i = 0; i < TAPS; i++) {
        float r = sqrt((float(i) + 0.5) / float(TAPS));
        float a = float(i) * 2.39996323;
        acc += texture(InSampler, uv + vec2(cos(a), sin(a)) * r * radius * texel).rgb;
    }
    fragColor = vec4(acc / float(TAPS), 1.0);
}
