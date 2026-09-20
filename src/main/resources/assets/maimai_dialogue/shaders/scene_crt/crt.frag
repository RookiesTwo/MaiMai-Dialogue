#version 330 core
uniform sampler2D Scene;
uniform sampler2D Bloom;
uniform vec4 Geometry; // curvature, scanline strength, mask strength, chromatic aberration in pixels
uniform vec4 Effects;  // vignette, noise, flicker, bloom
uniform vec2 SceneSize;
uniform float Time;
in vec2 uv;
out vec4 fragColor;

vec4 sceneAt(vec2 pos) {
    if (any(lessThan(pos, vec2(0.0))) || any(greaterThan(pos, vec2(1.0)))) return vec4(0.0);
    return texture(Scene, pos);
}
vec3 straight(vec4 color) { return color.a > 0.00001 ? color.rgb / color.a : vec3(0.0); }
float random(vec2 pos) { return fract(sin(dot(pos, vec2(12.9898,78.233))) * 43758.5453); }

void main() {
    vec2 p = uv * 2.0 - 1.0;
    vec2 warped = p * (vec2(1.0) + Geometry.x * 0.35 * p.yx * p.yx);
    vec2 sampleUV = warped * 0.5 + 0.5;
    vec4 center = sceneAt(sampleUV);
    if (center.a <= 0.0) { fragColor = vec4(0.0); return; }
    vec3 rgb = straight(center);
    if (Geometry.w > 0.0) {
        vec2 offset = vec2(Geometry.w / SceneSize.x, 0.0);
        rgb.r = straight(sceneAt(sampleUV + offset)).r;
        rgb.b = straight(sceneAt(sampleUV - offset)).b;
    }
    float scanline = 0.5 + 0.5 * cos(sampleUV.y * SceneSize.y * 2.0943951);
    rgb *= 1.0 - Geometry.y * scanline;
    int channel = int(mod(floor(uv.x * SceneSize.x), 3.0));
    vec3 mask = channel == 0 ? vec3(1.0,0.65,0.65) : channel == 1 ? vec3(0.65,1.0,0.65) : vec3(0.65,0.65,1.0);
    rgb *= mix(vec3(1.0), mask, Geometry.z);
    rgb *= 1.0 - Effects.x * smoothstep(0.15, 1.6, dot(p,p));
    if (Effects.y > 0.0) rgb += (random(floor(sampleUV * SceneSize) + floor(Time * 60.0)) - 0.5) * Effects.y * 0.35;
    if (Effects.z > 0.0) rgb *= 1.0 + sin(Time * 31.0) * Effects.z * 0.08;
    if (Effects.w > 0.0) rgb += texture(Bloom, sampleUV).rgb * Effects.w * 1.5;
    // Keep the warped scene's alpha; SceneContentView supplies the black CRT backdrop outside this pass.
    fragColor = vec4(clamp(rgb, 0.0, 1.0) * center.a, center.a);
}
