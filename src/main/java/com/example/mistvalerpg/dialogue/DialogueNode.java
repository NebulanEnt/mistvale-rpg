package com.example.mistvalerpg.dialogue;

import java.util.List;

import org.jetbrains.annotations.Nullable;

/**
 * One box of dialogue: somebody says something, then either the conversation moves on to the next box
 * ({@code next}), ends (next is null), or the player picks one of the answers ({@code choices}).
 * The words themselves live in the language file under dialogue.mistvale_rpg.[scene].[id].
 * {@code actions} are things that happen in the world the moment this box appears.
 */
public record DialogueNode(String id, DialogueSpeaker speaker, Emotion emotion, @Nullable String next, List<Choice> choices, List<DialogueAction> actions) {

    /** One answer the player can pick. Its words are in the language file under [node id].choice0, choice1, ... */
    public record Choice(String next, List<DialogueAction> actions) {
    }

    public boolean isQuestion() {
        return !choices.isEmpty();
    }
}
