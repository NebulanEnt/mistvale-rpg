package com.example.mistvalerpg.prologue;

/**
 * The map of the sea. It answers one question: "what is at this spot?". The answer comes from arithmetic and nothing else,
 * never from the world's seed and never from a file, so every world and every player gets the very same sea, and an old
 * world builds the same sea as a new one.
 *
 * The sea is empty ocean with a handful of islands. One of them, the "home" island at the middle of the map, is where the
 * player will wake up. Around the ship (1600 blocks to the east) nothing is allowed to grow, so it has open water to float in.
 */
public final class PrologueLayout {
    /** The sea level: water fills everything up to this height. */
    public static final int SEA = 63;
    /** The sea is built 160 blocks high, from 0 up to 159. */
    public static final int HEIGHT = 160;
    /** The sea is a ring: after 4096 blocks to the east you are back where you started. Every player gets a copy of it (see PrologueSlots). */
    public static final int PERIOD = 4096;
    /** Islands are placed one to every square "cell" of this many blocks. */
    public static final int CELL = 192;
    /** Where the ship floats, and how far around it there must be no island. */
    public static final int SHIP_X = 1600;
    public static final int SHIP_Z = 0;
    public static final int SHIP_CLEAR = 850;

    /** What one column of the sea looks like: how high the ground is, and whether it is an island. */
    public record Col(int top, boolean land, double t, Kind kind, boolean home) {
    }

    /** One island: where its middle is, how wide it is, which look it has and how high it rises above the sea. */
    public record Isle(int cx, int cz, double radius, Kind kind, double height) {
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

    // Which cell a coordinate belongs to. Cell 0 is centred on 0 and reaches 96 blocks to each side.
    private static int cellOf(int coordinate) {
        return Math.floorDiv(coordinate + CELL / 2, CELL);
    }

    /** The island of one cell, or null if the cell is empty sea. The first four cells are fixed by hand, the home island and its three neighbours. */
    public static Isle isleOfCell(int cellX, int cellZ) {
        if (cellX == 0 && cellZ == 0) {
            return new Isle(0, 0, 54.0, Kind.TROPICS, 10.0);                                  // the home island, the biggest
        } else if (cellX == 1 && cellZ == -1) {
            return new Isle(cellX * CELL - 14, cellZ * CELL + 20, 44.0, Kind.JUNGLE, 8.0);
        } else if (cellX == -1 && cellZ == 0) {
            return new Isle(cellX * CELL + 18, cellZ * CELL - 26, 38.0, Kind.TROPICS, 7.0);
        } else if (cellX == 0 && cellZ == 1) {
            return new Isle(cellX * CELL + 10, cellZ * CELL + 8, 34.0, Kind.JUNGLE, 7.0);
        } else if (hash01(1L, cellX, cellZ) > 0.55) {
            return null;                                                                       // about half of the other cells stay empty
        } else {
            int x = cellX * CELL + (int) ((hash01(2L, cellX, cellZ) - 0.5) * 56.0);
            int z = cellZ * CELL + (int) ((hash01(3L, cellX, cellZ) - 0.5) * 56.0);
            if (Math.hypot(x - SHIP_X, z - SHIP_Z) < SHIP_CLEAR) {
                return null;                                                                   // the ship needs open water around it
            }
            double radius = 26.0 + hash01(4L, cellX, cellZ) * 24.0;
            Kind kind = hash01(5L, cellX, cellZ) < 0.5 ? Kind.JUNGLE : Kind.TROPICS;
            return new Isle(x, z, radius, kind, 5.0 + hash01(6L, cellX, cellZ) * 5.0);
        }
    }

    /** What the sea looks like at one column. */
    public static Col col(int x, int z) {
        return colLocal(wrapX(x), z);
    }

    private static Col colLocal(int x, int z) {
        int cellX = cellOf(x);
        int cellZ = cellOf(z);
        // Look at the islands of this cell and its eight neighbours and find the one this column is "most inside" of.
        // 1.0 is the very middle of an island, 0.0 is its shore, below 0 is open water. Two bits of noise make the shore wobbly.
        double best = -9.0;
        Isle nearest = null;
        for (int i = cellX - 1; i <= cellX + 1; i++) {
            for (int j = cellZ - 1; j <= cellZ + 1; j++) {
                Isle isle = isleOfCell(i, j);
                if (isle != null) {
                    double distance = Math.hypot(x - isle.cx(), z - isle.cz()) / isle.radius()
                            + 0.3 * (noise(x / 20.0, z / 20.0, 7L) - 0.5)
                            + 0.12 * (noise(x / 8.0, z / 8.0, 8L) - 0.5);
                    double inside = 1.0 - distance;
                    if (inside > best) {
                        best = inside;
                        nearest = isle;
                    }
                }
            }
        }

        // The sea floor: about 25 blocks up from the bottom, gently rolling by up to 4 blocks.
        int floor = 25 + (int) Math.round((noise(x / 40.0, z / 40.0, 9L) - 0.5) * 8.0);
        if (nearest != null && best > 0.0) {
            // Land. It rises from the shore (64) up to the island's height in the middle, with a little roughness.
            int top = 64 + (int) Math.round(nearest.height() * Math.pow(best, 0.9) + 1.6 * (noise(x / 9.0, z / 9.0, 10L) - 0.5));
            return new Col(Math.max(top, 64), true, best, nearest.kind(), nearest.cx() == 0 && nearest.cz() == 0);
        }
        if (nearest != null) {
            // Water close to an island: the floor slopes up towards the shore.
            floor = Math.max(floor, SEA - Math.max(1, (int) Math.round(45.0 * Math.pow(-best, 1.4))));
        }
        return new Col(Math.min(floor, SEA - 1), false, best, nearest == null ? Kind.JUNGLE : nearest.kind(), false);
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

    /**
     * Where the player wakes up: on the home island's beach. It starts in the middle of the island and walks south until the
     * land ends, then steps seven blocks back. Returns {x, z}.
     */
    public static int[] beach() {
        int x = -6;
        int z = 0;
        while (colLocal(x, z).land()) {
            z++;
        }
        return new int[] {x, z - 7};
    }

    /** True for the circle of 30 blocks around the beach. Nothing is built there (no spires), so the beach stays open. */
    public static boolean reserved(int x, int z) {
        int[] beach = beach();
        double dx = wrapX(x) - beach[0];
        double dz = z - beach[1];
        return dx * dx + dz * dz < 900.0;
    }

    private PrologueLayout() {
    }
}