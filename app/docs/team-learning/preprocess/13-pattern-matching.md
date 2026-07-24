# Pattern matching

## Idée

Le pattern matching avec `match` permet de traiter plusieurs cas de façon lisible.

## Cas concret du projet

Dans `resolveTargetLabel`, on fait :

```scala
row.label match {
  case "NORMAL" => ...
  case "PNEUMONIA" => ...
  case other => ...
}
```

## Entrée

Par exemple :

```scala
val label = "PNEUMONIA"
```

## Transformation

`match` va comparer la valeur avec les `case` dans l'ordre.

Ici :

- si la valeur vaut `"NORMAL"`, on prend le premier cas
- si elle vaut `"PNEUMONIA"`, on prend le second
- sinon, on tombe dans `case other =>`

## Sortie

Une seule branche est exécutée.

## Pourquoi c'est utile

Cette syntaxe est souvent plus claire qu'un gros bloc `if / else`.

Elle est très fréquente en Scala.

## Où c'est utilisé dans le projet

- [ImagePreprocessor.scala:75](../../../src/main/scala/preprocess/ImagePreprocessor.scala#L75)

## Points de syntaxe à retenir

- `match` ouvre le pattern matching
- `case "X" =>` teste une valeur précise
- `case other =>` récupère toute autre valeur
- `_` sert quand on veut ignorer une valeur

## Code complet exécutable

```scala
object PatternMatchingExample {
  def describe(label: String): String =
    label match {
      case "NORMAL" => "healthy case"
      case "PNEUMONIA" => "raw pneumonia folder label"
      case other => s"unexpected label: $other"
    }

  def main(args: Array[String]): Unit = {
    println(describe("NORMAL"))
    println(describe("PNEUMONIA"))
    println(describe("OTHER"))
  }
}
```
