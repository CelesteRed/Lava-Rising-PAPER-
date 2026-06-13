package com.lavarising.v2;

import java.util.List;
import java.util.Locale;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.configuration.file.FileConfiguration;

public final class GamemodeSettings {
    private final GameModeType type;
    private final FileConfiguration config;

    public GamemodeSettings(GameModeType type, FileConfiguration config) {
        this.type = type;
        this.config = config;
    }

    public GameModeType type() {
        return type;
    }

    public FileConfiguration config() {
        return config;
    }

    public boolean enabled() {
        return config.getBoolean("enabled", true);
    }

    public String displayName() {
        return config.getString("displayName", type.defaultDisplayName());
    }

    public ChatColor color() {
        String raw = config.getString("color", type.defaultColor().name());
        try {
            return ChatColor.valueOf(raw.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            return type.defaultColor();
        }
    }

    public Material icon() {
        Material material = Material.matchMaterial(config.getString("icon", type.defaultIcon().name()));
        return material == null ? type.defaultIcon() : material;
    }

    public List<String> description() {
        return config.getStringList("description");
    }

    public Material buildingBlock(Material fallback) {
        Material material = Material.matchMaterial(config.getString("buildingBlock", fallback.name()));
        return material == null || !material.isBlock() ? fallback : material;
    }

    public boolean givesBuildingBlocks() {
        return config.getBoolean("giveBuildingBlocks", true);
    }

    public boolean silkTouchHands() {
        return config.getBoolean("silkTouchHands", false);
    }

    public String coloredName() {
        return color() + "" + ChatColor.BOLD + displayName();
    }
}
