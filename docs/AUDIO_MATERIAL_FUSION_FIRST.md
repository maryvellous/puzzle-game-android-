# Lava Blobs — La materia diventa suono

**Decisione di sound design — 22 novembre 2026.** Documento di indirizzo del nuovo motore. Fa fede sulle precedenti ipotesi C1–C4.1 e sulle parti superate di [PROCEDURAL_SOUND_DESIGN.md](PROCEDURAL_SOUND_DESIGN.md).

> **«Un sistema in cui il comportamento della materia diventa suono.»**

## Principio progettuale

Il suono non è un elenco di effetti assegnati a eventi di gioco. Il motore deve osservare le membrane, riconoscere che cosa stanno facendo, ed esprimere con **un'unica identità timbrica** urto, contatto, sfregamento, deformazione e fusione.

**La fusione è l'evento principe.** Non va sostituita da una ricostruzione matematica dei suoi suoni: quando due blob si fondono, si deve sentire **uno dei quattro Bloop originali di floraphonic, completo**, con i suoi gesti e la sua coda naturali, senza tagliare il corpo centrale. La riproduzione può mantenere l'adattamento di pitch per dimensione già giudicato positivo, senza cambiare arbitrariamente l'identità del campione. Il Bloop completo resta la voce principale della scena.

**Tutti gli altri comportamenti devono derivare dalla stessa famiglia dei quattro Bloop**, ma non sono Bloop interi suonati più piano: sarebbero falsi segnali di fusione. Il motore può ricavare attacchi, code, microtrame, risonanze e residui dai Bloop, riorganizzandoli in funzione della fisica. Questa è la nuova base, **non il suono C4/C4.1**. Le prove C4 e le APK restano archivio di feedback, non materiali prescritti per l'implementazione finale.

### Una grammatica del Bloop, non quattro tagli arbitrari

Il modello artistico iniziale è un gesto sonoro con tre regioni da **individuare tramite analisi e ascolto**, non dividendo meccanicamente in tre parti di uguale durata:

- **Attacco (A):** contatto/eccitazione iniziale, morbido e corto.
- **Corpo (B):** sviluppo/trasformazione timbrica centrale, distintivo della fusione.
- **Coda (C):** rilascio e residuo del materiale.

**Fusione = A + B + C, nella registrazione originale completa**. Non eseguire concatenazioni di A/B/C per la fusione, perché potrebbero introdurre giunzioni e cambiare il suono positivo che già abbiamo.

**Altre interazioni = trasformazioni parziali e fisicamente modulate di A e/o C**. Per esempio, *attacco + coda senza il corpo centrale* è una proposta concreta per un urto: non deve però diventare una ricetta obbligatoria o riprodurre una mini-fusione. Il comportamento decide se e come usare i frammenti, non viceversa.

| Fenomeno osservato | Ipotesi di trattamento acustico da provare | Cosa evitare |
|---|---|---|
| **Fusione** | Bloop originale completo, protagonista, modulazione delicata per taglia | Ricostruzione sintetica inferiore all'originale; Bloop tagliato; interruzione della sua coda senza necessità |
| **Urto nuovo e significativo** | Attacco A e possibile coda C, inviluppo breve, intensità da energia reale; nessun corpo centrale B | Volume minimo fisso, microcollisioni udibili, falso segnale di fusione |
| **Sfregamento continuo** | Trama derivata soprattutto da code/residui C, tessitura variabile con scorrimento tangenziale locale e carico di contatto | Loop udibile come nota, evento a ogni frame, dipendenza esclusiva dai legami adesivi |
| **Compressione / deformazione** | Modifica smorzamento, inviluppo o risonanza di un suono già attivo, oppure resta silenziosa; eventuali dettagli da A/C solo se dimostrano utilità | Effetto «laser», voce autonoma costante per una deformazione statica |
| **Distacco** | Rilascio dolce della voce di contatto, eventualmente con residuo di coda C | Pop automatico ogni volta che due membrane si separano |
| **Lancio** | Feedback brevissimo, di livello basso, con budget proprio e senza accumulo a raffica; famiglia timbrica coerente ove possibile | Swoosh dominante che copre la scena o crepita con i lanci rapidi |
| **POP/cancellazione** | Gesto breve della stessa materia, ben distinguibile dalla fusione | Riproduzione di un Bloop intero che comunica fusione inesistente |

Questa mappa rappresenta **ipotesi da ascoltare**. L'unica equivalenza definitivamente approvata ora è **fusione → Bloop originale completo**. Non dobbiamo inventare un suono autonomo per ogni comportamento se la materia può restare silenziosa.

## Dati fisici e modello di interazione

Il cuore del motore rimane l'osservazione **passiva** del solver esistente, non una riscrittura delle 24 particelle per blob.

1. **Contact Probe:** raccoglie per coppia identità stabili, contatto reale fra segmenti, velocità locale normale/tangenziale, zona di contatto, schiacciamento e raggio dei due blob; aggrega i cinque passaggi del solver per evitare eventi duplicati.
2. **Contact Tracker:** conserva nel tempo l'identità di ciascun contatto e le fasi avvicinamento / primo contatto / permanenza / rilascio. Un piccolo assestamento di due membrane già aderenti **non** è un nuovo impatto.
3. **Material Interpreter:** usa i parametri continui per descrivere l'eccitazione e la risposta della stessa materia; l'adesione influenza la viscosità, ma **non è la condizione obbligatoria per riconoscere lo sfregamento**.
4. **Bloop Material Bank:** contiene gli **originali completi** per le fusioni e, separatamente, frammenti e descrittori analizzati offline per altri fenomeni. Archivia provenienza, durate e procedura di estrazione; niente porzioni arbitrarie spacciate per misure fisiche.
5. **Acoustic Renderer / Mixer:** pochi generatori controllati dalla fisica, un'uscita PCM comune, smoothing e controllo dei livelli; la fusione ha priorità percepita, gli altri fenomeni non la devono sovrastare.

La massa percepita segue la dimensione effettiva di entrambi i blob; **più grandi → generalmente più gravi e lenti**, non automaticamente più forti. L'ampiezza segue soprattutto l'energia del gesto. Le variazioni sono progressive, non salti di pitch.

### Sovrapposizioni e silenzio

- Evitare **sommatoria lineare di una voce per ogni particella o coppia**. In una scena affollata più sfregamenti possono contribuire a una sola texture collettiva.
- **Mai risolvere il crepitio soltanto con tagli bruschi o soglie alte**: la Audio Mix Test ha peggiorato il risultato. Distinguere clipping/somma di segnali, interruzioni ai confini dei loop, underrun e difetti del materiale registrato.
- Le fusioni a catena devono essere riconoscibili: gestione dell'energia del mix e delle code, con attenuazione morbida degli altri suoni; non eliminare indiscriminatamente le fusioni.
- **Assenza di moto/energia rilevante → silenzio**; compressione statica non crea un tono.
- Il lancio a raffica ha un budget proprio e un volume da calibrare al ribasso: non sovrapporre identiche code di lancio.

## Tecnica di implementazione

**Prima uno strumento di misura, poi un sintetizzatore, poi il mixer, e solo dopo un'APK di prova.**

- In Java, integrare una telemetria fisico-acustica e una macchina a stati per i contatti senza modificare la simulazione. Tracciare azioni ripetibili: lancio a raffica, urto debole/forte, strisciamento, deformazione, fusione multipla.
- In un processo **offline ripetibile**, analizzare e segmentare i quattro Bloop (attacco, corpo, coda, risonanze) e produrre coppie A/B per le interazioni non-fusione. Il riferimento di ascolto per le fusioni è sempre l'originale completo.
- Separare la **logica sonora fisica** dal backend di playback. Prima confrontare le tecniche e misurare CPU/latency; scegliere un motore con **un solo mixer** e output PCM. La via nativa C++/Oboe è una candidata solida per il DSP finale, ma non un vincolo imposto prima dei benchmark.
- Il futuro laboratorio Android dovrà avere un pannello diagnostico e la possibilità di isolare i fenomeni senza ricompilare a ogni calibrazione. Registrare attivazioni/numero di voci/livelli, oltre al suono.
- Nessuna nuova release del gioco né modifica a fisica, salvataggi, tasto SFX o musica finché le prove non sono approvate.

## Esito dei prototipi precedenti e loro ruolo

- **Audio Test:** primo funzionamento dei fenomeni guidati dalla fisica, promettente ma ancora incompleto.
- **Audio Size Test:** **migliore riferimento pratico** sul telefono, con pitch e durata che seguono la dimensione. Non è ancora la versione definitiva.
- **Audio Mix Test:** **regressione**: impatti quasi spariti, sfregamento non udibile, frittura non risolta, pur con meno voci. Non prenderne i parametri come base.
- **C1–C4.1:** esperimenti di ascolto utili per capire cosa evitare e cosa conservare nel carattere timbrico, **non la sorgente obbligata** della nuova sintesi.

Le tre APK sperimentali possono essere disinstallate dal telefono dall'utente. Gli eventuali dati locali delle singole app di prova saranno persi; la release normale `com.maryvellous.lavablobs` ha ID diverso e non va disinstallata. **Non cancellare repo, branche, artefatti storici o documentazione** come conseguenza della disinstallazione.

## Criterio d'accettazione

Per ciascun evento deve essere possibile spiegare: **quale comportamento della membrana ha alimentato il suono**, quale parte del materiale Bloop è stata utilizzata e perché il livello è coerente con l'energia della scena. I Bloop completi devono continuare a identificare senza ambiguità la fusione.

Il progetto sarà riuscito quando l'audio reagirà in modo continuo e riconoscibile alla materia, con varietà naturale e senza friggere anche nei lanci a raffica; non quando avremo semplicemente aggiunto più effetti o una nuova APK.

> **Il nostro faro: «Un sistema in cui il comportamento della materia diventa suono».**
