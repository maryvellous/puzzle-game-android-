# Lava Blobs — Sound design procedurale guidato dalla fisica

**Documento progettuale · Versione 1.0 · 21 novembre 2026**  
**Aggiornamento playtest 22 novembre 2026:** una prima [APK Audio Test](ANDROID_AUDIO_TEST_APK.md) con timbri C4.1 adattati alla fisica è stata installata e funziona complessivamente bene. Problema: blob grandi e piccoli suonano troppo simili. [APK Audio Size Test](ANDROID_AUDIO_SIZE_TEST.md) cambia **soltanto il playback rate** di impatto, sfregamento e compressione in funzione del volume delle membrane, con interpolazione morbida. La release 1.0.11 rimane invariata e il motore PCM procedurale finale non è ancora implementato.

**Stato:** direzione progettuale approvata; **fase A completata**, **fase B giudicata complessivamente buona all'ascolto con lieve disturbo residuo aperto**, **fase C1 (due blob) bocciata artisticamente per urti e compressione**; **fase C2 non convincente**, ma l'urto forse va nella direzione giusta; **fase C3 ascoltata: impatto abbastanza giusto ma grave, sfregamento ancora poco credibile, compressione bocciata come «laser»**; **C4 ascoltata: compression B e friction adhesive gradite, impatti inudibili; fusione inserita erroneamente nei montaggi di ascolto**, ora **C4.1 correttiva in attesa di ascolto**. Motore procedurale **non integrato nel gioco**.  
**Risultati:** [laboratorio A/B/C/D](PROCEDURAL_AUDIO_LAB_2026-11-21.md) e [prototipo fisico a due blob](PROCEDURAL_AUDIO_TWO_BLOB_PROTOTYPE.md), con [feedback negativo e revisione C2](PROCEDURAL_AUDIO_C2_FEEDBACK_REWORK.md) e [mix discreto C3](PROCEDURAL_AUDIO_C3_SUBTLE_MIX.md) e [confronti C4](PROCEDURAL_AUDIO_C4_ORGANIC_CONTACT.md) con [correzione C4.1 dei montaggi](PROCEDURAL_AUDIO_C41_PREVIEW_FIX.md).  
**Base di riferimento:** Lava Blob Android **1.0.11**, repository `maryvellous/puzzle-game-android-`.  
**Ambito:** tutti gli **effetti sonori** (fusioni, urti, deformazioni, attriti, rilascio, lancio e POP). **Musica separata**.

> **Visione:** i blob non riproducono effetti preconfezionati quando si verifica un evento: *producono* un suono coerente con la loro materia, dimensione e comportamento. I quattro Bloop originali di floraphonic sono il riferimento timbrico del materiale, perché le fusioni attuali funzionano e sembrano appartenere realmente alle bolle.

## 1. Obiettivi e vincoli

### Obiettivi percettivi
1. **Causalità:** guardando le bolle deve essere intuitivo capire *che cosa* genera il suono.
2. **Identità:** fusioni, urti e sfregamenti devono appartenere alla stessa famiglia sonora, ricavata dai quattro Bloop.
3. **Continuità:** il contatto prolungato cambia il suono nel tempo; non è una ripetizione di piccoli "colpi".
4. **Dimensione:** blob più grandi generano in generale risonanze più gravi, corpose e persistenti; non necessariamente più rumorose.
5. **Dinamica:** l'intensità dipende dall'energia dell'interazione e non solo dal raggio. Un blob grande quasi fermo può essere silenzioso.
6. **Silenzio:** nessun suono percepibile per oscillazioni irrilevanti o un semplice contatto statico; evitare affaticamento sonoro.
7. **Fusione:** la nuova fusione procedurale deve essere *almeno altrettanto convincente* dei quattro Bloop attuali prima di sostituirli.
8. **Musica:** futuro controllo ON/OFF e mix indipendenti dagli effetti sonori; il nuovo motore non sceglie né produce la colonna sonora.

### Vincoli tecnici e di prodotto
- **Non modificare la fisica** del gioco per "far suonare meglio" gli effetti. Il modello audio osserva la fisica esistente.
- Non peggiorare frame rate, input, gioco infinito, salvataggi o consumo energetico in modo significativo.
- Il progetto è **Android nativo**, Java, minSdk 26, targetSdk 35 (stato 1.0.11). Evitare servizi remoti, IA online e costi ricorrenti.
- Il tasto già esistente `SUONI/MUTO` e la preferenza `sound_effects_enabled_v1` devono controllare **tutti** gli effetti, nuovi inclusi; silenziare anche le code già in riproduzione.
- Salvare il sistema classico per confronti A/B e rollback. **Nessuna release o migrazione automatica** durante la sperimentazione.
- Tutti i parametri numerici indicati come *iniziali* sono ipotesi da misurare e regolare, **non** valori validati all'ascolto.

### Non-obiettivi iniziali
Simulazione scientificamente esatta di onde in un fluido 3D; riverbero ambientale complesso; musica procedurale; un sintetizzatore per ogni nodo della membrana; un grande motore audio general purpose; riscrittura del rendering e della fisica.

## 2. Materiale di partenza e stato attuale

### I quattro Bloop

| Asset nel repository | Autore / origine | Durata MP3 codificata | Utilizzo attuale |
|---|---|---:|---|
| `app/src/main/res/raw/bloop_1.mp3` | floraphonic, Pixabay | ca. 0,627 s | Fusione |
| `app/src/main/res/raw/bloop_2.mp3` | floraphonic, Pixabay | ca. 0,627 s | Fusione |
| `app/src/main/res/raw/bloop_3.mp3` | floraphonic, Pixabay | ca. 1,045 s | Fusione |
| `app/src/main/res/raw/bloop_4.mp3` | floraphonic, Pixabay | ca. 1,045 s | Fusione |

I quattro file sono MP3 stereo, 44,1 kHz, 256 kbps. Le durate riportate derivano dai frame MP3 e **non dimostrano** l'effettiva durata della componente sonora: possono includere ritardo/riempimento dell'encoder o silenzio finale.

**Analisi acustica ancora da eseguire:** i file devono essere decodificati in PCM per stimare andamento dei fondamentali, modi di risonanza, inviluppi, rumore, attacchi, code ed eventuali differenze fra le quattro registrazioni. Non inventare frequenze dominanti, pitch o curve RMS finché non vengono misurati.

Fonte/autore e licenza:
- [Bloop 1](https://pixabay.com/sound-effects/film-special-effects-bloop-1-184019/)
- [Bloop 2](https://pixabay.com/sound-effects/film-special-effects-bloop-2-186531/)
- [Bloop 3](https://pixabay.com/sound-effects/film-special-effects-bloop-3-186532/)
- [Bloop 4](https://pixabay.com/sound-effects/film-special-effects-bloop-4-186533/)
- [Pixabay Content License](https://pixabay.com/service/license-summary/): impiego/modifica in un'opera più ampia consentiti secondo le condizioni applicabili; **non** redistribuire i campioni o derivati come sound pack autonomo. Conservare attribuzione e provenienza nei documenti di progetto; verificare la liceità di eventuali asset grezzi esposti nel repository pubblico.

### Implementazione classica attuale (1.0.11)
- `GameAudio.java` usa `SoundPool`; sceglie i quattro Bloop per la fusione tramite shuffle bag e varia leggermente il playback rate in base al livello.
- Urto diverso-tier: un WAV originale separato, `lava_impact_soft.wav` (112 ms), volume fisso basso, soglia relativa **42**; intervallo globale **350 ms**, per coppia **700 ms**. Questi sono *workaround* dell'audio a eventi, **non** leggi della fisica da conservare nel nuovo modello.
- Lancio e POP: clip preparati da `EffectCompiler.java`.
- Il tasto `SUONI/MUTO` controlla fusioni, urti, lancio e POP; la musica non è implementata.
- `BlobWorld.java` rileva fusioni prima della risoluzione dei contatti e aggiorna membrane/contatti attraverso **5 passaggi** di vincoli per step; `LavaView.java` esegue passi fissi da **1/60 s** (massimo 4 per frame).
- `Blob.java`: **24 nodi** con posizioni e velocità; `radiusTarget`, `radius()`, `area()`, `expectedArea()`, `finish(dt)`. Le adesioni tra blob offrono dati aggiuntivi per l'effetto elastico/viscoso.

**Decisione:** i quattro Bloop sono il *materiale sorgente* della futura sintesi, non semplici sample da assegnare a quattro livelli. Il vecchio motore resta il riferimento sonoro da superare.

## 3. Metodo di sintesi selezionato

**Sintesi ibrida, informata da esempi e guidata dalla fisica**:

1. **Risonanza modale leggera:** poche componenti tonali (oscillatori/filtri risonanti con decadimento e pitch variabile) costruite da parametri estratti dai Bloop.
2. **Componente originale registrata:** microframmenti selezionati dalle registrazioni, con finestre morbide e sovrapposizione granulare, per preservare dettagli impossibili da rendere con poche sinusoidi.
3. **Componente stocastica controllata:** piccola trama di rumore filtrato ricavata dai residui dei Bloop, principalmente per sfregamento e contatto viscoso.
4. **Inviluppi ed eccitazioni:** il movimento fornisce input energetico; il materiale determina come esso risuona e si estingue.

**Perché non solo sample:** ripeterli crea attacchi uguali, durata fissa e confusione sulla causa del suono.  
**Perché non solo oscillatori:** rischiano di perdere la naturalezza timbrica che rende riusciti i Bloop.  
**Perché ibrido:** permette una gamma continua di comportamenti preservando una parte del suono registrato.

Ricerche di riferimento: [Phya — fisica, contatto e risonanza](https://link.springer.com/article/10.1155/2010/137878), [Example-Guided Physically Based Modal Sound Synthesis](https://gamma-web.iacs.umd.edu/AUDIO_MATERIAL/examplebasedsoundsynthesis.pdf), [Interactive Granular Synthesis of Haptic Contact Sounds](https://aes.org/publications/elibrary/elibrary-page/?id=11144).

### Pipeline di analisi offline dei campioni — fase A
1. Decodificare in PCM con strumenti deterministici, preservando i file originali; verificare canali, effettiva durata e caratteristiche della compressione MP3.
2. Eliminare solo silenzio/ritardi tecnici di codifica **per l'analisi**, non ritagliare in modo distruttivo gli originali.
3. Calcolare curve RMS/envelope, attacco, decadimento, crest factor e quantità di energia per segmento.
4. Eseguire STFT/spettrogramma e analisi di frequenze dominanti e loro evoluzione; stimare pitch solo dove la componente tonale lo consente. Non presumere che ogni Bloop abbia una singola fondamentale.
5. Stimare componenti risonanti (frequenza, ampiezza, damping), parte transiente e residuo rumoroso; confrontare l'errore di ricostruzione con la sorgente.
6. Identificare brevi grani di carattere timbrico (es. attacco, corpo, coda) con crossfade appropriati; sperimentare 20–80 ms **come intervallo iniziale**, non come costante definitiva.
7. Produrre `sound_material_v1.json` (o asset equivalente) con parametri **misurati**, riferimenti ai grani e versionamento dell'analisi. Non includere dati spettrali inventati.
8. Rendere una piccola raccolta di file A/B e annotare gli scostamenti percepiti: attacco troppo netto, suono troppo elettronico, coda metallica, ripetizioni udibili.

L'analisi avviene **offline**; il gioco non deve fare STFT o aprire/decodificare MP3 nella callback audio.

## 4. Modello fisico-acustico

### 4.1 Unità di osservazione

L'unità principale è una **coppia di superfici in contatto**, non la semplice distanza fra i centri dei due blob. Eventi di fusione, lancio e POP sono cambiamenti topologici o azioni esplicite, ma la loro **sonorità** viene calcolata dal modello del materiale.

Per ogni contatto aggregato lungo uno step fisico:
- ID stabili dei due blob; posizione del contatto o posizione media pesata.
- Normale locale `n` orientata da A verso B; tangente locale `t`.
- Velocità locale relativa `vA - vB` campionata **prima** della correzione di posizione del solver.
- Avvicinamento `vn = max(0, dot(vA-vB, n))` con orientamento coerente; scorrimento `vt = abs(dot(vA-vB,t))`.
- Profondità/prossimità e numero di nodi in contatto; evoluzione di tali grandezze nel tempo.
- Raggi, rapporto di dimensioni, area e deformazione di entrambe le membrane.
- Durata del contatto, transizioni `begin/update/end`, presenza di eventuali adesioni e loro estensione.

**Attenzione:** il solver è basato su proiezioni di posizione, non produce direttamente forze o impulsi fisici attendibili. L'audio utilizza *proxy* calibrati; non spacciare uno spostamento imposto dal solver per forza misurata.

### 4.2 Compressione e forma

Misura di partenza, da combinare e validare:

`Carea = clamp((expectedArea - area) / expectedArea, 0, 1)`.

Per evitare falsi positivi dovuti al rigonfiamento programmato dopo una fusione, `expectedArea()` usa il target attuale della membrana, non il raggio finale. Aggiungere una misura **locale** di schiacciamento/curvatura e la sua derivata smussata; distinguere la pressione/oscillazione interna dal contatto esterno.

Sorgenti sonore:
- `contactPressure`: cresce con la deformazione sostenuta e la prossimità; entra/esce con inviluppo dolce, non ripete lo stesso sample.
- `scrapeRate`: dipende da `vt` e dal carico di contatto, non soltanto dalla velocità globale del blob.
- `excitation`: aumenta quando la componente di avvicinamento crea una compressione rapida; ricarica la risonanza del corpo.

### 4.3 Dimensione e dinamica

Esempio **iniziale da calibrare**, non proprietà fisica misurata:

`f(r) = fRef * pow(rRef / max(r,rMin), alpha)`, con `alpha` da esplorare indicativamente fra **0,25 e 0,50**.

All'aumentare del raggio, la frequenza tende a scendere e la risonanza può durare più a lungo. Imporre limiti percettivi e scegliere `fRef` **dopo** l'analisi dei Bloop.

Per l'intensità usare una trasformazione compressa/saturata dell'eccitazione (per esempio `log1p(k*E)` normalizzato), non `volume = raggio`. Un proxy di energia può combinare velocità normale e massa percepita (`massa ~ raggio^2` come scelta artistica in 2D); nessun impulso/forza fisica "esatto" è disponibile senza cambiare il solver.

Prevedere smoothing distinto per inviluppo di attacco (breve), evoluzione del contatto (più lenta), cambiamento di pitch e rilascio. I coefficienti si determinano ascoltando.

### 4.4 Non confondere i cinque passaggi del solver con cinque urti

`BlobWorld.step()` chiama `projectContacts()` per ogni coppia fino a cinque volte. Un `AcousticProbe` deve creare **un solo `ContactUpdate` aggregato per coppia per step fisico**, accumulando massimo/medie pesate e profondità, senza accodare un evento per ogni proiezione. In presenza di `MAX_STEPS_PER_FRAME=4`, inviare le osservazioni in ordine temporale o con timestamp di simulazione per evitare burst audio fittizi in un unico frame grafico.

Preferire identificatori di blob espliciti e stabili per la durata della loro vita; non usare `identityHashCode` come chiave duratura del nuovo sistema.

### 4.5 Fusione: transizione di materiale e massa

**Primo obiettivo qualitativo del progetto.** Oggi le fusioni fra blob dello stesso tier vengono rilevate **prima** della risoluzione della membrana e rimpiazzano i due blob con uno nuovo. Il nuovo `MergeAcousticEvent` deve essere catturato prima della rimozione e contenere:
- ID, raggio e tier dei due genitori.
- Raggio/tier previsto del blob risultante.
- Velocità relativa, deformazione, posizione e caratteristiche di contatto disponibili prima della fusione.
- Energia e stato delle risonanze correnti dei genitori, se attive.

Sequenza sonora proposta:
1. Le membrane si avvicinano e possono già avere una risonanza eccitata.
2. Alla fusione, le eccitazioni dei genitori alimentano una **voce di transizione** del nuovo corpo.
3. Il pitch/corpo evolve verso la risonanza del nuovo raggio (generalmente più grave e ampia).
4. Una breve componente transiente/granulare riconoscibile, derivata dai Bloop, conserva il carattere appagante della fusione.
5. La voce si spegne con il decadimento del nuovo blob; **non** si sovrappone in parallelo un Bloop completo.

Non si deve pretendere che due note distinte siano udibili per ogni fusione: l'esito desiderato è una trasformazione timbrica *naturale e soddisfacente*, non un esercizio musicale.

### 4.6 Altre interazioni

| Gesto fisico | Eccitazione | Evoluzione del timbro |
|---|---|---|
| Urto breve | Picco `vn` e compressione locale | Attacco elastico, risonanza corta |
| Schiacciamento | Pressione/deformazione sostenuta | Risonanza morbida con ampiezza correlata alla pressione |
| Sfregamento | `vt` sotto carico di contatto | Trama granulare/rumorosa a bassa intensità, continua |
| Distacco | Termine del contatto, energia residua | Coda naturale, nessun "pop" obbligatorio |
| Adesione che si tende | Allungamento e velocità del filo | Lieve tensione/viscosità; niente sibilo dominante |
| Lancio | Movimento/accelerazione iniziale e dimensione | Breve transizione dalla stessa famiglia timbrica, non clip `swoosh` estranea |
| POP/cancellazione | Rilascio rapido della pressione e volume del blob | Transiente rapido + rilascio risonante dallo stesso materiale |

Le pareti del vaso sono una **fase successiva opzionale**: prima validare blob-blob e fusione, poi decidere se il contatto col vaso debba usare lo stesso materiale o una caratteristica diversa.

## 5. Architettura software proposta

```text
BlobWorld (simulazione 60 Hz, 5 passaggi di vincoli)
   ├─ AcousticProbe
   │    ├─ ContactUpdate(pairId, vn, vt, compression, radii, duration, time)
   │    ├─ MergeAcousticEvent(parents, newBlob, motion, time)
   │    ├─ LaunchAcousticEvent / PopAcousticEvent
   │    └─ ContactEnd(pairId, time)
   └─ Rendering/UI invariati
          │
          ▼
AudioEventBridge (coda bounded, timestamp, coalescing per contatto)
          │
          ▼
BlobSynth (voci parametriche: modi + grani + residuo)
          │
          ▼
Mixer / envelope smoothing / limiter / mute SFX
          │
          ▼
AudioTrack PCM (prototipo) → eventuale Oboe (se necessario)
```

Responsabilità:
- **`AcousticProbe`**: osservazione fisica, nessuna sintesi o I/O; restituisce dati riutilizzabili nei test JVM.
- **`SoundMaterialProfile`**: modello versionato offline, con parametri dei quattro Bloop, grani e intervalli sicuri.
- **`AudioEventBridge`**: passaggio thread-safe e senza blocchi rilevanti dei parametri del motore fisico; aggiornamenti di contatto coalescibili, fusioni mai perse.
- **`BlobSynth`**: risuonatori, texture granulari, inviluppi, pitch, stato delle voci e loro terminazione.
- **`AudioMixer` / `ProceduralAudioEngine`**: priorità delle voci, limiter, volume generale degli effetti, output PCM e ciclo di vita Android.
- **`MusicEngine`**: futuro sistema separato; non è parte del primo prototipo.

### Output Android, latenza e threading

**Prototipo:** `AudioTrack` Java in modalità PCM streaming per limitare le dipendenze. Un thread audio dedicato produce blocchi regolari; campioni decodificati e profili precaricati.

**Opzione successiva:** [Google Oboe](https://developer.android.com/games/sdk/oboe/low-latency-audio), tramite Android NDK C++, *solo se* il prototipo mostra latenza/instabilità che AudioTrack non risolve. Oboe usa le API native disponibili e richiede una callback audio con attenzione a memoria, blocchi e priorità del thread.

**Regole:** niente caricamenti, file I/O, lock bloccanti, allocazioni, log frequenti, Android UI, accessi a rete o sintesi FFT dentro la callback audio. Un'unica uscita stereo/mono con mix interno. La fisica aggiorna parametri a 60 Hz; il DSP interpola verso il sample rate effettivo del dispositivo (ad es. 48 kHz). Non ricalcolare 24 nodi di fisica per campione audio.

Prevedere corretta gestione di pause, resume, focus audio, interruzioni, rotazione non prevista, cambio dispositivo audio e rilascio. Silenziare gli effetti deve fermare/ridurre dolcemente tutte le voci e rispettare la preferenza persistente già presente.

### Budget e priorità delle voci (target iniziali)

- Prototipo: **4–8 voci sonore** totali; regolare secondo profilo CPU e qualità percepita. Non un oscillatore per ciascuno dei 24 nodi di ogni blob.
- **Priorità:** fusione > urto significativo > contatto deformante > leggero sfregamento. L'energia locale influisce sulla priorità, non solo il tipo.
- Per i contatti continuativi una voce è associata al `pairId`; aggiornarla, non crearne una nuova ogni frame.
- Rilascio con fade/crossfade quando una voce viene rimossa o rubata, per evitare click.
- Limiter morbido per impedire clipping e controllo del loudness del mix; non comprimere in modo aggressivo i Bloop.
- Target indicativo: feedback sonoro percepito come immediato (da misurare con latenza *evento fisico → output* sul telefono), nessun underrun udibile, frame time del gameplay non peggiorato. Fissare soglie di accettazione dopo il benchmark iniziale.

## 6. Metodologia sperimentale e confronto A/B

### Primo prototipo controllato
Scena con due blob e quattro azioni ripetibili: **urto, compressione, sfregamento, fusione**. Stessi raggi, velocità e traiettorie per confronti fra sistemi.

Dovrà esistere una possibilità di ascolto A/B fra:
- A. Fusioni originali attuali (`SoundPool`, quattro Bloop).
- B. Fusione interamente procedurale con i parametri estratti.
- C. Fusione ibrida che conserva una quota superiore di frammenti originali, se B risulta troppo sintetica.

Le versioni possono essere esportate come *mix contestuali di prova*, non come pacchetto di asset di floraphonic autonomi.

### Accettazione qualitativa
- **Fusione:** l'utente approva il nuovo modello confrontandolo direttamente con gli originali; nessuna sostituzione solo perché la forma d'onda "sembra simile" nei grafici.
- **Causalità:** ascoltando con video, almeno in condizioni di prova controllate, è chiaro se si tratta di urto, compressione, sfregamento o fusione.
- **Coerenza:** piccolo/grande e lento/veloce hanno differenze riconoscibili e non caricaturali; nessun salto evidente quando si cambia dimensione.
- **Non affaticamento:** una partita con molti blob e scuotimenti intensi non genera un tappeto di suoni ripetitivi o irritanti.
- **Musicalità:** il mix non interferisce eccessivamente con una musica di sottofondo in prova; musica e SFX hanno mute e livelli indipendenti nel prodotto finale.

### Test automatici e tecnici
1. Dati fisici sintetici: silenzio senza contatto/energia, risposta monotona per maggiore eccitazione, durata e pitch coerenti col raggio.
2. Test dei contatti: un solo aggiornamento per coppia/step nonostante i 5 passaggi del solver; begin-update-end, pressione e sfregamento distinguibili.
3. Fusione: evento unico con i due genitori corretti e il raggio del risultato; nessun replay del Bloop classico nella modalità procedurale.
4. DSP deterministico: test di output senza NaN, clipping, click ai confini dei blocchi, voce che non termina, coda infinita o DC persistente.
5. Prestazioni: gioco con numero alto di blob, molte collisioni, shake, fusioni simultanee; CPU, memoria, underrun, frame rate, batteria.
6. Ciclo di vita: Home/partita salvata, pause/resume, mute mentre una voce suona, ripristino della preferenza e audio focus.
7. Build Android: test JVM, test strumentali per audio dove possibile e ascolto reale su smartphone. CI verde **non certifica** la qualità timbrica.

## 7. Roadmap con gate decisionali

| Fase | Deliverable | Gate prima di proseguire |
|---|---|---|
| **A — Analisi** | Profili misurati dei 4 Bloop, spettrogrammi, envelope, grani selezionati, note sulle licenze | Identità sonora documentata, nessun dato inventato |
| **B — Fusione offline** | Tre varianti A/B/C, renderer deterministico e report d'ascolto | Almeno una fusione procedurale/ibrida è convincente quanto gli originali |
| **C — Due blob** | Contatti, schiacciamento, sfregamento e fusione in scena controllata | Relazione chiara fra movimento e suono, senza artefatti |
| **D — Prototipo Android** | `AcousticProbe`, bridge, DSP e `AudioTrack` in modalità sperimentale | Latenza, fluidità, mute e gestione voci accettabili sul telefono |
| **E — Estensione** | Lancio, POP, contatti sostenuti, eventuali pareti | Unico materiale timbrico, mix non fastidioso |
| **F — Integrazione finale** | Sostituzione controllata dei vecchi SFX, rollback e test regressione | Approvazione d'ascolto e tecnica; nessuna modifica non richiesta alla fisica |
| **G — Musica** | Traccia scelta/licenziata, mix e interruttore dedicato | Musica e effetti indipendenti, entrambi ascoltabili senza fatica |

Le fasi possono essere implementate in PR piccole; nessuna nuova versione di gioco va pubblicata finché la relativa fase non ha superato il gate. Evitare di sostituire urti e fusioni simultaneamente prima di aver validato la fase B.

## 8. Rischi e contromisure

| Rischio | Contromisura |
|---|---|
| Fusione sintetica meno piacevole del Bloop | Conservare transienti/grani originali; A/B; non sostituire gli originali finché il risultato non è approvato |
| Sfregamento ripetitivo o zanzaroso | Contatto continuo, grani non periodici, filtraggio morbido, gate energetico e mix basso |
| Suono fuori sincrono | Misurare timestamp fisica/audio; interpolazione, coda bounded, benchmark latenza e Oboe solo se serve |
| Audio che esplode nei cinque passaggi di vincoli | Aggregazione per coppia per step; non generare 5 eventi |
| Volume dipendente troppo dalla dimensione | Frequenza/durata dalla dimensione; ampiezza soprattutto dall'eccitazione compressa |
| Molti blob = molte voci e CPU | Budget limitato, priorità, voice stealing con fade, profili offline |
| Cambio del gameplay per soddisfare la sintesi | Probe passivo e invarianti di fisica verificati con regressioni |
| Proprietà intellettuale dei Bloop | Tracciamento licenza/provenienza, uso integrato nel gioco, evitare distribuzione di sample/derivati isolati |
| Incompatibilità con musica futura | Engine/controlli separati per SFX e musica; master limiter/mix dedicato |
| Regressione/insoddisfazione | Flag `classic/procedural` nelle build di prova, confronto A/B, rollback senza migrazione dati |

## 9. Decisioni e punti da misurare

### Decisioni assunte
- D1: **proceduralizzare anche le fusioni**; i quattro Bloop sono il riferimento artistico primario.
- D2: metodo **ibrido**: risonanze + materiale campionato + residuale controllato.
- D3: sensori acustici passivi basati sulla fisica già esistente; nessuna alterazione del solver per scopi sonori.
- D4: preservare effetti attuali e test A/B sino all'approvazione.
- D5: musica indipendente, con futuro interruttore separato; mantenere da subito il tasto SFX della 1.0.11.
- D6: partire dalla **fusione**, poi interazioni di due blob, poi motore completo.

### Parametri NON ancora convalidati
- Frequenze/modi/decadimenti e grani dei Bloop: **da estrarre dai PCM**, non da presumere.
- Intervalli di pitch, dimensione e decadimento: scegliere dopo le prove A/B.
- Intensità minima udibile, formula di energia e smoothing: calibrazione sulla scena a due blob.
- Quante voci simultanee e quale backend Android: decidere con misura di CPU/latency.
- Bilanciamento con musica: rimandato alla scelta della traccia e a un test con mix reale.

## 10. Note per l'implementazione futura

**Primissimo task tecnico:** strumento offline che decodifica i quattro MP3, produce report e genera varianti di fusione A/B senza cambiare il gioco. Il successo deve essere stabilito ascoltando, prima di impegnarsi nell'engine in tempo reale.

**Primo task in game:** estendere in modo passivo gli eventi fisici con ID persistenti e osservazioni per contatto; verificare che attivare o disattivare il probe non cambi traiettorie, punteggi o fusioni.

**Nessuna modifica richiesta ora** a `GameAudio`, `BlobWorld`, UI, Gradle, workflow di release o asset audio attuali. Questo documento è una *specifica* da cui partire per successivi interventi autorizzati.

## 11. Riferimenti tecnici

- [Phya — Physically Motivated Environmental Sound Synthesis for Virtual Worlds (2010)](https://link.springer.com/article/10.1155/2010/137878) — distinzione contatti/impatti, generatori e risonatori.
- [Example-Guided Physically Based Modal Sound Synthesis (2013)](https://gamma-web.iacs.umd.edu/AUDIO_MATERIAL/examplebasedsoundsynthesis.pdf) — modelli modali derivati da registrazioni e componente residuale.
- [Interactive Granular Synthesis of Haptic Contact Sounds](https://aes.org/publications/elibrary/elibrary-page/?id=11144) — segnali di contatto e sintesi granulare.
- [Android AudioTrack](https://developer.android.com/reference/android/media/AudioTrack) — output PCM streaming per il prototipo.
- [Google Oboe: low-latency audio](https://developer.android.com/games/sdk/oboe/low-latency-audio) e [FAQ Oboe](https://github.com/google/oboe/blob/main/docs/FAQ.md) — opzione native per bassa latenza, no allocazioni/operazioni bloccanti nella callback.
- [Pixabay Content License](https://pixabay.com/service/license-summary/) — uso delle registrazioni e restrizioni di redistribuzione autonoma.

---

**Stato di avanzamento:** `FASE A MISURATA — FASE B TIMBRO APPROVATO CON DISTURBO RESIDUO — FASE C1 BOCCIATA ALL'ASCOLTO — FASE C2 NON CONVINCENTE — FASE C3 PARZIALMENTE PROMETTENTE, COMPRESSIONE RESPINTA — C4 RISCONTRATA: FUSIONE EXTRA NEI WAV E IMPATTI INUDIBILI — C4.1 CORRETTA / ASCOLTO PENDENTE — MOTORE ANDROID NON INTEGRATO`. I risultati sono in [PROCEDURAL_AUDIO_LAB_2026-11-21.md](PROCEDURAL_AUDIO_LAB_2026-11-21.md) e [PROCEDURAL_AUDIO_TWO_BLOB_PROTOTYPE.md](PROCEDURAL_AUDIO_TWO_BLOB_PROTOTYPE.md); vedi [PROCEDURAL_AUDIO_C2_FEEDBACK_REWORK.md](PROCEDURAL_AUDIO_C2_FEEDBACK_REWORK.md) per la bocciatura artistica; [PROCEDURAL_AUDIO_C3_SUBTLE_MIX.md](PROCEDURAL_AUDIO_C3_SUBTLE_MIX.md) registra la nuova gerarchia sonora; [PROCEDURAL_AUDIO_C4_ORGANIC_CONTACT.md](PROCEDURAL_AUDIO_C4_ORGANIC_CONTACT.md) definisce la nuova prova senza componente laser; [PROCEDURAL_AUDIO_C41_PREVIEW_FIX.md](PROCEDURAL_AUDIO_C41_PREVIEW_FIX.md) corregge i WAV, preservando compression B e friction adhesive. L'ultima build utilizzabile rimane la **1.0.11**; nessun effetto esistente è stato sostituito.
