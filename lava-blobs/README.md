# Lava Blob 1.0.2 — Android

Un cozy merge puzzle personale. Le bolle salgono e si ammassano nel recipiente, deformandosi come slime. La **fisica approvata nella 0.5.0** (membrana di 24 particelle, pressione interna, molle, adesione temporanea) resta il nucleo del gioco.

## Nuova UI e temi — novembre 2026 (PR #3, in collaudo)

- Titolo Home originale mantenuto: stessa animazione delle lettere gelatinose; eliminato il sottotitolo. Home ridisegnata con card minimali, miniature dei recipienti reali, ripresa partita, tab Livelli/Infinita, sei livelli per pagina, selettore Temi e Copia dati.
- Tre temi selezionabili dalla Home: il tocco cambia subito l'anteprima e la preferenza resta salvata localmente tramite ThemeStore, senza modificare salvataggi e progressi. Colori di blob, effetti, card, controlli, HUD e finestra di fine partita derivano dal tema.
- Colori costanti: sfondo #322E32 → #3D3D3D e contorno dei recipienti #E6DFE3.
- Tema 1: #DAF301 #E9AE08 #EB8403 #F0420D #D23876 #B435A8 #9937C8 #5A29DA #2D2383.
- Tema 2: #A72F38 #E2575A #EF8F55 #F8DEBB #49A1A5 #1D3058 #9985C2 #773661 #5B5398.
- Tema 3: #FDA24B #FB6204 #CB3C06 #980203 #510207 #5F1564 #5B5398 #A20096 #0C0305 (conservato il nero, scartato #380842).
- Ogni palette ha nove colori e ricomincia dal primo al tier 10. Le anteprime degli altri temi sono quelle reali, non alternative inventate.
- La UI può scorrere su schermi compatti; il solver fisico, i contenitori e le condizioni di sconfitta restano invariati. In questa PR sono stati applicati anche la nuova formula (base 10), il primo obiettivo minimo di 1.500, i raggi maggiorati (+12% circa) e il nuovo HUD ispirato alla pagina 4 di Canva.
- La schermata partita riprende **lo stesso titolo animato della Home** tramite `BlobTitle`, mostra PUNTI/OBIETTIVO in due riquadri, il prossimo blob nel vassoio inferiore, e i controlli SCUOTI/ERASE. Le azioni non disponibili restano disabilitate.
- Build CI e test visivo su dispositivo reale da verificare prima dell'adozione come versione definitiva.

## Gioco
- Home minimale: titolo **Lava Blob** con ogni lettera deformata in tempo reale; sei livelli per pagina, swipe orizzontale.
- **12 livelli iniziali** con forme di contenitore differenti. Sblocchi il seguente completando il precedente; puoi rigiocarli senza limiti. Ogni livello richiede un punteggio minimo e presenta 2 o 3 obiettivi extra. **Le soglie sono provvisorie** e saranno tarate con le partite della giocatrice.
- **Modalità infinita immediata**: scegli uno fra tre recipienti. Ogni 2.000 punti (soglia temporanea equivalente alla precedente scala, da calibrare in playtest) si ottiene un Erase, accumulabile senza limiti; premendo Erase e poi un blob si cancella soltanto quel blob.
- **Game Over corretto**: non controlla più se una bolla scende in basso. Verifica invece se esiste un ingresso valido per il prossimo blob; serve un blocco persistente (7 secondi nei livelli, 10 nell'infinita), durante il quale si può scuotere e, nell'infinita, usare Erase.
- Colori: tre temi Canva, ciascuno con nove colori nell'ordine dei tier; ripetizione ciclica dal tier 10. Lo sfondo e il bordo del recipiente restano cromaticamente fissi.
- Drop soltanto di dimensioni 1–5: **45%, 28%, 16%, 8%, 3%**.
- Punteggio attuale: prima fusione **10**, poi ogni livello moltiplicato per **1,6** e arrotondato all'intero. I primi valori sono 10, 16, 26, 41, 66, 105, 168, 268, 429. Non ci sono bonus combo.
- I blob giganti non si dissolvono e i tier proseguono oltre il nono.
- Scuotimento via accelerometro, registrato solo mentre il gioco è in primo piano.

## Bilanciamento e privacy
Il record della vecchia scala resta archiviato, mentre il nuovo record viene conteggiato separatamente (`record_v2_`) per evitare confronti falsati. Le partite attive precedenti vengono migrate ricostruendo il nuovo punteggio dai conteggi delle fusioni, mantenendo le membrane salvate e il progresso dei livelli. L'aumento dimensionale dei blob resta da collaudare su telefono, soprattutto per le partite riprese.

Le misure delle partite vengono salvate **solo sul telefono** (punteggio, drop, fusioni, dimensione massima, Erase, durata, fusioni per livello). Dalla Home, `COPIA DATI PARTITE` copia il JSON delle ultime 50 partite per l'analisi manuale; non esiste backend, raccolta a distanza o account. Il primo obiettivo minimo è 1.500 punti nella nuova scala, come deciso. Gli altri obiettivi dei livelli e gli extra restano ai valori preesistenti in attesa di playtest con i blob maggiorati; la soglia Erase di 2.000 resta temporanea.

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
