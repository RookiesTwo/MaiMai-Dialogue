#version 330 core
uniform sampler2D Scene;
uniform vec2 Direction;
in vec2 uv;
out vec4 fragColor;
void main() {
    vec3 light = texture(Scene, uv).rgb * 0.227027;
    light += (texture(Scene, uv + Direction * 1.384615).rgb + texture(Scene, uv - Direction * 1.384615).rgb) * 0.316216;
    light += (texture(Scene, uv + Direction * 3.230769).rgb + texture(Scene, uv - Direction * 3.230769).rgb) * 0.070270;
    fragColor = vec4(light, 1.0);
}
