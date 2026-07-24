package com.xray.detection.vectorize

import com.sksamuel.scrimage.ImmutableImage

import java.awt.Color


object Vectorizer {

  // Single source of truth for the class labels: batch preprocessing,
  val defaultLabelMapping: Map[String, Int] = Map(
    "NORMAL" -> 0,
    "PNEUMONIA_BACTERIA" -> 1,
    "PNEUMONIA_VIRUS" -> 2
  )

  // Resolve the final target label from the raw folder label and the file name
  // (the pneumonia subtype travels in the file name in this dataset).
  def resolveTargetLabel(rawLabel: String, fileName: String): String = {
    rawLabel match {

      case "NORMAL" =>
        "NORMAL"

      case "PNEUMONIA" =>
        val fileNameLower = fileName.trim.toLowerCase

        if (fileNameLower.contains("bacteria")) {
          "PNEUMONIA_BACTERIA"
        } else if (fileNameLower.contains("virus")) {
          "PNEUMONIA_VIRUS"
        } else {
          throw new IllegalArgumentException(
            s"Unable to resolve pneumonia subtype from filename : $fileName"
          )
        }

      case other =>
        throw new IllegalArgumentException(
          s"Unsupported raw label '$other' for file: $fileName"
        )
    }
  }

  def applyResizeMode(image: ImmutableImage, targetSize: (Int, Int), resizeMode: String): ImmutableImage = {
    val (targetWidth, targetHeight) = targetSize
    val mode = if (resizeMode == null) "" else resizeMode.trim.toLowerCase

    if (mode == "scale") {
      image.scaleTo(targetWidth, targetHeight)
    } else if (mode == "cover") {
      image.cover(targetWidth, targetHeight)
    } else {
      image.fit(targetWidth, targetHeight, Color.BLACK)
    }
  }

  def extractFeatures(image: ImmutableImage): Array[Float] = {
    image.pixels().map { pixel =>
      (
        0.299f * pixel.red.toFloat +
        0.587f * pixel.green.toFloat +
        0.114f * pixel.blue.toFloat
      ) / 255.0f
    }
  }

  // Decode + resize + extract in one call: the streaming-side unit of work.
  // Returns (width, height, features).
  def vectorizeBytes(content: Array[Byte], imageSize: Int, resizeMode: String): (Int, Int, Array[Float]) = {
    val loaded = ImmutableImage.loader().fromBytes(content)
    val resized = applyResizeMode(loaded, (imageSize, imageSize), resizeMode)
    (resized.width, resized.height, extractFeatures(resized))
  }
}
