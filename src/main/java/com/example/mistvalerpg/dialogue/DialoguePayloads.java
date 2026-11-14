package com.example.mistvalerpg.dialogue;

import com.example.mistvalerpg.MistvaleRpg;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * The three little messages a conversation sends back and forth.
 * The server drives the conversation, so the player's game only ever has to show what it is told
 * and say what the player pressed.
 */
public final class DialoguePayloads {

    /** Server to player: show this box of this scene. */
    public record ShowNode(String scene, String node) implements CustomPacketPayload {
        public static final Type<ShowNode> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(MistvaleRpg.MODID, "dialogue_show"));
        public static final StreamCodec<RegistryFriendlyByteBuf, ShowNode> CODEC = StreamCodec.composite(
                ByteBufCodecs.STRING_UTF8, ShowNode::scene,
                ByteBufCodecs.STRING_UTF8, ShowNode::node,
                ShowNode::new);

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    /** Server to player: the conversation is over, close the box. */
    public record End() implements CustomPacketPayload {
        public static final Type<End> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(MistvaleRpg.MODID, "dialogue_end"));
        public static final StreamCodec<RegistryFriendlyByteBuf, End> CODEC = StreamCodec.unit(new End());

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    /**
     * Player to server: "I am done reading this box". For a question, {@code choice} is the answer picked (0, 1, 2 ...);
     * for a plain box it is {@link #CONTINUE}; if the player closed the box with Escape it is {@link #ABORT}.
     */
    public record Advance(int choice) implements CustomPacketPayload {
        public static final int CONTINUE = -1;
        public static final int ABORT = -2;
        public static final Type<Advance> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(MistvaleRpg.MODID, "dialogue_advance"));
        public static final StreamCodec<RegistryFriendlyByteBuf, Advance> CODEC = StreamCodec.composite(
                ByteBufCodecs.VAR_INT, Advance::choice,
                Advance::new);

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    private DialoguePayloads() {
    }
}
