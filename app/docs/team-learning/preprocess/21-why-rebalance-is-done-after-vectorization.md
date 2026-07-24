# Pourquoi le rééquilibrage est fait après la vectorisation

> **Note de contexte** : ce document décrit l'approche historique, quand le
> rééquilibrage vivait dans le préprocessing Scala. Il a depuis été déplacé
> dans le notebook d'entraînement (`ml/train_tf.ipynb`) pour que le
> préprocessing Scala reste une pure transformation par enregistrement,
> compatible streaming. Le raisonnement ci-dessous (pourquoi *après* la
> vectorisation) reste valable — il s'applique simplement côté Python.

## Idée

Le projet aurait pu essayer de reconstruire les splits plus tôt, au niveau des `ImageRecord`.

Mais la décision retenue a été de faire le rééquilibrage après la vectorisation.

## Pourquoi ce choix

Après vectorisation, on travaille déjà avec un dataset propre contenant :

- `sourcePath`
- `split`
- `labelId`
- `featureWidth`
- `featureHeight`
- `features`

Cela rend le rééquilibrage plus simple à exprimer.

## Pourquoi ne pas faire le rééquilibrage plus tôt au niveau de `ImageRecord` ?

Avant vectorisation, on a encore une représentation plus "brute" :

- `label` peut encore valoir `"PNEUMONIA"`
- le `labelId` final n'existe pas encore
- le dataset final n'est pas encore dans la forme qui sera écrite dans le parquet

Le rééquilibrage, lui, travaille sur des classes finales.

Or ces classes finales sont plus simples à manipuler une fois qu'on a déjà :

- `labelId`
- `sourcePath`
- `split`

Autrement dit :

- avant vectorisation, on travaille encore avec la logique de lecture et de transformation des images
- après vectorisation, on travaille déjà avec la structure finale du dataset

## Justification concrète dans le projet

Le rééquilibrage doit notamment :

- compter combien il manque d'exemples par `labelId`
- sélectionner des lignes de `train`
- déplacer ces lignes vers `val`
- retirer ces mêmes lignes du `train` via `sourcePath`

Toutes ces opérations deviennent plus simples à écrire après vectorisation, parce que les colonnes utiles existent déjà dans le dataset final.

## Avantage

On sépare mieux :

- la logique de lecture / vectorisation
- la logique de reconstruction des splits

Le code devient plus lisible.

Le choix retenu permet donc :

- de ne pas mélanger la lecture des images et le rééquilibrage
- de raisonner directement sur les labels finaux du modèle
- de réutiliser `sourcePath` comme clé de retrait propre dans `train`

## Sortie

On part donc de :

```text
dataset vectorisé
```

pour construire ensuite :

```text
train final
val final
test final
```

## Pourquoi c'est utile

Cette fiche clarifie une décision d'architecture importante dans le preprocess.

## Où c'est utilisé dans le projet

- [ImagePreprocessor.scala:121](../../../src/main/scala/preprocess/ImagePreprocessor.scala#L121)
- [ImagePreprocessor.scala:142](../../../src/main/scala/preprocess/ImagePreprocessor.scala#L142)
- [ImagePreprocessor.scala:154](../../../src/main/scala/preprocess/ImagePreprocessor.scala#L154)

## Points de syntaxe à retenir

- ici, l'enjeu est surtout la structure du pipeline
