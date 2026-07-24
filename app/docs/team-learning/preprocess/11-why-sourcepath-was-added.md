# Pourquoi `sourcePath` a été ajouté

## Idée

Le champ `sourcePath` a été ajouté à `VectorizationResult` pour garder la trace du fichier image d'origine.

## Pourquoi ce champ était nécessaire

Pendant le rééquilibrage de `val`, on prélève certaines lignes de `train` pour les déplacer vers `val`.

Ensuite, il faut retirer exactement ces lignes du `train`.

Pour faire cela proprement, il faut une clé stable permettant d'identifier chaque ligne.

## Cas concret du projet

Le code du projet a ensuite pu faire :

```scala
val selectedSourcePathsDf = extraValDf.select("sourcePath").distinct()

val remainingTrainDf = trainDf.join(
  selectedSourcePathsDf,
  Seq("sourcePath"),
  "left_anti"
)
```

## Entrée

Exemple simplifié de ligne vectorisée :

```scala
VectorizationResult(
  sourcePath = "data/chest_xray/train/PNEUMONIA/person1_bacteria_1.jpeg",
  split = "train",
  labelId = 1,
  featureWidth = 128,
  featureHeight = 128,
  features = Seq(...)
)
```

## Transformation

On extrait d'abord les `sourcePath` des lignes sélectionnées pour compléter `val`.

Puis on retire du `train` toutes les lignes dont le `sourcePath` apparaît dans cette sélection.

## Sortie

On obtient :

- un `extraValDf` contenant les lignes déplacées
- un `remainingTrainDf` ne contenant plus ces mêmes lignes

## Pourquoi c'est utile

Sans `sourcePath`, il aurait été plus difficile de :

- retirer exactement les bonnes lignes
- éviter les doublons entre `train` et `val`

## Où c'est utilisé dans le projet

- [ImagePreprocessor.scala:38](../../../src/main/scala/preprocess/ImagePreprocessor.scala#L38)
- [ImagePreprocessor.scala:230](../../../src/main/scala/preprocess/ImagePreprocessor.scala#L230)

## Points de syntaxe à retenir

- un champ ajouté à une case class peut rendre possible une opération de pipeline plus tard
- ici, `sourcePath` sert de clé d'identification
- `distinct()` évite les doublons dans la liste des chemins sélectionnés
