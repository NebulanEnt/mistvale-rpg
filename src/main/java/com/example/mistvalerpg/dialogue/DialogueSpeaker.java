package com.example.mistvalerpg.dialogue;

import java.util.function.Supplier;

import com.example.mistvalerpg.MistvaleRpg;
import com.example.mistvalerpg.ModSounds;

import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;

/**
 * Whoever is talking in a dialogue box. Each speaker has a name, a colour for the name plate,
 * a voice (the little "bip" sounds that play while the text types in) and a portrait for every emotion.
 */
public enum DialogueSpeaker {
    SPIRIT("spirit", 0x7FE6D6, ModSounds.SPIRIT_VOICE::get);

    private final String id;
    private final int colour;
    private final Supplier<SoundEvent> voice;

    DialogueSpeaker(String id, int colour, Supplier<SoundEvent> voice) {
        this.id = id;
        this.colour = colour;
        this.voice = voice;
    }

    public String id() {
        return id;
    }

    /** The name on the plate above the dialogue box, in the speaker's colour. */
    public Component displayName() {
        return Component.translatable("speaker.mistvale_rpg." + id).withColor(colour);
    }

    public SoundEvent voice() {
        return voice.get();
    }

    /** The square portrait picture for one emotion: textures/gui/portrait/spirit_happy.png and so on. */
    public ResourceLocation portrait(Emotion emotion) {
        return ResourceLocation.fromNamespaceAndPath(MistvaleRpg.MODID, "textures/gui/portrait/" + id + "_" + emotion.id() + ".png");
    }
}
