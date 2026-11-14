package com.example.mistvalerpg;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * Every sound the mod adds. The sound files themselves are listed in sounds.json.
 */
public final class ModSounds {
    public static final DeferredRegister<SoundEvent> SOUNDS = DeferredRegister.create(Registries.SOUND_EVENT, MistvaleRpg.MODID);

    // Rifts
    public static final DeferredHolder<SoundEvent, SoundEvent> RIFT_OPEN = register("rift_open");
    public static final DeferredHolder<SoundEvent, SoundEvent> RIFT_CLOSE = register("rift_close");
    // The pull and the landing when you walk through a rift
    public static final DeferredHolder<SoundEvent, SoundEvent> WARP_CHARGE = register("warp_charge");
    public static final DeferredHolder<SoundEvent, SoundEvent> WARP_BLOW = register("warp_blow");
    public static final DeferredHolder<SoundEvent, SoundEvent> WARP_ARRIVE = register("warp_arrive");
    // The darker versions used by the Corrupted Rift
    public static final DeferredHolder<SoundEvent, SoundEvent> VOID_CHARGE = register("void_charge");
    public static final DeferredHolder<SoundEvent, SoundEvent> VOID_DEVOUR = register("void_devour");
    public static final DeferredHolder<SoundEvent, SoundEvent> VOID_ARRIVE = register("void_arrive");
    // The Spirit
    public static final DeferredHolder<SoundEvent, SoundEvent> SPIRIT_APPEAR = register("spirit_appear");
    public static final DeferredHolder<SoundEvent, SoundEvent> SPIRIT_VANISH = register("spirit_vanish");
    public static final DeferredHolder<SoundEvent, SoundEvent> SPIRIT_VOICE = register("spirit_voice");

    private static DeferredHolder<SoundEvent, SoundEvent> register(String name) {
        return SOUNDS.register(name, () -> SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath(MistvaleRpg.MODID, name)));
    }

    private ModSounds() {
    }
}