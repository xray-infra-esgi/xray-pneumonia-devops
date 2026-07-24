# FeatureRow vers PredictionRow

## Idée

Le pipeline d'inférence prend une ligne de features en entrée et produit une ligne de prédiction en sortie.

## Entrée

Une ligne d'entrée a la forme :

```scala
FeatureRow(
  split = "test",
  labelId = 2,
  featureWidth = 128,
  featureHeight = 128,
  features = Seq(...)
)
```

## Transformation

Le runner fait essentiellement :

1. appeler `predictor.predict(...)`
2. récupérer les scores des classes
3. choisir la classe du score maximal
4. construire `PredictionRow`

Extrait proche du projet :

```scala
val scores = predictor.predict(
  row.features,
  row.featureHeight,
  row.featureWidth
)

val (predictedScore, predictedLabelId) =
  scores.zipWithIndex.maxBy { case (score, _) => score }

PredictionRow(
  split = row.split,
  labelId = row.labelId,
  predictedLabelId = predictedLabelId,
  score = predictedScore
)
```

## Sortie

```scala
PredictionRow(
  split = "test",
  labelId = 2,
  predictedLabelId = 2,
  score = 0.75f
)
```

## Pourquoi c'est utile

Cette fiche montre le flux principal du pipeline d'inférence.

## Où c'est utilisé dans le projet

- [InferenceSchemas.scala:3](../../../src/main/scala/inference/InferenceSchemas.scala#L3)
- [InferencePipelineRunner.scala:20](../../../src/main/scala/inference/InferencePipelineRunner.scala#L20)
- [InferencePipelineRunner.scala:45](../../../src/main/scala/inference/InferencePipelineRunner.scala#L45)

## Points de syntaxe à retenir

- `FeatureRow` représente une ligne de features lue depuis le parquet
- `PredictionRow` représente une ligne de sortie du pipeline d'inférence
- `val (...) = ...` permet de décomposer directement le résultat d'un tuple

## Code complet exécutable

```scala
object FeatureRowToPredictionRowExample {
  final case class FeatureRow(split: String, labelId: Int, features: Seq[Float], featureHeight: Int, featureWidth: Int)
  final case class PredictionRow(split: String, labelId: Int, predictedLabelId: Int, score: Float)

  def main(args: Array[String]): Unit = {
    val row = FeatureRow("test", 2, Seq(0.1f, 0.2f, 0.3f), 1, 3)
    val scores = Array(0.05f, 0.20f, 0.75f)
    val (predictedScore, predictedLabelId) = scores.zipWithIndex.maxBy { case (score, _) => score }

    val prediction = PredictionRow(row.split, row.labelId, predictedLabelId, predictedScore)
    println(prediction)
  }
}
```
