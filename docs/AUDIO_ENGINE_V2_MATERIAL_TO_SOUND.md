# Lava Blobs — Motore audio V2: la materia diventa suono

**Specifica ufficiale · 22 novembre 2026 · stato: decisione progettuale, NON implementata.**

> **Faro del progetto: «Un sistema in cui il comportamento della materia diventa suono».**
>
> **La fusione è l'evento principe del gioco.** Le quattro registrazioni originali **Bloop 1–4 COMPLETE** sono il suono della fusione e definiscono l'identità acustica della materia. Tutti gli altri suoni devono essere derivati da quelle stesse registrazioni, in particolare **dall'inizio, dalla coda o dalla loro combinazione SENZA la parte centrale**. Non progettiamo quattro effetti indipendenti: progettiamo un unico materiale che reagisce a stimoli diversi.

Questa V2 **sostituisce come specifica guida** il documento storico [PROCEDURAL_SOUND_DESIGN.md](PROCEDURAL_SOUND_DESIGN.md). Le prove C1–C4.1 e le tre APK sperimentali sono **archivio di ricerca e feedback**, non sorgenti canoniche per il nuovo suono. La release Android 1.0.11 non si modifica con questa decisione. Nessuna nuova APK richiesta.

## 1. Un solo materiale, un gesto completo

| Gesto fisico | Frammento / risposta della stessa materia | Regola |
|---|---|---|
| **Fusione** | **Bloop completo originale**, uno dei quattro | È il gesto massimo, soddisfacente e dominante. Il corpo centrale non si taglia né si risintetizza |
| **Urto** | **Attacco** del Bloop; eventualmente attacco + breve coda, **senza corpo centrale** | Breve, morbido, intensità da energia di collisione. Microassestamento quasi silenzioso |
| **Sfregamento** | Texture continua generata principalmente dalle **code** dei Bloop | Compare soltanto quando le membrane sono in reale contatto e scorrono; segue velocità, carico e dimensione |
| **Compressione** | Modifica di risonanza, smorzamento o texture già attiva; eventuale debole coda | Non necessariamente un effetto autonomo; deformazione statica può essere silenziosa; vietato il tono «laser» |
| **Distacco** | Decadimento della **coda** già eccitata | Niente POP automatico, non avvia un Bloop intero |
| **Lancio** | Feedback molto discreto, con propria gestione anti-accumulo | Non deve sovrastare le fusioni; nella prima iterazione può restare separato come feedback di input |
| **POP/cancellazione** | Attacco e/o coda rielaborati | Distinto, ma sempre subordinato alla fusione |

Esempio puramente illustrativo: un impatto può utilizzare pochi millisecondi dell'attacco seguiti da una parte della coda, uniti con inviluppo/crossfade. **Non usare segmenti fissi inventati**: i confini di attacco, corpo e coda devono essere misurati e approvati all'ascolto *per ciascuno* dei quattro Bloop. Per lo sfregamento la coda può fornire grani sovrapposti senza che si senta una ripetizione.

La fusione, al contrario, **riproduce il Bloop completo**, inclusa la parte centrale caratteristica. Il playback rate potrà adattarsi moderatamente alla massa come già sperimentato, ma non deve sacrificare l'identità dell'originale.

## 2. Material Bank: un'unica sorgente verificabile

**Originali:** bloop_1.mp3, bloop_2.mp3, bloop_3.mp3 e bloop_4.mp3 nella cartella **app/src/main/res/raw/**. Non sostituire né alterare i file originali.

**Preparazione offline, non nella callback Android:**

1. Decodificare i quattro Bloop in PCM; conservare hash e provenienza, misurare durata sonora reale, inviluppo, picchi, attacco, corpo e coda.
2. Costruire e revisionare a orecchio un manifest versionato **material_bank.json** con i confini per singola registrazione, marcati **attack / body / tail**. L'analisi automatica aiuta ma non decide da sola.
3. **Merge bank:** quattro Bloop originali **integrali**, senza ricostruzioni o estrazioni.
4. **Non-merge bank:** varianti derivate da **attack**, **tail** o **attack + tail**; escludere **body**. Applicare selezione a zero-crossing, finestre morbide e crossfade; non creare clic nei giunti.
5. **Friction material:** grani delle code riorganizzati in una texture continua e non ciclicamente riconoscibile; niente risonanze sintetiche estranee alla famiglia timbrica.
6. Esportare una piccola libreria interna di frammenti approvati e **un report con provenienza esatta di ciascun frammento**. Non distribuire il materiale come sound pack autonomo; mantenere attribuzione e licenza floraphonic/Pixabay già documentate.

**Test obbligatorio:** un evento diverso dalla fusione non deve poter richiamare per errore un Bloop completo o il segmento centrale della fusione. Gli esperimenti C4 hanno già mostrato quanto questo falsi l'ascolto.

## 3. La fisica guida l'eccitazione

La fisica di **BlobWorld** non va cambiata per far suonare meglio il gioco. Un **Contact Probe passivo** legge i contatti particella–segmento già risolti nelle membrane a 24 nodi e raggruppa i cinque passaggi del solver in **un solo aggiornamento per coppia e passo fisico**.

Parametri utili: ID stabili dei due blob, timestamp, punto e area approssimata di contatto, velocità relativa locale **normale** (urto), velocità relativa locale **tangenziale** (sfregamento), deformazione, andamento della pressione come proxy, raggi/massa acustica, stato del contatto.

**Non usare la sola lista Adhesion per decidere se due blob sfregano.** Le prove Android hanno dimostrato che una membrana può scivolare contro un'altra senza produrre il legame adesivo richiesto dal vecchio sistema.

Un **Contact Tracker** con memoria segue: **separazione → primo contatto → contatto sostenuto → distacco**. Un piccolo movimento durante un contatto già esistente non genera un nuovo urto. Le fusioni sono eventi topologici veri e vengono registrate prima di eliminare i blob genitori.

La stessa interazione produce **una risposta acustica continua**: attacco iniziale se l'energia è sufficiente, evoluzione viscosa se continua a scorrere, cambiamento timbrico quando si deforma, coda naturale quando si separa. Non avviare un effetto indipendente per ciascuno di questi stati.

**Dimensione:** piccoli più acuti e rapidi, grandi più gravi e lenti; continuità tra le coppie. L'intensità segue prima di tutto l'energia, non la grandezza. La Audio Size Test resta un *riferimento percettivo positivo*, non un vincolo sulle implementazioni.

## 4. Fusione protagonista, mixer coerente

- La **fusione completa** resta sempre chiaramente riconoscibile. Non troncarne brutalmente la parte centrale per far spazio ad altri suoni.
- Più contatti in parallelo non significano N texture che si sommano linearmente: scegliere poche interazioni rilevanti o una **texture collettiva di sfregamento**.
- Priorità acustica: fusione > impatto significativo > movimento viscoso > piccoli assestamenti.
- Non confondere gestione delle voci con tagli indiscriminati: usare smoothing, crossfade, gestione del guadagno e headroom prima del limiter finale.
- Lancio a raffica: il suono del drop-off deve essere più basso e non accumularsi. Il precedente valore di volume 0,16 è una **referenza da ricalibrare**, non una scelta definitiva.
- Una singola uscita/mixer per la scena, invece di due SoundPool indipendenti. Un limiter protegge dai picchi ma **non** corregge da solo clic, discontinuità o campioni distorti.
- Il crepitio delle tre APK **non è diagnosticato definitivamente**. Misurare ampiezza PCM, numero di voci attive, tagli, latenza e underrun durante lanci ripetuti e fusioni a catena.

La sintesi va separata dalla fisica e dal rendering: dati numerici timestampati → bridge bounded → motore del materiale → mixer → unico flusso PCM. Precaricare i suoni, non fare decodifica MP3, log frequente, I/O, lock o allocazioni nella callback audio. AudioTrack o C++/Oboe sono opzioni tecniche da scegliere con benchmark, non il fine del progetto.

## 5. Piano di implementazione e prove

| Fase | Cosa costruire | Criterio di avanzamento |
|---|---|---|
| **A — Contact Probe** | Telemetria e tracker dei veri contatti, con scenari ripetibili | Rileva scorrimento senza adesione; distingue nuovo urto da assestamento |
| **B — Analisi dei Bloop** | Material bank di quattro Bloop completi e segmenti attack/body/tail | Il suono completo è solo delle fusioni; i derivati risultano chiaramente della stessa famiglia |
| **C — Sintetizzatore offline** | Attacco + coda, texture continua, modulazione per massa e deformazione, render WAV dagli stessi dati di contatto | I risultati non suonano artificiali; la parte centrale non compare nei non-merge |
| **D — Mixer** | Gestione collettiva, headroom, inviluppi, lancio a raffica e stress test | Niente clipping PCM né clic introdotti dalle transizioni |
| **E — Android debug** | Solo dopo i test precedenti, APK con pannello diagnostico e possibilità di isolare le categorie sonore | Ascolto reale e prestazioni soddisfacenti |
| **F — Rilascio** | Integrazione soltanto dopo approvazione artistica | Fusione sempre il gesto principe, resto derivato e subordinato |

Nelle prove offline **non aggiungere implicitamente una fusione al termine** di WAV destinati a far ascoltare urto/compressione/sfregamento. Il Bloop completo deve avere un file di confronto separato e chiaramente etichettato.

## 6. Stato dei precedenti esperimenti

L'utente intende disinstallare le **tre APK sperimentali**; nessuna disinstallazione viene eseguita dal repository. I tre pacchetti sono separati dalla release normale:

- **Lava Blob - Audio Test** — com.maryvellous.lavablobs.audiotest: prima integrazione promettente.
- **Lava Blob - Audio Size Test** — com.maryvellous.lavablobs.audiosizetest: pitch correlato alle dimensioni positivo, ma il mix continua a friggere e lo sfregamento non emerge.
- **Lava Blob - Audio Mix Test** — com.maryvellous.lavablobs.audiomixtest: peggioramento, impatti troppo ridotti e crepitio irrisolto.

**Non disinstallare automaticamente l'app normale Lava Blob 1.0.11.** Non fondere in produzione il codice sperimentale delle PR Android. Gli esperimenti C1–C4.1 restano leggibili per capire cosa ha funzionato e cosa no, ma **C4 non diventa la sorgente del materiale V2**.

## 7. Verifica di coerenza progettuale

Prima di approvare qualsiasi cambiamento domandarsi:

1. **Deriva davvero dai Bloop originali?**
2. **Il Bloop completo viene riservato alle fusioni reali?**
3. **Il suono emerge dai movimenti reali delle membrane, senza effetti casuali?**
4. **L'insieme resta coerente e non distorto quando il gioco diventa caotico?**

Se una proposta fallisce queste condizioni, non è il motore che vogliamo costruire.

**Faro finale: «Un sistema in cui il comportamento della materia diventa suono».**
