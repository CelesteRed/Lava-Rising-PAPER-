package com.lavarising.v2;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

public final class ArenaStore {
    private final LavaRisingPlugin plugin;
    private final File dataFile;
    private final List<ArenaCenter> usedCenters = new ArrayList<>();
    private YamlConfiguration data;

    public ArenaStore(LavaRisingPlugin plugin) {
        this.plugin = plugin;
        this.dataFile = new File(plugin.getDataFolder(), "data.yml");
    }

    public void load() {
        if (!plugin.getDataFolder().exists()) {
            plugin.getDataFolder().mkdirs();
        }
        data = YamlConfiguration.loadConfiguration(dataFile);
        usedCenters.clear();
        for (String entry : data.getStringList("usedArenas")) {
            ArenaCenter center = parseCenter(entry);
            if (center != null) {
                usedCenters.add(center);
            }
        }

        ConfigurationSection legacy = data.getConfigurationSection("usedArenaCenters");
        if (legacy != null) {
            for (String key : legacy.getKeys(false)) {
                int x = legacy.getInt(key + ".x");
                int z = legacy.getInt(key + ".z");
                usedCenters.add(new ArenaCenter(x, z));
            }
            save();
        }
    }

    public List<ArenaCenter> usedCenters() {
        return Collections.unmodifiableList(usedCenters);
    }

    public void remember(ArenaCenter center) {
        if (!usedCenters.contains(center)) {
            usedCenters.add(center);
            save();
        }
    }

    public void clear() {
        usedCenters.clear();
        save();
    }

    private void save() {
        List<String> serialized = usedCenters.stream()
                .map(center -> center.x() + "," + center.z())
                .toList();
        data.set("usedArenas", serialized);
        data.set("usedArenaCenters", null);
        try {
            data.save(dataFile);
        } catch (IOException ex) {
            plugin.getLogger().warning("Unable to save data.yml: " + ex.getMessage());
        }
    }

    private ArenaCenter parseCenter(String entry) {
        if (entry == null) {
            return null;
        }
        String[] parts = entry.split(",");
        if (parts.length != 2) {
            return null;
        }
        try {
            return new ArenaCenter(Integer.parseInt(parts[0].trim()), Integer.parseInt(parts[1].trim()));
        } catch (NumberFormatException ex) {
            return null;
        }
    }
}
