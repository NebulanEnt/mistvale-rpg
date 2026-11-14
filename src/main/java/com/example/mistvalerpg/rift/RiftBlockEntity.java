package com.example.mistvalerpg.rift;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.jetbrains.annotations.Nullable;

import com.example.mistvalerpg.ModBlockEntities;
import com.example.mistvalerpg.ModSounds;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import software.bernie.geckolib.animatable.GeoBlockEntity;
import software.bernie.geckolib.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.animation.AnimatableManager;
import software.bernie.geckolib.animation.AnimationController;
import software.bernie.geckolib.animation.RawAnimation;
import software.bernie.geckolib.util.GeckoLibUtil;

/**
 * The brain of a rift. It walks through four states (closed, opening, open, closing),
 * notices players crossing the open rift, and pulls them through after a short delay.
 */
public class RiftBlockEntity extends BlockEntity implements GeoBlockEntity {
    public enum State { CLOSED, OPENING, OPEN, CLOSING }

    // Lengths of the opening and closing animations, in ticks.
    private static final int OPEN_TICKS = 40;
    private static final int CLOSE_TICKS = 34;
    /** From stepping into the rift to actually arriving on the other side. */
    public static final int TELEPORT_DELAY = 32;

    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);
    private State state = State.CLOSED;
    private int timer = 0;
    private RiftVariant variant = RiftVariant.BLUE;
    @Nullable
    private GlobalPos destination;
    private int autoCloseIn = 0;
    private long closeAt = 0L;
    private boolean removeOnClose = false;

    // Not saved: who stood in front of the rift last tick, and who is being pulled through right now.
    private final Map<UUID, Boolean> frontSide = new HashMap<>();
    private final Map<UUID, Integer> pendingTeleports = new HashMap<>();
    private int fxCooldown = 0;

    public RiftBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.RIFT.get(), pos, state);
    }

    public State getRiftState() {
        return state;
    }

    public RiftVariant getVariant() {
        return variant;
    }

    @Nullable
    public GlobalPos getDestination() {
        return destination;
    }

    /** True while you can walk through it. */
    public boolean isPassable() {
        return state == State.OPEN;
    }

    public void configure(RiftVariant variant, @Nullable GlobalPos destination, int autoCloseTicks, boolean removeOnClose) {
        this.variant = variant;
        this.destination = destination;
        this.autoCloseIn = autoCloseTicks;
        this.removeOnClose = removeOnClose;
        sync();
    }

    // ---- Opening and closing ----

    public void open() {
        if (state != State.CLOSED && state != State.CLOSING) {
            return;
        }
        setState(State.OPENING, OPEN_TICKS);
        if (variant.flashFx) {
            triggerAnim("rift_fx", "flash_open");
        }
        if (level != null && !level.isClientSide()) {
            float pitch = switch (variant) {
                case ORANGE -> 0.8F;
                case END -> 0.9F;
                case DEEP -> 1.1F;
                case WHITE -> 1.25F;
                default -> 1.0F;
            };
            if (variant != RiftVariant.CORRUPTED) {
                level.playSound(null, worldPosition, ModSounds.RIFT_OPEN.get(), SoundSource.BLOCKS, 1.4F, pitch);
            }
            if (variant == RiftVariant.WHITE) {
                level.playSound(null, worldPosition, SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.BLOCKS, 1.2F, 1.6F);
            }
        }
    }

    /** Closes the rift this many ticks from now, unless it was already going to close sooner. */
    public void closeWithin(int ticks) {
        if (level != null) {
            long at = level.getGameTime() + Math.max(1, ticks);
            if (closeAt == 0L || at < closeAt) {
                closeAt = at;
                autoCloseIn = 0;
                setChanged();
            }
        }
    }

    public void close() {
        if (state != State.OPEN && state != State.OPENING) {
            return;
        }
        setState(State.CLOSING, CLOSE_TICKS);
        if (variant.flashFx) {
            triggerAnim("rift_fx", "flash_close");
        }
        if (level != null && !level.isClientSide()) {
            level.playSound(null, worldPosition, ModSounds.RIFT_CLOSE.get(), SoundSource.BLOCKS, 1.4F, 1.0F);
        }
    }

    private void setState(State newState, int ticks) {
        state = newState;
        timer = ticks;
        sync();
    }

    private void sync() {
        setChanged();
        if (level != null) {
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_ALL);
        }
    }

    // ---- Server tick ----

    public static void serverTick(Level level, BlockPos pos, BlockState blockState, RiftBlockEntity rift) {
        // count down the opening/closing animation
        if (rift.timer > 0 && --rift.timer == 0) {
            if (rift.state == State.OPENING) {
                rift.setState(State.OPEN, 0);
            } else if (rift.state == State.CLOSING) {
                rift.setState(State.CLOSED, 0);
                if (rift.removeOnClose) {
                    level.removeBlock(pos, false);
                    return;
                }
            }
        }

        // turn "close after N ticks" into a fixed clock time, then close when it arrives
        if (rift.autoCloseIn > 0 && rift.closeAt == 0L) {
            rift.closeAt = level.getGameTime() + rift.autoCloseIn;
            rift.autoCloseIn = 0;
            rift.setChanged();
        }
        if (rift.closeAt > 0L && level.getGameTime() >= rift.closeAt && (rift.state == State.OPEN || rift.state == State.OPENING)) {
            rift.closeAt = 0L;
            rift.close();
        }

        if (rift.fxCooldown > 0) {
            rift.fxCooldown--;
        }
        if (rift.state == State.OPEN) {
            rift.detectCrossing(level, pos, blockState);
            if (rift.variant == RiftVariant.CORRUPTED && level.getGameTime() % 90L == 0L) {
                level.playSound(null, pos, SoundEvents.SCULK_CLICKING, SoundSource.BLOCKS, 0.7F, 0.6F);
            }
        }
        rift.tickTeleports(level);
    }

    /**
     * Is a point inside the rift's glowing sheet? Both numbers are measured from the block:
     * "sideways" along the sheet, "up" from the floor. The sheet is wide near the ground and narrow at the top.
     */
    public static boolean insideSheet(double sideways, double up) {
        double s = Math.abs(sideways);
        if (up > -0.6 && up < 1.8) {
            return s <= 2.0;
        }
        return s <= 0.95 && up > -1.3 && up < 3.1;
    }

    // Watches players near the rift. Stepping from the front to the back (or the other way) through
    // the sheet counts as a crossing. Walking in from the front starts the pull.
    private void detectCrossing(Level level, BlockPos pos, BlockState blockState) {
        Direction facing = blockState.getValue(RiftBlock.FACING);
        double centerX = pos.getX() + 0.5;
        double centerZ = pos.getZ() + 0.5;
        Set<UUID> nearby = new HashSet<>();

        for (Player player : level.getEntitiesOfClass(Player.class, new AABB(pos).inflate(3.5, 2.0, 3.5))) {
            nearby.add(player.getUUID());
            double dx = player.getX() - centerX;
            double dz = player.getZ() - centerZ;
            double depth = dx * facing.getStepX() + dz * facing.getStepZ();     // in front of (+) or behind (-) the sheet
            double sideways = Math.abs(dx * facing.getStepZ() - dz * facing.getStepX());
            double up = player.getY() - pos.getY();
            boolean inFront = depth > 0.0;

            Boolean wasInFront = frontSide.put(player.getUUID(), inFront);
            boolean crossed = wasInFront != null && wasInFront != inFront && insideSheet(sideways, up) && Math.abs(depth) <= 0.9;
            if (!crossed) {
                continue;
            }
            boolean enteringFromFront = wasInFront;
            if (enteringFromFront && player instanceof ServerPlayer serverPlayer
                    && pendingTeleports.putIfAbsent(serverPlayer.getUUID(), TELEPORT_DELAY) == null) {
                RiftWarp.begin(serverPlayer, variant);
            }
            if (variant.flashFx && fxCooldown == 0) {
                triggerAnim("rift_fx", enteringFromFront ? "enter" : "exit");
                fxCooldown = 20;
            }
        }
        frontSide.keySet().retainAll(nearby); // forget players who walked away
    }

    // Counts down every pending teleport and carries it out when it reaches zero.
    private void tickTeleports(Level level) {
        Iterator<Map.Entry<UUID, Integer>> it = pendingTeleports.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<UUID, Integer> entry = it.next();
            int left = entry.getValue() - 1;
            if (left > 0) {
                entry.setValue(left);
                continue;
            }
            it.remove();
            if (level.getPlayerByUUID(entry.getKey()) instanceof ServerPlayer player) {
                if (state == State.OPEN) {
                    ServerLevel from = player.serverLevel();
                    Vec3 leftAt = player.position();
                    variant.onEnter(player, this);
                    RiftWarp.arrive(player, variant, from, leftAt);
                } else {
                    RiftWarp.cancel(player); // the rift closed while they were being pulled
                }
            }
        }
    }

    // ---- Client tick: particles ----

    public static void clientTick(Level level, BlockPos pos, BlockState blockState, RiftBlockEntity rift) {
        if (rift.state != State.OPEN && rift.state != State.OPENING) {
            return;
        }
        RandomSource random = level.random;
        if (random.nextInt(2) != 0) {
            return;
        }
        Direction facing = blockState.getValue(RiftBlock.FACING);
        double along = (random.nextDouble() - 0.5) * 6.0;   // along the sheet
        double across = (random.nextDouble() - 0.5) * 1.2;  // a little in front of or behind it
        double x = pos.getX() + 0.5 + (facing.getAxis() == Direction.Axis.Z ? along : across);
        double z = pos.getZ() + 0.5 + (facing.getAxis() == Direction.Axis.Z ? across : along);
        double y = pos.getY() + 0.4 + random.nextDouble() * 2.8;
        level.addParticle(rift.variant.idleParticle(random), x, y, z,
                (random.nextDouble() - 0.5) * 0.01, 0.012 + random.nextDouble() * 0.015, (random.nextDouble() - 0.5) * 0.01);
    }

    // ---- Animation ----
    // "rift" plays the opening, idle and closing animations. "rift_fx" is a second layer on top
    // for the flashes when you walk in or out. Names come from rift.animation.json.
    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(new AnimationController<>(this, "rift", 0, event -> switch (getRiftState()) {
            case CLOSED -> event.setAndContinue(RawAnimation.begin().thenPlayAndHold("closed"));
            case OPENING -> event.setAndContinue(RawAnimation.begin().thenPlay(variant.anim("opening")).thenLoop(variant.anim("open_idle")));
            case OPEN -> event.setAndContinue(RawAnimation.begin().thenLoop(variant.anim("open_idle")));
            case CLOSING -> event.setAndContinue(RawAnimation.begin().thenPlayAndHold(variant.anim("closing")));
        }));
        controllers.add(new AnimationController<>(this, "rift_fx", 0, event -> event.setAndContinue(RawAnimation.begin().thenLoop("glow_off")))
                .triggerableAnim("enter", RawAnimation.begin().thenPlay("enter"))
                .triggerableAnim("exit", RawAnimation.begin().thenPlay("exit"))
                .triggerableAnim("flash_open", RawAnimation.begin().thenPlay("flash_open"))
                .triggerableAnim("flash_close", RawAnimation.begin().thenPlayAndHold("flash_close")));
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return cache;
    }

    // ---- Saving, and telling the client ----

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.putInt("RiftState", state.ordinal());
        tag.putInt("RiftTimer", timer);
        tag.putString("Variant", variant.getSerializedName());
        tag.putInt("AutoClose", autoCloseIn);
        tag.putLong("CloseAt", closeAt);
        tag.putBoolean("RemoveOnClose", removeOnClose);
        if (destination != null) {
            GlobalPos.CODEC.encodeStart(NbtOps.INSTANCE, destination).result().ifPresent(saved -> tag.put("Destination", saved));
        }
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        state = State.values()[tag.getInt("RiftState")];
        timer = tag.getInt("RiftTimer");
        variant = RiftVariant.byName(tag.getString("Variant"));
        autoCloseIn = tag.getInt("AutoClose");
        closeAt = tag.getLong("CloseAt");
        removeOnClose = tag.getBoolean("RemoveOnClose");
        Tag saved = tag.get("Destination");
        destination = saved == null ? null : GlobalPos.CODEC.parse(NbtOps.INSTANCE, saved).result().orElse(null);
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        CompoundTag tag = new CompoundTag();
        saveAdditional(tag, registries);
        return tag;
    }

    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }
}