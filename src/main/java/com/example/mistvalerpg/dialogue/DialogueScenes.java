package com.example.mistvalerpg.dialogue;

import java.util.List;
import java.util.Map;

import org.jetbrains.annotations.Nullable;

import com.example.mistvalerpg.dialogue.DialogueNode.Choice;

/**
 * Every conversation in the mod. This only says who speaks, in which mood, and where each box leads.
 *
 * THE WORDS ARE NOT HERE. To change what anybody says, open the language file (en_us.json) and edit the lines whose key
 * starts with dialogue.mistvale_rpg.<scene>.<box>, for example dialogue.mistvale_rpg.first_meeting.hello.
 * The text of an answer lives under the same key plus .choice0, .choice1 and so on.
 */
public final class DialogueScenes {
    private static final DialogueSpeaker SPIRIT = DialogueSpeaker.SPIRIT;
    private static final DialogueSpeaker PLAYER = DialogueSpeaker.PLAYER;

    /** The Spirit's very first talk with a new player. */
    public static final DialogueScene FIRST_MEETING = DialogueScene.builder("first_meeting", "hello")
            .say("hello", SPIRIT, Emotion.HAPPY, "who_am_i")
            .say("who_am_i", SPIRIT, Emotion.NEUTRAL, "ask_me")
            .say("ask_me", SPIRIT, Emotion.HAPPY, "questions")
            // A question the player can come back to as often as they like: every answer leads back here.
            // The player is the one asking, so the box shows the player's own character, speaking.
            .ask("questions", PLAYER, Emotion.NEUTRAL,
                    new Choice("where", List.of()),
                    new Choice("what_happened", List.of()),
                    new Choice("ready", List.of()))
            .say("where", SPIRIT, Emotion.NEUTRAL, "questions")
            .say("what_happened", SPIRIT, Emotion.SAD, "the_note")
            .say("the_note", SPIRIT, Emotion.ANGRY, "questions")
            .say("ready", SPIRIT, Emotion.HAPPY, "which_way")
            .ask("which_way", PLAYER, Emotion.NEUTRAL,
                    new Choice("north", List.of(DialogueAction.CHOOSE_NORTH)),
                    new Choice("west", List.of(DialogueAction.CHOOSE_WEST)),
                    new Choice("south", List.of(DialogueAction.CHOOSE_SOUTH)),
                    new Choice("east", List.of(DialogueAction.CHOOSE_EAST)))
            .say("north", SPIRIT, Emotion.HAPPY, "rift")
            .say("west", SPIRIT, Emotion.HAPPY, "rift")
            .say("south", SPIRIT, Emotion.HAPPY, "rift")
            .say("east", SPIRIT, Emotion.SCARED, "rift")
            // The rift opens the moment this box appears.
            .say("rift", SPIRIT, Emotion.HAPPY, null, DialogueAction.OPEN_BLUE_RIFT)
            .build();

    /** What she says to someone she has already met. */
    public static final DialogueScene AGAIN = DialogueScene.builder("again", "hello")
            .say("hello", SPIRIT, Emotion.HAPPY, "mist")
            .say("mist", SPIRIT, Emotion.NEUTRAL, null)
            .build();

    private static final Map<String, DialogueScene> ALL = Map.of(
            FIRST_MEETING.id(), FIRST_MEETING,
            AGAIN.id(), AGAIN);

    @Nullable
    public static DialogueScene get(String id) {
        return ALL.get(id);
    }

    public static Iterable<String> ids() {
        return ALL.keySet();
    }

    private DialogueScenes() {
    }
}