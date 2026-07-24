# case class et `.as[...]`

## Idée

Dans le projet, on utilise souvent une `case class` pour donner une forme claire aux lignes d'un Dataset Spark.

Exemples :

- `ImageRecord`
- `FeatureRow`
- `PredictionRow`

## Cas concret du projet

Exemple de logique :

```scala
val featuresDataset = spark.read
  .parquet(config.featuresPath)
  .as[FeatureRow]
```

Ici :

- Spark lit un DataFrame
- `.as[FeatureRow]` demande ensuite un Dataset typé

## Entrée

Imaginons un schéma avec ces colonnes :

```text
split
labelId
featureWidth
featureHeight
features
```

et une case class correspondante :

```scala
final case class FeatureRow(
  split: String,
  labelId: Int,
  featureWidth: Int,
  featureHeight: Int,
  features: Seq[Float]
)
```

## Transformation

Quand on écrit :

```scala
.as[FeatureRow]
```

on demande à Spark :

- de vérifier que les colonnes du DataFrame correspondent au type attendu
- de représenter ensuite chaque ligne comme un `FeatureRow`

## Sortie

On obtient :

```scala
Dataset[FeatureRow]
```

et non plus seulement un DataFrame non typé.

## Pourquoi c'est utile

Cela améliore :

- la lisibilité
- la sécurité de type
- l'accès aux champs avec des noms clairs

Au lieu de manipuler des lignes génériques, on manipule directement des objets métier.

## Où c'est utilisé dans le projet

- [ImagePreprocessor.scala:31](../../../src/main/scala/preprocess/ImagePreprocessor.scala#L31)
- [InferencePipelineRunner.scala:20](../../../src/main/scala/inference/InferencePipelineRunner.scala#L20)
- [InferenceSchemas.scala:3](../../../src/main/scala/inference/InferenceSchemas.scala#L3)

## Points de syntaxe à retenir

- une `case class` est une manière simple de définir un type de données structuré en Scala
- `.as[T]` sert à demander un Dataset typé
- pour que cela fonctionne, les noms de colonnes doivent être cohérents avec les champs de la case class

## Code complet exécutable

```scala
object CaseClassAndAsDatasetIdeaExample {
  final case class FeatureRow(split: String, labelId: Int)

  def main(args: Array[String]): Unit = {
    val row = FeatureRow("train", 1)

    println(row)
    println(s"split   = ${row.split}")
    println(s"labelId = ${row.labelId}")
  }
}
```
