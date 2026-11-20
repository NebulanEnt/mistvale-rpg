package com.example.mistvalerpg.prologue;

import java.util.Optional;

import com.example.mistvalerpg.dialogue.DialogueManager;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import software.bernie.geckolib.animatable.GeoEntity;
import software.bernie.geckolib.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.animation.AnimatableManager;
import software.bernie.geckolib.animation.AnimationController;
import software.bernie.geckolib.animation.PlayState;
import software.bernie.geckolib.animation.RawAnimation;
import software.bernie.geckolib.util.GeckoLibUtil;

/**
 * First Mate Brannoch: the pirate captain's right hand, who stands on the deck in front of the player and talks to them before
 * the fight. He cannot be hurt, does not walk, and only turns to look at whoever is near. When the fog comes, he is replaced by
 * the skeleton pirates (see Prologue).
 *
 * He is drawn from a Blockbench model and animated with GeckoLib: the files are geo/entity/brannoch.geo.json and
 * animations/entity/brannoch.animation.json. He stands like the skeleton captain, salutes with his saber when someone starts
 * talking to him, gestures while he talks, and looks around nervously when the mist comes in (see lookAround()).
 */
public class BrannochEntity extends PathfinderMob implements GeoEntity {
    // Data the server shares with every client that can see him: is he looking around at the mist right now?
    private static final EntityDataAccessor<Boolean> LOOKING_AROUND = SynchedEntityData.defineId(BrannochEntity.class, EntityDataSerializers.BOOLEAN);

    // The animations of the animation file, by name.
    private static final RawAnimation IDLE = RawAnimation.begin().thenLoop("animation.brannoch.idle");
    private static final RawAnimation WALK = RawAnimation.begin().thenLoop("animation.brannoch.walk");
    private static final RawAnimation LOOK_AROUND = RawAnimation.begin().thenPlayAndHold("animation.brannoch.look_around");
    private static final RawAnimation GREET = RawAnimation.begin().thenPlay("animation.brannoch.greet");
    private static final RawAnimation TALK = RawAnimation.begin().thenPlay("animation.brannoch.talk");
    private static final RawAnimation ATTACK = RawAnimation.begin().thenPlay("animation.brannoch.attack");

    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);
    // Counts down the ticks until the next gesture of his hand while he talks.
    private int gesture = 40;
    // True while the player is in a conversation and he has already saluted for it.
    private boolean greeted = false;

    public BrannochEntity(EntityType<? extends BrannochEntity> type, Level level) {
        super(type, level);
        setNoAi(true);                 // no wandering, no fighting: he only stands there
        setInvulnerable(true);
        setSilent(true);
        setPersistenceRequired();      // never despawns
        setCustomName(Component.translatable("entity.mistvale_rpg.brannoch"));
        setCustomNameVisible(false);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(LOOKING_AROUND, false);
    }

    public boolean isLookingAround() {
        return entityData.get(LOOKING_AROUND);
    }

    /** The mist rolls in: he stops watching the player and glances about, uneasy, for eight seconds (a long animation that ends trembling). */
    public void lookAround() {
        entityData.set(LOOKING_AROUND, true);
    }

    /** Puts the pirate saber in his hand (the saber of Bosses'Rise, or an iron sword if that mod does not have it). */
    public void equip() {
        Optional<ItemStack> saber = BuiltInRegistries.ITEM
                .getOptional(ResourceLocation.fromNamespaceAndPath(PrologueShip.SHIP_MOD, "pirate_saber"))
                .map(ItemStack::new);
        setItemSlot(EquipmentSlot.MAINHAND, saber.orElse(new ItemStack(Items.IRON_SWORD)));
        setDropChance(EquipmentSlot.MAINHAND, 0.0F);
    }

    @Override
    public void tick() {
        super.tick();
        if (level().isClientSide) {
            return;
        }
        // While the mist comes in he looks around by himself and ignores the player.
        if (isLookingAround()) {
            return;
        }
        // Turn the body, the head and the eyes to the nearest player, up to 24 blocks away.
        Player player = level().getNearestPlayer(this, 24.0);
        if (player == null) {
            return;
        }
        float target = (float) (Math.toDegrees(Math.atan2(player.getZ() - getZ(), player.getX() - getX())) - 90.0);
        // Turn at most 12 degrees a tick, the short way round (a raw angle would jump by 360 when the player passes behind him).
        float yaw = getYRot() + Mth.clamp(Mth.wrapDegrees(target - getYRot()), -12.0F, 12.0F);
        setYRot(yaw);
        yBodyRot = yaw;
        yHeadRot = yaw;
        double up = player.getEyeY() - getEyeY();
        double along = Math.hypot(player.getX() - getX(), player.getZ() - getZ());
        setXRot((float) -Math.toDegrees(Math.atan2(up, along)));
        // When the player starts a conversation he raises his saber in greeting; while it goes on he gestures now and then.
        if (player instanceof ServerPlayer talking && DialogueManager.isTalking(talking)) {
            if (!greeted) {
                greeted = true;
                triggerAnim("gesture", "greet");
                gesture = 100;                 // the salute takes three seconds; the first gesture comes after it
            } else if (--gesture <= 0) {
                triggerAnim("gesture", "talk");
                gesture = 60 + random.nextInt(60);
            }
        } else {
            greeted = false;
        }
    }

    // Anything that makes him swing (a fight, or another mod) plays the saber slash.
    @Override
    public void swing(InteractionHand hand, boolean updateSelf) {
        super.swing(hand, updateSelf);
        if (!level().isClientSide) {
            triggerAnim("gesture", "attack");
        }
    }

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        // "move" plays his standing, walking or looking-around loop; "gesture" plays the one-off ones on top of it.
        controllers.add(new AnimationController<>(this, "move", 6, state -> {
            if (isLookingAround()) {
                return state.setAndContinue(LOOK_AROUND);
            }
            return state.setAndContinue(state.isMoving() ? WALK : IDLE);
        }));
        controllers.add(new AnimationController<>(this, "gesture", 3, state -> PlayState.STOP)
                .triggerableAnim("greet", GREET)
                .triggerableAnim("talk", TALK)
                .triggerableAnim("attack", ATTACK));
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return cache;
    }

    // Nothing hurts him, except things that get through everything (the /kill command and the void).
    @Override
    public boolean hurt(DamageSource source, float amount) {
        return source.is(DamageTypeTags.BYPASSES_INVULNERABILITY) && super.hurt(source, amount);
    }

    @Override
    public boolean isPushable() {
        return false;
    }

    @Override
    public boolean removeWhenFarAway(double distance) {
        return false;
    }

    // He belongs to one scene. If the world is saved while he stands there, he is not saved with it.
    @Override
    public boolean shouldBeSaved() {
        return false;
    }
}
