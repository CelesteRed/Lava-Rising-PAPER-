package com.lavarising.v2;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.PotionMeta;
import org.bukkit.potion.PotionType;

/**
 * Parses the Juggernaut "kits:" config and hands the Juggernaut ONE random kit. Each kit may
 * define an "armor:" tier shorthand (DIAMOND/NETHERITE/IRON/...) and a list of "items:" where each
 * item has: type (material), and optionally amount, name, enchants (key:level), potion (PotionType).
 * Armour pieces and wearable heads/skulls are auto-equipped; everything else goes to the inventory.
 */
public final class JuggernautKits {
    private final List<Kit> kits = new ArrayList<>();

    public JuggernautKits(FileConfiguration config) {
        for (Map<?, ?> raw : config.getMapList("kits")) {
            kits.add(parseKit(raw));
        }
    }

    public boolean isEmpty() {
        return kits.isEmpty();
    }

    public void giveRandom(Player player, Random random) {
        if (kits.isEmpty()) {
            return;
        }
        Kit kit = kits.get(random.nextInt(kits.size()));
        PlayerInventory inv = player.getInventory();
        equipArmor(inv, kit.armor());
        for (ItemStack item : kit.items()) {
            if (isArmorPiece(item.getType())) {
                equipPiece(inv, item);
            } else if (isWearableHead(item.getType())) {
                inv.setHelmet(item);
            } else {
                inv.addItem(item);
            }
        }
    }

    private Kit parseKit(Map<?, ?> raw) {
        String armor = asString(raw.get("armor"), null);
        List<ItemStack> items = new ArrayList<>();
        if (raw.get("items") instanceof List<?> list) {
            for (Object entry : list) {
                if (entry instanceof Map<?, ?> map) {
                    ItemStack item = parseItem(map);
                    if (item != null) {
                        items.add(item);
                    }
                }
            }
        }
        return new Kit(armor, items);
    }

    private ItemStack parseItem(Map<?, ?> map) {
        Material type = Material.matchMaterial(asString(map.get("type"), ""));
        if (type == null || !type.isItem()) {
            return null;
        }
        ItemStack item = new ItemStack(type, Math.max(1, asInt(map.get("amount"), 1)));
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            String name = asString(map.get("name"), null);
            if (name != null) {
                meta.setDisplayName(ChatColor.translateAlternateColorCodes('&', name));
            }
            if (map.get("potion") != null && meta instanceof PotionMeta potionMeta) {
                PotionType potionType = resolvePotionType(asString(map.get("potion"), ""));
                if (potionType != null) {
                    potionMeta.setBasePotionType(potionType);
                }
            }
            item.setItemMeta(meta);
        }
        if (map.get("enchants") instanceof Map<?, ?> enchants) {
            for (Map.Entry<?, ?> entry : enchants.entrySet()) {
                Enchantment enchantment = resolveEnchantment(asString(entry.getKey(), ""));
                int level = asInt(entry.getValue(), 1);
                if (enchantment != null && level > 0) {
                    item.addUnsafeEnchantment(enchantment, level);
                }
            }
        }
        return item;
    }

    private static void equipArmor(PlayerInventory inv, String tier) {
        if (tier == null || tier.isBlank()) {
            return;
        }
        String prefix = tier.trim().toUpperCase(Locale.ROOT);
        for (String suffix : new String[] {"_HELMET", "_CHESTPLATE", "_LEGGINGS", "_BOOTS"}) {
            Material material = Material.matchMaterial(prefix + suffix);
            if (material != null) {
                equipPiece(inv, new ItemStack(material));
            }
        }
    }

    private static void equipPiece(PlayerInventory inv, ItemStack item) {
        String name = item.getType().name();
        if (name.endsWith("_HELMET")) {
            inv.setHelmet(item);
        } else if (name.endsWith("_CHESTPLATE")) {
            inv.setChestplate(item);
        } else if (name.endsWith("_LEGGINGS")) {
            inv.setLeggings(item);
        } else if (name.endsWith("_BOOTS")) {
            inv.setBoots(item);
        } else {
            inv.addItem(item);
        }
    }

    private static boolean isArmorPiece(Material type) {
        String name = type.name();
        return name.endsWith("_HELMET") || name.endsWith("_CHESTPLATE")
                || name.endsWith("_LEGGINGS") || name.endsWith("_BOOTS");
    }

    private static boolean isWearableHead(Material type) {
        String name = type.name();
        return name.endsWith("_HEAD") || name.endsWith("_SKULL");
    }

    private Enchantment resolveEnchantment(String key) {
        try {
            String name = key.trim().toLowerCase(Locale.ROOT);
            if (name.startsWith("minecraft:")) {
                name = name.substring("minecraft:".length());
            }
            return Registry.ENCHANTMENT.get(NamespacedKey.minecraft(name));
        } catch (RuntimeException ex) {
            return null;
        }
    }

    private PotionType resolvePotionType(String name) {
        try {
            return PotionType.valueOf(name.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }

    private static String asString(Object value, String fallback) {
        return value == null ? fallback : value.toString();
    }

    private static int asInt(Object value, int fallback) {
        if (value instanceof Number number) {
            return number.intValue();
        }
        try {
            return Integer.parseInt(String.valueOf(value));
        } catch (NumberFormatException ex) {
            return fallback;
        }
    }

    private record Kit(String armor, List<ItemStack> items) {
    }
}
