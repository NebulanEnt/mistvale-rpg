package com.example.mistvalerpg.dialogue;

import java.util.function.Consumer;

import com.example.mistvalerpg.ModAttachments;
import com.example.mistvalerpg.rift.RiftBlock;
import com.example.mistvalerpg.rift.RiftVariant;
import com.example.mistvalerpg.story.Region;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerPlayer;

/**
 * Something a conversation makes happen in the world: remembering a choice, opening a rift.
 * Actions always run on the server, never on the player's own computer, so nobody can fake them.
 */
public enum DialogueAction {
    CHOOSE_NORTH(player -> choose(player, Region.NORTH)),
    CHOOSE_WEST(player -> choose(player, Region.WEST)),
    CHOOSE_SOUTH(player -> choose(player, Region.SOUTH)),
    CHOOSE_EAST(player -> choose(player, Region.EAST)),
    /** The Spirit tears open a blue rift a few steps in front of the player. */
    OPEN_BLUE_RIFT(DialogueAction::openBlueRift);

    private final Consumer<ServerPlayer> effect;

    DialogueAction(Consumer<ServerPlayer> effect) {
        this.effect = effect;
    }

    public void run(ServerPlayer player) {
        effect.accept(player);
    }

    private static void choose(ServerPlayer player, Region region) {
        player.setData(ModAttachments.REGION, region.id());
    }

    private static void openBlueRift(ServerPlayer player) {
        Direction looking = player.getDirection();
        BlockPos pos = player.blockPosition().relative(looking, 4);
        // It faces the player so they can walk straight into it, and closes by itself after two minutes.
        RiftBlock.spawn(player.serverLevel(), pos, looking.getOpposite(), RiftVariant.BLUE, null, 2400, true);
    }
}
