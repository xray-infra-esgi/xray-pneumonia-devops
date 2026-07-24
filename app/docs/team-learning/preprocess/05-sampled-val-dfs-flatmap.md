# sampledValDfs avec flatMap

## Idée

Dans le rééquilibrage du split `val`, on veut compléter la validation jusqu'à atteindre un certain nombre d'exemples par classe.

Autrement dit :

- on garde `val`
- on mesure ce qu'il manque encore par classe
- puis on prélève dans `train` uniquement les classes qui ont encore besoin d'exemples

Dans cette étape, on parcourt les classes attendues :

- `0`
- `1`
- `2`

Pour chaque classe :

- soit il manque encore des exemples à ajouter dans `val`
- soit il ne manque rien

Pour chaque classe, on veut éventuellement construire un DataFrame contenant les lignes de `train` à ajouter à `val`.

## Cas concret du projet

Le code du projet utilise une logique de ce type :

```scala
val labelMapping = Map(
  "NORMAL" -> 0,
  "PNEUMONIA_BACTERIA" -> 1,
  "PNEUMONIA_VIRUS" -> 2
)

val expectedLabelIds: Seq[Int] = config.labelMapping.values.toSeq.distinct.sorted
// expectedLabelIds = Seq(0, 1, 2)

val currentValCounts = Map(
  0 -> 8L,
  1 -> 8L,
  2 -> 0L
)

val targetValCountPerClass = 100

val missingCounts: Map[Int, Int] = expectedLabelIds.map { labelId =>
  val currentValCount = currentValCounts.getOrElse(labelId, 0L)
  val missing = math.max(0, targetValCountPerClass - currentValCount.toInt)
  labelId -> missing
}.toMap
// missingCounts = Map(0 -> 92, 1 -> 92, 2 -> 100)

val sampledValDfs: Seq[DataFrame] = expectedLabelIds.flatMap { labelId =>
  val missingCount = missingCounts(labelId)

  if (missingCount <= 0) {
    None
  } else {
    Some(
      trainDf
        .filter(col("labelId") === labelId)
        .withColumn("_samplingKey", xxhash64(col("sourcePath")))
        .orderBy(col("_samplingKey"))
        .limit(missingCount)
        .drop("_samplingKey")
        .withColumn("split", lit("val"))
    )
  }
}
```

Ici :

- `expectedLabelIds` vaut typiquement `Seq(0, 1, 2)`
- cela correspond aux trois classes finales du projet
- on parcourt donc toutes les classes qu'on attend dans le dataset final
- dans un exemple concret comme celui ci-dessus, les ids viennent du `labelMapping`

## Entrée

Exemple simplifié :

```scala
val expectedLabelIds = Seq(0, 1, 2)

val missingCounts = Map(
  0 -> 92,
  1 -> 0,
  2 -> 100
)
```

Cela veut dire ici :

- pour la classe `0`, il manque encore `92` exemples dans `val`
- pour la classe `1`, il ne manque rien
- pour la classe `2`, il manque encore `100` exemples

## Transformation

Pour chaque `labelId` :

- si `missingCount <= 0`, on renvoie `None`
- sinon, on renvoie `Some(dataFrame)`

Exemple mental :

```scala
Seq(
  Some(dfForLabel0),
  None,
  Some(dfForLabel2)
)
```

Avec `flatMap`, le résultat final devient :

```scala
Seq(
  dfForLabel0,
  dfForLabel2
)
```

Le `None` disparaît automatiquement.

## Pourquoi on utilise flatMap ici

On ne veut pas :

- une séquence de `Option[DataFrame]`
- ni des éléments vides à gérer plus tard

On veut directement :

- une `Seq[DataFrame]`

contenant seulement les DataFrames à unionner.

La raison est simple :

- si une classe n'a plus besoin d'exemples, on ne veut pas construire un faux DataFrame vide juste pour elle
- on veut seulement garder les vraies sélections utiles
- ensuite, ces DataFrames sont fusionnés pour construire `extraValDf`

De façon plus concrète :

- s'il manque `92` lignes pour la classe `0`, alors un `sampledValDf` contiendra ces `92` lignes sélectionnées dans `trainDf`
- s'il manque `0` ligne pour la classe `1`, alors il n'y aura pas de `sampledValDf` pour cette classe
- s'il manque `100` lignes pour la classe `2`, alors un autre `sampledValDf` contiendra ces `100` lignes

Donc `sampledValDfs` est une séquence où chaque élément représente :

- les lignes à ajouter à `val`
- pour une classe donnée

## Sortie

Le résultat final est une séquence de DataFrames :

```scala
Seq(dfForLabel0, dfForLabel2)
```

et plus tard, on pourra faire :

```scala
sampledValDfs.reduceOption { (leftDf, rightDf) =>
  leftDf.unionByName(rightDf)
}
```

## Pourquoi c'est utile

Cette construction permet de :

- garder un code compact
- éviter de gérer à la main les cas vides
- construire proprement `extraValDf`

## Où c'est utilisé dans le projet

- [ImagePreprocessor.scala:206](../../../src/main/scala/preprocess/ImagePreprocessor.scala#L206)

## Points de syntaxe à retenir

- `map` garde un élément de sortie pour chaque élément d'entrée
- `flatMap` permet de produire `0`, `1` ou plusieurs éléments de sortie
- `Some(x)` signifie : il y a une valeur
- `None` signifie : pas de valeur
- avec `flatMap`, les `None` disparaissent du résultat final

## Code complet exécutable

```scala
object SampledValDfsFlatMapExample {
  def section(title: String): Unit = {
    println()
    println("=" * 100)
    println(title)
    println("=" * 100)
  }

  def main(args: Array[String]): Unit = {
    val expectedLabelIds = Seq(0, 1, 2)
    val missingCounts = Map(0 -> 92, 1 -> 0, 2 -> 100)

    section("Input data")
    println(s"expectedLabelIds = $expectedLabelIds")
    println(s"missingCounts    = $missingCounts")

    section("Result with map")
    val mappedOptions = expectedLabelIds.map { labelId =>
      val missingCount = missingCounts(labelId)

      if (missingCount <= 0) None
      else Some(s"sampledValDf for labelId=$labelId with $missingCount rows to add to val")
    }

    println(mappedOptions)

    section("Result with flatMap")
    val sampledValNames = expectedLabelIds.flatMap { labelId =>
      val missingCount = missingCounts(labelId)

      if (missingCount <= 0) None
      else Some(s"sampledValDf for labelId=$labelId with $missingCount rows to add to val")
    }

    println(sampledValNames)
  }
}
```

Dans cet exemple :

- on applique bien `flatMap` sur une séquence simple de nombres
- mais c'est volontaire, pour isoler le comportement de `Some` / `None`
- le but est de comprendre la syntaxe et la forme du résultat, sans ajouter la complexité Spark en plus

## Exemple séparé : `unionByName`

Une fois que `sampledValDfs` a été construit, le pipeline doit ensuite fusionner les DataFrames utiles entre eux.

Dans le projet, cela se fait avec :

```scala
sampledValDfs.reduceOption { (leftDf, rightDf) =>
  leftDf.unionByName(rightDf)
}
```

Voici un mini exemple Spark séparé pour illustrer seulement `unionByName`.

```scala
import org.apache.spark.sql.SparkSession

object UnionByNameExample {
  final case class SampledRow(sourcePath: String, split: String, labelId: Int)

  def main(args: Array[String]): Unit = {
    val spark = SparkSession.builder()
      .appName("UnionByNameExample")
      .master("local[*]")
      .getOrCreate()

    import spark.implicits._

    try {
      val dfForLabel0 = Seq(
        SampledRow("imgA", "val", 0),
        SampledRow("imgB", "val", 0)
      ).toDF()

      val dfForLabel2 = Seq(
        SampledRow("imgC", "val", 2),
        SampledRow("imgD", "val", 2)
      ).toDF()

      val extraValDf = dfForLabel0.unionByName(dfForLabel2)

      println("dfForLabel0")
      dfForLabel0.show(false)

      println("dfForLabel2")
      dfForLabel2.show(false)

      println("extraValDf")
      extraValDf.show(false)
    } finally {
      spark.stop()
    }
  }
}
```

Dans cet exemple :

- `dfForLabel0` représente les lignes à ajouter à `val` pour la classe `0`
- `dfForLabel2` représente les lignes à ajouter à `val` pour la classe `2`
- `unionByName` fusionne ces deux DataFrames parce qu'ils ont le même schéma
