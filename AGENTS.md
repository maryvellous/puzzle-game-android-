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

## Motore audio V2 — documentazione di riferimento ufficiale

- **Faro:** **«Un sistema in cui il comportamento della materia diventa suono.»**
- **[SPECIFICA VIGENTE V2](docs/AUDIO_ENGINE_V2_MATERIAL_BLOOP.md)**: la **fusione è l'evento principe** e riproduce un Bloop originale **completo**. Ogni altro suono usa **frammenti o trasformazioni degli stessi quattro Bloop**, ad esempio attacco/coda senza il nucleo centrale, con inviluppi e modulazione da fisica. Non generare un nuovo Bloop integrale tramite C4, non riutilizzare C4 come materiale sorgente definitivo.
- **Architettura da progettare**: Contact Probe passivo sul contatto reale delle membrane, Contact Tracker con stato delle coppie, Material Bank (Bloop intero per fusioni, segmenti per altri fenomeni), Scene Mixer unico senza frittura o tagli bruschi, output Android. Dimensione→pitch e durata sempre fisicamente guidati.
- **Stato dei test:** APK Audio Test, Audio Size Test e Audio Mix Test **storiche**; l'utente le disinstallerà. Audio Size Test era promettente nel legame dimensione/pitch; Mix Test ha peggiorato gli impatti senza risolvere il disturbo. Le PR #23 e #24 sono sperimentali **non destinate al merge automatico**. La release 1.0.11 resta intatta. **Niente nuove APK o DSP prima di validare Contact Probe, grammatica Bloop e mix offline.**
- **La [specifica V1](docs/PROCEDURAL_SOUND_DESIGN.md) e le fasi C1–C4.1 seguenti sono solo archivio storico**: qualunque istruzione di risintetizzare la fusione o assumere C4 come riferimento definitivo è superata.

## Vecchi esperimenti procedurali — archivio (non autoritativo)
- **Specifica storica V1, NON vigente:** [`docs/PROCEDURAL_SOUND_DESIGN.md`](docs/PROCEDURAL_SOUND_DESIGN.md). La sola specifica vigente è [`docs/AUDIO_ENGINE_V2_MATERIAL_BLOOP.md`](docs/AUDIO_ENGINE_V2_MATERIAL_BLOOP.md). [Fasi A/B](docs/PROCEDURAL_AUDIO_LAB_2026-11-21.md) disponibili e ascoltate: timbro complessivamente approvato, leggero disturbo ancora aperto. [Fase C1 a due blob](docs/PROCEDURAL_AUDIO_TWO_BLOB_PROTOTYPE.md) bocciata all'ascolto per urto e compressione; [revisione C2](docs/PROCEDURAL_AUDIO_C2_FEEDBACK_REWORK.md) non convincente; [laboratorio C3](docs/PROCEDURAL_AUDIO_C3_SUBTLE_MIX.md) con urto attenuato, sfregamento derivato solo dalla coda quasi impercettibile, fusione sempre dominante. C3 resta in attesa d'ascolto. **Il motore procedurale NON è implementato nel gioco**: la 1.0.11 continua a usare `SoundPool` e gli asset classici.
- [Fase C4](docs/PROCEDURAL_AUDIO_C4_ORGANIC_CONTACT.md): impatto C3 quasi giusto ma un po' grave; frizione ancora poco viscosa; compressione C3 bocciata (effetto laser). Due urti leggermente più acuti (+9/+16%), due drag viscosi da coda C3, compressione A volutamente silenziosa o B appena percettibile. **Nessun effetto approvato prima di ascolto e nessun aggiornamento APK.**
- [Correzione C4.1](docs/PROCEDURAL_AUDIO_C41_PREVIEW_FIX.md): nel C4 la fusione veniva aggiunta erroneamente a ogni montaggio di confronto, falsando il giudizio. L'utente gradisce compression B e friction adhesive: mantenerle **byte-identiche**. Urti C4 non udibili: due livelli di prova 32%/40% del picco fusione, ancora inferiori alla fusione. Mai aggiungere un Bloop nei montaggi non-fusione. Test automatici e ascolto prima dell'integrazione.
- **Obiettivo storico riformulato nella V2:** la fusione riproduce il Bloop originale intero; urti, compressioni, attriti, lancio e POP condividono il vocabolario acustico derivato dai quattro Bloop, con modulazione fisica senza modificarne il solver.
- Non cancellare i Bloop e **non sostituire né troncare quelli completi durante una fusione reale**. Non pubblicare nuove release soltanto perché esiste la specifica. I prototipi a due blob e C4 sono solo esperimenti storici. Il lieve disturbo residuo e il lancio ripetuto restano problemi aperti da affrontare col mixer unico.
- Conservare la preferenza SFX `sound_effects_enabled_v1`; musica futura con mute distinto. Le soglie/volume numerici in `AudioPolicy` sono valori dell'implementazione classica, non vincoli per il futuro motore.
- Per ogni iterazione procedurale: probe fisico passivo, deduplica contatti nei cinque passaggi del solver, test audio/CPU e confronto sul telefono prima di qualsiasi rollout.
