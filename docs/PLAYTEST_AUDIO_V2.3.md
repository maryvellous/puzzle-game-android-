# Protocollo di collaudo audio
**Baseline V2.3 / app 1.1.0 · 27 novembre 2026**

Preview cfb03c9 approvata sul telefono («è molto molto buono»); main acf6d2b, [CI verde](https://github.com/maryvellous/puzzle-game-android-/actions/runs/46060029269). La build main non è stata collaudata separatamente sul telefono. Segnalazione successiva: lieve frittura con molte collisioni simultanee. Non attribuirla a clipping senza misure.

Per ogni nuova preview:
1. Verificare SHA/versione e package preview separato. Volume app 100%, volume telefono costante; aspettare AUDIO V2 · PRONTO.
2. Rilasciare blob di varie taglie (swoosh), urtare vetro e gel separatamente, verificare POP e fusioni singole/cascate.
3. Riempire vaso, scuotere piano/forte, stress ripetibile; annotare distorsione, latenza, suoni sovrapposti. Registrare se possibile video/audio e log locali.
4. Lasciare a riposo per 15–20 s: nessun ronzio/ripetizione. Testare mute/ripristino e slider 100/50/0%, drag senza lanci, pausa/ripresa.
5. A/B a pari condizioni per fase 2: baseline vs clustering; valutare coerenza fisica, timbro, bilanciamento, pulizia, sincronia 1–5. Verificare che gli urti singoli non cambino e fusioni restino leggibili.
6. Misurare prima di dichiarare causa: underruns, queue age/depth, gain reduction e tempi di elaborazione, voci attive. Non pubblicare automaticamente.

[Specifica](AUDIO_ENGINE_V2_MATERIAL_BLOOP.md) · [Piano fase 2](AUDIO_AGGREGATION_PLAN.md)
