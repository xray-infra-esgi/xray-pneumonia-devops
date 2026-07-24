# Pourquoi `val` est conservé puis complété

## Idée

Le projet n'a pas supprimé complètement le split `val` source.

À la place, il a choisi de :

- conserver `val`
- puis le compléter depuis `train`

## Pourquoi ce choix

Le split `val` source contenait déjà :

- des images distinctes de `train`

Il aurait donc été dommage de le jeter complètement.

Mais il n'était pas suffisant pour le passage à 3 classes, car il ne contenait pas correctement toutes les classes.

## Décision retenue

Le pipeline fait donc :

- `test` reste inchangé
- `val` est conservé
- `train` sert à compléter `val`

## Sortie visée

Un `val` final :

- équilibré
- utilisable pour l'entraînement
- sans perdre les images déjà présentes dans `val`

## Pourquoi c'est utile

Cette décision garde un bon compromis entre :

- réutilisation du dataset existant
- correction du problème de validation
- stabilité de l'évaluation finale

## Où c'est utilisé dans le projet

- [ImagePreprocessor.scala:154](../../../src/main/scala/preprocess/ImagePreprocessor.scala#L154)

## Points de syntaxe à retenir

- ici, l'important est surtout une décision de pipeline, pas une syntaxe Scala particulière
