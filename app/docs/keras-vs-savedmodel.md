# `.keras` vs `SavedModel`

## Idée générale

Dans ce projet, on manipule deux formats de sauvegarde du modèle TensorFlow :

- `.keras`
- `SavedModel`

Ils ne servent pas exactement au même usage.

## Le format `.keras`

Le fichier `.keras` est le format pratique de Keras pour sauvegarder un modèle et le recharger facilement en Python.

Exemple d'usage :

```python
model = tf.keras.models.load_model("ml/artifacts/models/baseline_best.keras")
```

Ce format est très adapté pour :

- reprendre le travail dans un notebook Python
- relancer une évaluation
- continuer un entraînement
- rester dans l'écosystème Keras

On peut le voir comme le format de travail naturel du modèle dans Python.

## Le format `SavedModel`

`SavedModel` est le format standard d'export de TensorFlow pour l'inférence.

Contrairement à `.keras`, ce n'est pas un seul fichier. C'est un dossier contenant plusieurs éléments, en général :

- `saved_model.pb`
- `variables/`
- `assets/`

Exemple :

```text
ml/artifacts/models/saved_model/
  saved_model.pb
  variables/
  assets/
```

Ce format est plus adapté à :

- TensorFlow Serving
- l'inférence hors notebook
- l'intégration avec la JVM
- l'utilisation dans Spark Scala

On peut le voir comme une version exportée du modèle, faite pour être appelée par un moteur d'inférence TensorFlow.

## A quoi servent les fichiers d'un `SavedModel` ?

### `saved_model.pb`

C'est le coeur du modèle exporté.

Il contient notamment :

- la description du graphe exporté
- les signatures d'appel
- les informations sur les entrées et sorties exposées

Dans notre cas, c'est ce qui permet de savoir qu'on a par exemple :

- une entrée de forme `(None, 128, 128, 1)`
- une sortie de forme `(None, 3)`

### `variables/`

Ce dossier contient les poids du modèle.

Autrement dit :

- les valeurs apprises pendant l'entraînement
- les paramètres des couches

Sans ce dossier, le modèle exporté n'aurait pas ses vraies valeurs apprises.

### `assets/`

Ce dossier peut contenir des ressources additionnelles nécessaires au modèle.

Selon le modèle, il peut être vide ou peu important. Mais TensorFlow le crée dans la structure standard du `SavedModel`.

## Différence conceptuelle

### `.keras`

Le `.keras` sert surtout à dire :

> voici mon modèle Keras, je veux le retrouver facilement en Python

### `SavedModel`

Le `SavedModel` sert surtout à dire :

> voici mon modèle exporté pour l'inférence TensorFlow

## Pourquoi on a besoin des deux dans ce projet

Dans notre pipeline, ils ont deux rôles différents :

1. le notebook Python entraîne le modèle et sauvegarde un `.keras`
2. on exporte ensuite ce modèle en `SavedModel`
3. Spark Scala charge le `SavedModel` pour faire l'inférence distribuée

Donc :

- `.keras` est pratique pour l'entraînement et le travail dans Keras
- `SavedModel` est pratique pour l'inférence côté Scala/JVM

## Pourquoi Spark Scala ne charge pas directement le `.keras` ?

Parce que notre code Scala n'utilise pas Keras Python.

Il passe par la JVM et par l'API TensorFlow Java. Cette API travaille naturellement avec un `SavedModel`.

Donc le `SavedModel` sert de pont entre :

- le monde Python/Keras
- le monde Scala/Spark sur la JVM

## Résumé

- `.keras` : format pratique pour sauvegarder et recharger un modèle Keras en Python
- `SavedModel` : format d'export TensorFlow pour l'inférence
- `SavedModel` est un dossier, pas un fichier unique
- dans ce projet, on entraîne en Python puis on exporte en `SavedModel` pour l'inférence Spark Scala


# Mes notes en plus

Dans un ``SavedModel``, ``serve`` est le nom d'une **fonction exportée pour l'inférence**.
C'est la signature par défaut que TensorFlow utilise pour exposer le modèle lors de l'inférence.

Il y a également ce qu'on appelle un ``endpoint``.
TensorFlow exporte le modèle comme un artefact avec une ou plusieurs **portes d'entrée appelables (les endpoints)**.

Donc, le `SavedModel` n'est pas juste des poids. Il expose également des **fonctions qu'on peut appeler**.
Chacune de ces fonctions a :
- un nom (comme "serve")
- des entrées
- des sorties

Souvent le endpoint est ``serve`` ou ``serving_default``.

``serve`` est souvent le nom de l'endpoint exporté par Keras 3 avec `model.export(...)`.

``serving_default`` est un nom très courant dans l'écosystème `SavedModel`, notamment visible avec `saved_model_cli`.

### Pourquoi on en a besoin dans ``TensorflowPredictor.scala``

Parce que le code Scala devra :
- charger le `SavedModel`
- récupérer la fonction correspondant à l'endpoint (comme "serve")
- lui envoyer un tenseur d'entrée
- lire le tenseur de sortie

Donc ``TensorflowPredictor.scala`` doit connaître :
- quel endpoint appeler (ex: "serve")
- quel nom d'entrée utiliser
- comment lire la sortie

Comment on trouve/récupère ces informations ?

```python
from pathlib import Path
import shutil
import tensorflow as tf

PROJECT_ROOT = Path("/home/dan/projects/ESGI/4A/T2/scala/X-Ray-Pneumonia-Detection")
keras_model_path = PROJECT_ROOT / "ml" / "artifacts" / "models" / "baseline-20260328-011829_best.keras"
saved_model_dir = PROJECT_ROOT / "ml" / "artifacts" / "models" / "saved_model"

model = tf.keras.models.load_model(keras_model_path)

print("Input shape:", model.input_shape)
print("Output shape:", model.output_shape)

if saved_model_dir.exists():
    shutil.rmtree(saved_model_dir)

model.export(saved_model_dir)
print("SavedModel exported to:", saved_model_dir)
```

Ca donne un output comme :

```text
Input shape: (None, 128, 128, 1)
Output shape: (None, 3)
* Endpoint 'serve'
  args_0 (POSITIONAL_ONLY): TensorSpec(shape=(None, 128, 128, 1), dtype=tf.float32, name='input_layer')
Output Type:
  TensorSpec(shape=(None, 3), dtype=tf.float32, name=None)
Captures:
  138261551498448: TensorSpec(shape=(), dtype=tf.resource, name=None)
  138261551500176: TensorSpec(shape=(), dtype=tf.resource, name=None)
  138261551499024: TensorSpec(shape=(), dtype=tf.resource, name=None)
  138261551499600: TensorSpec(shape=(), dtype=tf.resource, name=None)
  138261551502096: TensorSpec(shape=(), dtype=tf.resource, name=None)
  138261551502864: TensorSpec(shape=(), dtype=tf.resource, name=None)
  138261551503056: TensorSpec(shape=(), dtype=tf.resource, name=None)
  138261551503248: TensorSpec(shape=(), dtype=tf.resource, name=None)
  138261551504400: TensorSpec(shape=(), dtype=tf.resource, name=None)
  138261551501520: TensorSpec(shape=(), dtype=tf.resource, name=None)
SavedModel exported to: */ml/artifacts/models/saved_model
```

Sur la base de ça, on sait que ``TensorflowPredictor.scala`` devra :
1. charger le ``SavedModel``
2. récupérer la fonction d'inférence exportée (endpoint "serve")
3. construire un tenseur d'entrée de forme `(None, 128, 128, 1)` et de type `float32`
4. appeler la fonction "serve" avec ce tenseur d'entrée
5. récupérer une sortie de forme `[1, 3]` et de type `float32`
6. lire les 3 scores de sortie
7. laisser le pipeline d'inférence déterminer ensuite la classe prédite à partir du score maximal

---

## 📌 Mise à jour (2026-06) : l'export est désormais automatique

Le script d'export manuel ci-dessus (avec son chemin T2) est conservé comme
référence pédagogique, mais il n'est **plus nécessaire** : la cellule
d'entraînement de `ml/train_tf.ipynb` exporte maintenant le `SavedModel`
automatiquement après la sauvegarde du `.keras` :

```python
model.save(final_model_path)
# Export the SavedModel consumed by the Scala TensorflowPredictor (serve endpoint).
model.export(str(MODEL_DIR / "saved_model"))
```

Le `SavedModel` est consommé par **deux** pipelines : l'inférence batch
(`./xray inference`) et le consumer streaming en mode infer
(`./xray consume --mode infer`, qui le vérifie en fail-fast au démarrage :
`Model not found: ...` si l'entraînement n'a pas encore eu lieu).
