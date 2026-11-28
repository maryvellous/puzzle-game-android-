# Stato tecnico e passaggio alla fase di prodotto
**28 novembre 2026 · consolidamento verificato, anteprima prodotto in collaudo**

## Stato
[PR #27](https://github.com/maryvellous/puzzle-game-android-/pull/27), non ancora integrata in main. Sorgente verificata: [5569e3c](https://github.com/maryvellous/puzzle-game-android-/commit/5569e3cebe4fe1c7d77b7db50cdd6b560b26b02a), [CI #253](https://github.com/maryvellous/puzzle-game-android-/actions/runs/46097012021), merge CI 07d52d59b89dc767cc647ab56239a7c0f6b9c1cd. Baseline percettiva [43aca8e](https://github.com/maryvellous/puzzle-game-android-/tree/baseline/late-game-approved-2026-11-28), CI #238: maggiore fluidità e nessun frittino percepito durante la prova. Non è una misura strumentale FPS/underrun. Nessuna distribuzione eseguita.

**Ramo di lavoro attuale:** `refactor/technical-debt-roadmap`, ultimo commit applicativo `29624f4a4e0079ebd6a565e8667c92d7011c1a3a` (logo Home, CI/visione da confermare); ultimo APK verificato da `96d4493fe66fe06dd16aab72e590a1ee4b200ac4`. [UI Preview 4](https://github.com/maryvellous/puzzle-game-android-/actions/runs/46147627037) e [CI completa](https://github.com/maryvellous/puzzle-game-android-/actions/runs/46147627030) entrambe verdi. [Artefatto APK](https://github.com/maryvellous/puzzle-game-android-/actions/runs/46147627037/artifacts/15264287233). Home/HUD/12 recipienti storia implementati e in collaudo, non approvati per merge. Priorità immediata: collaudare la nuova ricostruzione del logo in forme morbide (non testo deformato; originale non trovato nella storia consultata); verificare spaziatura e diagnosticare rallentamento 8–10 blob sul telefono. Requisiti, feedback e passaggio di sessione in [PRIVATE_PLAYTEST.md](PRIVATE_PLAYTEST.md).

## Priorità operativa — fluidità Android
Report reale del 28 novembre (shape 14): 43.2 FPS con 8–10 blob, 25.3 FPS con 11–15; fisica dominante nel tempo UI. [Piano di recupero prestazioni](PERFORMANCE_RECOVERY_PLAN.md): baseline, obiettivi, profilazione per fase, ottimizzazioni conservative, checkpoint e gate dispositivo. Priorità P0; F0–F2 implementazione iniziale in collaudo, F3 non avviata. Registro e risultati JVM nel piano; collaudo Android ancora aperto. Logo da approvare; tutorial, musica e bilanciamento sospesi fino alla verifica di fluidità.

## Interventi di recupero prestazioni
- Diagnostica per fasi, finestre recenti e percentili; ricerca ray-crossing pareti e nearest edge conservativo. CI #20 e #25: stati/eventi identici, miglioramenti JVM documentati nel piano.
- Checkpoint: snapshot immutabile e worker seriale latest-wins; test concorrenza/barriere aggiunti. Verificare su Android, inclusa finestra di durabilità al process kill. CI finale #31/#287 da confermare.

## Interventi conclusi
| Area | Risultato |
|---|---|
| Trasporto | Eventi immutabili con timestamp/generazione, FIFO preallocata con riserva, scheduling sample-offset e budget dispatch. |
| Runtime | AudioSession, owner unico AudioTrack, reset/pause/mute coordinati, preparazione PCM condivisa. |
| Audio | Aggregazione dopo gating, pool voci, mixer e limiter refined; factory unica di gameplay. CUBIC e APK bypass ritirati. |
| Fisica | Cache metriche e geometria pareti, pruning conservativo, riuso accumulatori e coppie, invariati 24 nodi/cinque passaggi e ordine proiezioni. |
| UI/sessione | SimulationClock e GameSessionController separati; runtime legacy inutilizzato rimosso. |
| CI | Fixture densa, replay condiviso, test/build debug, preview e release, confronto con baseline. |
| Distribuzione | Workflow manuale main-only, firma persistente obbligatoria, artifact-only di default, checksum e verifica APK. |

## Evidenze
- #238: tutti i test e build verdi, 18 trace e 34.126 eventi identici al riferimento originale; p99 tick JVM ridotto circa 19–22% sullo stesso runner. Non è una misura sul telefono.
- #253: tutti i test, confronto denso, replay e build verdi; WAV e stem conservati identici al codice #238 compilato separatamente. Release CI non firmata.
- [Fixture densa #253](https://github.com/maryvellous/puzzle-game-android-/actions/runs/46097012021/artifacts/15243438320) · [Replay #253](https://github.com/maryvellous/puzzle-game-android-/actions/runs/46097012021/artifacts/15243846383) · [Preview diagnostica #253](https://github.com/maryvellous/puzzle-game-android-/actions/runs/46097012021/artifacts/15243766942).
- Il riferimento storico f14cf96 serve alla verifica degli stati e dei trace densi; 43aca8e al confronto del PCM. Non modificare i gate conservativi per ottenere una CI verde. Se una forma/obiettivo cambia intenzionalmente, aggiornare scenario ed evidenza esplicitamente.

- CI UI #5: equivalenza stati/tracce sui tre recipienti arrotondati rispetto a bde5213; p99 JVM peggiora in due scene, migliora in una. Non dimostrata accelerazione generalizzata. UI Preview 3 rallenta con 8–10 blob secondo il collaudo dell'autrice. Causa sconosciuta; #4 con diagnostica ancora da provare sul telefono.

## Limiti residui e gate
- I test non certificano prestazioni GPU, latenza reale o underrun Android. Correggere regressioni riproducibili, non ottimizzare per principio.
- Coda finita: osservazioni possono essere respinte; overflow autorevole invalida la sessione, eventi startup contati ma non riprodotti; errore device terminale senza recovery automatica. Non promettere assenza assoluta di perdite o GC.
- Decoder Android MediaCodec e desktop FFmpeg non necessariamente bit-identici. Conservare test dei profili offline, asset e licenze.
- Prima della consegna: approvazione prodotto, smoke test pacchetto normale, versione crescente, verifica firma e distribuzione esplicitamente autorizzata. Secret reali non ancora verificati. Main invariata.

## Diagnostica su regressione
Nelle build debug/`uiPreview`, menu Pausa → Copia dati prestazioni: `FrameProfile` aggrega tempi per numero di blob. Chiedere export dopo aver riprodotto il rallentamento; separare tempo fisica/tick, Canvas CPU, intervalli frame, UI e salvataggi. Nessuna telemetria remota; non misura il tempo GPU.
LavaPerf e LavaV2Audio: frame gap, tick, canvas CPU submission, p99 dispatch/render/write, coda, underruns, limiter e voci. I p99 a bucket sono stime; write include pacing device. Preview: cache audio-trace-v1.csv e audio-trace-v1.meta (massimo 30 s/32768 eventi per generazione), export locale via run-as del package .audioaggregation; non telemetria, non output PCM. Consultare [specifica audio](AUDIO_ENGINE_V2_MATERIAL_BLOOP.md).

## Storia
[Registro completo fino al consolidamento](https://github.com/maryvellous/puzzle-game-android-/blob/5569e3cebe4fe1c7d77b7db50cdd6b560b26b02a/docs/TECHNICAL_ROADMAP.md), [audit originale](https://github.com/maryvellous/puzzle-game-android-/blob/5569e3cebe4fe1c7d77b7db50cdd6b560b26b02a/docs/TECHNICAL_DEBT_AUDIT.md), [piano aggregazione](https://github.com/maryvellous/puzzle-game-android-/blob/5569e3cebe4fe1c7d77b7db50cdd6b560b26b02a/docs/AUDIO_AGGREGATION_PLAN.md). Conservati nella storia Git, non più fonti operative.
