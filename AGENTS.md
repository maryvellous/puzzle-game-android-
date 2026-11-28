# AGENTS.md — Lava Blobs

- Consultare ramo di lavoro e [README](README.md). Il solo progetto Android è app/.
- Fonti operative: [stato tecnico](docs/TECHNICAL_ROADMAP.md), [specifica audio](docs/AUDIO_ENGINE_V2_MATERIAL_BLOOP.md), [piano prodotto e playtest](docs/PRIVATE_PLAYTEST.md). La storia Git conserva gli esperimenti precedenti.
- Consolidamento verificato dalla CI #253; baseline percettiva #238. Home, HUD e dodici nuove geometrie implementati nel ramo `refactor/technical-debt-roadmap` (PR #27). Prima di riprendere leggere la sezione Stato operativo di PRIVATE_PLAYTEST.md: logo Home ricostruito da forme (originale non trovato), da approvare; priorità P0 fluidità. Leggere [piano recupero prestazioni](docs/PERFORMANCE_RECOVERY_PLAN.md), F0–F2 implementazione iniziale da collaudare, niente F3 senza evidenze. Tutorial e distribuzione ancora da completare.
- Preservare solver, comportamento sonoro, campioni, fusioni complete, salvataggi. UI, silhouette dei contenitori e bilanciamento possono evolvere intenzionalmente: distinguere modifiche di prodotto da refactor. Non disattivare gate per far passare cambiamenti.
- Commit mirati, test proporzionati; distinguere codice scritto, CI, collaudo dispositivo e distribuzione. JDK 17, SDK 35, Gradle 8.9.
- Nessun force-push, merge o pubblicazione non autorizzati; non esporre segreti, non cancellare dati utente. CI lava-blobs.yml, consegna firmata manuale su main, artifact-only per default. Non distribuire preview debug o campioni audio come sound pack.
