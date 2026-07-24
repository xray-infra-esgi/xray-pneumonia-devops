package com.xray.detection.producer

import com.xray.detection.router.{AppConfig, CliArgs, Modes}
import org.apache.spark.sql.SparkSession
import pureconfig.generic.auto._

import scala.collection.mutable

object ProducerMain {

  def run(args: Array[String]): Int = {
    if (args.contains("--help") || args.contains("-h")) {
      println(helpText)
      return 0
    }

    val config = parseConfig(args)

    val validationErrors = validate(config)
    if (validationErrors.nonEmpty) {
      println(s"Invalid producer configuration:\n  - ${validationErrors.mkString("\n  - ")}")
      println()
      println(helpText)
      return 1
    }

    var spark: SparkSession = null
    try {
      spark = SparkSession.builder()
        .appName("X-Ray Producer")
        .master(s"local[${config.localThreads}]")
        .config("spark.sql.files.openCostInBytes", config.filesOpenCostBytes.toString)
        .getOrCreate()
      spark.sparkContext.setLogLevel("WARN")

      ProducerPipelineRunner.run(config, spark)
      0
    } catch {
      case e: Exception =>
        val msg = Option(e.getMessage).getOrElse(e.getClass.getSimpleName)
        println(s"Producer error: $msg")
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
      |Producer service (replays the dataset into a watched landing folder):
      |  ./xray produce --mode train --source data/chest_xray --dest data/incoming
      |
      |Options:
      |  --mode <train|infer>   Replay mode (default: train)
      |  --source <path>        Source dataset root (default: data/chest_xray)
      |  --dest <path>          Landing zone watched by the consumer (default: data/incoming, infer: data/incoming-infer)
      |  --batch-size <n>       Files dropped per batch (default: 64)
      |  --interval <ms>        Delay between batches in milliseconds; 0 = dump everything at once (default: 5000)
      |  --no-recursive         Do not descend into subfolders (default: recursive)
      |  --limit <n>            Emit at most n files; 0 or omitted = no limit, full dataset (default: unlimited)
      |  --threads <n>          Number of local Spark threads (default: 2)
      |  --open-cost-bytes <n>  Per-file packing cost in bytes; lower = fewer partitions (default: 65536)
      |  --help                 Show this help message
      |""".stripMargin.trim

  // Shape of the "producer" section of conf/application.conf.
  private final case class ProducerSection(
    source: String = "data/chest_xray",
    batchSize: Int = 64,
    intervalMs: Long = 1000L,
    localThreads: Int = 2,
    filesOpenCostBytes: Long = 65536L,
    trainDest: String = "data/incoming",
    inferDest: String = "data/incoming-infer"
  )

  private def parseConfig(args: Array[String]): ProducerConfig = {
    def cliValue(flag: String): Option[String] = CliArgs.optionValue(args, flag)

    val mode = cliValue("--mode").getOrElse(Modes.Train).toLowerCase

    // Layer 2: the deployment file overrides the code defaults (layer 1).
    val section = AppConfig.section("producer", ProducerSection())
    val defaultDest = if (mode == Modes.Infer) section.inferDest else section.trainDest

    // Layer 3: per-invocation CLI flags override everything.
    ProducerConfig(
      sourcePath = cliValue("--source").getOrElse(section.source),
      destPath = cliValue("--dest").getOrElse(defaultDest),
      batchSize = cliValue("--batch-size").map(v => v.toInt).getOrElse(section.batchSize),
      intervalMillis = cliValue("--interval").map(v => v.toLong).getOrElse(section.intervalMs),
      recursive = !args.contains("--no-recursive"),
      mode = mode,
      localThreads = cliValue("--threads").map(v => v.toInt).getOrElse(section.localThreads),
      filesOpenCostBytes = cliValue("--open-cost-bytes").map(v => v.toLong).getOrElse(section.filesOpenCostBytes),
      limit = cliValue("--limit").map(v => v.toInt).filter(v => v > 0)
    )
  }

  // Accumulates EVERY problem instead of stopping at the first one.
  private def validate(config: ProducerConfig): Seq[String] = {
    val errors = mutable.ArrayBuffer[String]()

    if (config.mode != Modes.Train && config.mode != Modes.Infer) {
      errors += s"Unknown mode '${config.mode}' (expected: train or infer)"
    }
    if (config.batchSize <= 0) {
      errors += s"--batch-size must be > 0 (received: ${config.batchSize})"
    }
    if (config.intervalMillis < 0) {
      errors += s"--interval must be >= 0 (received: ${config.intervalMillis})"
    }
    if (config.filesOpenCostBytes <= 0) {
      errors += s"--open-cost-bytes must be > 0 (received: ${config.filesOpenCostBytes})"
    }

    errors.toSeq
  }
}