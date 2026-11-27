# Lava Blobs — Android

Puzzle game *cozy* con blob morbidi che si deformano, collidono e si fondono dentro recipienti di forme diverse.

**Versione di sviluppo su main:** 1.1.0 (versionCode 18), package `com.maryvellous.lavablobs`. Ultima release pubblicata: 1.0.11; la pubblicazione della 1.1.0 è separata dall'integrazione del codice. Progetto attivo: `app/`.

## Stato e documenti di riferimento

- **27 novembre 2026:** l'utente approva Audio V2.3 sul telefono e chiede di adottarlo come nuova app principale e base dello sviluppo. Preview approvata: `cfb03c96fd5e348b5e0ddd0c201255101913fa5f`. Il motore è attivo in tutte le build, non solo nella preview.
- **Specifica vigente:** [la materia diventa suono](docs/AUDIO_ENGINE_V2_MATERIAL_BLOOP.md). Probe/Tracker → Material Engine → Scene Mixer → AudioTrack. Fusioni con i Bloop completi, urti contro vetro con attacco V2.2, urti tra blob gelatinosi, squish finiti dinamici, swoosh classico al lancio, silenzio in quiete.
- **Slider:** volume 0–100% persistente, mute e ripristino dell'ultimo valore; nessuna musica implementata.
- **Preview:** package separato `com.maryvellous.lavablobs.audiov21`, stesso motore, banner diagnostico. La build normale non mostra il banner. Non sono richiesti interventi aggiuntivi di migrazione dei salvataggi.
- **Verifica:** preview approvata percettivamente; [CI main verde](https://github.com/maryvellous/puzzle-game-android-/actions/runs/46060029269); APK con package principale non ancora collaudata separatamente sul telefono.
- **Fase 2:** primo stadio concluso; lieve frittura sotto stress da diagnosticare. [Piano di aggregazione](docs/AUDIO_AGGREGATION_PLAN.md), [audit debito tecnico](docs/TECHNICAL_DEBT_AUDIT.md), [protocollo collaudo](docs/PLAYTEST_AUDIO_V2.3.md). Nessuna aggregazione implementata ancora.
- **Storia:** SoundPool 1.0.11, PR #23/#24 e laboratori A/B–C4.1 sono riferimenti storici, non requisiti. Non reintrodurre loop di contatto o cambiare il timbro approvato.

## Gameplay e UX attuali

- **Fisica soft-body:** membrane con 24 nodi, pressione, molle e adesione temporanea; preservare il solver e la risposta dei blob durante il lavoro sull'audio.
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
gradle --no-daemon :app:testDebugUnitTest :app:assembleDebug :app:assemblePreview
```

La CI ordinaria è `.github/workflows/lava-blobs.yml`. La pubblicazione di `com.maryvellous.lavablobs` è **solo manuale** tramite `.github/workflows/lava-blobs-production.yml` e non deve essere avviata senza richiesta esplicita.

**Firma APK:** un certificato debug generato da runner diversi potrebbe impedire l'aggiornamento sopra l'APK installata. Una firma persistente richiede i secret `LAVA_KEYSTORE_BASE64`, `LAVA_SIGNING_STORE_PASSWORD`, `LAVA_SIGNING_KEY_ALIAS`, `LAVA_SIGNING_KEY_PASSWORD`. Mai versionare keystore o password. **Non suggerire disinstallazioni senza avvertire del rischio di perdita dei salvataggi.**

## Criteri di verifica

La CI verde dimostra test e compilazione, **non** la qualità sonora, la fluidità o l'esperienza su un telefono. Da verificare sul dispositivo: collisioni nei recipienti concavi, salvataggio/ripresa, countdown del Game Over, leggibilità e bilanciamento; per la V2, anche sincronizzazione fisica/audio, latenza, assenza di crepitii e gerarchia delle fusioni.

Per le decisioni di implementazione dell'audio **leggere la specifica V2**, non ricostruire requisiti dalle PR o dagli script storici.
