package com.example.mistvalerpg.cutscene;

import com.example.mistvalerpg.MistvaleRpg;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * The short messages the server sends to run a cutscene on one player's screen.
 * The server decides <em>when</em> something happens; the player's game does the drawing.
 */
public final class CutscenePayloads {

    /**
     * Server to player: fade the screen to a colour and back. All times are in game ticks (20 per second).
     * It takes {@code in} ticks to go dark, stays dark for {@code hold}, then takes {@code out} ticks to come back.
     * {@code color} is 0xRRGGBB, so black is 0x000000 and white is 0xFFFFFF.
     */
    public record Fade(int in, int hold, int out, int color) implements CustomPacketPayload {
        public static final Type<Fade> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(MistvaleRpg.MODID, "cutscene_fade"));
        public static final StreamCodec<RegistryFriendlyByteBuf, Fade> CODEC = StreamCodec.composite(
                ByteBufCodecs.VAR_INT, Fade::in,
                ByteBufCodecs.VAR_INT, Fade::hold,
                ByteBufCodecs.VAR_INT, Fade::out,
                ByteBufCodecs.VAR_INT, Fade::color,
                Fade::new);

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    private CutscenePayloads() {
    }
}