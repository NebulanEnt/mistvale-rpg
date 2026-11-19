package com.example.mistvalerpg.prologue;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.levelgen.Heightmap;

/**
 * The dark spires that stand on the islands: tall, thin needles of deepslate and blackstone that lean a little to one side.
 * They are placed by arithmetic, like everything else in the sea: the same spires stand in the same places in every world.
 */
final class PrologueDecor {
    // What a spire is made of. A block is picked from this list by a scrambled number, so the stone looks mixed.
    private static final BlockState[] SPIRE = {
            Blocks.DEEPSLATE.defaultBlockState(),
            Blocks.BLACKSTONE.defaultBlockState(),
            Blocks.POLISHED_BLACKSTONE.defaultBlockState(),
            Blocks.DEEPSLATE.defaultBlockState(),
            Blocks.BASALT.defaultBlockState(),
            Blocks.COBBLED_DEEPSLATE.defaultBlockState(),
            Blocks.OBSIDIAN.defaultBlockState(),
            Blocks.BLACKSTONE.defaultBlockState()
    };

    /** Looks at every spot where a spire could be built near this chunk, and builds the parts that fall inside the chunk. */
    static void decorate(ChunkAccess chunk) {
        ChunkPos pos = chunk.getPos();
        int minX = pos.getMinBlockX();
        int minZ = pos.getMinBlockZ();

        // The sea is cut into squares of 30 x 30 blocks. Each square has one possible spire spot, somewhere inside it.
        // A spire is wider than its spot, so the squares around the chunk are checked too.
        for (int cellX = Math.floorDiv(minX - 8, 30); cellX <= Math.floorDiv(minX + 24, 30); cellX++) {
            for (int cellZ = Math.floorDiv(minZ - 8, 30); cellZ <= Math.floorDiv(minZ + 24, 30); cellZ++) {
                int x = cellX * 30 + 3 + (int) (PrologueLayout.hash01(41L, cellX, cellZ) * 24.0);
                int z = cellZ * 30 + 3 + (int) (PrologueLayout.hash01(42L, cellX, cellZ) * 24.0);
                PrologueLayout.Col col = PrologueLayout.col(x, z);
                // Only on high land (not on the beach), not in the circle kept clear around the player's beach, and only sometimes:
                // half of the spots on the home island, about one in five elsewhere.
                boolean wanted = col.land() && col.top() > 67 && !(col.t() < 0.3) && !PrologueLayout.reserved(x, z)
                        && !(PrologueLayout.hash01(43L, cellX, cellZ) > (col.home() ? 0.5 : 0.18));
                if (wanted) {
                    spire(chunk, x, col.top(), z, cellX, cellZ);
                }
            }
        }
    }

    // Puts one block into the chunk, but only if it lies inside this chunk. (The same spire is built piece by piece by each chunk it touches.)
    private static void put(ChunkAccess chunk, int x, int y, int z, BlockState state) {
        ChunkPos pos = chunk.getPos();
        if (x >= pos.getMinBlockX() && x <= pos.getMaxBlockX() && z >= pos.getMinBlockZ() && z <= pos.getMaxBlockZ() && y >= 1 && y < PrologueLayout.HEIGHT) {
            chunk.setBlockState(new BlockPos(x, y, z), state, false);
            chunk.getOrCreateHeightmapUnprimed(Heightmap.Types.WORLD_SURFACE_WG).update(x & 15, y, z & 15, state);
        }
    }

    // One spire: a stack of thin discs, getting narrower towards the top, each slid a little sideways (the lean).
    private static void spire(ChunkAccess chunk, int x, int groundY, int z, int cellX, int cellZ) {
        double height = 18.0 + PrologueLayout.hash01(44L, cellX, cellZ) * 22.0;
        double baseRadius = 2.3 + PrologueLayout.hash01(45L, cellX, cellZ) * 1.7;
        double leanX = (PrologueLayout.hash01(46L, cellX, cellZ) - 0.5) * 0.35;
        double leanZ = (PrologueLayout.hash01(47L, cellX, cellZ) - 0.5) * 0.35;

        for (int level = -3; level < (int) height; level++) {
            double progress = (double) Math.max(0, level) / height;   // 0 at the bottom, almost 1 at the tip
            double radius = baseRadius * Math.pow(1.0 - progress, 1.25) + (PrologueLayout.hash01(48L, level, cellX * 31 + cellZ) - 0.5) * 0.7;
            int centreX = x + (int) Math.round(leanX * level);
            int centreZ = z + (int) Math.round(leanZ * level);
            int reach = (int) Math.ceil(radius);

            for (int dx = -reach; dx <= reach; dx++) {
                for (int dz = -reach; dz <= reach; dz++) {
                    boolean inside = !((double) (dx * dx + dz * dz) > radius * radius + 0.25) || dx == 0 && dz == 0;
                    if (inside) {
                        int pick = (int) (PrologueLayout.mix((long) (centreX + dx) * 341873128712L + (long) (centreZ + dz) * 132897987541L + (long) level * 31L) >>> 40);
                        // One block in 45 is crying obsidian, the purple glow among the dark stone.
                        BlockState state = pick % 45 == 0 ? Blocks.CRYING_OBSIDIAN.defaultBlockState() : SPIRE[pick % SPIRE.length];
                        put(chunk, centreX + dx, groundY + 1 + level, centreZ + dz, state);
                    }
                }
            }
        }
    }

    private PrologueDecor() {
    }
}