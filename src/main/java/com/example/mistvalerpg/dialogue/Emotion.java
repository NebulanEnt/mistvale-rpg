package com.example.mistvalerpg.dialogue;

import com.example.mistvalerpg.spirit.SpiritEntity;

/**
 * How a speaker looks in one line of dialogue. Each emotion picks the portrait picture
 * and, for the Spirit, the mood of her floating 3D model. (How a speaker SOUNDS belongs to the
 * speaker, not the mood: see {@link DialogueSpeaker.Voice}.)
 */
public enum Emotion {
    NEUTRAL("neutral", SpiritEntity.MOOD_NEUTRAL),
    HAPPY("happy", SpiritEntity.MOOD_HAPPY),
    SAD("sad", SpiritEntity.MOOD_SAD),
    ANGRY("angry", SpiritEntity.MOOD_ANGRY),
    SCARED("scared", SpiritEntity.MOOD_SHOCK);

    private final String id;
    private final int spiritMood;

    Emotion(String id, int spiritMood) {
        this.id = id;
        this.spiritMood = spiritMood;
    }

    /** Used in the portrait file name, for example spirit_happy.png. */
    public String id() {
        return id;
    }

    /** The matching mood number of the Spirit entity. */
    public int spiritMood() {
        return spiritMood;
    }
}