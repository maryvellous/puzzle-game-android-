# AGENTS.md — Lava Blobs

## Regole di lavoro
- ChatGPT può leggere e modificare repository, documentazione, codice, branch e PR direttamente via GitHub. Usare Codex per esecuzione locale, collaudo Android, UI e debugging operativo che richieda un ambiente interattivo.
- Completare gli interventi richiesti senza delegare attività eseguibili; autonomia tecnica, non nelle decisioni di prodotto non definite.
- Prima di scrivere verificare `main`, PR aperte e modifiche concorrenti. Commit mirati, test proporzionati; distinguere **documentato**, **committato**, **testato in CI**, **verificato sul telefono** e **distribuito**.
- Non fare force-push, merge di PR, release o deploy senza incarico esplicito. Niente cancellazioni di dati utente, refactor estranei, servizi a pagamento o modifiche arbitrarie alla fisica. Non pubblicare segreti.
- La cronologia Git conserva gli esperimenti passati; **non usare documenti o branch storici come requisiti correnti**.

## Stato del prodotto
- Repo `maryvellous/puzzle-game-android-`; progetto attivo `app/`.
- Main 1.1.0 (versionCode 18), package `com.maryvellous.lavablobs`. Audio V2.3 approvato sul telefono e promosso su richiesta dell'utente il 27 novembre 2026. Ultima release pubblicata 1.0.11; distribuzione separata, solo su richiesta.
- Base di sviluppo: preview approvata `cfb03c96fd5e348b5e0ddd0c201255101913fa5f`. Nuovi interventi partono dal main, non dai vecchi branch audio. Stesso motore in main e preview; SoundPool classico non selezionato.
- Preservare gameplay e solver a 24 nodi, palette e interfaccia, slider, quattro Bloop e swoosh. Nessuna attività aggiuntiva di migrazione salvataggi richiesta.
- Build: JDK 17, SDK 35, Gradle 8.9. CI `lava-blobs.yml`; pubblicazione manuale `lava-blobs-production.yml`.

## Specifica audio vigente

[Motore Audio V2 — la materia diventa suono](docs/AUDIO_ENGINE_V2_MATERIAL_BLOOP.md) è l'unica specifica vigente.

- Fusione = uno dei quattro Bloop completi, senza voice stealing.
- Parete = attacco V2.2; blob-blob = risposta gelatinosa; squish finiti guidati da movimento e deformazione. Lancio = swoosh classico approvato.
- Contatto non equivale a suono: quiete silenziosa, niente loop, deduplicazione dei cinque solver pass e degli urti sulle pareti.
- Probe passivo → Tracker → MaterialAudioEngine → AudioTrack. Conservare il DSP approvato; test fisici e sonori più verifica percettiva per future modifiche.
- Volume 0–100% dopo limiter, mute/ripristino persistenti. Musica futura distinta.

## Riscontro fisico e audio V2.1, 23 novembre 2026

- **Primo playtest V2 fallito:** nel video Android `1000057175.mp4` la user ha riconosciuto un acuto statico continuo con blob a contatto apparentemente fermi. L'assistente aveva erroneamente certificato il suono come buono sulla base di picchi e clipping; **mai ripetere questa inferenza**. L'esame della traccia AAC mono ha rilevato una componente persistente intorno a 576 Hz nei primi 4 s.
- **Difetto concreto:** `MaterialAudioEngine` ripeteva periodicamente una tail texture e la teneva attiva con scorrimenti >4 unità/s o piccole differenze di compressione, amplificando oscillazioni del solver. Non era un modello fedele di energia/movimento percepibile.
- **Fix sperimentale V2.1:** rimosso *ogni* loop da `BloopMaterialBank` e mixer. I contatti sono **silenti per default**: squish è un solo frammento breve di Bloop tail, innescato da movimento bulk rilevante **calcolato dalle velocità medie delle 24 particelle del blob** o da brusca deformazione; soglie di quiete e credito di moto con reset sui micro-movimenti. Impatti reali one-shot; fusioni sempre Bloops completi. `ContactProbe.Contact.bulkMotion` passa identico a tracce CSV, renderer JVM e adapter `AudioTrack`; il motore usa `dt` fisico anche se la coda Android accorpa comandi.
- **Regressioni con solver Java vero, CI 45279301704 completata con successo:** recipiente affollato dopo assestamento, 4 s, 1303 osservazioni da 5 coppie simultanee, slip locale fino a 126.9 ma bulk max 67.3 → **0 squish, 0 impatti, output esattamente 0**. Stesso setup con shake ripetuti → 1476 osservazioni, bulk max 509.1 → **9 squish**, 8 impatti, 3 fusioni. Test di contatti statici sintetici 10 s, assenza di loop, polifonia fusioni, Android `:app:assemblePreview` superati.
- **Stato storico:** V2.1 era una preview; la V2.3 successiva è stata approvata il 27 novembre.


## Materiale storico, non autoritativo
- `tools/audio_lab/`, i workflow `procedural-audio-lab.yml` e `two-blob-acoustic-prototype.yml`, e `AcousticTwoBlobTraceTest.java` provengono dagli esperimenti A/B e C1–C4.1. Possono fornire dati/idee, **non** implementano la V2 e non vanno eseguiti o copiati come percorso di produzione.
- Nelle prove vecchie la mappatura dimensione→pitch era promettente; molti urti/compressioni non erano convincenti, la Mix Test mostrava crepitii e le PR [#23](https://github.com/maryvellous/puzzle-game-android-/pull/23) e [#24](https://github.com/maryvellous/puzzle-game-android-/pull/24) non vanno fuse per inerzia.
- La documentazione V1 e i diari delle vecchie fasi sono stati rimossi dal branch attivo per evitare indicazioni contraddittorie; restano accessibili nella cronologia Git. **Non ricrearli né usare indicazioni di sintesi della fusione come se fossero approvate.**
