package com.xray.detection.consumer

import java.sql.Timestamp

final case class IncomingImage(
  path: String,
  split: String,
  label: String,
  fileName: String,
  content: Array[Byte]
)


final case class InferRequest(
  path: String,
  fileName: String,
  depositedAt: Timestamp,
  content: Array[Byte]
)


final case class InferOutcome(
  sourcePath: String,
  fileName: String,
  depositedAt: Timestamp,
  featureWidth: Int,
  featureHeight: Int,
  features: Array[Float],
  ok: Boolean,
  error: String
)

// Final prediction record (and the dashboard).
final case class StreamPrediction(
  sourcePath: String,
  fileName: String,
  predictedLabelId: Int,
  predictedLabel: String,
  score: Float,
  depositedAt: Timestamp,
  processedAt: Timestamp
)

// Quarantined input (unreadable image, wrong format...).
final case class RejectedFile(
  sourcePath: String,
  fileName: String,
  error: String,
  depositedAt: Timestamp,
  processedAt: Timestamp
)

// Vectorized labeled image (train mode), appended to the feature store for training.
final case class VectorizationResult(
  sourcePath: String,
  split: String,
  labelId: Int,
  featureWidth: Int,
  featureHeight: Int,
  features: Array[Float]
)
