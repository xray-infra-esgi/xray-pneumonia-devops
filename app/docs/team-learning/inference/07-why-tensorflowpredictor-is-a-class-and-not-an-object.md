# Pourquoi `TensorflowPredictor` est une class et pas un object

## Idée

`TensorflowPredictor` garde un état interne.

## Pourquoi

Le predictor conserve :

- le `SavedModelBundle`
- la fonction d'inférence

Ces éléments sont chargés à la création de l'instance.

## Cas concret du projet

Le code fait :

```scala
final class TensorflowPredictor(modelPath: String) extends AutoCloseable {
  private val modelBundle = SavedModelBundle.load(modelPath, "serve")
  private val inferenceFunction = modelBundle.function("serve")
}
```

## Que représente `SavedModelBundle.load(modelPath, "serve")` ?

Le predictor charge un `SavedModel`.

Le deuxième argument, ici `"serve"`, est un **tag** TensorFlow.

Un tag sert à dire :

- quel ensemble exporté du modèle on veut ouvrir
- dans quel contexte on charge le modèle

Dans notre cas, `"serve"` correspond au contexte d'inférence.

Il ne faut pas confondre ce tag avec l'endpoint.

## Qu'est-ce qu'un contexte de chargement ?

Un contexte de chargement indique **quelle version utilisable du modèle** on veut ouvrir à l'intérieur du `SavedModel`.

Autrement dit, un même modèle exporté peut contenir plusieurs graphes ou plusieurs ensembles de ressources selon l'usage prévu.

Le tag sert alors à dire :

- "je veux charger la version du modèle prévue pour le serving"
- ou un autre contexte si le modèle en expose plusieurs

Dans notre projet, on charge le contexte `serve` parce qu'on veut faire de l'inférence.

## Pourquoi un modèle pourrait avoir plusieurs contextes ?

Parce qu'un modèle TensorFlow peut être utilisé dans plusieurs situations différentes.

Par exemple :

- entraînement
- évaluation
- serving / inférence

Ces usages n'ont pas toujours exactement les mêmes besoins.

Selon le cas, on peut vouloir :

- des fonctions différentes
- des signatures d'entrée / sortie différentes
- des graphes spécialisés pour un certain usage

Le système de tags sert justement à distinguer ces ensembles.

## Que veut dire "charger un autre contexte" concrètement ?

Cela veut dire :

- ne pas ouvrir exactement le même ensemble exporté
- ne pas exposer forcément les mêmes fonctions
- ne pas viser forcément le même usage

Dans notre cas, on ne veut pas charger un contexte d'entraînement ou un autre contexte technique.

On veut ouvrir le modèle tel qu'il a été exporté pour être appelé en inférence.

## Tag vs endpoint

Dans le code du projet, on voit deux fois `"serve"` :

```scala
private val modelBundle = SavedModelBundle.load(modelPath, "serve")
private val inferenceFunction = modelBundle.function("serve")
```

Mais ces deux usages ne représentent pas exactement la même chose.

### `SavedModelBundle.load(modelPath, "serve")`

Ici, `"serve"` est le **tag** :

- il sert à charger le bon ensemble exporté du modèle

### `modelBundle.function("serve")`

Ici, `"serve"` est le **nom de la fonction d'inférence exposée** par le modèle :

- c'est l'endpoint qu'on va appeler pour faire les prédictions

## Pourquoi c'est important pour comprendre le choix d'une class

Le predictor garde donc plusieurs éléments internes :

- le chemin du modèle fourni au constructeur
- le `SavedModelBundle` chargé
- la fonction d'inférence récupérée depuis ce bundle

Ce ne sont pas de simples calculs temporaires.

Ce sont de vraies ressources ou dépendances internes dont l'objet a besoin pendant sa durée de vie.

## Pourquoi pas un object

Un `object` unique global ne convient pas bien ici, car :

- il faudrait gérer plus difficilement le lifecycle des ressources
- le predictor est instancié par partition

## Pourquoi c'est utile

Cette décision permet de bien encapsuler :

- l'état TensorFlow
- le chargement du modèle
- la fermeture des ressources

Elle permet aussi de mieux comprendre le lifecycle du predictor :

- création de l'instance
- chargement du `SavedModel`
- récupération de la fonction d'inférence
- utilisation pendant toute la partition
- fermeture avec `close()`

## Où c'est utilisé dans le projet

- [TensorflowPredictor.scala:20](../../../src/main/scala/inference/TensorflowPredictor.scala#L20)
- [InferencePipelineRunner.scala:25](../../../src/main/scala/inference/InferencePipelineRunner.scala#L25)

## Points de syntaxe à retenir

- une `class` peut porter un état interne
- un `object` singleton représente plutôt une instance unique globale
- ici, chaque partition crée sa propre instance de predictor
- `SavedModelBundle.load(...)` charge le modèle exporté
- le tag `"serve"` sert à choisir le bon contexte de chargement
- `modelBundle.function("serve")` récupère ensuite la fonction d'inférence exposée
