# zipWithIndex + maxBy

## Idée

En inférence multiclasses, le modèle renvoie un score par classe.

Il faut ensuite choisir :

- la classe prédite
- le score associé à cette classe

## Entrée

```scala
val scores = Array(0.05f, 0.20f, 0.75f)
```

Ici, on peut lire ce tableau comme :

- score de la classe `0` : `0.05`
- score de la classe `1` : `0.20`
- score de la classe `2` : `0.75`

## Transformation

### Étape 1

```scala
scores.zipWithIndex
```

Résultat :

```scala
Array((0.05f, 0), (0.20f, 1), (0.75f, 2))
```

Chaque score est maintenant associé à son indice.

### Étape 2

```scala
scores.zipWithIndex.maxBy { case (score, _) => score }
```

Résultat :

```scala
(0.75f, 2)
```

`maxBy` choisit le tuple dont le premier élément, `score`, est maximal.

### Étape 3

```scala
val (predictedScore, predictedLabelId) =
  scores.zipWithIndex.maxBy { case (score, _) => score }
```

Résultat final :

```scala
predictedScore   = 0.75f
predictedLabelId = 2
```

Ici, on utilise une **déstructuration**.

Cela veut dire que Scala prend le tuple retourné par :

```scala
scores.zipWithIndex.maxBy { case (score, _) => score }
```

et répartit directement ses deux éléments dans deux variables :

- `predictedScore`
- `predictedLabelId`

Autrement dit, si le résultat est :

```scala
(0.75f, 2)
```

alors Scala comprend :

```scala
val predictedScore = 0.75f
val predictedLabelId = 2
```

mais sous une forme plus compacte :

```scala
val (predictedScore, predictedLabelId) = ...
```

C'est une syntaxe générale de Scala, pas quelque chose de spécifique à Spark ou à ce projet.

## Sortie

- `predictedLabelId = 2`
- `predictedScore = 0.75f`

## Pourquoi c'est utile

Cette construction permet de transformer directement les scores du modèle en prédiction finale.

Elle évite :

- un seuil binaire
- une logique spéciale par classe

Elle marche naturellement avec une sortie multiclasses.

## Où c'est utilisé dans le projet

- [InferencePipelineRunner.scala:47](../../../src/main/scala/inference/InferencePipelineRunner.scala#L47)

## Points de syntaxe à retenir

- `zipWithIndex` transforme chaque élément en tuple `(valeur, index)`
- `maxBy` choisit l'élément qui maximise l'expression donnée
- `case (score, _) => score` signifie :
  on ne compare que sur `score`
- `val (a, b) = tuple` est une décomposition de tuple
- cette décomposition évite d'utiliser `_1` et `_2`

## Code complet exécutable

```scala
object ZipWithIndexMaxByExample {
  def main(args: Array[String]): Unit = {
    val scores = Array(0.05f, 0.20f, 0.75f)

    val (predictedScore, predictedLabelId) =
      scores.zipWithIndex.maxBy { case (score, _) => score }

    println(s"scores            = ${scores.mkString("Array(", ", ", ")")}")
    println(s"predictedScore    = $predictedScore")
    println(s"predictedLabelId  = $predictedLabelId")
  }
}
```

Sortie attendue :

```text
scores            = Array(0.05, 0.2, 0.75)
predictedScore    = 0.75
predictedLabelId  = 2
```
