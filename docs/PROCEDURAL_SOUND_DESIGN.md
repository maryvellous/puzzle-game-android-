# Lava Blobs — Motore acustico fisico, progetto fusion-first

**Specifica ufficiale · v2.0 · 22 novembre 2026 · SOLO PROGETTAZIONE (non implementata)**

> **FARO DEL PROGETTO: «Un sistema in cui il comportamento della materia diventa suono.»**

Questa frase è il criterio con cui valutare ogni modifica al motore. I blob non devono emettere effetti scollegati dal loro comportamento: forma, massa, contatto e movimento determinano una risposta sonora credibile, riconoscibile e piacevole.

## 0. Decisione artistica fondamentale — LA FUSIONE È L'EVENTO PRINCIPE

**FUSIONE = UN BLOOP ORIGINALE COMPLETO.** I quattro Bloop di floraphonic sono la *voce completa della materia* e costituiscono la fonte timbrica primaria e il riferimento artistico del sistema finale. Durante una fusione reale si ascolta **l'intero contenuto sonoro** di un Bloop (attacco, corpo centrale e coda), non un'imitazione sintetica, non un estratto C4 e non una ricostruzione per oscillatori.

- Manteniamo **tutti e quattro** i Bloop, con alternanza che evita ripetizioni consecutive. Le variazioni di riproduzione legate a dimensione/volume restano moderate e da calibrare.
- «Completo» significa **l'intera forma d'onda artisticamente significativa** del campione, senza togliere la parte centrale; gli eventuali silenzi di padding/encoder possono essere trattati tecnicamente senza tagliare il contenuto.
- La fusione deve essere udibile, appagante e dominante nel mix. Un altro suono non deve mascherarla né assomigliarle tanto da far credere che ci sia stata una fusione inesistente.
- Fusioni multiple reali possono coesistere in modo controllato con priorità e attenuazioni morbide; **non tagliare brutalmente il Bloop precedente** e non falsificare o scartare fusioni della fisica per risolvere il mix.
- La scelta di usare i Bloop **completi** per la fusione è il comportamento **di riferimento deciso**, non un provvisorio da rimpiazzare con una fusione procedurale.

### Tutti gli altri suoni sono espressioni parziali dello stesso materiale

Gli altri eventi non devono ricevere timbri arbitrari né campioni autonomi estranei. Si costruiscono usando **parti, caratteristiche e trasformazioni dei quattro Bloop completi**, regolate dalla fisica. Il Bloop è la «frase completa»; i contatti e i movimenti ne esprimono solo frammenti.

Schema iniziale di **analisi timbrica**, non tempi di taglio già misurati:

| Zona del Bloop | Funzione artistica | Impiego da esplorare |
|---|---|---|
| **A — inizio / attacco** | Prima eccitazione elastica, timbro e onset | Piccoli urti e avvii del movimento |
| **B — parte centrale / corpo** | Il gesto riconoscibile e appagante della fusione | **Riservato prioritariamente alla fusione completa**; per gli altri comportamenti non ricreare l'intera «firma» |
| **C — coda / decadimento** | Membrana che vibra, perde energia e torna a riposo | Attrito continuo granulare, rilascio, piccole risonanze residue |

**Esempio proposto dall'utente:** per un evento che *non* è una fusione si possono impiegare **l'inizio e la coda di un Bloop, senza la parte centrale**, scegliendo durata, inviluppo e proporzioni in base al movimento. Non incollare meccanicamente «inizio + coda» a ogni evento: alcuni movimenti richiedono solo una coda, altri un attacco breve, altri ancora semplice silenzio. Il suono deve risultare fisicamente motivato e non trasformarsi in una fusione fasulla.

**Questa è una gerarchia di significato:** il suono completo comunica *fusione*. Le manifestazioni parziali comunicano altri stati della **stessa materia**. Quindi tutto il sistema è subordinato alla fusione, sia come sorgente timbrica sia nel mix sia nella comprensibilità per chi gioca.

### Matrice di comportamento definitiva di principio

| Fenomeno | Segnale fisico | Risposta sonoro-materiale |
|---|---|---|
| **Fusione** | Evento reale di fusione di due blob | **Bloop originale completo**, con relazione delicata fra dimensione e pitch/tempo |
| **Urto** | Primo contatto nuovo + energia normale locale | Breve frammento d'attacco e/o coda smorzata del Bloop; il corpo caratteristico della fusione non viene eseguito |
| **Sfregamento** | Contatto reale persistente + velocità tangenziale locale | Texture ottenuta principalmente dalle code, con crossfade/grani e inviluppo continuo; non solo legami di adesione |
| **Compressione** | Variazione della deformazione, pressione/proxy locale | Modifica smorzamento/risonanza di una voce già eccitata; eventuale traccia sottilissima dal materiale, **mai un effetto laser autonomo** |
| **Distacco** | Fine del contatto e energia residua | Piccola coda che decade, oppure silenzio; non generare automaticamente un POP |
| **Lancio** | Azione di rilascio della nuova pallina | Feedback brevissimo, molto discreto, da frammento affine ai Bloop; anti-accumulo durante i lanci a raffica |
| **POP/cancellazione** | Rimozione effettiva del blob | Breve rilascio dalla stessa famiglia timbrica, senza confondersi con la fusione |
| **Immobilità** | Nessuna eccitazione significativa | **Silenzio**, anche con blob ancora deformati o appoggiati |

Tutte le proposte per i non-fusioni sono **ipotesi di sound design da testare**; nessun preciso taglio del Bloop è ancora approvato. Le texture del laboratorio C4/C4.1 possono essere consultate solo come *feedback storico*, non sono il materiale obbligatorio né la base architetturale del motore definitivo.

## 1. Che cosa sappiamo dai playtest

- Prima **Audio Test**: i suoni legati al gameplay erano più convincenti del previsto, ma blob piccoli e grandi suonavano quasi uguali.
- **Audio Size Test**: la modulazione del pitch/tempo in base alla dimensione **funzionava** ed è il riferimento comportamentale da conservare.
- **Audio Mix Test**: il gating aggressivo ha reso quasi inudibili gli impatti, non ha fatto emergere lo sfregamento e non ha risolto il disturbo; **non è la base di sviluppo**.
- Problemi ancora aperti: audio che «frigge» quando gli effetti si sovrappongono, soprattutto con lanci a raffica; lancio troppo predominante; micro-urti inutilmente udibili; sfregamento praticamente assente.
- In C4/C4.1 alcune texture isolate di compression B e friction adhesive erano apprezzate, ma **la nuova decisione è ricavare l'intera famiglia sonora dai Bloop completi**, non implementare C4 per inerzia.

**Stato delle app di prova:** le tre APK sperimentali Audio Test, Audio Size Test e Audio Mix Test sono **ritirate dall'uso attivo e sostituite da questa specifica**. L'utente le disinstallerà dal telefono; la disinstallazione non è stata effettuata da ChatGPT. La release normale **1.0.11** e i suoi dati restano separati e invariati. Branch, PR, workflow e risultati storici non diventano automaticamente candidati al merge o alla pubblicazione.

## 2. Come leggere la fisica senza cambiarla

**BlobWorld rimane autorità sulla fisica.** Non alterare traiettorie, urti, membrana, geometria, punteggio o fusioni per generare suoni. La geometria esistente è formata da membrane a **24 nodi** e da un solver con **cinque passaggi** per step.

Nuovo componente passivo: **Contact Probe** dentro o vicino alla fase di proiezione dei contatti. Raccoglie dati *prima* delle correzioni del solver, accumulando una sola sintesi per coppia e per step, non un evento audio per vertice o per passaggio. Le adesioni sono un fenomeno ausiliario, **non** l'unica fonte di prova del contatto.

Dati richiesti, per coppia e per step fisico:
- Identificatori **stabili** dei due blob, timestamp di simulazione, start/update/end del contatto e presenza di fusione.
- Normale e tangente *locali*, velocità relativa normale e tangenziale dei nodi coinvolti **prima** della correzione di posizione.
- Regione/numero di punti in contatto, eventuale interpenetrazione corretta e misura di carico/deformazione *proxy*, senza spacciare spostamenti per forze reali.
- Variazione di area/curvatura, rapporto dei raggi, dimensione media acustica o massa percepita, durata del contatto.
- Evento di fusione con dati dei genitori e del nuovo blob acquisiti **prima** della rimozione delle membrane.

Nuovo componente: **Contact Tracker**. Mantiene la memoria delle coppie di blob fra gli step: separati → primo contatto → contatto persistente → separazione. Il solver può correggere un contatto già attivo senza far nascere un nuovo urto sonoro. Un paio di blob che sfregano continuamente deve restare sonoro finché scorrono anche in assenza di legami adesivi.

Non usare soltanto velocità dei centri o soglie globali rigide: una correzione insignificante non è un urto nuovo, ma una collisione reale non deve scomparire perché suoni emessi di recente hanno consumato un cooldown arbitrario.

## 3. Motore audio: un unico materiale, varie eccitazioni

Il progetto è un **motore ibrido informato dai Bloop originali** e guidato dai parametri continui della fisica.

1. **Material Bank offline**. Decodifica *senza alterare* i quattro sorgenti MP3; analizza forma d'onda, inviluppi, attacchi, corpo riconoscibile, code, contenuto tonale e rumoroso. Produce un manifesto versionato con l'identità del Bloop, marker A/B/C basati su misure, crossfade sicuri, frammenti e metadati; gli originali integrali rimangono disponibili per ogni fusione.
2. **Excitation Engine**. Trasforma il primo impatto, lo scorrimento, la deformazione e il rilascio in eccitazioni e inviluppi. Lo stesso contatto può evolvere dall'urto allo sfregamento senza passare per due effetti scollegati.
3. **Material Renderer**. Riproduce il Bloop **completo** soltanto per fusioni reali; per gli altri stati usa attacchi, code, grani, filtraggio e/o risonanze **derivate** dal materiale, senza riemettere il caratteristico corpo centrale come se ci fosse stata una fusione.
4. **Size & Energy Mapping**. Piccoli blob più acuti e rapidi, grandi più gravi e lenti; l'intensità segue movimento e intensità di contatto, non semplicemente la dimensione. La curva della precedente Audio Size Test è il punto di partenza *ascoltato*, non una legge numerica universale.
5. **Scene Mixer unico**. Un solo bilancio di voci e un solo output PCM; le fusioni sono il primo piano, gli altri suoni fanno spazio con attenuazioni morbide. Evitare accumulo dei lanci, hard-stop dei Bloop, cuciture di loop e clipping del mix. Valutare budget dinamico delle voci e limiter finale; non confondere peak limit con qualità percettiva.

Il nuovo materiale sonoro **non deve essere generato ogni frame** da zero: i Bloop e le porzioni vengono preparati offline; durante la partita si modulano emissione, crossfade, intensità, pitch, inviluppi e combinazione. Un'implementazione Android C++/Oboe con rendering offline identico è una candidata plausibile, **non obbligatoria** finché non avremo misurato costi, latenza e necessità reali. L'attuale SoundPool e i test C4 sono riferimenti storici, non vincoli architetturali del nuovo motore.

### Vincoli di mix

- La fusione completa **deve restare riconoscibile e protagonista** anche in una cascata. Se più fusioni fisiche avvengono insieme, scegliere una gestione coerente delle voci, con headroom, inviluppi e compressione musicale controllata; non eliminare gli eventi di gioco né trasformare il Bloop in tronconi.
- Il volume del lancio va abbassato rispetto al feedback pregresso e i lanci ravvicinati devono condividere un voice budget, evitando code sovrapposte e distorsione.
- Un contatto continuo non deve suonare con un loop che salta o viene avviato e fermato ogni frame; campioni granulati e crossfade devono essere continui.
- **Silenzio è un risultato corretto.** Assestamenti impercettibili, compressione statica, poco movimento tangenziale o contatto privo di energia possono non emettere nulla.
- In un ammasso di molti blob, far prevalere l'**energia della scena**, non moltiplicare il volume per ogni coppia: una o poche texture collettive che riflettono i contatti più rilevanti.
- Con musica futura, bus SFX e musica distinti, controlli indipendenti; il toggle già esistente \`SUONI/MUTO\` continua a governare tutti gli SFX.

## 4. Strategia di sviluppo: non scrivere altre APK alla cieca

**Fase 0 — Baseline e archivio.** La versione stabile è **1.0.11**. Conservare i quattro Bloop integrali e gli esperimenti storici, non creare un'altra APK per tentare a caso variazioni di volume. La vecchia specifica v1 è in [PROCEDURAL_SOUND_DESIGN_V1_ARCHIVE.md](PROCEDURAL_SOUND_DESIGN_V1_ARCHIVE.md).

**Fase 1 — Bloop Anatomy.** Analisi strumentale dei quattro Bloop, ascolto segmentato A/B/C e prove che confrontano: Bloop integrale; breve attacco senza corpo; coda; combinazione attacco+coda senza il corpo. La fusione *rimane sempre integrale* anche se gli estratti non convincono. Salvare un manifesto e un report; non inventare durate, marker e frequenze.

**Fase 2 — Contact Probe e replay.** Telemetria reale per contatti tra membrane e lancio, con identificatori persistenti, aggregazione per step e nessuna modifica al moto. Scenari riproducibili: tocchi lievissimi, urti netti, sfregamenti con e senza adesione, compressioni immobili, lanci a raffica, fusioni multiple. In particolare verificare che lo sfregamento venga **rilevato**, prima di regolarne il guadagno.

**Fase 3 — Renderer offline deterministico.** A partire dalle *stesse* tracce fisiche, produrre WAV con il Bloop integrale durante fusione e segni parziali nei comportamenti non-fusione. Compiti misurabili: nessun Bloop «fantasma», nessuna fusione troncata, urti deboli non invadenti, attrito udibile quando il contatto scorre, silenzio a riposo.

**Fase 4 — Mixer, stress e qualità.** Renderizzare scene dense con strumenti di misura (picchi true/PCM, RMS, numero di voci, brusche discontinuità, onset, overs, glitch). Identificare separatamente la frittura dovuta a lanci, interruzioni e sovrapposizioni. Mitigare con gestione dei flussi e inviluppi, non con cooldown distruttivi.

**Fase 5 — Un'unica APK diagnostica.** Solo a esito favorevole delle prove precedenti: scegliere eventuale motore audio nativo, integrare con buffer non bloccanti e UI di calibrazione. Possibilità di isolare temporaneamente lancio/contatto/fusione, acquisire log e registrazioni brevi, confrontare A/B. **I tre prototipi precedenti non vanno riproposti come nuova base.**

### Criteri di approvazione

1. Fusione = un Bloop **completo** e riconoscibile, esclusivamente in corrispondenza di un evento reale di fusione.
2. Ogni altro suono viene dalla stessa **famiglia Bloop** e il solo ascolto non fa supporre fusioni inesistenti.
3. Contatto persistente, sfregamento, deformazione e separazione producono risposte continue legate ai dati del solver; immobilità silenziosa.
4. Piccoli/grandi distinguibili senza correlazione rigida «più grande = più forte».
5. Lanci a raffica e fusioni in cascata non producono crepitii, clipping, code tagliate o sforamenti significativi.
6. Test su telefono, stabilità FPS/CPU e costi audio entro budget misurati; CI verde da sola non basta.
7. Approvazione **artistica dell'utente** in una vera partita prima di integrare nella release normale.

## 5. Provenienza e storico

Fonti originali, attribuzione e condizioni d'uso:
- \`app/src/main/res/raw/bloop_1.mp3\` — [floraphonic, Bloop 1](https://pixabay.com/sound-effects/film-special-effects-bloop-1-184019/)
- \`app/src/main/res/raw/bloop_2.mp3\` — [floraphonic, Bloop 2](https://pixabay.com/sound-effects/film-special-effects-bloop-2-186531/)
- \`app/src/main/res/raw/bloop_3.mp3\` — [floraphonic, Bloop 3](https://pixabay.com/sound-effects/film-special-effects-bloop-3-186532/)
- \`app/src/main/res/raw/bloop_4.mp3\` — [floraphonic, Bloop 4](https://pixabay.com/sound-effects/film-special-effects-bloop-4-186533/)

Rispettare la [Pixabay Content License](https://pixabay.com/service/license-summary/) e non distribuire queste sorgenti o derivati come sound pack autonomo. I documenti [laboratorio A/B/C/D](PROCEDURAL_AUDIO_LAB_2026-11-21.md), [due blob](PROCEDURAL_AUDIO_TWO_BLOB_PROTOTYPE.md), [C2](PROCEDURAL_AUDIO_C2_FEEDBACK_REWORK.md), [C3](PROCEDURAL_AUDIO_C3_SUBTLE_MIX.md), [C4](PROCEDURAL_AUDIO_C4_ORGANIC_CONTACT.md) e [C4.1](PROCEDURAL_AUDIO_C41_PREVIEW_FIX.md) sono **materiale storico, non il progetto attuale**.

**Regola di modifica futura:** se un'implementazione ripristina la fusione sintetizzata o integra C4 come fonte primaria dei non-fusioni senza una nuova scelta artistica esplicita, è **fuori specifica**, anche se i test tecnici risultano verdi.
