# Pourquoi `close()` est important

## Idée

Le predictor manipule des ressources TensorFlow JVM qui ne sont pas de simples objets Scala ordinaires.

## Pourquoi

Ces ressources peuvent inclure :

- mémoire native
- objets internes TensorFlow
- handles de runtime

Il faut donc libérer proprement ces ressources.

## Qu'est-ce que la mémoire native ?

Quand on écrit du Scala, on manipule en général des objets gérés par la JVM.

Cela veut dire que :

- la JVM alloue ces objets
- le garbage collector les nettoie plus tard

Mais avec TensorFlow JVM, certaines ressources importantes vivent en dehors de la mémoire classique de la JVM.

La mémoire native désigne ici de la mémoire allouée en dehors du tas habituel de la JVM, par exemple :

- des buffers internes TensorFlow
- des structures internes du runtime TensorFlow
- des ressources liées au modèle chargé

Ces ressources ne sont pas de simples objets Scala ordinaires.

## Pourquoi le garbage collector ne suffit pas

Le garbage collector sait nettoyer les objets JVM lorsqu'ils ne sont plus référencés.

Mais cela ne garantit pas une libération immédiate et explicite des ressources natives associées derrière.

Autrement dit :

- un objet Scala peut sembler normal
- alors qu'il possède en réalité des ressources natives coûteuses derrière lui

C'est pour cela qu'une fermeture explicite est importante avec TensorFlow JVM.

## Cas concret du projet

Le predictor implémente :

```scala
extends AutoCloseable
```

et définit :

```scala
override def close(): Unit = {
  modelBundle.close()
}
```

## Pourquoi c'est utile

Cela évite de laisser des ressources ouvertes plus longtemps que nécessaire.

Dans le contexte TensorFlow JVM, cela aide à éviter par exemple :

- de garder un modèle chargé inutilement
- de conserver des buffers natifs plus longtemps que nécessaire
- d'accumuler des ressources ouvertes dans un pipeline distribué

## Lien avec `TensorflowPredictor`

Dans notre projet, `TensorflowPredictor` garde en mémoire :

- un `SavedModelBundle`
- une fonction d'inférence TensorFlow

Ces objets ne doivent pas être vus comme de simples valeurs Scala sans coût de fermeture.

Le `close()` du predictor existe justement pour fermer proprement ce qui a été ouvert côté TensorFlow.

## Pourquoi cette fermeture est importante dans un pipeline Spark

Dans notre projet, un predictor est créé à l'intérieur de `mapPartitions`.

Cela veut dire qu'un même predictor est utilisé pour toutes les lignes d'une partition, puis doit être fermé à la fin de cette consommation.

L'idée n'est donc pas seulement :

- "fermer un objet par principe"

mais plutôt :

- ouvrir la ressource au bon moment
- l'utiliser pendant toute la durée utile
- la fermer dès qu'elle n'est plus nécessaire

Ce lifecycle est important dans un pipeline distribué, parce que le même schéma peut se répéter sur plusieurs partitions.

## Que pourrait-il se passer si on ne fermait pas correctement ?

À petite échelle, cela peut passer inaperçu.

Mais à plus grande échelle, on risque plus facilement :

- d'accumuler des ressources natives ouvertes
- de garder des modèles chargés trop longtemps
- d'augmenter inutilement la pression mémoire

L'idée générale est donc :

- `close()` n'existe pas ici pour faire joli
- `close()` fait partie du bon usage de TensorFlow JVM

## Où c'est utilisé dans le projet

- [TensorflowPredictor.scala:20](../../../src/main/scala/inference/TensorflowPredictor.scala#L20)
- [TensorflowPredictor.scala:69](../../../src/main/scala/inference/TensorflowPredictor.scala#L69)
- [InferencePipelineRunner.scala:30](../../../src/main/scala/inference/InferencePipelineRunner.scala#L30)

## Points de syntaxe à retenir

- `AutoCloseable` signale qu'un objet possède une ressource à fermer
- `close()` représente l'action de libération explicite
- en TensorFlow JVM, certaines ressources importantes vivent en dehors de la mémoire classique de la JVM
