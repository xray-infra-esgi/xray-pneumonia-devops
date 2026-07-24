package com.xray.detection.consumer

import com.xray.detection.router.{AppConfig, CliArgs, Modes}
import org.apache.spark.sql.SparkSession
import pureconfig.generic.auto._

import java.nio.file.{Files, Paths}
import scala.collection.mutable

object ConsumerMain {

  def run(args: Array[String]): Int = {
    if (args.contains("--help") || args.contains("-h")) {
      println(helpText)
      return 0
    }

    val config = parseConfig(args)

    val validationErrors = validate(config)
    if (validationErrors.nonEmpty) {
      println(s"Invalid consumer configuration:\n  - ${validationErrors.mkString("\n  - ")}")
      println()
      println(helpText)
      return 1
    }

    var spark: SparkSession = null
    try {
      spark = SparkSession.builder()
        .appName("X-Ray Consumer")
        .master(s"local[${config.localThreads}]")
        .getOrCreate()
      spark.sparkContext.setLogLevel("WARN")

      ConsumerPipelineRunner.run(config, spark)
      0
    } catch {
      case e: Exception =>
        val msg = Option(e.getMessage).getOrElse(e.getClass.getSimpleName)
        println(s"Consumer error: $msg")
        e.printStackTrace()
        1
    } finally {
      if (spark != null) {
        spark.stop()
        println("Spark session stopped")
      }
    }
  }

  private val helpText: String =
    """
      |Consumer service (Structured Streaming: watches the landing folder and vectorizes):
      |  ./xray consume --mode train
      |
      |Options:
      |  --mode <train|infer>     Consumer mode (default: train)
      |  --incoming <path>        Watched landing zone (default: data/incoming, infer: data/incoming-infer)
      |  --output <path>          Sink path (default: output/features-stream.parquet, infer: output/predictions-stream.parquet)
      |  --model <path>           SavedModel dir, infer mode only (default: ml/artifacts/models/saved_model)
      |  --predict-batch-size <n> Images per TensorFlow call, infer mode only (default: 32)
      |  --keep-processed-files   Keep processed files in the landing zone (default: delete them)
      |  --checkpoint <path>      Checkpoint dir (default: output/checkpoints/consume-<mode>)
      |  --trigger <seconds>      Micro-batch cadence (default: 5)
      |  --max-files <n>          Max files per micro-batch, backpressure; 0 = unbounded, stress mode (default: 32)
      |  --duration <seconds>     Stop after n seconds (default: run until Ctrl+C)
      |  --threads <n>            Number of local Spark threads (default: 2)
      |  --help                   Show this help message
      |""".stripMargin.trim

  // Shape of the "consumer" section of conf/application.conf. 
  private final case class ModePaths(incoming: String, output: String, checkpoint: String)

  private final case class ConsumerSection(
    triggerSeconds: Int = 5,
    maxFilesPerTrigger: Int = 32,
    predictBatchSize: Int = 32,
    modelPath: String = "ml/artifacts/models/saved_model",
    deleteProcessedFiles: Boolean = true,
    localThreads: Int = 2,
    train: ModePaths = ModePaths(
      "data/incoming", "output/features-stream.parquet", "output/checkpoints/consume-train"),
    infer: ModePaths = ModePaths(
      "data/incoming-infer", "output/predictions-stream.parquet", "output/checkpoints/consume-infer")
  )

  private def parseConfig(args: Array[String]): ConsumerConfig = {
    def cliValue(flag: String): Option[String] = CliArgs.optionValue(args, flag)

    val mode = cliValue("--mode").getOrElse(Modes.Train).toLowerCase

    // Layer 2: the deployment file overrides the code defaults (layer 1).
    val cfgSection = AppConfig.section("consumer", ConsumerSection())
    val paths = if (mode == Modes.Infer) cfgSection.infer else cfgSection.train

    // Layer 3: per-invocation CLI flags override everything.
    ConsumerConfig(
      incomingPath = cliValue("--incoming").getOrElse(paths.incoming),
      outputPath = cliValue("--output").getOrElse(paths.output),
      checkpointPath = cliValue("--checkpoint").getOrElse(paths.checkpoint),
      triggerSeconds = cliValue("--trigger").map(_.toInt).getOrElse(cfgSection.triggerSeconds),
      maxFilesPerTrigger = cliValue("--max-files").map(_.toInt).getOrElse(cfgSection.maxFilesPerTrigger),
      mode = mode,
      modelPath = cliValue("--model").getOrElse(cfgSection.modelPath),
      predictBatchSize = cliValue("--predict-batch-size").map(_.toInt).getOrElse(cfgSection.predictBatchSize),
      deleteProcessedFiles =
        if (args.contains("--keep-processed-files")) false else cfgSection.deleteProcessedFiles,
      localThreads = cliValue("--threads").map(_.toInt).getOrElse(cfgSection.localThreads),
      durationSeconds = cliValue("--duration").map(_.toLong)
    )
  }

  private def validate(config: ConsumerConfig): Seq[String] = {
    val errors = mutable.ArrayBuffer[String]()

    if (config.mode != Modes.Train && config.mode != Modes.Infer) {
      errors += s"Unknown mode '${config.mode}' (expected: train or infer)"
    }
    if (config.mode == Modes.Infer && !Files.exists(Paths.get(config.modelPath))) {
      errors += s"Model not found: ${config.modelPath} (train and export a model first, or pass --model)"
    }
    if (config.triggerSeconds <= 0) {
      errors += s"--trigger must be > 0 (received: ${config.triggerSeconds})"
    }
    if (config.maxFilesPerTrigger < 0) {
      errors += s"--max-files must be >= 0, 0 meaning unbounded (received: ${config.maxFilesPerTrigger})"
    }
    if (config.predictBatchSize <= 0) {
      errors += s"--predict-batch-size must be > 0 (received: ${config.predictBatchSize})"
    }
    config.durationSeconds.foreach { duration =>
      if (duration <= 0) {
        errors += s"--duration must be > 0 (received: $duration)"
      }
    }

    errors.toSeq
  }
}