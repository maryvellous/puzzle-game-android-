# AGENTS.md — Lava Blobs

## Lavoro
- Consultare main e documentazione prima di modificare; distinguere codice scritto, CI, collaudo utente e distribuzione. Commit mirati, test proporzionati.
- Usare GitHub per codice/decisioni; Codex per esecuzione locale e dispositivo se necessario. Non delegare attività eseguibili.
- Non fare force-push, merge, pubblicazione o cambi di prodotto senza incarico. Non esporre segreti né cancellare dati utente. La storia Git conserva esperimenti e documenti superati.

## Prodotto
- Repo maryvellous/puzzle-game-android-; app/ è il solo progetto. Main 1.1.0/Audio V2.3 approvato il 27/11/2026; CI 46060029269 verde. Ultima release pubblicata 1.0.11. Primo stadio audio concluso. Nuovi branch da main; nessun lavoro aggiuntivo sui salvataggi richiesto.
- [Specifica canonica audio](docs/AUDIO_ENGINE_V2_MATERIAL_BLOOP.md); [piano fase 2](docs/AUDIO_AGGREGATION_PLAN.md); [audit](docs/TECHNICAL_DEBT_AUDIT.md); [collaudo](docs/PLAYTEST_AUDIO_V2.3.md).
- Preservare fisica soft-body (24 nodi), gameplay, palette, campioni originali, vetro/gel e swoosh approvati, slider. Fusioni complete nel mixer comune; niente loop da contatto statico. Non usare SoundPool classico o vecchie prove come requisiti.
- Prossimo lavoro: misurazione stress e contratto temporale/affidabilità trasporto, poi aggregazione acustica e confronto A/B. Non cambiare il suono approvato senza riscontro.
- JDK 17, SDK 35, Gradle 8.9; CI lava-blobs.yml, produzione solo workflow manuale lava-blobs-production.yml. Preview separata. Non distribuire Bloop derivati come sound pack.
