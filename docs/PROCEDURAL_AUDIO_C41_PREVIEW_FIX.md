# Lava Blobs — C4.1: correzione dei montaggi e degli impatti inudibili

**Data:** 21 novembre 2026. **Stato:** revisione dei file di ascolto, nessun nuovo APK.  
**Versione Android:** **1.0.11**, suoni classici invariati.

## Problema individuato

L'ascolto C4 ha rivelato un errore nei materiali di confronto: si sentiva un Bloop della fusione in momenti nei quali non avrebbe dovuto suonare. La causa è stata **verificata nel codice**: la funzione che creava i montaggi passava `fusion` come **ultimo elemento di ciascuna lista**, anche nei confronti dedicati esclusivamente a urto, sfregamento e compressione. Il riferimento poteva dunque sembrare parte del comportamento fisico in prova, falsando il giudizio.

Questo è **un errore nell'esportazione dei WAV offline**, non una regressione del gameplay Android: `GameAudio`, `BlobWorld`, i quattro Bloop originali e il resto dell'APK non sono stati modificati.

### Feedback artistico C4

| Comportamento | Esito | Decisione C4.1 |
|---|---|---|
| Fusione | Riferimento riconoscibile e gradito, ma compariva quando non richiesta nei montaggi | In un **solo WAV di riferimento esplicitamente nominato**, mai aggiunta ad altri confronti |
| Compressione **B** | **Va bene** | Conservare il WAV C4 **identico, byte per byte** |
| Friction **adhesive** | **Va bene** | Conservare il WAV C4 **identico, byte per byte** |
| Impatti | **Non si sentono affatto** | Aumentarne la presenza all'ascolto, mantenendoli decisamente più discreti della fusione |

## Soluzione

Lo strumento [`tools/audio_lab/prepare_c41_previews.py`](../tools/audio_lab/prepare_c41_previews.py) usa i WAV C4 già generati. Non modifica l'origine del suono, non cambia la fisica e non ritocca i due comportamenti graditi.

Gli urti C4, limitati al **20% del picco della fusione** e lunghi pochi istanti in una finestra piena di silenzio, erano poco percepibili. Per un test comparativo della medesima collisione fisica vengono creati:
- **Urto A (+9% pitch)** al **32%** del picco della fusione;
- **Urto A (+9% pitch)** al **40%** del picco della fusione;
- **Urto B (+16% pitch)** al **32%** del picco della fusione.

I nuovi livelli sono **solo di collaudo per la collisione fissa**. Nel gioco futuro il guadagno dovrà essere proporzionale alla reale velocità/energia del contatto: non normalizzare indiscriminatamente ogni impatto.

Per valutare gli urti, il file montaggio ritaglia soltanto il silenzio superfluo **attorno al segnale**, senza creare nuovi suoni. Nessun WAV di confronto contiene un Bloop di fusione nascosto.

### File di confronto (artifact GitHub Actions)

Nella cartella `two_blob_c41`:

| File | Contenuto reale |
|---|---|
| `07_COMPARE_ONLY_IMPACTS_NO_MERGE.wav` | Tre impatti (32%, 40%, 32%), separati da silenzi; **nessuna fusione** |
| `08_COMPARE_IMPACT_FRICTION_COMPRESSION_NO_MERGE.wav` | Urto A 40% → adhesive originale C4 → compression B originale C4; **nessuna fusione** |
| `01_impact_A_plus9_audible_32percent.wav` | Impatto A soltanto |
| `02_impact_A_plus9_audible_40percent.wav` | Impatto A più udibile soltanto |
| `03_impact_B_plus16_audible_32percent.wav` | Impatto B soltanto |
| `04_friction_adhesive_UNCHANGED.wav` | **Copia byte per byte** di C4 friction adhesive |
| `05_compression_B_UNCHANGED.wav` | **Copia byte per byte** di C4 compression B |
| `06_fusion_ONLY_EXPLICIT_REFERENCE.wav` | **Solo qui** la fusione, chiaramente etichettata |

Inoltre `C41_AUDIT.json` riporta guadagni, picchi e metadati; `READ_ME_FIRST.txt` descrive l'ordine e i contenuti dei file. Scaricare gli artifact **direttamente da GitHub Actions**: i precedenti collegamenti sandbox non funzionavano in modo affidabile sul PC.

### Test e gate

- Test che i nuovi impatti siano misurabilmente più presenti ma sempre al massimo al **40% del picco della fusione**.
- Test d'integrazione che i WAV delle due preferenze (friction adhesive e compression B) restino **byte-identici**.
- Verifica della lunghezza e composizione esatta dei montaggi; nessun evento di fusione in coda.
- Generazione automatica dei nuovi WAV mediante la stessa traccia fisica Java e CI GitHub.

**Non ancora convalidato artisticamente:** se gli impatti sono finalmente percepibili *e* mantengono il carattere morbido e discreto voluto. Non integrali nell'APK fino all'ascolto. **Il lieve disturbo/frittura dei Bloop derivati resta un problema separato, ancora aperto.**
