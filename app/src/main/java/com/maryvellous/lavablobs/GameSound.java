package com.maryvellous.lavablobs;

/** Audio facade. All gameplay builds now select Audio V2.3. */
interface GameSound {
    boolean effectsEnabled();
    void setEffectsEnabled(boolean enabled);
    default boolean supportsVolumeSlider(){return false;}
    default int volumePercent(){return effectsEnabled()?100:0;}
    default void setVolumePercent(int percent){setEffectsEnabled(percent>0);}
    void merge(BlobWorld.Merge merge);
    void launch(int tier,float radius);
    void pop(int blobId,float radius);
    void discardLegacyImpacts();
    void pause();
    void resume();
    void reset();
    void release();
    /** Diagnostic banner, or null when using the normal shipping sound. */
    String diagnosticStatus();
}

/** Historical SoundPool adapter retained for reference; not selected by LavaView. */
final class ClassicGameSound implements GameSound {
    private final GameAudio audio;
    ClassicGameSound(android.content.Context context) {
        audio=new GameAudio(context);
    }
    @Override public boolean effectsEnabled(){return audio.effectsEnabled();}
    @Override public void setEffectsEnabled(boolean enabled){audio.setEffectsEnabled(enabled);}
    @Override public void merge(BlobWorld.Merge merge){audio.merge(merge.tier);}
    @Override public void launch(int tier,float radius){audio.launch(tier);}
    @Override public void pop(int blobId,float radius){audio.pop();}
    @Override public void discardLegacyImpacts(){audio.impact();}
    @Override public void pause(){audio.pause();}
    @Override public void resume(){audio.resume();}
    @Override public void reset(){}
    @Override public void release(){audio.release();}
    @Override public String diagnosticStatus(){return null;}
}
