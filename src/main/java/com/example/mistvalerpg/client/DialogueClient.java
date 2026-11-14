package com.example.mistvalerpg.client;

import com.example.mistvalerpg.dialogue.DialogueNode;
import com.example.mistvalerpg.dialogue.DialoguePayloads;
import com.example.mistvalerpg.dialogue.DialogueScene;
import com.example.mistvalerpg.dialogue.DialogueScenes;

import net.minecraft.client.Minecraft;

/**
 * What the player's game does when the server sends a dialogue message:
 * open the dialogue box (or move an open one on to the next box), or close it.
 */
public final class DialogueClient {

    public static void onShow(DialoguePayloads.ShowNode packet) {
        DialogueScene scene = DialogueScenes.get(packet.scene());
        DialogueNode node = scene == null ? null : scene.node(packet.node());
        if (node == null) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.screen instanceof DialogueScreen open) {
            open.show(scene, node);
        } else {
            minecraft.setScreen(new DialogueScreen(scene, node));
        }
    }

    /** True while a dialogue box is on screen. Anything that draws on top of the game (the Quest Book's boxes later) should stay hidden then. */
    public static boolean isOpen() {
        return Minecraft.getInstance().screen instanceof DialogueScreen;
    }

    public static void onEnd() {
        if (Minecraft.getInstance().screen instanceof DialogueScreen open) {
            open.closeFromServer();
        }
    }

    private DialogueClient() {
    }
}
