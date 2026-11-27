# Collaudo V2.3 (telefono)

Installare esclusivamente l'artifact `lava-blobs-audio-v2.3-gameplay-test-apk` della run più recente del workflow Audio V2, corrispondente al commit finale di questa preparazione. Il pacchetto sperimentale resta `com.maryvellous.lavablobs.audiov21`; la 1.0.11 di produzione non viene modificata. Se Android rifiuta l'aggiornamento per firma diversa, disinstallare soltanto la preview (si perde il suo salvataggio), non la release stabile.

## Sequenza di ascolto

1. Lasciare volume app al 100%, volume telefono uguale alla prova V2.2.
2. Rilasciare blob di dimensioni diverse: verificare il ritorno dello swoosh breve.
3. Usare un vaso quasi vuoto: distinguere il suono contro il vetro dall'urto morbido fra due blob.
4. Riempire il vaso e scuotere piano e forte: confrontare differenza di intensità; ascoltare squish e fusioni.
5. Lasciare il vaso fermo per 15–20 secondi: nessun ronzio o ripetizione statica.
6. Provare 100/50/0%, toccare l'icona per ripristinare, trascinare rapidamente fino a zero e risalire, riaprire l'app per verificare persistenza. Il gesto del volume non deve lanciare un blob.
7. Ripetere i cinque punteggi del collaudo V2.2: coerenza fisica, timbro, bilanciamento, pulizia, sincronizzazione. Aggiungere un commento su rilascio, vetro vs gel e slider.

Gate: test JVM, APK preview, verifiche offline già configurate nel workflow; nessuna promozione su main fino ad approvazione su telefono. Le vecchie audizioni CLI non includono ancora eventi parete e usano il fallback di rilascio senza sorgente opzionale: non sostituiscono il collaudo della preview reale.
