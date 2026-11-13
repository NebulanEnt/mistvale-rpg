package com.example.mistvalerpg;

import com.example.mistvalerpg.soul.SoulId;

import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.component.BlockItemStateProperties;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.neoforge.client.event.RegisterColorHandlersEvent;

// This class is never loaded on a dedicated server, so code that draws things on screen is safe here.
@Mod(value = MistvaleRpg.MODID, dist = Dist.CLIENT)
@EventBusSubscriber(modid = MistvaleRpg.MODID, value = Dist.CLIENT)
public class MistvaleRpgClient {
    public MistvaleRpgClient(ModContainer container) {
    }

    @SubscribeEvent
    static void onClientSetup(FMLClientSetupEvent event) {
        MistvaleRpg.LOGGER.info("MistvaleRPG client is ready");
    }

    // The Charged Soul Block's model has one part marked "tint 0". This paints that part in the soul's colour.
    @SubscribeEvent
    static void onBlockColors(RegisterColorHandlersEvent.Block event) {
        event.register(
                (state, level, pos, tintIndex) -> tintIndex == 0 ? state.getValue(SoulId.PROPERTY).color() : -1,
                MistvaleRpg.CHARGED_SOUL_BLOCK.get());
    }

    // The same for the block in your hand or inventory: the soul is read from the item's block_state note.
    @SubscribeEvent
    static void onItemColors(RegisterColorHandlersEvent.Item event) {
        event.register((stack, tintIndex) -> {
            SoulId soul = stack.getOrDefault(DataComponents.BLOCK_STATE, BlockItemStateProperties.EMPTY).get(SoulId.PROPERTY);
            if (tintIndex != 0 || soul == null) {
                return -1;
            }
            return 0xFF000000 | soul.color();
        }, MistvaleRpg.CHARGED_SOUL_BLOCK_ITEM.get());
    }
}
