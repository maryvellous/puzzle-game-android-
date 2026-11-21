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
- Unico progetto attivo nella radice del repository: `app/` è Lava Blobs, package `com.maryvellous.lavablobs`; versionName corrente 1.0.11.
- Fonti: `README.md` e codice in `app/`; build JDK 17, SDK 35, Gradle 8.9; `gradle :app:testDebugUnitTest :app:assembleDebug :app:assemblePreview`.
- Preservare fisica soft-body, membrane, salvataggi, 12 recipienti, palette e suoni originali; niente cambi gameplay impliciti durante la manutenzione della repository.
- La release principale è `lava-blob-v1.0.11`; la workflow di pubblicazione è **manuale** e non va avviata senza incarico dell'utente. Per configurare una chiave stabile servono i secret GitHub previsti nel README. Non distribuire automaticamente nuovi APK.
- I file Android dell'originario Rovina Puzzle sono rimossi dal branch principale. I tag/release storici restano nella cronologia Git; non riaggiungere Rovina né confonderlo con il puzzle web di Scintilla nel diverso repository `junior-games-scintilla`.
- L'utente gestisce personalmente la rinomina futura del repository; non rinominarlo senza richiesta esplicita.

## Nuovo sound design procedurale — documentazione di riferimento
- Specifica ufficiale: [`docs/PROCEDURAL_SOUND_DESIGN.md`](docs/PROCEDURAL_SOUND_DESIGN.md). La [fase A e i prototipi offline B](docs/PROCEDURAL_AUDIO_LAB_2026-11-21.md) sono disponibili; **il motore procedurale NON è implementato nel gioco**. La build attuale 1.0.11 continua a usare `SoundPool` e gli asset classici.
- Obiettivo: un solo modello acustico ibrido, fondato sui quattro Bloop di floraphonic, per **fusioni incluse**, urti, compressioni, attriti, lancio e POP; il suono deve descrivere la fisica delle membrane senza modificarla.
- Non cancellare i Bloop, non sostituire i suoni di fusione riusciti e non pubblicare nuove release soltanto perché esiste la specifica. Le misure PCM sono state eseguite; il **gate d'ascolto** delle ricostruzioni A/B/C/D resta da superare prima della fase a due blob.
- Conservare la preferenza SFX `sound_effects_enabled_v1`; musica futura con mute distinto. Le soglie/volume numerici in `AudioPolicy` sono valori dell'implementazione classica, non vincoli per il futuro motore.
- Per ogni iterazione procedurale: probe fisico passivo, deduplica contatti nei cinque passaggi del solver, test audio/CPU e confronto sul telefono prima di qualsiasi rollout.
