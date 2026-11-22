#version 330

// Border Haze (EDGE chain): blurs the whole screen - world, hand, hotbar, open inventories - when the player
// is near the world border. Runs at the very end of the frame, needs no depth.
//
// The blurred area starts at the screen edges and corners and grows towards the centre as the player gets
// closer to the border (Edge.x = proximity 0..1); at the border it covers the whole screen. The chain runs this
// ONE shader four times: horizontal, vertical (first pair, radius Edge.y), horizontal, vertical (second pair,
// radius Edge.z). Two iterations keep even a very large blur smooth.

uniform sampler2D InSampler;

in vec2 texCoord;

// std140 blocks, must match EdgeUniforms.java and border_edge.json
layout(std140) uniform EdgeConfig {
    vec4 Edge;   // x proximity (0 far .. 1 at the border), y radius pair 1 (px), z radius pair 2 (px), w haze-wash strength
    vec4 Haze;   // rgb haze colour
};

layout(std140) uniform PassConfig {
    vec4 Dir;    // xy blur direction (texels), z 1 = second pair, w 1 = last pass
};

out vec4 fragColor;

// One direction of a separable tent-weighted blur (bilinear sampling). At most 12 taps per side:
// the tap spacing grows with the radius, so the cost stays bounded however strong the blur is.
vec3 tentBlur(vec2 uv, vec2 dir, float radius) {
    vec2 texel = 1.0 / vec2(textureSize(InSampler, 0));
    float stepPx = max(1.0, radius / 12.0);
    vec3 acc = textureLod(InSampler, uv, 0.0).rgb;
    float wsum = 1.0;
    for (int i = 1; i <= 12; i++) {
        float off = float(i) * stepPx;
        if (off > radius) {
            break;
        }
        float w = 1.0 - off / (radius + stepPx);
        vec2 d = dir * texel * off;
        acc += (textureLod(InSampler, uv + d, 0.0).rgb + textureLod(InSampler, uv - d, 0.0).rgb) * w;
        wsum += 2.0 * w;
    }
    return acc / wsum;
}

void main() {
    float prox = Edge.x;

    // How far the blur has crept in from the screen edges: r = 0 at the centre, 1 in the corners.
    // The front of the blurred area moves from beyond the corners (prox = 0) to past the centre (prox = 1).
    vec2 q = texCoord * 2.0 - 1.0;
    float r = length(q) * 0.70710678;
    float front = mix(1.1, -0.6, prox);
    float amount = smoothstep(front, front + 0.45, r);

    // The blur itself also gets stronger as the player gets closer
    float strength = 0.25 + 0.75 * prox;
    float radius = (Dir.z > 0.5 ? Edge.z : Edge.y) * amount * strength;

    vec3 color = radius >= 0.5 ? tentBlur(texCoord, Dir.xy, radius) : textureLod(InSampler, texCoord, 0.0).rgb;

    // In the last pass wash the (already very blurred) image towards the haze colour, strongest at the border
    if (Dir.w > 0.5) {
        color = mix(color, Haze.rgb, Edge.w * amount * prox * prox);
    }
    fragColor = vec4(color, 1.0);
}
