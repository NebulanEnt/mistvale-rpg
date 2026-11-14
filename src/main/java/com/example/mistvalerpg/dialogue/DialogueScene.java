package com.example.mistvalerpg.dialogue;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.jetbrains.annotations.Nullable;

/**
 * A whole conversation: a set of dialogue boxes that point at each other, and the id of the one to start with.
 * Scenes are built once in {@link DialogueScenes}; both the server and the player's game build the same ones,
 * so the server only has to say "show box X of scene Y".
 */
public final class DialogueScene {
    private final String id;
    private final String start;
    private final Map<String, DialogueNode> nodes;

    private DialogueScene(String id, String start, Map<String, DialogueNode> nodes) {
        this.id = id;
        this.start = start;
        this.nodes = nodes;
    }

    public String id() {
        return id;
    }

    public String start() {
        return start;
    }

    @Nullable
    public DialogueNode node(String nodeId) {
        return nodes.get(nodeId);
    }

    /** The key of a box's words in the language file. */
    public String textKey(String nodeId) {
        return "dialogue.mistvale_rpg." + id + "." + nodeId;
    }

    /** The key of one answer's words in the language file. */
    public String choiceKey(String nodeId, int index) {
        return textKey(nodeId) + ".choice" + index;
    }

    public static Builder builder(String id, String start) {
        return new Builder(id, start);
    }

    /** Lets a scene be written as a readable list: say(...), say(...), ask(...). */
    public static final class Builder {
        private final String id;
        private final String start;
        private final Map<String, DialogueNode> nodes = new LinkedHashMap<>();

        private Builder(String id, String start) {
            this.id = id;
            this.start = start;
        }

        /** A box that just talks, then goes on to {@code next} (or ends the conversation if next is null). */
        public Builder say(String nodeId, DialogueSpeaker speaker, Emotion emotion, @Nullable String next, DialogueAction... actions) {
            nodes.put(nodeId, new DialogueNode(nodeId, speaker, emotion, next, List.of(), List.of(actions)));
            return this;
        }

        /** A box that talks and then asks the player to pick an answer. */
        public Builder ask(String nodeId, DialogueSpeaker speaker, Emotion emotion, DialogueNode.Choice... choices) {
            nodes.put(nodeId, new DialogueNode(nodeId, speaker, emotion, null, List.of(choices), List.of()));
            return this;
        }

        public DialogueScene build() {
            return new DialogueScene(id, start, Map.copyOf(nodes));
        }
    }
}
