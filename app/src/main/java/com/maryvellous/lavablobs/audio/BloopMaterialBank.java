package com.maryvellous.lavablobs.audio;

/**
 * The only source of acoustic material in the V2 engine.
 *
 * Full fusion samples are never reconstructed from fragments. Attack and tail
 * arrays are derived offline from those same four original Bloop recordings.
 * The Android adapter will later load these PCM arrays off the audio thread;
 * the desktop harness loads identical arrays from generated WAV files.
 *
 * Pure Java: no Android, desktop audio, or external dependency.
 */
public final class BloopMaterialBank {
    public static final int SOURCE_COUNT=4;
    public static final int SAMPLE_RATE=44100;

    public static final class Material {
        public final float[] complete, attack, tail;
        final float[] texture;
        final float textureTrim;

        public Material(float[] complete,float[] attack,float[] tail) {
            this.complete=validated(complete,"complete",512);
            this.attack=validated(attack,"attack",96);
            this.tail=validated(tail,"tail",96);
            if(this.attack.length>=this.complete.length ||
                    this.tail.length>=this.complete.length)
                throw new IllegalArgumentException("Partial must be shorter than full Bloop");
            texture=makeSeamlessTexture(this.tail);
            float sum=0f;
            for(float v:texture)sum+=v*v;
            float rms=(float)Math.sqrt(sum/texture.length);
            textureTrim=rms>1e-7f?Math.min(4f,.14f/rms):1f;
        }

        private static float[] validated(float[] original,String label,int minimum) {
            if(original==null||original.length<minimum)
                throw new IllegalArgumentException("Missing/short "+label);
            float[] copy=original.clone();
            for(float s:copy)
                if(!Float.isFinite(s)||Math.abs(s)>1.01f)
                    throw new IllegalArgumentException("Corrupt PCM "+label);
            return copy;
        }

        /**
         * One period from 3 overlapping Hann-windowed copies of the actual
         * Bloop tail. Periodic blending avoids reattacking the Bloop core.
         * Called only on bank construction, not on the audio callback.
         */
        private static float[] makeSeamlessTexture(float[] tail) {
            int period=Math.max(64,tail.length/3);
            float[] texture=new float[period];
            for(int i=0;i<period;i++) {
                float weighted=0f,total=0f;
                for(int n=i;n<tail.length;n+=period) {
                    double phase=2*Math.PI*(n+.5)/tail.length;
                    float weight=(float)(.5-.5*Math.cos(phase));
                    weighted+=tail[n]*weight;
                    total+=weight;
                }
                texture[i]=total>.0001f?weighted/total:0f;
            }
            // Apply a smooth endpoint correction over the final ~6.7 ms.
            // The last sample then exactly equals the first and a repeated
            // texture period never creates a discontinuity at its boundary.
            int seam=Math.min(period/3,Math.max(8,SAMPLE_RATE/150));
            float delta=texture[0]-texture[period-1];
            for(int i=0;i<seam;i++) {
                float t=(i+1f)/seam;
                float ease=t*t*(3f-2f*t);
                texture[period-seam+i]+=delta*ease;
            }
            return texture;
        }
    }

    private final Material[] items;

    public BloopMaterialBank(Material[] items) {
        if(items==null||items.length!=SOURCE_COUNT)
            throw new IllegalArgumentException("Exactly four original Bloops required");
        this.items=items.clone();
        for(Material item:this.items)
            if(item==null)throw new IllegalArgumentException("Missing Bloop");
    }

    public Material at(int index) {return items[Math.floorMod(index,SOURCE_COUNT)];}
}
