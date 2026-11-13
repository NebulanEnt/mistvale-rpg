package com.example.mistvalerpg;

import org.slf4j.Logger;

import com.example.mistvalerpg.soul.SoulShardItem;
import com.mojang.logging.LogUtils;

import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Rarity;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

// The value here should match an entry in the META-INF/neoforge.mods.toml file
@Mod(MistvaleRpg.MODID)
public class MistvaleRpg {
    public static final String MODID = "mistvale_rpg";
    public static final Logger LOGGER = LogUtils.getLogger();

    // The three sign-up sheets: everything the mod adds is registered through one of these.
    public static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(MODID);
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(MODID);
    public static final DeferredRegister<CreativeModeTab> CREATIVE_MODE_TABS = DeferredRegister.create(Registries.CREATIVE_MODE_TAB, MODID);

    // ---- Souls ----
    // Soul Shard: dropped by lesser bosses, fed to a Caged Soul Block.
    public static final DeferredItem<SoulShardItem> SOUL_SHARD =
            ITEMS.registerItem("soul_shard", SoulShardItem::new, new Item.Properties().rarity(ModRarities.RARE.getValue()));

    // ---- Creative tab ----
    // One tab for the whole mod. Items appear in the order they are listed here.
    public static final DeferredHolder<CreativeModeTab, CreativeModeTab> MAIN_TAB = CREATIVE_MODE_TABS.register("main", () -> CreativeModeTab.builder()
            .title(Component.translatable("itemGroup.mistvale_rpg"))
            .icon(() -> SOUL_SHARD.get().getDefaultInstance())
            .displayItems((parameters, output) -> {
                output.accept(SOUL_SHARD.get());
            }).build());

    // The constructor is the first code that runs when the mod is loaded.
    public MistvaleRpg(IEventBus modEventBus, ModContainer modContainer) {
        BLOCKS.register(modEventBus);
        ITEMS.register(modEventBus);
        CREATIVE_MODE_TABS.register(modEventBus);
    }
}
