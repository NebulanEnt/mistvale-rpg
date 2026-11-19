package com.example.mistvalerpg.spirit;

import org.jetbrains.annotations.Nullable;

import com.example.mistvalerpg.ModEntities;
import com.example.mistvalerpg.ModSounds;
import com.example.mistvalerpg.dialogue.DialogueManager;
import com.example.mistvalerpg.dialogue.DialogueScene;
import com.example.mistvalerpg.dialogue.DialogueScenes;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import software.bernie.geckolib.animatable.GeoEntity;
import software.bernie.geckolib.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.animation.AnimatableManager;
import software.bernie.geckolib.animation.AnimationController;
import software.bernie.geckolib.animation.RawAnimation;
import software.bernie.geckolib.util.GeckoLibUtil;

/**
 * The Spirit of Mistvale: a small floating flame-spirit who guides the player through the story.
 *
 * She hovers in place, always turns to face the nearest player, blinks and sneezes now and then,
 * and has a talking animation for every mood. Right-click her and she starts a conversation
 * (see the dialogue package), which also decides which mood she is in.
 */
public class SpiritEntity extends PathfinderMob implements GeoEntity {
    // The moods she can talk in. -1 means "not talking".
    public static final int MOOD_NONE = -1;
    public static final int MOOD_NEUTRAL = 0;
    public static final int MOOD_HAPPY = 1;
    public static final int MOOD_SAD = 2;
    public static final int MOOD_ANGRY = 3;
    public static final int MOOD_SHOCK = 4;

    // Data that the server shares with every client that can see her, so everyone sees the same mood.
    private static final EntityDataAccessor<Integer> TALK_MOOD = SynchedEntityData.defineId(SpiritEntity.class, EntityDataSerializers.INT);

    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);
    /** The spot she hovers around. */
    @Nullable
    private Vec3 anchor;
    /** If above zero she is a visitor and vanishes when this runs out. It only counts down while nobody is talking to her. */
    private int staysTicks = 0;
    private int vanishTicks = -1;
    private int talkTicks = 0;

    public SpiritEntity(EntityType<? extends SpiritEntity> type, Level level) {
        super(type, level);
        setNoGravity(true);
        setInvulnerable(true);
        noPhysics = true;
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(TALK_MOOD, MOOD_NONE);
    }

    public int getTalkMood() {
        return entityData.get(TALK_MOOD);
    }

    /** Makes her talk in the given mood for a number of ticks. */
    public void talk(int mood, int ticks) {
        entityData.set(TALK_MOOD, mood);
        talkTicks = ticks;
    }

    /** She appears out of nowhere, and fades away again after the given time. */
    public static SpiritEntity appearNear(ServerLevel level, BlockPos pos) {
        SpiritEntity spirit = ModEntities.SPIRIT.get().create(level, null, pos, MobSpawnType.EVENT, false, false);
        if (spirit == null) {
            return null;
        }
        spirit.staysTicks = 20 * 40; // 40 seconds
        level.addFreshEntity(spirit);
        return spirit;
    }

    /** The slow, soft fade-out. */
    public void vanish() {
        if (vanishTicks >= 0) {
            return;
        }
        talkTicks = 0;
        entityData.set(TALK_MOOD, MOOD_NONE);
        triggerAnim("main", "vanish");
        vanishTicks = 30;
        level().playSound(null, blockPosition(), ModSounds.SPIRIT_VANISH.get(), SoundSource.NEUTRAL, 1.0F, 1.0F);
    }

    @Override
    public void tick() {
        super.tick();
        if (level().isClientSide()) {
            return;
        }
        if (anchor == null) {
            anchor = position();
        }
        if (tickCount == 3) { // a moment after she exists, so the client has her ready to animate
            triggerAnim("main", "summon");
            level().playSound(null, blockPosition(), ModSounds.SPIRIT_APPEAR.get(), SoundSource.NEUTRAL, 1.0F, 1.0F);
        }
        if (talkTicks > 0 && --talkTicks == 0) {
            entityData.set(TALK_MOOD, MOOD_NONE);
        }
        // A visitor's clock only runs while nobody is talking to her. A conversation stops it, and when the conversation is over it runs on from where it stopped.
        if (staysTicks > 0 && !DialogueManager.isSpeaking(this) && --staysTicks == 0) {
            vanish();
        }
        if (vanishTicks > 0 && --vanishTicks == 0) {
            discard();
        }
    }

    // aiStep runs every tick too, and is where mobs usually move. Hers is just a gentle float.
    @Override
    public void aiStep() {
        super.aiStep();
        if (level().isClientSide() || vanishTicks >= 0 || anchor == null) {
            return;
        }

        // bob up and down around the anchor
        Vec3 target = anchor.add(0.0, 0.06 * Math.sin(tickCount * 0.07), 0.0);
        setDeltaMovement(target.subtract(position()).scale(0.25));

        // look at the nearest player within 8 blocks
        Player nearest = level().getNearestPlayer(this, 8.0);
        if (nearest != null) {
            float yaw = (float) (Math.toDegrees(Math.atan2(nearest.getZ() - getZ(), nearest.getX() - getX())) - 90.0);
            setYRot(yaw);
            yBodyRot = yaw;
            yHeadRot = yaw;
        }

        // every now and then, when she is idle, a little personality
        if (talkTicks == 0 && random.nextInt(400) == 0) {
            String[] idles = { "blink_wink", "blink_double", "idle_look", "sneeze" };
            triggerAnim("main", idles[random.nextInt(idles.length)]);
        }
    }

    // Right-click. A player who has never met her gets the first meeting; everyone else gets a short hello.
    @Override
    public InteractionResult mobInteract(Player player, InteractionHand hand) {
        if (vanishTicks >= 0) {
            return InteractionResult.PASS;
        }
        if (!level().isClientSide() && player instanceof ServerPlayer serverPlayer && !DialogueManager.isTalking(serverPlayer)) {
            DialogueScene scene = DialogueManager.hasSeen(serverPlayer, DialogueScenes.FIRST_MEETING) ? DialogueScenes.AGAIN : DialogueScenes.FIRST_MEETING;
            triggerAnim("main", "spin_up");
            level().playSound(null, blockPosition(), SoundEvents.PLAYER_LEVELUP, SoundSource.NEUTRAL, 0.5F, 1.5F);
            ((ServerLevel) level()).sendParticles(ParticleTypes.HAPPY_VILLAGER, getX(), getY() + 1.0, getZ(), 10, 0.5, 0.6, 0.5, 0.0);
            DialogueManager.start(serverPlayer, scene, this);
        }
        return InteractionResult.sidedSuccess(level().isClientSide());
    }

    // ---- Animation ----
    // Whenever nothing special is happening she plays "idle_fly"; while talking, the animation
    // for her current mood. The one-off animations (summon, vanish, blinks) play on top and then give way again.
    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(new AnimationController<>(this, "main", 4, event -> {
            String talking = switch (getTalkMood()) {
                case MOOD_NEUTRAL -> "talk_neutral";
                case MOOD_HAPPY -> "talk_happy";
                case MOOD_SAD -> "talk_sad";
                case MOOD_ANGRY -> "talk_angry";
                case MOOD_SHOCK -> "talk_shock";
                default -> null;
            };
            return event.setAndContinue(RawAnimation.begin().thenLoop(talking == null ? "idle_fly" : talking));
        })
                .triggerableAnim("summon", RawAnimation.begin().thenPlay("summon"))
                .triggerableAnim("vanish", RawAnimation.begin().thenPlayAndHold("vanish"))
                .triggerableAnim("spin_up", RawAnimation.begin().thenPlay("spin_up"))
                .triggerableAnim("sneeze", RawAnimation.begin().thenPlay("sneeze"))
                .triggerableAnim("blink_wink", RawAnimation.begin().thenPlay("blink_wink"))
                .triggerableAnim("blink_double", RawAnimation.begin().thenPlay("blink_double"))
                .triggerableAnim("idle_look", RawAnimation.begin().thenPlay("idle_look")));
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return cache;
    }

    // ---- She cannot be hurt, pushed, or lost ----

    @Override
    public boolean hurt(DamageSource source, float amount) {
        return source.is(DamageTypeTags.BYPASSES_INVULNERABILITY) && super.hurt(source, amount); // only /kill works
    }

    @Override
    public boolean isPushable() {
        return false;
    }

    @Override
    public boolean removeWhenFarAway(double distance) {
        return false;
    }

    // ---- Saving ----

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        if (anchor != null) {
            tag.putDouble("AnchorX", anchor.x);
            tag.putDouble("AnchorY", anchor.y);
            tag.putDouble("AnchorZ", anchor.z);
        }
        tag.putInt("StaysTicks", staysTicks);
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        anchor = tag.contains("AnchorX") ? new Vec3(tag.getDouble("AnchorX"), tag.getDouble("AnchorY"), tag.getDouble("AnchorZ")) : null;
        staysTicks = tag.getInt("StaysTicks");
    }
}
