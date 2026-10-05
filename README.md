# Rovina Puzzle

Puzzle game Android touch-first con i primi cinque disegni del progetto.

## Funzioni v0.2.0

- 5 puzzle quadrati.
- Tutti i puzzle sono **10×10: 100 tessere**.
- L'immagine viene mescolata direttamente sulla tavola.
- Tocca una tessera e poi una seconda tessera per **scambiarle di posto**.
- Nessuna immagine fantasma sotto il puzzle e nessuna indicazione della soluzione.
- Zoom con pinch e pulsanti + / −.
- Pan trascinando la tavola.
- Pulsante **Anteprima**: mostra l'immagine completa solo mentre viene tenuto premuto.
- Conteggio delle mosse e memoria locale dei puzzle completati.
- APK compilato automaticamente da GitHub Actions e pubblicato nella GitHub Release `v0.2.0`.

## Build locale

Richiede JDK 17, Android SDK 35 e Gradle 8.9.

```bash
gradle :app:assembleDebug
```
