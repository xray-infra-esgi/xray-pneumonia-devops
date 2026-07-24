# Pourquoi le predictor doit rester ouvert pendant la consommation de la partition

## Idée

Le predictor ne doit pas être fermé trop tôt.

Avec `mapPartitions`, Spark fournit à la fonction un `Iterator` sur les lignes de la partition courante.

Dans le projet, cela part de ce code :

```scala
val featuresDataset = spark.read
  .parquet(config.featuresPath)
  .as[FeatureRow]

val predictionDataset = featuresDataset.mapPartitions { rows =>
  ...
}
```

Ici :

- `featuresDataset` est un `Dataset[FeatureRow]`
- `rows` est un `Iterator[FeatureRow]`

C'est pour cela que cette fiche parle directement d'iterator, de `hasNext` et de `next()`.

## Le problème

Spark évalue les transformations de façon paresseuse.

Cela veut dire que les opérations s'enchaînent d'abord pour construire un plan de calcul.
Ce plan n'est exécuté qu'au moment d'une action comme :

- `count()`
- `collect()`
- `write.parquet(...)`

Ensuite, pendant cette exécution, l'iterator retourné par `mapPartitions` est lui aussi consommé progressivement.

Autrement dit :

- créer l'iterator ne produit pas encore toutes les prédictions
- les lignes sont lues et transformées au fur et à mesure

Donc si on crée un predictor puis qu'on le ferme juste après avoir construit un iterator, on risque de le fermer avant que les lignes ne soient réellement consommées.

## Cas concret du projet

Le runner utilise un iterator custom :

```scala
new Iterator[PredictionRow] {
  ...
  override def hasNext: Boolean = {
    val hasMoreRows = rows.hasNext
    if (!hasMoreRows) closeIfNeeded()
    hasMoreRows
  }
}
```

La fermeture est donc déclenchée à la fin de la consommation réelle des lignes.

## Qu'est-ce qu'un iterator ?

Un `Iterator` est un objet qui produit des éléments un par un.

Les deux méthodes importantes sont :

- `hasNext` : est-ce qu'il reste encore un élément à produire ?
- `next()` : donne le prochain élément

## Que signifie `new Iterator[PredictionRow] { ... }` ?

Cette syntaxe signifie qu'on crée un iterator anonyme.

Autrement dit :

- on ne crée pas une classe nommée à part
- on crée directement un objet qui implémente `Iterator[PredictionRow]`

Dans cet objet, on doit définir :

- `hasNext`
- `next()`

## D'où viennent les lignes d'entrée ?

Dans `mapPartitions`, Spark fournit :

- `rows`

qui est un `Iterator[FeatureRow]` pour la partition courante.

Notre iterator de sortie produit ensuite des `PredictionRow`.

Le lien entre les deux se fait ici :

```scala
val row = rows.next()
```

Cette ligne signifie :

- prendre la prochaine ligne d'entrée
- puis la transformer en prédiction

## Comment l'iterator est consommé

Spark consomme ensuite l'iterator de sortie comme n'importe quel consommateur d'iterator Scala :

- il vérifie s'il reste encore des éléments à produire
- puis il demande le prochain élément

Dans notre implémentation, la fermeture du predictor est donc déclenchée quand `hasNext` détecte qu'il n'y a plus de lignes à produire.

## Pourquoi c'est utile

Cela évite le bug :

- modèle fermé trop tôt
- puis erreur lors de la prédiction suivante

## Où c'est utilisé dans le projet

- [InferencePipelineRunner.scala:27](../../../src/main/scala/inference/InferencePipelineRunner.scala#L27)
- [InferencePipelineRunner.scala:37](../../../src/main/scala/inference/InferencePipelineRunner.scala#L37)

## Points de syntaxe à retenir

- un iterator peut être consommé plus tard
- `hasNext` peut être utilisé pour savoir quand fermer la ressource
- ici, le lifecycle du predictor dépend de la consommation effective de l'iterator
- `new Iterator[String] { ... }` crée un iterator anonyme
- `rows.next()` récupère le prochain élément de l'iterator d'entrée

## Code complet exécutable

```scala
object WhyPredictorMustStayOpenExample {
  final class FakePredictor {
    private var closed = false

    def predict(row: String): String = {
      if (closed) {
        throw new IllegalStateException("predictor already closed")
      }
      s"prediction($row)"
    }

    def close(): Unit = {
      closed = true
      println("predictor closed")
    }
  }

  def main(args: Array[String]): Unit = {
    val rows = Iterator("row1", "row2")
    val predictor = new FakePredictor

    val predictions = new Iterator[String] {
      override def hasNext: Boolean = {
        val hasMore = rows.hasNext
        if (!hasMore) {
          predictor.close()
        }
        hasMore
      }

      override def next(): String = {
        val row = rows.next()
        predictor.predict(row)
      }
    }

    println(predictions.next())
    println(predictions.next())
  }
}
```
