package com.xray.detection.consumer

import org.apache.spark.sql.SparkSession
import org.apache.spark.sql.streaming.{StreamingQueryListener, StreamingQueryProgress}
import org.apache.spark.sql.streaming.StreamingQueryListener._

import java.io.{File, FileWriter}
import java.time.Instant


object StreamingMetricsLogger {

  private val CsvHeader =
    "timestamp,batchId,numInputRows,inputRowsPerSecond,processedRowsPerSecond,batchDurationMs"

  def register(spark: SparkSession, config: ConsumerConfig): Unit = {
    new File(config.metricsDir).mkdirs()
    val metricsFile = new File(config.metricsDir, s"consume-${config.mode}.csv")
    spark.streams.addListener(new MetricsListener(metricsFile))
  }

  private final class MetricsListener(metricsFile: File) extends StreamingQueryListener {
    override def onQueryStarted(event: QueryStartedEvent): Unit = ()
    override def onQueryTerminated(event: QueryTerminatedEvent): Unit = ()

    override def onQueryProgress(event: QueryProgressEvent): Unit = {
      val progress = event.progress
      if (progress.numInputRows > 0) {
        printSummary(progress)
        appendCsvRow(progress)
      }
    }

    private def printSummary(progress: StreamingQueryProgress): Unit =
      println(
        f"micro-batch ${progress.batchId}: ${progress.numInputRows} file(s) processed " +
        f"(${progress.processedRowsPerSecond}%.1f img/s, batch ${batchDurationMs(progress)} ms)"
      )

    private def appendCsvRow(progress: StreamingQueryProgress): Unit = {
      val writeHeader = !metricsFile.exists()
      val writer = new FileWriter(metricsFile, true) // true = append, never overwrite
      try {
        if (writeHeader) writer.write(CsvHeader + "\n")
        writer.write(
          s"${Instant.now()},${progress.batchId},${progress.numInputRows}," +
          f"${progress.inputRowsPerSecond}%.2f,${progress.processedRowsPerSecond}%.2f,${batchDurationMs(progress)}%n"
        )
      } finally {
        writer.close()
      }
    }

    private def batchDurationMs(progress: StreamingQueryProgress): Long =
      Option(progress.durationMs.get("triggerExecution")).map(_.toLong).getOrElse(0L)
  }
}
