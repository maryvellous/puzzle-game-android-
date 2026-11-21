# Lava Blobs — Audio Size Test: massa acustica, pitch e durata

**22 novembre 2026 · test Android in corso · non è una release del gioco.**

## Feedback del primo playtest installato

L'utente ha installato la prima APK Audio Test e riferisce che **funziona meglio del previsto**. In particolare, gli eventi sonori si collegano in modo credibile al movimento reale dei blob. Il difetto prioritario è però la **mancanza di differenziazione timbrica per taglia**: due blob piccoli e due grandi producono quasi lo stesso urto o sfregamento. Il comportamento desiderato, già previsto nel progetto, è **più grande = più grave e più lento**.

Non risintetizzare i Bloop, non cambiare la soglia degli eventi, la fisica o il volume degli effetti che stanno già funzionando. Questa iterazione agisce esclusivamente sul **playback rate** dei suoni di collisione e delle texture di sfregamento/compressione della prova.

## Implementazione della nuova APK

La nuova build si chiama **`Lava Blob - Audio Size Test`**:

- Build type: `audioSizeTest`.
- Application ID: `com.maryvellous.lavablobs.audiosizetest`, diverso dalla **1.0.11 normale** e dalla precedente **Audio Test**.
- Versione: `1.0.11-audio-size-test`.
- Nella schermata di gioco compare `AUDIO SIZE TEST - NON DEFINITIVO`.
- Gli stessi tre WAV C4.1 usati nella precedente APK sono riutilizzati **senza modificarne i campioni** mediante il source set `audioTest/res`.
- Nessuna modifica al punteggio, alle collisioni, agli algoritmi della membrana, alle preferenze audio normali o ai salvataggi della release principale.

### Quale dimensione usiamo?

`BlobWorld.Impact` ora trasporta i **due raggi effettivi della membrana** che ha rilevato al momento dell'urto (`radiusTarget`). La nuova misura del volume acustico della coppia è

\[
r_{\mathrm{eff}}=\sqrt[3]{\frac{r_a^3+r_b^3}{2}}\,.
\]

È la radice cubica del volume medio delle due sfere ideali, non una classificazione arbitraria in piccoli/grandi e non un calcolo della geometria di collisione. È una **proxy artistica** per un gioco a bolle 2D: determina il colore del suono, non la fisica.

Il fattore di playback, applicato a pitch e durata contemporaneamente dal `SoundPool`, è

\[
f(r)=\operatorname{clamp}\left(\left(\frac{32}{r}\right)^{0.46},\,0.56,\,1.34\right).
\]

È riferito al raggio di un blob di tier 2. Un corpo piccolo ha un rate maggiore di 1 (**più acuto e rapido**), uno grande minore di 1 (**più grave e lento**).

| Coppia (raggio dei blob) | Raggio effettivo circa | Playback circa |
|---|---:|---:|
| 20 / 26 | 23,4 | 1,15× |
| 32 / 40 | 36,4 | 0,94× |
| 77 / 94 | 86,3 | 0,64× |
| 111 / 111 | 111 | 0,56× |

Le velocità di impatto influenzano l'**intensità**, non l'intonazione. Per lo sfregamento, il rate prende un raggio medio ponderato dall'intensità dei contatti adesivi **realmente in scorrimento**. La compressione usa le coppie realmente deformate. Durante uno stesso contatto, il rate si interpola con un tempo caratteristico circa **100 ms**; quando inizia un nuovo suono parte direttamente dalla taglia corretta.

### Suoni che rimangono identici

- **Fusione:** Bloop originali con il loro lieve adattamento di pitch già esistente (in questa iterazione non si ritocca ciò che piace).
- **Urto, sfregamento adhesive e compressione B:** gli **stessi identici timbri** che erano nell'APK già provata, ora accelerati o rallentati a seconda della dimensione.
- **Lancio e POP:** invariati.

## Verifiche e ascolto

Sono presenti test JVM che dimostrano:
- `smallRate > mediumRate > largeRate`, con separazione evidente e limiti compatibili con SoundPool.
- Il raggio acustico della coppia tiene conto del volume; due corpi di dimensioni diverse non sono ridotti alla media del loro tier.
- I contatti trasportano i raggi reali, senza cambiare il risolutore fisico.
- Le transizioni del pitch sono regolari e non avvengono di colpo nel mezzo dello sfregamento.
- Tutti i test di fisica esistenti devono passare.

Workflow: [`.github/workflows/lava-blob-audio-test.apk.yml`](../.github/workflows/lava-blob-audio-test.apk.yml). Scaricare dalla run GitHub Actions l'artifact **`LavaBlob-AudioSizeTest-installable-APK`** ed estrarne **`LavaBlob-AudioSizeTest.apk`**.

**Test consigliato:** tenere aperte entrambe le app di prova e confrontare urti/sfregamenti fra blob piccoli e grandi. La nuova APK può essere installata *accanto* alla vecchia Audio Test e alla release normale, senza disinstallare nulla. Non è ancora testata su un dispositivo reale, anche dopo una CI riuscita.

## Decisione successiva

L'obiettivo non è rendere tutti gli effetti più alti o più bassi, ma far percepire chiaramente la massa gelatinosa di ogni interazione. Se la variazione risulta eccessiva o innaturale, intervenire su **esponente 0,46, limiti 0,56–1,34 e smorzamento 100 ms**, senza cambiare la sorgente dei suoni che è già promettente. Se funziona, potremo portare la stessa regola nel DSP procedurale definitivo.
