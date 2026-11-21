# Lava Blobs — Laboratorio audio procedurale, traguardi A/B

**Data:** 21 novembre 2026. **Base:** release Android 1.0.11.  
**Stato A:** completato con dati misurati dai quattro file MP3 originali.  
**Stato B:** quattro riferimenti/ricostruzioni prodotti e validati tecnicamente; **approvazione d'ascolto ancora necessaria**.  
**Nel gioco:** nessun cambiamento ad audio, fisica, preferenze o APK.

## 1. Riproducibilità e artefatti

- Script offline: [`tools/audio_lab/analyze_and_render.py`](../tools/audio_lab/analyze_and_render.py)
- Test DSP: [`tools/audio_lab/test_audio_lab.py`](../tools/audio_lab/test_audio_lab.py)
- Workflow GitHub Actions: [`.github/workflows/procedural-audio-lab.yml`](../.github/workflows/procedural-audio-lab.yml)
- **[Esecuzione CI verificata](https://github.com/maryvellous/puzzle-game-android-/actions/runs/44923497640)**, test unitari, analisi audio, generazione WAV/spettrogrammi e caricamento artifact riusciti.
- Artifact prodotto: `lava-blobs-bloop-ab-prototype` (caricato nella sezione *Artifacts* della run, **14 giorni di conservazione**, fino al 5 dicembre 2026).
- Contenuti artifact: `features.json` (dati), `bloop_analysis.png` (forme d'onda + spettrogrammi), 12 WAV di confronto, 6 variazioni di dimensione/energia e `listen_A_original_B_modal_C_hybrid_D_natural.wav` (montaggio confronto).

**Per ricreare l'esperimento localmente**, dalla radice del repository con Python, FFmpeg e `numpy`, `scipy`, `matplotlib` installati:

```bash
python tools/audio_lab/analyze_and_render.py --inputs app/src/main/res/raw --output build/audio_lab
python -m unittest discover -s tools/audio_lab -p 'test_*.py' -v
```

Su runner senza `ffmpeg` di sistema viene usato il pacchetto `imageio-ffmpeg`.

## 2. Misure sui quattro Bloop originali

Il procedimento decodifica gli MP3 mono PCM a 44,1 kHz, localizza il contenuto significativamente udibile con una finestra RMS da 10 ms e usa STFT per analizzare l'energia in frequenza.

| File | Durata decodificata | Estratto attivo (circa) | Centroide spettrale mediano | RMS sull'estratto |
|---|---:|---:|---:|---:|
| Bloop 1 | 627 ms | 190 ms | 248 Hz | 0,412 |
| Bloop 2 | 627 ms | 130 ms | 384 Hz | 0,432 |
| Bloop 3 | 1.045 ms | 230 ms | 187 Hz | 0,307 |
| Bloop 4 | 1.045 ms | 190 ms | 240 Hz | 0,322 |

**Osservazione:** una larga parte dei file è silenzio/padding, non risonanza sostenuta. I quattro campioni contengono transitori molto brevi con traiettorie spettrali differenti: l'attacco reale e la modulazione del timbro sono fondamentali per la loro riconoscibilità.

**Cautele metodologiche:** l'intervallo "attivo" dipende dalla soglia RMS dell'algoritmo (-50 dBFS o 2,5% dell'RMS massimo, con margini); non è una durata acustica oggettiva. Il centroide non equivale a un pitch. La lista `candidate_modes_hz` in JSON proviene dai massimi spettrali su STFT a risoluzione limitata: **non** è stata validata come elenco di veri modi fisici o fondamentali. L'elaborazione attuale decodifica in mono per un primo esperimento, e non ha ancora caratterizzato la componente stereo degli originali.

## 3. Esperimenti di ricostruzione

| Variante | Metodo | Scopo |
|---|---|---|
| **A — originale** | Breve porzione attiva del Bloop 2, riferimento | Valutazione comparativa |
| **B — modale** | Oscillatori con frequenze/ampiezze guidate dai massimi misurati nel tempo | Valutare quanta identità timbrica è riproducibile senza campioni nel corpo |
| **C — ibrida** | Attacco campionato + 64% componenti modali e 36% grani | Esplorare una ricostruzione parametrica |
| **D — texture conservata** | Attacco originale più esteso + 22% componente modale e 78% grani | Preservare più carattere del Bloop senza riprodurlo interamente |

Il montaggio A/B/C/D contiene pause da 400 ms, con bilanciamento RMS preliminare prima del limite di picco. Le dodici WAV nelle sottocartelle permettono di confrontare B/C/D per tutti e quattro i file originali.

Sei ulteriori WAV esplorano **tre taglie** (piccola/media/grande) e **due intensità** (soft/strong), usando provvisoriamente la variante C. Il passaggio di dimensione cambia velocità di lettura e durata; **non** è ancora il collegamento alla fisica del gioco.

**Primo riscontro tecnico:** la ricostruzione modale isolata perde parte della brillantezza spettrale dell'originale (visibile negli spettri). La variante D è un tentativo mirato a conservare più attacco e texture. Non dedurne quale suoni meglio senza ascolto.

## 4. Test automatici svolti

- 4 test JVM? **No:** sono 4 test Python DSP indipendenti da Android, su segnali sintetici.
- Rilevamento tratto attivo, ricostruzione modale/ibrida finita e distinguibile, durata variabile con la taglia, livello coerente con l'intensità, limite di picco.
- Analisi dei quattro MP3 reali e validazione dei WAV generati: PCM mono 44,1 kHz, ampiezza limitata, campioni finiti e durata non nulla.
- Spettrogrammi, JSON di misure, materiali di ascolto generati senza modificare l'app.

Nessun test d'ascolto da parte dell'utente è stato ancora eseguito. Nessuna prova di utilizzo CPU/latency su Android è stata eseguita: l'engine in tempo reale non esiste ancora.

## 5. Gate B da chiudere con ascolto

1. Aprire l'artifact della run e ascoltare `listen_A_original_B_modal_C_hybrid_D_natural.wav`. Confrontare A, B, C e D a pari livello percepito.
2. Ascoltare le tre varianti di ognuno dei quattro Bloop per giudicare attacco, naturalezza, eventuali sibilanti/crepitii o risonanze metalliche.
3. Ascoltare le variazioni piccola/media/grande e soft/strong: il cambiamento di carattere deve essere naturale, non un semplice effetto di nastro rallentato.
4. Se B/C/D restano meno convincenti dell'originale, ripetere l'analisi e i prototipi **prima** di qualsiasi integrazione nel gioco.

**Successivo traguardo, dopo approvazione del materiale sonoro:** prototipo fisico a due blob (collisione, compressione, sfregamento, fusione) con `AcousticProbe` passivo, verificando che cinque passaggi del solver non generino cinque impulsi sonori.

## 6. Licenze

Gli input sono quattro Bloop di floraphonic da Pixabay, già accreditati nel README del progetto. I file WAV derivati nell'artifact sono **materiale di valutazione interno**; evitare pubblicazione o vendita come raccolta di effetti sonori separata dal gioco. La PR contiene solo codice e documentazione, non nuovi sample derivati.

## 7. Iterazione dopo ascolto: eliminare il suono "fritto" (C e D)

**Feedback dell'utente:** il Bloop originale **A** non presenta il difetto; le ricostruzioni **C e D** sono abbastanza piacevoli, ma con un leggero crepitio/fruscio "fritto" che potrebbe risultare fastidioso dopo molti minuti.

**Ipotesi tecnica verificabile:** il primo granular renderer sceglieva frammenti da posizioni temporali arbitrarie a ogni salto di circa 22 ms e li sovrapponeva tramite finestre Hann. Questo non garantiva la continuità della fase, delle frequenze locali o del movimento del pitch; di conseguenza poteva introdurre piccole discontinuità e interferenze non presenti nell'originale. Il clipping non basta a spiegare questo difetto.

**Correzione sperimentale, non ancora approvata all'ascolto:**
- Nuovo *overlap-add* a sorgente temporalmente allineata: finestra Hann da circa 70 ms, salto pari a un quarto della finestra (sovrapposizione 75%), normalizzazione stabile.
- Niente salti casuali nei punti di lettura: in questa modalità di riferimento la componente granulare conserva esattamente la forma d'onda originale, inclusa la fase. I parametri della parte modale e le due percentuali C/D restano invariati.
- **Non è ancora un time-stretch o un contatto fisico granulare completo:** è una base trasparente per stabilire se il crepitio viene dall'algoritmo precedente. Gli spostamenti temporali/pitch per la fisica richiederanno in seguito una strategia coerente di riallineamento della fase (es. WSOLA o time-stretch dedicato).
- Il vecchio algoritmo viene conservato soltanto per il confronto diagnostico: non viene perso il campione che l'utente ha ascoltato.
- Test di regressione: la versione overlap-add ricostruisce il segnale di riferimento senza nuovi salti; C/D nuovi restano diversi dalle varianti precedenti e non clippano.
- I file di confronto sono generati come artefatto temporaneo da GitHub Actions; aprire **`listen_A_C_old_C_clean_D_old_D_clean.wav`** per confrontare in ordine A originale, C vecchia, C pulita, D vecchia, D pulita, con pause di 400 ms e livelli bilanciati.

**Gate aperto:** confermare all'ascolto che C e D pulite non siano più "fritte", mantenendo l'identità sonora soddisfacente. Solo allora sperimentare con i blob fisici; non modificare `GameAudio` o il gameplay della 1.0.11 sulla base dei soli test automatici.
