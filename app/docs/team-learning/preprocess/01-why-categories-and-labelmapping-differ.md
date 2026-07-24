# Pourquoi `categories` et `labelMapping` sont différents

## Idée

Dans le projet, `categories` et `labelMapping` ne décrivent pas exactement la même chose.

- `categories` décrit les dossiers bruts lus sur disque
- `labelMapping` décrit les labels finaux utilisés par le modèle

## Cas concret du projet

### `categories`

```scala
Seq("NORMAL", "PNEUMONIA")
```

### `labelMapping`

```scala
Map(
  "NORMAL" -> 0,
  "PNEUMONIA_BACTERIA" -> 1,
  "PNEUMONIA_VIRUS" -> 2
)
```

## Pourquoi ce n'est pas contradictoire

Le dossier du dataset est encore :

```text
train/
  NORMAL/
  PNEUMONIA/
```

Donc pour lire les images sur disque, on a bien besoin de :

```scala
categories = Seq("NORMAL", "PNEUMONIA")
```

Mais le modèle final ne travaille plus avec seulement :

- `NORMAL`
- `PNEUMONIA`

Il travaille avec :

- `NORMAL`
- `PNEUMONIA_BACTERIA`
- `PNEUMONIA_VIRUS`

## Transformation intermédiaire

Entre les deux, il existe une étape de résolution du label final :

```scala
"PNEUMONIA" + fileName contenant "bacteria"
  -> "PNEUMONIA_BACTERIA"

"PNEUMONIA" + fileName contenant "virus"
  -> "PNEUMONIA_VIRUS"
```

## Sortie

On peut résumer le flux comme ceci :

```text
dossier brut lu sur disque
  -> label brut
  -> label final
  -> labelId
```

Exemple :

```text
PNEUMONIA
  -> PNEUMONIA_BACTERIA
  -> 1
```

## Pourquoi c'est utile

Cette distinction évite une confusion fréquente :

- ce qu'on lit sur disque
- ce que le modèle doit prédire

## Où c'est utilisé dans le projet

- [RunConfig.scala:8](../../../src/main/scala/preprocess/RunConfig.scala#L8)
- [ImagePreprocessor.scala:75](../../../src/main/scala/preprocess/ImagePreprocessor.scala#L75)
- [preprocessing.conf:4](../../../conf/preprocessing.conf#L4)

## Points de syntaxe à retenir

- `Seq(...)` représente ici une liste ordonnée de catégories sources
- `Map(...)` représente ici un mapping entre label final texte et identifiant numérique
- deux structures différentes peuvent décrire deux niveaux différents du pipeline

## Code complet exécutable

```scala
object CategoriesVsLabelMappingExample {
  def main(args: Array[String]): Unit = {
    val categories = Seq("NORMAL", "PNEUMONIA")
    val labelMapping = Map(
      "NORMAL" -> 0,
      "PNEUMONIA_BACTERIA" -> 1,
      "PNEUMONIA_VIRUS" -> 2
    )

    println(s"categories   = $categories")
    println(s"labelMapping = $labelMapping")
  }
}
```
