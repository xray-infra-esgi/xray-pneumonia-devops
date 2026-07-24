# ℹ️ Note de contexte (2026-06-12)

Ces notes décrivent l'inférence **batch** (T2) : elles restent **toutes
valables** — ce code n'a pas changé (à un détail de syntaxe près : le paramètre
du `mapPartitions` est désormais parenthésé, `{ (rows: Iterator[FeatureRow]) =>`,
forme valide en Scala 2 et 3).

Nouveauté depuis la couche streaming : le pattern central enseigné ici
(`mapPartitions` + un `TensorflowPredictor` par partition + iterator custom qui
ferme le prédicteur en fin de partition) est **réutilisé tel quel** par le
consumer Structured Streaming en mode infer, dans
`consumer/InferBatchWriter.scala` — le `foreachBatch` livrant chaque micro-batch
comme un Dataset ordinaire. Comprendre ces notes, c'est donc comprendre les deux
pipelines.

Différences côté streaming : entrée vectorisée à la volée (pas de feature store),
sortie enrichie (`depositedAt`/`processedAt` pour la latence, label lisible,
quarantaine des fichiers illisibles), pas d'accuracy (pas de vérité terrain sur
une requête).
