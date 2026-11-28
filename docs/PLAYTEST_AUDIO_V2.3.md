# Protocollo di collaudo audio
**Baseline vigente: refined + aggregazione, CI #238 · 28 novembre 2026**

La preview è risultata più fluida e senza frittino percepito nel collaudo utente. Conservare questo riferimento. I successivi test devono cercare regressioni, non ritarare il timbro arbitrariamente.

1. Verificare SHA/versione, package `com.maryvellous.lavablobs.audioaggregation` e banner AUDIO · REFINED CLUSTER · PRONTO nella preview. Volume app e dispositivo costanti.
2. Provare lancio, vetro, gel, squish, fusioni singole/cascate, POP in endless; lasciare a riposo per 15–20 s. Silenzio dopo le code.
3. Riempire contenitore, scuotere piano/forte, confrontare reattività, artefatti e scatti con #238.
4. Verificare slider 100/50/0%, mute/ripristino, pause/resume rapide, home/riapertura, restart, checkpoint e game over. Nessun evento stantio.
5. Solo se emergono difetti, catturare logcat LavaPerf e LavaV2Audio nello stesso intervallo (gap, p99 render/dispatch, coda, underruns), video facoltativo. Il trace locale della preview non equivale a telemetria remota e non prova i tempi hardware.
6. Build normale: nessun banner, stesso motore; testare installazione e aggiornamento compatibili con la firma senza cancellare salvataggi. Non pubblicare automaticamente.

[Specifica](AUDIO_ENGINE_V2_MATERIAL_BLOOP.md) · [Roadmap](TECHNICAL_ROADMAP.md) · [Collaudo privato](PRIVATE_PLAYTEST.md)
