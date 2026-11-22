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

## Sound design V2 — riferimento ufficiale (22 novembre 2026)

**FARO: «Un sistema in cui il comportamento della materia diventa suono».**

- **Unica specifica normativa:** [docs/AUDIO_ENGINE_V2_MATERIAL_TO_SOUND.md](docs/AUDIO_ENGINE_V2_MATERIAL_TO_SOUND.md). La [specifica V1](docs/PROCEDURAL_SOUND_DESIGN.md) è **ARCHIVIATA/SUPERATA**, anche quando vi siano descrizioni dettagliate dei precedenti metodi. Le fasi C1–C4.1 sono archivio di ricerca, non base per la nuova generazione.
- **Fusione = evento principe:** quando BlobWorld rileva una vera fusione, riprodurre uno dei **quattro Bloop ORIGINALI COMPLETI**, parte centrale inclusa. Non tagliare, ricostruire o sostituire il Bloop della fusione per adattarlo all'architettura del sintetizzatore. Moderata modulazione del playback secondo la dimensione solo se approvata.
- **Tutti gli altri effetti di materiale** (urti, sfregamento, deformazione, distacco) si **DERIVANO DAI MEDESIMI Bloop**, mediante attacco, coda, o attacco+coda **senza la parte centrale**; trasformati e modulati da vera fisica delle membrane. Niente effetto «laser», niente 4 timbri creati indipendentemente, niente Bloop intero riprodotto accidentalmente fuori dalle fusioni.
- **La materia guida il suono:** Contact Probe passivo sui contatti reali particella–segmento, tracker persistente per coppia, velocità locali normali e tangenziali, memoria di contatto, deformazione e dimensione. Non considerare i legami Adhesion condizione necessaria per lo sfregamento. Non mutare la fisica per ragioni acustiche. Non trattare cinque passaggi del solver come cinque eventi.
- **Un unico mixer PCM controllato:** fusioni predominanti, sfregamento collettivo e continuo, inviluppi/crossfade e protezione dei picchi senza tagli bruschi. Il crepitio va diagnosticato misurando sovrapposizioni, discontinuità, clipping, underrun e campioni. Il lancio a raffica non deve accumularsi né sovrastare le fusioni.
- **Risultato degli esperimenti Android:** Audio Test promettente; Audio Size Test conferma piccoli acuti/rapidi e grandi gravi/lenti ma il mix resta disturbato e lo sfregamento non emerge; Audio Mix Test peggiorativa. L'utente intende disinstallare **le tre APK test** dal telefono. Mantenerne traccia storica, non creare o distribuire nuove APK per questa decisione.
- **Stato del codice in main:** release normale 1.0.11 invariata, motore V2 ancora da sviluppare. Le PR sperimentali Android aperte sono archivio e **non vanno fuse in produzione** soltanto perché esiste questa specifica.
- **Ordine di lavoro:** (A) misure e tracking contatti; (B) analisi/segmentazione dei quattro Bloop e prova degli estratti; (C) sintetizzatore offline da trace; (D) mixer e stress test; (E) solo dopo, APK diagnostica Android con prova reale; (F) approvazione prima di produzione. Confronti audio non-fusione MAI accompagnati da Bloop completo implicito.
- **Compatibilità:** conservare originali e licenze floraphonic, tasto SUONI/MUTO e preferenza sound_effects_enabled_v1; musica futura separata. Non cancellare gli esperimenti storici. Niente servizi online o abbonamenti per l'audio.
