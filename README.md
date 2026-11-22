# Lava Blobs — Android

Puzzle game *cozy* con blob morbidi che si deformano, collidono e si fondono dentro recipienti di forme diverse.

**Versione giocabile:** 1.0.11 (versionCode 17). **Package:** `com.maryvellous.lavablobs`. Il progetto Android attivo è in `app/`; il vecchio puzzle Rovina non fa parte di `main`.

## Stato e documenti di riferimento

- **Prodotto attuale:** codice in `app/`, fisica e gameplay funzionanti, effetti con `SoundPool`; pulsante **SUONI/MUTO** con preferenza persistente `sound_effects_enabled_v1`. Nessuna musica integrata.
- **Progetto futuro:** [Motore Audio V2 — la materia diventa suono](docs/AUDIO_ENGINE_V2_MATERIAL_BLOOP.md). È la **sola specifica artistica e architetturale vigente**, approvata il 22 novembre 2026, **non ancora implementata**.
- **Principio V2:** le fusioni reali riproducono **uno dei quattro Bloop originali interi**; urti, sfregamenti, compressioni, lancio e POP usano risposte **parziali o trasformate dalla stessa materia sonora**, guidate dalla fisica. Contact Probe → Contact Tracker → Material Engine → Scene Mixer. La fusione è sempre il momento sonoro dominante.
- **Fondamenta V2 in sviluppo (PR #26):** ID persistenti, `ContactProbe` passivo e `ContactTracker` con memoria dei contatti; test JVM e traccia CSV riproducibile. Non sono collegati a SoundPool e non cambiano il suono attuale. **Prossimo passo:** Material Bank dei quattro Bloop e mix offline. Niente APK V2 prima dei gate previsti.
- **Storia:** le precedenti specifiche di sintesi (V1), i laboratori A/B e C1–C4.1 e i vecchi changelog sono nella cronologia Git, **non** istruzioni da seguire. Le PR sperimentali [#23](https://github.com/maryvellous/puzzle-game-android-/pull/23) e [#24](https://github.com/maryvellous/puzzle-game-android-/pull/24) restano separate, senza merge nella versione principale. `tools/audio_lab/`, i workflow del laboratorio e il test JVM a due blob sono artefatti sperimentali storici, non l'implementazione V2.

## Gameplay e UX attuali

- **Fisica soft-body:** membrane con 24 nodi, pressione, molle e adesione temporanea; preservare il solver e la risposta dei blob durante il lavoro sull'audio.
- **12 livelli** con recipienti diversi e obiettivi, più modalità infinita con tre recipienti a scelta. I valori di bilanciamento sono nel codice; le soglie sono ancora soggette a playtest.
- **Lanci:** rilascio rapido nel recipiente oppure pulsante **TRASCINA** con anteprima e annullamento sul vassoio. **SCUOTI** usa gli impulsi fisici; lo scuotimento via accelerometro funziona in primo piano.
- **POP:** solo in modalità infinita, disponibile ogni 2.000 punti (soglia provvisoria); rimuove un blob dopo selezione corretta. Il Game Over verifica l'ingresso disponibile, con conto alla rovescia.
- **Punteggio:** fusioni a partire da 10 punti, fattore 1,6 con arrotondamento; drop tier 1–5 con probabilità 45/28/16/8/3%.
- **Aspetto:** titolo elastico, tre palette, contenitori irregolari, UI morbida. La Home gestisce ripresa della partita, selezione livelli/temi e copia dei dati.
- **Salvataggi e privacy:** partita e progressi persistono **localmente** sul telefono, senza account, backend né telemetria remota. **COPIA DATI PARTITE** esporta manualmente JSON delle ultime 50 partite; `activeSeconds` esclude periodi di inattività prolungata.

## Audio della versione 1.0.11

- Fusioni: quattro Bloop originali di floraphonic, selezionati con shuffle bag e piccoli aggiustamenti di playback rate.
- Collisioni: `lava_impact_soft.wav` a volume discreto, con filtri di intensità e ripetizione del sistema classico. Questi limiti **non sono la specifica del motore V2**.
- Lancio e POP: effetti gestiti dal sistema classico con `GameAudio`/`EffectCompiler`.
- **SUONI/MUTO:** agisce sugli effetti; non altera vibrazioni, fisica, dati né una futura musica, che avrà un comando distinto.

**Provenienza audio:** [Bloop 1](https://pixabay.com/sound-effects/film-special-effects-bloop-1-184019/), [Bloop 2](https://pixabay.com/sound-effects/film-special-effects-bloop-2-186531/), [Bloop 3](https://pixabay.com/sound-effects/film-special-effects-bloop-3-186532/), [Bloop 4](https://pixabay.com/sound-effects/film-special-effects-bloop-4-186533/) — floraphonic, Pixabay Content License. [Swoosh](https://freesound.org/people/migoreng1/sounds/786514/) — migoreng1, CC0. Controllare sempre la licenza per eventuale distribuzione; non distribuire i Bloop derivati come sound pack autonomo.

## Sviluppo

Richiede **JDK 17**, **Android SDK 35** e **Gradle 8.9**.

```bash
gradle --no-daemon :app:testDebugUnitTest :app:assembleDebug :app:assemblePreview
```

La CI ordinaria è `.github/workflows/lava-blobs.yml`. La pubblicazione di `com.maryvellous.lavablobs` è **solo manuale** tramite `.github/workflows/lava-blobs-production.yml` e non deve essere avviata senza richiesta esplicita.

**Firma APK:** un certificato debug generato da runner diversi potrebbe impedire l'aggiornamento sopra l'APK installata. Una firma persistente richiede i secret `LAVA_KEYSTORE_BASE64`, `LAVA_SIGNING_STORE_PASSWORD`, `LAVA_SIGNING_KEY_ALIAS`, `LAVA_SIGNING_KEY_PASSWORD`. Mai versionare keystore o password. **Non suggerire disinstallazioni senza avvertire del rischio di perdita dei salvataggi.**

## Criteri di verifica

La CI verde dimostra test e compilazione, **non** la qualità sonora, la fluidità o l'esperienza su un telefono. Da verificare sul dispositivo: collisioni nei recipienti concavi, salvataggio/ripresa, countdown del Game Over, leggibilità e bilanciamento; per la V2, anche sincronizzazione fisica/audio, latenza, assenza di crepitii e gerarchia delle fusioni.

Per le decisioni di implementazione dell'audio **leggere la specifica V2**, non ricostruire requisiti dalle PR o dagli script storici.
