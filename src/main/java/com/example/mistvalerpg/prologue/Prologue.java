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
import com.example.mistvalerpg.spirit.SpiritEntity;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.AABB;
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
    /** The deck is quiet, the Kraken is up and the player is fighting it. */
    public static final String KRAKEN = "kraken";
    /** The player has been beaten: the screen is black and the ship is going under. */
    public static final String KNOCKOUT = "knockout";
    /** The player wakes on the beach and meets the Spirit. */
    public static final String BEACH = "beach";

    /** How long the player may fight the Kraken: five minutes. After that the Kraken wins, whatever happened. */
    private static final int KRAKEN_MAX_TICKS = 6000;

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
        if (elapsed % 30 < 10 && player.getHealth() < 12.0F) {
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

    // The crew is down. The deck is quiet, the planks tremble, and the Kraken's spawner is put back: the Kraken rises.
    public static void crewDown(ServerPlayer player, ServerLevel sea) {
        setStage(player, KRAKEN);
        player.setHealth(Math.max(player.getHealth(), 14.0F));
        player.removeEffect(MobEffects.MOVEMENT_SLOWDOWN);
        Cutscene.subtitle(player, Component.translatable("scene.mistvale_rpg.deck_silent"), 10, 60, 15);
        heartbeat(player, 6);
        PrologueShip.armSpawner(sea, PrologueSea.slot(player));

        // The five minutes of the fight are counted from the end of the Kraken's rising, not from its start.
        long begun = sea.getGameTime() + KRAKEN_INTRO_TICKS;
        Later.afterPlayer(player.server, player, 20, () -> watchKraken(player, begun));

        // The film of the Kraken rising starts as soon as the Kraken is there to be filmed.
        krakenFilm(player, sea, PrologueSea.slot(player), 0);
    }

    /** How long Bosses'Rise's Kraken takes to rise, in ticks. During this time it cannot be hurt and it hurts nobody. */
    private static final int KRAKEN_INTRO_TICKS = 490;

    // Waits (looking every four ticks, for four seconds at most) until the rising Kraken exists, so the camera knows where to look.
    private static void krakenFilm(ServerPlayer player, ServerLevel sea, int slot, int waited) {
        if (!isInPrologue(player) || !stage(player).equals(KRAKEN)) {
            return;
        }
        Vec3 target = findKraken(sea, slot);
        if (target == null && waited < 80) {
            Later.afterPlayer(player.server, player, 4, () -> krakenFilm(player, sea, slot, waited + 4));
            return;
        }
        if (target == null) {
            // Not found (Bosses'Rise changed?): look at the water in front of the bow, where the Kraken is meant to come from.
            target = Vec3.atBottomCenterOf(PrologueShip.spawnerPos(slot)).add(0.0, -3.0, -20.0);
        }
        playKrakenFilm(player, target, KRAKEN_INTRO_TICKS - waited - 15);
    }

    // The Kraken that is rising (or, if it has risen already, the Kraken itself): where it is.
    @Nullable
    private static Vec3 findKraken(ServerLevel sea, int slot) {
        Vec3 found = null;
        for (Entity entity : sea.getEntities((Entity) null, PrologueShip.bounds(slot).inflate(60.0), e -> true)) {
            ResourceLocation id = BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType());
            if (id.getNamespace().equals(PrologueShip.SHIP_MOD) && id.getPath().equals("cinematic_kraken")) {
                return entity.position();
            }
            if (id.getNamespace().equals(PrologueShip.SHIP_MOD) && id.getPath().equals("kraken")) {
                found = entity.position();
            }
        }
        return found;
    }

    /**
     * Our own film of the Kraken rising, in four shots over "ticks" ticks, with the film bars in. The camera is ours, so it
     * always works. "target" is the foot of the Kraken; every shot looks at a point above it that climbs as the Kraken rises.
     *
     * 1. From the player's own eyes: a slow push towards the water while the look climbs.
     * 2. A sweep across the deck at rail height, from the player's right to their left, past the player.
     * 3. A high arc over the water, round the Kraken, from the right to the left.
     * 4. Back over the player's shoulder, ending where their eyes are, so the cut to the fight is not a jump.
     */
    private static void playKrakenFilm(ServerPlayer player, Vec3 target, int ticks) {
        Vec3 eye = player.getEyePosition();
        Vec3 flat = new Vec3(target.x - eye.x, 0.0, target.z - eye.z);
        double distance = flat.length();
        Vec3 dir = distance < 1.0 ? new Vec3(0.0, 0.0, -1.0) : flat.scale(1.0 / distance);   // from the player towards the Kraken
        Vec3 side = new Vec3(-dir.z, 0.0, dir.x);                                           // to the player's right
        Vec3 base = player.position();
        int first = ticks * 20 / 100;
        int second = ticks * 25 / 100;
        int third = ticks * 30 / 100;
        int fourth = ticks - first - second - third;

        Cutscene.bars(player, true);
        // 1
        Cutscene.shot(player, eye, eye.add(dir.scale(2.5)).add(0.0, 0.3, 0.0), target.add(0.0, 3.0, 0.0), target.add(0.0, 7.0, 0.0), first, true, false);
        // 2
        Cutscene.shot(player, base.add(side.scale(6.0)).add(dir.scale(2.0)).add(0.0, 2.6, 0.0),
                base.add(side.scale(-6.0)).add(dir.scale(8.0)).add(0.0, 3.2, 0.0),
                target.add(0.0, 4.0, 0.0), target.add(0.0, 8.0, 0.0), second, true, true);
        // 3
        Vec3 mid = base.add(dir.scale(distance * 0.55));
        Cutscene.shot(player, mid.add(side.scale(16.0)).add(0.0, 10.0, 0.0), mid.add(side.scale(-16.0)).add(0.0, 16.0, 0.0),
                target.add(0.0, 8.0, 0.0), target.add(0.0, 10.0, 0.0), third, true, true);
        // 4
        Cutscene.shot(player, base.add(dir.scale(-4.0)).add(side.scale(2.0)).add(0.0, 5.0, 0.0), eye,
                target.add(0.0, 10.0, 0.0), target.add(0.0, 8.0, 0.0), fourth, true, true);

        Later.afterPlayer(player.server, player, ticks, () -> {
            Cutscene.endShots(player);
            Cutscene.bars(player, false);
        });
    }

    // Looks at the Kraken fight twice a second. The only thing it checks is the clock: after five minutes the Kraken wins.
    // (The other way the fight ends, the player being beaten, is decided in PrologueEvents.onHurt.)
    private static void watchKraken(ServerPlayer player, long begun) {
        ServerLevel sea = PrologueSea.level(player);
        if (sea == null || !stage(player).equals(KRAKEN)) {
            return;
        }
        if (sea.getGameTime() - begun > KRAKEN_MAX_TICKS) {
            knockout(player);
            return;
        }
        Later.afterPlayer(player.server, player, 10, () -> watchKraken(player, begun));
    }

    /**
     * The player is beaten. The screen goes black, the ship goes under (the player does not see it), and a few lines of
     * text tell what happened. Then the player wakes on the beach of the home island. Every part below is "do this a
     * little later", counted in ticks from the moment the player was beaten.
     */
    public static void knockout(ServerPlayer player) {
        if (!stage(player).equals(KRAKEN)) {
            return;   // only once
        }
        setStage(player, KNOCKOUT);
        MinecraftServer server = player.server;

        // 0.0 seconds: black, a heavy blow, one half heart. (If the film of the Kraken is still running, it ends here.)
        Cutscene.endShots(player);
        Cutscene.bars(player, false);
        Cutscene.fade(player, 25, 3600, 0, 0x000000);
        player.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 120, 4, false, false));
        player.addEffect(new MobEffectInstance(MobEffects.BLINDNESS, 60, 0, false, false));
        player.level().playSound(null, player.blockPosition(), SoundEvents.PLAYER_HURT, SoundSource.PLAYERS, 1.0F, 0.5F);
        player.level().playSound(null, player.blockPosition(), SoundEvents.WARDEN_SONIC_BOOM, SoundSource.HOSTILE, 0.7F, 0.5F);
        player.setHealth(1.0F);

        // 1.5 seconds: behind the black screen, the player is carried to the beach and everything of the fight is cleared away.
        // The player cannot move until the screen lifts (the slowness lasts that long), so they cannot wander off blind.
        Later.afterPlayer(server, player, 30, () -> {
            ServerLevel sea = PrologueSea.level(player);
            if (sea != null) {
                discardFightEntities(sea, PrologueSea.slot(player));
            }
            player.removeAllEffects();
            player.getInventory().clearContent();
            player.setHealth(player.getMaxHealth());
            PrologueSea.toBeach(player);
            PrologueTime.set(player, PrologueTime.MORNING);   // behind the black screen it becomes morning
            player.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 490, 6, false, false));
        });

        // The sinking, over the black screen: four lines type themselves in, one after the other, each with the sounds of the sea
        // around a drowning heartbeat. (A subtitle cannot be used: the black screen is drawn over subtitles. The title card is drawn over
        // the black, so the lines use it, with no big line.) Each line: typing, then it stays, then it fades; the next one begins after.
        sink(server, player, 20, SoundEvents.AMBIENT_UNDERWATER_ENTER, 1.0F, 0.8F);
        sink(server, player, 24, SoundEvents.BUBBLE_COLUMN_WHIRLPOOL_INSIDE, 0.7F, 0.8F);
        // 2.5 s: "The Monster's power was too great..." Slow, heavy heartbeats under the water.
        koLine(server, player, 50, "scene.mistvale_rpg.ko_1", 40, 30);
        sink(server, player, 60, SoundEvents.WATER_AMBIENT, 0.9F, 0.6F);
        for (int i = 0; i < 4; i++) {
            sink(server, player, 56 + i * 28, SoundEvents.WARDEN_HEARTBEAT, 1.0F, 0.7F);
        }
        // 7 s: "...The ship went under." Splash, wood breaking and a rush of bubbles.
        koLine(server, player, 140, "scene.mistvale_rpg.ko_2", 30, 30);
        sink(server, player, 142, SoundEvents.PLAYER_SPLASH_HIGH_SPEED, 1.0F, 0.5F);
        sink(server, player, 150, SoundEvents.WOOD_BREAK, 0.9F, 0.4F);
        sink(server, player, 172, SoundEvents.WOOD_BREAK, 0.7F, 0.3F);
        for (int i = 0; i < 4; i++) {
            sink(server, player, 152 + i * 18, SoundEvents.BUBBLE_COLUMN_UPWARDS_INSIDE, 0.7F, 0.9F);
        }
        // 11 s: "But the fight is not over." The heartbeat comes back, stronger and quicker.
        koLine(server, player, 220, "scene.mistvale_rpg.ko_3", 36, 24);
        for (int i = 0; i < 5; i++) {
            sink(server, player, 226 + i * 22, SoundEvents.WARDEN_HEARTBEAT, 1.0F, 0.85F);
        }
        // 15 s: "Not yet." One last heavy beat, and a low cry from the deep.
        koLine(server, player, 300, "scene.mistvale_rpg.ko_4", 14, 40);
        sink(server, player, 300, SoundEvents.WARDEN_HEARTBEAT, 1.0F, 0.6F);
        sink(server, player, 304, SoundEvents.SOUL_ESCAPE.value(), 0.8F, 0.5F);
        sink(server, player, 306, SoundEvents.AMBIENT_UNDERWATER_EXIT, 0.8F, 0.7F);

        // 19.5 s: the card of the spawn film, still over the black: "Somewhere in..." types in, then MISTVALE fades in under it.
        // The sound is a shore now: slow waves washing up, one every three seconds, and the water going back.
        Later.afterPlayer(server, player, 390, () -> Cutscene.title(player, Component.translatable("scene.mistvale_rpg.somewhere_in"),
                Component.translatable("scene.mistvale_rpg.mistvale"), 20, 20, 40, 60));
        for (int i = 0; i < 6; i++) {
            int at = 380 + i * 60;
            sink(server, player, at, SoundEvents.WATER_AMBIENT, 1.0F, 0.5F);
            sink(server, player, at + 6, SoundEvents.PLAYER_SPLASH, 0.35F, 0.4F);
            sink(server, player, at + 20, SoundEvents.BOAT_PADDLE_WATER, 0.5F, 0.45F);
            sink(server, player, at + 38, SoundEvents.BOAT_PADDLE_WATER, 0.3F, 0.35F);
        }

        // 25 s: the black screen lifts, slowly, on the beach. The player is still dizzy for a moment.
        Later.afterPlayer(server, player, 500, () -> Cutscene.fade(player, 0, 0, 60, 0x000000));
        Later.afterPlayer(server, player, 505, () -> {
            player.removeEffect(MobEffects.MOVEMENT_SLOWDOWN);
            player.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 60, 1, false, false));
        });

        // 28 s: the player is awake. The beach is the next part of the prologue.
        Later.afterPlayer(server, player, 565, () -> {
            setStage(player, BEACH);
            player.addEffect(new MobEffectInstance(MobEffects.CONFUSION, 60, 0, false, false));
        });
    }

    // One line of the sinking, typed in over the black screen: "typing" ticks of typing, then it stays "hold" ticks and fades over 10.
    private static void koLine(MinecraftServer server, ServerPlayer player, int at, String key, int typing, int hold) {
        Later.afterPlayer(server, player, at, () -> Cutscene.title(player, Component.translatable(key), Component.empty(), typing, 0, 10, hold));
    }

    // A sound that only the player hears, "at" ticks from now, in the master channel: no distance, no echo, no slider can mute it.
    private static void sink(MinecraftServer server, ServerPlayer player, int at, SoundEvent sound, float volume, float pitch) {
        Later.afterPlayer(server, player, at, () -> player.playNotifySound(sound, SoundSource.MASTER, volume, pitch));
    }

    // After the fight: everything that is not a player is taken out of the ship's part of the sea (pirates, the Kraken, its tentacles).
    // The Spirit is the only creature that is allowed to stay.
    static void discardFightEntities(ServerLevel sea, int slot) {
        int x = PrologueShip.originX(slot);
        AABB box = new AABB(x - 140, 0, -140, x + 170, 160, 140);
        for (Entity entity : sea.getEntities((Entity) null, box, e -> !(e instanceof Player) && !(e instanceof SpiritEntity)
                && (e instanceof Mob || BuiltInRegistries.ENTITY_TYPE.getKey(e.getType()).getNamespace().equals(PrologueShip.SHIP_MOD)))) {
            entity.discard();
        }
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
