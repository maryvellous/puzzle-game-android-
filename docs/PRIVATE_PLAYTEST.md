# Progettazione UI/UX, level design e collaudo privato
**Aggiornamento 28 novembre 2026 — passaggio di sessione.**

Obiettivo: consegnare una build Android collaudata a tre tester scelti dall'autrice, senza pubblicazione pubblica.

## Stato operativo e prossimo intervento

Ramo `refactor/technical-debt-roadmap`, [PR #27](https://github.com/maryvellous/puzzle-game-android-/pull/27) aperta, non integrata. Ultimo commit applicativo `29624f4a4e0079ebd6a565e8667c92d7011c1a3a` (logo); ultimo APK confermato da `96d4493fe66fe06dd16aab72e590a1ee4b200ac4`. [UI Preview 4, test e build verdi](https://github.com/maryvellous/puzzle-game-android-/actions/runs/46147627037) · [APK (ZIP Actions)](https://github.com/maryvellous/puzzle-game-android-/actions/runs/46147627037/artifacts/15264287233) · [regressione completa verde](https://github.com/maryvellous/puzzle-game-android-/actions/runs/46147627030). Non confondere la build verificata con il collaudo sul telefono: l'ultimo feedback ricevuto riguarda UI Preview 3.

**Priorità 1 — collaudare il nuovo logo Home.** In `29624f4` è stata sostituita la costruzione da font con forme morbide esplicite, otto lettere con lobi e contatori, quattro colori del tema e deformazione animata. La ricerca ha verificato il primo commit Home `fef76ad`, i rami `feature/lava-blobs-android` e `perf/lava-blobs-first-pass-20261103` e la condivisione `b204e70`: tutti usano contorni di font, quindi il logo originale composto da blob non è stato trovato. La nuova versione è una ricostruzione, non un recupero. Build e verifica visiva delle tre palette e più frame ancora da confermare. La fisica, audio, scoring e layout non sono stati modificati.

**Requisito storico del logo (superato per implementazione, non per approvazione).** L'autrice ha chiarito che il titolo precedente era composto da blob, non da caratteri di font deformati. La versione rifiutata `BlobTitle.drawHome` usava `getTextPath` e `PathMeasure`: è proprio l'approccio rifiutato. Il risultato appare storto e non percepibilmente animato. Cercare il titolo originale nella storia Git, nelle vecchie implementazioni e nelle eventuali risorse disponibili prima di disegnarne uno nuovo. Non dichiarare di averlo recuperato se non è stato trovato. Ripristinare l'identità materiale: lettere costruite da forme/blob morbidi, leggibili anche da ferme, non wordart. Usare 3–4 colori della palette attiva per il logo e un altro per lo sfondo, animazione lenta ma visibile, deformazione lieve e senza rotture. Rispettare riduzione animazioni di sistema; verificare le tre palette e più frame della sequenza. Nessuna approvazione di un nuovo logo è implicita. Dopo la correzione, compilare e consegnare APK di anteprima, non main.

**Priorità 2 — collaudo di fluidità e spaziatura.** UI Preview 3: l'autrice apprezza moltissimo il gioco, ma riferisce rallentamento già con 8–10 blob e respiro verticale ancora insufficiente. In `96d4493` sono stati implementati 22 unità virtuali tra recipiente e lanciatore, 18 tra lanciatore e console, mantenendo la scala se possibile; non ancora verificati dall'autrice. In pausa, nelle build debug/preview, `Copia dati prestazioni` esporta misure locali per fasce 0–4 / 5–7 / 8–10 / 11–15 / 16+: intervalli frame, passi simulati, fisica/tick, Canvas CPU, tempo UI e salvataggi. Far giocare fino al rallentamento, copiare e analizzare il report prima di ulteriori ottimizzazioni; non attribuire automaticamente il problema all'anteprima o alle geometrie. Nessun invio automatico.

**Gate:** nessun merge in main prima dell'OK esplicito dell'autrice dopo il collaudo. La build attuale è isolata (`com.maryvellous.lavablobs.uipreview`), non sovrascrive il gioco principale. Per aggiornare la preview verificare compatibilità della firma; non imporre disinstallazioni che cancellino i dati. La nuova geometria ha invalidato le partite storia salvate prima della revisione dei raccordi, non record/sblocchi.

## Decisioni di prodotto e stato dei quattro filoni

Il level design è integrato nei quattro filoni, non una fase isolata.

| Filone | Stato attuale e decisioni |
|---|---|
| Home | Implementata: carosello verticale con snap animato, suggerimento «Scorri in basso per gli altri livelli» che scompare dopo il primo avanzamento, console bianca fissa, switch Infinita, Temi (popup dismissibile toccando fuori), aiuto, esportazione dati. Continua partita a tutta larghezza nella console, sopra i tre comandi, solo con salvataggio. Miniature con geometrie effettive. Nuovo logo in attesa di collaudo come sopra. |
| Forme | 12 livelli storia, profili 14–25, raccordi ellittici reali su entrambe le basi, 80 fasce/162 segmenti; disegno e collisioni condividono il contorno. Tratto d'ingresso piatto funzionale. Fixture 0–13 e tre recipienti Infinita conservati. Ciambella rinviata su richiesta: non introdurre ostacoli interni ora. Punteggi/obiettivi invariati e provvisori; da bilanciare con playtest. |
| HUD | Console scelta dall'autrice. [Mockup definitivo](mockups/console.html). Superficie bianca neutra indipendente dal tema; pulsanti con colori intermedi (mai colore 1); prossimo blob adiacente all'ingresso, indicatore chiaro e countdown; punteggio/obiettivi; Scuoti e Extra o POP sopra la scala delle nove taglie, senza numeri, contorni o target evidenziato. Pausa effettiva e conferma riavvio, aiuto, risultato e prossimo livello; suoni regolabili, volume musica predisposto ma nessuna traccia musicale. Adattamento visivo con trasformazione touch inversa, senza alterare capacità fisica. |
| Tutorial/onboarding | Aiuto sintetico già implementato: «Unisci abbastanza blob per raggiungere l'obiettivo prima che finisca lo spazio», scala completa e comandi. Suggerimento Home presente. Tutorial contestuale giocabile del primo vasetto ancora da progettare, con skip/ripeti, eventi reali e POP solo Infinita. Non introdurlo incidentalmente durante il fix logo. |

## Evidenze e limiti

- Baseline audio/fisica approvata `43aca8e` (#238), consolidamento `5569e3c` (#253).
- `1a7d454`: mockup Console scelto. `a3bc41a` / `0d9679d`: nuove Home, HUD e forme. `03bf694`: suggerimento, wordmark deformato (respinto) e layout adattivo. `bde5213`: raccordi arrotondati. `b063794`: Continua nella console, spaziatura intermedia, ricerca pareti a gruppi e corridoi conservativi; controllo ingresso per la mira solo se attiva.
- [CI UI #5](https://github.com/maryvellous/puzzle-game-android-/actions/runs/46105070804): stati e trace audio delle tre scene dense arrotondate identici a `bde5213`. Prestazioni JVM contrastanti (p99 vecchio → nuovo in ms: 4,908→6,570; 4,935→6,446; 5,824→5,400). Nessun miglioramento generale provato, nessun benchmark Android.
- `96d4493`: maggiore respiro verticale e diagnostica dispositivo. Build e regressione verdi, APK sopra. Titolo sostituito in `29624f4` dopo la Preview 4; problemi di fluidità ancora aperti.
- L'ultima revisione del solver resta quella di `b063794`; non modificare fisica, motore audio, capacità o scoring per un fix estetico. Preservare confronto bit-per-bit e gate di CI.

## Regressione audio e lifecycle

- Preview .audioaggregation: banner AUDIO · REFINED CLUSTER · PRONTO; build normale senza banner.
- A volume costante: lancio, vetro, gel, squish, fusioni/cascate, POP, quiete 15–20 s, contenitore pieno e shake. Confrontare con #238.
- Slider 100/50/0%, mute, pause/resume, Home, restart, checkpoint e game over; nessun evento stantio.
- In presenza di problemi, log LavaPerf e LavaV2Audio nello stesso intervallo; vedi [stato tecnico](TECHNICAL_ROADMAP.md). Nessun trace remoto.

## Gate della build per i tester

- UI/level design approvati, CI verde, collaudo finale sul dispositivo: installazione, lancio e shake, late game, volume/mute, pausa e ripresa, checkpoint, chiusura, game over, risultati e sblocco livelli.
- Incrementare versionCode e definire versionName prima della consegna; verificare package e compatibilità della firma. Nessuna disinstallazione non concordata: può cancellare i dati.
- Verificare i quattro secret di signing persistente e il certificato; conservarne backup sicuro fuori repo. Non condividere chiavi/password. La build firmata non è ancora verificata con i secret reali.
- Su main eseguire manualmente `Prepare signed Lava Blob Android` con `publish_release=false` solo dopo autorizzazione. Il workflow produce APK non debuggabile e SHA256SUMS.txt; non crea un GitHub Release. Il proprietario scarica lo ZIP, verifica il checksum, estrae l'APK e lo invia direttamente ai tester. Un artefatto Actions non è un canale di distribuzione privato garantito (accesso dipende dai permessi del repository).
- Per aggiornamenti usare lo stesso package e certificato, versionCode crescente. Non distribuire una preview debug come versione definitiva. Nessun upload pubblico automatico, nessuna telemetria aggiuntiva.

## Feedback dei tre tester

Lasciare giocare senza spiegare tutte le meccaniche in anticipo; raccogliere impressioni spontanee e problemi concreti.

- Dispositivo e versione Android; livello/modalità e versione APK.
- Cosa era chiaro o poco intuitivo? Dove si sono bloccati? Quanto è sembrato facile/difficile?
- Fluido? Suono gradevole? Scatti o crepitii? In quale scena?
- Se si verifica un bug, descrivere cosa è successo e come riprodurlo; screenshot o video solo se utile e volontario.
- Eventuale esportazione manuale dei dati partita (JSON locale), solo con consenso; nessuna raccolta automatica.

Classificare bug bloccanti, problemi UX, bilanciamento e preferenze personali. Correggere prima i difetti riproducibili; tenere separati feedback e idee nuove.
