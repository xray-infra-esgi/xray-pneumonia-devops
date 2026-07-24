# Pourquoi la shape de sortie a changé

## Idée

Le projet est passé :

- d'une classification binaire
- à une classification à 3 classes

La shape de sortie du modèle a donc changé.

## Avant

Avec une classification binaire, le modèle pouvait renvoyer :

```text
Output shape: (None, 1)
```

Cela correspond à :

- un seul score par image

Puis le code d'inférence faisait une logique du type :

```scala
if (score >= 0.5f) 1 else 0
```

## Maintenant

Avec 3 classes, le modèle renvoie :

```text
Output shape: (None, 3)
```

Cela correspond à :

- un score pour `NORMAL`
- un score pour `PNEUMONIA_BACTERIA`
- un score pour `PNEUMONIA_VIRUS`

## Transformation

Le predictor lit maintenant les 3 valeurs :

```scala
Array(
  outputScoreTensor.getFloat(0, 0),
  outputScoreTensor.getFloat(0, 1),
  outputScoreTensor.getFloat(0, 2)
)
```

Puis le runner choisit la classe prédite avec :

```scala
scores.zipWithIndex.maxBy { case (score, _) => score }
```

## Sortie

On ne travaille plus avec :

- un score unique

mais avec :

- un tableau de 3 scores
- puis un `predictedLabelId`

## Pourquoi c'est utile

Cette fiche relie :

- le notebook TensorFlow
- l'export `SavedModel`
- l'inférence Scala

## Où c'est utilisé dans le projet

- [TensorflowPredictor.scala:28](../../../src/main/scala/inference/TensorflowPredictor.scala#L28)
- [InferencePipelineRunner.scala:47](../../../src/main/scala/inference/InferencePipelineRunner.scala#L47)

## Points de syntaxe à retenir

- `(None, 1)` signifie : une sortie par exemple
- `(None, 3)` signifie : trois sorties par exemple
- `Array[Float]` est ici un petit vecteur de scores numériques
- `argmax` correspond ici à `maxBy` sur les scores

## Code complet exécutable

```scala
object WhyOutputShapeChangedExample {
  def main(args: Array[String]): Unit = {
    val binaryScore = 0.72f
    val binaryPredictedLabelId =
      if (binaryScore >= 0.5f) 1 else 0

    val multiclassScores = Array(0.10f, 0.25f, 0.65f)
    val multiclassPredictedLabelId =
      multiclassScores.zipWithIndex.maxBy { case (score, _) => score }._2

    println("Binary case:")
    println(s"score            = $binaryScore")
    println(s"predictedLabelId = $binaryPredictedLabelId")

    println()
    println("Multiclass case:")
    println(s"scores           = ${multiclassScores.mkString("Array(", ", ", ")")}")
    println(s"predictedLabelId = $multiclassPredictedLabelId")
  }
}
```
