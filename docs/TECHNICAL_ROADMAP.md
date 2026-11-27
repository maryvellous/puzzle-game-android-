# Roadmap tecnica canonica — 27 novembre 2026
Baseline: main 1.1.0 / Audio V2.3 approvato. Questo documento governa il risanamento della codebase e prevale sull'ordine di lavoro del [report A01–A08](TECHNICAL_DEBT_AUDIT.md); [specifica audio](AUDIO_ENGINE_V2_MATERIAL_BLOOP.md) resta normativa per il comportamento, [aggregazione](AUDIO_AGGREGATION_PLAN.md) per il progetto acustico.
## Obiettivo
Un prodotto maturo, non un game engine generico. Solver, presentazione e audio autonomi ma coerenti: simulazione unica fonte degli eventi, UI/aptica/audio consumatori; probe/tracker → qualificazione → aggregazione → scheduler PCM → mixer/limiter/volume → AudioTrack. Replay offline usa lo stesso core runtime. Conservare Java, solver, AudioTrack, campioni, timbro e gameplay approvati; nessuna migrazione C++/middleware senza evidenza. Nessun intervento salvataggi o pubblicazione.
## Sequenza e gate
0. Diagnostica e replay (A05, baseline A01/02/03/06): età/profondità coda, scarti per tipo, tempi dispatch/render/write, underruns delta, peak dry e gain reduction, voci per famiglia, tracce ripetibili e WAV. Gate: baseline misurabile, distinguere mix/timing/buffering. Telefono resta necessario.
1. Contratto e trasporto (A01/02/07): eventi tipizzati con identità, tick/tempo, generation; osservazioni comprimibili, eventi autorevoli mai persi silenziosamente; saturazione esplicita e riconciliazione, drain limitato; fusioni dal solver, non UI. Scheduler sample-offset, gestione ritardi e clock. Gate: ordering/reset, replay batching diversi, equivalenza bypass su eventi isolati.
2. Runtime (A04/05): unico owner AudioTrack; lifecycle esplicito, generation, pause/mute/reset/errore; core/decoder PCM/scheduling condivisi con CLI, trace wall/input/merge completa. Gate: test lifecycle e parità runtime/offline.
3. Aggregazione (A03): dopo gating, prima delle voci; GEL/WALL/SQUISH separati, finestre finite, energia limitata, pitch ponderato, bypass singolo identico; FUSION/LAUNCH/POP diretti, Bloop interi. A/B, target ≥50% riduzione voci secondarie nei burst rappresentativi, minore gain reduction, nessun nuovo underrun, silenzio e ascolto sul telefono. Nessun tuning definitivo senza ascolto.
4. Fisica (A06): profilare CPU/GC, ridurre allocazioni e calcoli ridondanti conservando campioni fisici; broad-phase solo se necessario. Gate: equivalenza numerica e trace, stress sul telefono.
5. Consolidamento (A07/08): separare orchestrazione UI, rimuovere legacy verificato (non risorse/licenze necessarie), consolidare CI e artefatti version/SHA; preservare release manuale e firma. Gate: test, build, documentazione coerente, playtest e approvazione prima di promuovere.
## Protocollo di lavoro
Branch dedicato, tranche piccole; CI in parallelo con attività indipendenti, nessuna attesa passiva; correggere esiti negativi prima di avanzare nei gate dipendenti. Nessuna promozione main né pubblicazione senza approvazione. Misure proposte non sono risultati già ottenuti.
## Registro esecuzione
### 27 novembre — tranche 1, PR #27
Branch [refactor/technical-debt-roadmap](https://github.com/maryvellous/puzzle-game-android-/pull/27). Main 1.1.0 / V2.3 invariata. Lavoro svolto in parallelo alle CI; le run superate da commit successivi sono cancellate per evitare sprechi.
| Fase | Implementazione candidata | Gate residuo |
|---|---|---|
| 0 | Peak dry/output, gain reduction, occupazione limiter, voci corte, queue age/depth, p99 stimati con istogrammi, underrun delta, scarti per tipo. Trace v1 JVM e registrazione locale limitata nelle preview; replay CI con WAV/CSV A/B. | Misure su telefono e A/B su materiale originale della CI finale; baseline V2.3 originale conservata in main. |
| 1 | AudioEvent immutabile; PhysicsAudioEvents condiviso; sorgente solver per fusioni, lancio, POP e pareti; FIFO con riserva per controlli, generation, AudioTimeline e AudioBlockScheduler. | Replay stress finale e verifica latenze reali; tick intero e policy di coalescing osservazioni ancora da valutare. |
| 2 | AudioTrack con un solo owner; invalidazione generazioni, flush e mute/reset coordinati; preparazione PCM condivisa; scheduler utilizzato anche offline. | Test lifecycle reali (avvio, home, pause/resume rapidi, mute, errore device); recovery e state machine formale non ancora completate. Decoder desktop FFmpeg e Android MediaCodec non sono bit-identici. |
| 3 | Aggregatore sperimentale GEL/WALL/SQUISH dopo gating, finestre 12/30 ms, rate compatibile 1.12, energia limitata, singolo invariato dopo allineamento. FUSION/LAUNCH/POP diretti. Build separata aggregationPreview. | A/B reale, ascolto, latenza; tuning finestre e budget per famiglia, cancellazione eventuali cluster ancora pendenti su rimozione. Default main/release resta bypass. |
| 4 | Bulk velocity calcolata una volta per confine di un blob, non per vertice; contatore voci corte O(1); esclusa raccolta impatti legacy in gameplay. | CI equivalenza, profilazione CPU/GC su dispositivo; nessuna riscrittura solver o broad-phase. |
| 5 | CI standard con test, replay, WAV/CSV e APK separati, cancellazione run PR obsolete; sorgente eventi non più dalla View. | Inventario e rimozione classi/test/workflow legacy, ulteriore separazione View; firma/pubblicazione non toccate. |
### Verifiche effettive
- CI [136](https://github.com/maryvellous/puzzle-game-android-/actions/runs/46062025494): strumentazione iniziale verde.
- CI [138](https://github.com/maryvellous/puzzle-game-android-/actions/runs/46062128334) e [140](https://github.com/maryvellous/puzzle-game-android-/actions/runs/46062169544): primo trasporto/timeline e test riserva FIFO verdi.
- CI [145](https://github.com/maryvellous/puzzle-game-android-/actions/runs/46062321480): scheduler condiviso, equivalenza PCM su due batching, generazioni e limiter verdi.
- CI [146](https://github.com/maryvellous/puzzle-game-android-/actions/runs/46062407828): preparazione materiale condivisa verde.
- CI [151](https://github.com/maryvellous/puzzle-game-android-/actions/runs/46062515792) e [153](https://github.com/maryvellous/puzzle-game-android-/actions/runs/46062576989): adapter eventi e trace reale verdi.
- CI [156](https://github.com/maryvellous/puzzle-game-android-/actions/runs/46062673453): aggregazione sintetica verde, 8 impatti compatibili → 1 voce, fusione completa, singleton identico dopo ritardo finestra. Non è un risultato del telefono.
- CI dell'insieme finale: consultare [check PR #27](https://github.com/maryvellous/puzzle-game-android-/pull/27/checks); non certificata al momento della stesura. Nessun collaudo sul dispositivo, nessun merge o release.
### Collaudo successivo
1. Scaricare dalla CI finale i due APK dello stesso SHA (bypass .audiov21, cluster .audioaggregation). Non disinstallare main. Confrontare stessi livelli, volume, stasi, shake, urti vetro/gel, cascata, lancio/POP e pausa/mute ripetuti; confrontare anche V2.3 approvata. 
2. Logcat tag LavaV2Audio: queue depth/age, p99 stimati, underrun delta, peak dry, gain minimo/occupazione, voci, cluster, scarti. I tempi p99 a bucket sono limiti superiori approssimati, non misure precise. Il write bloccante include pacing device, non solo CPU.
3. Nelle preview, dopo una scena breve e pausa, export in cache audio-trace-v1.csv e audio-trace-v1.meta: adb shell run-as com.maryvellous.lavablobs.audioaggregation cat cache/audio-trace-v1.csv (o .audiov21). Prima finestra 30s/32768 eventi per generation, metadata truncated e contatori cumulativi, non telemetria; la traccia è input fisico, non include arrivi reali né output audio. Replay normalizzato al primo evento. La registrazione può influire leggermente su profilo preview; per benchmark rigoroso misurare anche debug senza recorder.
4. Gate: nessun nuovo underrun, frittura ridotta, nessuna fusione persa, silenzio dopo code, latenza accettabile. Se A/B o CI fallisce correggere prima di promuovere. Non attendere CI passivamente: lavorare solo su passi indipendenti.
### Rischi espliciti
Coda finita: overflow autorevole segnala errore e sospende audio fino a reset invece di eliminare silenziosamente eventi precedenti; non è garanzia di zero perdite in carico illimitato. Avvio prima di READY ancora scarta eventi conteggiati. Budget dispatch per numero di eventi e non per nanosecondi. Nessuna diagnosi definitiva della frittura senza dati reali. Il prototipo non modifica il timbro delle sorgenti né la fisica, ma ritarda gli eventi secondari per la finestra; va ascoltato. Vecchi file conservati per test/storia fino all'inventario. Firma, salvataggi e pubblicazione non modificati.

### 27 novembre — tranche 2, stabilizzazione e separazione responsabilità
- CI [170](https://github.com/maryvellous/puzzle-game-android-/actions/runs/46063233061): test unitari passati, replay fallito in preparazione (prima del render), APK non generati. Il log originale non distingue trace mancante da FFmpeg mancante. Correzione: test delle tre varianti senza build cache, controllo esplicito delle tracce e delle risorse, installazione FFmpeg se assente, log per stadio. Non dichiarare risolto fino a CI verde.
- **A01/A04:** nuova `AudioSession` (snapshot atomico, STARTING/READY/PAUSED/MUTED/OVERFLOW/ERROR/STOPPED; admission e invalidazione coda serializzate; pause/mute idempotenti). Test di avvio, reset, generazioni, saturazione, failure terminale. La coda non promette perdite zero in carico illimitato; gli eventi di startup sono contati ma non riprodotti. Recupero automatico da errore hardware non implementato: da verificare sul device.
- **A03:** cluster con contributi identificati per blob e limite 64 per gruppo; su merge/POP annullare solo squish pendenti dei blob scomparsi, mantenere urti realmente avvenuti e voci già udibili. Test di cancellazione parziale/totale; nessun tuning definitivo prima dell'ascolto.
- **A07:** `SimulationClock` separa timestep e catch-up da Canvas, azzera il residuo al riavvio. `GameSessionController` separa checkpoint, tempo attivo, finalizzazione e restart, con port di persistenza testabile; nessuna modifica a GameSave, ProgressStore o formato dati. `LavaView` resta responsabile di presentazione, input, aptica e coordinamento dei componenti. Test di pacing, checkpoint fallito/riprova, finalizzazione unica e restart.
- **A07/A08:** rimossi ClassicGameSound, GameAudio (SoundPool), EffectCompiler e metodi legacy no-op dal runtime. Rimossi tre workflow sperimentali superati (procedural-audio-lab, two-blob-acoustic-prototype, audio-v2-offline); storia preservata in Git. Mantenuti risorse originali, licenze, workflow di release manuale, policy/fixture legacy ancora usati dai test; inventario finale di script/risorse rinviato a CI verde, nessuna cancellazione alla cieca.
- **Gate residuo:** CI complessiva delle modifiche di tranche 2, metriche A/B su trace solver, build di preview separate; collaudo hardware e profilazione. Non integrare main prima dell'approvazione. Non confondere unit test o replay con ascolto reale.

### 27 novembre — gate di collaudo raggiunto
CI [189](https://github.com/maryvellous/puzzle-game-android-/actions/runs/46065219493) verde su head `fa01f4afb2b2cb4a3a594bdf86c815f9f0905422`, merge di verifica `72c5b5a4fe3021a906c70de81558bc9ed7911dc6`. Test debug, preview e aggregationPreview, replay e tre APK riusciti. Artefatti verificati non scaduti al momento del controllo:
- [A: bypass APK](https://github.com/maryvellous/puzzle-game-android-/actions/runs/46065219493/artifacts/15230412471), package .audiov21.
- [B: aggregazione APK](https://github.com/maryvellous/puzzle-game-android-/actions/runs/46065219493/artifacts/15229858129), package .audioaggregation verificato da aapt.
- [WAV e metriche A/B](https://github.com/maryvellous/puzzle-game-android-/actions/runs/46065219493/artifacts/15230437510), trace solver identica, decoder desktop.
| Metrica | A bypass | B cluster |
|---|---:|---:|
| Picco voci secondarie |20|9|
| Picco voci totali |28|18|
| Fusioni iniziate/completate |9/9|9/9|
| Peak dry |1.5485017|1.6843451|
| Peak output |0.8700001|0.8700001|
| Minimo gain limiter |0.5618334|0.5165212|
| Frame con limiting |44274|42083|
| Frame renderizzati |220800|220800|
| Voci corte respinte |0|0|
B: 49 candidati, 33 gruppi, 16 aggregati, nessun gruppo respinto. Riduzione del picco voci secondarie 55% (target 50% superato sul fixture reale); limiting meno frequente ma più intenso nel caso peggiore, picco dry maggiore. Non implica eliminazione della frittura. Nessun collaudo hardware eseguito. Freeze del codice candidato: prossimo gate ascolto A/B e confronto con main V2.3, verifica lifecycle e profiling. Non disinstallare main; eventuale conflitto firma riguarda solo preview. Ulteriore pulizia legacy rimandata per non cambiare la candidata prima del collaudo.
