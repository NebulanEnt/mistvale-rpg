package com.example.mistvalerpg.prologue;

import java.util.List;
import java.util.Optional;

import org.jetbrains.annotations.Nullable;

import com.example.mistvalerpg.MistvaleRpg;
import com.example.mistvalerpg.ModAttachments;
import com.example.mistvalerpg.ModEntities;
import com.example.mistvalerpg.ModSounds;
import com.example.mistvalerpg.cutscene.Cutscene;
import com.example.mistvalerpg.cutscene.Later;
import com.example.mistvalerpg.dialogue.DialogueManager;
import com.example.mistvalerpg.dialogue.DialogueScenes;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;

/**
 * The prologue: the first minutes of a new player. It begins on the Kraken's ship, in a private sea, with a talk with First
 * Mate Brannoch. This class is the director: it knows which part the player is in and starts the next one.
 *
 * Where the player is in the story is remembered in the attachment PROLOGUE_STAGE, one of the words below. Because the word
 * is saved with the player, a player who leaves the game in the middle of the prologue is still in the same part when
 * they come back.
 */
public final class Prologue {
    /** Not started yet (or the prologue was never meant for this player). */
    public static final String NONE = "";
    /** On the deck, talking to Brannoch. Nothing can hurt the player. */
    public static final String TALK = "talk";
    /** The skeleton pirates are fighting the player. */
    public static final String CREW = "crew";
    /** The deck is quiet and the sea is about to answer. */
    public static final String KRAKEN = "kraken";

    /** The tag every pirate of the prologue carries. Pirates without the tag are not ours and are not allowed into the sea. */
    public static final String PIRATE_TAG = "mistvale_rpg_prologue_pirate";

    /** The word for the player's part of the prologue. */
    public static String stage(ServerPlayer player) {
        return player.getData(ModAttachments.PROLOGUE_STAGE);
    }

    public static void setStage(ServerPlayer player, String stage) {
        player.setData(ModAttachments.PROLOGUE_STAGE, stage);
    }

    /** True while the player is somewhere in the prologue, from the first talk on deck to the end. */
    public static boolean isInPrologue(ServerPlayer player) {
        return PrologueSea.isInSea(player) && !stage(player).isEmpty();
    }

    /**
     * Starts the prologue from the very beginning: the ship, the gear, Brannoch, and one second of black that fades away.
     * It can be called again at any time to start over (the ship is simply placed again).
     */
    public static boolean begin(ServerPlayer player) {
        ServerLevel sea = PrologueSea.level(player);
        if (sea == null) {
            return false;
        }
        int slot = PrologueSea.slot(player);
        if (!PrologueShip.placeShip(sea, slot)) {
            return false;
        }
        setStage(player, TALK);
        gear(player);

        // The player: on the deck, looking north towards the bow, healthy and with a roof over their head.
        BlockPos deck = PrologueShip.deckSpawn(slot);
        player.removeAllEffects();
        player.teleportTo(sea, deck.getX() + 0.5, deck.getY(), deck.getZ() + 0.5, 180.0F, 4.0F);
        player.setHealth(player.getMaxHealth());
        player.getFoodData().setFoodLevel(20);
        player.resetFallDistance();
        // If they die, they wake up here again, not in the world's spawn.
        player.setRespawnPosition(PrologueRegistry.SEA, deck, 180.0F, true, false);
        // On the ship it is almost night. The sea has no fixed hour of its own: the hour is told to this player (see PrologueTime).
        PrologueTime.set(player, PrologueTime.NIGHT);

        spawnBrannoch(sea, slot);
        // One second of black, which fades away over two and a half seconds. The talk starts when it is gone.
        Cutscene.fade(player, 0, 20, 50, 0x000000);
        Later.afterPlayer(player.server, player, 100, () -> startIntro(player));
        return true;
    }

    // Everything the player carries at the start: netherite armour, a sword, a bow, a shield and food.
    private static void gear(ServerPlayer player) {
        player.getInventory().clearContent();
        player.setItemSlot(EquipmentSlot.HEAD, new ItemStack(Items.NETHERITE_HELMET));
        player.setItemSlot(EquipmentSlot.CHEST, new ItemStack(Items.NETHERITE_CHESTPLATE));
        player.setItemSlot(EquipmentSlot.LEGS, new ItemStack(Items.NETHERITE_LEGGINGS));
        player.setItemSlot(EquipmentSlot.FEET, new ItemStack(Items.NETHERITE_BOOTS));
        player.getInventory().setItem(0, new ItemStack(Items.NETHERITE_SWORD));
        player.getInventory().setItem(1, new ItemStack(Items.BOW));
        player.getInventory().setItem(2, new ItemStack(Items.ARROW, 32));
        player.getInventory().setItem(3, new ItemStack(Items.GOLDEN_APPLE, 4));
        player.getInventory().setItem(4, new ItemStack(Items.COOKED_BEEF, 16));
        player.setItemSlot(EquipmentSlot.OFFHAND, new ItemStack(Items.SHIELD));
        player.getInventory().selected = 0;
    }

    /** Where Brannoch stands: a little to the side and two and a half blocks in front of the player, towards the bow. */
    static Vec3 brannochSpot(int slot) {
        BlockPos deck = PrologueShip.deckSpawn(slot);
        return new Vec3(deck.getX() + 0.5 + 0.8, deck.getY(), deck.getZ() - 2.5);
    }

    private static void spawnBrannoch(ServerLevel sea, int slot) {
        // If an earlier try left a Brannoch standing there, he goes first.
        for (BrannochEntity old : brannochs(sea, slot)) {
            old.discard();
        }
        BrannochEntity brannoch = ModEntities.BRANNOCH.get().create(sea);
        if (brannoch != null) {
            Vec3 spot = brannochSpot(slot);
            brannoch.moveTo(spot.x, spot.y, spot.z, 0.0F, 0.0F);
            brannoch.equip();
            sea.addFreshEntity(brannoch);
        }
    }

    static List<BrannochEntity> brannochs(ServerLevel sea, int slot) {
        return sea.getEntitiesOfClass(BrannochEntity.class, PrologueShip.bounds(slot).inflate(10.0));
    }

    /**
     * The talk is over, the fight begins. This is what the last box of Brannoch's talk asks for. The box asks for it while it
     * is still open, so we wait until the player has closed it: a fight and a conversation must not run at the same time.
     */
    public static void startFightAfterTalk(ServerPlayer player) {
        if (player.hasDisconnected()) {
            return;
        }
        if (DialogueManager.isTalking(player)) {
            Later.after(player.server, 10, () -> startFightAfterTalk(player));
            return;
        }
        startFight(player);
    }

    /**
     * The fog comes. Brannoch is taken by the light: for two and a half seconds the soul-fog gathers round him and the heartbeat
     * quickens, then he turns into the skeleton pirate captain where he stood, with a skeleton pirate on each side of him and two
     * crossbow pirates up on the helm deck. Then the Captain speaks and the fight begins. See transformStep and finishTransformation.
     */
    public static void startFight(ServerPlayer player) {
        ServerLevel sea = PrologueSea.level(player);
        if (sea == null || !isInPrologue(player)) {
            return;
        }
        int slot = PrologueSea.slot(player);
        setStage(player, CREW);
        BrannochEntity brannoch = null;
        for (BrannochEntity found : brannochs(sea, slot)) {
            if (brannoch == null) {
                brannoch = found;
            } else {
                found.discard();
            }
        }
        Vec3 spot = brannochSpot(slot);
        if (brannoch != null) {
            brannoch.lookAround();   // he looks about nervously as the mist comes in
        }
        // The light dims and pulses for a moment, like the world holding its breath.
        player.addEffect(new MobEffectInstance(MobEffects.DARKNESS, TRANSFORM_TICKS + 30, 0, false, false));
        sea.playSound(null, BlockPos.containing(spot), SoundEvents.SOUL_ESCAPE.value(), SoundSource.HOSTILE, 1.4F, 0.5F);
        sea.playSound(null, BlockPos.containing(spot), SoundEvents.ZOMBIE_VILLAGER_CURE, SoundSource.HOSTILE, 0.8F, 0.5F);
        transformStep(player, sea, slot, brannoch, spot, 0);
    }

    /** How long Brannoch takes to turn, in ticks. */
    private static final int TRANSFORM_TICKS = 50;

    // Every two ticks until the moment of change: soul-fog spirals in and up round Brannoch, thickening towards him, with
    // a heartbeat that gets faster. Then finishTransformation.
    private static void transformStep(ServerPlayer player, ServerLevel sea, int slot, @Nullable BrannochEntity brannoch, Vec3 spot, int tick) {
        if (!isInPrologue(player) || !stage(player).equals(CREW)) {
            return;
        }
        if (tick >= TRANSFORM_TICKS) {
            finishTransformation(player, sea, slot, brannoch, spot);
            return;
        }
        double progress = (double) tick / TRANSFORM_TICKS;
        double radius = 2.6 * (1.0 - progress) + 0.35;
        for (int arm = 0; arm < 3; arm++) {
            double angle = tick * 0.32 + arm * (Math.PI * 2.0 / 3.0);
            double px = spot.x + Math.cos(angle) * radius;
            double pz = spot.z + Math.sin(angle) * radius;
            double py = spot.y + 0.2 + progress * 1.9;
            // a soul drifting inwards, a spark of soul fire riding with it, and a puff of fog low on the deck
            sea.sendParticles(ParticleTypes.SOUL, px, py, pz, 0, (spot.x - px) * 0.04, 0.05, (spot.z - pz) * 0.04, 1.0);
            sea.sendParticles(ParticleTypes.SOUL_FIRE_FLAME, px, py - 0.2, pz, 0, (spot.x - px) * 0.05, 0.03, (spot.z - pz) * 0.05, 1.0);
            if (tick % 4 == 0) {
                sea.sendParticles(ParticleTypes.CLOUD, px, spot.y + 0.1, pz, 0, (spot.x - px) * 0.03, 0.0, (spot.z - pz) * 0.03, 1.0);
            }
        }
        // sparks of pale light that leave him as the soul is pulled out
        sea.sendParticles(ParticleTypes.SCULK_SOUL, spot.x, spot.y + 1.0 + progress * 0.6, spot.z, 1 + tick / 12, 0.3, 0.5, 0.3, 0.02);
        if (tick > 20) {
            sea.sendParticles(ParticleTypes.REVERSE_PORTAL, spot.x, spot.y + 1.0, spot.z, 3, 0.35, 0.7, 0.35, 0.1);
        }
        // the heartbeat, played to the player alone, quickening and rising
        if (tick == 0 || tick == 18 || tick == 32 || tick == 42 || tick == 48) {
            player.playNotifySound(SoundEvents.WARDEN_HEARTBEAT, SoundSource.MASTER, 1.0F, 0.7F + (float) progress * 0.5F);
        }
        if (tick == 24) {
            sea.playSound(null, BlockPos.containing(spot), ModSounds.SKELETON_VOICE.get(), SoundSource.HOSTILE, 0.8F, 0.6F);
        }
        Later.afterPlayer(player.server, player, 2, () -> transformStep(player, sea, slot, brannoch, spot, tick + 2));
    }

    // The moment of change: the soul-fog bursts outwards, Brannoch is gone and the skeleton pirates stand where the fog was.
    // All of them stand perfectly still until the Captain has spoken his last words.
    private static void finishTransformation(ServerPlayer player, ServerLevel sea, int slot, @Nullable BrannochEntity brannoch, Vec3 spot) {
        if (brannoch != null) {
            brannoch.discard();
        }
        for (BrannochEntity left : brannochs(sea, slot)) {
            left.discard();
        }

        Mob captain = spawnPirate(sea, "pirate_captain", spot, player);
        spawnPirate(sea, "pirate_rook", spot.add(-3.6, 0.0, -2.4), player);
        spawnPirate(sea, "pirate_rook", spot.add(3.6, 0.0, -2.4), player);
        spawnPirate(sea, "crossbow_pirate", helmSpot(slot, -4.0), player);
        spawnPirate(sea, "crossbow_pirate", helmSpot(slot, 4.0), player);

        // The burst: souls and soul fire thrown outwards, a ring of fog along the deck, a pull of "reverse portal" light.
        sea.sendParticles(ParticleTypes.SOUL_FIRE_FLAME, spot.x, spot.y + 1.0, spot.z, 70, 0.2, 0.5, 0.2, 0.16);
        sea.sendParticles(ParticleTypes.SOUL, spot.x, spot.y + 1.2, spot.z, 40, 0.3, 0.6, 0.3, 0.12);
        sea.sendParticles(ParticleTypes.SCULK_SOUL, spot.x, spot.y + 1.0, spot.z, 30, 0.5, 0.7, 0.5, 0.08);
        sea.sendParticles(ParticleTypes.REVERSE_PORTAL, spot.x, spot.y + 1.0, spot.z, 60, 0.3, 0.8, 0.3, 0.5);
        sea.sendParticles(ParticleTypes.LARGE_SMOKE, spot.x, spot.y + 0.6, spot.z, 20, 0.8, 0.4, 0.8, 0.02);
        for (int i = 0; i < 4; i++) {
            sea.sendParticles(ParticleTypes.CLOUD, spot.x + (i - 1.5) * 3.0, spot.y + 0.3, spot.z - 2.0, 24, 2.4, 0.5, 2.4, 0.01);
        }
        sea.playSound(null, BlockPos.containing(spot), SoundEvents.SOUL_ESCAPE.value(), SoundSource.HOSTILE, 1.6F, 0.4F);
        sea.playSound(null, BlockPos.containing(spot), SoundEvents.SKELETON_AMBIENT, SoundSource.HOSTILE, 1.4F, 0.5F);
        sea.playSound(null, BlockPos.containing(spot), SoundEvents.ZOMBIE_VILLAGER_CURE, SoundSource.HOSTILE, 1.0F, 0.4F);
        sea.playSound(null, BlockPos.containing(spot), SoundEvents.WITHER_SPAWN, SoundSource.HOSTILE, 0.35F, 0.5F);
        player.playNotifySound(SoundEvents.WARDEN_HEARTBEAT, SoundSource.MASTER, 1.0F, 0.6F);

        // A second and a half for the fog to settle, then the Captain speaks.
        Later.afterPlayer(player.server, player, 30, () -> roar(player, captain));
    }

    // The Captain's last words: a roar with the last of the man in it. The speaker is the Captain himself, so the camera finds him.
    private static void roar(ServerPlayer player, @Nullable Mob captain) {
        if (!isInPrologue(player) || !stage(player).equals(CREW)) {
            return;
        }
        DialogueManager.start(player, DialogueScenes.PROLOGUE_ROAR, captain);
        awaitRoar(player);
    }

    // Every half second: is the box still open? When it is closed, the crew comes alive and the fight begins.
    private static void awaitRoar(ServerPlayer player) {
        if (!isInPrologue(player) || !stage(player).equals(CREW)) {
            return;
        }
        if (DialogueManager.isTalking(player)) {
            Later.afterPlayer(player.server, player, 10, () -> awaitRoar(player));
            return;
        }
        ServerLevel sea = PrologueSea.level(player);
        for (Mob pirate : pirates(sea, PrologueSea.slot(player))) {
            pirate.setNoAi(false);
        }
        long begun = sea.getGameTime();
        Later.afterPlayer(player.server, player, 10, () -> watchCrew(player, begun));
    }

    /**
     * A spot on the helm deck, the raised deck at the back of the ship where the steering wheel is. It is seven and a half
     * blocks behind the player's starting place and three blocks higher. "sideways" is how far it is from the ship's middle.
     */
    static Vec3 helmSpot(int slot, double sideways) {
        BlockPos deck = PrologueShip.deckSpawn(slot);
        return new Vec3(deck.getX() + 0.5 + sideways, deck.getY() + 3.0, deck.getZ() + 7.5);
    }

    // One skeleton pirate, made from Bosses'Rise's own mob. "id" is "pirate_captain", "pirate_rook" or "crossbow_pirate".
    // Returns the pirate (or null if Bosses'Rise does not have it). It stands still until the Captain has spoken: see awaitRoar.
    @Nullable
    private static Mob spawnPirate(ServerLevel sea, String id, Vec3 at, ServerPlayer player) {
        Optional<EntityType<?>> type = BuiltInRegistries.ENTITY_TYPE.getOptional(ResourceLocation.fromNamespaceAndPath(PrologueShip.SHIP_MOD, id));
        if (type.isEmpty() || !(type.get().create(sea) instanceof Mob pirate)) {
            MistvaleRpg.LOGGER.warn("The entity {}:{} was not found. Is Bosses'Rise installed?", PrologueShip.SHIP_MOD, id);
            return null;
        }
        pirate.addTag(PIRATE_TAG);
        // The sea has no fixed hour of its own, so the server may think it is day: the skeletons must not burn in it.
        pirate.addEffect(new MobEffectInstance(MobEffects.FIRE_RESISTANCE, MobEffectInstance.INFINITE_DURATION, 0, false, false));
        // Face the player.
        float yaw = (float) (Math.toDegrees(Math.atan2(player.getZ() - at.z, player.getX() - at.x)) - 90.0);
        pirate.moveTo(at.x, at.y, at.z, yaw, 0.0F);
        pirate.setYHeadRot(yaw);
        pirate.yBodyRot = yaw;
        pirate.finalizeSpawn(sea, sea.getCurrentDifficultyAt(pirate.blockPosition()), MobSpawnType.EVENT, null);
        pirate.setPersistenceRequired();
        pirate.setNoAi(true);
        sea.addFreshEntity(pirate);
        sea.sendParticles(ParticleTypes.CLOUD, at.x, at.y + 1.0, at.z, 26, 0.45, 0.8, 0.45, 0.02);
        sea.sendParticles(ParticleTypes.SOUL_FIRE_FLAME, at.x, at.y + 0.8, at.z, 10, 0.35, 0.6, 0.35, 0.03);
        return pirate;
    }

    /** The pirates of this player's ship that are still alive. */
    static List<Mob> pirates(ServerLevel sea, int slot) {
        return sea.getEntitiesOfClass(Mob.class, PrologueShip.bounds(slot).inflate(40.0), mob -> mob.isAlive() && mob.getTags().contains(PIRATE_TAG));
    }

    // Looks at the fight every half second: the pirates always have the player as their target, a player who is almost
    // beaten gets a little help, and when the crew is down (or the fight takes more than two minutes) the deck falls silent.
    private static void watchCrew(ServerPlayer player, long begun) {
        ServerLevel sea = PrologueSea.level(player);
        if (sea == null || !stage(player).equals(CREW)) {
            return;
        }
        long elapsed = sea.getGameTime() - begun;
        List<Mob> crew = pirates(sea, PrologueSea.slot(player));
        for (Mob pirate : crew) {
            if (pirate.getTarget() == null || !pirate.getTarget().isAlive()) {
                pirate.setTarget(player);
            }
        }
        // Below six hearts the player slowly heals, so the pirates can win a round but never the fight.
        if (elapsed % 30 == 0 && player.getHealth() < 12.0F) {
            player.heal(1.0F);
        }
        if (elapsed > 60 && crew.isEmpty() || elapsed > 2400) {
            for (Mob pirate : crew) {
                sea.sendParticles(ParticleTypes.CLOUD, pirate.getX(), pirate.getY() + 1.0, pirate.getZ(), 20, 0.4, 0.6, 0.4, 0.02);
                pirate.discard();
            }
            crewDown(player, sea);
            return;
        }
        Later.afterPlayer(player.server, player, 10, () -> watchCrew(player, begun));
    }

    // The crew is down. The deck is quiet. The next part (the Kraken) begins here.
    private static void crewDown(ServerPlayer player, ServerLevel sea) {
        setStage(player, KRAKEN);
        player.setHealth(Math.max(player.getHealth(), 14.0F));
        Cutscene.subtitle(player, Component.translatable("scene.mistvale_rpg.deck_silent"), 10, 60, 15);
        heartbeat(player, 6);
    }

    // A heartbeat that only the player hears: a beat, then the next one a second later, "beats" times. It is played
    // straight to the player in the master channel, so no distance, no echo and no "hostile creatures" slider can mute it.
    private static void heartbeat(ServerPlayer player, int beats) {
        if (beats <= 0 || player.hasDisconnected()) {
            return;
        }
        player.playNotifySound(SoundEvents.WARDEN_HEARTBEAT, SoundSource.MASTER, 1.0F, 0.8F);
        Later.afterPlayer(player.server, player, 22, () -> heartbeat(player, beats - 1));
    }

    // The talk with Brannoch. The camera finds him, because he is the speaker.
    private static void startIntro(ServerPlayer player) {
        if (!isInPrologue(player) || !stage(player).equals(TALK)) {
            return;   // the player left or the prologue went on without this talk
        }
        ServerLevel sea = PrologueSea.level(player);
        List<BrannochEntity> brannoch = brannochs(sea, PrologueSea.slot(player));
        DialogueManager.start(player, DialogueScenes.PROLOGUE_INTRO, brannoch.isEmpty() ? null : brannoch.get(0));
    }

    private Prologue() {
    }
}
