# Comment l'endpoint du SavedModel est appelé

## Idée

Charger le modèle et appeler l'inférence sont deux étapes différentes.

Dans le projet, on ne fait pas simplement :

- "charger un fichier"
- puis "obtenir directement une prédiction"

Il y a en réalité plusieurs étapes distinctes.

## Cas concret du projet

Le code fait d'abord :

```scala
private val modelBundle = SavedModelBundle.load(modelPath, "serve")
```

Puis :

```scala
private val inferenceFunction = modelBundle.function("serve")
```

Et enfin, pendant `predict(...)` :

```scala
val outputTensor = inferenceFunction.call(inputTensor)
```

## Ce que cela signifie

### Étape 1

On charge le `SavedModel` dans la JVM.

Cette étape ouvre le modèle exporté et le rend disponible côté TensorFlow JVM.

### Étape 2

On récupère la fonction d'inférence exposée par le modèle.

Autrement dit :

- le modèle chargé contient des fonctions appelables
- on choisit ici celle nommée `serve`

### Étape 3

On appelle cette fonction avec un tenseur d'entrée.

Cette étape est la vraie exécution de l'inférence.

Le predictor doit donc :

1. charger le modèle
2. récupérer la fonction d'inférence
3. construire le tenseur d'entrée
4. appeler la fonction
5. lire le tenseur de sortie

## Tag de chargement et endpoint

Dans le code du projet, on voit deux fois `serve` :

```scala
SavedModelBundle.load(modelPath, "serve")
modelBundle.function("serve")
```

Mais ces deux usages ne représentent pas exactement la même chose.

### Dans `load(modelPath, "serve")`

`"serve"` est le tag de chargement.

Il sert à ouvrir le bon contexte du `SavedModel`.

### Dans `modelBundle.function("serve")`

`"serve"` est le nom de la fonction d'inférence exposée.

Il sert à récupérer l'endpoint qu'on veut appeler.

## Pourquoi c'est utile

Cette fiche aide à distinguer :

- chargement du modèle
- récupération de la fonction
- exécution réelle de l'inférence

Elle aide aussi à ne pas confondre :

- le tag de chargement
- l'endpoint d'inférence

## Où c'est utilisé dans le projet

- [TensorflowPredictor.scala:24](../../../src/main/scala/inference/TensorflowPredictor.scala#L24)
- [TensorflowPredictor.scala:25](../../../src/main/scala/inference/TensorflowPredictor.scala#L25)
- [TensorflowPredictor.scala:49](../../../src/main/scala/inference/TensorflowPredictor.scala#L49)

## Points de syntaxe à retenir

- `SavedModelBundle.load(...)` charge le modèle
- `modelBundle.function("serve")` récupère l'endpoint
- `call(...)` exécute réellement la prédiction
- dans le projet, le predictor garde séparément :
  - le modèle chargé
  - la fonction d'inférence récupérée

## Code complet exécutable

```scala
object HowSavedModelEndpointIsCalledExample {
  final class FakeFunction(name: String) {
    def call(inputTensor: String): String =
      s"output from $name with input=$inputTensor"
  }

  final class FakeModelBundle(tag: String) {
    def function(name: String): FakeFunction = {
      println(s"get function '$name' from model loaded with tag '$tag'")
      new FakeFunction(name)
    }
  }

  def main(args: Array[String]): Unit = {
    println("1. load SavedModel")
    val modelBundle = new FakeModelBundle("serve")

    println("2. get endpoint function")
    val inferenceFunction = modelBundle.function("serve")

    println("3. call endpoint with input tensor")
    val outputTensor = inferenceFunction.call("inputTensor")

    println(s"outputTensor = $outputTensor")
  }
}
```

Dans cet exemple :

- `FakeModelBundle("serve")` représente le chargement du modèle
- `function("serve")` représente la récupération de l'endpoint
- `call("inputTensor")` représente l'exécution réelle de l'inférence
