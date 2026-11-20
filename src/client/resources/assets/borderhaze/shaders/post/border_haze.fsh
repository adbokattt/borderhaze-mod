#version 330

// STEP 4a TEST SHADER: red tint over the whole screen.
// Replaced by the real mask / blur / ripple shader in the next steps.

uniform sampler2D InSampler;

in vec2 texCoord;

out vec4 fragColor;

void main() {
    vec4 color = texture(InSampler, texCoord);
    fragColor = vec4(mix(color.rgb, vec3(1.0, 0.15, 0.15), 0.35), 1.0);
}
