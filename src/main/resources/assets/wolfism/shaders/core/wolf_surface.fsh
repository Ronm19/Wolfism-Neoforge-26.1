#version 330

#moj_import <minecraft:fog.glsl>
#moj_import <minecraft:dynamictransforms.glsl>

uniform sampler2D Sampler0;
uniform sampler2D WolfDetailSampler;
in float sphericalVertexDistance;
in float cylindricalVertexDistance;
in vec4 vertexPerFaceColorBack;
in vec4 vertexPerFaceColorFront;
in vec4 lightMapColor;
in vec4 overlayColor;
in vec2 texCoord0;
out vec4 fragColor;

void main() {
    vec4 skin = texture(Sampler0, texCoord0);
    if (skin.a < ALPHA_CUTOUT) discard;
    // Match the model's original logical texels even with a higher-resolution resource pack.
    vec2 logical = floor(texCoord0 * vec2(UV_WIDTH, UV_HEIGHT));
    vec2 cell = (mod(logical, 32.0) + 0.5) / 32.0;
    vec2 atlasUV = (cell + vec2(DETAIL_COLUMN, DETAIL_ROW)) / vec2(4.0, 2.0);
    float grain = texture(WolfDetailSampler, atlasUV).r;
    float detail = clamp((grain - DETAIL_CENTER) * DETAIL_STRENGTH, -0.18, 0.16);
    // Eye, muzzle and ear pixels stay exact. Extra wings, horns and decorations
    // outside the original wolf UV area keep their authored surface treatment.
#if WOLF_BABY == 1
    bool face = logical.y >= 12.0 && logical.y < 22.0 && logical.x < 27.0;
    bool extra = logical.x >= 32.0 || logical.y >= 32.0;
#else
    bool face = logical.x < 21.0 && logical.y < 18.0;
    bool extra = logical.x >= 56.0 || logical.y >= 32.0;
#endif
    if (face || extra) detail = 0.0;
    // Multiplicative neutral shading preserves hue, markings, cutout alpha and silhouettes.
    skin.rgb *= 1.0 + detail;
    vec4 lighting = gl_FrontFacing ? vertexPerFaceColorFront : vertexPerFaceColorBack;
    skin *= lighting * ColorModulator;
    skin.rgb = mix(overlayColor.rgb, skin.rgb, overlayColor.a);
    skin *= lightMapColor;
    fragColor = apply_fog(skin, sphericalVertexDistance, cylindricalVertexDistance,
        FogEnvironmentalStart, FogEnvironmentalEnd, FogRenderDistanceStart, FogRenderDistanceEnd, FogColor);
}
