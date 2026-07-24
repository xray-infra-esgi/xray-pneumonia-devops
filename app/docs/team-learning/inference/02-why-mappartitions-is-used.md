# Pourquoi `mapPartitions` est utilisé

## Idée

Le projet n'utilise pas un simple `map` pour faire l'inférence.

Il utilise :

```scala
mapPartitions
```

## Pourquoi

Le modèle TensorFlow est coûteux à charger.

Si on instancie un predictor pour chaque ligne, on recharge le modèle beaucoup trop souvent.

Avec `mapPartitions`, on peut :

- créer un predictor une seule fois par partition
- l'utiliser pour toutes les lignes de cette partition

## Cas concret du projet

Le code fait :

```scala
featuresDataset.mapPartitions { rows =>
  val predictor = new TensorflowPredictor(config.modelPath)
  ...
}
```

## Sortie

On garde la même sortie fonctionnelle, mais avec un coût bien plus raisonnable.

## Pourquoi c'est utile

Cette décision améliore fortement l'efficacité du pipeline d'inférence.

## Où c'est utilisé dans le projet

- [InferencePipelineRunner.scala:24](../../../src/main/scala/inference/InferencePipelineRunner.scala#L24)
- [TensorflowPredictor.scala:20](../../../src/main/scala/inference/TensorflowPredictor.scala#L20)

## Points de syntaxe à retenir

- `map` transforme une ligne à la fois
- `mapPartitions` transforme une partition entière à la fois
- ici, le but principal est de réutiliser le même predictor dans toute la partition

## Code complet exécutable

```scala
object WhyMapPartitionsIsUsedExample {
  def main(args: Array[String]): Unit = {
    val partitions = Seq(
      Seq("row1", "row2"),
      Seq("row3", "row4")
    )

    partitions.zipWithIndex.foreach { case (rows, partitionId) =>
      println(s"create predictor once for partition $partitionId")
      rows.foreach(row => println(s"predict on $row"))
    }
  }
}
```
