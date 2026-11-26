#version 150

uniform sampler2D Sampler0;
uniform vec4 ColorModulator;
uniform vec3 MinimapBounds;

in vec4 vertexColor;
in vec2 texCoord0;

out vec4 fragColor;

void main() {
    vec2 offset = (texCoord0 - MinimapBounds.xy) / MinimapBounds.z;
    float radiusSquared = dot(offset, offset);
    if (radiusSquared >= 1.0) {
        discard;
    }

    vec4 color = texture(Sampler0, texCoord0) * vertexColor;
    if (color.a < 0.1) {
        discard;
    }
    if (radiusSquared > 0.81) {
        // Normalize the exponential to reach zero at the rim. Ease the endpoints so
        // the feather meets the opaque center and the background smoothly.
        float edge = smoothstep(0.9, 1.0, sqrt(radiusSquared));
        color.a *= (exp2(-2.0 * edge) - 0.25) / 0.75;
    }
    fragColor = color * ColorModulator;
}
