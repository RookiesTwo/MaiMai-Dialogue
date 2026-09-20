#version 330 core
uniform sampler2D Scene;
in vec2 uv;
out vec4 fragColor;
vec3 bright(vec2 pos) {
    vec4 color = texture(Scene, pos);
    vec3 rgb = color.a > 0.00001 ? color.rgb / color.a : vec3(0.0);
    float luminance = dot(rgb, vec3(0.2126,0.7152,0.0722));
    return rgb * smoothstep(0.6, 0.85, luminance) * color.a;
}
void main() {
    vec2 offset = 1.0 / vec2(textureSize(Scene,0));
    vec3 light = bright(uv + vec2(-offset.x,-offset.y)) + bright(uv + vec2(offset.x,-offset.y))
               + bright(uv + vec2(-offset.x,offset.y)) + bright(uv + vec2(offset.x,offset.y));
    fragColor = vec4(light * 0.25, 1.0);
}
