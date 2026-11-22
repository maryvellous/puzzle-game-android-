# Lava Blobs Audio V2 — laboratorio offline (NON specifica di prodotto)

**Riferimento progettuale unico:** [specifica Audio V2](../../docs/AUDIO_ENGINE_V2_MATERIAL_BLOOP.md).

Questo laboratorio sul branch `experiment/lava-blobs-audio-v2-engine` non modifica la release `main` 1.0.11 e **non è ancora il motore DSP Android**. Serve esclusivamente a verificare se gli stessi quattro Bloop possano produrre una grammatica coerente di risposte al comportamento della materia.

## Cosa esiste

- `material_bank.py`: decodifica gli MP3 originali a PCM **solo in memoria**, misura onset/crest/decadimento con RMS a 5 ms e propone un attacco breve, un nucleo centrale riservato e una coda. **Confini candidati, non approvati automaticamente**: `bloop_regions_review.png` e `material_bank_candidates.json` permettono di vedere le misure e confermarle all'ascolto.
- Audizioni isolate: per ognuno dei quattro Bloop, **completo**, **attacco**, **coda** e **attacco+coda senza nucleo**. Non si committano file estratti né si distribuiscono sound pack.
- `render_auditions.py`: legge `ContactProbeTraceTest` (vero solver Java), riproduce **un Bloop intero** solo su fusione; urto e scorrimento sono fatti con attacchi e code. La compressione modula solo la risposta già attiva; la quiete è ammessa. L'anteprima è **un esperimento Python da ascoltare**, non il DSP da copiare nell'app.
- `synthetic_mixer_stress.wav`: stress tecnico volutamente **sintetico, non fisica reale** con 12 fusioni intere e 80 attacchi corti; nessuna fusione viene interrotta. L'headroom del mix viene regolato globalmente: non è ancora un mix bus realtime con priorità e budget voci.
- Test con sorgenti sintetiche per segmentazione, decadimenti, campioni non validi, mapping dimensione→durata, assenza di Bloop completi nei non-fusione, mix e preservazione integrale dei Bloop sulle fusioni.

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
```

## Come valutare i file

1. Leggere `material/LISTEN_FIRST.md` e verificare con le proprie orecchie se i confini proposti davvero separano attacco/nucleo/coda.
2. Ascoltare separatamente `impact`, `slide`, `pressure`, `release`, `fusion`. **Solo `fusion` contiene un Bloop completo.** Il file `listen_real_physics_v2.wav` mette in fila queste cinque prove, separate da 300 ms di silenzio (non aggiunge fusioni agli altri test).
3. Controllare i dati `listening/metrics.json`, in particolare picchi e headroom. Non considerare un preview WAV riuscito equivalente a una resa convincente sul telefono.
4. Testare da ultimo `synthetic_mixer_stress.wav`, che rappresenta solo un caso limite di sovrapposizione.

## Blocchi prima dell'APK diagnostica

- Ascolto/validazione *artistica* delle singole regioni e della gerarchia timbrica.
- Trasferire la risposta della materia e il mix in **un solo core DSP deterministico**, riusabile per render offline e dispositivo (non mantenere un sintetizzatore Python indipendente e un secondo motore Android divergente).
- Misurare durata, allocazioni, CPU, latenza e comportamento di più contatti; confrontare stress tecnico con casi prodotti dalla vera fisica.
- Solo dopo, preparare una APK diagnostica **separata** dal gioco normale e provare l'audio dal telefono.

**Nessun merge su `main` o nuova release** prima dei gate decisionali della specifica.
