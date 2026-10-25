# Lava Blobs — Android

Un piccolo gioco originale per Android, ispirato alla soddisfazione dei merge puzzle e al movimento di una lava lamp. **Le bolle salgono**, anziché cadere. Tocca, trascina orizzontalmente e rilascia per lanciare un blob; due blob uguali si fondono in uno più grande. Due blob di livello massimo si dissolvono e liberano spazio.

## Aspetto e fisica
- Palette di nove colori distinti: indaco brillante, azzurro chiaro, indaco profondo, lavanda, viola intenso, pervinca, viola scuro, lilla e ametista. Nessun rosso, giallo o verde.
- Ogni blob ha **20 punti su un perimetro elastico**: gli urti comprimono la superficie effettiva, le molle tra vicini trasmettono le onde, smorzamento e oscillazioni ricostruiscono lentamente la forma. Le collisioni e i bordi usano direttamente il profilo visibile, non un secondo cerchio rigido.
- Le masse hanno galleggiamento verso l'alto, attrito viscoso, urti con pareti e fra blob, trasferimento della quantità di moto e deformazioni di contatto.
- **Scuoti il telefono**: l'accelerometro rileva l'accelerazione brusca senza confonderla con la gravità statica. La scossa imprime una spinta reale (anche muovendo il telefono avanti e indietro), provoca collisioni e fa tremolare i 20 nodi elastici. Sensore attivo solo mentre il gioco è in primo piano; nessun permesso speciale o accesso alla rete.
- Merge con pulsazione circolare, gocce di colore, vibrazione aptica e piccola accelerazione verso l'alto.
- Record salvato sul dispositivo; pulsante ricomincia; game over quando il fondo rimane occupato troppo a lungo.
- Grafica interamente disegnata su Android Canvas: niente immagini, librerie di gioco, login, ads o rete. Nessun alone circolare esterno: la superficie visibile corrisponde al corpo che collide.

## Compilare

Progetto Gradle **autonomo** nella cartella `lava-blobs/`, non modifica il puzzle Rovina presente alla radice del repo.

Requisiti: JDK 17, Android SDK 35, Gradle 8.9.

```bash
gradle -p lava-blobs :app:testDebugUnitTest :app:assembleDebug
```

L'APK sarà in `lava-blobs/app/build/outputs/apk/debug/app-debug.apk`. La workflow **Build Lava Blobs APK** lo allega come artifact scaricabile ad ogni build riuscita.

## Controlli
- Tocca un punto o trascina il dito per scegliere la colonna.
- Rilascia per far **salire** il blob.
- Due blob della stessa dimensione si uniscono, facendo punti.
- Il pulsante ↻ ricomincia; alla fine tocca per ripartire.
- Se il punto di lancio è occupato, spostati e riprova.
- **Scuoti fisicamente il telefono** per agitare tutti i blob e farli interagire; funziona anche con movimenti avanti/indietro, non serve inclinarlo. Se il dispositivo non ha accelerometro, tutto il resto del gioco continua a funzionare.

## Struttura
- `BlobWorld.java`: simulazione e punteggio senza dipendenze Android, testabile con JUnit.
- `Blob.java`: deformazione elastica a 20 nodi.
- `LavaView.java`: rendering procedurale, input, particelle e UI.
- `MainActivity.java`: host Android fullscreen e gestione del ciclo di vita del sensore.
- `ShakeDetector.java`: filtro gravità, soglia scossa e cooldown, testabile senza Android.

## Stato
Versione 0.3.0: scuotimento accelerometrico con vere spinte fisiche e palette indaco–azzurro–lavanda–viola più differenziata. Conserva la correzione 0.2.0 alla falsa hitbox circolare. La CI verifica la compilazione, il sensore simulato e la logica fisica; la sensibilità ai gesti e le prestazioni richiedono un collaudo su telefono.
