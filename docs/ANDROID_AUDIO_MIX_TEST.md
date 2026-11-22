# Lava Blobs — Audio Mix Test: meno sovrapposizioni, urti selettivi, sfregamento udibile

**22 novembre 2026. Solo APK sperimentale; release 1.0.11 intatta.**

## Feedback del playtest Audio Size Test

Il primo aggiornamento del pitch in base al volume dei blob **funziona bene**. La nuova fase affronta quattro difetti osservati durante il gioco:

1. Quando molti suoni si sovrappongono compare un leggero disturbo/fruscio («si frigge»).
2. Anche una microcollisione fra due blob già vicini produce un urto udibile.
3. Lo sfregamento non si sente, nonostante il timbro *adhesive* fosse apprezzato nei WAV isolati.
4. Occorre ancora più varietà, senza perdere la differenza di pitch fra blob piccoli e grandi.

Questi sono **problemi di mix e di controlli fisici**; non è stato dimostrato che la sola sovrapposizione causi tutto il disturbo residuo. Non modificare fisica, geometria, Bloop delle fusioni o curva di dimensione già approvata.

## APK nuova, installabile in parallelo

- **App label:** `Lava Blob - Audio Mix Test`.
- **Application ID:** `com.maryvellous.lavablobs.audiomixtest`, diverso da `lavablobs`, `.audiotest` e `.audiosizetest`.
- **Build type:** `audioMixTest`.
- **Sorgenti:** stessi materiali C4.1 per impatto A, friction adhesive e compression B; aggiunta una **seconda variante d'impatto** tratta dalla prova C4.1, senza modificarne la sorgente nel laboratorio.
- **Pitch per taglia:** formula e interpolazione dell'Audio Size Test mantenute; microvariazioni di intonazione solo attorno al rate fisico dell'impatto.
- **Watermark in-game:** `AUDIO MIX TEST - NON DEFINITIVO`.

## Politica di mix sperimentale

**Gerarchia:** fusione originale → impatto significativo → sfregamento continuo → compressione secondaria.

| Situazione | Regola Audio Mix Test | Motivo |
|---|---|---|
| Molte fusioni in cascata | Massimo un Bloop alla volta; pausa di circa **145 ms** fra fusioni udibili; un Bloop nuovo sostituisce il precedente | Evitare la somma incontrollata di fusioni lunghe |
| Fusione | Interrompe impatto e texture continue; queste non riprendono per **230 ms** | Dare priorità al Bloop riconoscibile |
| Urto | Suona solo con velocità relativa normale **≥118 unità/s**, da evento reale del solver | Non enfatizzare le correzioni minime fra membrane già vicine |
| Urto significativo | Massimo uno ogni **255 ms**; interrompe le texture continue e riserva **160 ms** al transiente | Evitare accumuli e perdita del carattere elastico |
| Sfregamento | Curva nuova con soglia di **8 unità/s** e crescita morbida più rapida; volume dinamico con massimo moderato | Far sentire l'adesione in movimento anche quando non è fortissima |
| Compressione e sfregamento | **Mai due loop continui insieme**: se c'è scorrimento apprezzabile, privilegiare lo sfregamento; altrimenti compressione B | Evitare texture sovrapposte e friggere |
| Lancio/POP vicino alle fusioni | Gli effetti secondari vengono temporaneamente soppressi | Limitare altre sovrapposizioni accidentali |

Il nuovo `SoundPool` sperimentale ha limite di **due stream**. Il `SoundPool` delle fusioni ne può usare fino a tre, con una politica che non mantiene più fusioni concorrenti in questa build. Questo **non costituisce ancora un limiter PCM master**: nel mix Android restano suoni di sistema o altri effetti, e la qualità definitiva va valutata con una registrazione della partita.

**Nuova varietà:** gli impatti alternano due timbri già derivati dal materiale C4.1; qualche impatto è variato solo di circa ±2,5% nel rate. La dimensione reale dei blob continua a determinare la parte dominante della tonalità e della durata. Sfregamento e compressione rimangono legati in tempo reale al movimento delle membrane e alla loro dimensione.

## Cosa verificare sul telefono

1. Con tanti blob: l'audio diventa meno disturbato e le fusioni restano riconoscibili?
2. Mentre due blob si toccano quasi da fermi: spariscono i piccoli «toc» inutili?
3. Strofinando due blob diversi: lo sfregamento adhesive è finalmente udibile **senza sembrare una nota continua o un Bloop extra**?
4. Impatti di blob piccoli e grandi: rimangono diversi, e le due varianti timbriche introducono varietà naturale?
5. Durante le fusioni multiple: il limite di una fusione per volta è piacevole, oppure taglia troppo bruscamente il Bloop precedente?

## File e verifica

- `app/src/main/java/com/maryvellous/lavablobs/ExperimentalAudio.java`: voice budget e gestione esclusiva dei loop.
- `app/src/main/java/com/maryvellous/lavablobs/GameAudio.java`: priorità fusione ed esclusione dei Bloop sovrapposti.
- `app/src/main/java/com/maryvellous/lavablobs/AudioTestModel.java`: curve di sensibilità, solo mappatura audio.
- `tools/audio_lab/prepare_android_audio_test.py`: due varianti d'impatto e materiale continuo C4.1.
- `.github/workflows/lava-blob-audio-test.apk.yml`: test JVM, generazione WAV, `:app:assembleAudioMixTest`, controllo package, risorse e ZIP, APK GitHub Actions.

Nessuna modifica alla fisica di `BlobWorld` o alla release 1.0.11. La nuova build è per playtest, non un motore procedurale PCM definitivo. La PR della prima Audio Size Test rimane separata da `main`: non pubblicare release finché non abbiamo ascoltato il risultato.
