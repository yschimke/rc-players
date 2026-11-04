package ee.schimke.composeai.rcplayer.threed

import ee.schimke.composeai.rcplayer.threed.Rc3DShaderMeshData.Companion.MAX_LIGHTS

/**
 * The GLSL ES 3.0 programs the GPU depth renderers share: GLES 3.0 on Android and WebGL2 in the
 * browser accept the same source. The vertex shader repeats the engine's `litColor` from the shader
 * mesh's per-face layout ([Rc3DShaderMeshData]); the fragment shader applies the texture.
 */
internal object Rc3DDepthShaders {
  val ATTRIBUTE_NAMES =
    listOf(
      0 to "aPosition",
      1 to "aNormal",
      2 to "aEdge1",
      3 to "aEdge2",
      4 to "aCentroid",
      5 to "aUv",
    )

  /** Location, float offset in the vertex, and component count. */
  val ATTRIBUTES =
    listOf(
      Triple(0, Rc3DShaderMeshData.POSITION, 3),
      Triple(1, Rc3DShaderMeshData.NORMAL, 3),
      Triple(2, Rc3DShaderMeshData.EDGE1, 3),
      Triple(3, Rc3DShaderMeshData.EDGE2, 3),
      Triple(4, Rc3DShaderMeshData.CENTROID, 3),
      Triple(5, Rc3DShaderMeshData.UV, 2),
    )

  /** `litColor` and `litColorSpecular` per vertex, as the shader mesh lights. */
  val MESH_VERTEX =
    """
    #version 300 es
    in vec3 aPosition;
    in vec3 aNormal;
    in vec3 aEdge1;
    in vec3 aEdge2;
    in vec3 aCentroid;
    in vec2 aUv;
    uniform mat4 uMvp;
    uniform mat4 uMv;
    uniform mat4 uNm;
    uniform vec4 uBase;
    uniform vec3 uShading;
    uniform int uLightCount;
    uniform vec4 uLightPos[$MAX_LIGHTS];
    uniform vec4 uLightColor[$MAX_LIGHTS];
    out vec4 vShade;
    out vec2 vUv;

    vec3 toLight(vec4 light, vec3 p) {
      if (light.w > 0.5) {
        vec3 d = light.xyz - p;
        float l = length(d);
        return l > 0.0 ? d / l : d;
      }
      return light.xyz;
    }

    void main() {
      gl_Position = uMvp * vec4(aPosition, 1.0);
      vUv = vec2(aUv.x, 1.0 - aUv.y);
      vec3 eye = (uMv * vec4(aPosition, 1.0)).xyz;
      vec3 face = cross((uMv * vec4(aEdge1, 0.0)).xyz, (uMv * vec4(aEdge2, 0.0)).xyz);
      // Not "centroid": a reserved word in GLSL ES 3.0.
      vec3 faceCentre = (uMv * vec4(aCentroid, 1.0)).xyz;
      vec3 n;
      vec3 p;
      if (uShading.x > 0.5) {
        n = (uNm * vec4(aNormal, 0.0)).xyz;
        p = eye;
      } else {
        n = face;
        p = uShading.y > 0.0 ? eye : faceCentre;
      }
      float nl = length(n);
      n = nl > 0.0 ? n / nl : vec3(0.0);
      vec3 diffuse = vec3(${Rc3DShaderMeshData.AMBIENT});
      vec3 spec = vec3(0.0);
      if (uShading.y > 0.0) {
        float pl = length(p);
        vec3 view = pl > 0.0 ? -p / pl : vec3(0.0, 0.0, 1.0);
        if (dot(n, view) < 0.0) n = -n;
        for (int i = 0; i < uLightCount; i++) {
          vec3 l = toLight(uLightPos[i], p);
          float ndl = dot(n, l);
          if (ndl <= 0.0) continue;
          diffuse += uLightColor[i].rgb * ndl;
          vec3 h = l + view;
          float hl = length(h);
          if (hl <= 0.0) continue;
          float ndh = dot(n, h) / hl;
          if (ndh <= 0.0) continue;
          spec += uLightColor[i].rgb * (pow(ndh, uShading.z) * uShading.y);
        }
      } else {
        for (int i = 0; i < uLightCount; i++) {
          diffuse += uLightColor[i].rgb * abs(dot(n, toLight(uLightPos[i], p)));
        }
      }
      vec3 rgb = min(uBase.rgb * diffuse + spec, vec3(1.0));
      vShade = vec4(rgb * uBase.a, uBase.a);
    }
    """
      .trimIndent()

  val MESH_FRAGMENT =
    """
    #version 300 es
    precision highp float;
    in vec4 vShade;
    in vec2 vUv;
    uniform sampler2D uTexture;
    uniform float uTextured;
    out vec4 fragColor;

    void main() {
      if (uTextured > 0.5) {
        vec4 texel = texture(uTexture, vUv);
        // As the software rasterizer does: a fully transparent texel writes neither colour nor
        // depth, so what is behind a hole in the texture shows through it.
        if (texel.a == 0.0) discard;
        fragColor = vShade * texel;
      } else {
        fragColor = vShade;
      }
    }
    """
      .trimIndent()
}
