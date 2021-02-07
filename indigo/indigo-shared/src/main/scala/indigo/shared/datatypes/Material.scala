package indigo.shared.datatypes

import indigo.shared.assets.AssetName
import indigo.shared.shader.ShaderId
import indigo.shared.shader.Uniform
import indigo.shared.shader.ShaderPrimitive
import indigo.shared.shader.StandardShaders
import indigo.shared.shader.ShaderPrimitive.float

sealed trait Material {
  def hash: String
  // def shaderId: ShaderId
}

sealed trait StandardMaterial extends Material {
  // def default: AssetName
  // def isLit: Boolean
  // def lit: Material
  // def unlit: Material
  def toGLSLShader: GLSLShader
}

final case class GLSLShader(
    shaderId: ShaderId,
    uniforms: Map[Uniform, ShaderPrimitive],
    channel0: Option[AssetName],
    channel1: Option[AssetName],
    channel2: Option[AssetName],
    channel3: Option[AssetName]
) extends Material {
  def uniformHash: String =
    uniforms.toList.map(p => p._1.name + p._2.hash).mkString

  def withUniforms(newUniforms: List[(Uniform, ShaderPrimitive)]): GLSLShader =
    this.copy(uniforms = newUniforms.toMap)
  def withUniforms(newUniforms: (Uniform, ShaderPrimitive)*): GLSLShader =
    withUniforms(newUniforms.toList)

  def addUniforms(newUniforms: List[(Uniform, ShaderPrimitive)]): GLSLShader =
    this.copy(uniforms = uniforms ++ newUniforms)
  def addUniforms(newUniforms: (Uniform, ShaderPrimitive)*): GLSLShader =
    addUniforms(newUniforms.toList)

  def withChannel0(assetName: AssetName): GLSLShader =
    this.copy(channel0 = Some(assetName))
  def withChannel1(assetName: AssetName): GLSLShader =
    this.copy(channel1 = Some(assetName))
  def withChannel2(assetName: AssetName): GLSLShader =
    this.copy(channel2 = Some(assetName))
  def withChannel3(assetName: AssetName): GLSLShader =
    this.copy(channel3 = Some(assetName))

  lazy val hash: String =
    s"custom-${shaderId.value}" +
      s"-${uniformHash}" +
      s"-${channel0.map(_.value).getOrElse("")}" +
      s"-${channel1.map(_.value).getOrElse("")}" +
      s"-${channel2.map(_.value).getOrElse("")}" +
      s"-${channel3.map(_.value).getOrElse("")}"

}
object GLSLShader {

  def apply(shaderId: ShaderId): GLSLShader =
    GLSLShader(shaderId, Map(), None, None, None, None)

  def apply(shaderId: ShaderId, uniforms: Map[Uniform, ShaderPrimitive]): GLSLShader =
    GLSLShader(shaderId, uniforms, None, None, None, None)

  def apply(shaderId: ShaderId, channel0: AssetName, channel1: AssetName, channel2: AssetName, channel3: AssetName): GLSLShader =
    GLSLShader(shaderId, Map(), Option(channel0), Option(channel1), Option(channel2), Option(channel3))

}

object Material {

  final case class Basic(diffuse: AssetName, alpha: Double) extends StandardMaterial {
    val hash: String =
      diffuse.value + alpha.toString().take(5)

    def toGLSLShader: GLSLShader =
      GLSLShader(
        StandardShaders.Basic,
        Map(Uniform("ALPHA") -> float(alpha)),
        Some(diffuse),
        None,
        None,
        None
      )
  }

  object Basic {
    def apply(diffuse: AssetName): Basic =
      Basic(diffuse, 1.0d)
  }

  // final case class Textured(diffuse: AssetName, isLit: Boolean) extends StandardMaterial {

  //   def withDiffuse(newDiffuse: AssetName): Textured =
  //     this.copy(diffuse = newDiffuse)

  //   def lit: Textured =
  //     this.copy(isLit = true)

  //   def unlit: Textured =
  //     this.copy(isLit = false)

  //   def toGLSLShader: GLSLShader =
  //     GLSLShader(
  //       StandardShaders.Basic,
  //       Map(),
  //       Some(diffuse),
  //       None,
  //       None,
  //       None
  //     )

  //   lazy val hash: String =
  //     diffuse.value + (if (isLit) "1" else "0")
  // }
  // object Textured {
  //   def apply(diffuse: AssetName): Textured =
  //     new Textured(diffuse, false)

  //   def unapply(t: Textured): Option[(AssetName, Boolean)] =
  //     Some((t.diffuse, t.isLit))
  // }

  // final case class Lit(
  //     albedo: AssetName,
  //     emissive: Option[Texture],
  //     normal: Option[Texture],
  //     specular: Option[Texture],
  //     isLit: Boolean
  // ) extends StandardMaterial {

  //   def withAlbedo(newAlbedo: AssetName): Lit =
  //     this.copy(albedo = newAlbedo)

  //   def withEmission(emissiveAssetName: AssetName, amount: Double): Lit =
  //     this.copy(emissive = Some(Texture(emissiveAssetName, amount)))

  //   def withNormal(normalAssetName: AssetName, amount: Double): Lit =
  //     this.copy(normal = Some(Texture(normalAssetName, amount)))

  //   def withSpecular(specularAssetName: AssetName, amount: Double): Lit =
  //     this.copy(specular = Some(Texture(specularAssetName, amount)))

  //   def lit: Lit =
  //     this.copy(isLit = true)

  //   def unlit: Lit =
  //     this.copy(isLit = false)

  //   lazy val hash: String =
  //     albedo.value +
  //       emissive.map(_.hash).getOrElse("_") +
  //       normal.map(_.hash).getOrElse("_") +
  //       specular.map(_.hash).getOrElse("_") +
  //       (if (isLit) "1" else "0")

  //   def toGLSLShader: GLSLShader =
  //     GLSLShader(
  //       StandardShaders.Basic,
  //       Map(),
  //       Some(albedo),
  //       emissive.map(_.assetName),
  //       normal.map(_.assetName),
  //       specular.map(_.assetName)
  //     )
  // }
  // object Lit {
  //   def apply(
  //       albedo: AssetName,
  //       emissive: Option[Texture],
  //       normal: Option[Texture],
  //       specular: Option[Texture]
  //   ): Lit =
  //     new Lit(albedo, emissive, normal, specular, true)

  //   def apply(
  //       albedo: AssetName
  //   ): Lit =
  //     new Lit(albedo, None, None, None, true)

  //   def apply(
  //       albedo: AssetName,
  //       emissive: AssetName
  //   ): Lit =
  //     new Lit(
  //       albedo,
  //       Some(Texture(emissive, 1.0d)),
  //       None,
  //       None,
  //       true
  //     )

  //   def apply(
  //       albedo: AssetName,
  //       emissive: AssetName,
  //       normal: AssetName
  //   ): Lit =
  //     new Lit(
  //       albedo,
  //       Some(Texture(emissive, 1.0d)),
  //       Some(Texture(normal, 1.0d)),
  //       None,
  //       true
  //     )

  //   def apply(
  //       albedo: AssetName,
  //       emissive: AssetName,
  //       normal: AssetName,
  //       specular: AssetName
  //   ): Lit =
  //     new Lit(
  //       albedo,
  //       Some(Texture(emissive, 1.0d)),
  //       Some(Texture(normal, 1.0d)),
  //       Some(Texture(specular, 1.0d)),
  //       true
  //     )

  //   def fromAlbedo(albedo: AssetName): Lit =
  //     new Lit(albedo, None, None, None, true)
  // }

}

// final case class Texture(assetName: AssetName, amount: Double) {
//   def hash: String =
//     assetName.value + amount.toString()
// }
