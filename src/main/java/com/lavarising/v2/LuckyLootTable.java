package com.lavarising.v2;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Random;
import java.util.Set;
import java.util.function.Supplier;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.PotionMeta;
import org.bukkit.potion.PotionType;

/**
 * Builds the Lucky Lava Rush loot pool from the gamemode config. Each roll first picks a
 * rarity tier by weight, then a random item from that tier, and tags it in the tier colour.
 *
 * <p>Item entries are material names, the token {@code *} (every obtainable block), or
 * {@code splash:<PotionType>} for a splash potion (e.g. {@code splash:HARMING}).
 */
public final class LuckyLootTable {
    private final List<Tier> tiers = new ArrayList<>();
    private int totalWeight;

    public LuckyLootTable(FileConfiguration config) {
        Set<Material> excluded = parseMaterialSet(config.getStringList("excludeBlocks"));
        ConfigurationSection rarities = config.getConfigurationSection("rarities");
        if (rarities != null) {
            for (String key : rarities.getKeys(false)) {
                ConfigurationSection section = rarities.getConfigurationSection(key);
                if (section == null) {
                    continue;
                }
                String label = section.getString("label", capitalize(key));
                ChatColor color = parseColor(section.getString("color", "WHITE"));
                int weight = Math.max(0, section.getInt("weight", 1));
                List<Supplier<ItemStack>> items = buildItems(section.getStringList("items"), excluded);
                if (weight > 0 && !items.isEmpty()) {
                    tiers.add(new Tier(label, color, weight, items));
                    totalWeight += weight;
                }
            }
        }
        if (tiers.isEmpty()) {
            // Legacy fallback: a flat luckyItems list treated as one common tier.
            List<Supplier<ItemStack>> items = buildItems(config.getStringList("luckyItems"), excluded);
            if (!items.isEmpty()) {
                tiers.add(new Tier("Common", ChatColor.WHITE, 1, items));
                totalWeight = 1;
            }
        }
    }

    public boolean isEmpty() {
        return tiers.isEmpty() || totalWeight <= 0;
    }

    public ItemStack roll(Random random) {
        Tier tier = pickTier(random);
        ItemStack item = tier.items().get(random.nextInt(tier.items().size())).get();
        tagRarity(item, tier);
        return item;
    }

    private Tier pickTier(Random random) {
        int roll = random.nextInt(totalWeight);
        int cumulative = 0;
        for (Tier tier : tiers) {
            cumulative += tier.weight();
            if (roll < cumulative) {
                return tier;
            }
        }
        return tiers.get(tiers.size() - 1);
    }

    private void tagRarity(ItemStack item, Tier tier) {
        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            return;
        }
        meta.setDisplayName(tier.color() + readableName(item.getType()));
        List<String> lore = meta.hasLore() ? new ArrayList<>(meta.getLore()) : new ArrayList<>();
        lore.add(tier.color() + "" + ChatColor.BOLD + tier.label().toUpperCase(Locale.ROOT));
        meta.setLore(lore);
        item.setItemMeta(meta);
    }

    private List<Supplier<ItemStack>> buildItems(List<String> raw, Set<Material> excluded) {
        List<Supplier<ItemStack>> result = new ArrayList<>();
        for (String entry : raw) {
            if (entry == null || entry.trim().isEmpty()) {
                continue;
            }
            String token = entry.trim();
            if (token.equals("*") || token.equalsIgnoreCase("ALL_BLOCKS")) {
                for (Material material : Material.values()) {
                    if (material.isBlock() && material.isItem() && !material.isLegacy()
                            && !excluded.contains(material) && !isUnobtainable(material)) {
                        ItemStack stack = new ItemStack(material);
                        result.add(stack::clone);
                    }
                }
                continue;
            }
            if (token.toLowerCase(Locale.ROOT).startsWith("splash:")) {
                PotionType type = parsePotionType(token.substring("splash:".length()));
                if (type != null) {
                    result.add(() -> buildSplashPotion(type));
                }
                continue;
            }
            Material material = Material.matchMaterial(token);
            if (material != null && material.isItem() && !material.isLegacy()) {
                ItemStack stack = new ItemStack(material);
                result.add(stack::clone);
            }
        }
        return result;
    }

    private boolean isUnobtainable(Material material) {
        // Admin / unbreakable blocks that would break a lava game if handed out as loot.
        switch (material) {
            case BEDROCK:
            case BARRIER:
            case COMMAND_BLOCK:
            case CHAIN_COMMAND_BLOCK:
            case REPEATING_COMMAND_BLOCK:
            case STRUCTURE_BLOCK:
            case STRUCTURE_VOID:
            case JIGSAW:
            case LIGHT:
            case SPAWNER:
            case END_PORTAL_FRAME:
            case END_GATEWAY:
            case NETHER_PORTAL:
            case END_PORTAL:
                return true;
            default:
                return false;
        }
    }

    private ItemStack buildSplashPotion(PotionType type) {
        ItemStack potion = new ItemStack(Material.SPLASH_POTION);
        if (potion.getItemMeta() instanceof PotionMeta potionMeta) {
            potionMeta.setBasePotionType(type);
            potion.setItemMeta(potionMeta);
        }
        return potion;
    }

    private PotionType parsePotionType(String name) {
        try {
            return PotionType.valueOf(name.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }

    private Set<Material> parseMaterialSet(List<String> names) {
        Set<Material> result = new HashSet<>();
        for (String name : names) {
            if (name == null) {
                continue;
            }
            Material material = Material.matchMaterial(name.trim());
            if (material != null) {
                result.add(material);
            }
        }
        return result;
    }

    private ChatColor parseColor(String raw) {
        try {
            return ChatColor.valueOf(raw.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            return ChatColor.WHITE;
        }
    }

    private String readableName(Material material) {
        return capitalize(material.name());
    }

    private String capitalize(String enumName) {
        StringBuilder sb = new StringBuilder();
        for (String part : enumName.toLowerCase(Locale.ROOT).split("_")) {
            if (part.isEmpty()) {
                continue;
            }
            sb.append(Character.toUpperCase(part.charAt(0))).append(part.substring(1)).append(' ');
        }
        return sb.toString().trim();
    }

    private record Tier(String label, ChatColor color, int weight, List<Supplier<ItemStack>> items) {
    }
}
