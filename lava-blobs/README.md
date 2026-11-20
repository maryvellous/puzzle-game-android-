# Lava Blob 1.0.2 — Android

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

## Progettazione audio — decisioni del 20 novembre 2026 (da implementare)

### Effetti di fusione: campioni approvati

Usare esclusivamente i quattro effetti originali **Bloop 1, Bloop 2, Bloop 3 e Bloop 4** di **floraphonic**, disponibili su Pixabay:

- [Bloop 1](https://pixabay.com/sound-effects/film-special-effects-bloop-1-184019/)
- [Bloop 2](https://pixabay.com/sound-effects/film-special-effects-bloop-2-186531/)
- [Bloop 3](https://pixabay.com/sound-effects/film-special-effects-bloop-3-186532/)
- [Bloop 4](https://pixabay.com/sound-effects/film-special-effects-bloop-4-186533/)

Alternare i quattro campioni durante le fusioni per variare il feedback sonoro ed evitare monotonia. Al crescere della **dimensione della fusione**, riprodurre il campione **lievissimamente più lentamente**, in modo graduale e continuo (e quindi leggermente più grave se la velocità altera anche il pitch). Preservare il carattere originale *bubbly*, rotondo ed elastico dei Bloop: niente suoni bagnati, viscidi, squish realistici o distorsioni aggiuntive. I valori precisi della curva di riproduzione sono **da calibrare all'ascolto**, non ancora approvati come specifica numerica.

I cinque effetti sintetizzati di prova proposti in chat sono stati **scartati integralmente** e non devono essere usati o inseriti tra le risorse del gioco.

### Audio ancora da definire

- **Collisioni fra blob:** campione non approvato; cercare un suono davvero gommoso, leggero e wobbly, evitando schiocchi secchi, suoni vischiosi o registrazioni umide sgradevoli. Non sostituirlo automaticamente con uno dei suoni di fusione.
- **Musica di sottofondo:** ancora da ascoltare e selezionare. Direzione zen, discreta e non ripetitiva.

### Stato implementativo

Questa sezione documenta **scelte di design approvate**, non l'integrazione effettiva nell'APK. Prima dell'implementazione, recuperare i quattro file originali, verificare la licenza Pixabay vigente e conservarne attribuzione e provenienza nel progetto. Testare l'alternanza e la variazione di velocità sul telefono senza introdurre cambiamenti alla fisica approvata.

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

## 1.0.2 — partita riprendibile, forme e Home

- **Un solo salvataggio di partita in corso**: snapshot locale completo e atomico delle posizioni/velocità delle 24 particelle per blob, livelli, stato della membrana, legami viscosi, RNG, prossimo lancio, punti, record, Erase e conto alla rovescia di Game Over. Home → Continua riprende la stessa partita. Al ritorno alla Home e durante il ciclo di vita Android viene salvato automaticamente; una nuova partita sovrascrive la precedente **dopo una conferma**.
- Il salvataggio dello stato di gioco è separato dai progressi sbloccati e dal report `COPIA DATI PARTITE`; mettere in pausa non crea una nuova sessione di playtest.
- **Colori originali HEX pieni e opachi**, senza shader o filtri sul riempimento; la palette non è stata arbitrariamente alterata. Su display diversi i blu possono comunque apparire simili poiché alcuni valori di partenza sono affini.
- **Geometrie più caratteristiche**: maggiori rientranze, strozzature e asimmetrie, senza cambiare il solver approvato.
- **Home riprogettata**: pulsante Continua, cards con miniatura separata dai testi, coordinate di clipping della miniatura e nuovo contrasto cromatico. Conservato l'effetto visivo all'interno del vetro.
- **Firma APK**: le build debug prodotte da runner GitHub differenti possono essere firmate con chiavi differenti; finché non si collega una chiave di firma persistente, Android non permetterà sempre l'installazione sopra la 1.0.1 conservando i dati. Non chiedere mai di disinstallare senza avvertire del rischio di perdita dei progressi.
- **Test**: roundtrip completo delle membrane, RNG, progressi di gioco, geometrie e regressioni fisiche; richiede ancora collaudo visivo reale sul telefono.
