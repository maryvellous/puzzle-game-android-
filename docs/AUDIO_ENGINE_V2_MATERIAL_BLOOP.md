# Lava Blobs — Motore acustico V2: la materia diventa suono

**Decisione progettuale del 22 novembre 2026 · Specifica artistica e architetturale vigente · Prima integrazione gameplay Audio V2 pronta per il playtest Android, isolata da `main` e non ancora verificata sul dispositivo.**

> **«Un sistema in cui il comportamento della materia diventa suono.»**

Questa frase è il **faro del progetto**. Ogni comportamento sonoro deve avere una causa leggibile nelle membrane e appartenere alla stessa materia. Il suono non è un effetto appiccicato a un evento: è la risposta del materiale al comportamento fisico.

### Riscontro audiovisivo e passaggio a codice unico (23 novembre 2026)

L'utente ha giudicato **«perfetto»** il primo ascolto *insieme alle vere membrane simulate* (cinque gesti e scena reale a 12 blob). Questa è una validazione della **direzione artistica del prototipo Python sincronizzato**, non ancora una validazione della latenza su Android e neppure un'autorizzazione a integrare o pubblicare l'app.

Sul branch `experiment/lava-blobs-audio-v2-engine` è stato creato un core PCM deterministico **puro Java** sotto `app/src/main/java/com/maryvellous/lavablobs/audio/`, riutilizzato da `tools/audio_v2_java/SceneRenderCli.java` per anteprime audiovisive A/B. La versione Java appare convincente all'ascolto; l'utente richiede **test durante il gameplay reale**. Il core ora viene usato anche da una **APK Android diagnostica** con `AudioTrack`, senza duplicarne il DSP. `AndroidBloopDecoder` ricava il materiale PCM dai quattro MP3 originali del pacchetto e `V2GameSound` trasferisce le osservazioni del solver su coda dedicata. Questa implementazione è soltanto in `experiment/lava-blobs-audio-v2-engine` con package `com.maryvellous.lavablobs.audiov21`; non è stata integrata in `main`. La reattività e la qualità sull'hardware restano da verificare.

### Correzione obbligatoria dopo primo playtest Android (23 novembre 2026)

La prima APK V2 ha prodotto un **suono acuto statico persistente** quando i blob restavano a contatto anche da apparentemente fermi. L'utente ha bocciato esplicitamente il risultato; la precedente valutazione positiva dell'assistente sul video era ingiustificata (picchi/clipping non misurano timbro o appropriatezza). Nel filmato Android originale di circa 62 s la scena pressoché statica dei primi 4 s conserva una componente tonale intorno a 576 Hz. Il codice confermava un loop di Bloop tail mantenuto vivo da micro-scorrimenti del solver.

**Requisito V2.1 non negoziabile: CONTATTO NON EQUIVALE A SUONO.** Una coppia può restare in contatto per un tempo indefinito producendo PCM 0. Urti, lanci, POP, squish e fusioni sono gesti finiti, innescati dalla fisica; solo il Bloop integrale può suonare in una fusione. Rimuovere la texture ciclica, non limitarsi a ridurne il volume.

Implementazione isolata: il nuovo `MaterialAudioEngine` non contiene loop di contatto; usa impulsi parziali del Bloop dopo un'integrazione dell'attività (slide, variazioni di compressione, velocità bulk del blob). Il bulk è calcolato da **tutti i 24 nodi fisici** in `ContactProbe` e inviato con il `dt` del solver, indipendentemente dai buffer PCM. Deformazioni davvero brusche possono suonare anche quando il centro è vincolato. Regolazione della massa tramite pitch e gain, headroom delle sovrapposizioni, fusioni complete.

**Verifica automatica integrata (CI 45279301704, successo):** scena vera con 1303 osservazioni fisiche e cinque coppie contemporanee, lasciata ferma dopo assestamento → 0 squish, 0 impatti, PCM di uscita 0. La stessa scena agitata produce 9 squish e 8 impatti; nelle fusioni si usano ancora le registrazioni originali. È una regressione tecnica di causalità, **non** la prova di qualità percettiva o latenza su Android. La prima APK V2 è stata scartata; la seconda APK diagnostica separata resta da valutare nell'uso reale.

### Playtest V2.1 e regolazione diagnostica V2.2 (27 novembre 2026)

L'utente giudica la V2.1 quasi perfetta: non percepisce il precedente rumore statico e il movimento sembra coerente, ma gli effetti secondari sono così bassi da non consentire una valutazione certa. Su richiesta, V2.2 applica un unico guadagno fisso 3× (+9,5 dB) a tutte le voci non-fusione (impatto, squish, lancio, POP) dopo il controllo di ammissione della voce; nessun cambio a campioni, soglie fisiche, pitch, inviluppi, voce fusione o limitatore globale. Il limitatore condiviso può attenuare il mix nei picchi sovrapposti. È una regolazione diagnostica, non un mix definitivo: attendere feedback strutturato sul telefono. Nessun merge su main.

### V2.3 — distinzione vetro/gel (27 novembre 2026)

Playtest V2.2: coerenza fisica 3,5/5, timbro 4/5, bilanciamento 2/5, pulizia 4/5, sincronizzazione 4,5/5. Effetti secondari ancora troppo deboli e dinamica poco proporzionale al movimento. Su indicazione dell'utente, l'attacco V2.2 originale resta per gli impatti con le pareti reali; gli impatti blob-blob ricevono una risposta più gelatinosa (attacco smussato, coda originale e curva di pitch ascendente), con maggiore dinamica per gli impatti e gli squish. Fusioni e campioni originali intatti. Il probe aggrega i contatti per blob e lato del vaso, con isteresi e velocità bulk normale; nessun suono continuo da appoggio. Qualità percettiva da collaudare. Slider generale integrato nella preview: 0–100%, curva quadratica dopo il limiter, rampa 8 ms per i cambi non nulli, mute con ripristino dell'ultimo valore, preferenza persistente e gesture del cursore separate dal lancio. Impostazione iniziale 100% per conservare il volume della V2.2. Il controllo classico resta invariato fuori dalla preview.

**Eccezione esplicita per il gesto di rilascio (27 novembre):** l'utente richiede un lancio più simile alla versione precedente, interpretata come 1.0.11. Riutilizzare lo `swoosh_source` già incluso: segmento 47–94% invertito, fade 5/13 ms, velocità per tier come nel classico, gain .20 invece di .16 per la prova. Il gesto passa nel mixer PCM condiviso e non usa SoundPool. In caso di decodifica non riuscita resta il fallback parziale Bloop; il laboratorio offline senza sorgente opzionale mantiene tale fallback. È l'unica eccezione timbrica al materiale Bloop (feedback di input, non contatto fisico). Fusioni, pareti e collisioni fra blob invariati.

## 1. Decisione sonora fondamentale: la fusione è l'evento principe

**La fusione usa uno dei quattro Bloop originali COMPLETO, dall'inizio alla fine.** Non lo ricostruiamo da un sintetizzatore e non eliminiamo il suo nucleo centrale. Conserviamo la varietà dei quattro Bloop, con eventuale variazione moderata di pitch/durata secondo la dimensione. La fusione è il momento più riconoscibile, pieno e gratificante del gioco.

**Tutti gli altri suoni sono espressioni parziali o trasformazioni della stessa sorgente timbrica.** I Bloop non sono soltanto riferimenti estetici: costituiscono il **materiale sorgente comune**. L'idea dell'utente è di poter usare, per esempio, l'attacco e la coda di un Bloop *senza la sua parte centrale*. Non imporre il taglio rigido come ricetta universale: identificare le regioni acustiche in ciascuno dei quattro Bloop e sperimentare ricombinazioni che non creino salti, mini-fusioni o ripetizioni riconoscibili.

### Grammatica del materiale

Un Bloop intero può essere analizzato come una sequenza percettiva (i confini **non** vanno indovinati dalla durata del file MP3):

- **Attacco / eccitazione**: l'inizio dell'interazione, breve, elastico, potenzialmente utilizzabile per un vero impatto.
- **Corpo / nucleo della fusione**: il gesto pieno e riconoscibile che distingue una fusione. Per design **riservato alla fusione**.
- **Coda / decadimento**: il rilascio della risonanza e i dettagli di superficie; può alimentare attrito, deformazione, assestamento e separazione.
- **Microtrame / residui**: componenti meno riconoscibili estratte dal materiale dei Bloop, impiegabili per continuità e varietà, a basso livello.

L'attacco, il corpo e la coda sono **regioni acustiche**, non percentuali fisse: ognuno dei quattro Bloop va misurato e ascoltato individualmente. Preservare gli originali integralmente. Non scambiare lo splice diretto fra inizio e fine per una garanzia di buon sound design: servono fading, crossfade, inviluppi e controllo delle discontinuità.

### Mappatura dei comportamenti

| Fenomeno fisico | Comportamento sonoro | Materiale di partenza |
|---|---|---|
| **Fusione effettiva** | **Bloop originale intero**, presenza e priorità massima; la sequenza completa resta integra anche nelle cascate | Uno dei quattro Bloop completi |
| Primo **impatto reale** | Brevissimo attacco + risonanza ridotta; più energia di collisione = più eccitazione, mai volume minimo artificiosamente alto | Attacco, eventuale coda; **senza corpo centrale** |
| **Sfregamento** persistente | Texture delicata, granulare/viscosa, modellata su scorrimento e durata del contatto; nessun Bloop completo aggiuntivo | Code, residui, microtrame dei Bloop |
| **Compressione** | Modula risonanza, smorzamento, filtro e risposta già attiva. Se statica può non suonare | Risposta e coda della materia; **nessuna voce obbligatoria** |
| **Assestamento** senza nuova collisione | Silenzio o dissolvenza dell'energia esistente; niente falso attacco | Nessun nuovo campione |
| **Separazione / rilascio** | Esaurimento morbido della texture attiva, se percepibile | Decadimento delle code |
| **Lancio** da parte del giocatore | Feedback molto discreto, autonomo dal mondo di contatto e soggetto a limite di frequenza; esplorare materiale parziale Bloop per unità timbrica | Frammento/aria/attacco tenue dal banco Bloop, da validare |
| **POP / cancellazione** | Segnale breve, distinguibile dalla fusione e non in competizione con essa | Frammento breve derivato dai Bloop, da validare |

**La fusione non è semplicemente un suono più alto:** è l'unica azione che esprime la frase acustica completa. Gli altri comportamenti ne usano il vocabolario, non il messaggio intero.

## 2. Architettura: fisica reale → interazione persistente → materia sonora → mix

### A. Contact Probe (solo osservazione)

Inserire osservazione passiva nei contatti delle **membrane reali** già risolti da `BlobWorld`: normale, tangente, velocità locali relative, compressione/deformazione, durata e dimensioni. Non modificarne la fisica né aggiungere forze per rendere più interessante l'audio. Aggregare i dati dei 5 passaggi del solver: non emettere 5 impulsi per una stessa interazione.

La precedente Audio Size Test alimentava lo sfregamento esclusivamente tramite i legami `Adhesion`; ciò non basta, perché due membrane possono toccarsi e scorrere senza legami adesivi. Distinguere **contatto reale** e **adesione**. Quest'ultima può modificare la qualità vischiosa, non decidere se lo sfregamento esiste.

### B. Contact Tracker (memoria delle coppie)

Tenere identità stabili delle coppie e transizioni **inizio → permanenza → rilascio**. Una correzione del solver su due membrane già a contatto **non è** un nuovo impatto. Una separazione seguita da vero nuovo avvicinamento può esserlo.

Il tracker deve deduplicare eventi, mantenere uno stato temporale, usare intervalli di isteresi ragionevoli per contatti intermittenti e distinguere la fusione dall'impatto: l'evento di fusione è autorevole, emette il **Bloop intero** e non un urto/fusione secondario.

### C. Material Engine (una identità, molti modi di eccitarla)

L'analisi offline produce un `Bloop Material Bank` a partire dai **quattro MP3 originali**, conservando per ciascuno il Bloop intero e identificando regioni e parametri di attacco, corpo, coda e residui. Preparare PCM e metadati misurati, non inventare frequenze.

Il runtime usa due percorsi:
1. **Fusion Voice:** riproduce il Bloop originale completo; nessuna ricostruzione da frammenti in questa fase; volume, pitch/durata e inviluppo generale possono essere modulati senza tagliare il gesto integrale.
2. **Material Response:** ricava eccitazioni parziali da attacchi/code e trame derivate, guidate in continuo dalle grandezze fisiche. Una compressione può modulare una voce di contatto già attiva anziché generare un nuovo sample.

Preservare la formula di dimensione come riferimento artistico positivo (**piccoli più acuti/rapidi, grandi più gravi/lenti**), ma calibrare separatamente intensità ed energia: grande non significa sempre più rumoroso. I suoni non-fusione non devono mai contenere per errore il Bloop completo o il nucleo della fusione.

### D. Scene Mixer (un solo output)

Mixer unico con budget dinamico di voci, inviluppi continui, headroom, protezione dai picchi e diagnostica. Non tagliare bruscamente i Bloop per fare spazio: **l'intero suono della fusione deve potersi completare**. In una cascata, gestire sovrapposizione, volume relativo e mascheramento in modo musicalmente naturale senza sostituire arbitrariamente un Bloop a metà.

Gli altri suoni devono accompagnare la fusione, non coprirla. Non far crescere il livello totale linearmente col numero dei contatti: più membrane attive possono guidare una texture collettiva. Il **lancio a raffica** non deve accumulare decine di code. Misurare se la «frittura» deriva da clipping, campioni difettosi, discontinuità/loop o scheduling: non presumere che un limiter sia la risposta unica.

## 3. Scelta tecnologica

L'app Android rimane Java per `BlobWorld` e gameplay. Il probe fisico produce solo controlli; un **motore DSP riutilizzabile offline** genera il mix. Per una fase finale a bassa latenza valutare C++ con **Oboe** (o `AudioTrack` se il prototipo dimostra che è sufficiente). La logica acustica non deve dipendere da SoundPool né essere duplicata in Python e Android.

Una prima implementazione può usare direttamente le regioni PCM (e non la piena sintesi modale). Aggiungere filtri risonanti, eccitatori o sintesi granulare soltanto dove migliorano il timbro: la precedente compressione «laser» dimostra che più DSP non equivale a migliore suono.

**Nessun acquisto, servizio remoto o dipendenza obbligatoria dall'IA** per il funzionamento del gioco.

## 4. Piano di realizzazione e verifica

1. **Analisi sorgenti (solo offline):** decodificare i quattro Bloop, segnare per ognuno attacco, corpo e coda ascoltabili, calcolare envelope e spettrogrammi; preparare varianti *attacco solo*, *coda sola*, *attacco + coda* con crossfade; controllare esplicitamente che i non-fusione non contengano il corpo riconoscibile.
2. **Contact Probe e Tracker:** registrare tracce fisiche reali e deterministiche; verificare 2 blob che urtano, scorrono senza adesione, comprimono staticamente, si separano e si fondono. Niente nuove APK finché la misura non è credibile.
3. **Material Engine offline:** fare ascoltare la grammatica completa per fusione e risposte parziali per i diversi gesti, usando i medesimi parametri fisici.
4. **Scene Mixer offline:** stress test con lanci a raffica, decine di contatti, fusioni a catena; misurare picchi, energia, click/discontinuità, numero di voci ed eventuali glitch, senza tagliare i Bloop completi.
5. **Una sola APK diagnostica** quando la catena offline funziona. Controlli di isolamento canali e registrazione, comparazione con la Audio Size Test come riferimento storico, prestazioni sul dispositivo.
6. **Approvazione artistica sul telefono:** causalità, timbro, varietà, quiete, senso di massa, supremazia delle fusioni. Solo allora discutere la distribuzione.

Nelle prove audio ogni file deve avere etichette fedeli e non aggiungere accidentalmente un Bloop completo a un confronto di urto o sfregamento, come accaduto nella fase C4.

### Misure storiche utili (non istruzioni di sintesi)

La prima analisi offline A/B, eseguita su PCM mono derivato dai quattro Bloop, aveva prodotto le seguenti **stime preliminari**, da ricalcolare e verificare ascoltando le regioni reali prima di definire il Material Bank V2:

| Sorgente | Durata file decodificato | Regione attiva stimata | Centroide spettrale mediano | RMS sul tratto attivo |
|---|---:|---:|---:|---:|
| Bloop 1 | 627 ms | circa 190 ms | 248 Hz | 0,412 |
| Bloop 2 | 627 ms | circa 130 ms | 384 Hz | 0,432 |
| Bloop 3 | 1.045 ms | circa 230 ms | 187 Hz | 0,307 |
| Bloop 4 | 1.045 ms | circa 190 ms | 240 Hz | 0,322 |

Queste misure **non** indicano automaticamente i confini attacco/corpo/coda e il centroide **non** è il pitch. Sono numeri storici dipendenti dalla soglia RMS dell'esperimento, non parametri finali. Il diario originale delle prove e il codice sperimentale rimangono nella cronologia Git.

## 5. Decisioni sul lavoro precedente e sui tre prototipi

- **C1–C4.1**: archivio storico di prove e lezioni; **non è più il generatore di riferimento** del nuovo motore. Le texture C4.1 possono servire soltanto come confronto percettivo, non come vincolo o fonte timbrica definitiva.
- **Audio Test / Audio Size Test / Audio Mix Test**: build sperimentali, destinate alla disinstallazione dal telefono dell'utente. Non richiederne la conservazione; nessun dato della release normale deve essere toccato. Le loro PR/branch non costituiscono una nuova specifica e **non vanno fuse nel ramo principale senza una nuova decisione**.
- La **Audio Size Test** aveva un mapping dimensione→pitch promettente. La Mix Test ha ridotto troppo impatti e sfregamento senza eliminare la «frittura». Il lancio risultava troppo presente; era un effetto indipendente e sovrapponibile. Questi risultati guidano la progettazione, non obbligano a riutilizzare gli asset.
- **La release 1.0.11 rimane intatta.** Nessuna compilazione, rimozione di codice, cambiamento dei Bloop o release richiesta da questa decisione di documentazione.

## 6. Gate progettuali non negoziabili

- Solo una **fusione reale** può richiedere un **Bloop completo**.
- Non-fusione = **stesso materiale sorgente**, ma espressione parziale, con causa e inviluppo fisici.
- Le fusioni sono il **fulcro del piacere sonoro**: nessun ducking o voice-stealing che ne tronchi sistematicamente il campione.
- Il silenzio è una risposta corretta quando non c'è eccitazione materiale significativa.
- Il rilevamento del contatto non dipende esclusivamente dai legami adesivi.
- L'esecuzione di molti solver pass, contatti o lanci **non equivale** a molteplici eventi sonori.
- Fisica immutata, DSP deterministico e provabile offline, mixer comune, niente click/clipping/crepitii in condizioni di stress.
- Nessuna nuova APK finché Contact Probe, grammar Bloop e mix offline non sono valutabili.

---

**Stato:** la specifica artistica V2 è approvata. **Fondamenta tecniche in PR #26:** identità stabili, Contact Probe/Tracker e test. **Sul branch sperimentale:** Material Bank, core PCM Java riutilizzabile, test fisici/sonori offline, `AudioTrack` e una APK diagnostica separata già compilata in CI. **In attesa del playtest su telefono**, senza merge o pubblicazione. La precedente V1 e gli esperimenti A/B–C4.1 restano esclusivamente nella cronologia Git; le loro proposte di ricostruire un Bloop completo tramite sintesi sono superate.
