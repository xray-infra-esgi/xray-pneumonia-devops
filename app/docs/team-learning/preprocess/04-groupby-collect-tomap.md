# groupBy + collect + toMap

## Idée

Dans le preprocess, certaines opérations commencent côté Spark, puis finissent côté Scala local.

Exemple typique :

- Spark calcule des counts par classe
- puis Scala transforme ce petit résultat en `Map[Int, Long]`

## Cas concret du projet

Le code du projet utilise une logique de ce type :

```scala
val currentValCounts: Map[Int, Long] = valDf
  .groupBy("labelId")
  .count()
  .collect()
  .map { row =>
    val labelId = row.getAs[Int]("labelId")
    val count = row.getAs[Long]("count")
    labelId -> count
  }
  .toMap
```

## Entrée

Imaginons un `valDf` contenant :

```text
labelId
-------
0
0
1
1
1
2
```

## Transformation

### Étape 1 : groupBy + count

```scala
valDf.groupBy("labelId").count()
```

Résultat conceptuel :

```text
labelId | count
--------+------
0       | 2
1       | 3
2       | 1
```

Cette partie est encore gérée par Spark.

### Étape 2 : collect

```scala
.collect()
```

Cette étape ramène le petit résultat sur le driver Scala.

On obtient alors un tableau de lignes Spark.

### Étape 3 : map

```scala
.map { row =>
  val labelId = row.getAs[Int]("labelId")
  val count = row.getAs[Long]("count")
  labelId -> count
}
```

Résultat conceptuel :

```scala
Array(
  0 -> 2L,
  1 -> 3L,
  2 -> 1L
)
```

### Explication détaillée de `getAs[Int](...)` et `getAs[Long](...)`

La syntaxe suivante peut sembler étrange quand on débute :

```scala
row.getAs[Int]("labelId")
row.getAs[Long]("count")
```

Parce qu'on peut avoir l'habitude de voir des fonctions appelées seulement avec des arguments entre parenthèses, par exemple :

```scala
myFunction("labelId")
```

Ici, il y a en plus :

- un type entre crochets

## Que veulent dire les crochets ?

En Scala, les crochets servent souvent à passer un **paramètre de type**.

Autrement dit :

- ce n'est pas une valeur
- ce n'est pas une chaîne
- ce n'est pas un argument "normal"

C'est une information de type que l'on donne à la méthode.

Dans :

```scala
row.getAs[Int]("labelId")
```

cela veut dire :

- appelle la méthode `getAs`
- en lui demandant de me retourner la valeur comme un `Int`

Et dans :

```scala
row.getAs[Long]("count")
```

cela veut dire :

- appelle la même méthode `getAs`
- mais cette fois en demandant une valeur de type `Long`

## Pourquoi il y a à la fois des crochets et des parenthèses ?

Parce qu'ici la méthode reçoit deux choses différentes :

### 1. Un paramètre de type

Exemple :

```scala
[Int]
```

ou :

```scala
[Long]
```

Cela indique le type attendu en sortie.

### 2. Un argument classique

Exemple :

```scala
("labelId")
```

ou :

```scala
("count")
```

Cela indique quelle colonne on veut lire dans la ligne Spark.

On peut donc lire :

```scala
row.getAs[Int]("labelId")
```

comme :

> récupère dans cette ligne la colonne `"labelId"` et retourne-la comme un `Int`

## Est-ce un principe général en Scala ?

Oui.

Ce n'est pas une bizarrerie propre à Spark.

C'est une idée générale en Scala :

- certaines méthodes prennent des **paramètres de type**
- en plus de leurs arguments classiques

Par exemple, dans d'autres contextes Scala, on peut voir des choses du même style :

```scala
List.empty[Int]
Option.empty[String]
```

ou encore :

```scala
myMethod[Double](...)
```

L'idée reste la même :

- les crochets servent à préciser un type
- les parenthèses servent à passer des valeurs

## Pourquoi Spark utilise cette syntaxe ici

Une `Row` Spark peut contenir plusieurs colonnes de types différents.

Donc quand on écrit :

```scala
row.getAs[Int]("labelId")
```

on dit explicitement à Spark :

- la colonne s'appelle `"labelId"`
- je m'attends à lire un `Int`

Cela rend le code :

- plus clair
- plus typé
- plus sûr qu'une lecture totalement non typée

## Lecture mentale recommandée

Tu peux t'entraîner à lire :

```scala
row.getAs[Int]("labelId")
```

comme :

> get as Int, column labelId

et :

```scala
row.getAs[Long]("count")
```

comme :

> get as Long, column count

Cette lecture mentale aide beaucoup au début.

### Étape 4 : toMap

```scala
.toMap
```

Résultat final :

```scala
Map(
  0 -> 2L,
  1 -> 3L,
  2 -> 1L
)
```

## Sortie

```scala
Map[Int, Long]
```

## Pourquoi c'est utile

Le résultat agrégé est très petit :

- une ligne par classe

Donc il est raisonnable de :

- calculer distribué avec Spark
- puis ramener ce mini résultat côté Scala local

## Où c'est utilisé dans le projet

- [ImagePreprocessor.scala:163](../../../src/main/scala/preprocess/ImagePreprocessor.scala#L163)

## Points de syntaxe à retenir

- `groupBy(...).count()` fait l'agrégation côté Spark
- `collect()` ramène le résultat côté driver
- `row.getAs[...]("nomColonne")` lit une valeur typée dans une ligne Spark
- `labelId -> count` construit une paire clé/valeur
- `.toMap` transforme une collection de paires en `Map`

## Code complet exécutable

```scala
object GroupByCollectToMapExample {
  def main(args: Array[String]): Unit = {
    val labelIds = Seq(0, 0, 1, 1, 1, 2)

    val counts = labelIds
      .groupBy(identity)
      .view
      .mapValues(_.size.toLong)
      .toMap

    println(s"labelIds = $labelIds")
    println(s"counts   = $counts")
  }
}
```
