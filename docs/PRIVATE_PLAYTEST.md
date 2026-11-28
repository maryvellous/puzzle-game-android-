# Finitura e collaudo privato

**Obiettivo:** completare la UI, bilanciare i livelli e consegnare una build Android a tre tester scelti dall'autrice (familiari/compagno). Non è un lancio pubblico.

## 1. Finitura UI

- Inventariare Home, selezione livelli e temi, modalità infinita, HUD, launcher/drag, POP, pausa/ripresa, game over e risultati.
- Definire navigazione, gerarchie, stati dei controlli, feedback, leggibilità e dimensioni touch; provare diverse proporzioni schermo.
- Separare i cambiamenti di presentazione da modifiche a solver, audio, progressi e salvataggi. Preservare baseline #238.
- Eventuale musica è una scelta di prodotto distinta, non una condizione del consolidamento tecnico.

## 2. Level design

`GameSpec` contiene livelli, forme e obiettivi. I valori attuali sono provvisori. Valutare progressione, difficoltà, soglie, leggibilità dell'obiettivo e modalità infinita con partite reali; registrare modifiche e motivazioni. Non introdurre livelli o cambiare punteggi durante un refactor.

Se un cambiamento intenzionale a forme, parametri o livelli modifica la fixture di regressione, aggiornare esplicitamente lo scenario di test e confrontare i nuovi risultati: non disabilitare i gate per ottenere una CI verde. Mantenere un riferimento fisso per la fisica e l'audio approvati.

## 3. Gate della build per i tester

- UI/level design approvati, CI verde, collaudo finale sul dispositivo: installazione, lancio e shake, late game, volume/mute, pausa e ripresa, checkpoint, chiusura, game over, risultati e sblocco livelli.
- Incrementare versionCode e definire versionName prima della consegna; verificare package e compatibilità della firma. Nessuna disinstallazione non concordata: può cancellare i dati.
- Verificare i quattro secret di signing persistente e il certificato; conservarne backup sicuro fuori repo. Non condividere chiavi/password. La build firmata non è ancora verificata con i secret reali.
- Su main eseguire manualmente `Prepare signed Lava Blob Android` con `publish_release=false` solo dopo autorizzazione. Il workflow produce APK non debuggabile e SHA256SUMS.txt; non crea un GitHub Release. Il proprietario scarica lo ZIP, verifica il checksum, estrae l'APK e lo invia direttamente ai tester. Un artefatto Actions non è un canale di distribuzione privato garantito (accesso dipende dai permessi del repository).
- Per aggiornamenti usare lo stesso package e certificato, versionCode crescente. Non distribuire una preview debug come versione definitiva. Nessun upload pubblico automatico, nessuna telemetria aggiuntiva.

## 4. Feedback dei tre tester

Lasciare giocare senza spiegare tutte le meccaniche in anticipo; raccogliere impressioni spontanee e problemi concreti.

- Dispositivo e versione Android; livello/modalità e versione APK.
- Cosa era chiaro o poco intuitivo? Dove si sono bloccati? Quanto è sembrato facile/difficile?
- Fluido? Suono gradevole? Scatti o crepitii? In quale scena?
- Se si verifica un bug, descrivere cosa è successo e come riprodurlo; screenshot o video solo se utile e volontario.
- Eventuale esportazione manuale dei dati partita (JSON locale), solo con consenso; nessuna raccolta automatica.

Classificare bug bloccanti, problemi UX, bilanciamento e preferenze personali. Correggere prima i difetti riproducibili; tenere separati feedback e idee nuove.
