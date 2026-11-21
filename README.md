# Lava Blob 1.0.9 — Android

## Collisioni — taratura su test reale (21 novembre 2026)

- Confermato sul telefono: la 1.0.8 riproduce i suoni delle collisioni, ma troppo spesso e con timbro simile a una fusione.
- Soglia della velocità relativa riportata a **20** (dalla precedente 8), senza modificare la fisica o il cooldown.
- Nuovo estratto del `Bloop 2` per gli urti: `lava_impact_v3.wav`, più corto e filtrato (rimozione del corpo a bassa frequenza), frequenze di riproduzione più alte rispetto alle fusioni. Volume degli urti ridotto a 0,095–0,18 rispetto a 0,56 delle fusioni.
- Il fallback ora usa `swoosh_source` (campione non usato nelle fusioni), riprodotto più veloce e più piano; non riproduce più la registrazione intera di `Bloop 2` come collisione.
- Test aggiornati per soglia di 20, filtro del campione, basso volume, contatto rapido dal basso e non-fusione. Controllo timbrico finale su telefono ancora necessario.
- Versione Android 1.0.9 / code 15, stesso identificativo dell'app. APK firmato temporaneamente se non è configurata una chiave stabile; può essere richiesta la disinstallazione con perdita dei dati locali non esportati.

---

# Lava Blob 1.0.8 — Android

## Collisioni sonore: correzioni e test (21 novembre 2026)

- La preparazione dei campioni è indipendente: errori di conversione su lancio/POP non possono impedire il caricamento delle collisioni. Il campione originale `Bloop 2` già incorporato è usato come **fallback per gli urti** se il campione derivato non è disponibile, manca o è silenzioso; la selezione mantiene sempre l'effetto derivato quando pronto.
- La conversione PCM verifica che l'estratto audio non sia vuoto/quasi silenzioso; la cache WAV viene convalidata prima di riutilizzarla.
- Gli urti tra blob differenti vengono registrati anche quando si manifesta il primo contatto nei passaggi successivi del solver; rimangono la soglia di velocità 8, il cooldown per coppia e il filtro globale anti-ripetizione. Non cambia il moto delle bolle.
- Test di regressione: bolla veloce dal basso contro bolla quasi ferma in alto, non-fusione, niente suoni a distanza, scelta del fallback e rigetto di sample silenziosi.
- Il controllo esito di `SoundPool.play()` registra gli errori, senza bloccare il gameplay. La riuscita CI non è ancora un collaudo sul telefono.
- Versione 1.0.8 (versionCode 14). Senza firma persistente il normale APK della CI non è garantito aggiornabile sopra la versione precedente: per l'installazione può essere necessaria disinstallazione (con perdita dati locali non esportati).

---

# Lava Blob 1.0.7 — Android

Aggiornamento audio (21 novembre 2026): soglia di avvicinamento per il suono degli urti tra blob differenti ridotta da 42 a 8. Nessuna modifica a volume, asset sonori, frequenza, anti-ripetizione o fisica. È una prova da ascoltare su dispositivo, non una conferma che il suono sia riprodotto.

Distribuzione: versioneCode 13. La release 1.0.6 era firmata con chiave debug temporanea da GitHub Actions (firma stabile=0); chi ha installato quella versione potrebbe doverla disinstallare prima di installare la 1.0.7. Finché non viene configurato un keystore persistente, aggiornamenti futuri possono richiedere lo stesso passaggio.

---

# Lava Blob 1.0.6 — Android

## Modifiche della 1.0.6 (20 novembre 2026)

**Soglie in punti per forma:** valori sperimentali che non seguono una progressione numerica obbligatoria. Fiala = 2.200, Onde = 2.000. Gli extra in punti sono separati e ricalibrati per evitare i traguardi finora irrealistici.

| Livello | Minimo | Extra punti |
|---|---:|---:|
| 1 · Primo vasetto | 1.500 | 2.200 |
| 2 · La coppa | 950 | 1.450 |
| 3 · Anfora | 1.600 | 2.200 |
| 4 · Gola stretta | 1.850 | 2.500 |
| 5 · La fiala | 2.200 | 2.700 |
| 6 · Onde | 2.000 | 2.550 |
| 7 · Pancia | 2.100 | 2.800 |
| 8 · Clessidra | 1.900 | 2.550 |
| 9 · Piccola goccia | 1.800 | 2.450 |
| 10 · Vaso lunare | 2.150 | 2.850 |
| 11 · Il nodo | 2.000 | 2.750 |
| 12 · Ultima danza | 2.150 | 2.900 |

**Audio:** accorciato il campione per l'immissione a circa 0,09 s percepiti; alzato moderatamente il volume delle collisioni e ridotta la soglia di velocità richiesta per produrle, con anti-ripetizione conservata; aggiunto un breve POP solo dopo una cancellazione riuscita. Le quattro fusioni Bloop e la loro curva restano inalterate.

**UX:** il pulsante con la bolla «TRASCINA» ora avvia un trascinamento con una bolla fantasma e linea di mira; il rilascio nel recipiente lancia e il rilascio sul vassoio annulla. Il lancio rapido direttamente nel recipiente resta funzionante. Indicazione essenziale al primo avvio del livello.

**Telemetria:** il JSON delle partite prende la versione dal pacchetto realmente installato; mantiene `durationSeconds` per confrontare lo storico e aggiunge `activeSeconds`, che non avanza quando il gioco rimane aperto senza input per più di 12 secondi. Il valore `activeSeconds` fa parte del salvataggio della partita in corso. Non modifica il timer fisico.

**Distribuzione:** il workflow supporta un keystore di firma persistente tramite quattro segreti (`LAVA_KEYSTORE_BASE64`, `LAVA_SIGNING_STORE_PASSWORD`, `LAVA_SIGNING_KEY_ALIAS`, `LAVA_SIGNING_KEY_PASSWORD`). In assenza di questi segreti produce ancora un APK debug con firma non stabile; configurare i segreti è necessario per aggiornare le future versioni senza reinstallazione. Non inserire keystore o password in Git.

**Criterio di accettazione:** CI Android (test + build) e collaudo reale di trascinamento, annullamento, tempi attivi, suoni e difficoltà sui recipienti. Non equiparare la riuscita della CI al collaudo UX.

---

# Lava Blob 1.0.5 — Android (storico)

## Sistema audio — v1.0.5

- **Fusioni:** suoni originali `Bloop 1–4` di floraphonic (Pixabay), in shuffle-bag di quattro varianti senza ripetizioni consecutive; volume dominante, velocità progressivamente appena ridotta da `1.00×` a minimo `0.92×` in base alla dimensione.
- **Immissione dal basso:** `swoosh_v2` di migoreng1 (Freesound, CC0), invertito, accorciato e sfumato con fade tramite decoder audio Android; volume molto discreto.
- **Collisioni:** porzione centrale del `Bloop 2`, senza l'attacco iniziale, sfumata con fade; pitch più alto/veloce per piccoli blob (`wip`), più basso e lungo per grandi (`wouupp`). Molto più quieto di una fusione.
- **Motore:** `GameAudio` usa `SoundPool` con attributi audio di gioco e caricamento asincrono. `EffectCompiler` decodifica e prepara in background i due derivati WAV una sola volta per cache; non tocca il thread della fisica o del rendering.
- **Collisioni effettive:** rilevate dal passaggio iniziale di risoluzione delle membrane differenti, con soglia di velocità relativa di avvicinamento, cooldown per coppia, limite di eventi in coda e priorità dei suoni di fusione. Gli urti deboli e i blob già premuti gli uni contro gli altri restano silenziosi.
- **Lifecycle:** risorse liberate al ritorno alla Home; pausa/ripresa in background Android. Fallimento eventuale nella preparazione di due clip non interrompe il gameplay.
- **Musica ambient:** non aggiunta, non ancora scelta.
- **Stato:** implementato nel codice; collaudo timbrico sul dispositivo ancora necessario.

**Licenze e fonti:** floraphonic Bloop 1 https://pixabay.com/sound-effects/film-special-effects-bloop-1-184019/ ; Bloop 2 https://pixabay.com/sound-effects/film-special-effects-bloop-2-186531/ ; Bloop 3 https://pixabay.com/sound-effects/film-special-effects-bloop-3-186532/ ; Bloop 4 https://pixabay.com/sound-effects/film-special-effects-bloop-4-186533/ (Pixabay Content License). Immissione: migoreng1 https://freesound.org/people/migoreng1/sounds/786514/ (CC0). Risorse incorporate nel gioco, non fornite come libreria audio indipendente.

## Audio — decisioni progettuali approvate il 20 novembre 2026

**Effetti delle fusioni: quattro campioni scelti, da integrare.** Utilizzare i suoni originali di floraphonic pubblicati su Pixabay:

- [Bloop 1](https://pixabay.com/sound-effects/film-special-effects-bloop-1-184019/)
- [Bloop 2](https://pixabay.com/sound-effects/film-special-effects-bloop-2-186531/)
- [Bloop 3](https://pixabay.com/sound-effects/film-special-effects-bloop-3-186532/)
- [Bloop 4](https://pixabay.com/sound-effects/film-special-effects-bloop-4-186533/)

Alternare i quattro suoni durante le fusioni (evitando ripetizioni ravvicinate) e **rallentare lievissimamente la riproduzione al crescere della dimensione della fusione**: variazione progressiva e contenuta, mantenendo il timbro *bubbly* e rotondo dei campioni. Nessuna nuova sintesi, riverbero o sostituzione del suono originale. **La curva numerica precisa non è ancora stata approvata:** regolarla e ascoltarla in gioco prima di fissare valori definitivi.

I cinque file sintetizzati dall'assistente durante la ricerca sono **scartati completamente**. Evitare suoni di gelatina bagnata, squish vischiosi, colpi secchi o campioni di collisione già rifiutati.

**Non ancora scelti:** effetto per gli urti fra blob (deve risultare davvero elastico, morbido e *wobbly*, senza impressione umida o disgustosa) e musica ambient zen. I precedenti suggerimenti non costituiscono asset approvati.

**Stato storico al 20 novembre:** specifica inizialmente non implementata; vedi «Sistema audio — v1.0.5» per lo stato attuale. Prima dell'integrazione verificare condizioni d'uso/licenza Pixabay per i file scelti, conservarne provenienza e testare sul dispositivo Android, senza cambiare la fisica del gioco.

Un cozy merge puzzle personale. Le bolle salgono e si ammassano nel recipiente, deformandosi come slime. La **fisica approvata nella 0.5.0** (membrana di 24 particelle, pressione interna, molle, adesione temporanea) resta il nucleo del gioco.

## UI bubbly e interazioni — 19 novembre 2026 (v1.0.4)

**Decisioni correnti, indipendenti da mockup esterni.** La schermata di gioco deve essere morbida, vivace e giocosa, senza nascondere il recipiente dietro grandi riquadri. La fisica, i blob, le particelle in sospensione, i colori dei nove tier e i salvataggi persistenti non vengono modificati.

- **Header** con il titolo elastico `BlobTitle` originale su fondo avorio fisso `#E6DFE3`, anche in Home. Pulsanti Home/Ricomincia in bolle tonde.
- **PUNTI, OBIETTIVO e RECORD** dentro pillole organiche colorate. I numeri grandi usano il font di sistema Android `casual` in grassetto: tono dolce e arrotondato, senza distribuire il carattere commerciale More Sugar; numeri in gioco con breve effetto di rimbalzo.
- **Controlli inferiori**: prossimo blob nella sua bolla chiara, pulsante circolare SCUOTI, pulsante POP **esclusivamente in modalità infinita**; nei livelli e nelle prove POP non viene disegnato e il layout a due elementi si riequilibra.
- **POP** (nuovo nome visibile della precedente meccanica POP) continua a consumare una carica soltanto dopo la scelta corretta di un blob. Scoppio visivo: lieve espansione della sagoma colorata, anello avorio e 14 goccioline che si disperdono e spariscono; feedback aptico. Nessuna alterazione delle regole o delle probabilità.
- **SCUOTI** esegue più impulsi alternati tramite il metodo fisico `BlobWorld.shake` già esistente; un'oscillazione grafica breve rende evidente lo scuotimento del recipiente senza spostare i collider né modificare il solver. Il gesto fisico del telefono continua a funzionare.
- **Home**: miniature dei livelli direttamente sulle card avorio, **nessun rettangolo nero** dietro le sagome. Il contorno delle miniature è ora in tinta scura neutra per rimanere leggibile.
- **Principi da preservare**: niente bordini decorativi, niente toni colorati sulle superfici neutre; particelle originali in movimento **identiche** (numero, opacità, traiettorie, distribuzione). Non ingrandire eccessivamente il titolo o ridurre inutilmente l'area di gioco.

**Test automatici**: oltre a quelli preesistenti, `PopFeatureTest` verifica che il comando funzioni solo in infinita, consumi correttamente una carica, registri il blob rimosso e che gli impulsi multipli non causino aree di membrana non valide. **Collaudo visivo su telefono ancora necessario**; build Android CI != verifica dell'esperienza reale.

## UI precedente e introduzione dei temi — versione 1.0.3 (storico)

- Titolo Home originale mantenuto: stessa animazione delle lettere gelatinose; eliminato il sottotitolo. Home ridisegnata con card minimali, miniature dei recipienti reali, ripresa partita, tab Livelli/Infinita, sei livelli per pagina, selettore Temi e Copia dati.
- Tre temi selezionabili dalla Home: la preferenza resta salvata localmente tramite ThemeStore, senza modificare salvataggi e progressi. I temi colorano blob, animazioni e soli accenti UI; le superfici neutre non ereditano il colore del tema.
- Colori costanti dal mockup: sfondo #322E32 → #3D3D3D; contorno dei recipienti e superfici neutre #E6DFE3. I pannelli Home/HUD e il vassoio del prossimo blob hanno sfondo avorio senza bordino decorativo; i pulsanti intenzionalmente colorati mantengono i colori del tema.
- Tema 1: #DAF301 #E9AE08 #EB8403 #F0420D #D23876 #B435A8 #9937C8 #5A29DA #2D2383.
- Tema 2: #A72F38 #E2575A #EF8F55 #F8DEBB #49A1A5 #1D3058 #9985C2 #773661 #5B5398.
- Tema 3: #FDA24B #FB6204 #CB3C06 #980203 #510207 #5F1564 #5B5398 #A20096 #0C0305 (conservato il nero, scartato #380842).
- Ogni palette ha nove colori e ricomincia dal primo al tier 10. Le anteprime degli altri temi sono quelle reali, non alternative inventate.
- La UI può scorrere su schermi compatti; il solver fisico, i contenitori e le condizioni di sconfitta restano invariati. In questa PR sono stati applicati anche la nuova formula (base 10), il primo obiettivo minimo di 1.500, i raggi maggiorati (+12% circa) e il nuovo HUD ispirato alla pagina 4 di di partenza.
- La schermata partita riprende **lo stesso titolo animato della Home** tramite `BlobTitle`, mostra PUNTI/OBIETTIVO in due riquadri accentati, il prossimo blob nel vassoio avorio e i controlli SCUOTI/POP (nella vecchia UI entrambe sempre visibili). La UI attuale mostra POP solo in infinita. Interno recipiente #E6DFE3 a circa 17% di opacità; particelle sospese dello stesso bianco con **movimento, numero, distribuzione, dimensione e alpha originali invariati**.
- La CI Android esegue i test automatici e compila l'APK. **Il collaudo manuale sul telefono resta separato dalla verifica CI**: soprattutto contrasto, salvataggi e resa dei recipienti.

## Gioco
- Home minimale: titolo **Lava Blob** con ogni lettera deformata in tempo reale; sei livelli per pagina, swipe orizzontale.
- **12 livelli iniziali** con forme di contenitore differenti. Sblocchi il seguente completando il precedente; puoi rigiocarli senza limiti. Ogni livello richiede un punteggio minimo e presenta 2 o 3 obiettivi extra. **Le soglie sono provvisorie** e saranno tarate con le partite della giocatrice.
- **Modalità infinita immediata**: scegli uno fra tre recipienti. Ogni 2.000 punti (soglia temporanea equivalente alla precedente scala, da calibrare in playtest) si ottiene un POP, accumulabile senza limiti; premendo POP e poi un blob si cancella soltanto quel blob.
- **Game Over corretto**: non controlla più se una bolla scende in basso. Verifica invece se esiste un ingresso valido per il prossimo blob; serve un blocco persistente (7 secondi nei livelli, 10 nell'infinita), durante il quale si può scuotere e, nell'infinita, usare POP.
- Colori: tre temi di partenza, ciascuno con nove colori nell'ordine dei tier; ripetizione ciclica dal tier 10. Lo sfondo e il bordo del recipiente restano cromaticamente fissi.
- Drop soltanto di dimensioni 1–5: **45%, 28%, 16%, 8%, 3%**.
- Punteggio attuale: prima fusione **10**, poi ogni livello moltiplicato per **1,6** e arrotondato all'intero. I primi valori sono 10, 16, 26, 41, 66, 105, 168, 268, 429. Non ci sono bonus combo.
- I blob giganti non si dissolvono e i tier proseguono oltre il nono.
- Scuotimento via accelerometro, registrato solo mentre il gioco è in primo piano.

## Bilanciamento e privacy
Il record della vecchia scala resta archiviato, mentre il nuovo record viene conteggiato separatamente (`record_v2_`) per evitare confronti falsati. Le partite attive precedenti vengono migrate ricostruendo il nuovo punteggio dai conteggi delle fusioni, mantenendo le membrane salvate e il progresso dei livelli. L'aumento dimensionale dei blob resta da collaudare su telefono, soprattutto per le partite riprese.

Le misure delle partite vengono salvate **solo sul telefono** (punteggio, drop, fusioni, dimensione massima, POP, durata, fusioni per livello). Dalla Home, `COPIA DATI PARTITE` copia il JSON delle ultime 50 partite per l'analisi manuale; non esiste backend, raccolta a distanza o account. Il primo obiettivo minimo è 1.500 punti nella nuova scala, come deciso. Gli altri obiettivi dei livelli e gli extra restano ai valori preesistenti in attesa di playtest con i blob maggiorati; la soglia POP di 2.000 resta temporanea.

## Sviluppo
Progetto Android **Lava Blobs** nella radice del repository. Richiede JDK 17, Android SDK 35 e Gradle 8.9. Il vecchio puzzle Rovina non fa più parte del codice attivo.

```bash
gradle :app:testDebugUnitTest :app:assembleDebug
```

Il workflow `Build Lava Blobs APK` produce un artifact installabile. Debug APK firmati in job diversi potrebbero richiedere disinstallazione della versione precedente: il certificato di debug di GitHub Actions non è stabile.

## Aree da verificare con il telefono
- Collisioni delle membrane dentro recipienti concavi e al soffitto; non alterare gratuitamente i parametri della 0.5.
- Assenza di false sconfitte dopo una scossa e usabilità dell'POP durante il conto alla rovescia.
- Leggibilità del titolo animato e spaziature su display di diversa altezza.
- Difficoltà e percentuali di completamento, per rivedere le soglie.

## Prove contenitore — versione 1.0.1

Dalla Home premi **TEST VASETTI**. Tre varianti della stessa forma e larghezza sono accessibili fin da subito: **A = 285**, **B = 340**, **C = 400** unità di altezza. Le prove sono indipendenti dallo sblocco livelli; il livello 1 normale usa la variante B provvisoriamente. È importante provare tutte e tre per confrontare punteggio, drop, fusioni e durata, quindi copiare i dati con **COPIA DATI PARTITE**. Nel JSON ogni sessione porta `mode: vessel_trial` e `vesselHeight`.

I contenitori dei livelli hanno altezze fisse per forma, non crescono in altezza a seconda del dispositivo. Contenitore e pannello di lancio vengono centrati come blocco unitario sotto l'HUD; il pannello è alto 100 unità invece di estendersi fino al bordo inferiore. Le viste sono ospitate in un contenitore che applica l'inset inferiore della barra di navigazione Android; tutto il contenuto interattivo rimane in area sicura. Le molle, il pressure solver, l'adesione e il movimento dei blob della 0.5.0 non sono modificati.

Le soglie-obiettivo restano quelle provvisorie della 1.0.0 finché non saranno disponibili le misure delle prove; non adattarle arbitrariamente in base al solo screenshot con vasetto sproporzionato.

## 1.0.2 — partita riprendibile, forme e Home

- **Un solo salvataggio di partita in corso**: snapshot locale completo e atomico delle posizioni/velocità delle 24 particelle per blob, livelli, stato della membrana, legami viscosi, RNG, prossimo lancio, punti, record, POP e conto alla rovescia di Game Over. Home → Continua riprende la stessa partita. Al ritorno alla Home e durante il ciclo di vita Android viene salvato automaticamente; una nuova partita sovrascrive la precedente **dopo una conferma**.
- Il salvataggio dello stato di gioco è separato dai progressi sbloccati e dal report `COPIA DATI PARTITE`; mettere in pausa non crea una nuova sessione di playtest.
- **Colori originali HEX pieni e opachi**, senza shader o filtri sul riempimento; la palette non è stata arbitrariamente alterata. Su display diversi i blu possono comunque apparire simili poiché alcuni valori di partenza sono affini.
- **Geometrie più caratteristiche**: maggiori rientranze, strozzature e asimmetrie, senza cambiare il solver approvato.
- **Home riprogettata**: pulsante Continua, cards con miniatura separata dai testi, coordinate di clipping della miniatura e nuovo contrasto cromatico. Conservato l'effetto visivo all'interno del vetro.
- **Firma APK**: le build debug prodotte da runner GitHub differenti possono essere firmate con chiavi differenti; finché non si collega una chiave di firma persistente, Android non permetterà sempre l'installazione sopra la 1.0.1 conservando i dati. Non chiedere mai di disinstallare senza avvertire del rischio di perdita dei progressi.
- **Test**: roundtrip completo delle membrane, RNG, progressi di gioco, geometrie e regressioni fisiche; richiede ancora collaudo visivo reale sul telefono.

## Distribuzione (storia della versione 1.0.4 e stato attuale)

- Il workflow `.github/workflows/lava-blobs-production.yml` pubblica **solo il pacchetto principale** `com.maryvellous.lavablobs`; le release storiche includono `lava-blob-v1.0.4` e l'attuale `lava-blob-v1.0.6`. Dopo la separazione del repository, la pubblicazione si avvia **solo manualmente** (`workflow_dispatch`), non a ogni push su `main`.
- L'app `com.maryvellous.lavablobs.preview` è separata e rimane unicamente uno strumento di test; **non fa parte della release principale**.
- L'APK generato in GitHub Actions usa la **firma debug del runner**: potrebbe non essere compatibile con la firma dell'APK già installato sul telefono. Un aggiornamento senza perdere progressi richiede lo stesso certificato; **non disinstallare né cancellare i dati della vecchia app senza averli preservati e senza consenso esplicito**. La build non è destinata a Google Play.
- Il vecchio workflow Rovina Puzzle (`android-release.yml`) e i sorgenti del puzzle non sono più presenti nel branch principale. Le release/tag storici di Rovina restano conservati nella cronologia GitHub.
