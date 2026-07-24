package com.xray.detection.vectorize

import com.xray.detection.router.AppConfig
import pureconfig.generic.auto._

final case class VectorizationSettings(
  imageSize: Int = 128,
  resizeMode: String = "fit",
  imageExtensions: Seq[String] = Seq("jpg", "jpeg", "png")
)

object VectorizationSettings {
  val shared: VectorizationSettings =
    AppConfig.section("vectorization", VectorizationSettings())
}
