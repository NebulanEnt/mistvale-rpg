package com.example.mistvalerpg.dialogue;

import java.util.function.Supplier;

import com.example.mistvalerpg.MistvaleRpg;
import com.example.mistvalerpg.ModSounds;

import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;

/**
 * Whoever is talking in a dialogue box. Each speaker has a name, a colour for the name plate,
 * a voice (the little "bip" sounds that play while the text types in) and a portrait.
 * The Spirit's portrait is a set of pictures, one per emotion. The player's portrait is a live
 * picture of the player's own character, so it has no pictures at all.
 */
public enum DialogueSpeaker {
    // The numbers are the old mod's: the Spirit types at 26 letters a second with a steady pitch,
    // the player a little faster with a slightly wobbling pitch.
    SPIRIT("spirit", 0x7FE8D8, false, new Voice(ModSounds.SPIRIT_VOICE::get, 1.0F, 0.0F, 2, 0.5F, 26)),
    PLAYER("player", 0xF2D49B, true, new Voice(ModSounds.PLAYER_VOICE::get, 1.0F, 0.1F, 2, 0.5F, 30));

    /**
     * How somebody's voice sounds while their text types in.
     *
     * @param sound           the sound event with the little voice clips
     * @param pitch           1.0 is the clip as recorded, higher is squeakier
     * @param variance        how much the pitch wobbles from bip to bip: 0.1 means up to 10% either way
     * @param interval        a bip plays on every Nth letter
     * @param volume          0 to 1
     * @param charsPerSecond  how fast the text types
     */
    public record Voice(Supplier<SoundEvent> sound, float pitch, float variance, int interval, float volume, int charsPerSecond) {
    }

    private final String id;
    private final int colour;
    private final boolean live;
    private final Voice voice;

    DialogueSpeaker(String id, int colour, boolean live, Voice voice) {
        this.id = id;
        this.colour = colour;
        this.live = live;
        this.voice = voice;
    }

    public String id() {
        return id;
    }

    public int colour() {
        return colour;
    }

    /** The name on the plate above the dialogue box, in the speaker's colour. The player's plate shows the player's own name. */
    public Component displayName(String playerName) {
        return Component.translatable("speaker.mistvale_rpg." + id, playerName).withColor(colour);
    }

    public Voice voice() {
        return voice;
    }

    /** True when the portrait is a live 3D picture of the player's character instead of a flat picture. */
    public boolean isLive() {
        return live;
    }

    /** The square portrait picture for one emotion: textures/gui/portrait/spirit_happy.png and so on. */
    public ResourceLocation portrait(Emotion emotion) {
        return ResourceLocation.fromNamespaceAndPath(MistvaleRpg.MODID, "textures/gui/portrait/" + id + "_" + emotion.id() + ".png");
    }
}
