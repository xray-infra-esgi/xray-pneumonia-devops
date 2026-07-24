# Pourquoi la taille de batch est 1 dans le predictor

## Idée

Le predictor construit un tenseur d'entrée avec :

- `batchSize = 1`

## Pourquoi

Dans le pipeline Scala actuel, `predict(...)` traite une seule ligne de features à la fois.

Or une ligne de features correspond à :

- une image

Donc le batch d'entrée contient une seule image.

## Cas concret du projet

```scala
Shape.of(1L, height.toLong, width.toLong, 1L)
```

Le premier `1L` correspond à la dimension batch.

## Pourquoi c'est utile

Cette fiche clarifie le lien entre :

- une ligne du parquet
- une image
- un batch TensorFlow de taille 1

## Où c'est utilisé dans le projet

- [TensorflowPredictor.scala:35](../../../src/main/scala/inference/TensorflowPredictor.scala#L35)

## Points de syntaxe à retenir

- la dimension batch existe même si on traite un seul exemple
- ici, `1L` signifie : un seul exemple dans le tenseur d'entrée
