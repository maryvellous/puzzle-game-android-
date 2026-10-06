# Rovina Puzzle

Puzzle game Android touch-first con i primi cinque disegni del progetto.

## Funzioni v0.3.0

- 5 puzzle quadrati.
- Tutti i puzzle sono **10×10: 100 tessere**.
- Tocca una tessera e poi una seconda tessera per **scambiarle di posto**.
- Nessuna immagine fantasma sotto il puzzle e nessuna indicazione della soluzione.
- Zoom con pinch e pulsanti + / −.
- Pan trascinando la tavola.
- Pulsante **Anteprima**: mostra l'immagine completa solo mentre viene tenuto premuto.
- **Salvataggio automatico** dopo ogni scambio.
- I puzzle non finiti compaiono nel menu come **Continua · N mosse** e ripartono esattamente dalla disposizione salvata.
- Tornando al menu o chiudendo la vista, il progresso corrente viene conservato.
- Schermata di vittoria sobria: immagine completata ancora visibile, conteggio mosse e soli pulsanti essenziali.
- Memoria locale dei puzzle completati.
- APK compilato automaticamente da GitHub Actions e pubblicato nella GitHub Release `v0.3.0`.

## Build locale

Richiede JDK 17, Android SDK 35 e Gradle 8.9.

```bash
gradle :app:assembleDebug
```
