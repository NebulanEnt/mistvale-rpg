package com.example.mistvalerpg.prologue;

import java.util.stream.Stream;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.core.Holder;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeSource;
import net.minecraft.world.level.biome.Climate;

/**
 * Decides which biome every spot of the sea has. There are five to choose from, named in the dimension's JSON file:
 * deep sea, shallow lagoon, beach, tropics and jungle. The layout (see PrologueLayout) says which one is where.
 */
public final class PrologueBiomeSource extends BiomeSource {
    public static final MapCodec<PrologueBiomeSource> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
            Biome.CODEC.fieldOf("deep").forGetter(source -> source.biomes[0]),
            Biome.CODEC.fieldOf("lagoon").forGetter(source -> source.biomes[1]),
            Biome.CODEC.fieldOf("beach").forGetter(source -> source.biomes[2]),
            Biome.CODEC.fieldOf("tropics").forGetter(source -> source.biomes[3]),
            Biome.CODEC.fieldOf("jungle").forGetter(source -> source.biomes[4])
    ).apply(instance, PrologueBiomeSource::new));

    /** The five biomes, in the order of the layout's numbers: 0 deep, 1 lagoon, 2 beach, 3 tropics, 4 jungle. */
    private final Holder<Biome>[] biomes;

    @SuppressWarnings("unchecked")
    public PrologueBiomeSource(Holder<Biome> deep, Holder<Biome> lagoon, Holder<Biome> beach, Holder<Biome> tropics, Holder<Biome> jungle) {
        this.biomes = new Holder[] {deep, lagoon, beach, tropics, jungle};
    }

    @Override
    protected MapCodec<? extends BiomeSource> codec() {
        return CODEC;
    }

    @Override
    protected Stream<Holder<Biome>> collectPossibleBiomes() {
        return Stream.of(biomes);
    }

    // The game asks for biomes in 4 x 4 blocks cells; the middle block of the cell is what we look up.
    @Override
    public Holder<Biome> getNoiseBiome(int cellX, int cellY, int cellZ, Climate.Sampler sampler) {
        return biomes[PrologueLayout.biomeAt(cellX * 4 + 2, cellZ * 4 + 2)];
    }
}