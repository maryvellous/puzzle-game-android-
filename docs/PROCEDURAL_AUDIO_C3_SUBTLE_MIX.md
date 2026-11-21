# Lava Blobs — C3: gerarchia sonora e sfregamento appena accennato

**21 novembre 2026 · prototipo offline · nessuna modifica alla build Android 1.0.11.**

## Feedback d'ascolto e decisioni

Dopo la revisione C2, il giudizio è stato **«non ci siamo»**. Tuttavia l'urto C2 *comincia forse ad avere senso*: non scartare subito la sua direzione timbrica, ma ridurlo drasticamente. La fusione resta il riferimento positivo e deve essere **sempre il suono dominante**.

Per lo sfregamento l'indicazione è precisa: **conservare soltanto la parte finale del suono precedente**, senza la parte iniziale più pronunciata, rendendolo **meno vivo, molto meno accentuato e quasi impercettibile**.

La compressione non è ancora approvata. Ridurne il livello può aiutare il mix, ma **non equivale ad aver trovato il timbro corretto**.

## Esperimento C3

Script: [`tools/audio_lab/refine_subtle_mix.py`](../tools/audio_lab/refine_subtle_mix.py). Utilizza gli stessi CSV della fisica reale e i WAV di riferimento di C1 e C2, senza modificare il motore dei blob. I limiti del rapporto **di picco** sono controllati rispetto alla versione di fusione mantenuta intatta:

| Elemento | Sorgente | Limite di picco (rispetto alla fusione) |
|---|---|---:|
| Fusione | Versione C1 invariata | 100% |
| Urto A/B | Due alternative C2, solo attenuate | **21%** |
| Compressione A/B | C2, attenuate, ancora da riprogettare | **10,5%** |
| Sfregamento *whisper* | Solo ultimi ~235 ms udibili dello sfregamento C1, filtrati e letti in modo continuo | **5,5%** |
| Sfregamento *ghost* | Stessa parte finale, ancora più smorzata e scura | **2,7%** |

**Nota:** questi sono tetti di picco, **non** equivalenze di loudness percepito. Un suono continuo può risultare più invasivo di un impulso con picco più alto. Le scelte vanno validate con l'orecchio e poi in un mix con musica.

### Sfregamento: soltanto il finale

Il laboratorio individua la fine della porzione udibile della **vecchia registrazione di sfregamento C1**, ne estrae circa 235 ms e scarta completamente la porzione iniziale più pronunciata. Crea da questo materiale una piccola trama ciclica con sovrapposizione di circa 44 ms e attenua le frequenze acute **solo dello sfregamento**. La velocità tangenziale e la presenza del contatto fisico controllano l'intensità, in modo che un blob fermo non emetta questo suono.

Non si tratta di un nuovo suono aggiunto **alla fine** di ogni contatto: è **la coda timbrica** che viene riutilizzata durante lo scorrimento. Così lo sfregamento rimane coerente con la fisica senza il precedente attacco invadente.

## Confronti da ascoltare

File prodotti dal workflow [`two-blob-acoustic-prototype.yml`](../.github/workflows/two-blob-acoustic-prototype.yml):

- `01_impact_newA_soft_newB_soft.wav`: urti C2 A/B resi più discreti.
- `02_compression_newA_soft_newB_soft.wav`: due prove di compressione molto attenuate, **ancora non approvate**.
- `03_friction_old_tail_whisper_tail_ghost.wav`: sfregamento vecchio → solo coda discreta → solo coda appena percettibile.
- `04_combined_soft_impact_compression_friction_fusion.wav`: confronto della gerarchia fra nuovo urto A, compressione B, sfregamento discreto e **fusione invariata**.
- WAV individuali, `C3_MIX_REPORT.json` e `LISTENING_GUIDE.txt`.

## Criterio di approvazione e lavoro successivo

1. Tutti i suoni non-fusione devono sembrare parte della materia, **senza prendere mai il primo piano**.
2. Lo sfregamento deve essere una presenza quasi subliminale, senza l'energia né l'attacco del precedente.
3. L'urto deve essere percepibile solo quando l'interazione lo giustifica e non deve competere con la fusione.
4. La compressione resta un problema timbrico aperto; nessun via libera finché non risulta credibile.
5. Il lieve crepitio della famiglia C/D rimane un difetto da investigare, **non** qualcosa che i tetti di volume dimostrano risolto.

**Stato:** prova offline pronta per il confronto. Non promuovere gli effetti in `GameAudio` né pubblicare un APK per questa iterazione. La scelta sul suono resta un gate **artistico** oltre che tecnico.
