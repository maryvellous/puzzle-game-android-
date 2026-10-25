# Lava Blobs — Android

Un piccolo gioco originale per Android, ispirato alla soddisfazione dei merge puzzle e al movimento di una lava lamp. **Le bolle salgono**, anziché cadere. Tocca, trascina orizzontalmente e rilascia per lanciare un blob; due blob uguali si fondono in uno più grande. Due blob di livello massimo si dissolvono e liberano spazio.

## Aspetto e fisica
- Palette esclusivamente indaco, viola, ametista e lavanda; nessun rosso, giallo o verde.
- Ogni blob ha **20 punti su un perimetro elastico**: gli urti comprimono la superficie effettiva, le molle tra vicini trasmettono le onde, smorzamento e oscillazioni ricostruiscono lentamente la forma. Le collisioni e i bordi usano direttamente il profilo visibile, non un secondo cerchio rigido.
- Le masse hanno galleggiamento verso l'alto, attrito viscoso, urti con pareti e fra blob, trasferimento della quantità di moto e deformazioni di contatto.
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

## Struttura
- `BlobWorld.java`: simulazione e punteggio senza dipendenze Android, testabile con JUnit.
- `Blob.java`: deformazione elastica a 20 nodi.
- `LavaView.java`: rendering procedurale, input, particelle e UI.
- `MainActivity.java`: host Android fullscreen.

## Stato
Versione 0.2.0: sistemata la falsa hitbox circolare evidenziata dal primo test su telefono. Ora collisione e rendering condividono lo stesso contorno deformabile. La CI verifica la compilazione e la logica essenziale; il feeling fisico e le prestazioni su dispositivi reali richiedono un collaudo sul telefono.
