# Ce que `mapPartitions` reçoit et retourne

## Idée

`mapPartitions` ne travaille pas sur une seule ligne.

Il reçoit :

- un `Iterator` sur les lignes d'une partition

et il doit retourner :

- un autre `Iterator`

## Cas concret du projet

Dans notre code :

```scala
val featuresDataset = spark.read
  .parquet(config.featuresPath)
  .as[FeatureRow]

val predictionDataset = featuresDataset.mapPartitions { rows =>
  ...
}
```

Ici :

- `featuresDataset` est un `Dataset[FeatureRow]`
- `rows` est un `Iterator[FeatureRow]`

## Ce que la fonction doit renvoyer

La fonction passée à `mapPartitions` ne renvoie pas une ligne, mais un iterator de sortie.

Dans notre cas :

```scala
Iterator[PredictionRow]
```

On peut donc résumer :

- entrée : `Iterator[FeatureRow]`
- sortie : `Iterator[PredictionRow]`

## Pourquoi cela a du sens

Une partition contient plusieurs lignes.

Spark laisse donc la fonction :

- parcourir les lignes de la partition
- produire les sorties une par une

Cela permet aussi d'ouvrir une ressource une seule fois pour toute la partition.

## Lien avec le code du projet

Dans le runner, le lien entre l'entrée et la sortie se fait dans :

```scala
val row = rows.next()
```

puis :

```scala
PredictionRow(...)
```

Autrement dit :

- `rows.next()` prend la prochaine ligne d'entrée
- puis cette ligne est transformée en une ligne de sortie

## Pourquoi c'est utile

Cette fiche aide à comprendre exactement la signature mentale de `mapPartitions`.

## Où c'est utilisé dans le projet

- [InferencePipelineRunner.scala:20](../../../src/main/scala/inference/InferencePipelineRunner.scala#L20)
- [InferencePipelineRunner.scala:24](../../../src/main/scala/inference/InferencePipelineRunner.scala#L24)
- [InferencePipelineRunner.scala:45](../../../src/main/scala/inference/InferencePipelineRunner.scala#L45)

## Points de syntaxe à retenir

- `mapPartitions` travaille sur des iterators, pas directement sur des lignes isolées
- l'iterator d'entrée est fourni par Spark
- l'iterator de sortie est construit par notre code
- `rows.next()` fait avancer l'iterator d'entrée

## Code complet exécutable

```scala
object WhatMapPartitionsReturnsExample {
  final case class FeatureRow(id: String)
  final case class PredictionRow(id: String, prediction: String)

  def main(args: Array[String]): Unit = {
    val rows: Iterator[FeatureRow] = Iterator(FeatureRow("a"), FeatureRow("b"))

    val predictions: Iterator[PredictionRow] = new Iterator[PredictionRow] {
      override def hasNext: Boolean = rows.hasNext

      override def next(): PredictionRow = {
        val row = rows.next()
        PredictionRow(row.id, s"prediction-for-${row.id}")
      }
    }

    predictions.foreach(println)
  }
}
```
