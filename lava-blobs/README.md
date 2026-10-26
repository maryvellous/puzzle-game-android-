# Lava Blob 1.0.1 — Android

Un cozy merge puzzle personale. Le bolle salgono e si ammassano nel recipiente, deformandosi come slime. La **fisica approvata nella 0.5.0** (membrana di 24 particelle, pressione interna, molle, adesione temporanea) resta il nucleo del gioco.

## Gioco
- Home minimale: titolo **Lava Blob** con ogni lettera deformata in tempo reale; sei livelli per pagina, swipe orizzontale.
- **12 livelli iniziali** con forme di contenitore differenti. Sblocchi il seguente completando il precedente; puoi rigiocarli senza limiti. Ogni livello richiede un punteggio minimo e presenta 2 o 3 obiettivi extra. **Le soglie sono provvisorie** e saranno tarate con le partite della giocatrice.
- **Modalità infinita immediata**: scegli uno fra tre recipienti. Ogni 20.000 punti (soglia provvisoria) si ottiene un Erase, accumulabile senza limiti; premendo Erase e poi un blob si cancella soltanto quel blob.
- **Game Over corretto**: non controlla più se una bolla scende in basso. Verifica invece se esiste un ingresso valido per il prossimo blob; serve un blocco persistente (7 secondi nei livelli, 10 nell'infinita), durante il quale si può scuotere e, nell'infinita, usare Erase.
- Colori definitivi (dimensioni 1–9): `#065143`, `#4B748E`, `#6D9DC5`, `#6E73AD`, `#53599A`, `#6F4994`, `#8A4C88`, `#A54F7B`, `#DB5461`. Ripetuti ciclicamente oltre la nona.
- Drop soltanto di dimensioni 1–5: **45%, 28%, 16%, 8%, 3%**.
- Punteggio proposto: prima fusione **100**, poi ogni livello moltiplicato per **1,6** e arrotondato alle decine. Non ci sono bonus combo.
- I blob giganti non si dissolvono e i tier proseguono oltre il nono.
- Scuotimento via accelerometro, registrato solo mentre il gioco è in primo piano.

## Bilanciamento e privacy
Le misure delle partite vengono salvate **solo sul telefono** (punteggio, drop, fusioni, dimensione massima, Erase, durata, fusioni per livello). Dalla Home, `COPIA DATI PARTITE` copia il JSON delle ultime 50 partite per l'analisi manuale; non esiste backend, raccolta a distanza o account. Le soglie dei livelli e dell'Erase sono ipotesi iniziali, non risultati di playtest.

## Sviluppo
Progetto indipendente dentro `lava-blobs/`. L'app di puzzle Rovina nella radice del repository è separata. Richiede JDK 17, Android SDK 35 e Gradle 8.9.

```bash
gradle -p lava-blobs :app:testDebugUnitTest :app:assembleDebug
```

Il workflow `Build Lava Blobs APK` produce un artifact installabile. Debug APK firmati in job diversi potrebbero richiedere disinstallazione della versione precedente: il certificato di debug di GitHub Actions non è stabile.

## Aree da verificare con il telefono
- Collisioni delle membrane dentro recipienti concavi e al soffitto; non alterare gratuitamente i parametri della 0.5.
- Assenza di false sconfitte dopo una scossa e usabilità dell'Erase durante il conto alla rovescia.
- Leggibilità del titolo animato e spaziature su display di diversa altezza.
- Difficoltà e percentuali di completamento, per rivedere le soglie.

## Prove contenitore — versione 1.0.1

Dalla Home premi **TEST VASETTI**. Tre varianti della stessa forma e larghezza sono accessibili fin da subito: **A = 285**, **B = 340**, **C = 400** unità di altezza. Le prove sono indipendenti dallo sblocco livelli; il livello 1 normale usa la variante B provvisoriamente. È importante provare tutte e tre per confrontare punteggio, drop, fusioni e durata, quindi copiare i dati con **COPIA DATI PARTITE**. Nel JSON ogni sessione porta `mode: vessel_trial` e `vesselHeight`.

I contenitori dei livelli hanno altezze fisse per forma, non crescono in altezza a seconda del dispositivo. Contenitore e pannello di lancio vengono centrati come blocco unitario sotto l'HUD; il pannello è alto 100 unità invece di estendersi fino al bordo inferiore. Le viste sono ospitate in un contenitore che applica l'inset inferiore della barra di navigazione Android; tutto il contenuto interattivo rimane in area sicura. Le molle, il pressure solver, l'adesione e il movimento dei blob della 0.5.0 non sono modificati.

Le soglie-obiettivo restano quelle provvisorie della 1.0.0 finché non saranno disponibili le misure delle prove; non adattarle arbitrariamente in base al solo screenshot con vasetto sproporzionato.
