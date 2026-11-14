package com.example.mistvalerpg;

import java.util.Arrays;

import com.example.mistvalerpg.cutscene.Cutscene;
import com.example.mistvalerpg.dialogue.DialogueManager;
import com.example.mistvalerpg.dialogue.DialogueScene;
import com.example.mistvalerpg.dialogue.DialogueScenes;
import com.example.mistvalerpg.rift.RiftBlock;
import com.example.mistvalerpg.rift.RiftVariant;
import com.example.mistvalerpg.spirit.SpiritEntity;
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
import net.neoforged.neoforge.event.RegisterCommandsEvent;

/**
 * Commands for testing and for the people running a server (operators only).
 *
 *   /mistvale rift <blue|orange|corrupted|white|end|deep>   opens a rift 4 blocks in front of you
 *   /mistvale spirit                                        makes the Spirit appear in front of you
 *   /mistvale dialogue <scene>                              plays a conversation for you
 *   /mistvale dialogue reset                                makes the Spirit forget you (clears seen scenes and your chosen land)
 *   /mistvale cutscene fade <in> <hold> <out>               fades your screen to black and back (times in ticks, 20 = 1 second)
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
                                                .executes(ModCommands::cutsceneFade)))));
    }

    private static int cutsceneFade(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        ServerPlayer player = context.getSource().getPlayerOrException();
        Cutscene.fade(player, IntegerArgumentType.getInteger(context, "in"), IntegerArgumentType.getInteger(context, "hold"),
                IntegerArgumentType.getInteger(context, "out"), 0x000000);
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