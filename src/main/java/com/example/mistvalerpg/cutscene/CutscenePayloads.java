package com.example.mistvalerpg.cutscene;

import com.example.mistvalerpg.MistvaleRpg;

import io.netty.buffer.ByteBuf;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;

/**
 * The short messages the server sends to run a cutscene on one player's screen.
 * The server decides <em>when</em> something happens; the player's game does the drawing.
 */
public final class CutscenePayloads {

    // A position in the world is three decimal numbers. The game has no ready-made way to send one, so we write one.
    private static final StreamCodec<ByteBuf, Vec3> VEC3 = StreamCodec.of(
            (buffer, vec) -> {
                buffer.writeDouble(vec.x);
                buffer.writeDouble(vec.y);
                buffer.writeDouble(vec.z);
            },
            buffer -> new Vec3(buffer.readDouble(), buffer.readDouble(), buffer.readDouble()));

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

    /**
     * Server to player: fly the camera from {@code from} to {@code to} in {@code ticks} ticks, while it looks at a point
     * that moves from {@code aimFrom} to {@code aimTo}. While a flight is on, the player's own view is replaced and
     * the player cannot move, click or turn.
     *
     * The path is a cubic Bezier curve: it leaves {@code from} heading towards {@code control1}, arrives at {@code to}
     * coming from {@code control2}, and bends smoothly in between. (A curve whose two handles lie on the straight line
     * from start to end is simply a straight flight.)
     *
     * {@code smooth}: start and stop gently instead of at a steady speed.
     * {@code queue}: wait for the flight in progress to finish instead of cutting it off. A scene is a row of queued flights.
     */
    public record Shot(Vec3 from, Vec3 control1, Vec3 control2, Vec3 to, Vec3 aimFrom, Vec3 aimTo, int ticks, boolean smooth, boolean queue) implements CustomPacketPayload {
        public static final Type<Shot> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(MistvaleRpg.MODID, "cutscene_shot"));
        // A codec made with StreamCodec.composite can only hold six values and this message has nine, so it is written out by hand.
        public static final StreamCodec<RegistryFriendlyByteBuf, Shot> CODEC = StreamCodec.of(
                (buffer, shot) -> {
                    VEC3.encode(buffer, shot.from());
                    VEC3.encode(buffer, shot.control1());
                    VEC3.encode(buffer, shot.control2());
                    VEC3.encode(buffer, shot.to());
                    VEC3.encode(buffer, shot.aimFrom());
                    VEC3.encode(buffer, shot.aimTo());
                    buffer.writeVarInt(shot.ticks());
                    buffer.writeBoolean(shot.smooth());
                    buffer.writeBoolean(shot.queue());
                },
                buffer -> new Shot(VEC3.decode(buffer), VEC3.decode(buffer), VEC3.decode(buffer), VEC3.decode(buffer),
                        VEC3.decode(buffer), VEC3.decode(buffer), buffer.readVarInt(), buffer.readBoolean(), buffer.readBoolean()));

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    /** Server to player: the scene is over, give the player their own view back (and let them move again). */
    public record EndShots() implements CustomPacketPayload {
        public static final Type<EndShots> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(MistvaleRpg.MODID, "cutscene_end_shots"));
        public static final StreamCodec<RegistryFriendlyByteBuf, EndShots> CODEC = StreamCodec.unit(new EndShots());

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    /**
     * Server to player: turn the player's view towards the entity with this id and keep it on that entity, with a gentle
     * zoom, until told otherwise. {@link #RELEASE} lets go. It is how the camera "finds" the Spirit when she speaks.
     */
    public record Focus(int entityId) implements CustomPacketPayload {
        public static final int RELEASE = -1;
        public static final Type<Focus> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(MistvaleRpg.MODID, "cutscene_focus"));
        public static final StreamCodec<RegistryFriendlyByteBuf, Focus> CODEC = StreamCodec.composite(
                ByteBufCodecs.VAR_INT, Focus::entityId,
                Focus::new);

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    /**
     * Server to player: the title card of a scene, "Somewhere in... / THE NORTH MARR LANDS". The small line types in letter
     * by letter, then the big line fades in, they stay together, and then both fade out. All times are in ticks.
     * {@code delay} is how long nothing shows, {@code step} is how long the typing takes and also how long the big line takes to
     * fade in and how long both take to fade away, {@code pause} is the wait between the end of the typing and the start of
     * the big line's fade-in, and {@code hold} is how long both stay after the big line has faded in.
     */
    public record Title(Component small, Component big, int delay, int step, int pause, int hold) implements CustomPacketPayload {
        public static final Type<Title> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(MistvaleRpg.MODID, "cutscene_title"));
        public static final StreamCodec<RegistryFriendlyByteBuf, Title> CODEC = StreamCodec.composite(
                ComponentSerialization.STREAM_CODEC, Title::small,
                ComponentSerialization.STREAM_CODEC, Title::big,
                ByteBufCodecs.VAR_INT, Title::delay,
                ByteBufCodecs.VAR_INT, Title::step,
                ByteBufCodecs.VAR_INT, Title::pause,
                ByteBufCodecs.VAR_INT, Title::hold,
                Title::new);

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    private CutscenePayloads() {
    }
}
