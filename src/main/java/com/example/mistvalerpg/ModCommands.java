package com.example.mistvalerpg;

import java.util.Arrays;

import com.example.mistvalerpg.rift.RiftBlock;
import com.example.mistvalerpg.rift.RiftVariant;
import com.example.mistvalerpg.spirit.SpiritEntity;
import com.mojang.brigadier.arguments.StringArgumentType;
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
                .then(Commands.literal("spirit")
                        .executes(ModCommands::summonSpirit)));
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

    private ModCommands() {
    }
}