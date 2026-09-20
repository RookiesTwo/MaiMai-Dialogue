#version 330 core
uniform sampler2D Scene;
uniform vec3 Adjustments;
uniform vec4 Tint;
in vec2 uv;
out vec4 fragColor;
void main() {
    vec4 src = texture(Scene, uv);
    vec3 rgb = src.a > 0.0 ? src.rgb / src.a : vec3(0.0);
    float luminance = dot(rgb, vec3(0.2126, 0.7152, 0.0722));
    rgb = clamp(mix(vec3(luminance), rgb, 1.0 + Adjustments.z / 100.0), 0.0, 1.0);
    rgb = clamp((rgb - 0.5) * (1.0 + Adjustments.y / 100.0) + 0.5, 0.0, 1.0);
    rgb = clamp(rgb + Adjustments.x / 100.0, 0.0, 1.0);
    rgb = mix(rgb, Tint.rgb, Tint.a);
    fragColor = vec4(rgb * src.a, src.a);
}
