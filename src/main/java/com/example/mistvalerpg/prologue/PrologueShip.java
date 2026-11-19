package com.example.mistvalerpg.prologue;

import java.util.Optional;

import org.slf4j.Logger;

import com.example.mistvalerpg.MistvaleRpg;
import com.mojang.logging.LogUtils;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Vec3i;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.templatesystem.BlockIgnoreProcessor;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.minecraft.world.phys.AABB;

/**
 * The Kraken's ship. We do not build it ourselves: Bosses'Rise (the mod, whose id is "block_factorys_bosses") ships the ship
 * as three "structure files" (a front, a back and a top), and we ask the game to place those three pieces into the sea.
 * Every player has their own copy of the sea (see PrologueSea), so every player gets their own ship, 4096 blocks apart.
 */
public final class PrologueShip {
    private static final Logger LOG = LogUtils.getLogger();

    /** The id of the mod that owns the ship. */
    public static final String SHIP_MOD = "block_factorys_bosses";

    /** The height of the ship's keel. The sea is at 63, so the ship sits in the water. */
    public static final int KEEL_Y = 60;

    private static ResourceLocation owned(String name) {
        return ResourceLocation.fromNamespaceAndPath(SHIP_MOD, name);
    }

    /** The x of the ship for a sea number: 1600 plus one sea width for each number. */
    public static int originX(int slot) {
        return PrologueLayout.SHIP_X + PrologueSea.shift(slot);
    }

    /** Where the player stands on the deck at the start. */
    public static BlockPos deckSpawn(int slot) {
        return new BlockPos(originX(slot) + 14, 69, 12);
    }

    /**
     * Where the Kraken's spawner block is built into the ship (the ship's files have it at the bow). While it stands there,
     * the Kraken comes at once. So it is taken out when the ship is placed, and put back when the fight is ready for it.
     */
    public static BlockPos spawnerPos(int slot) {
        return new BlockPos(originX(slot) + 14, 69, -6);
    }

    /** The side the spawner block faces, as the ship's files have it. */
    public static final Direction SPAWNER_FACING = Direction.NORTH;

    /** The box around the whole ship. Anything inside it belongs to the fight. */
    public static AABB bounds(int slot) {
        int x = originX(slot);
        return new AABB(x - 3, 59.0, -51.0, x + 32, 130.0, 31.0);
    }

    /**
     * Places the three pieces of the ship. Returns false if the game does not have the ship's files, which means that
     * Bosses'Rise is not installed.
     */
    public static boolean placeShip(ServerLevel sea, int slot) {
        int x = originX(slot);
        load(sea, x - 8, -60, x + 40, 36);
        // The three pieces fit together like a puzzle; the numbers are where each one goes.
        boolean placed = piece(sea, "kraken_ship_back", new BlockPos(x, KEEL_Y, 0), Rotation.NONE)
                & piece(sea, "kraken_ship_front", new BlockPos(x, KEEL_Y, -48), Rotation.NONE)
                & piece(sea, "kraken_ship_top", new BlockPos(x, 108, -11), Rotation.NONE);
        if (placed) {
            // Take the Kraken's spawner out for now.
            BlockPos spawner = spawnerPos(slot);
            if (BuiltInRegistries.BLOCK.getKey(sea.getBlockState(spawner).getBlock()).equals(owned("kraken_spawner"))) {
                sea.setBlock(spawner, Blocks.AIR.defaultBlockState(), 2);
            }
        }
        return placed;
    }

    // Places one structure file at a position.
    private static boolean piece(ServerLevel sea, String name, BlockPos at, Rotation rotation) {
        Optional<StructureTemplate> template = sea.getStructureManager().get(owned(name));
        if (template.isEmpty()) {
            LOG.error("{}: the structure {} was not found. Is Bosses'Rise installed?", MistvaleRpg.MODID, owned(name));
            return false;
        }
        StructurePlaceSettings settings = new StructurePlaceSettings()
                .setRotation(rotation)
                .setIgnoreEntities(true)
                // Structure files have "air" and "structure void" blocks that must not wipe what is already there.
                .addProcessor(BlockIgnoreProcessor.STRUCTURE_AND_AIR);
        boolean placed = template.get().placeInWorld(sea, at, at, settings, sea.getRandom(), 2);
        if (placed) {
            removeMarkers(sea, at, template.get().getSize(rotation));
        }
        return placed;
    }

    // Structure files contain helper blocks (jigsaw, structure block, barrier) that are only there for the builders.
    // They are turned into air, so nobody sees them.
    private static void removeMarkers(ServerLevel sea, BlockPos from, Vec3i size) {
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        for (int dx = 0; dx < size.getX(); dx++) {
            for (int dy = 0; dy < size.getY(); dy++) {
                for (int dz = 0; dz < size.getZ(); dz++) {
                    pos.set(from.getX() + dx, from.getY() + dy, from.getZ() + dz);
                    BlockState state = sea.getBlockState(pos);
                    if (state.is(Blocks.JIGSAW) || state.is(Blocks.STRUCTURE_BLOCK) || state.is(Blocks.STRUCTURE_VOID) || state.is(Blocks.BARRIER)) {
                        sea.removeBlockEntity(pos);
                        sea.setBlock(pos, Blocks.AIR.defaultBlockState(), 2);
                    }
                }
            }
        }
    }

    // Asks for every chunk in a rectangle, so that the ground exists before blocks are placed into it.
    private static void load(ServerLevel sea, int fromX, int fromZ, int toX, int toZ) {
        for (int chunkX = Mth.floor(fromX / 16.0); chunkX <= Mth.floor(toX / 16.0); chunkX++) {
            for (int chunkZ = Mth.floor(fromZ / 16.0); chunkZ <= Mth.floor(toZ / 16.0); chunkZ++) {
                sea.getChunk(chunkX, chunkZ);
            }
        }
    }

    private PrologueShip() {
    }
}