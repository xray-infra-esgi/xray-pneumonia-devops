# left_anti join

## Idée

Après avoir sélectionné des lignes de `train` pour compléter `val`, il faut retirer ces lignes du `train`.

On ne veut pas :

- garder les mêmes exemples dans `train` et dans `val`

Le `left_anti join` sert précisément à cela.

## Cas concret du projet

Le code du projet utilise une logique de ce type :

```scala
val selectedSourcePathsDf = extraValDf.select("sourcePath").distinct()

val remainingTrainDf = trainDf.join(
  selectedSourcePathsDf,
  Seq("sourcePath"),
  "left_anti"
)
```

## Entrée

### trainDf

```text
sourcePath
----------------
imgA
imgB
imgC
imgD
```

### selectedSourcePathsDf

```text
sourcePath
----------------
imgB
imgD
```

## Transformation

Avec un `left_anti join`, Spark garde :

- les lignes du DataFrame de gauche
- qui n'ont **aucune correspondance** dans le DataFrame de droite

Ici :

- `imgB` est exclue
- `imgD` est exclue

## Sortie

### remainingTrainDf

```text
sourcePath
----------------
imgA
imgC
```

## Pourquoi c'est utile

Cette opération garantit qu'un exemple déplacé vers `val` :

- n'existe plus dans `train`

Donc :

- pas de duplication entre splits
- pas de fuite de validation

## Où c'est utilisé dans le projet

- [ImagePreprocessor.scala:230](../../../src/main/scala/preprocess/ImagePreprocessor.scala#L230)

## Points de syntaxe à retenir

- `join(..., "left_anti")` garde uniquement les lignes sans correspondance
- le DataFrame de gauche est celui qu'on veut filtrer
- le DataFrame de droite contient les clés à exclure
- ici, la clé utilisée est `sourcePath`

## Code complet exécutable

```scala
import org.apache.spark.sql.SparkSession

object LeftAntiJoinExample {
  final case class TrainRow(sourcePath: String, split: String, labelId: Int)
  final case class SelectedRow(sourcePath: String)

  def section(title: String): Unit = {
    println()
    println("=" * 100)
    println(title)
    println("=" * 100)
  }

  def main(args: Array[String]): Unit = {
    val spark = SparkSession.builder()
      .appName("LeftAntiJoinExample")
      .master("local[*]")
      .getOrCreate()

    spark.sparkContext.setLogLevel("WARN")

    import spark.implicits._

    try {
      val trainDf = Seq(
        TrainRow("imgA", "train", 0),
        TrainRow("imgB", "train", 1),
        TrainRow("imgC", "train", 2),
        TrainRow("imgD", "train", 2)
      ).toDF()

      val selectedSourcePathsDf = Seq(
        SelectedRow("imgB"),
        SelectedRow("imgD")
      ).toDF()

      val remainingTrainDf = trainDf.join(
        selectedSourcePathsDf,
        Seq("sourcePath"),
        "left_anti"
      )

      section("trainDf")
      trainDf.show(false)

      section("selectedSourcePathsDf")
      selectedSourcePathsDf.show(false)

      section("remainingTrainDf after left_anti join")
      remainingTrainDf.show(false)
    } finally {
      spark.stop()
    }
  }
}
```
