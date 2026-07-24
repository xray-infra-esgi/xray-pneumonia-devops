package com.xray.detection.producer

import com.xray.detection.vectorize.VectorizationSettings

final case class ProducerConfig(
  sourcePath: String,
  destPath: String,
  batchSize: Int,
  intervalMillis: Long,
  recursive: Boolean,
  mode: String,
  localThreads: Int = 2,
  filesOpenCostBytes: Long = 65536L,
  limit: Option[Int] = None,
  stagingDirName: String = "_staging",
  splits: Seq[String] = Seq("train", "test", "val"),
  categories: Seq[String] = Seq("NORMAL", "PNEUMONIA"),
  imageExtensions: Seq[String] = VectorizationSettings.shared.imageExtensions
)