package com.example.mistvalerpg.prologue;

import java.util.List;

import com.example.mistvalerpg.ModAttachments;
import com.example.mistvalerpg.cutscene.Cutscene;
import com.example.mistvalerpg.cutscene.Later;
import com.example.mistvalerpg.dialogue.DialogueManager;
import com.example.mistvalerpg.dialogue.DialogueScenes;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.EquipmentSlot;
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
        BrannochEntity brannoch = com.example.mistvalerpg.ModEntities.BRANNOCH.get().create(sea);
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