# Pourquoi `vectorizedDataset` est le bon endroit pour `persist`

## Idée

Tous les DataFrames ne sont pas de bons candidats pour `persist`.

Dans le projet, le meilleur candidat a été :

- `vectorizedDataset`

## Pourquoi

`vectorizedDataset` est :

- coûteux à produire
- réutilisé plusieurs fois

Il demande :

- lecture des images
- resize
- vectorisation

## Comparaison avec `trainDf`

`trainDf` est dérivé de `vectorizedDataset` avec un simple filtre.

Il est donc beaucoup moins coûteux à reconstruire.

Pendant le travail, on a observé que persister `trainDf` n'apportait pas de gain utile, et pouvait même coûter un peu plus.

## Décision retenue

On persiste :

- `vectorizedDataset`

et pas :

- `trainDf`

## Pourquoi c'est utile

Cette décision montre qu'un bon `persist` dépend de deux questions :

1. est-ce que le calcul est cher ?
2. est-ce qu'il est réutilisé plusieurs fois ?

## Où c'est utilisé dans le projet

- [ImagePreprocessor.scala:121](../../../src/main/scala/preprocess/ImagePreprocessor.scala#L121)

## Points de syntaxe à retenir

- `persist` n'est pas utile partout
- il faut viser le bon intermédiaire
