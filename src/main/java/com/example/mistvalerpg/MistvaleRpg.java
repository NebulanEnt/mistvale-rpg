package com.example.mistvalerpg;

import org.slf4j.Logger;

import com.example.mistvalerpg.cutscene.Later;
import com.example.mistvalerpg.prologue.PrologueEvents;
import com.example.mistvalerpg.prologue.PrologueRegistry;
import com.example.mistvalerpg.rift.RiftBlock;
import com.example.mistvalerpg.dialogue.DialogueManager;
import com.example.mistvalerpg.soul.CagedSoulBlock;
import com.example.mistvalerpg.soul.ChargedSoulBlock;
import com.example.mistvalerpg.soul.SoulBlockItem;
import com.example.mistvalerpg.soul.SoulCrystalItem;
import com.example.mistvalerpg.soul.SoulId;
import com.example.mistvalerpg.soul.SoulPlatform;
import com.example.mistvalerpg.soul.SoulShardItem;
import com.mojang.logging.LogUtils;

import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.material.PushReaction;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.DeferredSpawnEggItem;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
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

    // ---- Rifts ----
    // The rift block has no item: it is only ever opened by a ritual or by the /mistvale command.
    // Like bedrock it cannot be broken, it has no hitbox, and it gets out of the way if something is built on it.
    public static final DeferredBlock<RiftBlock> RIFT_BLOCK = BLOCKS.registerBlock(
            "rift",
            RiftBlock::new,
            BlockBehaviour.Properties.of()
                    .mapColor(MapColor.NONE)
                    .strength(-1.0F, 3600000.0F)
                    .noCollission()
                    .noOcclusion()
                    .noLootTable()
                    .pushReaction(PushReaction.BLOCK)
                    .replaceable());

    // ---- The Spirit ----
    // A spawn egg is the easy way to place her in creative mode.
    public static final DeferredItem<DeferredSpawnEggItem> SPIRIT_SPAWN_EGG = ITEMS.register("spirit_spawn_egg",
            () -> new DeferredSpawnEggItem(ModEntities.SPIRIT, 0x7FE6D6, 0xFFF1B8, new Item.Properties()));

    public static final DeferredItem<SoulBlockItem> CAGED_SOUL_BLOCK_ITEM = ITEMS.register("caged_soul_block",
            () -> new SoulBlockItem(CAGED_SOUL_BLOCK.get(), new Item.Properties().rarity(ModRarities.EPIC.getValue())));
    public static final DeferredItem<SoulBlockItem> CHARGED_SOUL_BLOCK_ITEM = ITEMS.register("charged_soul_block",
            () -> new SoulBlockItem(CHARGED_SOUL_BLOCK.get(), new Item.Properties().rarity(ModRarities.LEGENDARY.getValue())));

    // Soul Crystal: the floating form of a Charged Soul Block. They turn into each other in the crafting grid.
    public static final DeferredItem<SoulCrystalItem> SOUL_CRYSTAL_ITEM = ITEMS.register("soul_crystal",
            () -> new SoulCrystalItem(new Item.Properties().rarity(ModRarities.LEGENDARY.getValue())));

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
                for (SoulId soul : SoulId.values()) {
                    output.accept(SoulBlockItem.withSoul(SOUL_CRYSTAL_ITEM.get(), soul));
                }
                output.accept(SPIRIT_SPAWN_EGG.get());
            }).build());

    // The constructor is the first code that runs when the mod is loaded.
    public MistvaleRpg(IEventBus modEventBus, ModContainer modContainer) {
        BLOCKS.register(modEventBus);
        ITEMS.register(modEventBus);
        CREATIVE_MODE_TABS.register(modEventBus);
        ModBlockEntities.BLOCK_ENTITIES.register(modEventBus);
        ModEntities.ENTITIES.register(modEventBus);
        ModSounds.SOUNDS.register(modEventBus);
        ModParticles.PARTICLES.register(modEventBus);
        ModAttachments.ATTACHMENTS.register(modEventBus);
        PrologueRegistry.register(modEventBus);                  // the sea of the prologue: its ground and its biomes

        modEventBus.addListener(ModEntities::onAttributes);      // health and so on for the Spirit
        modEventBus.addListener(ModNetwork::register);           // every message between the server and the player's game
        NeoForge.EVENT_BUS.addListener(ModCommands::onRegisterCommands);
        NeoForge.EVENT_BUS.addListener(SoulPlatform::onLevelTick);   // the soul platform's ritual steps
        NeoForge.EVENT_BUS.addListener(Later::onServerTick);         // "do this in a moment": the timing of every cutscene
        NeoForge.EVENT_BUS.addListener(Later::onServerStopped);
        // The rules of the prologue sea (see PrologueEvents).
        NeoForge.EVENT_BUS.addListener(PrologueEvents::onHurt);
        NeoForge.EVENT_BUS.addListener(PrologueEvents::onKrakenHurt);
        NeoForge.EVENT_BUS.addListener(PrologueEvents::onDeath);
        NeoForge.EVENT_BUS.addListener(PrologueEvents::onJoin);
        NeoForge.EVENT_BUS.addListener(PrologueEvents::onNaturalSpawn);
        NeoForge.EVENT_BUS.addListener(PrologueEvents::onPirateDrops);
        NeoForge.EVENT_BUS.addListener(PrologueEvents::onPirateXp);
        // A player who logs out in the middle of a conversation must not stay "talking" forever.
        NeoForge.EVENT_BUS.addListener((PlayerEvent.PlayerLoggedOutEvent event) -> {
            if (event.getEntity() instanceof ServerPlayer player) {
                DialogueManager.forget(player);
            }
        });
    }
}
