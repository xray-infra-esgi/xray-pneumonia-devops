# Pourquoi `test` reste inchangé

## Idée

Le split `test` n'est pas modifié par le rééquilibrage.

## Pourquoi

Le rôle de `test` est différent de celui de `val`.

- `val` sert à piloter l'entraînement
- `test` sert à l'évaluation finale

Si on modifie `test`, on change le jeu de référence utilisé pour juger le modèle final.

## Décision retenue

Le pipeline :

- rééquilibre `val`
- mais laisse `test` intact

## Pourquoi c'est utile

Cela permet de garder :

- une évaluation finale stable
- une séparation claire entre validation et test

## Où c'est utilisé dans le projet

- [ImagePreprocessor.scala:159](../../../src/main/scala/preprocess/ImagePreprocessor.scala#L159)
- [ImagePreprocessor.scala:241](../../../src/main/scala/preprocess/ImagePreprocessor.scala#L241)

## Points de syntaxe à retenir

- aucun
