package com.maryvellous.lavablobs;

/** UI-facing audio controls only. Physical events originate in BlobWorld. */
interface GameSound {
    boolean effectsEnabled();
    void setEffectsEnabled(boolean enabled);
    default boolean supportsVolumeSlider(){return false;}
    default int volumePercent(){return effectsEnabled()?100:0;}
    default void setVolumePercent(int percent){setEffectsEnabled(percent>0);}
    void pause();
    void resume();
    void reset();
    void release();
    /** Diagnostic banner, or null when using the normal shipping sound. */
    String diagnosticStatus();
}
