# Utilité de `sourcePath` dans le projet

## Idée

`sourcePath` a été ajouté au dataset vectorisé pour conserver la trace du fichier image d'origine.

## Cas concret du projet

Dans le rééquilibrage du split `val`, certaines lignes de `train` sont sélectionnées pour être ajoutées à `val`.

Une fois cette sélection faite, il faut retirer ces mêmes lignes du `train`.

Pour cela, le projet extrait d'abord les chemins source :

```scala
val selectedSourcePathsDf = extraValDf.select("sourcePath").distinct()
```

Puis il retire du `train` les lignes correspondantes avec :

```scala
val remainingTrainDf = trainDf.join(
  selectedSourcePathsDf,
  Seq("sourcePath"),
  "left_anti"
)
```

Ici, `sourcePath` sert donc à identifier précisément quelles lignes doivent être retirées du `train`.

## Pourquoi c'est utile

Ce champ rend possible une opération importante du pipeline :

- déplacer des lignes de `train` vers `val`
- puis retirer proprement ces mêmes lignes du `train`

Autrement dit, `sourcePath` sert de lien entre :

- la ligne vectorisée
- et le fichier image d'origine

## Utilité concrète dans le pipeline

Le rôle de `sourcePath` est donc de permettre :

- une sélection précise des lignes déplacées
- une exclusion propre via `left_anti`
- une reconstruction cohérente des splits finaux

## Où c'est utilisé dans le projet

- [ImagePreprocessor.scala:38](../../../src/main/scala/preprocess/ImagePreprocessor.scala#L38)
- [ImagePreprocessor.scala:230](../../../src/main/scala/preprocess/ImagePreprocessor.scala#L230)

## Points de syntaxe à retenir

- `sourcePath` est ici une colonne métier utile au pipeline
- `select("sourcePath").distinct()` construit la liste des chemins à retirer
- `left_anti` utilise ensuite cette colonne pour filtrer `train`
