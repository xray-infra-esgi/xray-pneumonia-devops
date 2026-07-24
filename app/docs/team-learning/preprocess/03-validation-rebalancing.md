# Validation Rebalancing

## Idée

Le split `val` du dataset source n'était pas adapté au passage à 3 classes.

Avant rééquilibrage :

```text
val:
label 0 -> 8
label 1 -> 8
label 2 -> 0
```

La classe virale était absente du split de validation.

## Objectif

Construire un split `val` final contenant :

- `100` exemples de la classe `0`
- `100` exemples de la classe `1`
- `100` exemples de la classe `2`

sans toucher au split `test`.

## Entrée

Exemple simplifié :

```scala
val currentValCounts = Map(
  0 -> 8L,
  1 -> 8L,
  2 -> 0L
)

val targetValCountPerClass = 100
```

## Transformation

Le code calcule d'abord combien il manque par classe :

```scala
val missingCounts: Map[Int, Int] = expectedLabelIds.map { labelId =>
  val currentValCount = currentValCounts.getOrElse(labelId, 0L)
  val missing = math.max(0, targetValCountPerClass - currentValCount.toInt)
  labelId -> missing
}.toMap
```

Avec l'exemple ci-dessus, on obtient :

```scala
Map(
  0 -> 92,
  1 -> 92,
  2 -> 100
)
```

Ensuite :

- on prélève ces exemples dans `train`
- on change leur `split` en `val`
- on retire ces lignes du `train`
- on réassemble `train`, `val`, `extraVal` et `test`

## Sortie

Exemple de résultat visé :

```text
val:
label 0 -> 100
label 1 -> 100
label 2 -> 100
```

## Pourquoi c'est utile

Un split de validation sans une des classes du problème rend l'entraînement et l'évaluation beaucoup moins fiables.

Le rééquilibrage permet :

- d'avoir les 3 classes en validation
- de garder `test` inchangé
- d'éviter de dupliquer des exemples entre `train` et `val`

## Où c'est utilisé dans le projet

- [ImagePreprocessor.scala:154](../../../src/main/scala/preprocess/ImagePreprocessor.scala#L154)
- [RunConfig.scala:15](../../../src/main/scala/preprocess/RunConfig.scala#L15)
- [preprocessing.conf:10](../../../conf/preprocessing.conf#L10)

## Points de syntaxe à retenir

- `getOrElse(labelId, 0L)` sert à gérer le cas où une classe est absente de la map
- `math.max(0, ...)` évite d'obtenir un nombre négatif
- `Map[Int, Long]` et `Map[Int, Int]` ne représentent pas exactement la même chose
- `left_anti` sert à retirer du `train` les lignes déjà sélectionnées pour compléter `val`

## Code complet exécutable

Le code suivant est un mini exemple Scala autonome.

Il ne dépend pas de Spark et il montre seulement le calcul de `missingCounts`.

```scala
object ValidationRebalancingExample {
  def main(args: Array[String]): Unit = {
    val expectedLabelIds = Seq(0, 1, 2)
    val currentValCounts = Map(0 -> 8L, 1 -> 8L, 2 -> 0L)
    val targetValCountPerClass = 100

    val missingCounts = expectedLabelIds.map { labelId =>
      val currentValCount = currentValCounts.getOrElse(labelId, 0L)
      val missing = math.max(0, targetValCountPerClass - currentValCount.toInt)
      labelId -> missing
    }.toMap

    println(s"currentValCounts = $currentValCounts")
    println(s"missingCounts    = $missingCounts")
  }
}
```
