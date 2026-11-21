package com.example.mistvalerpg.dialogue;

import java.util.function.Consumer;

import com.example.mistvalerpg.ModAttachments;
import com.example.mistvalerpg.prologue.Prologue;
import com.example.mistvalerpg.story.Region;
import com.example.mistvalerpg.story.SpawnScene;

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
    /** The Spirit sends the player to the land they chose. It is a little film: it starts when the conversation is closed. */
    START_SPAWN_SCENE(SpawnScene::playAfterTalk),
    /** The fog comes and the skeleton pirates attack. Like the spawn film, it waits until the conversation is closed. */
    START_FIGHT(Prologue::startFightAfterTalk);

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
}
