package com.xm6680.it.watching;

/**
 * Narrative horror phases. Watching level is only one gate into these phases.
 */
public enum HorrorPhase {
    DORMANT(1, "DORMANT", 0),
    WATCHING(2, "WATCHING", 20),
    IMITATING(3, "IMITATING", 40),
    INTRUSION(4, "INTRUSION", 60),
    MANIFESTATION(5, "MANIFESTATION", 80);

    private final int number;
    private final String displayName;
    private final int minimumWatchingLevel;

    HorrorPhase(int number, String displayName, int minimumWatchingLevel) {
        this.number = number;
        this.displayName = displayName;
        this.minimumWatchingLevel = minimumWatchingLevel;
    }

    public int getNumber() {
        return number;
    }

    public String getDisplayName() {
        return displayName;
    }

    public int getMinimumWatchingLevel() {
        return minimumWatchingLevel;
    }

    public boolean isAtLeast(HorrorPhase phase) {
        return number >= phase.number;
    }

    public HorrorPhase next() {
        int nextOrdinal = ordinal() + 1;
        HorrorPhase[] phases = values();
        return nextOrdinal >= phases.length ? this : phases[nextOrdinal];
    }

    public static HorrorPhase fromNumber(int number) {
        for (HorrorPhase phase : values()) {
            if (phase.number == number) {
                return phase;
            }
        }

        return DORMANT;
    }
}
