# Lava Blobs — Sound Design C4: urto, sfregamento viscoso, compressione senza laser

**Data:** 21 novembre 2026. **Stato:** esperimento audio offline; ascolto e approvazione artistica ancora necessari. **Release Android:** resta 1.0.11 senza alterazioni.

## 1. Feedback che guida questa iterazione

La revisione C3 non era ancora sufficientemente convincente. Indicazioni esplicite:
- **Urto:** «ci sta abbastanza», inizia a somigliare a ciò che serve, ma la tonalità è ancora un po' **troppo bassa**.
- **Sfregamento:** «vagamente» vicino alla direzione desiderata; deve evocare **due blob che sfregano fra loro**, non una nota o un suono accessorio isolato.
- **Compressione:** non riconoscibile come compressione; evoca «qualcuno che spara con un fucile laser da molto lontano». **Modello respinto**, non correggibile con semplice volume o equalizzazione.
- **Fusione:** rimane il **riferimento positivo e dominante**, invariato nel mix.
- Il lieve disturbo ancora presente nei Bloop ricostruiti resta un problema aperto da controllare in ascolto.

## 2. Strategia C4 (senza modifiche al gioco)

Implementazione sperimentale in [`tools/audio_lab/refine_c4.py`](../tools/audio_lab/refine_c4.py), test in [`tools/audio_lab/test_refine_c4.py`](../tools/audio_lab/test_refine_c4.py).

| Comportamento | Variazioni in prova | Misura di discrezione |
|---|---|---|
| **Urto** | C3 con pitch aumentato del **9%** oppure del **16%** mediante resampling; non si aggiunge un nuovo timbro sintetico | Picco massimo **20%** del riferimento fusione |
| **Sfregamento wet drag** | Texture C3 fatta soltanto dalla coda morbida, con filtraggio e ondulazioni lente controllate dal moto tangenziale | Picco massimo **4,3%** della fusione |
| **Sfregamento adhesive ghost** | Ancora più scuro e appena percettibile; piccole irregolarità di attrito, legate a variazioni della velocità, sempre da materiale C3 | Picco massimo **2,8%** della fusione |
| **Compressione A** | **Silenzio**: il blob che si deforma non riceve obbligatoriamente un effetto sonoro autonomo | **0%** |
| **Compressione B** | Un'ombra di texture non tonale, derivata dalla coda del materiale C3, attiva **solo mentre cambia la deformazione**, mai a forma statica | Picco massimo **1,5%** della fusione |
| **Fusione** | WAV già ritenuto valido, **senza alcuna modifica** | **100%** |

Gli indici di picco sono limiti di sicurezza nel prototipo, non misure complete della percezione sonora. Un contatto sostenuto potrebbe ancora risultare invasivo, quindi serve l'ascolto, anche con musica futura e più blob.

### Principio centrale

**La compressione non deve per forza avere una voce.** Può modificare il comportamento del materiale quando è già eccitato da urto o scorrimento, oppure restare silenziosa. In questo esperimento A è proprio il silenzio; B verifica se una sfumatura leggerissima aggiunga qualcosa senza creare un «pew» autonomo.

**Lo sfregamento è un fenomeno continuo, non un trigger.** Le due texture si percepiscono soltanto mentre due membrane rimangono in contatto e scorrono. L'intensità risponde alla velocità tangenziale e alle sue variazioni; non si avvia un file intero ogni volta che il solver rileva il contatto.

## 3. File da ascoltare

Il workflow [`two-blob-acoustic-prototype.yml`](../.github/workflows/two-blob-acoustic-prototype.yml) genera l'artifact temporaneo **`lava-blobs-c4-organic-impacts-friction-compression`**, scaricabile dalla scheda *Actions* della PR. Contiene i file in `two_blob_c4/`:

- `A_impact_C3_vs_C4_pitch.wav`: vecchio urto C3 → nuovo +9% → nuovo +16% → **fusione invariata**.
- `B_friction_C3_vs_C4_wet.wav`: sfregamento C3 → wet drag → adhesive ghost → **fusione invariata**.
- `C_compression_silent_vs_whisper.wav`: **silenzio volontario** → fruscio minimo → fusione; se il primo segmento non si sente, è corretto.
- `D_mix_new_impact_friction_compression_fusion.wav`: urto +9%, wet drag, compressione whisper, fusione.
- `E_mix_impact_friction_no_compression_fusion.wav`: urto +9%, adhesive ghost, **nessuna compressione**, fusione.
- Sei WAV individuali, `fusion_anchor_UNCHANGED.wav`, `C4_METRICS.json` e `LISTENING_GUIDE.txt`.

Le tracce di movimento sono le stesse **misurate dal vero solver Java di `BlobWorld`**, non ricostruzioni inventate. Il renderer C4 lavora offline e non richiede alcun aggiornamento dell'APK.

## 4. Test e limiti

Test automatici previsti:
1. Picchi dell'impatto entro i limiti, output finito, alternative udibilmente **diverse** come segnale.
2. Nessuno sfregamento se la velocità tangenziale è zero **o** se manca il contatto.
3. Nessun suono di compressione quando il blob mantiene una forma deformata ma **statica**.
4. Deformazione in movimento può eccitare il fruscio B, sempre molto debole.
5. Render dei WAV C4 da C3 + CSV reale di fisica e pubblicazione di un artifact scaricabile.

**Limiti e gate artistico:** il fatto che i test passino non dimostra che i suoni sembrino davvero provenire dai blob. Nessun urto, compressione o sfregamento C4 è «approvato» prima di ascolto diretto; nemmeno il confronto di tonalità sostituisce una partita reale. Conserveremo sempre la fusione originale e il motore classico della 1.0.11.

## 5. Decisione dopo l'ascolto

Se l'urto convince, lo useremo come direzione. Se lo sfregamento non evoca ancora un contatto viscoso, ne rivedremo **il timbro**, non soltanto il volume. Per la compressione valutare seriamente **A (nessun suono autonomo)** come soluzione definitiva. Solo dopo un insieme convincente realizzeremo l'integrazione nel gioco con motore audio separato, prove di sincronizzazione e performance reali.
