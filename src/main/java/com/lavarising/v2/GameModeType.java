package com.lavarising.v2;

import java.util.Locale;
import org.bukkit.ChatColor;
import org.bukkit.Material;

public enum GameModeType {
    CLASSIC("classic", "Classic", ChatColor.GRAY, Material.DIRT),
    LUCKY_LAVA_RUSH("lucky-lava-rush", "Lucky Lava Rush", ChatColor.GOLD, Material.CHEST),
    HARDCORE_MASSACRE("hardcore-massacre", "Hardcore Massacre", ChatColor.DARK_RED, Material.DIAMOND_SWORD),
    RANGE_RAMPAGE("range-rampage", "Range Rampage", ChatColor.BLUE, Material.BOW),
    SAND_MAYHEM("sand-mayhem", "Sand Mayhem", ChatColor.YELLOW, Material.SAND),
    TINY("tiny", "Tiny", ChatColor.LIGHT_PURPLE, Material.RABBIT_FOOT),
    LOW_GRAVITY("low-gravity", "Low Gravity", ChatColor.AQUA, Material.FEATHER),
    JUGGERNAUT("juggernaut", "Juggernaut", ChatColor.DARK_RED, Material.IRON_CHESTPLATE),
    TEAM_CHAOS("team-chaos", "Team Chaos - TDM", ChatColor.GOLD, Material.TNT);

    private final String id;
    private final String defaultDisplayName;
    private final ChatColor defaultColor;
    private final Material defaultIcon;

    GameModeType(String id, String defaultDisplayName, ChatColor defaultColor, Material defaultIcon) {
        this.id = id;
        this.defaultDisplayName = defaultDisplayName;
        this.defaultColor = defaultColor;
        this.defaultIcon = defaultIcon;
    }

    public String id() {
        return id;
    }

    public String defaultDisplayName() {
        return defaultDisplayName;
    }

    public ChatColor defaultColor() {
        return defaultColor;
    }

    public Material defaultIcon() {
        return defaultIcon;
    }

    public static GameModeType byId(String id) {
        if (id == null) {
            return null;
        }
        String normalized = id.trim().toLowerCase(Locale.ROOT);
        for (GameModeType type : values()) {
            if (type.id.equals(normalized)) {
                return type;
            }
        }
        return null;
    }
}
