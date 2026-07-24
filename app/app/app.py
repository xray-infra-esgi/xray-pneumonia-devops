import math
import os
import shutil
import uuid
from pathlib import Path

import altair as alt
import pandas as pd
import pyarrow.dataset as pds
import streamlit as st

PROJECT_ROOT = Path(__file__).resolve().parent.parent
PREDICTIONS_PATH = PROJECT_ROOT / "output" / "predictions-stream.parquet"
REJECTED_PATH = PROJECT_ROOT / "output" / "rejected-stream.parquet"
FEATURES_PATH = PROJECT_ROOT / "output" / "features-stream.parquet"
METRICS_INFER = PROJECT_ROOT / "output" / "metrics" / "consume-infer.csv"
METRICS_TRAIN = PROJECT_ROOT / "output" / "metrics" / "consume-train.csv"
INCOMING_INFER = PROJECT_ROOT / "data" / "incoming-infer"
INCOMING_TRAIN = PROJECT_ROOT / "data" / "incoming"
LOCAL_TZ = "Europe/Paris"

st.set_page_config(page_title="X-Ray — Dashboard du flux", layout="wide")

st.title("Détection de pneumonie — dashboard du flux")

refresh_seconds = st.sidebar.slider("Rafraîchissement (secondes)", 1, 10, 2)


def count_rows(path: Path):
    try:
        return pds.dataset(str(path), format="parquet").count_rows()
    except Exception:
        return None


def landing_file_names(zone: Path) -> set:
    try:
        return {
            p.name
            for p in zone.rglob("*")
            if p.is_file()
            and p.suffix.lower() in (".jpg", ".jpeg")
            and "_staging" not in p.parts
        }
    except Exception:
        return set()


def to_local(series: pd.Series) -> pd.Series:
    try:
        return series.dt.tz_localize("UTC").dt.tz_convert(LOCAL_TZ).dt.tz_localize(None)
    except Exception:
        return series


def now_local() -> pd.Timestamp:
    return pd.Timestamp.now(tz=LOCAL_TZ).tz_localize(None)


def load_parquet(path: Path):
    try:
        return pd.read_parquet(path)
    except Exception:
        return None


def load_predictions(path: Path):
    df = load_parquet(path)
    if df is None or df.empty:
        return df
    for column in ("processedAt", "depositedAt"):
        if column in df.columns:
            df[column] = to_local(df[column])
    if {"processedAt", "depositedAt"}.issubset(df.columns):
        df["latence (s)"] = (df["processedAt"] - df["depositedAt"]).dt.total_seconds().round(1)
    if "processedAt" in df.columns:
        df = df.sort_values("processedAt", ascending=False)
    return df


def load_metrics(path: Path):
    try:
        df = pd.read_csv(path, parse_dates=["timestamp"])
    except Exception:
        return None
    if df.empty:
        return None
    df["timestamp"] = to_local(df["timestamp"])
    return df.tail(200)


def queue_history(*frames):
    events = []
    for df in frames:
        if df is not None and not df.empty and {"depositedAt", "processedAt"}.issubset(df.columns):
            events.append(pd.DataFrame({"t": df["depositedAt"], "delta": 1}))
            events.append(pd.DataFrame({"t": df["processedAt"], "delta": -1}))
    if not events:
        return None
    timeline = pd.concat(events).dropna().sort_values("t")
    if timeline.empty:
        return None
    levels = timeline.set_index("t")["delta"].cumsum().rename("en attente")
    levels = levels.groupby(level=0).last()
    return levels.resample("1s").last().ffill()


def atomic_deposit(target_dir: Path, file_name: str, payload: bytes) -> None:
    target_dir.mkdir(parents=True, exist_ok=True)
    tmp_path = target_dir / f".{file_name}.tmp"
    final_path = target_dir / file_name
    with open(tmp_path, "wb") as handle:
        handle.write(payload)
    os.replace(tmp_path, final_path)


def inject_file(src_file: Path, target_dir: Path, file_name: str) -> None:
    target_dir.mkdir(parents=True, exist_ok=True)
    dest = target_dir / file_name
    try:
        os.link(src_file, dest)
    except OSError:
        dest.write_bytes(src_file.read_bytes())


def soft_reset() -> None:
    for path in (PREDICTIONS_PATH, REJECTED_PATH, METRICS_INFER):
        if path.is_dir():
            shutil.rmtree(path, ignore_errors=True)
        elif path.exists():
            try:
                path.unlink()
            except OSError:
                pass


st.sidebar.divider()
st.sidebar.subheader("Réinitialiser la démo")
if st.sidebar.button("Vider l'affichage (le consumer continue)"):
    soft_reset()
    for key in ("prev_pred", "prev_rej", "prev_backlog", "pred_page"):
        st.session_state.pop(key, None)
    st.sidebar.success("Affichage vidé — le consumer tourne toujours.")
tab_live, tab_train, tab_inject = st.tabs(
    [
        ":material/monitoring: Inférence en direct",
        ":material/model_training: Flux d'entraînement",
        ":material/upload: Injection d'images",
    ]
)


with tab_live:

    @st.fragment(run_every=refresh_seconds)
    def live_dashboard():
        n_pred = count_rows(PREDICTIONS_PATH)
        n_rejected = count_rows(REJECTED_PATH)
        df = load_predictions(PREDICTIONS_PATH)

        rejected_df = load_predictions(REJECTED_PATH)
        processed_names = set()
        if df is not None and not df.empty and "fileName" in df.columns:
            processed_names |= set(df["fileName"])
        if rejected_df is not None and not rejected_df.empty and "fileName" in rejected_df.columns:
            processed_names |= set(rejected_df["fileName"])
        n_backlog = len(landing_file_names(INCOMING_INFER) - processed_names)

        prev_pred = st.session_state.get("prev_pred")
        prev_rej = st.session_state.get("prev_rej")
        prev_backlog = st.session_state.get("prev_backlog")
        st.session_state["prev_pred"] = n_pred
        st.session_state["prev_rej"] = n_rejected
        st.session_state["prev_backlog"] = n_backlog

        pneumonia_rate = None
        median_latency = None
        if df is not None and not df.empty:
            pneumonia_rate = float((df["predictedLabelId"] != 0).mean())
            if "latence (s)" in df.columns:
                median_latency = float(df["latence (s)"].median())

        c1, c2, c3, c4, c5 = st.columns(5)
        c1.metric(
            "Prédictions (traitées)",
            n_pred if n_pred is not None else "—",
            delta=(n_pred - prev_pred)
            if (n_pred is not None and prev_pred is not None and n_pred != prev_pred)
            else None,
        )
        c2.metric(
            "En file d'attente",
            n_backlog,
            delta=(n_backlog - prev_backlog)
            if (prev_backlog is not None and n_backlog != prev_backlog)
            else None,
            delta_color="inverse",
        )
        c3.metric("Latence médiane", f"{median_latency:.1f} s" if median_latency is not None else "—")
        c4.metric("Taux pneumonie", f"{pneumonia_rate:.0%}" if pneumonia_rate is not None else "—")
        c5.metric(
            "Quarantaine",
            n_rejected if n_rejected is not None else 0,
            delta=(n_rejected - prev_rej)
            if (n_rejected is not None and prev_rej is not None and n_rejected != prev_rej)
            else None,
            delta_color="inverse",
        )

        st.caption(
            f"Dernière lecture : {now_local().strftime('%H:%M:%S')} · "
            f"rafraîchissement toutes les {refresh_seconds} s"
        )

        m1, m2 = st.columns(2)
        with m1:
            st.subheader("File d'attente dans le temps")
            queue_series = queue_history(df, rejected_df)
            if queue_series is not None:
                st.area_chart(
                    queue_series,
                    height=200,
                    color="#E8973A",
                    x_label="heure",
                    y_label="img",
                )
        with m2:
            st.subheader("Débit du consumer (images / seconde)")
            metrics_infer = load_metrics(METRICS_INFER)
            if metrics_infer is not None:
                base = alt.Chart(metrics_infer).encode(
                    x=alt.X("timestamp:T", title="heure"),
                    y=alt.Y("processedRowsPerSecond:Q", title="img/s"),
                )
                st.altair_chart(
                    alt.layer(
                        base.mark_line(color="#1D9E75", strokeDash=[4, 4], strokeWidth=1),
                        base.mark_point(filled=True, size=75, opacity=1, color="#1D9E75"),
                        alt.Chart(metrics_infer)
                        .mark_rule(strokeWidth=12, opacity=0)
                        .encode(
                            x=alt.X("timestamp:T", title="heure"),
                            tooltip=[
                                alt.Tooltip("timestamp:T", title="heure", format="%H:%M:%S.%L"),
                                alt.Tooltip("processedRowsPerSecond:Q", title="img/s", format=".1f"),
                                alt.Tooltip("numInputRows:Q", title="images"),
                                alt.Tooltip("batchDurationMs:Q", title="durée batch (ms)"),
                            ],
                        ),
                    )
                    .properties(height=200)
                    .interactive(),
                    width="stretch",
                )
                last = metrics_infer.iloc[-1]
                st.caption(
                    f"Dernier batch : {int(last['numInputRows'])} images en "
                    f"{int(last['batchDurationMs'])} ms ({last['processedRowsPerSecond']:.1f} img/s)"
                )

        if df is None or df.empty:
            st.info("En attente de prédictions.")
        else:
            if {"depositedAt", "processedAt", "latence (s)"}.issubset(df.columns):
                proc = df["processedAt"].sort_values()
                starts = proc[proc.diff().dt.total_seconds() > 60]
                run_start = starts.iloc[-1] if len(starts) else proc.iloc[0]
                run = df[df["processedAt"] >= run_start]
                dep_span = max((run["depositedAt"].max() - run["depositedAt"].min()).total_seconds(), 1.0)
                total_span = max((run["processedAt"].max() - run["depositedAt"].min()).total_seconds(), 1.0)
                st.caption(
                    f"Dernier run : {len(run)} images · déposées en {dep_span:.0f} s "
                    f"({len(run) / dep_span:.0f} img/s) · traitées en {total_span:.0f} s au total "
                    f"({len(run) / total_span:.0f} img/s) · latence médiane {run['latence (s)'].median():.1f} s"
                )

            d1, d2 = st.columns(2)
            with d1:
                st.subheader("Images déposées dans le temps")
                if "depositedAt" in df.columns:
                    deposits = (
                        df.set_index("depositedAt").sort_index().assign(n=1)["n"]
                        .resample("1s").sum().tail(600)
                        .rename("images").reset_index()
                    )
                    st.altair_chart(
                        alt.Chart(deposits)
                        .mark_bar(color="#E8973A")
                        .encode(
                            x=alt.X("depositedAt:T", title="heure"),
                            y=alt.Y("images:Q", title="img / s"),
                            tooltip=[
                                alt.Tooltip("depositedAt:T", title="heure", format="%H:%M:%S.%L"),
                                alt.Tooltip("images:Q", title="images"),
                            ],
                        )
                        .properties(height=240)
                        .interactive(),
                        width="stretch",
                    )
            with d2:
                st.subheader("Images traitées dans le temps")
                if "processedAt" in df.columns:
                    timeline = (
                        df.set_index("processedAt").sort_index().assign(n=1)["n"]
                        .resample("1s").sum().tail(600)
                        .rename("images").reset_index()
                    )
                    st.altair_chart(
                        alt.Chart(timeline)
                        .mark_bar(color="#1D9E75")
                        .encode(
                            x=alt.X("processedAt:T", title="heure"),
                            y=alt.Y("images:Q", title="img / s"),
                            tooltip=[
                                alt.Tooltip("processedAt:T", title="heure", format="%H:%M:%S.%L"),
                                alt.Tooltip("images:Q", title="images"),
                            ],
                        )
                        .properties(height=240)
                        .interactive(),
                        width="stretch",
                    )

            st.subheader("Répartition des classes")
            st.bar_chart(
                df["predictedLabel"].value_counts(),
                height=200,
                horizontal=True,
                x_label="img",
                y_label="classe",
            )

            columns = [
                c
                for c in ["processedAt", "fileName", "predictedLabel", "score", "latence (s)"]
                if c in df.columns
            ]
            total = len(df)
            st.subheader(f"Prédictions ({total}) — plus récentes d'abord")
            p1, p2, _ = st.columns([1, 1, 2])
            page_size = p1.number_input(
                "Lignes par page", min_value=5, max_value=100, value=20, step=5, key="page_size"
            )
            total_pages = max(1, math.ceil(total / page_size))
            if st.session_state.get("pred_page", 1) > total_pages:
                st.session_state["pred_page"] = total_pages
            page = p2.number_input(
                "Page", min_value=1, max_value=total_pages, step=1, key="pred_page"
            )
            start = (int(page) - 1) * page_size
            st.dataframe(
                df.iloc[start:start + page_size][columns],
                hide_index=True,
                width="stretch",
                column_config={
                    "processedAt": st.column_config.DatetimeColumn("traitée à", format="HH:mm:ss"),
                    "fileName": st.column_config.TextColumn("fichier"),
                    "predictedLabel": st.column_config.TextColumn("prédiction"),
                    "score": st.column_config.ProgressColumn(
                        "confiance", min_value=0.0, max_value=1.0, format="%.2f"
                    ),
                },
            )
            st.caption(f"Page {int(page)} / {total_pages}")

        if rejected_df is not None and not rejected_df.empty:
            with st.expander(f"Quarantaine — {len(rejected_df)} fichier(s) illisible(s)"):
                st.dataframe(
                    rejected_df.head(10)[["fileName", "error"]],
                    hide_index=True,
                    width="stretch",
                )

    live_dashboard()


with tab_train:

    @st.fragment(run_every=refresh_seconds)
    def train_dashboard():
        n_feat = count_rows(FEATURES_PATH)
        prev_feat = st.session_state.get("prev_feat")
        st.session_state["prev_feat"] = n_feat

        st.metric(
            "Features (flux train)",
            n_feat if n_feat is not None else "—",
            delta=(n_feat - prev_feat)
            if (n_feat is not None and prev_feat is not None and n_feat != prev_feat)
            else None,
        )
        st.caption(
            f"Dernière lecture : {now_local().strftime('%H:%M:%S')} · "
            f"rafraîchissement toutes les {refresh_seconds} s"
        )

        st.subheader("Débit du consumer (images / seconde)")
        metrics_train = load_metrics(METRICS_TRAIN)
        if metrics_train is not None:
            base = alt.Chart(metrics_train).encode(
                x=alt.X("timestamp:T", title="heure"),
                y=alt.Y("processedRowsPerSecond:Q", title="img/s"),
            )
            st.altair_chart(
                alt.layer(
                    base.mark_line(color="#378ADD", strokeDash=[4, 4], strokeWidth=1),
                    base.mark_point(filled=True, size=75, opacity=1, color="#378ADD"),
                    alt.Chart(metrics_train)
                    .mark_rule(strokeWidth=12, opacity=0)
                    .encode(
                        x=alt.X("timestamp:T", title="heure"),
                        tooltip=[
                            alt.Tooltip("timestamp:T", title="heure", format="%H:%M:%S"),
                            alt.Tooltip("processedRowsPerSecond:Q", title="img/s", format=".1f"),
                            alt.Tooltip("numInputRows:Q", title="images"),
                            alt.Tooltip("batchDurationMs:Q", title="durée (ms)"),
                        ],
                    ),
                )
                .properties(height=200)
                .interactive(),
                width="stretch",
            )
            last = metrics_train.iloc[-1]
            st.caption(
                f"Dernier batch : {int(last['numInputRows'])} images en "
                f"{int(last['batchDurationMs'])} ms ({last['processedRowsPerSecond']:.1f} img/s)"
            )

    train_dashboard()


with tab_inject:
    mode = st.radio(
        "Pipeline cible",
        ["Inférence (prédire de nouvelles radios)", "Entraînement (enrichir le feature store)"],
        horizontal=True,
    )
    infer_mode = mode.startswith("Inférence")
    flux_tab = "Inférence en direct" if infer_mode else "Flux d'entraînement"

    st.subheader("Dépôt direct — quelques images")

    split = "train"
    category = "NORMAL"
    subtype = None
    if not infer_mode:
        col_a, col_b, col_c = st.columns(3)
        split = col_a.selectbox("Split", ["train", "val", "test"])
        category = col_b.selectbox("Catégorie", ["NORMAL", "PNEUMONIA"])
        if category == "PNEUMONIA":
            subtype = col_c.selectbox("Sous-type", ["bacteria", "virus"])

    uploads = st.file_uploader(
        "Radiographies (JPG/JPEG)", type=["jpg", "jpeg"], accept_multiple_files=True
    )
    target_dir = INCOMING_INFER if infer_mode else INCOMING_TRAIN / split / category

    if st.button("Déposer dans le flux", type="primary", disabled=not uploads):
        deposited = 0
        for upload in uploads:
            marker = f"{subtype}_" if subtype else ""
            file_name = f"{uuid.uuid4().hex[:8]}_{marker}{upload.name}"
            atomic_deposit(target_dir, file_name, upload.getvalue())
            deposited += 1
        st.success(
            f"{deposited} image(s) déposée(s) dans `{target_dir.relative_to(PROJECT_ROOT)}` — "
            f"regarde l'onglet {flux_tab}."
        )

    st.divider()
    st.subheader("Injection rapide depuis le dataset")
    qcol1, qcol2 = st.columns([3, 1])
    quick_source = qcol1.text_input(
        "Dossier source (sous la racine du projet)",
        value="data/chest_xray/test",
        key="quick_source",
    )
    quick_n = qcol2.number_input("Nombre", min_value=1, value=20, step=1, key="quick_n")
    if st.button("Injecter maintenant", type="primary", key="quick_inject"):
        src_root = (PROJECT_ROOT / quick_source).resolve()
        files = sorted(
            p for p in src_root.rglob("*") if p.suffix.lower() in (".jpg", ".jpeg")
        )
        if not files:
            st.error(f"Aucune image .jpg/.jpeg trouvée sous `{quick_source}`.")
        else:
            count = min(int(quick_n), len(files))
            marker = f"{subtype}_" if (not infer_mode and subtype) else ""
            for src_file in files[:count]:
                name = f"{uuid.uuid4().hex[:8]}_{marker}{src_file.name}"
                inject_file(src_file, target_dir, name)
            st.success(
                f"{count} image(s) injectée(s) dans `{target_dir.relative_to(PROJECT_ROOT)}` "
                f"(noms uniques) — suis le résultat dans l'onglet {flux_tab}."
            )

    st.divider()
    st.subheader("Injection massive — un dossier entier via le producer")
    source_dir = st.text_input("Dossier source", value="data/chest_xray/test")
    col1, col2, col3 = st.columns(3)
    batch_size = col1.number_input("Taille de lot", min_value=1, value=64)
    interval_ms = col2.number_input("Intervalle (ms) — 0 = tout d'un coup", min_value=0, value=1000)
    limit = col3.number_input("Limite d'images — 0 = illimité (stress test)", min_value=0, value=0)

    command = (
        f"docker compose run --rm producer --service produce --source {source_dir} "
        f"--batch-size {int(batch_size)} --interval {int(interval_ms)}"
    )
    if infer_mode:
        command += " --mode infer --dest data/incoming-infer"
    else:
        command += " --dest data/incoming"
    if limit > 0:
        command += f" --limit {int(limit)}"

    st.code(command, language="bash")


