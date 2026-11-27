# Laboratorio audio — strumenti offline
La [specifica canonica](../../docs/AUDIO_ENGINE_V2_MATERIAL_BLOOP.md) governa l'app; la V2.3 è approvata e attiva su main 1.1.0. Il laboratorio non è un secondo DSP di produzione.

- material_bank.py: segmenta quattro Bloop originali in PCM in memoria, attacco/coda, analisi RMS; non distribuire campioni.
- render_auditions.py: prototipo Python storico per confronti artistici; non riprodurre le sue scelte come requisiti runtime.
- render_sync_video.py: video sincronizzati con trace di membrane vere; non misura latenza Android.
- ../audio_v2_java/SceneRenderCli.java: renderer JVM dello stesso core MaterialAudioEngine; oggi le trace non includono pareti/input e manca lo swoosh opzionale, quindi non è ancora replay completo della V2.3. Il [piano fase 2](../../docs/AUDIO_AGGREGATION_PLAN.md) richiede di colmare il divario.
- Workflow audio-v2-offline.yml sul branch storico esegue analisi e render; main lava-blobs.yml verifica test/build. Non considerare una vecchia audizione una nuova release o un collaudo hardware.

## Rigenerazione locale
JDK 17, SDK 35, Gradle 8.9; Python 3.11 con dipendenze fissate nel workflow.

```bash
gradle --no-daemon :app:testDebugUnitTest
python -m unittest discover -s tools/audio_v2 -p 'test_*.py' -v
python tools/audio_v2/material_bank.py --sources app/src/main/res/raw --output build/audio_v2/material
python tools/audio_v2/render_auditions.py --sources app/src/main/res/raw --trace app/build/audio_v2/contact_probe_trace.csv --output build/audio_v2/listening
```

Per CLI Java e video vedere i comandi nel workflow. Tenere PCM/WAV e video derivati negli artefatti CI privati, non versionarli. Per future prove usare stessa trace e baseline V2.3, metriche e ascolto A/B; niente giudizi percettivi solo da picchi.
