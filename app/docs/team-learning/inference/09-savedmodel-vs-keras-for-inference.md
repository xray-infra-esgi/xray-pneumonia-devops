# `SavedModel` vs `.keras` pour l'inférence

## Idée

Dans le projet, l'inférence Scala charge un `SavedModel`, pas un fichier `.keras`.

## Pourquoi

Le format `.keras` est pratique pour le travail dans Python / Keras.

Le `SavedModel` est le format TensorFlow d'export utilisé côté JVM pour l'inférence.

## À quoi sert chaque format dans le projet

### `.keras`

Le `.keras` sert surtout à :

- recharger facilement le modèle dans Python
- continuer à travailler dans Keras
- relancer un entraînement ou une évaluation dans le notebook

On peut donc le voir comme le format de travail naturel du modèle côté Python.

### `SavedModel`

Le `SavedModel` sert surtout à :

- exporter le modèle pour l'inférence TensorFlow
- l'utiliser hors notebook
- l'intégrer côté JVM / Scala

Dans notre projet, c'est ce format qui sert de pont entre :

- le notebook Python
- le pipeline Spark Scala

## Structure d'un `SavedModel`

Contrairement à `.keras`, un `SavedModel` n'est pas un simple fichier.

C'est un dossier contenant en général :

- `saved_model.pb`
- `variables/`
- `assets/`

Exemple :

```text
ml/artifacts/models/saved_model/
  saved_model.pb
  variables/
  assets/
```

### Que contient `saved_model.pb` ?

Ce fichier contient notamment :

- le graphe exporté
- les signatures d'appel
- les informations sur les entrées et sorties exposées

Dans notre cas, c'est ce qui permet de savoir que le modèle exporté attend :

- une entrée de forme `(None, 128, 128, 1)`
- une sortie de forme `(None, 3)`

### Que contient `variables/` ?

Ce dossier contient les poids du modèle :

- valeurs apprises pendant l'entraînement
- paramètres des couches

### Que contient `assets/` ?

Ce dossier peut contenir des ressources additionnelles.

Selon le modèle, il peut être presque vide, mais il fait partie de la structure standard du `SavedModel`.

## Cas concret du projet

Le predictor fait :

```scala
SavedModelBundle.load(modelPath, "serve")
```

## Pourquoi Scala charge un `SavedModel` et pas un `.keras`

Le code Scala du projet ne passe pas par Keras Python.

Il passe par :

- la JVM
- l'API TensorFlow Java

Or cette API charge naturellement un `SavedModel`.

Donc :

- le notebook entraîne et exporte
- puis Scala recharge ce `SavedModel` pour l'inférence

## Rôle de l'endpoint `serve`

Dans le `SavedModel`, `serve` est le nom d'une fonction d'inférence exposée.

Autrement dit :

- le modèle exporté ne contient pas seulement des poids
- il expose aussi des fonctions appelables

Le predictor doit donc :

- charger le `SavedModel`
- récupérer la fonction `serve`
- lui envoyer un tenseur d'entrée
- lire le tenseur de sortie

Dans notre cas, cette sortie contient ensuite les 3 scores de classes.

## Pourquoi c'est utile

Cette distinction explique pourquoi :

- le notebook entraîne et exporte le modèle
- le code Scala ne recharge pas directement un `.keras`
- le predictor doit connaître l'endpoint d'inférence exposé par le `SavedModel`

## Où c'est utilisé dans le projet

- [TensorflowPredictor.scala:24](../../../src/main/scala/inference/TensorflowPredictor.scala#L24)
- [TensorflowPredictor.scala:25](../../../src/main/scala/inference/TensorflowPredictor.scala#L25)
- [keras-vs-savedmodel.md](../../../docs/keras-vs-savedmodel.md)

## Points de syntaxe à retenir

- ici, le point important est surtout le format du modèle, pas la syntaxe Scala
- `SavedModelBundle.load(...)` charge le modèle exporté
- `serve` est ici le nom de la fonction d'inférence exposée par le modèle
