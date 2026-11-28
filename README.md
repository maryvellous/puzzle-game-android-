# Lava Blobs — Android

Puzzle game *cozy* con blob morbidi che si deformano, collidono e si fondono dentro recipienti di forme diverse.

**Versione di sviluppo:** 1.1.0 (versionCode 18), package `com.maryvellous.lavablobs`. Progetto attivo: `app/`. Consolidamento verificato dalla [CI #253](https://github.com/maryvellous/puzzle-game-android-/actions/runs/46097012021), baseline collaudata #238. PR #27 ancora da integrare in main; distribuzione separata.

## Stato e documenti di riferimento

- **Baseline approvata:** [43aca8e](https://github.com/maryvellous/puzzle-game-android-/tree/baseline/late-game-approved-2026-11-28), Aggregation Preview #238. Il playtest indica maggiore fluidità e assenza del frittino percepito durante la prova.
- **Specifica vigente:** [la materia diventa suono](docs/AUDIO_ENGINE_V2_MATERIAL_BLOOP.md). Un solo percorso di gameplay: eventi fisici → scheduler → aggregazione dopo gating → mixer PCM refined → AudioTrack.
- **Preview diagnostica:** package separato `com.maryvellous.lavablobs.audioaggregation`, stesso motore della build normale; banner e trace locale solo nella preview. I vecchi profili bypass e approved sono controlli offline; l'esperimento cubico e la variante APK bypass sono ritirati.
- **Stato tecnico e risultati:** [TECHNICAL_ROADMAP.md](docs/TECHNICAL_ROADMAP.md). I report superati restano nella storia Git.
- **Prossimo lavoro di prodotto:** progettazione integrata UI/UX e level design (Home, contenitori, HUD, onboarding), poi [collaudo e distribuzione privata](docs/PRIVATE_PLAYTEST.md). Non è previsto rimettere mano a solver o timbro senza un difetto riproducibile.

## Gameplay e UX attuali

- **Fisica soft-body:** membrane con 24 nodi, pressione, molle e adesione temporanea; solver approvato, da preservare.
- **12 livelli** con recipienti diversi e obiettivi, più modalità infinita con tre recipienti a scelta. I valori di bilanciamento sono nel codice; le soglie sono ancora soggette a playtest.
- **Lanci:** rilascio rapido nel recipiente oppure pulsante **TRASCINA** con anteprima e annullamento sul vassoio. **SCUOTI** usa gli impulsi fisici; lo scuotimento via accelerometro funziona in primo piano.
- **POP:** solo in modalità infinita, disponibile ogni 2.000 punti (soglia provvisoria); rimuove un blob dopo selezione corretta. Il Game Over verifica l'ingresso disponibile, con conto alla rovescia.
- **Punteggio:** fusioni a partire da 10 punti, fattore 1,6 con arrotondamento; drop tier 1–5 con probabilità 45/28/16/8/3%.
- **Aspetto:** titolo elastico, tre palette, contenitori irregolari, UI morbida. La Home gestisce ripresa della partita, selezione livelli/temi e copia dei dati.
- **Salvataggi e privacy:** partita e progressi persistono **localmente** sul telefono, senza account, backend né telemetria remota. **COPIA DATI PARTITE** esporta manualmente JSON delle ultime 50 partite; `activeSeconds` esclude periodi di inattività prolungata.

## Audio della versione 1.1.0

- Un unico mixer PCM Java e AudioTrack; effetti finiti, nessun loop di contatto.
- Slider dopo il limiter: il bilanciamento interno resta invariato.
- Conservare i quattro Bloop originali e lo swoosh approvato.

**Provenienza audio:** [Bloop 1](https://pixabay.com/sound-effects/film-special-effects-bloop-1-184019/), [Bloop 2](https://pixabay.com/sound-effects/film-special-effects-bloop-2-186531/), [Bloop 3](https://pixabay.com/sound-effects/film-special-effects-bloop-3-186532/), [Bloop 4](https://pixabay.com/sound-effects/film-special-effects-bloop-4-186533/) — floraphonic, Pixabay Content License. [Swoosh](https://freesound.org/people/migoreng1/sounds/786514/) — migoreng1, CC0. Controllare sempre la licenza per eventuale distribuzione; non distribuire i Bloop derivati come sound pack autonomo.

## Sviluppo

Richiede **JDK 17**, **Android SDK 35** e **Gradle 8.9**.

```bash
gradle --no-daemon :app:testDebugUnitTest :app:testAggregationPreviewUnitTest :app:testReleaseUnitTest
gradle --no-daemon :app:assembleDebug :app:assembleAggregationPreview :app:assembleRelease
```

La CI ordinaria è `.github/workflows/lava-blobs.yml`. La distribuzione di `com.maryvellous.lavablobs` è **solo manuale** su main tramite `.github/workflows/lava-blobs-production.yml`: per default produce un artefatto APK firmato con checksum; un GitHub Release richiede la scelta esplicita `publish_release=true`. Nessun fallback debug per distribuzione. Non avviare senza richiesta esplicita.

**Firma APK:** un certificato debug generato da runner diversi potrebbe impedire l'aggiornamento sopra l'APK installata. Per la distribuzione, una firma persistente obbligatoria richiede i secret `LAVA_KEYSTORE_BASE64`, `LAVA_SIGNING_STORE_PASSWORD`, `LAVA_SIGNING_KEY_ALIAS`, `LAVA_SIGNING_KEY_PASSWORD`. Mai versionare keystore o password. **Non suggerire disinstallazioni senza avvertire del rischio di perdita dei salvataggi.**

## Criteri di verifica

La CI verde dimostra test e compilazione, **non** la qualità sonora, la fluidità o l'esperienza su un telefono. Da verificare sul dispositivo: collisioni nei recipienti concavi, salvataggio/ripresa, countdown del Game Over, leggibilità e bilanciamento; per la V2, anche sincronizzazione fisica/audio, latenza, assenza di crepitii e gerarchia delle fusioni.

Per le decisioni di implementazione dell'audio **leggere la specifica V2**, non ricostruire requisiti dalle PR o dagli script storici.
