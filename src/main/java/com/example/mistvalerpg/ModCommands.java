package com.example.mistvalerpg;

import java.util.Arrays;

import com.example.mistvalerpg.cutscene.Cutscene;
import com.example.mistvalerpg.cutscene.Later;
import com.example.mistvalerpg.dialogue.DialogueManager;
import com.example.mistvalerpg.dialogue.DialogueScene;
import com.example.mistvalerpg.dialogue.DialogueScenes;
import com.example.mistvalerpg.rift.RiftBlock;
import com.example.mistvalerpg.rift.RiftVariant;
import com.example.mistvalerpg.spirit.SpiritEntity;
import com.example.mistvalerpg.story.Region;
import com.example.mistvalerpg.story.SpawnScene;
import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.arguments.FloatArgumentType;
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
import net.minecraft.server.MinecraftServer;
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
 *   /mistvale cutscene spawn [north|west|south|east]        plays the whole arrival film: you are sent 50 blocks out from spawn to that land (north if you say nothing)
 *   /mistvale cutscene look <degrees> <ticks>               turns your view to the right (or to the left, with a minus) over some ticks
 *   /mistvale cutscene title <text>                         two seconds of black, then "Somewhere in..." and the text in the bottom left corner
 *   /mistvale cutscene shoulder <on|off>                    forces the over-the-shoulder camera, or gives your camera back
 *   /mistvale cutscene flight                               the camera circles around you once (about 8 seconds)
 *   /mistvale cutscene demo                                 plays a little film that uses every tool so far
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
                .then(Commands.literal("title")
                        .then(Commands.argument("text", StringArgumentType.greedyString())
                                .executes(ModCommands::cutsceneTitle)))
                .then(Commands.literal("spawn")
                        .executes(context -> {
                            SpawnScene.play(context.getSource().getPlayerOrException(), Region.NORTH);   // without a land: the north
                            return 1;
                        })
                        .then(Commands.argument("region", StringArgumentType.word())
                                .suggests((context, builder) -> SharedSuggestionProvider.suggest(Arrays.stream(Region.values()).map(Region::id), builder))
                                .executes(ModCommands::cutsceneSpawn)))
                .then(Commands.literal("look")
                        .then(Commands.argument("degrees", FloatArgumentType.floatArg(-360.0F, 360.0F))
                                .then(Commands.argument("ticks", IntegerArgumentType.integer(1, 600))
                                        .executes(context -> {
                                            Cutscene.look(context.getSource().getPlayerOrException(), FloatArgumentType.getFloat(context, "degrees"),
                                                    IntegerArgumentType.getInteger(context, "ticks"));
                                            return 1;
                                        }))))
                .then(Commands.literal("flight")
                        .executes(ModCommands::cutsceneFlight))
                .then(Commands.literal("demo")
                        .executes(ModCommands::cutsceneDemo))
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

    private static int cutsceneFlight(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        ServerPlayer player = context.getSource().getPlayerOrException();
        Vec3 chest = player.position().add(0.0, 1.2, 0.0);
        Cutscene.orbit(player, chest, 5.0, 1.0, player.getYRot() + 90.0, 160);
        return 1;
    }

    // A little film made of every tool so far, with the moments of it laid out on a timeline (20 ticks = 1 second).
    private static int cutsceneDemo(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        ServerPlayer player = context.getSource().getPlayerOrException();
        MinecraftServer server = context.getSource().getServer();
        Vec3 chest = player.position().add(0.0, 1.2, 0.0);

        Cutscene.bars(player, true);
        Cutscene.fade(player, 0, 10, 30, 0x000000);                    // start black and fade up
        Cutscene.orbit(player, chest, 5.0, 1.0, player.getYRot() + 90.0, 160);   // the camera circles for 8 seconds
        Later.after(server, 30, () -> Cutscene.subtitle(player, Component.literal("A story begins."), 15, 80, 15));
        Later.after(server, 165, () -> Cutscene.fade(player, 20, 25, 25, 0x000000));   // go black again...
        Later.after(server, 185, () -> {                                // ...and while it is black, give everything back
            Cutscene.endShots(player);
            Cutscene.bars(player, false);
        });
        return 1;
    }

    // The spawn film for a land: "north", "west", "south" or "east".
    private static int cutsceneSpawn(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        Region region = Region.byId(StringArgumentType.getString(context, "region"));
        if (region == null) {
            context.getSource().sendFailure(Component.literal("The lands are north, west, south and east."));
            return 0;
        }
        SpawnScene.play(context.getSource().getPlayerOrException(), region);
        return 1;
    }

    // The title card on its own: two seconds of black, then the card, then the screen comes back.
    private static int cutsceneTitle(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        ServerPlayer player = context.getSource().getPlayerOrException();
        Cutscene.fade(player, 10, 220, 20, 0x000000);
        Later.after(context.getSource().getServer(), 50, () -> Cutscene.title(player, Component.translatable("scene.mistvale_rpg.somewhere_in"),
                Component.literal(StringArgumentType.getString(context, "text")), 20, 20, 40, 60));
        return 1;
    }

    private static int cutsceneSubtitle(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        ServerPlayer player = context.getSource().getPlayerOrException();
        Component text = Component.literal(StringArgumentType.getString(context, "text"));
        Cutscene.subtitle(player, text, IntegerArgumentType.getInteger(context, "in"), IntegerArgumentType.getInteger(context, "hold"),
                IntegerArgumentType.getInteger(context, "out"));
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
