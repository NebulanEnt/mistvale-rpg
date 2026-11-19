package com.example.mistvalerpg;

import java.util.List;
import java.util.function.Supplier;

import com.mojang.serialization.Codec;

import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

/**
 * Things the mod remembers about each player. An "attachment" is a small piece of data that Minecraft
 * saves together with the player, so it survives logging out, dying and server restarts.
 */
public final class ModAttachments {
    public static final DeferredRegister<AttachmentType<?>> ATTACHMENTS = DeferredRegister.create(NeoForgeRegistries.ATTACHMENT_TYPES, MistvaleRpg.MODID);

    /** The ids of every dialogue scene the player has already seen to the end. */
    public static final Supplier<AttachmentType<List<String>>> SEEN_SCENES = ATTACHMENTS.register("seen_scenes",
            () -> AttachmentType.<List<String>>builder(() -> List.of())
                    .serialize(Codec.STRING.listOf())
                    .copyOnDeath() // dying must not make the Spirit forget you
                    .build());

    /** Which of the four lands the player chose ("north", "west", "south", "east"), or "" while they have not chosen. */
    public static final Supplier<AttachmentType<String>> REGION = ATTACHMENTS.register("region",
            () -> AttachmentType.builder(() -> "")
                    .serialize(Codec.STRING)
                    .copyOnDeath()
                    .build());

    /** The number of the player's private sea in the prologue (see SeaSlots), or -1 while they have none. */
    public static final Supplier<AttachmentType<Integer>> SEA_SLOT = ATTACHMENTS.register("sea_slot",
            () -> AttachmentType.builder(() -> -1)
                    .serialize(Codec.INT)
                    .copyOnDeath()
                    .build());

    private ModAttachments() {
    }
}
