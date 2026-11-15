package com.example.mistvalerpg;

import com.example.mistvalerpg.client.CinematicOverlay;
import com.example.mistvalerpg.client.DialogueClient;
import com.example.mistvalerpg.client.FadeOverlay;
import com.example.mistvalerpg.client.WarpOverlay;
import com.example.mistvalerpg.cutscene.CutscenePayloads;
import com.example.mistvalerpg.dialogue.DialogueManager;
import com.example.mistvalerpg.dialogue.DialoguePayloads;
import com.example.mistvalerpg.rift.RiftWarp;

import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

/**
 * Tells the game about every message the mod sends between the server and the player's computer,
 * and what to do when each one arrives. A message must be registered here on both sides, or it is refused.
 */
public final class ModNetwork {

    public static void register(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = event.registrar("1");

        // Rifts: three short messages that drive the screen effect while you are pulled through.
        registrar.playToClient(RiftWarp.Warp.TYPE, RiftWarp.Warp.CODEC, (payload, context) -> WarpOverlay.onPacket(payload));

        // Dialogue: the server shows boxes, the player answers.
        registrar.playToClient(DialoguePayloads.ShowNode.TYPE, DialoguePayloads.ShowNode.CODEC, (payload, context) -> DialogueClient.onShow(payload));
        registrar.playToClient(DialoguePayloads.End.TYPE, DialoguePayloads.End.CODEC, (payload, context) -> DialogueClient.onEnd());
        registrar.playToServer(DialoguePayloads.Advance.TYPE, DialoguePayloads.Advance.CODEC,
                (payload, context) -> DialogueManager.onAdvance((ServerPlayer) context.player(), payload.choice()));

        // Cutscenes: short messages that run film tricks on the player's screen.
        registrar.playToClient(CutscenePayloads.Fade.TYPE, CutscenePayloads.Fade.CODEC, (payload, context) -> FadeOverlay.onPacket(payload));
        registrar.playToClient(CutscenePayloads.Bars.TYPE, CutscenePayloads.Bars.CODEC, (payload, context) -> CinematicOverlay.onBars(payload));
        registrar.playToClient(CutscenePayloads.Subtitle.TYPE, CutscenePayloads.Subtitle.CODEC, (payload, context) -> CinematicOverlay.onSubtitle(payload));
    }

    private ModNetwork() {
    }
}
