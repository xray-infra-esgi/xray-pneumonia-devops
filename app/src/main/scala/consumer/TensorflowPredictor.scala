package com.xray.detection.consumer

import org.tensorflow.SavedModelBundle
import org.tensorflow.ndarray.Shape
import org.tensorflow.ndarray.buffer.DataBuffers
import org.tensorflow.types.TFloat32

// This class knows how to talk to TF Model via Scala/JVM API
//
// Responsibilities:
// 1. Load Tensorflow exported model
// 2. Transform scala feature vectors in TF tensors
// 3. Call model and return score results

// One image's data, ready to be scored: just what the model needs.
final case class ScoreInput(features: Array[Float], height: Int, width: Int)

final class TensorflowPredictor(modelPath: String) extends AutoCloseable {

    // SavedModel serving endpoint (Keras 3 model.export publishes "serve").
    private val ServingEndpoint = "serve"

    // 1. Load model
    private val modelBundle = SavedModelBundle.load(modelPath, ServingEndpoint)
    private val inferenceFunction = modelBundle.function(ServingEndpoint)

    // Predict a batch of images in ONE TensorFlow call: builds a
    // [n, height, width, 1] tensor and returns one score array per image.
    def predictBatch(featuresBatch: Seq[Array[Float]], height: Int, width: Int): Array[Array[Float]] = {
        val n = featuresBatch.length
        val expectedSize = height * width

        val flat = new Array[Float](n * expectedSize)
        featuresBatch.zipWithIndex.foreach { case (features, batchIndex) =>
            require(features.length == expectedSize, s"Expected $expectedSize features, got ${features.length}")
            System.arraycopy(features, 0, flat, batchIndex * expectedSize, expectedSize)
        }

        val inputTensor = TFloat32.tensorOf(
            Shape.of(n.toLong, height.toLong, width.toLong, 1L),
            DataBuffers.of(flat, true, false)
        )

        try {
            val outputTensor = inferenceFunction.call(inputTensor)
            try {
                val outputScoreTensor = outputTensor.asInstanceOf[TFloat32]
                val numClasses = outputScoreTensor.shape().get(1).toInt
                Array.tabulate(n) { i =>
                    Array.tabulate(numClasses) { c =>
                        outputScoreTensor.getFloat(i.toLong, c.toLong)
                    }
                }
            } finally {
                outputTensor.close()
            }
        } finally {
            inputTensor.close()
        }
    }

    // Single-image prediction
    def predict(features: Array[Float], height: Int, width: Int): Array[Float] =
        predictBatch(Seq(features), height, width).head

    override def close(): Unit = {
        modelBundle.close()
    }
}

object TensorflowPredictor {

    private var cached: TensorflowPredictor = null

    def shared(modelPath: String): TensorflowPredictor = synchronized {
        if (cached == null) {
            cached = new TensorflowPredictor(modelPath)
            sys.addShutdownHook(cached.close())
        }
        cached
    }

    def scorePartition(rows: Iterator[ScoreInput], batchSize: Int, modelPath: String): Iterator[(Int, Float)] = {
        val predictor = shared(modelPath)
        rows.grouped(batchSize).flatMap { group =>
            val first = group.head
            val allScores = predictor.predictBatch(group.map(input => input.features), first.height, first.width)
            allScores.map { scores =>
                val best = scores.max
                (scores.indexOf(best), best)
            }
        }
    }
}
