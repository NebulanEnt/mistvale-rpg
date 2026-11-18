package com.example.mistvalerpg.client;

import io.github.leawind.perspectiveapi.api.Perspective;
import io.github.leawind.perspectiveapi.api.PerspectiveAPI;
import io.github.leawind.perspectiveapi.api.PerspectiveRegistry;
import io.github.leawind.perspectiveapi.api.PerspectiveSwitcher;
import io.github.leawind.perspectiveapi.api.PerspectiveSwitcherBehavior;
import io.github.leawind.perspectiveapi.api.Transition;

/**
 * Our bridge to the Perspective API by Leawind, the mod that decides which camera the player has, and which
 * Leawind's Third Person (the over-the-shoulder camera) is built on. Both are mods of the Mistvale pack, and both
 * are declared as required mods in neoforge.mods.toml, so this class calls their code directly.
 *
 * Every other class of ours talks to the cameras only through this one. If the mod ever changes its API, this is
 * the only file that has to be fixed.
 *
 * Perspective ids we care about:
 *   leawind_third_person.third_person   the over-the-shoulder camera (from Leawind's Third Person)
 *   perspective_api.first_person        plain first person
 *
 * Perspective API: https://github.com/Leawind/Perspective-API (MIT)
 * Leawind's Third Person: https://github.com/Leawind/Third-Person (MIT)
 */
public final class PerspectiveLink {
    public static final String SHOULDER = "leawind_third_person.third_person";
    public static final String FIRST_PERSON = "perspective_api.first_person";

    /** True if the Perspective API is switched on (a player can turn it off in its settings). */
    public static boolean isActive() {
        try {
            return PerspectiveAPI.isEnabled();
        } catch (RuntimeException e) {
            return false; // the mod is not ready yet, for example during loading
        }
    }

    /** The id of the perspective the player is in right now, or null if the API is off or not ready. */
    public static String current() {
        if (!isActive()) {
            return null;
        }
        try {
            return PerspectiveAPI.getCurrent().info().id();
        } catch (RuntimeException e) {
            return null;
        }
    }

    /** Switches to the perspective with this id. Returns false if the API is off or does not know that id. */
    public static boolean select(String id) {
        if (!isActive()) {
            return false;
        }
        try {
            PerspectiveRegistry registry = PerspectiveAPI.getRegistry();
            if (!registry.contains(id)) {
                return false;
            }
            Perspective perspective = registry.get(id);
            PerspectiveSwitcher switcher = PerspectiveAPI.getSwitcherManager().getSelectedSwitcher();
            if (switcher instanceof PerspectiveSwitcherBehavior behavior) {
                behavior.onActivated(perspective);
                return true;
            }
            return false;
        } catch (RuntimeException e) {
            return false;
        }
    }

    /** How long the mod takes to glide from one perspective to the next, in milliseconds, or null if the API is off. */
    public static Double transitionMs() {
        if (!isActive()) {
            return null;
        }
        try {
            Transition transition = PerspectiveAPI.getTransition();
            return transition.getDurationMs();
        } catch (RuntimeException e) {
            return null;
        }
    }

    /** Sets how long the mod takes to glide between perspectives. 0 means "switch at once", which a cutscene camera needs. */
    public static void setTransitionMs(double milliseconds) {
        if (!isActive()) {
            return;
        }
        try {
            PerspectiveAPI.getTransition().setDurationMs(milliseconds);
        } catch (RuntimeException e) {
            // nothing to do: without a working API there is no gliding to switch off
        }
    }

    private PerspectiveLink() {
    }
}