package com.example.mistvalerpg.dialogue;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.jetbrains.annotations.Nullable;

import com.example.mistvalerpg.ModAttachments;
import com.example.mistvalerpg.spirit.SpiritEntity;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * Runs conversations on the server. It remembers which box each player is looking at, sends the next one
 * when the player presses a key, carries out the actions, and writes "seen" on the player once a scene is finished.
 */
public final class DialogueManager {
    /** Where one player is in one conversation. */
    private record Session(DialogueScene scene, String nodeId, int speakerEntityId) {
    }

    private static final Map<UUID, Session> SESSIONS = new ConcurrentHashMap<>();

    public static boolean isTalking(ServerPlayer player) {
        return SESSIONS.containsKey(player.getUUID());
    }

    public static boolean hasSeen(ServerPlayer player, DialogueScene scene) {
        return player.getData(ModAttachments.SEEN_SCENES).contains(scene.id());
    }

    /** Starts a scene. {@code speaker} is the Spirit (or whoever) standing there, so her 3D model can talk along; it may be null. */
    public static void start(ServerPlayer player, DialogueScene scene, @Nullable Entity speaker) {
        if (isTalking(player)) {
            return;
        }
        show(player, new Session(scene, scene.start(), speaker == null ? -1 : speaker.getId()));
    }

    // Shows one box: runs its actions, makes the Spirit's model talk in the right mood, and tells the player's game.
    private static void show(ServerPlayer player, Session session) {
        DialogueNode node = session.scene().node(session.nodeId());
        if (node == null) {
            finish(player, session, false);
            return;
        }
        SESSIONS.put(player.getUUID(), session);
        node.actions().forEach(action -> action.run(player));
        SpiritEntity spirit = spiritOf(player, session);
        if (spirit != null) {
            // Her model talks only while SHE is the one talking; when the player speaks she listens quietly.
            spirit.talk(node.speaker() == DialogueSpeaker.SPIRIT ? node.emotion().spiritMood() : SpiritEntity.MOOD_NONE, 200);
        }
        PacketDistributor.sendToPlayer(player, new DialoguePayloads.ShowNode(session.scene().id(), node.id()));
    }

    /** The player pressed a key on the box they are looking at (see DialoguePayloads.Advance). */
    public static void onAdvance(ServerPlayer player, int choice) {
        Session session = SESSIONS.get(player.getUUID());
        if (session == null) {
            return; // not in a conversation: somebody is sending messages they should not
        }
        if (choice == DialoguePayloads.Advance.ABORT) {
            finish(player, session, false);
            return;
        }
        DialogueNode node = session.scene().node(session.nodeId());
        if (node == null) {
            finish(player, session, false);
            return;
        }

        String next;
        if (node.isQuestion()) {
            if (choice < 0 || choice >= node.choices().size()) {
                return; // a question needs a real answer
            }
            DialogueNode.Choice picked = node.choices().get(choice);
            picked.actions().forEach(action -> action.run(player));
            next = picked.next();
        } else {
            next = node.next();
        }

        if (next == null) {
            finish(player, session, true);
        } else {
            show(player, new Session(session.scene(), next, session.speakerEntityId()));
        }
    }

    // Ends the conversation. A scene only counts as "seen" if the player reached its end.
    private static void finish(ServerPlayer player, Session session, boolean completed) {
        SESSIONS.remove(player.getUUID());
        PacketDistributor.sendToPlayer(player, new DialoguePayloads.End());
        SpiritEntity spirit = spiritOf(player, session);
        if (spirit != null) {
            spirit.talk(SpiritEntity.MOOD_NONE, 0);
        }
        if (completed && !hasSeen(player, session.scene())) {
            List<String> seen = new ArrayList<>(player.getData(ModAttachments.SEEN_SCENES));
            seen.add(session.scene().id());
            player.setData(ModAttachments.SEEN_SCENES, List.copyOf(seen));
        }
    }

    /** Called when a player leaves the game in the middle of a conversation. */
    public static void forget(ServerPlayer player) {
        SESSIONS.remove(player.getUUID());
    }

    /** Forgets everything the player has seen and chosen (used by /mistvale dialogue reset). */
    public static void reset(ServerPlayer player) {
        player.setData(ModAttachments.SEEN_SCENES, List.of());
        player.setData(ModAttachments.REGION, "");
    }

    @Nullable
    private static SpiritEntity spiritOf(ServerPlayer player, Session session) {
        Entity entity = session.speakerEntityId() < 0 ? null : player.serverLevel().getEntity(session.speakerEntityId());
        return entity instanceof SpiritEntity spirit ? spirit : null;
    }

    private DialogueManager() {
    }
}