# AGENTS.md — Lava Blobs

## Regole di lavoro
- ChatGPT può leggere e modificare repository, documentazione, codice, branch e PR direttamente via GitHub. Usare Codex per esecuzione locale, collaudo Android, UI e debugging operativo che richieda un ambiente interattivo.
- Completare gli interventi richiesti senza delegare attività eseguibili; autonomia tecnica, non nelle decisioni di prodotto non definite.
- Prima di scrivere verificare `main`, PR aperte e modifiche concorrenti. Commit mirati, test proporzionati; distinguere **documentato**, **committato**, **testato in CI**, **verificato sul telefono** e **distribuito**.
- Non fare force-push, merge di PR, release o deploy senza incarico esplicito. Niente cancellazioni di dati utente, refactor estranei, servizi a pagamento o modifiche arbitrarie alla fisica. Non pubblicare segreti.
- La cronologia Git conserva gli esperimenti passati; **non usare documenti o branch storici come requisiti correnti**.

## Stato del prodotto
- **Repository:** `maryvellous/puzzle-game-android-` (l'utente rinominerà eventualmente il repo; non farlo autonomamente). **Progetto attivo:** `app/`, solo Lava Blobs Android. Nessun Rovina Puzzle nel codice principale; `junior-games-scintilla` è un progetto diverso.
- **Release attuale:** **1.0.11**, versionCode 17, package `com.maryvellous.lavablobs`. SFX attuali con `SoundPool` e pulsante `SUONI/MUTO`; musica non implementata. La versione classica resta invariata finché non c'è approvazione esplicita di una sostituzione.
- **Da preservare:** soft-body da 24 nodi e solver, adesioni, 12 recipienti, progressi e salvataggi, colori/UI, quattro Bloop originali, preferenza `sound_effects_enabled_v1`. Musica futura con mute distinto.
- **Build:** JDK 17, SDK 35, Gradle 8.9; `gradle --no-daemon :app:testDebugUnitTest :app:assembleDebug :app:assemblePreview`. Pubblicazione via workflow manuale `.github/workflows/lava-blobs-production.yml`; non avviarlo per manutenzione. Conservare le avvertenze sulla firma APK non persistente.

## UNA SOLA fonte progettuale per l'audio nuovo

**[Motore Audio V2 — la materia diventa suono](docs/AUDIO_ENGINE_V2_MATERIAL_BLOOP.md)** è la **specifica vigente**. Non sostituirla con appunti, vecchi esperimenti, PR o un'altra proposta V1.

> «Un sistema in cui il comportamento della materia diventa suono.»

- **Fusione = evento principe:** deve riprodurre uno dei **quattro Bloop originali COMPLETO**. Non sintetizzarne un sostituto né troncare il suono per liberare voci.
- **Altri gesti:** solo **materiale parziale/trasformato dagli stessi Bloop** (attacchi, code, microtrame senza corpo della fusione), con inviluppi morbidi e risposta a movimento, pressione, sfregamento, dimensione e durata. La quiete è spesso corretta.
- **Catena:** Contact Probe passivo su membrane reali → Contact Tracker con identità e durata delle coppie → Material Engine/Bloop Bank → Scene Mixer unico → output Android.
- Distinguere contatto fisico da adesione; i **5 passaggi del solver** non generano 5 urti. Nessuna modifica alla fisica per inventare suoni. I blob piccoli tendono a essere più acuti/rapidi, quelli grandi più gravi/lenti; non confondere massa e volume.
- Fusione prioritaria ma non necessariamente più forte per ogni istante; impedire glitch, clipping, click, ripetizioni di urti e accumulo del lancio senza troncare i Bloop completi.
- **Ordine di lavoro:** analisi offline dei quattro Bloop → Contact Probe/Tracker con test deterministici → banco materiale e mix offline → stress test → **una sola** APK diagnostica → ascolto e approvazione su telefono → eventuale integrazione. **Non creare APK V2 prima dei gate offline.**

## Materiale storico, non autoritativo
- `tools/audio_lab/`, i workflow `procedural-audio-lab.yml` e `two-blob-acoustic-prototype.yml`, e `AcousticTwoBlobTraceTest.java` provengono dagli esperimenti A/B e C1–C4.1. Possono fornire dati/idee, **non** implementano la V2 e non vanno eseguiti o copiati come percorso di produzione.
- Nelle prove vecchie la mappatura dimensione→pitch era promettente; molti urti/compressioni non erano convincenti, la Mix Test mostrava crepitii e le PR [#23](https://github.com/maryvellous/puzzle-game-android-/pull/23) e [#24](https://github.com/maryvellous/puzzle-game-android-/pull/24) non vanno fuse per inerzia.
- La documentazione V1 e i diari delle vecchie fasi sono stati rimossi dal branch attivo per evitare indicazioni contraddittorie; restano accessibili nella cronologia Git. **Non ricrearli né usare indicazioni di sintesi della fusione come se fossero approvate.**
