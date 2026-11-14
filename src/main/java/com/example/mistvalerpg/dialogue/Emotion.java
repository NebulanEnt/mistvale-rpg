package com.example.mistvalerpg.dialogue;

import com.example.mistvalerpg.spirit.SpiritEntity;

/**
 * How a speaker looks and sounds in one line of dialogue. Each emotion picks the portrait picture
 * and, for the Spirit, the mood of her floating 3D model.
 */
public enum Emotion {
    NEUTRAL("neutral", SpiritEntity.MOOD_NEUTRAL, 1.0F),
    HAPPY("happy", SpiritEntity.MOOD_HAPPY, 1.15F),
    SAD("sad", SpiritEntity.MOOD_SAD, 0.82F),
    ANGRY("angry", SpiritEntity.MOOD_ANGRY, 0.9F),
    SCARED("scared", SpiritEntity.MOOD_SHOCK, 1.3F);

    private final String id;
    private final int spiritMood;
    private final float voicePitch;

    Emotion(String id, int spiritMood, float voicePitch) {
        this.id = id;
        this.spiritMood = spiritMood;
        this.voicePitch = voicePitch;
    }

    /** Used in the portrait file name, for example spirit_happy.png. */
    public String id() {
        return id;
    }

    /** The matching mood number of the Spirit entity. */
    public int spiritMood() {
        return spiritMood;
    }

    /** Sad voices are lower and scared ones squeakier. */
    public float voicePitch() {
        return voicePitch;
    }
}
