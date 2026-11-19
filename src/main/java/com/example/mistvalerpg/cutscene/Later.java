package com.example.mistvalerpg.cutscene;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.server.MinecraftServer;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/**
 * "Do this in a moment." A cutscene is a list of things that happen at moments in time: the screen fades,
 * a second later the camera starts to fly, two seconds after that a line of text appears. This class keeps
 * that list and carries each one out when its time comes.
 *
 * <pre>
 *   Later.after(server, 40, () -&gt; Cutscene.bars(player, true));   // in 40 ticks, which is 2 seconds
 * </pre>
 */
public final class Later {
    private record Task(long dueTick, Runnable action) {
    }

    private static final List<Task> TASKS = new ArrayList<>();

    /** Runs {@code action} after {@code ticks} ticks (20 ticks = 1 second). */
    public static void after(MinecraftServer server, int ticks, Runnable action) {
        TASKS.add(new Task(server.getTickCount() + ticks, action));
    }

    // Called by the game at the end of every server tick (registered in MistvaleRpg).
    public static void onServerTick(ServerTickEvent.Post event) {
        if (TASKS.isEmpty()) {
            return;
        }
        long now = event.getServer().getTickCount();
        // Take the tasks that are due out of the list first, then run them. A task may add new tasks
        // while it runs, and changing a list while you walk through it is a mistake waiting to happen.
        List<Task> due = new ArrayList<>();
        TASKS.removeIf(task -> {
            if (task.dueTick() <= now) {
                due.add(task);
                return true;
            }
            return false;
        });
        due.forEach(task -> task.action().run());
    }

    // When the world closes, whatever was still waiting belongs to a world that is gone.
    public static void onServerStopped(ServerStoppedEvent event) {
        TASKS.clear();
    }

    private Later() {
    }
}