# Pourquoi `score` signifie le score de la classe prédite

## Idée

Dans `PredictionRow`, le champ `score` ne contient pas tous les scores du modèle.

Il contient seulement :

- le score de la classe choisie

## Cas concret du projet

Le code fait :

```scala
val (predictedScore, predictedLabelId) =
  scores.zipWithIndex.maxBy { case (score, _) => score }
```

Puis :

```scala
PredictionRow(
  ...,
  predictedLabelId = predictedLabelId,
  score = predictedScore
)
```

## Exemple

Si :

```scala
scores = Array(0.10f, 0.25f, 0.65f)
```

alors :

- `predictedLabelId = 2`
- `score = 0.65f`

## Pourquoi c'est utile

Cette fiche évite une confusion fréquente :

- `score` n'est pas un score global abstrait
- c'est bien le score de la classe gagnante

## Où c'est utilisé dans le projet

- [InferencePipelineRunner.scala:53](../../../src/main/scala/inference/InferencePipelineRunner.scala#L53)
- [InferenceSchemas.scala:11](../../../src/main/scala/inference/InferenceSchemas.scala#L11)

## Points de syntaxe à retenir

- le score gardé en sortie est `predictedScore`
- les autres scores ne sont pas stockés dans `PredictionRow`

## Code complet exécutable

```scala
object WhyScoreMeansScoreOfPredictedClassExample {
  def main(args: Array[String]): Unit = {
    val scores = Array(0.10f, 0.25f, 0.65f)
    val (predictedScore, predictedLabelId) = scores.zipWithIndex.maxBy { case (score, _) => score }

    println(s"predictedLabelId = $predictedLabelId")
    println(s"score            = $predictedScore")
  }
}
```
