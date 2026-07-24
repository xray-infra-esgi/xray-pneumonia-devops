package com.xray.detection.consumer

import org.apache.spark.sql.Dataset

import java.sql.Timestamp


object InferBatchWriter {

  def predictAndWrite(
    batch: Dataset[InferOutcome],
    config: ConsumerConfig,
    labelNames: Map[Int, String]
  ): Unit = {
    import batch.sparkSession.implicits._

    // The batch feeds two sinks: persist used so that vectorization is not recomputed.
    batch.persist()
    try {
      val predictions = batch.filter(outcome => outcome.ok).mapPartitions { rows =>
        val rowSeq = rows.toSeq
        val inputs = rowSeq.iterator.map(o => ScoreInput(o.features, o.featureHeight, o.featureWidth))
        val scored = TensorflowPredictor.scorePartition(inputs, config.predictBatchSize, config.modelPath)
        rowSeq.iterator.zip(scored).map { case (row, (predictedLabelId, predictedScore)) =>
          StreamPrediction(
            sourcePath = row.sourcePath,
            fileName = row.fileName,
            predictedLabelId = predictedLabelId,
            predictedLabel = labelNames.getOrElse(predictedLabelId, "UNKNOWN"),
            score = predictedScore,
            depositedAt = row.depositedAt,
            processedAt = new Timestamp(System.currentTimeMillis())
          )
        }
      }

      predictions.write.mode("append").parquet(config.outputPath)

      val rejected = batch.filter(outcome => !outcome.ok)
      val rejectedCount = rejected.count()
      if (rejectedCount > 0) {
        rejected
          .map { outcome =>
            RejectedFile(
              sourcePath = outcome.sourcePath,
              fileName = outcome.fileName,
              error = outcome.error,
              depositedAt = outcome.depositedAt,
              processedAt = new Timestamp(System.currentTimeMillis())
            )
          }
          .write.mode("append").parquet(config.rejectedOutputPath)
        println(s"WARN $rejectedCount file(s) quarantined to ${config.rejectedOutputPath}")
      }
    } finally {
      batch.unpersist()
    }
  }
}
