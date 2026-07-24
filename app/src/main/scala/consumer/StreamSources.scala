package com.xray.detection.consumer

import com.xray.detection.vectorize.PathColumns
import org.apache.spark.sql.{Dataset, SparkSession}
import org.apache.spark.sql.functions.{col, size}
import org.apache.spark.sql.streaming.DataStreamReader
import org.apache.spark.sql.types.{BinaryType, LongType, StringType, StructField, StructType, TimestampType}
import org.apache.spark.sql.DataFrame


object StreamSources {

  private val binaryFileSchema = StructType(Seq(
    StructField("path", StringType, nullable = false),
    StructField("modificationTime", TimestampType, nullable = false),
    StructField("length", LongType, nullable = false),
    StructField("content", BinaryType, nullable = true)
  ))

  // Shared reader base: maxFilesPerTrigger <= 0 means unbounded (stress mode).
  private def streamReader(config: ConsumerConfig, spark: SparkSession): DataStreamReader = {
    val imagesOnlyGlobPattern = s"*.{${config.imageExtensions.map(ext => ext.toLowerCase).mkString(",")}}"

    var reader = spark.readStream
      .format("binaryFile")
      .schema(binaryFileSchema)
      .option("recursiveFileLookup", "true")
      .option("pathGlobFilter", imagesOnlyGlobPattern)

    if (config.deleteProcessedFiles) {
      reader = reader.option("cleanSource", "delete")
    }
    if (config.maxFilesPerTrigger > 0) {
      reader = reader.option("maxFilesPerTrigger", config.maxFilesPerTrigger.toString)
    }
    reader
  }

  def readIncomingStream(config: ConsumerConfig, spark: SparkSession): Dataset[IncomingImage] = {
    import spark.implicits._
    val extensionsLower = config.imageExtensions.map(ext => ext.toLowerCase)
    val incomingFilesStream: DataFrame = streamReader(config, spark).load(config.incomingPath)

    PathColumns
      .withImagePathColumns(incomingFilesStream, pathColumn = "path")
      .filter(col("extension").isInCollection(extensionsLower))
      .filter(size(col("segments")) >= 3)
      .filter(col("split").isInCollection(config.splits))
      .filter(col("label").isInCollection(config.categories))
      .select(
        col("path"),
        col("split"),
        col("label"),
        col("fileName"),
        col("content")
      )
      .as[IncomingImage]
  }


  def readInferStream(config: ConsumerConfig, spark: SparkSession): Dataset[InferRequest] = {
    import spark.implicits._
    val extensionsLower = config.imageExtensions.map(ext => ext.toLowerCase)

    PathColumns
      .withImagePathColumns(streamReader(config, spark).load(config.incomingPath), pathColumn = "path")
      .filter(col("extension").isInCollection(extensionsLower))
      .select(
        col("path"),
        col("fileName"),
        col("modificationTime").as("depositedAt"),
        col("content")
      )
      .as[InferRequest]
  }
}
