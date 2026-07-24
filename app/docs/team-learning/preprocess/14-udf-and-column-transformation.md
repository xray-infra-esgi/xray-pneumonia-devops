# UDF et transformation de colonnes

## Idée

Une UDF Spark permet d'appliquer une fonction Scala à une colonne d'un DataFrame.

## Cas concret du projet

Dans le preprocess, on a une logique de ce type :

```scala
def normalizePath(rawPath: String): String =
  rawPath.replace("file:", "")

val normalizeSparkPath = udf((rawPath: String) => normalizePath(rawPath))
```

Puis :

```scala
df.withColumn("pathAbs", normalizeSparkPath(col("sparkPath")))
```

## Entrée

Imaginons une colonne :

```text
sparkPath
------------------------
file:/tmp/image1.jpeg
file:/tmp/image2.jpeg
```

## Transformation

On part d'abord d'une fonction Scala normale :

```scala
def normalizePath(rawPath: String): String =
  rawPath.replace("file:", "")
```

Puis on la transforme en UDF Spark :

```scala
val normalizeSparkPath = udf(normalizePath _)
```

Ensuite, on applique cette UDF à la colonne `sparkPath` :

```scala
df.withColumn("pathAbs", normalizeSparkPath(col("sparkPath")))
```

L'idée est donc :

- fonction Scala simple
- transformation en UDF
- application à une colonne Spark

Dans ce projet, l'UDF est appliquée à une seule colonne.
Mais une UDF Spark peut aussi recevoir plusieurs colonnes en entrée.

## Sortie

Conceptuellement :

```text
pathAbs
------------------------
/tmp/image1.jpeg
/tmp/image2.jpeg
```

## Pourquoi c'est utile

Cela permet de relier :

- une fonction Scala classique
- à une transformation de colonne Spark

## Où c'est utilisé dans le projet

- [ImagePreprocessor.scala:49](../../../src/main/scala/preprocess/ImagePreprocessor.scala#L49)
- [ImagePreprocessor.scala:56](../../../src/main/scala/preprocess/ImagePreprocessor.scala#L56)

## Points de syntaxe à retenir

- `def normalizePath(...)` définit une fonction Scala normale
- `udf(...)` transforme une fonction Scala en fonction utilisable dans Spark SQL
- `col("x")` désigne une colonne d'entrée
- `withColumn("y", expr)` crée ou remplace une colonne de sortie

## Code complet exécutable

```scala
import org.apache.spark.sql.SparkSession
import org.apache.spark.sql.functions.{col, udf}

object UdfAndColumnTransformationExample {
  def normalizePath(rawPath: String): String =
    rawPath.replace("file:", "")

  def main(args: Array[String]): Unit = {
    val spark = SparkSession.builder()
      .appName("UdfAndColumnTransformationExample")
      .master("local[*]")
      .getOrCreate()

    import spark.implicits._

    try {
      val df = Seq(
        "file:/tmp/image1.jpeg",
        "file:/tmp/image2.jpeg"
      ).toDF("sparkPath")

      val normalizeSparkPath = udf((rawPath: String) => normalizePath(rawPath))

      val resultDf =
        df.withColumn("pathAbs", normalizeSparkPath(col("sparkPath")))

      resultDf.show(false)
    } finally {
      spark.stop()
    }
  }
}
```
