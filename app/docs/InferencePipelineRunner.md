
``InferencePipelineRunner`` est un orchestrateur stateless qui gère le pipeline d'inférence.

- aucun state => object singleton
- expose une méthode run pour appeler le pipeline d'inférence


Son rôle est de :

1. Lire le dataset 
2. Lancer la transformation Spark pour faire l'inférence à partir des features déjà prétraitées
3. Appeler le prédicteur TensorFlow pour faire les prédictions
4. Ecrire le résultat 


Le runner commence par lire la config. 

Note : 
```scala
val featuresDataset = spark.read
            .parquet(config.featuresPath)
            .as[FeatureRow]
```

Le as[FeatureRow] permet de convertir le DataFrame Spark en Dataset typé, où chaque ligne est représentée par une instance de la classe case class FeatureRow.
Le Dataset[FeatureRow] offre des avantages de type-safety et permet d'utiliser des fonctions de haut niveau pour manipuler les données, tout en bénéficiant des optimisations de Spark. (mais fondamentalement, c'est un DataFrame typé et rien de plus compliqué que ça : ça reste un DataFrame Spark, mais avec une structure de données définie par la case class FeatureRow)



<span style="color:yellow;font-weight:bold">Note implémentation :</span>
Au sein de chaque partition, on crée un itérateur d'éléments de type PredictionRow. Chaque élément PredictionRow contient le résultat de la prédiction pour une ligne d'entrée.

En entrée de `mapPartitions`, Spark nous fournit `rows` qui est un `Iterator[FeatureRow]` pour la partition courante. Le type Iterator[FeatureRow] signifie que nous avons un flux de données de type FeatureRow à traiter.

Notre rôle dans `mapPartitions` est de transformer cet itérateur d'entrée en un nouvel itérateur de type `Iterator[PredictionRow]`, où chaque `PredictionRow` contient les résultats de la prédiction pour une ligne d'entrée.

- entrée : `Iterator[FeatureRow]` (flux de données d'entrée)
- sortie : `Iterator[PredictionRow]` (flux de données de sortie après transformation)

La syntaxe Scala compacte : 

```scala
new Iterator[PredictionRow]{
    override def hasNext: Boolean = rows.hasNext
    
    override def next(): PredictionRow = {
        val featureRow = rows.next()
        // appeler le prédicteur TensorFlow pour obtenir les scores des classes
        // choisir la classe prédite à partir du score maximal
        // construire et retourner un PredictionRow avec les résultats

    }
}
```     

Cette syntaxe compacte signifie simplement : 

"on crée un nouvel objet anonyme qui implémente l'interface ``Iterator[PredictionRow]``".

Autrement dit, on définit nous mêmes : 
- ``hasNext`` : qui indique comment vérifier s'il reste encore des éléments dans la partition à traiter (c'est à dire dans le Iterator[FeatureRow] fourni par Spark)
- ``next`` : qui définit comment produire le prochain élément de type PredictionRow à partir du prochain élément de type FeatureRow (en appelant le prédicteur TensorFlow pour faire la transformation)

<span style="color:yellow;font-weight:bold">IMPORTANT</span>
On utilise un itérateur custom parce que le prédicteur tensorflow doit rester ouvert pendant toute la consommation réelle des lignes de la partition.

Si on écrivait seulement : 

```scala
row.map(row => ...)
```
 avec un try/finally et une libération de ressource avec .close() sur le predictor dans le finally, alos on fermerait le prédicteur trop tôt car l'itération Spark est paresseuse(lazy).

Notre itérateu personnalisé permet de : 
- créer un ``TensorflowPredictor`` une seule fois par partition
- l'utiliser pour chaque ligne de la partition 
- le fermer exactement quand la partition n'a plus d'éléments à produire 


Il est à comprendre que le lien entre ``Iterator[PredictionRow]`` et les rows passées en entrée dans la partition se fait dans la ligne : 
```scala 
val row = rows.next()
```

## Logique de prédiction multiclasses

Le projet n'est plus en classification binaire.

Le modèle TensorFlow exporté renvoie maintenant une sortie de forme `(None, 3)` :
- un score pour `NORMAL`
- un score pour `PNEUMONIA_BACTERIA`
- un score pour `PNEUMONIA_VIRUS`

Le rôle de `TensorflowPredictor` est de lire ces 3 scores et de les retourner sous forme d'un `Array[Float]`.

Ensuite, `InferencePipelineRunner` transforme ces scores en prédiction finale.

Extrait de logique :

```scala
val scores = predictor.predict(
    row.features,
    row.featureHeight,
    row.featureWidth
)

val (predictedScore, predictedLabelId) =
    scores.zipWithIndex.maxBy { case (score, _) => score }
```

### Explication

- `scores` contient les 3 scores renvoyés par le modèle
- `zipWithIndex` associe chaque score à son indice
- `maxBy` sélectionne le couple dont le score est le plus élevé

Exemple mental :

```scala
scores = Array(0.05f, 0.20f, 0.75f)
```

après `zipWithIndex` :

```scala
Array((0.05f, 0), (0.20f, 1), (0.75f, 2))
```

puis `maxBy` retourne :

```scala
(0.75f, 2)
```

Donc :
- `predictedScore = 0.75f`
- `predictedLabelId = 2`

Autrement dit :
- la classe prédite est celle dont le score est le plus élevé
- le score stocké dans `PredictionRow` correspond au score de la classe prédite

<span style="color:yellow;font-weight:bold">Ancien bug</span> : on fermait le modèle à la fin de la création de l'iterator, pas à la fin de sa consommation.

Cause : `rows.map(...)` est paresseux.

Solution : utiliser un iterator custom qui garde le prédicteur ouvert pendant tout le parcours de la partition et le ferme seulement à la fin.

---

## 📌 Mise à jour (couche streaming, 2026-06)

Tout ce qui est décrit ici reste valable pour l'inférence **batch**. Depuis
l'ajout de la couche Structured Streaming, le **même pattern** (`mapPartitions`
+ un `TensorflowPredictor` par partition + iterator custom avec `closeIfNeeded`)
est réutilisé **tel quel** par le consumer streaming, dans
`consumer/InferBatchWriter.scala` : le `foreachBatch` livre chaque micro-batch
comme un Dataset ordinaire, sur lequel ce pattern se rebranche sans modification.

Différences côté streaming :
- l'entrée n'est plus `FeatureRow` (lu du feature store) mais `InferOutcome`
  (vectorisé à la volée depuis la landing zone) ;
- la sortie `StreamPrediction` ajoute `depositedAt`/`processedAt` (latence) et
  un label lisible ;
- pas de calcul d'accuracy : une requête d'inférence n'a pas de vérité terrain.
