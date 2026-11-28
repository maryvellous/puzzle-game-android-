# Progettazione UI/UX, level design e collaudo privato

**Obiettivo:** completare il gioco e consegnare una build Android a tre tester scelti dall'autrice. Non è un lancio pubblico.

## Quattro filoni di progettazione

| Filone | Ambito | Punto di partenza |
|---|---|---|
| Home | Identità, navigazione, ripresa, selezione livelli/modalità, temi e progressi; rendere chiara la scelta della partita. | HomeView, MainActivity, ProgressStore |
| Forme dei contenitori | Silhouette, riconoscibilità, spazio utile, imboccatura, concavità e difficoltà; progettare insieme forma e comportamento dei livelli. | Vessel, GameSpec |
| HUD di gioco | Punteggio, obiettivi e avanzamento, prossimo blob, controlli di lancio/drag, shake, POP, volume, game over e risultati; gerarchia e leggibilità senza ostacolare il campo. | LavaView, GameSessionController |
| Tutorial/onboarding | Prima esperienza, apprendimento graduale delle meccaniche, feedback contestuali, introduzione di forme, obiettivi e controlli. | Da progettare a partire dal gioco reale |

Il level design non è una fase isolata: forme e difficoltà, obiettivi e HUD, progressione e onboarding si definiscono insieme. I quattro filoni sono una suddivisione del lavoro, non un ordine di esecuzione vincolante. Valori di GameSpec (12 livelli e 3 endless) provvisori; verificare progressione e bilanciamento con partite reali. Le decisioni estetiche e di gameplay sono ancora da prendere.

Preservare solver e audio #238/#253; se le geometrie o gli obiettivi cambiano intenzionalmente, aggiornare e motivare le fixture, mantenendo il riferimento fisico approvato. Provare proporzioni schermo diverse, leggibilità e dimensioni touch. Musica eventuale: scelta di prodotto distinta.

## Audit UI/UX — proposte da discutere (28 novembre)

Base: sorgente b73e97e, filmato playtest #238 (HUD/vasetto); Home e altre forme valutate dal codice, non da screenshot o test utente. Nessuna scelta grafica o di gameplay ancora approvata. Identità da preservare: materia elastica, colori esatti delle tre palette, fondo scuro, vetro neutro, titolo deformabile, UI morbida, feedback fisico/sonoro. Rendere la materia protagonista, evitare decorazioni estranee.

| Filone | Evidenza attuale | Direzione proposta |
|---|---|---|
| Home | HomeView: griglia 6 livelli/pagina con swipe orizzontale e scroll verticale; miniature da Vessel, testi piccoli; temi e copia dati nel flusso principale. | Collezione di recipienti con silhouette grandi, progressi espliciti, azione primaria Continua/prossimo livello, modalità infinita riconoscibile, impostazioni/export separati, navigazione verticale prevedibile. |
| Forme | GameSpec: 12 livelli/11 forme, Gola stretta e Clessidra stesso shape 3; Vessel usa stesso poligono per grafica e collisioni. Geometrie campionate e altezze nominali fisse ma adattabili allo spazio. | Archetipi fisici (regolare, pancia, strettoia, asimmetria, concavità), progressione di comportamenti; misurare capacità, min width, spazio lancio inferiore, frequenza blocco, difficulty e obiettivi. Niente collider invisibili, no cambi solver impliciti. |
| HUD | LavaView: wordmark e grandi contatori in alto, obiettivo statico e extra n/m; riavvio su ACTION_DOWN, tap anche su parti informative può avviare mira/lancio, esito con sole azioni Riprova/Home. Ghost/linea non esplicitano sempre validità, countdown testuale. | Header compatto, obiettivo/progresso e extra leggibili, campo protagonista; vero menu pausa e conferma restart, hitbox separate, ghost coerente con canLaunch e ingresso inferiore, feedback blocco vicino all'ingresso, Prossimo livello. Volume completo in pausa, mute rapido se utile. Non aumentare capacità del recipiente per ragioni puramente estetiche. |
| Onboarding | Istruzione iniziale sul lancio, etichette PROSSIMO/TRASCINA/SCUOTI, messaggi per blocco/POP; nessun tutorial strutturato rilevato nel flusso Home/MainActivity/LavaView. | Primo vasetto come tutorial giocabile, suggerimenti a evento: lancio dal basso, fusione di pari taglia, shake, obiettivo, recupero ingresso; POP solo in endless. Salta/ripeti, aiuto contestuale, riconoscere azioni reali, niente tutorial che blocchi o sovrascriva una partita. |

Priorità proposta: correggere azioni accidentali e feedback di lancio/HUD; definire archetipi e progressione prima di fissare schede/miniature Home e contenuti onboarding; esplorazione visiva in parallelo. Verificare leggibilità, contrasto e dimensioni touch su più schermi; Canvas richiede valutazione di accessibilità semantica, non solo palette. Misurare con tre tester: primo lancio, prima fusione, comprensione shake, obiettivi, recupero ingresso, passaggio livello; osservazione spontanea senza spiegazioni preventive. Proposte, non task di implementazione autorizzati.

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
