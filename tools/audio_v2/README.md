# Lava Blobs Audio V2 — laboratorio offline (NON specifica di prodotto)

**Riferimento progettuale unico:** [specifica Audio V2](../../docs/AUDIO_ENGINE_V2_MATERIAL_BLOOP.md).

Il laboratorio sul branch `experiment/lava-blobs-audio-v2-engine` non modifica `main` 1.0.11. La ricerca offline Python è rimasta un riferimento d'ascolto; **ora esiste anche un core PCM Java riutilizzato da una APK diagnostica Android**. Nessuna distribuzione o approvazione hardware prima del gameplay test.

## Cosa esiste

- `material_bank.py`: decodifica gli MP3 originali a PCM **solo in memoria**, misura onset/crest/decadimento con RMS a 5 ms e propone un attacco breve, un nucleo centrale riservato e una coda. **Confini candidati, non approvati automaticamente**: `bloop_regions_review.png` e `material_bank_candidates.json` permettono di vedere le misure e confermarle all'ascolto.
- Audizioni isolate: per ognuno dei quattro Bloop, **completo**, **attacco**, **coda** e **attacco+coda senza nucleo**. Non si committano file estratti né si distribuiscono sound pack.
- `render_auditions.py`: legge `ContactProbeTraceTest` (vero solver Java), riproduce **un Bloop intero** solo su fusione; urto e scorrimento sono fatti con attacchi e code. La compressione modula solo la risposta già attiva; la quiete è ammessa. L'anteprima è **un esperimento Python da ascoltare**, non il DSP da copiare nell'app.
- `synthetic_mixer_stress.wav`: stress tecnico volutamente **sintetico, non fisica reale** con 12 fusioni intere e 80 attacchi corti; nessuna fusione viene interrotta. L'headroom del mix viene regolato globalmente: non è ancora un mix bus realtime con priorità e budget voci.
- `real_12_blob_scene_stress.wav`: sovrapposizione **da 12 blob simulati davvero in `BlobWorld`**, con contatti, scorrimento e fusioni effettivamente rilevati. I dati provengono da `scene_stress_trace.csv`. Il rendering gestisce una texture collettiva con guadagno sublineare e conserva ogni Bloop di fusione completo.
- Test con sorgenti sintetiche per segmentazione, decadimenti, campioni non validi, mapping dimensione→durata, assenza di Bloop completi nei non-fusione, mix e preservazione integrale dei Bloop sulle fusioni.
- **Video sincronizzati:** `render_sync_video.py` usa gli snapshot di **tutti i 24 nodi** delle membrane esportati dal vero `ContactProbeTraceTest` (CSV `two_blob_mesh_frames.csv` e `scene_stress_mesh_frames.csv`) e allinea i video MP4 agli WAV di ricerca: cinque comportamenti, scena con 12 blob. I punti chiari sono contatti misurati. **Il recipiente è schematico e non è l'UI Android:** il test verifica causalità audiovisiva, non latenza su dispositivo.

## Rigenerazione

L'esecuzione completa è automatizzata nel workflow
`.github/workflows/audio-v2-offline.yml`, con artefatto ad accesso limitato di 14 giorni.

```bash
# JDK 17 + SDK Android 35 + Gradle 8.9:
gradle --no-daemon :app:testDebugUnitTest

# Python 3.11, numpy/scipy/matplotlib/imageio-ffmpeg:
python -m unittest discover -s tools/audio_v2 -p 'test_*.py' -v
python tools/audio_v2/material_bank.py --sources app/src/main/res/raw --output build/audio_v2/material
python tools/audio_v2/render_auditions.py --sources app/src/main/res/raw --trace app/build/audio_v2/contact_probe_trace.csv --output build/audio_v2/listening
python tools/audio_v2/render_sync_video.py \
  --mesh-two app/build/audio_v2/two_blob_mesh_frames.csv \
  --mesh-stress app/build/audio_v2/scene_stress_mesh_frames.csv \
  --contacts-two app/build/audio_v2/contact_probe_trace.csv \
  --contacts-stress app/build/audio_v2/scene_stress_trace.csv \
  --listen-dir build/audio_v2/listening \
  --output build/audio_v2/visual
```

## Come valutare i file

1. Leggere `material/LISTEN_FIRST.md` e verificare con le proprie orecchie se i confini proposti davvero separano attacco/nucleo/coda.
2. Ascoltare separatamente `impact`, `slide`, `pressure`, `release`, `fusion`. **Solo `fusion` contiene un Bloop completo.** Il file `listen_real_physics_v2.wav` mette in fila queste cinque prove, separate da 300 ms di silenzio (non aggiunge fusioni agli altri test).
3. Controllare i dati `listening/metrics.json`, in particolare picchi e headroom. Non considerare un preview WAV riuscito equivalente a una resa convincente sul telefono.
4. Testare da ultimo `synthetic_mixer_stress.wav`, che rappresenta solo un caso limite di sovrapposizione.
5. Ascoltare anche `real_12_blob_scene_stress.wav`, ottenuto esclusivamente dalla simulazione multi-blob. Confrontare `metrics.json` e `scene_stress_trace.csv`.
6. **Preferire i video sincronizzati:** `visual/lava_v2_five_physical_behaviors_synced.mp4` e `visual/lava_v2_12_real_blobs_synced.mp4`. Permettono di giudicare il suono alla vista del movimento reale senza anticipare un'APK; i primi cinque scenari sono separati da pause di 300 ms. Alla fine di ogni prova le membrane restano ferme per lasciare terminare le code audio. Il video non misura latenza né prova che il risultato funzioni in Android.

## Core PCM Java riutilizzabile (in sviluppo)

- `app/src/main/java/com/maryvellous/lavablobs/audio/BloopMaterialBank.java`: banca PCM dei **quattro** Bloop, che conserva gli originali integrali e le sole regioni di attacco/coda per i non-fusione.
- `app/src/main/java/com/maryvellous/lavablobs/audio/MaterialAudioEngine.java`: **un unico motore deterministico puro Java**, usabile sia da JVM offline sia da un futuro adapter `AudioTrack` Android, senza implementazioni DSP divergenti. Risposte continue alla tangenziale/deformazione, launch e POP parziali, fusioni intere non interrompibili, budget per le sole voci corte, gestione headroom.
- `tools/audio_v2_java/SceneRenderCli.java`: harness desktop, decodifica gli stessi PCM prodotti dal Material Bank, applica gli eventi della fisica Java reale e salva WAV. Le librerie desktop per lettura/scrittura WAV **non** sono richieste dal motore Android.
- `MaterialAudioEngineTest`: regressioni su onsets unici, silenzio statico, progressione delle code, nessun Bloop intero fuori fusione, sovrapposizione 20 fusioni senza interruzioni, massimo di voci parziali e nessun clipping in uscita.
- `build/audio_v2/java_video/`: due video sincronizzati con il DSP Java, **da confrontare con i video Python approvati** in `build/audio_v2/visual/`. Il 23 novembre 2026 è stata approvata **la direzione artistica delle prove Python**; la fedeltà percettiva del nuovo engine Java deve ancora essere valutata, quindi `main` rimane intatto.

## APK diagnostica V2 e verifiche ancora necessarie

- Ascolto/validazione *artistica* delle singole regioni e della gerarchia timbrica.
- Trasferire la risposta della materia e il mix in **un solo core DSP deterministico**, riusabile per render offline e dispositivo (non mantenere un sintetizzatore Python indipendente e un secondo motore Android divergente).
- Misurare durata, allocazioni, CPU, latenza e comportamento di più contatti; confrontare stress tecnico con casi prodotti dalla vera fisica.
- **APK diagnostica creata in CI:** variante `preview`, package `com.maryvellous.lavablobs.audiov21`, nome «Lava Blob - Audio V2.1». `AndroidBloopDecoder` carica e segmenta i quattro MP3 originali fuori dal thread UI; `V2GameSound` collega ContactProbe/Tracker al core Java via coda limitata e `AudioTrack`. Banner in partita per `CARICAMENTO`, `PRONTO`, `ERRORE` e `MUTO`. La build non sostituisce né condivide i dati dell'app principale.
- **Playtest obbligatorio sul telefono:** aspettare `AUDIO V2 · PRONTO`, verificare urti e sfregamenti, fusioni ripetute/cascate, lanci a raffica, POP in modalità infinita, pause/ripresa, mute, prestazioni e latenza percepita. Se il banner mostra `ERRORE`, non giudicare il timbro: raccogliere il log `LavaV2Audio`. Una release V2 richiede ulteriori verifiche di fluidità e compatibilità hardware.

**Nessun merge su `main` o nuova release** prima dei gate decisionali della specifica.
