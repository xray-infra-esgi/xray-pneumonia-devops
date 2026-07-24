package com.xray.detection.consumer

import com.xray.detection.vectorize.{VectorizationSettings, Vectorizer}

final case class ConsumerConfig (
    incomingPath: String,
    outputPath: String,
    checkpointPath: String,
    mode: String,
    triggerSeconds: Int,
    maxFilesPerTrigger: Int,
    modelPath: String,
    predictBatchSize: Int,
    deleteProcessedFiles: Boolean,
    rejectedOutputPath: String = "output/rejected-stream.parquet",
    metricsDir: String = "output/metrics",
    localThreads: Int,
    durationSeconds: Option[Long] = None,
    splits: Seq[String] = Seq("train", "test", "val"),
    categories: Seq[String] = Seq("NORMAL", "PNEUMONIA"),
    imageExtensions: Seq[String] = VectorizationSettings.shared.imageExtensions,
    imageSize: Int = VectorizationSettings.shared.imageSize,
    resizeMode: String = VectorizationSettings.shared.resizeMode,
    labelMapping: Map[String, Int] = Vectorizer.defaultLabelMapping
)