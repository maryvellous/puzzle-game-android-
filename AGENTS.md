# AGENTS.md — puzzle-game-android-

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

## Progetto: Rovina Puzzle Android
- Fonti: `README.md` e codice sotto `app/`; build: JDK 17, SDK 35, Gradle 8.9, `gradle :app:assembleDebug`.
- La release 0.3.0 attuale usa tessere a scambio: NON è il jigsaw richiesto. Non presentare lo swap come soluzione finale; per il prossimo goal usare forme e incastri veri, con zoom e molte tessere, base 10×10.
- Preserva salvataggio/ripresa, preview a pressione, navigazione e schermata di vittoria già implementati; testare drag e zoom su dispositivo prima di dichiarare finito.
- Gli asset sono illustrazioni dell'utente: non modificarne il design o sostituirle senza richiesta.
