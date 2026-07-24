# Pourquoi `predict` retourne un `Array[Float]`

## Idée

Le predictor ne retourne plus un seul score.

Il retourne maintenant :

```scala
Array[Float]
```

## Pourquoi

Le modèle n'est plus binaire.

Il renvoie une sortie de forme :

```text
(None, 3)
```

Donc pour une image :

- un score pour la classe 0
- un score pour la classe 1
- un score pour la classe 2

## Cas concret du projet

Le code fait :

```scala
Array(
  outputScoreTensor.getFloat(0, 0),
  outputScoreTensor.getFloat(0, 1),
  outputScoreTensor.getFloat(0, 2)
)
```

## Sortie

Exemple :

```scala
Array(0.10f, 0.25f, 0.65f)
```

## Pourquoi c'est utile

Le choix final de la classe est alors fait dans le runner, pas dans le predictor.

Le predictor reste donc responsable seulement de :

- lire les scores du modèle

## Pourquoi on ne décide pas directement la classe finale dans le predictor

Le predictor a un rôle technique :

- charger le modèle
- construire le tenseur d'entrée
- appeler TensorFlow
- lire les scores de sortie

Le runner a ensuite un rôle plus métier / pipeline :

- choisir la classe prédite
- construire `PredictionRow`
- calculer les sorties d'inférence

Cette séparation est utile parce qu'elle garde le predictor simple et réutilisable.

Par exemple, si plus tard on voulait :

- conserver les 3 scores complets dans la sortie
- utiliser une autre règle de décision
- comparer plusieurs stratégies d'interprétation des scores

on pourrait le faire sans modifier la partie TensorFlow du predictor.

## Où c'est utilisé dans le projet

- [TensorflowPredictor.scala:28](../../../src/main/scala/inference/TensorflowPredictor.scala#L28)
- [TensorflowPredictor.scala:52](../../../src/main/scala/inference/TensorflowPredictor.scala#L52)

## Points de syntaxe à retenir

- `Array[Float]` est adapté ici à un petit bloc numérique de scores
- le predictor lit les scores, mais ne décide pas encore la classe finale
