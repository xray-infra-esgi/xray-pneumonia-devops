# `Seq` vs `Array` vs `Map`

## Idée

Ces trois types apparaissent souvent dans le projet, mais ils ne servent pas au même usage.

## `Seq`

Exemple :

```scala
Seq("NORMAL", "PNEUMONIA")
```

Dans le projet, `Seq` sert souvent à représenter :

- une suite d'éléments ordonnée
- comme des catégories ou des features

## `Array`

Exemple :

```scala
Array(0.10f, 0.25f, 0.65f)
```

Dans le projet, `Array[Float]` est utilisé pour :

- les scores de sortie du modèle TensorFlow

## `Map`

Exemple :

```scala
Map("NORMAL" -> 0, "PNEUMONIA_BACTERIA" -> 1, "PNEUMONIA_VIRUS" -> 2)
```

Dans le projet, `Map` sert souvent à représenter :

- un mapping
- ou des counts par clé

## Pourquoi ces choix

- `Seq` : quand on veut une suite ordonnée
- `Array` : quand on veut un petit bloc numérique indexable directement
- `Map` : quand on veut faire une correspondance clé -> valeur

## Où c'est utilisé dans le projet

- [RunConfig.scala:10](../../../src/main/scala/preprocess/RunConfig.scala#L10)
- [ImagePreprocessor.scala:44](../../../src/main/scala/preprocess/ImagePreprocessor.scala#L44)
- [TensorflowPredictor.scala:28](../../../src/main/scala/inference/TensorflowPredictor.scala#L28)

## Points de syntaxe à retenir

- `Seq(...)` crée une séquence
- `Array(...)` crée un tableau
- `Map(k -> v)` crée une association clé/valeur
- `getOrElse(...)` est très utile avec `Map`

## Code complet exécutable

```scala
object SeqVsArrayVsMapExample {
  def main(args: Array[String]): Unit = {
    val categories = Seq("NORMAL", "PNEUMONIA")
    val scores = Array(0.10f, 0.25f, 0.65f)
    val labelMapping = Map("NORMAL" -> 0, "PNEUMONIA_BACTERIA" -> 1, "PNEUMONIA_VIRUS" -> 2)

    println(s"categories   = $categories")
    println(s"scores       = ${scores.mkString("Array(", ", ", ")")}")
    println(s"labelMapping = $labelMapping")
  }
}
```
