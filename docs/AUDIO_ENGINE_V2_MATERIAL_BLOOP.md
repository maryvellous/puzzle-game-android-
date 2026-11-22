# Lava Blobs — Motore acustico V2: la materia diventa suono

**Decisione progettuale del 22 novembre 2026 · Specifica artistica e architetturale vigente · Non ancora implementata.**

> **«Un sistema in cui il comportamento della materia diventa suono.»**

Questa frase è il **faro del progetto**. Ogni comportamento sonoro deve avere una causa leggibile nelle membrane e appartenere alla stessa materia. Il suono non è un effetto appiccicato a un evento: è la risposta del materiale al comportamento fisico.

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

**Stato:** decisione architetturale/artística approvata, implementazione ancora da avviare. Il documento è la **specifica vigente**; [`PROCEDURAL_SOUND_DESIGN.md`](PROCEDURAL_SOUND_DESIGN.md) resta una versione precedente, utile come storia tecnica, ma le sue indicazioni di ricostruire proceduralmente anche la fusione sono **superate**.
