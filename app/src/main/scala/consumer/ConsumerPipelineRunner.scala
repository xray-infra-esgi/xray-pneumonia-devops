package com.xray.detection.consumer

import com.xray.detection.router.Modes
import org.apache.hadoop.fs.{Path => HadoopPath}
import org.apache.spark.sql.{Dataset, SparkSession}
import org.apache.spark.sql.streaming.{StreamingQuery, Trigger}

import java.sql.Timestamp

// Streaming consumer orchestrator (Structured Streaming), two modes:
// - train: watch the labeled landing zone, vectorize, append to the feature store.
// - infer: watch the flat (unlabeled) landing zone, vectorize, predict, append
//   predictions (healthy files) and quarantine records (unreadable files).
object ConsumerPipelineRunner {

  def run(config: ConsumerConfig, spark: SparkSession): Unit = {
    StreamingMetricsLogger.register(spark, config)

    val incomingPath = new HadoopPath(config.incomingPath)
    incomingPath.getFileSystem(spark.sparkContext.hadoopConfiguration).mkdirs(incomingPath)

    // ~5-8 s of cold start paid upfront at start time instead of first inference
    // cached singleton predictor reused in local mode as 1 single JVM driver
    if (config.mode == Modes.Infer) {
      warmUpInferModel(config, spark)
    }

    val query = config.mode match {
      case Modes.Infer => startInferQuery(config, spark)
      case _           => startTrainQuery(config, spark)
    }

    val maxFilesLabel =
      if (config.maxFilesPerTrigger > 0) config.maxFilesPerTrigger.toString else "unbounded"
    println(
      s"Consumer started (mode=${config.mode}): watching ${config.incomingPath} " +
      s"(trigger=${config.triggerSeconds}s, max-files=$maxFilesLabel) -> ${config.outputPath}"
    )

    config.durationSeconds match {
      case Some(seconds) =>
        val terminated = query.awaitTermination(seconds * 1000)
        if (!terminated) {
          query.stop()
          println(s"Duration of ${seconds}s elapsed: consumer stopped gracefully")
        }
      case None =>
        query.awaitTermination()
    }
  }

  // Pre-loads the model and warms the write path at startup
  private def warmUpInferModel(config: ConsumerConfig, spark: SparkSession): Unit = {
    import spark.implicits._
    val pixels = config.imageSize * config.imageSize

    // (1) Load the model + compile its TF graph (one blank inference).
    println(s"Loading inference model: ${config.modelPath} ...")
    val predictor = TensorflowPredictor.shared(config.modelPath)
    predictor.predictBatch(Seq(Array.fill(pixels)(0.0f)), config.imageSize, config.imageSize)

    // (2) Warm the rest of the write path (Spark codegen, JIT, parquet write) by
    //     pushing one synthetic row through the real writer.
    val warmConfig = config.copy(
      outputPath = config.outputPath + ".warmup",
      rejectedOutputPath = config.rejectedOutputPath + ".warmup"
    )
    val labelNames: Map[Int, String] = config.labelMapping.map { case (name, id) => (id, name) }
    val dummy = Seq(
      InferOutcome("warmup", "warmup.jpeg", new Timestamp(0L),
        config.imageSize, config.imageSize, Array.fill(pixels)(0.0f), ok = true, error = "")
    )
    InferBatchWriter.predictAndWrite(dummy.toDS(), warmConfig, labelNames)

    deleteDir(warmConfig.outputPath, spark)
    deleteDir(warmConfig.rejectedOutputPath, spark)
    println("Inference path warmed up.")
  }

  private def deleteDir(path: String, spark: SparkSession): Unit = {
    val p = new HadoopPath(path)
    val fs = p.getFileSystem(spark.sparkContext.hadoopConfiguration)
    if (fs.exists(p)) {
      fs.delete(p, true)
    }
  }

  // Train pipeline: read -> vectorize (skip unreadable) -> append to the feature store.
  private def startTrainQuery(config: ConsumerConfig, spark: SparkSession): StreamingQuery = {
    import spark.implicits._

    StreamSources.readIncomingStream(config, spark)
      .flatMap(image => StreamVectorizer.vectorizeTrain(image, config))
      .writeStream
      .format("parquet")
      .option("path", config.outputPath)
      .option("checkpointLocation", config.checkpointPath)
      .outputMode("append")
      .trigger(Trigger.ProcessingTime(s"${config.triggerSeconds} seconds"))
      .start()
  }

  // Infer pipeline: read -> vectorize (total) -> foreachBatch (predict + quarantine).
  private def startInferQuery(config: ConsumerConfig, spark: SparkSession): StreamingQuery = {
    import spark.implicits._

    // Reverse mapping for human-readable predictions (0 -> "NORMAL", ...).
    val labelNames: Map[Int, String] = config.labelMapping.map { case (name, id) => (id, name) }

    val outcomes = StreamSources.readInferStream(config, spark)
      .map(request => StreamVectorizer.vectorizeInfer(request, config))

    val writeBatch: (Dataset[InferOutcome], Long) => Unit =
      (batch, batchId) => InferBatchWriter.predictAndWrite(batch, config, labelNames)

    outcomes.writeStream
      .foreachBatch(writeBatch)
      .option("checkpointLocation", config.checkpointPath)
      .trigger(Trigger.ProcessingTime(s"${config.triggerSeconds} seconds"))
      .start()
  }
}
