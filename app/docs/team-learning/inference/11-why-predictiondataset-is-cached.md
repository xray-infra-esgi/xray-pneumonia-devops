# Pourquoi `predictionDataset` est mis en cache

## Idée

Dans le runner, `predictionDataset` est mis en cache avant plusieurs actions.

## Cas concret du projet

Le code fait :

```scala
val predictionDataset = featuresDataset.mapPartitions { rows =>
  ...
}.cache()
```

Puis ensuite :

```scala
val total = predictionDataset.count()
val correct = predictionDataset.filter(r => r.labelId == r.predictedLabelId).count()

predictionDataset
  .write
  .mode("overwrite")
  .parquet(config.outputPath)
```

## Pourquoi c'est important

Sans `cache()`, Spark pourrait recalculer `predictionDataset` à chaque action.

Ici, il y a plusieurs actions :

- `count()`
- `filter(...).count()`
- `write.parquet(...)`

Donc sans cache, Spark pourrait refaire plusieurs fois :

- l'appel au predictor
- la lecture des scores
- la construction des `PredictionRow`

## Idée générale

Spark est paresseux :

- les transformations construisent un plan de calcul
- ce plan n'est exécuté que lorsqu'une action est appelée

Quand plusieurs actions utilisent le même DataFrame intermédiaire, il peut être rentable de le mettre en cache.

## Pourquoi le cache est utile ici

`predictionDataset` est :

- réutilisé plusieurs fois
- plus coûteux qu'un simple DataFrame trivial

Même si cette étape est moins lourde que la vectorisation du preprocess, elle appelle quand même le modèle TensorFlow.

Il est donc logique d'éviter de refaire inutilement cette étape plusieurs fois.

## Sortie

Le résultat fonctionnel ne change pas.

Le cache sert seulement à :

- éviter des recalculs
- rendre le pipeline plus efficace

## Où c'est utilisé dans le projet

- [InferencePipelineRunner.scala:24](../../../src/main/scala/inference/InferencePipelineRunner.scala#L24)
- [InferencePipelineRunner.scala:64](../../../src/main/scala/inference/InferencePipelineRunner.scala#L64)
- [InferencePipelineRunner.scala:66](../../../src/main/scala/inference/InferencePipelineRunner.scala#L66)
- [InferencePipelineRunner.scala:72](../../../src/main/scala/inference/InferencePipelineRunner.scala#L72)

## Points de syntaxe à retenir

- `cache()` demande à Spark de conserver le résultat intermédiaire
- `count()` et `write(...)` sont des actions
- plusieurs actions sur un même DataFrame intermédiaire peuvent justifier un cache

## Code complet exécutable

```scala
import org.apache.spark.sql.SparkSession

object WhyPredictionDatasetIsCachedExample {
  def main(args: Array[String]): Unit = {
    val spark = SparkSession.builder()
      .appName("WhyPredictionDatasetIsCachedExample")
      .master("local[1]")
      .getOrCreate()

    import spark.implicits._

    try {
      val baseDs = (1 to 200).toDS()

      def fakePrediction(x: Int): Int = {
        val result = (1 to 1000000).foldLeft(0L) { (acc, i) =>
          acc + ((x.toLong * i) % 97L)
        }
        result.toInt
      }

      println("Warm-up...")
      baseDs.map(fakePrediction).collect()

      println()
      println("Without cache:")
      val predictionsWithoutCache = baseDs.map(fakePrediction)

      val startWithoutCache1 = System.nanoTime()
      val resultWithoutCache1 = predictionsWithoutCache.collect()
      val elapsedWithoutCache1 = (System.nanoTime() - startWithoutCache1) / 1000000

      val startWithoutCache2 = System.nanoTime()
      val resultWithoutCache2 = predictionsWithoutCache.collect()
      val elapsedWithoutCache2 = (System.nanoTime() - startWithoutCache2) / 1000000

      println()
      println("With cache:")
      val predictionsWithCache = baseDs.map(fakePrediction).cache()

      val startWithCache1 = System.nanoTime()
      val resultWithCache1 = predictionsWithCache.collect()
      val elapsedWithCache1 = (System.nanoTime() - startWithCache1) / 1000000

      val startWithCache2 = System.nanoTime()
      val resultWithCache2 = predictionsWithCache.collect()
      val elapsedWithCache2 = (System.nanoTime() - startWithCache2) / 1000000

      println()
      println(s"same results without cache = ${resultWithoutCache1.sameElements(resultWithoutCache2)}")
      println(s"same results with cache    = ${resultWithCache1.sameElements(resultWithCache2)}")
      println(s"without cache, first collect  = ${elapsedWithoutCache1} ms")
      println(s"without cache, second collect = ${elapsedWithoutCache2} ms")
      println(s"with cache, first collect     = ${elapsedWithCache1} ms")
      println(s"with cache, second collect    = ${elapsedWithCache2} ms")
      predictionsWithCache.unpersist()
    } finally {
      spark.stop()
    }
  }
}
```

Si tu l'exécutes, il faut observer :

- dans la partie `Without cache`, le deuxième `collect()` reste coûteux car Spark peut recalculer le Dataset
- dans la partie `With cache`, le deuxième `collect()` doit normalement être plus léger car le résultat a déjà été matérialisé

Autrement dit :

- sans cache, Spark peut recalculer le Dataset intermédiaire à chaque action
- avec cache, Spark peut réutiliser le résultat déjà matérialisé

Les temps peuvent varier selon :

- l'initialisation de Spark
- le warm-up de la JVM
- la machine utilisée

Dans cet exemple, un warm-up est lancé avant les mesures pour réduire cet effet.

Mais l'idée générale reste la même :

- le cache ne change pas le résultat
- il sert à éviter ou réduire les recalculs lors des actions suivantes
