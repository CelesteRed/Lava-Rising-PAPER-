package com.lavarising.v2;

import java.io.File;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

public final class GamemodeService {
    private final LavaRisingPlugin plugin;
    private final Map<GameModeType, GamemodeSettings> modes = new EnumMap<>(GameModeType.class);

    public GamemodeService(LavaRisingPlugin plugin) {
        this.plugin = plugin;
    }

    public void load() {
        modes.clear();
        File dir = new File(plugin.getDataFolder(), "gamemodes");
        if (!dir.exists()) {
            dir.mkdirs();
        }
        for (GameModeType type : GameModeType.values()) {
            String resourcePath = "gamemodes/" + type.id() + ".yml";
            File file = new File(plugin.getDataFolder(), resourcePath);
            if (!file.exists()) {
                try {
                    plugin.saveResource(resourcePath, false);
                } catch (IllegalArgumentException ex) {
                    plugin.getLogger().warning("[LavaRising] Missing bundled gamemode config: " + resourcePath);
                }
            }
            FileConfiguration config = YamlConfiguration.loadConfiguration(file);
            modes.put(type, new GamemodeSettings(type, config));
        }
        plugin.logGame("Loaded gamemodes: " + enabledModes().stream().map(GameModeType::id).toList() + ".");
    }

    public GamemodeSettings settings(GameModeType type) {
        GamemodeSettings found = modes.get(type);
        return found != null ? found : new GamemodeSettings(type, new YamlConfiguration());
    }

    public List<GameModeType> enabledModes() {
        List<GameModeType> result = new ArrayList<>();
        for (GameModeType type : GameModeType.values()) {
            GamemodeSettings settings = modes.get(type);
            if (settings != null && settings.enabled()) {
                result.add(type);
            }
        }
        return result;
    }

    public boolean isEnabled(GameModeType type) {
        GamemodeSettings settings = modes.get(type);
        return settings != null && settings.enabled();
    }
}
