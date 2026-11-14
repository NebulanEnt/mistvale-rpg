package com.example.mistvalerpg.client;

import java.util.Set;

import com.example.mistvalerpg.MistvaleRpg;

import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderGuiLayerEvent;

/**
 * While a dialogue box is open, the quest windows on the right edge of the screen would sit on top
 * of the answers, so they are not drawn. Everything else (hotbar, hearts, minimap, chat) stays.
 * They come back by themselves when the dialogue closes, because nothing is switched off for good:
 * we only skip drawing them while the box is open.
 */
@EventBusSubscriber(modid = MistvaleRpg.MODID, value = Dist.CLIENT)
public final class DialogueHud {

    /** The screen layers that hold quest windows: the old Mistvale mod's tracker, and this mod's own one later. */
    private static final Set<ResourceLocation> QUEST_LAYERS = Set.of(
            ResourceLocation.fromNamespaceAndPath("mistvale", "quest_tracker"),
            ResourceLocation.fromNamespaceAndPath(MistvaleRpg.MODID, "quest_tracker"));

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    static void onLayer(RenderGuiLayerEvent.Pre event) {
        if (DialogueClient.isOpen() && QUEST_LAYERS.contains(event.getName())) {
            event.setCanceled(true);
        }
    }

    private DialogueHud() {
    }
}
