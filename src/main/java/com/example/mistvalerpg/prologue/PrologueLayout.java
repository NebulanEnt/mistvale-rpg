package com.example.mistvalerpg.prologue;

/**
 * The map of the sea. It answers one question: "what is at this spot?". The answer comes from arithmetic and nothing else,
 * never from the world's seed and never from a file, so every world and every player gets the very same sea, and an old
 * world builds the same sea as a new one.
 *
 * For now the whole sea is empty ocean. The islands arrive in the next step.
 */
public final class PrologueLayout {
    /** The sea level: water fills everything up to this height. */
    public static final int SEA = 63;
    /** The sea is built 160 blocks high, from 0 up to 159. */
    public static final int HEIGHT = 160;
    /** The sea is a ring: after 4096 blocks to the east you are back where you started. Every player gets a copy of it (see PrologueSlots). */
    public static final int PERIOD = 4096;

    /** What one column of the sea looks like: how high the ground is, and whether it is an island. */
    public record Col(int top, boolean land, double t, Kind kind, boolean home) {
    }

    /** The two looks of an island: green jungle, or a lighter tropical meadow. */
    public enum Kind {
        JUNGLE, TROPICS
    }

    /** Makes any x fit into the ring: -2048 to 2047. */
    public static int wrapX(int x) {
        return Math.floorMod(x + PERIOD / 2, PERIOD) - PERIOD / 2;
    }

    /** Scrambles a number into another, completely different looking number. The same input always gives the same output. */
    public static long mix(long value) {
        value = (value ^ value >>> 30) * -4658895280553007687L;
        value = (value ^ value >>> 27) * -7723592293110705685L;
        return value ^ value >>> 31;
    }

    /** A random-looking number from 0 to 1 for a point of a grid. "Random", but the same every time you ask. */
    public static double hash01(long salt, int x, int z) {
        long scrambled = mix(salt * -7046029254386353131L + (long) x * 7146057691288625177L + (long) z * -8306560040656073257L + 19088743L);
        return (double) (scrambled >>> 11) * 1.110223E-16F;
    }

    // Smooths the step between two numbers so that it eases in and out.
    private static double smooth(double t) {
        return t * t * (3.0 - 2.0 * t);
    }

    /** Smooth "value noise": hills and valleys that flow into each other, made from the random grid above. */
    public static double noise(double x, double z, long salt) {
        int x0 = (int) Math.floor(x);
        int z0 = (int) Math.floor(z);
        double fx = smooth(x - x0);
        double fz = smooth(z - z0);
        double a = hash01(salt, x0, z0);
        double b = hash01(salt, x0 + 1, z0);
        double c = hash01(salt, x0, z0 + 1);
        double d = hash01(salt, x0 + 1, z0 + 1);
        double top = a + (b - a) * fx;
        double bottom = c + (d - c) * fx;
        return top + (bottom - top) * fz;
    }

    /** What the sea looks like at one column. */
    public static Col col(int x, int z) {
        return colLocal(wrapX(x), z);
    }

    private static Col colLocal(int x, int z) {
        // The sea floor: about 25 blocks up from the bottom, gently rolling by up to 4 blocks.
        int floor = 25 + (int) Math.round((noise(x / 40.0, z / 40.0, 9L) - 0.5) * 8.0);
        return new Col(Math.min(floor, SEA - 1), false, -9.0, Kind.JUNGLE, false);
    }

    /** Which of the five biomes belongs here: 0 deep sea, 1 lagoon, 2 beach, 3 tropics, 4 jungle. */
    public static int biomeAt(int x, int z) {
        Col col = col(x, z);
        if (!col.land()) {
            return col.t() > -0.45 ? 1 : 0;   // water near an island is a lagoon, far from any island it is deep sea
        }
        if (col.top() <= 66) {
            return 2;
        }
        return col.kind() == Kind.TROPICS ? 3 : 4;
    }

    private PrologueLayout() {
    }
}