# Resolve Target Label

## Idée

Le dataset source contient encore les dossiers :

- `NORMAL`
- `PNEUMONIA`

Mais le projet travaille maintenant avec 3 classes finales :

- `NORMAL`
- `PNEUMONIA_BACTERIA`
- `PNEUMONIA_VIRUS`

Il faut donc transformer un label brut de dossier en label final de modèle.

## Entrée

Exemple 1 :

```scala
ImageRecord(
  pathAbs = "data/chest_xray/train/NORMAL/normal1.jpeg",
  split = "train",
  label = "NORMAL",
  fileName = "normal1.jpeg"
)
```

Exemple 2 :

```scala
ImageRecord(
  pathAbs = "data/chest_xray/train/PNEUMONIA/person1_bacteria_1.jpeg",
  split = "train",
  label = "PNEUMONIA",
  fileName = "person1_bacteria_1.jpeg"
)
```

Exemple 3 :

```scala
ImageRecord(
  pathAbs = "data/chest_xray/train/PNEUMONIA/person2_virus_3.jpeg",
  split = "train",
  label = "PNEUMONIA",
  fileName = "person2_virus_3.jpeg"
)
```

## Transformation

La logique est :

- si `row.label == "NORMAL"` alors le label final est `NORMAL`
- si `row.label == "PNEUMONIA"` et que `fileName` contient `bacteria`, alors le label final est `PNEUMONIA_BACTERIA`
- si `row.label == "PNEUMONIA"` et que `fileName` contient `virus`, alors le label final est `PNEUMONIA_VIRUS`
- sinon, on lève une erreur

Extrait simplifié :

```scala
row.label match {
  case "NORMAL" =>
    "NORMAL"

  case "PNEUMONIA" =>
    val fileNameLower = row.fileName.trim.toLowerCase

    if (fileNameLower.contains("bacteria")) {
      "PNEUMONIA_BACTERIA"
    } else if (fileNameLower.contains("virus")) {
      "PNEUMONIA_VIRUS"
    } else {
      throw new IllegalArgumentException(...)
    }

  case other =>
    throw new IllegalArgumentException(...)
}
```

## Sortie

Exemple 1 :

```scala
"NORMAL"
```

Exemple 2 :

```scala
"PNEUMONIA_BACTERIA"
```

Exemple 3 :

```scala
"PNEUMONIA_VIRUS"
```

## Pourquoi c'est utile

Le dossier `PNEUMONIA` ne suffit plus à lui seul pour décrire la classe finale du modèle.

Cette étape sert à :

- garder les catégories physiques du dataset source
- produire les bons labels finaux du problème de classification

## Où c'est utilisé dans le projet

- [ImagePreprocessor.scala:75](../../../src/main/scala/preprocess/ImagePreprocessor.scala#L75)

## Points de syntaxe à retenir

- `match` permet de traiter plusieurs cas de façon plus lisible qu'un grand `if / else`
- `case other =>` est le cas par défaut ici
- `trim.toLowerCase` normalise le nom de fichier avant les tests
- `contains(...)` vérifie si une sous-chaîne est présente dans le nom

## Code complet exécutable

```scala
object ResolveTargetLabelExample {
  final case class ImageRecord(label: String, fileName: String)

  def resolveTargetLabel(row: ImageRecord): String =
    row.label match {
      case "NORMAL" => "NORMAL"
      case "PNEUMONIA" =>
        val fileNameLower = row.fileName.trim.toLowerCase
        if (fileNameLower.contains("bacteria")) "PNEUMONIA_BACTERIA"
        else if (fileNameLower.contains("virus")) "PNEUMONIA_VIRUS"
        else throw new IllegalArgumentException("Unknown pneumonia subtype")
      case other =>
        throw new IllegalArgumentException(s"Unsupported label: $other")
    }

  def main(args: Array[String]): Unit = {
    val rows = Seq(
      ImageRecord("NORMAL", "normal1.jpeg"),
      ImageRecord("PNEUMONIA", "person1_bacteria_1.jpeg"),
      ImageRecord("PNEUMONIA", "person2_virus_3.jpeg")
    )

    rows.foreach { row =>
      println(s"${row.label} / ${row.fileName} -> ${resolveTargetLabel(row)}")
    }
  }
}
```
