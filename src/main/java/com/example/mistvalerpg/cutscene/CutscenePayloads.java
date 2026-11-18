package com.example.mistvalerpg.cutscene;

import com.example.mistvalerpg.MistvaleRpg;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
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

    /**
     * Server to player: slide the black film bars in (or out) at the top and bottom of the screen.
     * While they are in, the normal heads-up display (hearts, hotbar, crosshair) is hidden.
     */
    public record Bars(boolean on) implements CustomPacketPayload {
        public static final Type<Bars> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(MistvaleRpg.MODID, "cutscene_bars"));
        public static final StreamCodec<RegistryFriendlyByteBuf, Bars> CODEC = StreamCodec.composite(
                ByteBufCodecs.BOOL, Bars::on,
                Bars::new);

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    /**
     * Server to player: show a line of text in the middle of the screen. It fades in over {@code in} ticks,
     * stays for {@code hold}, and fades out over {@code out}. The text is a {@link Component}, so it can be
     * plain words or the name of a line in the language file.
     */
    public record Subtitle(Component text, int in, int hold, int out) implements CustomPacketPayload {
        public static final Type<Subtitle> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(MistvaleRpg.MODID, "cutscene_subtitle"));
        public static final StreamCodec<RegistryFriendlyByteBuf, Subtitle> CODEC = StreamCodec.composite(
                ComponentSerialization.STREAM_CODEC, Subtitle::text,
                ByteBufCodecs.VAR_INT, Subtitle::in,
                ByteBufCodecs.VAR_INT, Subtitle::hold,
                ByteBufCodecs.VAR_INT, Subtitle::out,
                Subtitle::new);

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    /**
     * Server to player: put the camera behind the player's shoulder and keep it there ({@code on}),
     * or give the player their own camera back.
     */
    public record ShoulderView(boolean on) implements CustomPacketPayload {
        public static final Type<ShoulderView> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(MistvaleRpg.MODID, "cutscene_shoulder"));
        public static final StreamCodec<RegistryFriendlyByteBuf, ShoulderView> CODEC = StreamCodec.composite(
                ByteBufCodecs.BOOL, ShoulderView::on,
                ShoulderView::new);

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    private CutscenePayloads() {
    }
}
