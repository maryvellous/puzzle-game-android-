# Rovina Puzzle

Piccolo puzzle game Android touch-first con i primi cinque disegni forniti per il progetto.

## Funzioni v0.1.0

- 5 puzzle quadrati.
- 3 difficoltà: 36, 64 e 100 pezzi.
- Zoom con pinch e pulsanti + / −.
- Pan del tavolo di gioco trascinando lo sfondo.
- Snap automatico quando un pezzo viene portato nella posizione corretta.
- Memorizzazione locale dei puzzle completati.
- APK compilato automaticamente da GitHub Actions e pubblicato nella GitHub Release `v0.1.0`.

## Build locale

Richiede JDK 17, Android SDK 35 e Gradle 8.9.

```bash
gradle :app:assembleDebug
```
