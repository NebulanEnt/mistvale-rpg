package com.example.mistvalerpg;

import com.example.mistvalerpg.client.CagedSoulRenderer;
import com.example.mistvalerpg.client.RiftRenderer;
import com.example.mistvalerpg.client.RiftSoulParticle;
import com.example.mistvalerpg.client.SoulCrystalRenderer;
import com.example.mistvalerpg.client.SpiritRenderer;
import com.example.mistvalerpg.client.WarpOverlay;

import com.example.mistvalerpg.soul.SoulBlockItem;

import net.minecraft.client.renderer.item.ItemProperties;
import net.minecraft.client.renderer.item.ItemPropertyFunction;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;
import net.neoforged.neoforge.client.event.RegisterParticleProvidersEvent;

// This class is never loaded on a dedicated server, so code that draws things on screen is safe here.
@Mod(value = MistvaleRpg.MODID, dist = Dist.CLIENT)
@EventBusSubscriber(modid = MistvaleRpg.MODID, value = Dist.CLIENT)
public class MistvaleRpgClient {
    public MistvaleRpgClient(ModContainer container) {
    }

    @SubscribeEvent
    static void onClientSetup(FMLClientSetupEvent event) {
        // The soul blocks and the crystals look different for each soul. An item has no block state,
        // so here we teach the game a new "soul" number that item models can ask about (see models/item/charged_soul_block.json).
        event.enqueueWork(() -> {
            ResourceLocation soul = ResourceLocation.fromNamespaceAndPath(MistvaleRpg.MODID, "soul");
            ItemPropertyFunction number = (stack, level, entity, seed) -> SoulBlockItem.soulOf(stack).ordinal() * 0.1F;
            ItemProperties.register(MistvaleRpg.CHARGED_SOUL_BLOCK_ITEM.get(), soul, number);
            ItemProperties.register(MistvaleRpg.SOUL_CRYSTAL_ITEM.get(), soul, number);
        });
        MistvaleRpg.LOGGER.info("MistvaleRPG client is ready");
    }

    // Tell Minecraft which renderer draws which block entity or entity.
    @SubscribeEvent
    static void onRegisterRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerBlockEntityRenderer(ModBlockEntities.CAGED_SOUL.get(), context -> new CagedSoulRenderer());
        event.registerBlockEntityRenderer(ModBlockEntities.RIFT.get(), context -> new RiftRenderer());
        event.registerEntityRenderer(ModEntities.SPIRIT.get(), SpiritRenderer::new);
        event.registerEntityRenderer(ModEntities.SOUL_CRYSTAL.get(), SoulCrystalRenderer::new);
    }

    // Our own particle needs a "factory" that turns a request for one into a particle you can see.
    @SubscribeEvent
    static void onRegisterParticles(RegisterParticleProvidersEvent event) {
        event.registerSpriteSet(ModParticles.RIFT_SOUL.get(), RiftSoulParticle.Provider::new);
    }

    // The swirling screen effect while a rift pulls you through is drawn as a layer above everything else.
    @SubscribeEvent
    static void onRegisterGuiLayers(RegisterGuiLayersEvent event) {
        event.registerAboveAll(ResourceLocation.fromNamespaceAndPath(MistvaleRpg.MODID, "warp"), WarpOverlay::render);
    }
}
