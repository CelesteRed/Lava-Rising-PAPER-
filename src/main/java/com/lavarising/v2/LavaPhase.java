package com.lavarising.v2;

public enum LavaPhase {
    START_TO_Y0("startToY0", "Start"),
    Y0_TO_Y60("y0ToY60", "Y 0"),
    Y60_TO_Y100("y60ToY100", "Y 60"),
    Y100_TO_DEATHMATCH("y100ToDeathmatch", "Y 100"),
    DEATHMATCH_TO_TOP("deathmatchToTop", "Deathmatch");

    private final String configKey;
    private final String displayName;

    LavaPhase(String configKey, String displayName) {
        this.configKey = configKey;
        this.displayName = displayName;
    }

    public String configKey() {
        return configKey;
    }

    public String displayName() {
        return displayName;
    }
}
