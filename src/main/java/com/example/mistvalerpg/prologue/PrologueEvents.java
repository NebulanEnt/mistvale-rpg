package com.example.mistvalerpg.prologue;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobSpawnType;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.living.LivingDropsEvent;
import net.neoforged.neoforge.event.entity.living.LivingExperienceDropEvent;
import net.neoforged.neoforge.event.entity.living.FinalizeSpawnEvent;
import net.minecraft.core.registries.BuiltInRegistries;

/**
 * The rules of the prologue sea. These are not things the story "does": they are things the game asks us about, which we
 * answer. "A player is about to be hurt: how much?", "something is about to die: may it?" and so on. Each method below is
 * registered in MistvaleRpg and called by the game at exactly that moment.
 */
public final class PrologueEvents {

    /** "A player is about to take damage." Decides how much, by the part of the prologue the player is in. */
    public static void onHurt(LivingDamageEvent.Pre event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || !Prologue.isInPrologue(player)) {
            return;
        }
        if (event.getSource().is(DamageTypeTags.BYPASSES_INVULNERABILITY)) {
            return;   // the void and the /kill command are always allowed
        }
        String stage = Prologue.stage(player);
        if (stage.equals(Prologue.TALK)) {
            event.setNewDamage(0.0F);   // nobody is fighting yet
        } else if (stage.equals(Prologue.CREW)) {
            // A pirate never takes more than two hearts at a time, and never takes the player below one and a half hearts.
            float health = player.getHealth() + player.getAbsorptionAmount();
            event.setNewDamage(Math.max(0.0F, Math.min(Math.min(event.getNewDamage(), 4.0F), health - 3.0F)));
        }
    }

    /** "Something is about to die." A player in the prologue is left at one half heart instead. */
    public static void onDeath(LivingDeathEvent event) {
        if (event.getEntity() instanceof ServerPlayer player && Prologue.isInPrologue(player)
                && !event.getSource().is(DamageTypeTags.BYPASSES_INVULNERABILITY)) {
            event.setCanceled(true);
            player.setHealth(1.0F);
        }
    }

    /** "A creature is about to enter the sea." Pirates that are not ours (the ones Bosses'Rise would add by itself) are turned away. */
    public static void onJoin(EntityJoinLevelEvent event) {
        if (event.getLevel().isClientSide() || !event.getLevel().dimension().equals(PrologueRegistry.SEA)) {
            return;
        }
        var id = BuiltInRegistries.ENTITY_TYPE.getKey(event.getEntity().getType());
        if (id.getNamespace().equals(PrologueShip.SHIP_MOD) && id.getPath().contains("pirate") && !event.getEntity().getTags().contains(Prologue.PIRATE_TAG)) {
            event.setCanceled(true);
        }
    }

    /** "A creature of the sea is about to be born by itself." Nothing is, in the sea: only what the prologue makes. */
    public static void onNaturalSpawn(FinalizeSpawnEvent event) {
        if (!event.getLevel().getLevel().dimension().equals(PrologueRegistry.SEA)) {
            return;
        }
        MobSpawnType type = event.getSpawnType();
        if (type == MobSpawnType.NATURAL || type == MobSpawnType.CHUNK_GENERATION || type == MobSpawnType.PATROL
                || type == MobSpawnType.REINFORCEMENT || type == MobSpawnType.JOCKEY) {
            event.setSpawnCancelled(true);
        }
    }

    /** Pirates drop nothing: no items, and no experience. They are part of a scene, not a farm. */
    public static void onPirateDrops(LivingDropsEvent event) {
        if (event.getEntity().getTags().contains(Prologue.PIRATE_TAG)) {
            event.setCanceled(true);
        }
    }

    public static void onPirateXp(LivingExperienceDropEvent event) {
        if (event.getEntity().getTags().contains(Prologue.PIRATE_TAG)) {
            event.setDroppedExperience(0);
        }
    }

    private PrologueEvents() {
    }
}
