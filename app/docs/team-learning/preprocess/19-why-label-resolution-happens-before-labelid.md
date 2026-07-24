# Passage vers `labelId` dans le projet

## Idée

Dans le projet, `labelId` n'est pas calculé directement à partir du label brut lu dans le dossier.

Le passage se fait en trois étapes :

- `row.label`
- `targetLabel`
- `labelId`

## Variables utilisées dans le projet

### 1. `row.label`

Dans `ImageRecord`, on lit :

```scala
label: String
```

Cette valeur correspond au label brut lu depuis l'arborescence du dataset.

Exemples possibles :

- `"NORMAL"`
- `"PNEUMONIA"`

### 2. `targetLabel`

Le code appelle ensuite :

```scala
val targetLabel = resolveTargetLabel(row)
```

Ici :

- `targetLabel` est aussi un `String`
- mais cette fois il représente le label final du modèle

Exemples possibles :

- `"NORMAL"`
- `"PNEUMONIA_BACTERIA"`
- `"PNEUMONIA_VIRUS"`

### 3. `labelId`

Enfin, le code fait :

```scala
val labelId = config.labelMapping.getOrElse(targetLabel, ...)
```

Ici :

- `labelId` est un `Int`
- c'est l'identifiant numérique utilisé dans le parquet final

Exemples possibles :

- `0`
- `1`
- `2`

## Pourquoi on ne passe pas directement de `row.label` à `labelId`

Parce que `row.label` peut encore valoir :

```text
PNEUMONIA
```

et cette valeur ne suffit pas à dire directement s'il faut produire :

- `PNEUMONIA_BACTERIA`
- ou `PNEUMONIA_VIRUS`

Il faut donc d'abord résoudre le label final à partir du nom de fichier.

## Transformation réelle

Exemple 1 :

```text
PNEUMONIA
  -> PNEUMONIA_BACTERIA
  -> 1
```

Exemple 2 :

```text
PNEUMONIA
  -> PNEUMONIA_VIRUS
  -> 2
```

## Pourquoi c'est utile

Cette fiche sert surtout à clarifier :

- quelles variables interviennent réellement
- dans quel ordre elles apparaissent
- quel est leur type
- et quel est leur rôle dans le pipeline

## Où c'est utilisé dans le projet

- [ImagePreprocessor.scala:75](../../../src/main/scala/preprocess/ImagePreprocessor.scala#L75)
- [ImagePreprocessor.scala:247](../../../src/main/scala/preprocess/ImagePreprocessor.scala#L247)

## Points de syntaxe à retenir

- `row.label` est un `String`
- `targetLabel` est un `String`
- `labelId` est un `Int`
- `config.labelMapping.getOrElse(...)` intervient seulement après `resolveTargetLabel(row)`

## Code complet exécutable

```scala
object WhyLabelResolutionHappensBeforeLabelIdExample {
  final case class ImageRecord(label: String, fileName: String)

  def resolveTargetLabel(row: ImageRecord): String =
    row.label match {
      case "NORMAL" =>
        "NORMAL"

      case "PNEUMONIA" =>
        val fileNameLower = row.fileName.trim.toLowerCase

        if (fileNameLower.contains("bacteria")) "PNEUMONIA_BACTERIA"
        else if (fileNameLower.contains("virus")) "PNEUMONIA_VIRUS"
        else throw new IllegalArgumentException("Unknown pneumonia subtype")

      case other =>
        throw new IllegalArgumentException(s"Unsupported label: $other")
    }

  def main(args: Array[String]): Unit = {
    val row = ImageRecord(
      label = "PNEUMONIA",
      fileName = "person1_bacteria_1.jpeg"
    )

    val labelMapping = Map(
      "NORMAL" -> 0,
      "PNEUMONIA_BACTERIA" -> 1,
      "PNEUMONIA_VIRUS" -> 2
    )

    val targetLabel = resolveTargetLabel(row)
    val labelId = labelMapping(targetLabel)

    println(s"rawLabel    = ${row.label}")
    println(s"fileName    = ${row.fileName}")
    println(s"targetLabel = $targetLabel")
    println(s"labelId     = $labelId")
  }
}
```
