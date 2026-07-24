# `withColumn`, `col` et `lit`

## Idée

Dans Spark SQL, on manipule souvent les colonnes avec :

- `withColumn(...)`
- `col(...)`
- `lit(...)`

Ces trois éléments reviennent souvent dans le preprocess.

## Cas concret du projet

Exemple de logique proche du projet :

```scala
trainDf
  .filter(col("labelId") === labelId)
  .withColumn("split", lit("val"))
```

## Signification

### `col("labelId")`

Désigne la colonne nommée `labelId`.

### `lit("val")`

Crée une valeur littérale constante utilisable comme colonne Spark.

Ici :

```scala
lit("val")
```

représente une colonne dont la valeur vaut toujours `"val"`.

### `withColumn("split", lit("val"))`

Remplace ou ajoute une colonne `split` dont la valeur sera `"val"` pour chaque ligne.

## Entrée

Imaginons un mini tableau :

```text
split | labelId
------+--------
train | 0
train | 1
train | 2
```

## Transformation

Après :

```scala
.withColumn("split", lit("val"))
```

on obtient conceptuellement :

```text
split | labelId
------+--------
val   | 0
val   | 1
val   | 2
```

## Pourquoi c'est utile

Cette syntaxe permet de construire ou modifier des colonnes de manière déclarative dans Spark.

Dans le projet, c'est utile pour :

- changer un split
- créer des colonnes intermédiaires
- écrire des transformations lisibles

## Où c'est utilisé dans le projet

- [ImagePreprocessor.scala:55](../../../src/main/scala/preprocess/ImagePreprocessor.scala#L55)
- [ImagePreprocessor.scala:219](../../../src/main/scala/preprocess/ImagePreprocessor.scala#L219)

## Points de syntaxe à retenir

- `col("x")` représente une colonne existante
- `lit(v)` représente une valeur constante vue comme colonne Spark
- `withColumn(name, expr)` ajoute ou remplace une colonne

## Code complet exécutable

```scala
import org.apache.spark.sql.SparkSession
import org.apache.spark.sql.functions.{col, lit}

object WithColumnColLitExample {
  final case class Row(split: String, labelId: Int)

  def section(title: String): Unit = {
    println()
    println("=" * 100)
    println(title)
    println("=" * 100)
  }

  def main(args: Array[String]): Unit = {
    val spark = SparkSession.builder()
      .appName("WithColumnColLitExample")
      .master("local[*]")
      .getOrCreate()

    spark.sparkContext.setLogLevel("WARN")

    import spark.implicits._

    try {
      val df = Seq(
        Row("train", 0),
        Row("train", 1),
        Row("train", 2)
      ).toDF()

      val updatedDf = df
        .filter(col("labelId") >= 1)
        .withColumn("split", lit("val"))

      section("df")
      df.show(false)

      section("updatedDf")
      updatedDf.show(false)
    } finally {
      spark.stop()
    }
  }
}
```
