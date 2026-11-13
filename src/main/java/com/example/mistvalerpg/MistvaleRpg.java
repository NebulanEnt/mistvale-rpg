package com.example.mistvalerpg;

import org.slf4j.Logger;

import com.example.mistvalerpg.soul.CagedSoulBlock;
import com.example.mistvalerpg.soul.ChargedSoulBlock;
import com.example.mistvalerpg.soul.SoulBlockItem;
import com.example.mistvalerpg.soul.SoulId;
import com.example.mistvalerpg.soul.SoulShardItem;
import com.mojang.logging.LogUtils;

import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.material.PushReaction;
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

    // Caged Soul Block: strength -1 means it can never be broken (like bedrock). It glows brighter with every shard.
    public static final DeferredBlock<CagedSoulBlock> CAGED_SOUL_BLOCK = BLOCKS.registerBlock(
            "caged_soul_block",
            CagedSoulBlock::new,
            BlockBehaviour.Properties.of()
                    .mapColor(MapColor.COLOR_CYAN)
                    .strength(-1.0F, 3600000.0F)
                    .noLootTable()
                    .pushReaction(PushReaction.BLOCK)
                    .sound(SoundType.CHAIN)
                    .lightLevel(state -> 4 + state.getValue(CagedSoulBlock.CHARGE) * 3));

    // Charged Soul Block: the freed soul. Breaks quickly with anything and drops itself.
    public static final DeferredBlock<ChargedSoulBlock> CHARGED_SOUL_BLOCK = BLOCKS.registerBlock(
            "charged_soul_block",
            ChargedSoulBlock::new,
            BlockBehaviour.Properties.of()
                    .mapColor(MapColor.DIAMOND)
                    .strength(0.3F, 6.0F)
                    .sound(SoundType.GLASS)
                    .lightLevel(state -> 13));

    public static final DeferredItem<SoulBlockItem> CAGED_SOUL_BLOCK_ITEM = ITEMS.register("caged_soul_block",
            () -> new SoulBlockItem(CAGED_SOUL_BLOCK.get(), new Item.Properties().rarity(ModRarities.EPIC.getValue())));
    public static final DeferredItem<SoulBlockItem> CHARGED_SOUL_BLOCK_ITEM = ITEMS.register("charged_soul_block",
            () -> new SoulBlockItem(CHARGED_SOUL_BLOCK.get(), new Item.Properties().rarity(ModRarities.LEGENDARY.getValue())));

    // ---- Creative tab ----
    // One tab for the whole mod. Items appear in the order they are listed here.
    public static final DeferredHolder<CreativeModeTab, CreativeModeTab> MAIN_TAB = CREATIVE_MODE_TABS.register("main", () -> CreativeModeTab.builder()
            .title(Component.translatable("itemGroup.mistvale_rpg"))
            .icon(() -> SOUL_SHARD.get().getDefaultInstance())
            .displayItems((parameters, output) -> {
                output.accept(SOUL_SHARD.get());
                // one of each soul, caged first, then freed
                for (SoulId soul : SoulId.values()) {
                    output.accept(SoulBlockItem.withSoul(CAGED_SOUL_BLOCK_ITEM.get(), soul));
                }
                for (SoulId soul : SoulId.values()) {
                    output.accept(SoulBlockItem.withSoul(CHARGED_SOUL_BLOCK_ITEM.get(), soul));
                }
            }).build());

    // The constructor is the first code that runs when the mod is loaded.
    public MistvaleRpg(IEventBus modEventBus, ModContainer modContainer) {
        BLOCKS.register(modEventBus);
        ITEMS.register(modEventBus);
        CREATIVE_MODE_TABS.register(modEventBus);
    }
}
