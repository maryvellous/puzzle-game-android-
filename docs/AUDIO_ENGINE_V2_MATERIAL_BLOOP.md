# Lava Blobs — Audio: la materia diventa suono
**Specifica canonica vigente · 27 novembre 2026**

> «Un sistema in cui il comportamento della materia diventa suono.»

## Stato
App main 1.1.0, Audio V2.3, preview approvata dall'utente cfb03c9, promossa con acf6d2b; [CI main verde](https://github.com/maryvellous/puzzle-game-android-/actions/runs/46060029269). Primo stadio di sviluppo concluso. Ultima release pubblicata 1.0.11; pubblicazione della 1.1.0 separata. Fase 2: ridurre la lieve frittura percepita sotto stress mediante aggregazione di eventi simili. [Piano aggregazione](AUDIO_AGGREGATION_PLAN.md) e [audit](TECHNICAL_DEBT_AUDIT.md) sono proposte di lavoro, non funzionalità già presenti. Non serve migrazione aggiuntiva dei salvataggi.

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
- V2GameSound: coda di comandi verso thread audio, AudioTrack PCM mono 44.1 kHz, blocchi da 384 frame. AndroidBloopDecoder decodifica materiali fuori UI. Main e preview condividono il motore.
- MaterialAudioEngine/BloopMaterialBank: gating, voci finite, mix condiviso; 24 voci corte, fusioni complete. Limiter condiviso .87, rilascio 150 ms; non attribuire proprietà di priorità al limiter che non possiede.
- Slider 0–100% quadratico dopo limiter, rampa 8 ms, mute/ripristino e preferenza locale. Musica futura separata.
- La fase 2 dovrà aggregare dopo gating, prima dell'emissione, mantenendo materiali e fusioni distinti; il piano specifica i gate. Nessun algoritmo di clustering è ancora approvato nel dettaglio.

## Regressioni da non reintrodurre
Il playtest V2.0 ha bocciato un ronzio acuto persistente (~576 Hz rilevato nel filmato) da loop della coda eccitato da micro-movimenti. V2.1 ha eliminato i loop: [CI 45279301704](https://github.com/maryvellous/puzzle-game-android-/actions/runs/45279301704) scena ferma 1303 osservazioni, 0 squish/impatti, PCM zero; stessa scena agitata 9 squish/8 impatti. V2.2 ha alzato non-fusioni 3x, valutazioni 3,5/4/2/4/4,5 (fisica/timbro/bilanciamento/pulizia/sincronia). V2.3 ha distinto vetro/gel, ripristinato swoosh e aggiunto slider. L'utente l'ha approvata. Queste misure e feedback sono storici, non parametri da ritarare automaticamente.

## Gate per ogni modifica
Test deterministici con solver reale e stress; silenzio a riposo; un Bloop intero per fusione; nessun clipping/artefatto; latenza e comportamento percettivo verificati sul telefono. CI non sostituisce il playtest. [Protocollo collaudo](PLAYTEST_AUDIO_V2.3.md). Non modificare fisica, UI o timbro senza motivo; non promuovere o pubblicare senza richiesta. Le vecchie prove Python, SoundPool e branch A/B–C4.1 sono ricerca storica; Git conserva le versioni precedenti della specifica.
