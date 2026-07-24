``TensorFlowPredictor.scala`` est comme un adaptateur entre :
- les ``features`` Scala
- le modèle TensorFlow exporté en ``SavedModel``


Le fichier a plusieurs responsabilités.

### 1. Charger le modèle exporté

Le prédicteur doit garder en mémoire :
- le saved model : ``SavedModel``
- la fonction d'inférence qu'on va appeler (endpoint "serve")

Donc, la classe a un état interne. C'est pour ça qu'on a choisi une ``class`` et pas un ``object`` en Scala.

Ca donne :
```scala
final class TensorflowPredictor(modelPath: String) extends AutoCloseable
```

et à l'intérieur on :
- charge le bundle TensorFlow
- récupère la fonction exportée

### 2. Transformer ``features`` en tenseur TensorFlow

Le fichier parquet contient des ``features`` Scala.
- ``features: Seq[Float]``
- ``featureHeight``
- ``featureWidth``

Or le modèle attend :
- un batch
- de forme ``[batchSize, featureHeight, featureWidth, channels]``

Dans notre cas :
- batch = 1
- height = 128
- width = 128
- channels = 1


### 3. Appeler la fonction d'inférence (le endpoint "serve")

Une fois que le tenseur d'entrée est prêt, il faut appeler la fonction exportée du modèle.

C'est là qu'interviennent :
- le endpoint ``serve``
- le nom d'entrée ``input_layer``

### 4. Lire la sortie

Le modèle renvoie maintenant :
- une sortie de forme ``(None, 3)``

Donc, pour une prédiction avec batch = 1, on s'attend à :
- un tenseur de sortie de forme ``(batch, 3)``

Autrement dit, pour notre exemple :
- la ligne ``0`` correspond à notre unique image du batch
- les 3 colonnes correspondent aux 3 classes du projet

Dans notre cas, cela représente :
- un score pour ``NORMAL``
- un score pour ``PNEUMONIA_BACTERIA``
- un score pour ``PNEUMONIA_VIRUS``

Il faudra donc lire :
- ``output(0, 0)``
- ``output(0, 1)``
- ``output(0, 2)``

Le predictor retourne donc un ``Array[Float]`` contenant les 3 scores.

<span style="color:yellow;font-weight:bold">Important</span> :
Le rôle de ``TensorflowPredictor`` est seulement de lire les scores du modèle.

Le choix final de la classe prédite ne se fait pas ici.
Il se fait dans ``InferencePipelineRunner.scala``, qui récupère les scores et choisit la classe dont le score est le plus élevé.


# Les imports à connaître

Dans ``TensorflowPredictor.scala``, on doit importer des classes de TensorFlow Java :

- ``org.tensorflow.SavedModelBundle`` : pour charger le modèle exporté
- ``org.tensorflow.ndarray.Shape`` : pour définir la forme des tenseurs d'entrée
- ``org.tensorflow.ndarray.NdArrays`` : pour créer des tableaux de données à partir de nos features Scala
- ``org.tensorflow.types.TFloat32`` : pour travailler avec des tenseurs de type float32, qui est le type attendu par notre modèle

# SavedModelBundle.load et les tags

``SavedModelBundle.load(modelPath, "serve")``

Le 2ème paramètre de ``.load`` s'appelle ``tags`` et correspond au tag set du ``SavedModel``.

Un ``SavedModel`` peut avoir plusieurs graphes ou variantes destinées à des usages différents.
Les tags indiquent : "je veux charger la version du modèle qui correspond à tel usage".

Pour l'inférence, on utilise très souvent ``serve``.

<span style="color:yellow">Mais, il ne faut pas confondre le tag avec le endpoint.</span>
Ca sert à charger le bon ensemble de graphes du modèle, pas à appeler la bonne fonction d'inférence.

**Pourquoi il y a un système de tags en TensorFlow ?**

Parce qu'un même modèle ``SavedModel`` peut contenir plusieurs usages :
- entraînement
- évaluation
- serving (inférence)
- autres variantes

Les tags permettent de choisir le bon contexte de chargement.

La notion de graphes exposés par le modèle :

Un graphe permet de représenter le calcul du modèle comme :
- des entrées
- des opérations
- des sorties

Cette chaîne de calcul peut être vue comme un graphe de noeuds (opérations / tensors) et de liens (comment les données circulent entre les noeuds).

<span style="color:yellow;font-weight:bold">BIEN A SAVOIR : Une fonction exposée par le modèle est une façon officielle d'appeler un graphe spécifique avec des entrées et sorties dans un format précis</span>

## Notes sur les ressources et la nécessité de fermer les ressources

En Scala, on ne gère pas la mémoire directement.

- la JVM alloue des objets
- le garbage collector les nettoie plus tard

Cependant, lorsqu'on utilise TensorFlow JVM, TensorFlow JVM manipule des ``resources natives``.

#### Qu'est-ce qu'une resource native ?

Ce sont des ressources qui vivent en **dehors de la mémoire ordinaire de la JVM**, comme par exemple :
- mémoire allouée par du code natif (C/C++)
- buffers internes TensorFlow
- handles vers des objets du runtime TensorFlow

Donc, même si on écrit du Scala, certaines choses utilisées par TensorFlow ne sont pas de simples objets Scala "normaux" gérés par le garbage collector de la JVM.

---

## 📌 Mise à jour (couche streaming, 2026-06)

`TensorflowPredictor` est inchangé, mais il a désormais **deux clients** :

1. l'inférence **batch** historique (`inference/InferencePipelineRunner`) ;
2. le **consumer streaming** en mode infer (`consumer/InferBatchWriter`), qui
   l'instancie dans le `foreachBatch` de Structured Streaming — toujours un
   prédicteur par partition, fermé en fin de partition (mêmes règles de
   ressources natives que décrites ci-dessus).

Le `SavedModel` qu'il charge est maintenant **exporté automatiquement** par le
notebook d'entraînement (`model.export(...)` dans la cellule d'entraînement,
endpoint `serve`) — plus d'export manuel.
