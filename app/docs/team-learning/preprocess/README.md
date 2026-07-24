# ⚠️ Note de contexte (2026-06-12)

Ces notes pédagogiques ont été écrites pendant la construction du préprocessing
batch (T2). Elles restent un **excellent matériau d'apprentissage**, mais le code
a évolué depuis — voici la carte de ce qui a changé pour les lire correctement.

## Ce qui a été DÉPLACÉ hors du Scala

**Le rééquilibrage du split de validation** (`rebalanceValidationSplit`) a été
**retiré du préprocessing Scala** et vit désormais dans le notebook Python
(`ml/train_tf.ipynb`, cellule numpy avant la construction des splits).
Raison : c'est une opération *globale* (comptages sur tout le dataset),
incompatible avec la nouvelle couche streaming — et ~20 lignes de numpy
remplacent ~90 lignes de Spark. Le préprocessing Scala est depuis une pure
transformation par enregistrement, donc 100 % *streamable*.

Notes concernées (décrivent du code qui n'existe plus en Scala — la logique
décrite reste vraie, mais elle s'exécute en Python) :

- `03-validation-rebalancing.md`
- `04-groupby-collect-tomap.md`
- `05-sampled-val-dfs-flatmap.md`
- `06-left-anti-join.md`
- `17-why-val-is-kept-and-completed-instead-of-rebuilt.md`
- `18-why-test-is-left-unchanged.md`
- `21-why-rebalance-is-done-after-vectorization.md` (le rebalance n'est plus
  « après la vectorisation » : il est après le *chargement des features* dans
  le notebook)
- `09-persist-and-spark-laziness.md` et
  `20-why-vectorizeddataset-is-the-right-place-to-persist.md` : le `persist`
  du dataset vectorisé a disparu avec le rebalance (le pipeline est redevenu
  mono-passe) — le concept enseigné reste valable.

## Ce qui a DÉMÉNAGÉ dans un module partagé

Les fonctions de transformation (`resolveTargetLabel`, `applyResizeMode`,
`extractFeatures`) vivent désormais dans **`vectorize/Vectorizer.scala`**, le
module partagé entre le batch et le streaming (garantie anti train/serving
skew). `ImagePreprocessor` y délègue. Les notes `02-resolve-target-label.md`
et `19-why-label-resolution-happens-before-labelid.md` restent justes sur le
fond — seul l'emplacement du code a changé.

## Ce qui est INCHANGÉ

Le reste (lecture distribuée `binaryFile`, dérivation split/label depuis le
chemin, case classes et `as[Dataset]`, UDF, pattern matching, `sourcePath`...)
décrit toujours le code actuel — et ces mêmes techniques sont maintenant
réutilisées par le consumer streaming (`consumer/StreamSources.scala`).
