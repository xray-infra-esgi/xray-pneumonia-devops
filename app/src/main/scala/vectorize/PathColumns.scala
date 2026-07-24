package com.xray.detection.vectorize

import org.apache.spark.sql.DataFrame
import org.apache.spark.sql.functions.{col, element_at, lower, translate, split, substring_index }

// The one place that knows how split/label/fileName/extension are derived from
// an image path (dataset layout: .../split/label/fileName.ext). Shared by the
// batch preprocessing, the producer and the streaming consumer.
object PathColumns {

  def withImagePathColumns(df: DataFrame, pathColumn: String): DataFrame = df
    .withColumn("_pathNorm", translate(col(pathColumn), "\\", "/"))
    .withColumn("segments", split(col("_pathNorm"), "/"))
    .withColumn("fileName", element_at(col("segments"), -1))
    .withColumn("label", element_at(col("segments"), -2))
    .withColumn("split", element_at(col("segments"), -3))
    .withColumn("extension", lower(substring_index(col("fileName"), ".", -1)))
}
