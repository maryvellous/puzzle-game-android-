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
        public Material(float[] complete,float[] attack,float[] tail) {
            this.complete=validated(complete,"complete",512);
            this.attack=validated(attack,"attack",96);
            this.tail=validated(tail,"tail",96);
            if(this.attack.length>=this.complete.length ||
                    this.tail.length>=this.complete.length)
                throw new IllegalArgumentException("Partial must be shorter than full Bloop");
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
