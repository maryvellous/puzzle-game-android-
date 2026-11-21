# Lava Blobs — APK Android sperimentale con audio fisico in partita

**Stato:** collaudo installabile *separato* da Lava Blob 1.0.11. Non è una release destinata alla pubblicazione né un'implementazione finale del sintetizzatore modale/granulare.

## Come si installa

La compilazione dedicata `audioTest` crea l'APK firmata con la chiave di debug di GitHub Actions, con:
- **Nome app:** `Lava Blob - Audio Test`.
- **ID applicazione:** `com.maryvellous.lavablobs.audiotest`.
- **Nome versione:** `1.0.11-audio-test`.
- **Salvataggi e preferenze isolati** dal gioco originale perché l'ID è diverso.
- Una scritta `AUDIO TEST - NON DEFINITIVO` durante la partita.

Nella pagina **GitHub Actions** di `Installable Lava Blob AUDIO TEST APK (experimental)`, aprire la run della PR, scorrere a **Artifacts** e scaricare `LavaBlob-AudioTest-installable-APK`. Estrarre lo ZIP e installare **`LavaBlob-AudioTest.apk`** sul dispositivo Android. Se Android blocca l'installazione, autorizzare *solo per questa operazione* l'installazione da quel gestore file/browser, poi disabilitare l'autorizzazione se non serve.

La firma debug può cambiare fra build GitHub Actions, perciò un'eventuale nuova APK di prova potrebbe richiedere la **disinstallazione della precedente Audio Test**, senza toccare la 1.0.11.

## Che cosa contiene davvero

Il materiale acustico proviene dagli esperimenti C4.1 ricavati dai quattro Bloop originali, attraverso i WAV offline e la simulazione fisica Java. Lo script [`prepare_android_audio_test.py`](../tools/audio_lab/prepare_android_audio_test.py) prepara **tre piccoli file WAV** solo durante la compilazione del build type `audioTest`; i file non vengono aggiunti alle risorse di produzione.

Il motore [`ExperimentalAudio.java`](../app/src/main/java/com/maryvellous/lavablobs/ExperimentalAudio.java) usa `SoundPool` **per riprodurre materiale generato offline**, mentre [`AudioTestModel.java`](../app/src/main/java/com/maryvellous/lavablobs/AudioTestModel.java) legge la fisica del gioco:

| Evento | Comportamento sperimentale in partita |
|---|---|
| Fusione | **Quattro Bloop originali invariati**, riprodotti soltanto quando `BlobWorld` registra una fusione |
| Impatto | **C4.1 urto A +9%, livello di confronto 40%**: eccitazione breve quando `BlobWorld` registra una collisione, modulazione di intensità con la velocità e una lieve variazione con la dimensione |
| Sfregamento | **C4 friction adhesive**: breve texture ciclica derivata dalla versione giudicata buona, volume variabile in funzione della velocità tangenziale misurata sulle adesioni reali delle membrane |
| Compressione | **C4 compression B**: texture quasi impercettibile, controllata dalla **variazione** di area/raggio della membrana in contatto; una forma compressa immobile non dovrebbe produrre una nota continua |
| Lancio e POP | Suoni **classici** della 1.0.11 invariati in questa prima prova |

Il motore **non modifica `BlobWorld`**: osserva i contatti adesivi già calcolati dal gioco una sola volta dopo i passaggi del solver, legge le velocità dei nodi e osserva la deformazione geometrica. Evita quindi cinque suoni per cinque passaggi di collisione. Un incontro senza adesione registrata potrebbe non generare sfregamento o compressione, anche se visivamente le superfici si toccano: **limite noto** di questa prima versione.

La preferenza **SUONI/MUTO** della partita viene rispettata: l'audio continuo deve interrompersi quando i suoni vengono disattivati, il gioco va in pausa o si torna alla home. Il gioco normale continua a usare i suoni della 1.0.11.

## Limite tecnico importante

**Questa non è ancora la sintesi audio procedurale completa con AudioTrack/Oboe.** È un *motore ibrido adattivo*: usa piccoli buffer ottenuti dalla sintesi/granularità offline e controlla in tempo reale avvio, volume, velocità e durata in funzione della fisica. Serve per capire durante una partita reale se gli eventi vengono percepiti correttamente prima di investire nel motore DSP finale.

La fusione non è stata risintetizzata nel telefono: resta il Bloop originale, scelta deliberata per conservare il riferimento riuscito. Il leggero disturbo delle versioni sperimentali non è ancora dichiarato risolto.

## Cosa ascoltare durante il test

Gioca normalmente e presta particolare attenzione a tre situazioni:
1. **Collisione:** si sente un impatto delicato, ma riconoscibile? È collegato visivamente alla collisione, senza Bloop di fusione casuali?
2. **Sfregamento:** quando due blob di tier diverso scorrono l'uno sull'altro, il suono continua e varia con il moto, oppure sembra un campione isolato?
3. **Compressione:** quando rimangono pressati fra loro, compare ancora una nota artificiale, un rumore ripetitivo, o rimangono perlopiù silenziosi?

Per diagnosticare problemi di prestazioni, il build tipo debug stampa statistiche `LavaPerf` e log `LavaAudioTest` (massimo una traccia diagnostica ogni circa 600 campioni di fisica). **La CI valida compilazione, pacchetto, risorse e controlli fisici; non può sostituire il test reale sul telefono.**

## Riproduzione

Il workflow [.github/workflows/lava-blob-audio-test.apk.yml](../.github/workflows/lava-blob-audio-test.apk.yml) esegue: test Java, simulazione due blob, generazione materiale C1 → C4.1, conversione in risorse `src/audioTest/res/raw`, compilazione `:app:assembleAudioTest`, controllo package, etichetta, presenza risorse, integrità ZIP e upload APK.

**Non avviare la pubblicazione della release di produzione `lava-blob-v1.0.11`.** Questa APK è un progetto prova separato e non richiede modifiche all'installazione principale.
