# Argmax à partir des scores

## Idée

Quand le modèle renvoie plusieurs scores, il faut choisir la classe du score maximal.

## Entrée

```scala
val scores = Array(0.10f, 0.25f, 0.65f)
```

## Transformation

Dans le projet :

```scala
val (predictedScore, predictedLabelId) =
  scores.zipWithIndex.maxBy { case (score, _) => score }
```

## Sortie

```scala
predictedScore   = 0.65f
predictedLabelId = 2
```

## Pourquoi c'est utile

Cette étape transforme la sortie brute du modèle en prédiction exploitable.

## Où c'est utilisé dans le projet

- [InferencePipelineRunner.scala:47](../../../src/main/scala/inference/InferencePipelineRunner.scala#L47)

## Points de syntaxe à retenir

- `zipWithIndex` associe chaque score à son indice
- `maxBy` choisit le score maximal
- l'indice du score maximal devient le `predictedLabelId`

## Code complet exécutable

```scala
object ArgmaxFromScoresExample {
  def main(args: Array[String]): Unit = {
    val scores = Array(0.10f, 0.25f, 0.65f)
    val (predictedScore, predictedLabelId) = scores.zipWithIndex.maxBy { case (score, _) => score }

    println(s"predictedScore   = $predictedScore")
    println(s"predictedLabelId = $predictedLabelId")
  }
}
```
