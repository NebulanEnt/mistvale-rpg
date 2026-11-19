package com.example.mistvalerpg.prologue;

import com.mojang.serialization.MapCodec;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.BiomeSource;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * Teaches the game two new kinds of things: a way to build the ground of a world (a "chunk generator") and a way to
 * choose its biomes (a "biome source"). Both are used by the prologue's sea, the private ocean where a new player begins.
 *
 * The names are registered under "mistvale", the name the first version of the mod used, and not under "mistvale_rpg".
 * A world that has already visited the sea has those names written into its save files. If this mod knows them under the
 * same names, that world loads with either version of the mod.
 */
public final class PrologueRegistry {
    /** The old name of the mod. Only the sea uses it. Everything else the mod adds lives under "mistvale_rpg". */
    public static final String SEA_NAMESPACE = "mistvale";

    public static final DeferredRegister<MapCodec<? extends ChunkGenerator>> GENERATORS =
            DeferredRegister.create(Registries.CHUNK_GENERATOR, SEA_NAMESPACE);
    public static final DeferredRegister<MapCodec<? extends BiomeSource>> BIOME_SOURCES =
            DeferredRegister.create(Registries.BIOME_SOURCE, SEA_NAMESPACE);

    /** The address of the sea dimension: "mistvale:prologue". Its description is the file data/mistvale/dimension/prologue.json. */
    public static final ResourceKey<Level> SEA = ResourceKey.create(Registries.DIMENSION,
            ResourceLocation.fromNamespaceAndPath(SEA_NAMESPACE, "prologue"));

    public static void register(IEventBus modEventBus) {
        // A codec is the recipe for reading a thing from a JSON file. The JSON says "type": "mistvale:prologue" and the game finds the recipe here.
        GENERATORS.register("prologue", () -> PrologueChunkGenerator.CODEC);
        BIOME_SOURCES.register("prologue", () -> PrologueBiomeSource.CODEC);
        GENERATORS.register(modEventBus);
        BIOME_SOURCES.register(modEventBus);
    }

    private PrologueRegistry() {
    }
}