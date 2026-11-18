package com.example.mistvalerpg;

import java.util.Arrays;

import com.example.mistvalerpg.cutscene.Cutscene;
import com.example.mistvalerpg.dialogue.DialogueManager;
import com.example.mistvalerpg.dialogue.DialogueScene;
import com.example.mistvalerpg.dialogue.DialogueScenes;
import com.example.mistvalerpg.rift.RiftBlock;
import com.example.mistvalerpg.rift.RiftVariant;
import com.example.mistvalerpg.spirit.SpiritEntity;
import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

/**
 * Commands for testing and for the people running a server (operators only).
 *
 *   /mistvale rift <blue|orange|corrupted|white|end|deep>   opens a rift 4 blocks in front of you
 *   /mistvale spirit                                        makes the Spirit appear in front of you
 *   /mistvale dialogue <scene>                              plays a conversation for you
 *   /mistvale dialogue reset                                makes the Spirit forget you (clears seen scenes and your chosen land)
 *   /mistvale cutscene fade <in> <hold> <out>               fades your screen to black and back (times in ticks, 20 = 1 second)
 *   /mistvale cutscene bars <on|off>                        slides the film bars in or out
 *   /mistvale cutscene subtitle <in> <hold> <out> <text>    shows a line of text in the middle of the screen
 *   /mistvale cutscene shoulder <on|off>
 *   /mistvale cutscene flight                               the camera circles around you once (about 8 seconds)
 *   /mistvale cutscene stop                                 ends a flight and gives you your camera back
 */
public final class ModCommands {

    public static void onRegisterCommands(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("mistvale")
                .requires(source -> source.hasPermission(2))
                .then(Commands.literal("rift")
                        .then(Commands.argument("variant", StringArgumentType.word())
                                .suggests((context, builder) -> SharedSuggestionProvider.suggest(
                                        Arrays.stream(RiftVariant.values()).map(RiftVariant::getSerializedName), builder))
                                .executes(context -> openRift(context, StringArgumentType.getString(context, "variant")))))
                .then(cutsceneCommands())
                .then(Commands.literal("spirit")
                        .executes(ModCommands::summonSpirit))
                .then(Commands.literal("dialogue")
                        .then(Commands.literal("reset")
                                .executes(context -> {
                                    DialogueManager.reset(context.getSource().getPlayerOrException());
                                    context.getSource().sendSuccess(() -> Component.literal("The Spirit has forgotten you."), true);
                                    return 1;
                                }))
                        .then(Commands.argument("scene", StringArgumentType.word())
                                .suggests((context, builder) -> SharedSuggestionProvider.suggest(DialogueScenes.ids(), builder))
                                .executes(context -> playDialogue(context, StringArgumentType.getString(context, "scene"))))));
    }

    // The test commands for the cutscene toolbox. Each tool of the toolbox gets its own line here as it is built.
    private static LiteralArgumentBuilder<CommandSourceStack> cutsceneCommands() {
        return Commands.literal("cutscene")
                .then(Commands.literal("fade")
                        .then(Commands.argument("in", IntegerArgumentType.integer(0, 600))
                                .then(Commands.argument("hold", IntegerArgumentType.integer(0, 1200))
                                        .then(Commands.argument("out", IntegerArgumentType.integer(0, 600))
                                                .executes(ModCommands::cutsceneFade)))))
                .then(Commands.literal("bars")
                        .then(Commands.argument("on", BoolArgumentType.bool())
                                .executes(ModCommands::cutsceneBars)))
                .then(Commands.literal("subtitle")
                        .then(Commands.argument("in", IntegerArgumentType.integer(0, 600))
                                .then(Commands.argument("hold", IntegerArgumentType.integer(0, 1200))
                                        .then(Commands.argument("out", IntegerArgumentType.integer(0, 600))
                                                .then(Commands.argument("text", StringArgumentType.greedyString())
                                                        .executes(ModCommands::cutsceneSubtitle))))))
                .then(Commands.literal("shoulder")
                        .then(Commands.argument("on", BoolArgumentType.bool())
                                .executes(ModCommands::cutsceneShoulder)))
                .then(Commands.literal("flight")
                        .executes(ModCommands::cutsceneFlight))
                .then(Commands.literal("stop")
                        .executes(context -> {
                            Cutscene.endShots(context.getSource().getPlayerOrException());
                            return 1;
                        }));
    }

    private static int cutsceneFade(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        ServerPlayer player = context.getSource().getPlayerOrException();
        Cutscene.fade(player, IntegerArgumentType.getInteger(context, "in"), IntegerArgumentType.getInteger(context, "hold"),
                IntegerArgumentType.getInteger(context, "out"), 0x000000);
        return 1;
    }

    private static int cutsceneBars(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        Cutscene.bars(context.getSource().getPlayerOrException(), BoolArgumentType.getBool(context, "on"));
        return 1;
    }

    private static int cutsceneShoulder(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        Cutscene.shoulderView(context.getSource().getPlayerOrException(), BoolArgumentType.getBool(context, "on"));
        return 1;
    }

    private static int cutsceneSubtitle(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        ServerPlayer player = context.getSource().getPlayerOrException();
        Component text = Component.literal(StringArgumentType.getString(context, "text"));
        Cutscene.subtitle(player, text, IntegerArgumentType.getInteger(context, "in"), IntegerArgumentType.getInteger(context, "hold"),
                IntegerArgumentType.getInteger(context, "out"));
        return 1;
    }

    // Eight queued flights that together circle the player once, one for every eighth of a turn. Each flight is a Bezier curve
    // whose two handles point along the circle, so the pieces join without a corner.
    private static int cutsceneFlight(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        ServerPlayer player = context.getSource().getPlayerOrException();
        Vec3 centre = player.position().add(0.0, 1.2, 0.0);   // look at the player's chest
        double start = Math.toRadians(player.getYRot() + 90.0);
        int steps = 8;
        double turn = Math.PI * 2.0 / steps;
        double handle = 4.0 / 3.0 * Math.tan(turn / 4.0) * 5.0;   // how long the handles of a circular arc of radius 5 have to be
        for (int i = 0; i < steps; i++) {
            double a1 = start + turn * i;
            double a2 = a1 + turn;
            Vec3 from = centre.add(Math.cos(a1) * 5.0, 1.0, Math.sin(a1) * 5.0);
            Vec3 to = centre.add(Math.cos(a2) * 5.0, 1.0, Math.sin(a2) * 5.0);
            Vec3 control1 = from.add(-Math.sin(a1) * handle, 0.0, Math.cos(a1) * handle);   // along the circle, forwards
            Vec3 control2 = to.add(Math.sin(a2) * handle, 0.0, -Math.cos(a2) * handle);     // along the circle, backwards
            Cutscene.curve(player, from, control1, control2, to, centre, centre, 20, false, true);
        }
        return 1;
    }

    private static int openRift(CommandContext<CommandSourceStack> context, String name) throws CommandSyntaxException {
        ServerPlayer player = context.getSource().getPlayerOrException();
        RiftVariant variant = RiftVariant.byName(name);
        Direction looking = player.getDirection();
        BlockPos pos = player.blockPosition().relative(looking, 4);
        // It faces you, so you can walk straight into it. It closes by itself after two minutes.
        RiftBlock.spawn(player.serverLevel(), pos, looking.getOpposite(), variant, null, 2400, true);
        context.getSource().sendSuccess(() -> Component.literal("Opened a " + variant.getSerializedName() + " rift."), true);
        return 1;
    }

    private static int summonSpirit(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        ServerPlayer player = context.getSource().getPlayerOrException();
        SpiritEntity.appearNear(player.serverLevel(), player.blockPosition().relative(player.getDirection(), 3).above());
        return 1;
    }

    private static int playDialogue(CommandContext<CommandSourceStack> context, String id) throws CommandSyntaxException {
        DialogueScene scene = DialogueScenes.get(id);
        if (scene == null) {
            context.getSource().sendFailure(Component.literal("There is no scene called " + id + "."));
            return 0;
        }
        // If the Spirit is standing within 8 blocks she talks along with her own 3D model.
        ServerPlayer player = context.getSource().getPlayerOrException();
        SpiritEntity spirit = player.level().getEntitiesOfClass(SpiritEntity.class, player.getBoundingBox().inflate(8.0)).stream().findFirst().orElse(null);
        DialogueManager.start(player, scene, spirit);
        return 1;
    }

    private ModCommands() {
    }
}
