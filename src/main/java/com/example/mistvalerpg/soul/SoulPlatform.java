package com.example.mistvalerpg.soul;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.jetbrains.annotations.Nullable;

import com.example.mistvalerpg.MistvaleRpg;
import com.example.mistvalerpg.rift.RiftBlock;
import com.example.mistvalerpg.rift.RiftVariant;
import com.example.mistvalerpg.spirit.SpiritEntity;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.event.tick.LevelTickEvent;

/**
 * The ritual. Nine Charged Soul Blocks holding nine different souls wake up and, after a few seconds of
 * building power, tear open a Corrupted Rift above the middle block.
 *
 * The blocks do NOT have to sit in a neat square. One block is the centre. The other eight each
 * stand on their own "lane" going out from the centre, like the spokes of a wheel:
 * north, north-east, east, south-east, south, south-west, west and north-west.
 * A lane only allows certain spots (the "stations"): on the straight lanes a soul may stand
 * 1, 3, 5, 6 or 8 blocks out, on the diagonal lanes 1, 2, 3, 4 or 5 steps out. Any soul may stand on any lane,
 * and the blocks may be at any height, so players can build circles, spirals and obelisks.
 * Only the centre block matters for the height of the rift: it opens right above the centre.
 *
 * A Soul Crystal floating in a cell counts exactly like a Charged Soul Block in that cell.
 *
 * Nothing is stored in the world for this: whenever a charged block (or crystal) is placed we simply look
 * around it to see if a finished platform exists.
 */
public final class SoulPlatform {
    /** The ritual takes 20 steps, one every 4 ticks: 4 seconds in total. */
    public static final int STEP_TICKS = 4;
    public static final int STEPS = 20;
    /** The rift closes by itself after 6000 ticks (5 minutes). */
    public static final int RIFT_LIFETIME = 6000;

    // The eight lanes as {east, south} steps: N, NE, E, SE, S, SW, W, NW.
    private static final int[][] LANE_DIRECTIONS = { { 0, -1 }, { 1, -1 }, { 1, 0 }, { 1, 1 }, { 0, 1 }, { -1, 1 }, { -1, 0 }, { -1, -1 } };
    // Where along a lane a soul may stand. The straight lanes are broken into separate segments; the diagonal ones are unbroken.
    private static final int[] STRAIGHT_STATIONS = { 1, 3, 5, 6, 8 };
    private static final int[] DIAGONAL_STATIONS = { 1, 2, 3, 4, 5 };

    /** STATIONS[lane][n] is the {east, south} distance from the centre of the n-th allowed spot on that lane. */
    private static final int[][][] STATIONS = buildStations();

    private static int[][][] buildStations() {
        int[][][] stations = new int[LANE_DIRECTIONS.length][][];
        for (int lane = 0; lane < LANE_DIRECTIONS.length; lane++) {
            int[] direction = LANE_DIRECTIONS[lane];
            boolean diagonal = direction[0] != 0 && direction[1] != 0;
            int[] steps = diagonal ? DIAGONAL_STATIONS : STRAIGHT_STATIONS;
            stations[lane] = new int[steps.length][];
            for (int n = 0; n < steps.length; n++) {
                stations[lane][n] = new int[] { direction[0] * steps[n], direction[1] * steps[n] };
            }
        }
        return stations;
    }

    /** A finished platform: the centre block and the eight blocks around it, one per lane. */
    public record Layout(BlockPos center, List<BlockPos> outer) {
    }

    /** A ritual that is running right now. Index 0 of the lists is the centre block. */
    private static final class Ritual {
        final List<BlockPos> blocks;
        final List<SoulId> souls;
        int step = 0;
        long nextAt; // the game time of the next step

        Ritual(List<BlockPos> blocks, List<SoulId> souls) {
            this.blocks = blocks;
            this.souls = souls;
        }
    }

    // Which platforms are mid-ritual right now. Kept in memory only.
    private static final Map<GlobalPos, Ritual> RITUALS = new ConcurrentHashMap<>();

    /** Where the Corrupted Rift leads. Nothing sets it yet: until the champion's arena exists it stays empty. */
    @Nullable
    public static volatile GlobalPos championDestination = null;

    // ---- Finding a platform ----

    /**
     * Looks around a freshly placed block for a finished platform it belongs to. The new block is either
     * the centre itself, or stands on one of the stations of the centre, so we only have to try
     * those few columns to find every possible centre.
     */
    @Nullable
    public static Layout find(ServerLevel level, BlockPos placed) {
        List<BlockPos> centres = new ArrayList<>(chargedInColumn(level, placed.getX(), placed.getZ()));
        for (int[][] lane : STATIONS) {
            for (int[] station : lane) {
                centres.addAll(chargedInColumn(level, placed.getX() - station[0], placed.getZ() - station[1]));
            }
        }
        // Try the centres closest to the new block's height first.
        centres.sort(Comparator.comparingInt(centre -> Math.abs(centre.getY() - placed.getY())));
        for (BlockPos centre : centres) {
            Layout layout = layoutAround(level, centre);
            if (layout != null) {
                return layout;
            }
        }
        return null;
    }

    /** Checks one candidate centre: does every lane hold a soul, so that all nine souls together are different? */
    @Nullable
    public static Layout layoutAround(ServerLevel level, BlockPos centre) {
        SoulId centreSoul = soulAt(level, centre);
        if (centreSoul == null) {
            return null;
        }

        // For each lane, every charged block standing on one of its stations, at any height.
        List<List<Found>> lanes = new ArrayList<>();
        for (int[][] lane : STATIONS) {
            List<Found> found = new ArrayList<>();
            for (int[] station : lane) {
                for (BlockPos pos : chargedInColumn(level, centre.getX() + station[0], centre.getZ() + station[1])) {
                    SoulId soul = soulAt(level, pos);
                    if (soul != null && soul != centreSoul) {
                        found.add(new Found(pos, soul));
                    }
                }
            }
            if (found.isEmpty()) {
                return null; // an empty lane: the platform is not finished
            }
            lanes.add(found);
        }

        EnumSet<SoulId> used = EnumSet.of(centreSoul);
        BlockPos[] chosen = new BlockPos[lanes.size()];
        if (!assign(lanes, 0, used, chosen)) {
            return null;
        }
        return new Layout(centre, List.of(chosen));
    }

    private record Found(BlockPos pos, SoulId soul) {
    }

    // Picks one block for each lane so that no soul is used twice. If a lane holds a spare block, we may need to try
    // another one, so this tries every choice and steps back when it gets stuck ("backtracking").
    private static boolean assign(List<List<Found>> lanes, int lane, EnumSet<SoulId> used, BlockPos[] chosen) {
        if (lane == lanes.size()) {
            return true;
        }
        for (Found candidate : lanes.get(lane)) {
            if (used.add(candidate.soul())) {
                chosen[lane] = candidate.pos();
                if (assign(lanes, lane + 1, used, chosen)) {
                    return true;
                }
                used.remove(candidate.soul());
            }
        }
        return false;
    }

    /**
     * The soul standing in one cell of the world: a Charged Soul Block, or a Soul Crystal floating in that cell.
     * Returns null when the cell holds neither.
     */
    @Nullable
    public static SoulId soulAt(ServerLevel level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        if (state.is(MistvaleRpg.CHARGED_SOUL_BLOCK.get())) {
            return state.getValue(SoulId.PROPERTY);
        }
        List<SoulCrystalEntity> crystals = level.getEntitiesOfClass(SoulCrystalEntity.class, new AABB(pos).deflate(0.01));
        return crystals.isEmpty() ? null : crystals.get(0).soul();
    }

    // Every charged block and every crystal in one column of the world, from the lowest layer to the highest. Unloaded columns count as empty.
    private static List<BlockPos> chargedInColumn(ServerLevel level, int x, int z) {
        List<BlockPos> result = new ArrayList<>();
        if (!level.hasChunkAt(new BlockPos(x, level.getMinBuildHeight(), z))) {
            return result;
        }
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos(x, 0, z);
        for (int y = level.getMinBuildHeight(); y < level.getMaxBuildHeight(); y++) {
            cursor.setY(y);
            if (level.getBlockState(cursor).is(MistvaleRpg.CHARGED_SOUL_BLOCK.get())) {
                result.add(cursor.immutable());
            }
        }
        // Crystals are entities, so they are found by asking for everything inside this column's box.
        AABB column = new AABB(x, level.getMinBuildHeight(), z, x + 1, level.getMaxBuildHeight(), z + 1).deflate(0.01, 0.0, 0.01);
        for (SoulCrystalEntity crystal : level.getEntitiesOfClass(SoulCrystalEntity.class, column)) {
            BlockPos cell = crystal.blockPosition();
            if (!result.contains(cell)) {
                result.add(cell);
            }
        }
        return result;
    }

    // ---- Starting the ritual ----

    public static void onChargedPlaced(ServerLevel level, BlockPos placed) {
        Layout layout = find(level, placed);
        if (layout != null) {
            startRitual(level, layout);
        }
    }

    public static boolean startRitual(ServerLevel level, Layout layout) {
        BlockPos center = layout.center();
        if (isBusy(level, layout)) {
            return false;
        }
        BlockPos above = center.above();
        if (level.getBlockState(above).getBlock() instanceof RiftBlock) {
            tell(level, center, "message.mistvale_rpg.soul_platform.rift_open");
            return false;
        }
        if (!level.getBlockState(above).canBeReplaced()) {
            tell(level, center, "message.mistvale_rpg.soul_platform.no_room");
            return false;
        }

        List<BlockPos> blocks = new ArrayList<>();
        blocks.add(center);
        blocks.addAll(layout.outer());
        List<SoulId> souls = new ArrayList<>();
        for (BlockPos pos : blocks) {
            souls.add(soulAt(level, pos));
        }
        Ritual ritual = new Ritual(blocks, souls);
        ritual.nextAt = level.getGameTime() + STEP_TICKS;
        RITUALS.put(GlobalPos.of(level.dimension(), center), ritual);
        level.playSound(null, center, SoundEvents.SCULK_SHRIEKER_SHRIEK, SoundSource.BLOCKS, 0.8F, 0.5F);
        return true;
    }

    // A block that already takes part in a running ritual must not start a second one.
    private static boolean isBusy(ServerLevel level, Layout layout) {
        for (Map.Entry<GlobalPos, Ritual> entry : RITUALS.entrySet()) {
            if (!entry.getKey().dimension().equals(level.dimension())) {
                continue;
            }
            if (entry.getValue().blocks.contains(layout.center()) || layout.outer().stream().anyMatch(entry.getValue().blocks::contains)) {
                return true;
            }
        }
        return false;
    }

    // ---- The ritual itself ----

    /**
     * Runs after every world tick (see MistvaleRpg). Each running ritual in this world takes its next step
     * when its time has come. A world clock is used instead of asking the middle block to tick,
     * because the middle may be a floating crystal, and a crystal is not a block.
     */
    public static void onLevelTick(LevelTickEvent.Post event) {
        if (!(event.getLevel() instanceof ServerLevel level) || RITUALS.isEmpty()) {
            return;
        }
        long now = level.getGameTime();
        for (Map.Entry<GlobalPos, Ritual> entry : RITUALS.entrySet()) {
            if (entry.getKey().dimension().equals(level.dimension()) && entry.getValue().nextAt <= now) {
                ritualTick(level, entry.getKey().pos());
            }
        }
    }

    /** One step of the ritual. Called by onLevelTick every STEP_TICKS ticks. */
    private static void ritualTick(ServerLevel level, BlockPos center) {
        GlobalPos key = GlobalPos.of(level.dimension(), center);
        Ritual ritual = RITUALS.get(key);
        if (ritual == null) {
            return;
        }
        if (!stillStanding(level, ritual)) {
            // Somebody broke or moved a block: the power drains away.
            RITUALS.remove(key);
            level.playSound(null, center, SoundEvents.RESPAWN_ANCHOR_DEPLETE.value(), SoundSource.BLOCKS, 1.0F, 0.6F);
            tell(level, center, "message.mistvale_rpg.soul_platform.fizzle");
            return;
        }

        int next = ritual.step + 1;
        float progress = (float) next / STEPS;
        Vec3 focus = Vec3.atCenterOf(center).add(0.0, 1.6, 0.0); // the point above the middle where the rift will open

        for (int i = 0; i < ritual.blocks.size(); i++) {
            BlockPos pos = ritual.blocks.get(i);
            Vec3 rgb = Vec3.fromRGB24(ritual.souls.get(i).color());
            Vec3 top = new Vec3(pos.getX() + 0.5, pos.getY() + 1.05, pos.getZ() + 0.5);
            // a puff of the soul's own colour on top of every block...
            level.sendParticles(new DustParticleOptions(rgb.toVector3f(), 1.2F), top.x, top.y, top.z, 3, 0.25, 0.05, 0.25, 0.0);
            if (i == 0) {
                continue; // the centre is where everything flows to
            }
            // ...and a string of sparks that runs from each outer block to the focus, however far away or high up it stands.
            Vec3 line = focus.subtract(top);
            int sparks = 5;
            for (int k = 0; k < sparks; k++) {
                double t = (next * 0.19 + (double) k / sparks) % 1.0;
                Vec3 p = top.add(line.scale(t));
                level.sendParticles(new DustParticleOptions(rgb.toVector3f(), 0.7F + 0.5F * progress), p.x, p.y, p.z, 1, 0.04, 0.04, 0.04, 0.0);
                if (k % 2 == 0) {
                    level.sendParticles(ParticleTypes.END_ROD, p.x, p.y, p.z, 0, 0.0, 0.0, 0.0, 0.0);
                }
            }
        }
        level.sendParticles(ParticleTypes.SCULK_SOUL, focus.x, focus.y, focus.z, 2 + (int) (6 * progress), 0.35, 0.5, 0.35, 0.01);
        level.playSound(null, center, SoundEvents.RESPAWN_ANCHOR_CHARGE, SoundSource.BLOCKS, 0.9F, 0.5F + progress);
        if (next == STEPS / 2) {
            level.playSound(null, center, SoundEvents.WARDEN_HEARTBEAT, SoundSource.BLOCKS, 1.5F, 0.7F);
        }

        if (next >= STEPS) {
            RITUALS.remove(key);
            openRift(level, center);
        } else {
            ritual.step = next;
            ritual.nextAt = level.getGameTime() + STEP_TICKS;
        }
    }

    // True while every block (or crystal) of the ritual is still there, holding the same soul as when it began.
    private static boolean stillStanding(ServerLevel level, Ritual ritual) {
        for (int i = 0; i < ritual.blocks.size(); i++) {
            if (soulAt(level, ritual.blocks.get(i)) != ritual.souls.get(i)) {
                return false;
            }
        }
        return true;
    }

    private static void openRift(ServerLevel level, BlockPos center) {
        // The rift faces whoever is standing closest, so they can walk straight into it.
        Player nearest = level.getNearestPlayer(center.getX() + 0.5, center.getY() + 0.5, center.getZ() + 0.5, 32.0, false);
        Direction facing = nearest == null
                ? Direction.NORTH
                : Direction.getNearest(nearest.getX() - (center.getX() + 0.5), 0.0, nearest.getZ() - (center.getZ() + 0.5));
        if (facing.getAxis() == Direction.Axis.Y) {
            facing = Direction.NORTH;
        }

        RiftBlock.spawn(level, center.above(), facing, RiftVariant.CORRUPTED, championDestination, RIFT_LIFETIME, true);
        level.playSound(null, center, SoundEvents.END_PORTAL_SPAWN, SoundSource.BLOCKS, 0.7F, 0.6F);
        level.playSound(null, center, SoundEvents.SCULK_SHRIEKER_SHRIEK, SoundSource.BLOCKS, 1.2F, 0.4F);
        level.sendParticles(ParticleTypes.SONIC_BOOM, center.getX() + 0.5, center.getY() + 1.8, center.getZ() + 0.5, 1, 0.0, 0.0, 0.0, 0.0);

        // The Spirit comes to witness it, close to the rift and out of the way.
        SpiritEntity.appearNear(level, center.relative(facing, 3).above());
    }

    // A short line above the hotbar for everyone standing near the platform.
    private static void tell(ServerLevel level, BlockPos center, String translationKey) {
        for (ServerPlayer player : level.getEntitiesOfClass(ServerPlayer.class, new AABB(center).inflate(16.0))) {
            player.displayClientMessage(Component.translatable(translationKey), true);
        }
    }

    private SoulPlatform() {
    }
}
