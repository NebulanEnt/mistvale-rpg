package com.example.mistvalerpg;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;

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
}
