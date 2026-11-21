# AGENTS.md — Lava Blobs

## Divisione del lavoro
- ChatGPT in chat gestisce direttamente su GitHub lettura/scrittura del codice, documentazione, branch, commit, PR, review e controlli CI, anche per modifiche consistenti.
- Codex serve per browser interattivo, avvio dell'app, build/test locali, debugging runtime, verifiche visive e collaudi operativi autorizzati: non consumarlo per operazioni fattibili via GitHub.
- Quando servono entrambi: ChatGPT implementa, Codex verifica, ChatGPT corregge su GitHub. Codex modifica codice se necessario al test locale o se espressamente richiesto.
- Non delegare all'utente operazioni che puoi svolgere con gli strumenti disponibili.

## Goal e scelte
- Completa il risultato richiesto end-to-end, senza approvazioni di micro-task già definiti.
- Autonomia tecnica, non progettuale: rispetta i comportamenti e i compromessi approvati. Chiedi solo se una decisione irrisolta cambia prodotto, sicurezza, dati o scope.
- Mantieni i cambiamenti focalizzati; niente refactor estranei, nuove dipendenze o servizi inutili, astrazioni preventive, integrazioni simulate o procedure inventate.
- Preserva ciò che funziona; evita costi ricorrenti e riscritture senza necessità dimostrata.
- Leggi le specifiche pertinenti e segui i pattern del progetto, senza duplicare la documentazione in questo file.

## Verifica, Git e sicurezza
- Usa test proporzionati al goal; indica test eseguiti e risultati, oppure «non eseguiti». La CI verde non prova da sola il funzionamento in browser o sul dispositivo.
- Distingui scritto, committato, verificato in CI, testato localmente e distribuito. Codex deve riportare passi di riproduzione ed evidenze concrete.
- Prima di intervenire controlla stato del branch, PR aperte e modifiche esistenti; non sovrascrivere lavoro non correlato. Commit e PR mirati.
- Niente force-push, merge o deploy in produzione senza incarico esplicito. Non fare migrazioni distruttive, cancellazioni di dati o modifiche a sistemi live senza autorizzazione.
- Non registrare o divulgare segreti, token, credenziali o dati personali; nei test usa dati sintetici.

## Progetto: Lava Blobs Android
- Unico progetto attivo nella radice del repository: `app/` è Lava Blobs, package `com.maryvellous.lavablobs`; versionName corrente 1.0.6.
- Fonti: `README.md` e codice in `app/`; build JDK 17, SDK 35, Gradle 8.9; `gradle :app:testDebugUnitTest :app:assembleDebug :app:assemblePreview`.
- Preservare fisica soft-body, membrane, salvataggi, 12 recipienti, palette e suoni originali; niente cambi gameplay impliciti durante la manutenzione della repository.
- La release principale è `lava-blob-v1.0.6`; la workflow di pubblicazione è **manuale** e non va avviata senza incarico dell'utente. Per configurare una chiave stabile servono i secret GitHub previsti nel README. Non distribuire automaticamente nuovi APK.
- I file Android dell'originario Rovina Puzzle sono rimossi dal branch principale. I tag/release storici restano nella cronologia Git; non riaggiungere Rovina né confonderlo con il puzzle web di Scintilla nel diverso repository `junior-games-scintilla`.
- L'utente gestisce personalmente la rinomina futura del repository; non rinominarlo senza richiesta esplicita.
