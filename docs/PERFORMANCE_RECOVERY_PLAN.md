# Lava Blob — Piano di recupero prestazioni
**28 novembre 2026 · proposta operativa, nessuna modifica applicativa autorizzata da questo documento.** Ramo `refactor/technical-debt-roadmap`, PR #27. Priorità P0 prima di tutorial, musica, bilanciamento e consegna privata.

## Baseline reale
Report fornito dall'autrice il 28/11: 1.1.0-ui-preview, livello 0, shape 14, 1080×2267. Build/commit esatto non presente nel report: non attribuire i dati a un commit specifico. Device di riferimento Redmi Note 14 5G; confermare nel prossimo export. I dati sono aggregati di una sessione reale, non benchmark controllato; non misurano GPU, thermal state o p95/p99. Scatti soprattutto quando si lanciano più blob rapidamente.

| Blob | Frame | FPS | Tick/s | Gap >25 ms | Gap max ms | Fisica/frame ms | Fisica/tick ms | Canvas CPU ms | UI/frame ms | UI max ms | Save count | Save avg/max ms |
|---|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|
| 0–4 | 4128 | 59.8 | 60.0 | 20 | 58.40 | 5.55 | 5.53 | 1.12 | 6.75 | 50.58 | 48 | 4.78/14.69 |
| 5–7 | 5561 | 58.3 | 60.0 | 170 | 79.25 | 10.39 | 10.10 | .77 | 11.24 | 77.50 | 76 | 4.61/10.75 |
| 8–10 | 2964 | 43.2 | 58.9 | 616 | 100.75 | 17.65 | 12.94 | .75 | 18.51 | 90.13 | 66 | 4.88/9.90 |
| 11–15 | 713 | 25.3 | 55.5 | 398 | 121.01 | 35.01 | 15.95 | .77 | 35.93 | 112.31 | 32 | 5.08/6.90 |

Gap >25 ms: 0.48%, 3.06%, 20.78%, 55.82%. Fisica dominante nel tempo UI misurato; costo Canvas CPU piccolo, GPU non esclusa. Salvataggi sincroni a ogni lancio e periodici (~5 ms) sono un fattore plausibile nei burst ma non la causa dominante dimostrata. SimulationClock: 60 Hz, max 4 tick/frame; ogni tick due sottopassi e cinque passaggi vincoli; più tick/frame amplificano il carico per frame, senza dimostrare da soli una spirale infinita. A 11–15 blob circa 2.19 tick/frame, budget 16.67 ms/frame a 60 Hz. Le misure non distinguono collisioni, pareti, membrane e probe: non scegliere un colpevole interno senza misurarli.

## Obiettivi e invarianti
- Sul dispositivo di riferimento, a 60 Hz: 58–60 FPS medi per fascia fino a 15 blob, ~60 tick/s (59–61 su finestre lunghe); <=8 ms medi/tick nella scena densa 11–15; gap >25 ms <2% e p95 frame interval <=20 ms, p99 <=33.4 ms nelle finestre attive ripetute (escludere pause, non scartare burst o GC). Obiettivi iniziali, non promesse; registrare anche 16+, senza tagliare artificialmente le scene.
- Miglioramento senza ridurre i 24 nodi, due sottopassi, cinque passaggi, frequenza simulazione, capacità, qualità audio o cambiare geometria, ordine delle proiezioni, scoring e RNG. Nessun limite di blob o rallentamento artificiale.
- Mantenere tracce/stati bit-per-bit nelle fixture deterministiche e PCM/replay di riferimento; ogni modifica deliberata della semantica richiede decisione separata. CI verde non equivale a 60 FPS su Android.

## Fase 0 — Baseline riproducibile e strumentazione mirata
- Conservare baseline APK e sorgente; aggiungere build SHA/version, device/refresh rate, sessione, shape, numero di drop, finestra recente (ultimi 10–20 s) oltre ai totali e timestamp relativi. Export locale da Pausa, pulsante evidente, senza telemetria.
- Inserire misure per integrate/finish, merge detection, collectProjectionPairs, solveMembrane, projectContacts, solveAdhesions, Vessel.confine/contains/closest, ContactProbe begin/observe/finish e controllo ingresso; contatori per candidati, nodi/segmenti esaminati, contatti, link, chiamate e catch-up. Aggregare in bucket preallocati, cronometrare per fase/pass non per singolo nodo salvo campionamento diagnostico separato. Misurare overhead con diagnostica on/off.
- Aggiungere istogrammi tick/frame/save, p50/p95/p99, max, frequenza frame lenti, tick persi/clamp, conteggio tick/frame e costo input/checkpoint. Tracciare anche i tempi relativi ai lanci e audio underrun/coda con diagnostica già disponibile. GPU/GC/thermal solo con strumenti Android (Perfetto/FrameTimeline, Android Studio profiler) se disponibili; non chiamare le metriche Canvas GPU.
- Fixture deterministiche a seed fisso: 0–4, 5–7, 8–10, 11–15, 16+; burst di lanci validi con cadenza definita, ammasso in movimento, ammasso fermo, shake, cascate di fusioni, pareti arrotondate e concave, infinita. Warm-up JVM prima dei confronti; stessi input/runner e scene, almeno 3 ripetizioni; riportare mediane e code, non solo medie. Sul telefono: stessa modalità, tema, refresh, condizioni termiche/power comparabili, 3 sessioni, finestre di durata e conteggio comparabili. A/B su APK isolate con firma compatibile; niente disinstallazioni non concordate.
- Exit: report leggibile e costo dominante riproducibile, baseline conservata; se i dati non isolano il costo, estendere solo la misura necessaria.

## Fase 1 — Ottimizzazioni conservative, ordinate per profilo
Una modifica per commit e benchmark, mantenendo l'ordine dei candidati e i tie-break esatti. Le seguenti sono ipotesi da selezionare dopo fase 0, non modifiche già approvate.
1. **Collisioni blob/blob** (BlobWorld): collectProjectionPairs già riusa candidati; projectContacts ricalcola bounds per coppia e pass, closestEdge scorre fino a 24 segmenti per nodo, pointInside può ripercorrere il poligono. Valutare bounds conservative riutilizzabili con invalidazione/espansione ogni mutazione, rejection spaziale, cache proprietà immutabili o indicizzazione di segmenti con aggiornamento al movimento. Non usare cache stantie dopo solveMembrane/adesioni/confinamento; mantenere tie-break e ordine delle correzioni. Confrontare ogni proiezione nei test su contatti, coincidenze, concavità e soglie.
2. **Pareti** (Vessel): shape>=14 ha 162 segmenti e già usa gruppi AABB e corridoio sicuro. Misurare quanti nodi sfuggono al corridoio, costo contains/closest e gruppi visitati. Valutare indice statico per banda Y o gerarchia AABB con lower bound conservativo; mantenere scelta edge e tie-break, normalizzazione, lato osservato dal probe e contorno identico. Test sui vertici, confini bande, concavità, ingresso e punti esterni; confronto brute-force indipendente. Non ridurre segmenti né sostituire il collider con una forma approssimata.
3. **Membrane/adesioni/probe**: verificare solvePressure/solveDistance, trigonometria e sqrt/hypot, scansioni/allocazioni e mappe del ContactProbe. Riusare solo quantità realmente invarianti durante il pass, contenitori temporanei e accumulatori; eventuale sostituzione matematica solo con equivalenza dimostrata. Il probe resta passivo, nessuna perdita o aggregazione anticipata degli eventi.
- Exit per patch: regressioni stati/tracce/PCM e test completi verdi; miglioramento ripetibile della fase e del tick, non peggioramento materiale in altre scene. Revert delle patch senza beneficio o con regressioni. Rivalutazione dispositivo dopo ogni gruppo significativo. Non forzare tutti gli interventi se il target è già raggiunto.

## Fase 2 — Salvataggio senza bloccare i lanci
GameSave.encode serializza nodi/link in JSON e usa AtomicFile; LavaView invoca saveCheckpoint sul thread UI durante i lanci, shake, POP, pausa e checkpoint periodico (GameSessionController: 1.5 s di tempo simulato). Verificare attribuzione con fase 0.
- Separare acquisizione snapshot consistente (copia immutabile delle primitive sul thread proprietario del mondo) da serializzazione e scrittura atomica su singolo worker. Mai leggere un BlobWorld mutabile da thread concorrente.
- Coalescere solo checkpoint superati in attesa (latest wins per generazione), mantenere ordine/barriere di clear, restart, game over, cambio sessione, Home e lifecycle; evitare resurrezione di salvataggi obsoleti. Definire esplicitamente la finestra di perdita in caso di process kill; non peggiorare la garanzia approvata senza decisione utente. Se serve, flush esplicito nei confini lifecycle con timeout misurato, non in input.
- Test round-trip compatibile, rapid drop, pause/resume, restart, process kill, errore disco, cancellazione, race e finalizzazione. Obiettivo costo checkpoint UI <1 ms medio e p95 <2 ms (da validare).
- Exit: meno picchi durante burst, salvataggi integri, record/sblocchi invariati. Se asincronia non necessaria, mantenere soluzione più semplice e misurata.

## Fase 3 — Pacing e architettura, solo se target mancato
- Separare costi di singolo tick da catch-up, misurare ritardi input, sim time vs wall time e frame intervals. Provare pacing legato a vsync senza cambiare STEP, sottopassi, MAX_STEPS o countdown; evitare skip di tick o slow motion come soluzione.
- Se il solver ottimizzato resta troppo costoso o blocca UI, progettare prototipo isolato: unico thread proprietario di BlobWorld, coda comandi ordinata/timestamped, snapshot render immutabili preallocati/double-buffer, publish atomico, rendering con interpolazione solo visiva se utile, audio eventi dalla timeline fisica; pause/reset e save con barriere. Non parallelizzare proiezioni dipendenti in-place.
- Valutare contention, latenza input→sim→render, drop di snapshot/eventi, stabilità memoria, audio, determinismo e lifecycle. Non adottare se il tick singolo non rientra nel budget o se sposta soltanto il problema. Cambi semantici, solver nativo o algoritmo radicale sono ultima fase da approvare separatamente.

## Fase 4 — Collaudo e gate
- Replay e CI completi (debug, uiPreview, release, fixture dense, confronto stati/trace/PCM, test lifecycle e save), preservare tutti i gate esistenti.
- APK uiPreview isolata con identificatore e commit visibili, confronti sul Redmi: lanci rapidi, livello 0 shape 14, recipienti densi/concavi, infinita, shake, mute e volume normale, fusione/quiet, pausa/ripresa, game over e restart. Misure almeno 3 sessioni, dati completi, verifica percettiva autrice. Se possibile secondo device più debole.
- Tabella A/B: FPS, tick/s, p95/p99 gap, gap>25 ms, tick medio/p95/p99, costi per fase e checkpoint, underrun/coda e comportamento fisico. Registrare limiti e regressioni. Se target raggiunto, non fare ulteriori refactor per principio.
- Nessun merge in main, release o distribuzione senza collaudo e OK esplicito. Logo già modificato ma ancora da approvare; non cambiare estetica durante questa fase.

## Ordine operativo e decisioni
F0 diagnostica → F1 hotspot dominante (collisioni/pareti/probe secondo dati) → F2 salvataggi se rilevanti → F3 solo se necessario → F4 collaudo. Ogni iterazione lascia commit, evidenze e rollback chiari. La prossima attività è F0, non un refactor generalizzato. Aggiornare il presente piano con risultati e mantenere TECHNICAL_ROADMAP come indice.
