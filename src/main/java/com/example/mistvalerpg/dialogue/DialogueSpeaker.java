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
 * picture of the player's own character, so it has no pictures at all. Brannoch and his skeleton form are live
 * pictures too, but of an entity: the model is drawn on a backdrop, exactly like the player's.
 */
public enum DialogueSpeaker {
    // The numbers are the old mod's: the Spirit types at 26 letters a second with a steady pitch,
    // the player a little faster with a slightly wobbling pitch.
    SPIRIT("spirit", 0x7FE8D8, false, new Voice(ModSounds.SPIRIT_VOICE::get, 1.0F, 0.0F, 2, 0.5F, 26)),
    PLAYER("player", 0xF2D49B, true, new Voice(ModSounds.PLAYER_VOICE::get, 1.0F, 0.1F, 2, 0.5F, 30)),
    // Captain Brannoch: a gruff pirate. He uses the player's clips, played low and slow. His portrait is his own model, drawn live.
    BRANNOCH("brannoch", 0xD9A05B, false, new Voice(ModSounds.PLAYER_VOICE::get, 0.7F, 0.1F, 2, 0.5F, 22),
            "brannoch", "mistvale_rpg:brannoch", false, 0.78F, 0.22F),
    // Cursed Captain Brannoch, after the light took him: a pale, hollow voice from the old mod's "deep" clips (pitch 1.12, 28 letters
    // a second). His portrait is the skeleton pirate captain's model from Bosses' Rise on a ghostly backdrop. If that mod's captain
    // cannot be found, the flat picture "brannoch_*.png" is shown instead.
    BRANNOCH_BONES("brannoch_bones", 0xB9C7C9, false, new Voice(ModSounds.SKELETON_VOICE::get, 1.12F, 0.12F, 2, 0.5F, 28),
            "brannoch", "block_factorys_bosses:pirate_captain", true, 1.0F, 0.0F);

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
    private final String portraitName;
    private final String portraitEntity;
    private final boolean ghostly;
    private final float zoom;
    private final float lift;

    DialogueSpeaker(String id, int colour, boolean live, Voice voice) {
        this(id, colour, live, voice, id, null, false, 1.0F, 0.0F);
    }

    // "portraitName" is the start of the flat portrait files' names. "portraitEntity" is the id of an entity to draw live instead
    // (null for none), "ghostly" gives that live portrait the cold, misty backdrop instead of the dark square, and "zoom" makes the
    // live model bigger (above 1) or smaller (below 1) in its frame, and "lift" moves it down in the frame by that many blocks.
    DialogueSpeaker(String id, int colour, boolean live, Voice voice, String portraitName, String portraitEntity, boolean ghostly, float zoom, float lift) {
        this.id = id;
        this.colour = colour;
        this.live = live;
        this.voice = voice;
        this.portraitName = portraitName;
        this.portraitEntity = portraitEntity;
        this.ghostly = ghostly;
        this.zoom = zoom;
        this.lift = lift;
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

    /** True when the portrait is a live 3D picture (of the player's character or of an entity) instead of a flat picture. */
    public boolean isLive() {
        return live || portraitEntity != null;
    }

    /** True when the live picture shows the player's own character. */
    public boolean isPlayer() {
        return live;
    }

    /** The registry id of the entity drawn as the portrait, such as "mistvale_rpg:brannoch", or null. */
    public String portraitEntity() {
        return portraitEntity;
    }

    /** How big the live model is in its frame: 1 is the standard head-and-shoulders picture, less is zoomed out. */
    public float portraitZoom() {
        return zoom;
    }

    /** How far the live model is moved down in its frame, in blocks (0 for the standard picture). */
    public float portraitLift() {
        return lift;
    }

    /** True when the portrait gets the cold, misty ghost backdrop. */
    public boolean isGhostly() {
        return ghostly;
    }

    /** The square portrait picture for one emotion: textures/gui/portrait/spirit_happy.png and so on. */
    public ResourceLocation portrait(Emotion emotion) {
        return ResourceLocation.fromNamespaceAndPath(MistvaleRpg.MODID, "textures/gui/portrait/" + portraitName + "_" + emotion.id() + ".png");
    }
}
