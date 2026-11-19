package com.example.mistvalerpg.prologue;

import java.util.Optional;

import com.example.mistvalerpg.dialogue.DialogueManager;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;

/**
 * First Mate Brannoch: the pirate captain's right hand, who stands on the deck in front of the player and talks to them before
 * the fight. He cannot be hurt, does not walk, and only turns to look at whoever is near. When the fog comes, he is replaced by
 * the skeleton pirates (see Prologue).
 */
public class BrannochEntity extends PathfinderMob {
    // Counts down the ticks until the next wave of his hand while he talks.
    private int gesture = 40;

    public BrannochEntity(EntityType<? extends BrannochEntity> type, Level level) {
        super(type, level);
        setNoAi(true);                 // no wandering, no fighting: he only stands there
        setInvulnerable(true);
        setSilent(true);
        setPersistenceRequired();      // never despawns
        setCustomName(Component.translatable("entity.mistvale_rpg.brannoch"));
        setCustomNameVisible(false);
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
        // Turn the body, the head and the eyes to the nearest player, up to 24 blocks away.
        Player player = level().getNearestPlayer(this, 24.0);
        if (player == null) {
            return;
        }
        float yaw = (float) (Math.toDegrees(Math.atan2(player.getZ() - getZ(), player.getX() - getX())) - 90.0);
        setYRot(yaw);
        yBodyRot = yaw;
        yHeadRot = yaw;
        double up = player.getEyeY() - getEyeY();
        double along = Math.hypot(player.getX() - getX(), player.getZ() - getZ());
        setXRot((float) -Math.toDegrees(Math.atan2(up, along)));
        // While the player is in a conversation, he waves his saber hand now and then.
        if (player instanceof ServerPlayer talking && DialogueManager.isTalking(talking) && --gesture <= 0) {
            swing(InteractionHand.MAIN_HAND);
            gesture = 45 + random.nextInt(60);
        }
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