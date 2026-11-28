# Report 01 — Aggregazione acustica delle interazioni
**Documento storico · proposta del 27 novembre 2026**

> Superato come piano operativo: aggregazione adottata nella baseline #238. Per lo stato vigente consultare [specifica audio](AUDIO_ENGINE_V2_MATERIAL_BLOOP.md) e [roadmap](TECHNICAL_ROADMAP.md). Il resto descrive la situazione e le proposte dell'audit originario.

## 1. Obiettivo e perimetro
Il primo stadio è concluso: l'utente ha approvato V2.3 su telefono e main è stato promosso con DSP identico alla preview. La [CI main](https://github.com/maryvellous/puzzle-game-android-/actions/runs/46060029269) è verde. Durante stress test l'utente sente una lieve frittura quando molte collisioni coincidono. Non abbiamo registrazione, profilo del dispositivo né misure di gain reduction per attribuirla con certezza. L'obiettivo della fase 2 è fare suonare la materia nel suo insieme, senza appiattire il suono di una singola interazione. Nessuna riscrittura della fisica, dei campioni o del mix approvato; nessun intervento su salvataggi o pubblicazione.

## 2. Stato verificato
- [audio/MaterialAudioEngine.java:164–175](https://github.com/maryvellous/puzzle-game-android-/blob/acf6d2b39c197584cb810c92a1d7275c8eb42f5a/app/src/main/java/com/maryvellous/lavablobs/audio/MaterialAudioEngine.java#L164-L175): massimo 24 voci corte, ammissione cronologica; nessuna aggregazione.
- [audio/MaterialAudioEngine.java:218–230](https://github.com/maryvellous/puzzle-game-android-/blob/acf6d2b39c197584cb810c92a1d7275c8eb42f5a/app/src/main/java/com/maryvellous/lavablobs/audio/MaterialAudioEngine.java#L218-L230) e [audio/MaterialAudioEngine.java:260–272](https://github.com/maryvellous/puzzle-game-android-/blob/acf6d2b39c197584cb810c92a1d7275c8eb42f5a/app/src/main/java/com/maryvellous/lavablobs/audio/MaterialAudioEngine.java#L260-L272): gli impatti e squish vengono emessi a livello di singola coppia. Il gate fisico è prezioso e resta intatto.
- [audio/MaterialAudioEngine.java:290–303](https://github.com/maryvellous/puzzle-game-android-/blob/acf6d2b39c197584cb810c92a1d7275c8eb42f5a/app/src/main/java/com/maryvellous/lavablobs/audio/MaterialAudioEngine.java#L290-L303): fusione e vetro sono gestiti dallo stesso motore; fusioni complete con percorso di ammissione separato. Il vetro usa l'attacco originale; il gel ha la propria trasformazione.
- [audio/MaterialAudioEngine.java:331–353](https://github.com/maryvellous/puzzle-game-android-/blob/acf6d2b39c197584cb810c92a1d7275c8eb42f5a/app/src/main/java/com/maryvellous/lavablobs/audio/MaterialAudioEngine.java#L331-L353): somma campioni, limitatore istantaneo a .87 con release 150 ms, slider a valle. Il limiter è protezione, non una soluzione alla densità timbrica.
- [V2GameSound.java:126–143](https://github.com/maryvellous/puzzle-game-android-/blob/acf6d2b39c197584cb810c92a1d7275c8eb42f5a/app/src/main/java/com/maryvellous/lavablobs/V2GameSound.java#L126-L143) e [V2GameSound.java:304–330](https://github.com/maryvellous/puzzle-game-android-/blob/acf6d2b39c197584cb810c92a1d7275c8eb42f5a/app/src/main/java/com/maryvellous/lavablobs/V2GameSound.java#L304-L330): osservazioni senza timestamp nel comando, svuotamento coda a raffica prima del render. La coincidenza acustica può essere artificiale. Il renderer offline, al contrario, posiziona gli eventi secondo timestamp: [SceneRenderCli](https://github.com/maryvellous/puzzle-game-android-/blob/acf6d2b39c197584cb810c92a1d7275c8eb42f5a/tools/audio_v2_java/SceneRenderCli.java#L146-L169).

## 3. Decisione architetturale
Aggregare eventi fisici *dopo* gating e deduplicazione, *prima* della creazione delle voci PCM. Non sommare otto campioni e poi comprimerli: estrarre un descrittore collettivo e renderizzare un gesto unico. Separare responsabilità senza introdurre un secondo motore. Pipeline target:

```
solver -> probe/tracker -> eventi acustici qualificati (timestamp)
       -> aggregatore per famiglia / compatibilità
       -> material voice renderer -> mixer unico -> limiter -> slider -> AudioTrack
```

Tipi: GEL_IMPACT, WALL_IMPACT, SQUISH; FUSION, LAUNCH, POP transitano direttamente nel renderer con priorità/lifecycle espliciti. Fusioni non aggregate: un Bloop originale completo per fusione reale, anche nelle cascate. Lo swoosh mantiene il suo ruolo e il suono POP resta distinto. Non trattare un urto contro il vetro come deformazione fra blob; niente suono da pressione statica.

## 4. Modello degli eventi e clustering
Descrittore immutabile e testabile: tipo, ID/coppia o blob+lato, tick e tempo di simulazione, generation/reset, raggio equivalente, intensità normalizzata ricavata dall'attuale curva, pitch/variant, eventuale posizione, durata. Non chiamare l'intensità forza reale: è proxy percettivo. Conservare l'identità delle coppie e i cancellamenti; non raddoppiare contributi ripetuti dal solver. Onset e squish sono eventi diversi; squish può usare una finestra più lunga. Il materiale non cambia.
- Finestra di prototipo: 12–20 ms per impatti, 25–40 ms per squish, a partire dal primo evento e non estesa indefinitamente dai successivi. Valori da A/B, non specifica finale. Per la prima prova usare bucket per tempo fisico, famiglia e fascia di pitch; possibile passaggio a raggruppamento per vicinanza se utile. Vetro: conservare materiale distinto e valutare lati; non occorre spazializzare in mono.
- Single-event passthrough: un evento da solo riproduce esattamente campione, rate e gain V2.3 (tolleranza solo sulla latenza). Per più eventi compatibili: peso w_i = a_i², energia proxy E = somma w_i, pitch rappresentativo = media geometrica dei rate ponderata con w_i. Caso E=0: nessuna voce. Ampiezza proposta A = min(A_max, sqrt(E)) con soft knee; alternativa A = a_max * (1 + k log(1 + E/a_max² - 1)) da confrontare. Non crescere linearmente con N; calibrare i limiti per famiglia rispetto alla V2.3.
- Durata/timbro: scegliere un attacco/coda già approvati e modulare in modo lieve la durata (inizialmente al massimo 15–25%), preservando il profilo vetro/gel; niente sintesi estranea o loop. Se un gruppo è eterogeneo per dimensione, mantenere due cluster distinti anziché annullare la variazione di pitch. Un impatto forte isolato resta leggibile. Variante deterministica dipendente dal primo evento e/o dal contatore di famiglia; niente salti discontinui.
- Flush quando scade la finestra anche senza nuovi contatti; scheduling sul clock PCM con mapping dal tempo fisico, bounded lookahead. Ritardo extra non oltre 20 ms per impatti nella prima prova. Non aspettare indefinitamente l'arrivo di altri eventi e non ammassare burst dovuti al catch-up UI.
- Stop/reset/mute/pause: annullare cluster pendenti della generation precedente; merge/POP chiudono le coppie dei genitori senza cancellare code già udibili. Policy overload separata e deterministica, non FIFO cieco; proteggere fusioni/lifecycle e mantenere budget delle voci corte. Eventi secondari scartabili solo con metrica esplicita e regola energetica. Se più cluster coesistono, non introdurre hard cuts.

## 5. Piano di integrazione (ordine vincolante)
| Fase | Deliverable | Gate |
|---|---|---|
| 0 — Baseline | Acquisire tracce e WAV stress V2.3 (telefono + JVM); log queue age/depth, dispatch/render/write, underruns delta, picco pre-limiter, gain reduction e voci per famiglia. | Riprodurre o localizzare il difetto; distinguish mix, timing e buffering. |
| 1 — Contratto eventi | Estrarre AcousticEvent e separare qualificazione da emissione. Un solo flusso timestampato per contatti, pareti, fusioni e input; test di ordering/reset. | Modalità bypass bit-equivalente sul core per singoli eventi e trace coerenti. |
| 2 — Cluster offline | Implementare aggregatore puro Java con finestre e pesi configurabili. Rendere CLI e Android consumatori dello stesso core; estendere trace a WALL/LAUNCH/POP. | Determinismo, quiete PCM zero, energia monotona limitata, identità dei materiali, nessuna fusione persa. |
| 3 — Stress e tuning | A/B baseline/cluster su stessa trace: 1, 2, 4, 8, 16 eventi, urti misti, cascate, scuotimenti prolungati e stasi. | Riduzione delle voci secondarie nelle raffiche e del tempo sotto limiter; niente degrado su singole interazioni. |
| 4 — Preview | Feature flag bypass/aggregation solo nella build di prova; niente cambi di gameplay o distribuzione. | CI verde, stesso hardware/volume, confronto ascoltato dall'utente. |
| 5 — Adozione | Se approvato, default aggregato, rimuovere percorso sperimentale non più utile, aggiornare specifica e report. | Main verificato; release separata. |

## 6. Criteri di accettazione proposti
- Zero Bloop completi da eventi non-fusione; conteggi fusioni ricevute/avviate/concluse uguali fuori mute/reset, anche con coda satura. Nessun lifecycle perso.
- Quiete fisica prolungata: PCM esattamente zero dopo le code; niente loop né retrigger da solver fermo.
- Single-event: stessa waveform V2.3 dopo allineamento temporale; vetro/gel distinguibili e volume slider invariato.
- Burst di 8 eventi compatibili in finestra: una voce collettiva (o massimo due se pitch incompatibile), non otto; livello non diminuisce aumentando energia e non supera budget. Metriche da testare su tracce vere, non solo sintetiche.
- Target iniziale: riduzione >=50% del picco di voci secondarie in scenario burst rappresentativo, minore occupazione del limiter; picco PCM <=.87; underruns delta zero nel test ripetibile su telefono; processing + rendering p99 con margine rispetto a 384 frame (circa 8.7 ms). Latenza aggiuntiva misurata e compatibile col gate della fase 2; non scambiare questi target per misure già raggiunte.
- Ascolto A/B su telefono a volume costante: la frittura deve ridursi senza perdere sincronia, timbro, dinamica, qualità delle fusioni e sensazione di massa. Valutazione utente, non solo numerica.

## 7. Rischi e dipendenze
Raggruppare troppo può rendere i blob indistinguibili e appiattire il gameplay; ritardare troppo può rompere il 4,5/5 di sincronia già osservato in V2.2. Un cluster continuo che si rinnova perpetuamente riproporrebbe il ronzio V2.0. Il limiter globale può comunque reagire a fusioni simultanee: misurare prima di cambiare il routing o il gain. La coda/timing sono prerequisiti: non usare il clustering per nascondere perdita di eventi. Vedi [audit debito tecnico](TECHNICAL_DEBT_AUDIT.md).
