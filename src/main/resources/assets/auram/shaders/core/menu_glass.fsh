#version 150

uniform sampler2D Sampler0;
in vec2 texCoord0;
out vec4 fragColor;

void main() {
    vec2 stepUV = 1.0 / vec2(textureSize(Sampler0, 0));
    vec3 color = vec3(0.0);
    for (int y = -1; y <= 1; ++y) {
        for (int x = -1; x <= 1; ++x) {
            vec2 sampleUV = clamp(texCoord0 + vec2(x, y) * stepUV, stepUV * 0.5, 1.0 - stepUV * 0.5);
            color += texture(Sampler0, sampleUV).rgb * float((2 - abs(x)) * (2 - abs(y)));
        }
    }
    fragColor = vec4(mix(color / 16.0, vec3(0.035, 0.065, 0.10), 0.70), 1.0);
}
