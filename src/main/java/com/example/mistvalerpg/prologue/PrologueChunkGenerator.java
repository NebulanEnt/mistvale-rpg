package com.example.mistvalerpg.prologue;

import java.util.List;
import java.util.concurrent.CompletableFuture;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderSet;
import net.minecraft.core.RegistryAccess;
import net.minecraft.server.level.WorldGenRegion;
import net.minecraft.util.random.WeightedRandomList;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.level.LevelHeightAccessor;
import net.minecraft.world.level.NoiseColumn;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeGenerationSettings;
import net.minecraft.world.level.biome.BiomeManager;
import net.minecraft.world.level.biome.BiomeSource;
import net.minecraft.world.level.biome.MobSpawnSettings;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.chunk.ChunkGeneratorStructureState;
import net.minecraft.world.level.levelgen.GenerationStep;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.levelgen.blending.Blender;
import net.minecraft.world.level.levelgen.placement.PlacedFeature;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplateManager;

/**
 * Builds the ground of the sea, one chunk (16 x 16 blocks) at a time, from the map in PrologueLayout. It makes no caves, no
 * ores, no structures and no animals: a sea and nothing in it.
 */
public final class PrologueChunkGenerator extends ChunkGenerator {
    public static final MapCodec<PrologueChunkGenerator> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
            BiomeSource.CODEC.fieldOf("biome_source").forGetter(generator -> generator.biomeSource),
            // Extra things to grow on the islands, named in the dimension's JSON file. The file may leave them out.
            PlacedFeature.LIST_CODEC.optionalFieldOf("extra_features", HolderSet.direct()).forGetter(generator -> generator.extra)
    ).apply(instance, PrologueChunkGenerator::new));

    private static final BlockState STONE = Blocks.STONE.defaultBlockState();
    private static final BlockState DEEPSLATE = Blocks.DEEPSLATE.defaultBlockState();
    private static final BlockState WATER = Blocks.WATER.defaultBlockState();
    private static final BlockState SAND = Blocks.SAND.defaultBlockState();
    private static final BlockState GRAVEL = Blocks.GRAVEL.defaultBlockState();
    private static final BlockState DIRT = Blocks.DIRT.defaultBlockState();
    private static final BlockState GRASS = Blocks.GRASS_BLOCK.defaultBlockState();
    private static final BlockState BEDROCK = Blocks.BEDROCK.defaultBlockState();
    private static final BlockState AIR = Blocks.AIR.defaultBlockState();

    private final HolderSet<PlacedFeature> extra;

    public PrologueChunkGenerator(BiomeSource biomeSource, HolderSet<PlacedFeature> extra) {
        super(biomeSource, biome -> onlyGentleFeatures(biome, extra));
        this.extra = extra;
    }

    // Every biome comes with a list of things it grows (trees, flowers, lava pools, ores...). The sea keeps the gentle ones
    // and leaves out lava, geodes, monsters, springs, magma, ores and glow lichen. Step 2 and 9 are "vegetation" and 10 is "top layer".
    private static BiomeGenerationSettings onlyGentleFeatures(Holder<Biome> biome, HolderSet<PlacedFeature> extra) {
        BiomeGenerationSettings original = biome.value().getGenerationSettings();
        BiomeGenerationSettings.PlainBuilder builder = new BiomeGenerationSettings.PlainBuilder();
        List<HolderSet<PlacedFeature>> steps = original.features();
        for (int step : new int[] {2, 9, 10}) {
            if (step < steps.size()) {
                for (Holder<PlacedFeature> feature : steps.get(step)) {
                    String name = feature.unwrapKey().map(key -> key.location().getPath()).orElse("");
                    if (!name.contains("lava") && !name.contains("geode") && !name.contains("monster") && !name.contains("spring")
                            && !name.contains("magma") && !name.contains("ore_") && !name.contains("glow_lichen")) {
                        builder.addFeature(step, feature);
                    }
                }
            }
        }
        // The extra features (the palms) grow everywhere except in an ocean.
        if (!biome.is(key -> key.location().getPath().contains("ocean"))) {
            for (Holder<PlacedFeature> feature : extra) {
                builder.addFeature(9, feature);
            }
        }
        return builder.build();
    }

    @Override
    protected MapCodec<? extends ChunkGenerator> codec() {
        return CODEC;
    }

    // No animals and no monsters from the map itself. The prologue puts its own creatures in by hand.
    @Override
    public WeightedRandomList<MobSpawnSettings.SpawnerData> getMobsAt(Holder<Biome> biome, StructureManager structures, MobCategory category, BlockPos pos) {
        return WeightedRandomList.create();
    }

    // The things this generator does not do. They are empty on purpose.
    @Override
    public void createStructures(RegistryAccess registries, ChunkGeneratorStructureState state, StructureManager structures, ChunkAccess chunk, StructureTemplateManager templates) {
    }

    @Override
    public void createReferences(WorldGenLevel level, StructureManager structures, ChunkAccess chunk) {
    }

    @Override
    public void applyCarvers(WorldGenRegion region, long seed, RandomState random, BiomeManager biomes, StructureManager structures, ChunkAccess chunk, GenerationStep.Carving step) {
    }

    @Override
    public void buildSurface(WorldGenRegion region, StructureManager structures, RandomState random, ChunkAccess chunk) {
    }

    @Override
    public void spawnOriginalMobs(WorldGenRegion region) {
    }

    @Override
    public int getGenDepth() {
        return PrologueLayout.HEIGHT;
    }

    @Override
    public int getSeaLevel() {
        return PrologueLayout.SEA;
    }

    @Override
    public int getMinY() {
        return 0;
    }

    // How high the ground is at a column. Asked by many things in the game (where to place a structure, where a mob may stand).
    @Override
    public int getBaseHeight(int x, int z, Heightmap.Types type, LevelHeightAccessor level, RandomState random) {
        PrologueLayout.Col col = PrologueLayout.col(x, z);
        boolean looksForTheFloor = type == Heightmap.Types.OCEAN_FLOOR || type == Heightmap.Types.OCEAN_FLOOR_WG;
        return (!looksForTheFloor && !col.land() ? PrologueLayout.SEA : col.top()) + 1;
    }

    // The column of blocks itself: stone up to the ground, water up to the sea level, air above.
    @Override
    public NoiseColumn getBaseColumn(int x, int z, LevelHeightAccessor level, RandomState random) {
        PrologueLayout.Col col = PrologueLayout.col(x, z);
        BlockState[] states = new BlockState[level.getHeight()];
        for (int i = 0; i < states.length; i++) {
            int y = level.getMinBuildHeight() + i;
            states[i] = y <= col.top() ? STONE : (y <= PrologueLayout.SEA ? WATER : AIR);
        }
        return new NoiseColumn(level.getMinBuildHeight(), states);
    }

    @Override
    public void addDebugScreenInfo(List<String> lines, RandomState random, BlockPos pos) {
        lines.add("Mistvale prologue sea");
    }

    /** The real work: fills one chunk with blocks. */
    @Override
    public CompletableFuture<ChunkAccess> fillFromNoise(Blender blender, RandomState random, StructureManager structures, ChunkAccess chunk) {
        int minX = chunk.getPos().getMinBlockX();
        int minZ = chunk.getPos().getMinBlockZ();
        Heightmap floorMap = chunk.getOrCreateHeightmapUnprimed(Heightmap.Types.OCEAN_FLOOR_WG);
        Heightmap surfaceMap = chunk.getOrCreateHeightmapUnprimed(Heightmap.Types.WORLD_SURFACE_WG);
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();

        for (int dx = 0; dx < 16; dx++) {
            for (int dz = 0; dz < 16; dz++) {
                int x = minX + dx;
                int z = minZ + dz;
                PrologueLayout.Col col = PrologueLayout.col(x, z);
                // Sand on beaches and in shallow water, grass and dirt on the higher parts of an island, gravel in the deep.
                boolean sandy = col.land() ? col.top() <= 66 : col.top() > 51;

                for (int y = 0; y <= col.top(); y++) {
                    BlockState state;
                    if (y == 0) {
                        state = BEDROCK;
                    } else if (y < col.top() - 4) {
                        state = y < 16 ? DEEPSLATE : STONE;
                    } else if (y < col.top()) {
                        state = sandy ? SAND : (col.land() ? DIRT : GRAVEL);
                    } else {
                        state = sandy ? SAND : (col.land() ? GRASS : (col.top() < 35 ? GRAVEL : SAND));
                    }
                    chunk.setBlockState(pos.set(x, y, z), state, false);
                }

                // The game wants to know the height of the highest block in every column ("heightmaps").
                BlockState topState = chunk.getBlockState(pos.set(x, col.top(), z));
                floorMap.update(dx, col.top(), dz, topState);
                if (col.land()) {
                    surfaceMap.update(dx, col.top(), dz, topState);
                } else {
                    for (int y = col.top() + 1; y <= PrologueLayout.SEA; y++) {
                        chunk.setBlockState(pos.set(x, y, z), WATER, false);
                    }
                    surfaceMap.update(dx, PrologueLayout.SEA, dz, WATER);
                }
            }
        }
        PrologueDecor.decorate(chunk);   // the dark spires
        return CompletableFuture.completedFuture(chunk);
    }
}