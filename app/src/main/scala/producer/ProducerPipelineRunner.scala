package com.xray.detection.producer

import com.xray.detection.router.Modes
import com.xray.detection.vectorize.PathColumns
import org.apache.hadoop.conf.Configuration
import org.apache.hadoop.fs.{FileSystem, FileUtil, Path}
import org.apache.spark.sql.{DataFrame, Row, SparkSession}
import org.apache.spark.sql.functions.{col, size}

import scala.jdk.CollectionConverters._

// Stateless producer orchestrator.
// Responsibilities:
// 1. Enumerate the source images recursively (Spark).
// 2. Replay them into the destination folder in batches, at a fixed cadence.
// 3. Deposit each file atomically (staging dir + atomic rename)
object ProducerPipelineRunner {

  // One image to emit: its source path (as a URI) + its relative target path under the destination folder.
  private final case class Emission(sourcePath: String, relativeTarget: String)

  def run(config: ProducerConfig, spark: SparkSession): Unit = {
    val files = enumerateSource(config, spark)

    val available = files.count()
    val total = config.limit.map(max => math.min(max.toLong, available)).getOrElse(available)
    if (total == 0L) {
      throw new RuntimeException(s"No images found under ${config.sourcePath}")
    }

    val hadoopConf = spark.sparkContext.hadoopConfiguration
    val destRoot = new Path(config.destPath)
    val stagingRoot = new Path(destRoot, config.stagingDirName)
    val destFs = destRoot.getFileSystem(hadoopConf)
    val sourceFs = new Path(config.sourcePath).getFileSystem(hadoopConf)
    // The local Hadoop filesystem writes .crc sidecar files next to every file
    // by default; disable that so the landing zone holds the images only
    // (HDFS keeps its integrity checks internally either way).
    destFs.setWriteChecksum(false)
    destFs.mkdirs(stagingRoot)

    println(
      s"Producer starting: $total files, " +
      s"batch-size=${config.batchSize}, interval=${config.intervalMillis}ms, mode=${config.mode}"
    )

    val emissions = files.toLocalIterator().asScala.map(row => toEmission(row, config))
    val selected = config.limit match {
      case Some(max) => emissions.take(max)
      case None      => emissions
    }

    var emitted = 0
    selected.grouped(config.batchSize).foreach { batch =>
      batch.foreach { emission =>
        depositAtomically(emission, sourceFs, destFs, destRoot, stagingRoot, hadoopConf)
        emitted += 1
      }
      println(s"  emitted $emitted/$total")
      if (config.intervalMillis > 0 && emitted < total) {
        Thread.sleep(config.intervalMillis)
      }
    }

    println(s"Producer done: $emitted files dropped into ${config.destPath}")
  }

  private def enumerateSource(config: ProducerConfig, spark: SparkSession): DataFrame = {
    val extensionsLower = config.imageExtensions.map(ext => ext.toLowerCase)

    val allFiles = PathColumns.withImagePathColumns(
      df = spark.read
        .format("binaryFile")
        .option("recursiveFileLookup", config.recursive.toString)
        .load(config.sourcePath)
        .select(col("path").as("sparkPath")),
      pathColumn = "sparkPath"
    ).filter(col("extension").isInCollection(extensionsLower))

    val df =
      if (config.mode == Modes.Train)
        allFiles
          .filter(size(col("segments")) >= 3)
          .filter(col("split").isInCollection(config.splits))
          .filter(col("label").isInCollection(config.categories))
      else allFiles

    df.select("sparkPath", "split", "label", "fileName")
  }

  private def toEmission(row: Row, config: ProducerConfig): Emission = {
    val relativeTarget =
      if (config.mode == Modes.Train) {
        s"${row.getAs[String]("split")}/${row.getAs[String]("label")}/${row.getAs[String]("fileName")}"
      } else {
        row.getAs[String]("fileName")
      }
    Emission(row.getAs[String]("sparkPath"), relativeTarget)
  }

  private def depositAtomically(
    emission: Emission,
    sourceFs: FileSystem,
    destFs: FileSystem,
    destRoot: Path,
    stagingRoot: Path,
    hadoopConf: Configuration
  ): Unit = {
    val finalPath = new Path(destRoot, emission.relativeTarget)
    val stagingPath = new Path(stagingRoot, emission.relativeTarget)

    destFs.mkdirs(finalPath.getParent)
    destFs.mkdirs(stagingPath.getParent)

    FileUtil.copy(
      sourceFs, new Path(emission.sourcePath),
      destFs, stagingPath,
      false,  // deleteSource: leave the source dataset untouched
      true,   // overwrite: re-runs replace staging leftovers
      hadoopConf
    )

    if (destFs.exists(finalPath)) {
      destFs.delete(finalPath, false)
    }
    if (!destFs.rename(stagingPath, finalPath)) {
      throw new java.io.IOException(s"Atomic rename failed: $stagingPath -> $finalPath")
    }
  }
}
