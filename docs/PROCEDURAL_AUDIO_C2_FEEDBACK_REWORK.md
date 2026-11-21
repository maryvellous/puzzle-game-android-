# Lava Blobs — Fase C2: revisione di urto e compressione

**21 novembre 2026 · prototipo offline · non integrato nel gioco**.

## Feedback d'ascolto sul prototipo C1

La persona che sta progettando il gioco ha ascoltato i quattro comportamenti prodotti dalla fisica reale:

| Comportamento | Esito |
|---|---|
| **Urto** | **Bocciato**: suono completamente sbagliato |
| **Compressione** | **Bocciata**: suono completamente sbagliato |
| **Sfregamento** | Solo vagamente simile a quello desiderato: non approvato |
| **Fusione** | Accettabile, da conservare come punto di riferimento |

Resta aperto anche il **lieve disturbo** già percepito nei Bloop C/D precedenti.

**I test tecnici della fase C1 sono riusciti, ma il test artistico è fallito.** Non etichettare il prototipo C1 come sound design approvato. La distinzione fra comportamenti sonori non basta se timbro e causalità non convincono.

## Diagnosi del primo tentativo

Nello script `render_physics_prototype.py`, una somma continua di tre sinusoidi a **185, 260 e 325 Hz** veniva amplificata in funzione della **compressione assoluta**. Di conseguenza, un corpo già compresso ma sostanzialmente fermo poteva continuare a emettere un tono. Questa scelta era estranea alla dinamica visiva della membrana.

L'urto sommava un frammento di un Bloop a questa risonanza continua. Cambiare volume, soglia o pitch non può correggere da solo la scelta sbagliata del materiale eccitato.

Lo sfregamento era il comportamento più promettente perché la texture seguiva una quantità **dinamica**, la velocità tangenziale del contatto.

## Cosa cambia nel laboratorio C2

La simulazione `BlobWorld` e la sua traccia a due bolle **non cambiano**. Il test confronta direttamente vecchio e nuovo audio sulla **stessa dinamica fisica**.

- **Urto A:** un'impulsione fisica breve, determinata dall'inizio reale del contatto e dalla velocità normale, eccita una risposta elastica smorzata tratta dal **Bloop 1**.
- **Urto B:** stesso principio, ma un'altra risposta smorzata tratta dal **Bloop 3**.
- **Compressione A:** variazioni reali della forma della membrana eccitano brevi risonanze ricavate dal Bloop 3; una deformazione costante non continua a generare nuovi suoni.
- **Compressione B:** la **velocità di deformazione** controlla invece una texture continua a fase coerente ricavata dal Bloop 3; la texture sfuma quando la forma smette di cambiare.
- **Sfregamento:** versione C1 mantenuta identica nel montaggio, **non approvata definitivamente**.
- **Fusione:** versione C1 mantenuta identica nel montaggio come riferimento positivo.

Le alternative C2 rimangono solo una **prima prova**, non sono promosse al gioco. Per l'urto l'inizio del contatto fornisce un *impulso fisico* al materiale: è diversa dalla regola precedente che riproduceva un WAV prestabilito ogni volta che la velocità superava una soglia. La coda acustica è generata dal modello a partire dal campione di materiale.

Lo script sperimentale è [`tools/audio_lab/rework_impact_compression.py`](../tools/audio_lab/rework_impact_compression.py); la CI relativa è [`two-blob-acoustic-prototype.yml`](../.github/workflows/two-blob-acoustic-prototype.yml).

## Test e confronto d'ascolto

Il laboratorio deve produrre:

1. `listen_impact_old_newA_newB.wav` — impatto bocciato / nuova A / nuova B.
2. `listen_compression_old_newA_newB.wav` — compressione bocciata / nuova A / nuova B.
3. `listen_all_new_impact_compression_sliding_fusion.wav` — insieme C2 con sfregamento e fusione C1 invariati.
4. `C2_METRICS_AND_FEEDBACK.json` — livelli, impulsi generati e feedback di riferimento.
5. WAV individuali per ogni alternativa.

Test automatici: una bolla con contatto **statico e deformazione costante** deve rimanere silenziosa dopo gli eventuali transitori iniziali; l'urto deve rispondere a velocità normale non nulla; i WAV devono avere valori finiti e assenza di clipping.

**Risultato di verifica tecnica C2:** 11 test Python completati (inclusi 5 nuovi test del movimento e del silenzio a pressione statica); test e traccia reali del solver Java riusciti. La [run CI 44935362278](https://github.com/maryvellous/puzzle-game-android-/actions/runs/44935362278) ha generato i nuovi WAV. Picchi misurati: urto A 0,420, urto B 0,371, compressione A 0,136, compressione B 0,142 (campioni normalizzati, sotto il limite di 0,70). Nessun ascolto della nuova versione ancora effettuato dall'utente.

**Gate d'ascolto non ancora superato:** urto e compressione devono sembrare effettivamente i movimenti della bolla, non semplicemente due suoni diversi. Se nessuno convince, continuare a cercare un modello di eccitazione più adatto; non forzare la promozione al codice Android. **Il gioco resta alla versione 1.0.11.**
