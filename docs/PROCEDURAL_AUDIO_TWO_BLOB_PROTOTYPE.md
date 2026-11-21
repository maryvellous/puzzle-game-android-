# Lava Blobs — Fase C: suono guidato da due blob fisici

**Data:** 21 novembre 2026. **Stato:** prototipo offline **eseguito e verificato tecnicamente**; ascolto e approvazione artistica da svolgere.  
**Versione del gioco:** resta **1.0.11**. **Nessuna modifica a gameplay, SoundPool, UI, asset audio originali o release.**

## 1. Perché questa fase

La fase B ha dimostrato che è possibile ricostruire il carattere dei Bloop, con un piccolo disturbo residuo percepito nelle varianti C/D. Il feedback ricevuto: **suono complessivamente buono, lieve disturbo ancora presente**. Il difetto resta aperto: non considerarlo eliminato e non compensarlo con filtri indiscriminati o cambi alla fisica.

Il traguardo della fase C è verificare una cosa diversa dalla perfezione timbrica: **il suono risponde a ciò che le membrane stanno facendo davvero**, senza riprodurre un campione identico a ogni urto del solver.

## 2. Cosa è stato implementato

### Raccolta dati: vera simulazione Java di Lava Blobs

Il test `app/src/test/java/com/maryvellous/lavablobs/AcousticTwoBlobTraceTest.java` crea quattro mondi riproducibili, ciascuno con due istanze della classe `Blob` originale, e avanza `BlobWorld.tick(1/60f)` per 96 frame.

Un osservatore **solo nei test** legge le superfici a 24 nodi (proiezione di vertici su segmenti, non collisione fra cerchi), campionando velocità normali e tangenziali relative, deformazione dei blob, dimensione e fusione. Il test esporta un CSV di valori per frame.

Scenari:
- **Impact:** blob più piccolo lanciato verso uno più grande, di tier diverso.
- **Compression:** due blob di tier diverso tenuti in contatto e schiacciati tramite impulsi di prova.
- **Sliding:** due superfici di tier diverso che scorrono mentre sono in contatto.
- **Fusion:** due blob dello stesso tier che si toccano e vengono uniti dal vero algoritmo di fusione.

Queste sono forzature di **input sperimentali** in un mondo di prova; non alterano il solver reale, la fisica della versione giocabile o i dati delle partite.

### Sintesi offline

Lo script `tools/audio_lab/render_physics_prototype.py` interpreta la traccia fisica e utilizza il **Bloop 2 originale** come prima sorgente del materiale, con la ricostruzione ibrida che conserva una maggiore porzione del timbro originale (versione D ripulita).

Genera:
- Una breve eccitazione sonora quando inizia un contatto sufficientemente dinamico.
- Una **risonanza continua** modulata dalla deformazione misurata.
- Una **texture di sfregamento continua** alimentata dalla velocità tangenziale, con lettura coerente temporalmente (senza selezione casuale di grani).
- Una **fusione** con suono derivato dal Bloop e intensità dipendente dall'interazione.
- Variazioni di altezza/carattere in funzione del raggio, senza assegnare automaticamente volume elevato ai blob grandi.

Si tratta di un **renderer sperimentale**, non ancora del DSP Android in tempo reale. Le frequenze iniziali della parte di risonanza continua (185, 260, 325 Hz) sono parametri artistici provvisori vicini alla regione analizzata dei Bloop, **non frequenze modali fisicamente validate**. La texture di sfregamento e il modello di fusione potranno cambiare dopo il confronto d'ascolto.

## 3. Risultati misurati della simulazione

Dalla [CI verificata](https://github.com/maryvellous/puzzle-game-android-/actions/runs/44934214149):

| Scenario | Frame con contatto / 96 | Velocità normale max | Velocità tangenziale max | Compressione max | Fusioni |
|---|---:|---:|---:|---:|---:|
| Urto | 26 | 292,22 | 27,64 | 0,2946 | 0 |
| Compressione | 96 | 92,32 | 32,99 | 0,2947 | 0 |
| Sfregamento | 51 | 60,87 | 196,30 | 0,1557 | 0 |
| Fusione | 1* | 178,97 | 15,66 | 0 | 1 |

*Nel test della fusione, l'unione avviene al primo step; l'osservazione delle membrane dei genitori non può continuare dopo la loro rimozione. L'evento è registrato come fusione unica.*

Le unità di velocità/compressione sono **unità interne della simulazione**, non metri al secondo o misure sperimentali di forza reale. Le grandezze sono proxy acustici derivati dai nodi; non sono forze fisiche misurate.

Tutti e quattro i WAV sono stati prodotti e controllati in CI (PCM mono 44,1 kHz, livelli sotto il limite di sicurezza, nessun file vuoto), i test Java e le regressioni esistenti sono passati.

## 4. Materiale per l'ascolto

Artifact temporaneo **`lava-blobs-physics-to-sound-two-blob`** nella [pagina della run](https://github.com/maryvellous/puzzle-game-android-/actions/runs/44934214149), con conservazione di 14 giorni dal 21 novembre 2026.

Contenuti:
- `listen_real_physics_four_behaviors.wav` — montaggio **urto → compressione → sfregamento → fusione**, separati da pause.
- `impact_physics.wav`, `compression_physics.wav`, `sliding_physics.wav`, `fusion_physics.wav` — tracce singole.
- `real_physics.csv`, `real_physics_trace.png`, `physics_audio_metrics.json` — valori fisici, grafici e misure.
- Copia dell'esperimento fuori dal codice di produzione; nessun sample derivato incorporato nel gioco.

## 5. Cosa conferma e cosa NON conferma

**Confermato:** si possono estrarre senza modificare il gameplay delle misure di contatto/velocità/deformazione dal vero risolutore e usarle per variare un suono derivato dai Bloop. Il prototipo produce quattro comportamenti distinguibili come condizioni fisiche.

**Non confermato:** che le quattro tracce risultino naturali o piacevoli per l'utente; che il disturbo residuo dei prototipi C/D sia sparito; che la fusione procedurale superi la fusione originale; che la sincronizzazione e il budget CPU siano adeguati su un vero telefono Android. Il test è offline.

## 6. Gate d'ascolto e successivi passaggi

Il gate della fase C resta aperto fino a un ascolto sul telefono:
1. **Causalità:** il suono dell'impatto si distingue da quello dell'attrito e la compressione sembra sostenuta, non una sequenza di campioni casuali?
2. **Qualità:** resta un rumore fritto o appaiono crepitii ai passaggi tra contatto e silenzio?
3. **Fusione:** mantiene il fascino dei Bloop originali?
4. **Bilanciamento:** come si comporterebbe durante una partita con più blob e una musica in sottofondo?

Dopo questo ascolto: affinare il modello continuo con i **quattro** Bloop e le loro caratteristiche misurate (qui si usa Bloop 2 come singolo esempio), aggiungere ID stabili di contatto e deduplica dei cinque passaggi del solver, quindi valutare un primo motore PCM Android di prova con interruttore A/B e test di latenza/CPU. **Non sostituire l'audio della release 1.0.11 prima dell'approvazione.**

## 7. Riproduzione tecnica

In GitHub Actions si eseguono `:app:testDebugUnitTest` e la generazione di `app/build/audio_lab/two_blob_physics.csv`, poi:

```bash
python tools/audio_lab/render_physics_prototype.py \
  --traces app/build/audio_lab/two_blob_physics.csv \
  --sources app/src/main/res/raw \
  --output build/audio_lab/two_blob
```

Workflow: `.github/workflows/two-blob-acoustic-prototype.yml`.  
Fonte sonora: Bloop 2 (floraphonic, Pixabay; provenienza e licenza documentate nel README). I WAV di prova non vanno redistribuiti come pacchetto audio autonomo.
