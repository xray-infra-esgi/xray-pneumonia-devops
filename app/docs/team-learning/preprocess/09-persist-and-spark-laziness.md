# persist et lazy evaluation dans Spark

## Idée

Spark évalue les transformations de façon paresseuse.

Cela veut dire :

- construire un DataFrame ne calcule pas immédiatement les données
- Spark attend qu'une action soit demandée

Exemples d'actions :

- `count()`
- `collect()`
- `write.parquet(...)`

## Le problème rencontré dans le projet

Dans le preprocess, on a une étape coûteuse :

- lire les images
- les redimensionner
- extraire les features

Cette étape produit `vectorizedDataset`.

Ensuite, ce résultat est réutilisé plusieurs fois dans `rebalanceValidationSplit(...)` :

- pour construire `trainDf`
- pour construire `valDf`
- pour construire `testDf`
- pour faire des `groupBy(...).count()`
- pour échantillonner des lignes
- pour faire le `left_anti join`
- pour écrire le résultat final

## Sans persist

Sans `persist`, Spark peut repartir de la source logique à chaque action importante.

Dans notre cas, cela veut dire qu'il peut refaire plusieurs fois :

- la lecture des images
- le resize
- la vectorisation

Schéma simplifié :

```text
records
  -> map(vectorizeRecord)
  -> toDF()
  -> groupBy(...).count()

records
  -> map(vectorizeRecord)
  -> toDF()
  -> sampling train -> val

records
  -> map(vectorizeRecord)
  -> toDF()
  -> write.parquet(...)
```

Le point important est que `map(vectorizeRecord)` est coûteux.

## Avec persist

On demande à Spark de conserver le résultat intermédiaire :

```scala
val vectorizedDataset =
  records
    .repartition(targetPartitions)
    .map(row => vectorizeRecord(row, config))
    .toDF()
    .persist(StorageLevel.MEMORY_AND_DISK)
```

Puis on force sa matérialisation :

```scala
vectorizedDataset.count()
```

Ensuite, les opérations suivantes réutilisent ce résultat déjà calculé.

Schéma simplifié :

```text
records
  -> map(vectorizeRecord)
  -> toDF()
  -> persist
  -> count()   => calcul réel une seule fois

résultat persisté
  -> groupBy(...).count()
  -> sampling train -> val
  -> left_anti join
  -> write.parquet(...)
```

## Pourquoi MEMORY_AND_DISK

On a utilisé :

```scala
StorageLevel.MEMORY_AND_DISK
```

Cela signifie :

- Spark essaie de garder les partitions en mémoire
- si tout ne tient pas en mémoire, il peut stocker aussi sur disque

Ce choix est plus robuste que `MEMORY_ONLY` pour un intermédiaire coûteux à recalculer.

## Résultat observé dans le projet

Mesure observée pendant le travail :

- avant `persist` : `467 s`
- après `persist` : `77 s`

Le gain a été très important, car le vrai problème n'était pas seulement :

- `groupBy`
- `join`
- `union`

Le vrai problème était surtout la recomputation de la vectorisation des images.

## Sortie

Ce qu'il faut retenir n'est pas une sortie de données, mais un effet sur le pipeline :

- même résultat fonctionnel
- beaucoup moins de recalcul
- temps total très réduit

## Pourquoi c'est utile

Cette fiche sert à comprendre deux idées importantes :

- Spark est lazy
- un DataFrame coûteux réutilisé plusieurs fois est souvent un bon candidat pour `persist`

## Où c'est utilisé dans le projet

- [ImagePreprocessor.scala:121](../../../src/main/scala/preprocess/ImagePreprocessor.scala#L121)

## Points de syntaxe à retenir

- `persist(...)` ne force pas le calcul à lui seul
- `count()` a ici servi à matérialiser le DataFrame persisté
- `unpersist()` libère la ressource à la fin
- `persist` est utile surtout quand un résultat :
  - coûte cher à produire
  - et est réutilisé plusieurs fois

## Code complet exécutable

```scala
import org.apache.spark.sql.SparkSession
import org.apache.spark.storage.StorageLevel

object PersistAndSparkLazinessExample {
  def section(title: String): Unit = {
    println()
    println("=" * 100)
    println(title)
    println("=" * 100)
  }

  def main(args: Array[String]): Unit = {
    val spark = SparkSession.builder()
      .appName("PersistAndSparkLazinessExample")
      .master("local[1]")
      .getOrCreate()

    spark.sparkContext.setLogLevel("WARN")

    import spark.implicits._

    try {
      val baseDs = (1 to 200).toDS()

      def expensiveComputation(x: Int): Int = {
        val result = (1 to 1000000).foldLeft(0L) { (acc, i) =>
          acc + ((x.toLong * i) % 97L)
        }
        result.toInt
      }

      section("Warm-up")
      baseDs.map(expensiveComputation).collect()

      section("Without persist")
      val withoutPersist = baseDs.map(expensiveComputation)

      val startWithoutPersist1 = System.nanoTime()
      val resultWithoutPersist1 = withoutPersist.collect()
      val elapsedWithoutPersist1 = (System.nanoTime() - startWithoutPersist1) / 1000000

      val startWithoutPersist2 = System.nanoTime()
      val resultWithoutPersist2 = withoutPersist.collect()
      val elapsedWithoutPersist2 = (System.nanoTime() - startWithoutPersist2) / 1000000

      section("With persist")
      val withPersist = baseDs
        .map(expensiveComputation)
        .persist(StorageLevel.MEMORY_AND_DISK)

      val startWithPersist1 = System.nanoTime()
      val resultWithPersist1 = withPersist.collect()
      val elapsedWithPersist1 = (System.nanoTime() - startWithPersist1) / 1000000

      val startWithPersist2 = System.nanoTime()
      val resultWithPersist2 = withPersist.collect()
      val elapsedWithPersist2 = (System.nanoTime() - startWithPersist2) / 1000000

      println(s"same results without persist = ${resultWithoutPersist1.sameElements(resultWithoutPersist2)}")
      println(s"same results with persist    = ${resultWithPersist1.sameElements(resultWithPersist2)}")
      println(s"without persist, first collect = ${elapsedWithoutPersist1} ms")
      println(s"without persist, second collect = ${elapsedWithoutPersist2} ms")
      println(s"with persist, first collect    = ${elapsedWithPersist1} ms")
      println(s"with persist, second collect   = ${elapsedWithPersist2} ms")

      withPersist.unpersist()
    } finally {
      spark.stop()
    }
  }
}
```

Si tu l'exécutes, il faut observer :

- sans `persist`, le deuxième `collect()` reste coûteux car Spark peut recalculer le Dataset
- avec `persist`, le premier `collect()` calcule et matérialise le résultat
- avec `persist`, le deuxième `collect()` doit normalement être beaucoup plus léger

Les temps peuvent varier selon :

- l'initialisation de Spark
- le warm-up de la JVM
- la machine utilisée

Mais l'idée générale reste la même :

- `persist` ne change pas le résultat
- `persist` sert à éviter ou réduire les recalculs lors des actions suivantes
