# Lava Blobs — Audio: la materia diventa suono
**Specifica canonica vigente · 28 novembre 2026**

> «Un sistema in cui il comportamento della materia diventa suono.»

## Stato
Baseline approvata dall'utente: Aggregation Preview [CI #238](https://github.com/maryvellous/puzzle-game-android-/actions/runs/46094635920), sorgente [43aca8e](https://github.com/maryvellous/puzzle-game-android-/tree/baseline/late-game-approved-2026-11-28). Feedback: più fluido, nessun frittino percepito durante il collaudo. Il consolidamento adotta REFINED + aggregazione in tutte le build; gli altri profili mantenuti sono controlli offline. Non è una misura strumentale di underrun. Main e distribuzione richiedono promozione separata. Vedi [roadmap](TECHNICAL_ROADMAP.md).

## Grammatica approvata
| Evento | Risposta |
|---|---|
| Fusione reale | Uno dei quattro Bloop originali integrali, con pitch per dimensione; mai ricostruiti o troncati per liberare voci. |
| Blob contro parete | Attacco originale V2.2, intensità dalla velocità normale bulk. |
| Blob contro blob | Attacco/coda Bloop senza nucleo, smussati, pitch ascendente, risposta gelatinosa. |
| Scorrimento/deformazione | Squish finiti, intensità legata all'attività fisica; contatto statico silente. |
| Lancio | Swoosh classico invertito (sorgente CC0 già inclusa), pitch per tier, eccezione di feedback input. |
| POP | Frammento Bloop breve. |
| Quiete | Silenzio dopo esaurimento delle code, niente loop. |

Fusioni riconoscibili per completezza e ricchezza, non necessariamente più forti di ogni collisione. Nessun voice stealing delle fusioni. La massa influisce su rate e volume tramite proxy; non inventare una forza non misurata. Preservare suono approvato e i quattro file originali; licenze in README. Non distribuire campioni come sound pack.

## Architettura attuale
- BlobWorld/Vessel: solver soft-body 24 nodi, cinque passaggi; non modificare fisica per produrre audio.
- ContactProbe: osservazione passiva, aggregazione per coppia/sottopasso; velocità relative, pressione proxy, moto bulk. Vetro aggregato per blob/lato con isteresi. ContactTracker gestisce ciclo di contatto e cancellazioni.
- PhysicsAudioEvents/AudioEventQueue: eventi immutabili timestamped, FIFO preallocata 8192 con riserva 1024 per controlli, nessuna espulsione di eventi ammessi; overflow di controllo invalida la sessione.
- AudioSession/AudioBlockScheduler/AudioTimeline: generazioni per reset e lifecycle, scheduling a offset campione; budget 512 dispatch per blocco, lead nominale 384 frame. V2GameSound è l'unico owner di AudioTrack PCM16 mono 44.1 kHz, blocchi da 384 frame. Decoder Android fuori UI.
- MaterialAudioEngine.forGameplay: gating fisico, AcousticAggregator dopo gating, PcmMixer e PeakLimiter; 24 voci corte, fusioni complete senza voice stealing, 64 voci riciclabili. Cluster GEL/WALL (12 ms) e SQUISH (30 ms), rapporto rate <= 1.12, massimo 32 gruppi × 64 contributi, ordine deterministico. LAUNCH/POP/fusioni non aggregati. Cancellazione squish pendenti su rimozione/fusione.
- REFINED: interpolazione lineare, envelope 0.5/1.5 ms, limiter .87 con 132 frame (~3 ms) lookahead e rilascio 150 ms; slider quadratico 0–100% a valle, rampa 8 ms, preferenza locale. Campioni originali immutati; il cubico non è stato adottato.
- Debug principale e preview diagnostica usano la stessa factory del runtime release; solo la preview mostra banner e registra trace locale. Offline usa lo stesso scheduler e core, decoder desktop diverso. Confronti bypass/APPROVED rimangono per regressioni, non opzioni di gioco. Musica futura separata.

## Regressioni da non reintrodurre
Il playtest V2.0 ha bocciato un ronzio acuto persistente (~576 Hz rilevato nel filmato) da loop della coda eccitato da micro-movimenti. V2.1 ha eliminato i loop: [CI 45279301704](https://github.com/maryvellous/puzzle-game-android-/actions/runs/45279301704) scena ferma 1303 osservazioni, 0 squish/impatti, PCM zero; stessa scena agitata 9 squish/8 impatti. V2.2 ha alzato non-fusioni 3x, valutazioni 3,5/4/2/4/4,5 (fisica/timbro/bilanciamento/pulizia/sincronia). V2.3 ha distinto vetro/gel, ripristinato swoosh e aggiunto slider. L'utente l'ha approvata. Queste misure e feedback sono storici, non parametri da ritarare automaticamente.

## Gate per ogni modifica
Test deterministici con solver reale e stress; silenzio a riposo; un Bloop intero per fusione; nessun clipping/artefatto; latenza e comportamento percettivo verificati sul telefono. CI non sostituisce il playtest. [Protocollo collaudo](PLAYTEST_AUDIO_V2.3.md). Non modificare fisica, UI o timbro senza motivo; non promuovere o pubblicare senza richiesta. Le vecchie prove Python, SoundPool e branch A/B–C4.1 sono ricerca storica; Git conserva le versioni precedenti della specifica.
