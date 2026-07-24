# Path vers ImageRecord

## Idée

Le preprocess reconstruit plusieurs informations à partir du chemin du fichier image.

À partir d'un seul chemin, on veut retrouver :

- `split`
- `label`
- `fileName`

## Cas concret du projet

Chemin exemple :

```text
data/chest_xray/train/PNEUMONIA/person1_bacteria_1.jpeg
```

## Transformation

Après normalisation du chemin et découpage en segments :

```text
["data", "chest_xray", "train", "PNEUMONIA", "person1_bacteria_1.jpeg"]
```

On lit ensuite :

- dernier segment -> `fileName`
- avant-dernier -> `label`
- troisième en partant de la fin -> `split`

## Sortie

```scala
ImageRecord(
  pathAbs = "data/chest_xray/train/PNEUMONIA/person1_bacteria_1.jpeg",
  split = "train",
  label = "PNEUMONIA",
  fileName = "person1_bacteria_1.jpeg"
)
```

## Pourquoi c'est utile

Cette étape reconstruit les métadonnées de base du pipeline à partir de l'arborescence du dataset.

## Où c'est utilisé dans le projet

- [ImagePreprocessor.scala:47](../../../src/main/scala/preprocess/ImagePreprocessor.scala#L47)

## Points de syntaxe à retenir

- `split("/")` découpe une chaîne en segments
- `segments.last` prend le dernier élément
- `segments(segments.length - 2)` lit l'avant-dernier
- cette logique dépend de la structure stable du dataset

## Code complet exécutable

```scala
object PathToImageRecordExample {
  final case class ImageRecord(pathAbs: String, split: String, label: String, fileName: String)

  def parsePath(path: String): ImageRecord = {
    val normalizedPath = path.replace("\\", "/")
    val segments = normalizedPath.split("/").filter(_.nonEmpty)

    ImageRecord(
      pathAbs = normalizedPath,
      split = segments(segments.length - 3),
      label = segments(segments.length - 2),
      fileName = segments.last
    )
  }

  def main(args: Array[String]): Unit = {
    val record = parsePath("data/chest_xray/train/PNEUMONIA/person1_bacteria_1.jpeg")

    println(s"record.pathAbs  = ${record.pathAbs}")
    println(s"record.split    = ${record.split}")
    println(s"record.label    = ${record.label}")
    println(s"record.fileName = ${record.fileName}")
  }
}
```
