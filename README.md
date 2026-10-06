# Rovina Puzzle

Puzzle game Android touch-first con i primi cinque disegni del progetto.

## Funzioni v0.3.0

- 5 puzzle quadrati, tutti **10×10: 100 tessere**.
- Tocca due tessere per scambiarle di posto.
- Nessuna immagine fantasma sotto il puzzle.
- Zoom con pinch, controlli + / − e pan della tavola.
- **Anteprima** visibile solo mentre tieni premuto il pulsante.
- **Salvataggio automatico** dello stato del puzzle e del numero di mosse.
- Dal menu un puzzle lasciato a metà appare come **Continua Puzzle** e riparte esattamente da dove era stato lasciato.
- Il salvataggio viene eliminato automaticamente quando il puzzle è completato.
- Nuova schermata di vittoria sobria: immagine completa senza griglia, animazione breve, conteggio mosse, pulsanti Menu e Prossimo.
- APK compilato automaticamente da GitHub Actions e pubblicato nella GitHub Release `v0.3.0`.

## Build locale

Richiede JDK 17, Android SDK 35 e Gradle 8.9.

```bash
gradle :app:assembleDebug
```
