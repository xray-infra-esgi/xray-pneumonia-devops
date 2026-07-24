package com.xray.detection.consumer

import com.xray.detection.vectorize.Vectorizer

import scala.util.{Failure, Success, Try}


object StreamVectorizer {


  def vectorizeTrain(image: IncomingImage, config: ConsumerConfig): Option[VectorizationResult] = {
    Try {
      val targetLabel = Vectorizer.resolveTargetLabel(image.label, image.fileName)
      val labelId = config.labelMapping.get(targetLabel) match {
        case Some(id) => id
        case None     => throw new IllegalArgumentException(s"Missing label mapping for '$targetLabel'")
      }

      val (width, height, features) =
        Vectorizer.vectorizeBytes(image.content, config.imageSize, config.resizeMode)

      VectorizationResult(
        sourcePath = image.path,
        split = image.split,
        labelId = labelId,
        featureWidth = width,
        featureHeight = height,
        features = features
      )
    } match {
      case Success(result) => Some(result)
      case Failure(e) =>
        println(s"WARN Quarantined unreadable train file ${image.path}: ${e.getMessage}")
        None
    }
  }

  // Infer mode: total function, never throws. A corrupt image yields an
  // ok=false outcome that the batch side routes to quarantine.
  def vectorizeInfer(request: InferRequest, config: ConsumerConfig): InferOutcome = {
    Try {
      Vectorizer.vectorizeBytes(request.content, config.imageSize, config.resizeMode)
    } match {
      case Success((width, height, features)) =>
        InferOutcome(
          sourcePath = request.path,
          fileName = request.fileName,
          depositedAt = request.depositedAt,
          featureWidth = width,
          featureHeight = height,
          features = features,
          ok = true,
          error = ""
        )
      case Failure(e) =>
        InferOutcome(
          sourcePath = request.path,
          fileName = request.fileName,
          depositedAt = request.depositedAt,
          featureWidth = 0,
          featureHeight = 0,
          features = Array.empty[Float],
          ok = false,
          error = Option(e.getMessage).getOrElse(e.getClass.getSimpleName)
        )
    }
  }
}
