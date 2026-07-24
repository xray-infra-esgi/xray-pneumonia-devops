# Input shape de TensorflowPredictor

## Idée

Le predictor reçoit des features Scala sous forme de vecteur aplati, mais le modèle attend un tenseur 4D de la forme :

```text
[batchSize, height, width, channels]
```

Dans le projet, cela correspond à :

```text
[1, height, width, 1]
```

avec :

- `batchSize = 1` car on prédit une image à la fois
- `height` et `width` qui viennent de `featureHeight` et `featureWidth`
- `channels = 1` car les images sont traitées en niveaux de gris

## Entrée

Le parquet fournit :

- `features: Seq[Float]`
- `featureHeight`
- `featureWidth`

## Forme attendue par le modèle

Le modèle TensorFlow attend :

```text
[batchSize, height, width, channels]
```

Dans le projet :

- batch = 1
- channels = 1

Donc la forme d'entrée est :

```text
[1, height, width, 1]
```

## Cas concret du projet

Le code fait :

```scala
val inputArray = NdArrays.ofFloats(Shape.of(1L, height.toLong, width.toLong, 1L))
```

Puis il place les pixels dans ce tenseur :

```scala
val y = pixelIndex / width
val x = pixelIndex % width
inputArray.setFloat(pixelValue, 0L, y.toLong, x.toLong, 0L)
```

## Pourquoi c'est utile

Cette étape relie :

- la représentation plate des features dans le parquet
- à la forme attendue par le modèle TensorFlow

## Où c'est utilisé dans le projet

- [TensorflowPredictor.scala:28](../../../src/main/scala/inference/TensorflowPredictor.scala#L28)
- [TensorflowPredictor.scala:35](../../../src/main/scala/inference/TensorflowPredictor.scala#L35)
- [TensorflowPredictor.scala:37](../../../src/main/scala/inference/TensorflowPredictor.scala#L37)

## Points de syntaxe à retenir

- `Shape.of(...)` décrit les dimensions du tenseur
- `pixelIndex / width` donne `y`
- `pixelIndex % width` donne `x`
- on reconstruit ici une image 2D à partir d'un vecteur plat

## Code complet exécutable

```scala
import org.tensorflow.ndarray.NdArrays
import org.tensorflow.ndarray.Shape

object TensorflowPredictorInputShapeExample {
  def main(args: Array[String]): Unit = {
    val features = Seq(10f, 20f, 30f, 40f)
    val height = 2
    val width = 2
    val inputArray = NdArrays.ofFloats(Shape.of(1L, height.toLong, width.toLong, 1L))

    features.zipWithIndex.foreach { case (pixelValue, pixelIndex) =>
      val y = pixelIndex / width
      val x = pixelIndex % width
      val batchIndex = 0L
      val channelIndex = 0L

      inputArray.setFloat(pixelValue, batchIndex, y.toLong, x.toLong, channelIndex)

      println(s"pixelValue=$pixelValue goes to tensor[0][$y][$x][0]")
    }

    println()
    println("Tensor shape built: [1, 2, 2, 1]")
  }
}
```
