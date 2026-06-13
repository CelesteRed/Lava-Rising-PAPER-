package com.lavarising.v2;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Sound;
import org.bukkit.entity.HumanEntity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

public final class GamemodeVote implements InventoryHolder {
    private final GamemodeService gamemodes;
    private final List<GameModeType> options;
    private final Map<Integer, GameModeType> slotToMode = new HashMap<>();
    private final Map<UUID, GameModeType> votes = new HashMap<>();
    private final Inventory inventory;
    private boolean closing;

    public GamemodeVote(GamemodeService gamemodes, List<GameModeType> options) {
        this.gamemodes = gamemodes;
        this.options = new ArrayList<>(options);
        this.inventory = Bukkit.createInventory(this, 9, ChatColor.DARK_GRAY + "Vote for a Gamemode");
        render();
    }

    @Override
    public Inventory getInventory() {
        return inventory;
    }

    public void open(Player player) {
        player.openInventory(inventory);
    }

    public void closeAll() {
        closing = true;
        for (HumanEntity viewer : new ArrayList<>(inventory.getViewers())) {
            viewer.closeInventory();
        }
    }

    public boolean hasVoted(Player player) {
        return votes.containsKey(player.getUniqueId());
    }

    public boolean isClosing() {
        return closing;
    }

    public void handleClick(Player player, int slot) {
        GameModeType type = slotToMode.get(slot);
        if (type == null) {
            return;
        }
        votes.put(player.getUniqueId(), type);
        player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 1.0F, 1.2F);
        player.sendActionBar(ChatColor.GREEN + "You voted for " + gamemodes.settings(type).coloredName());
        render();
        for (HumanEntity viewer : new ArrayList<>(inventory.getViewers())) {
            if (viewer instanceof Player p) {
                p.updateInventory();
            }
        }
    }

    public GameModeType winner(Random random, GameModeType fallback) {
        Map<GameModeType, Integer> counts = new LinkedHashMap<>();
        for (GameModeType type : options) {
            counts.put(type, 0);
        }
        for (GameModeType vote : votes.values()) {
            counts.merge(vote, 1, Integer::sum);
        }
        int max = 0;
        for (int count : counts.values()) {
            max = Math.max(max, count);
        }
        if (max == 0) {
            if (fallback != null && options.contains(fallback)) {
                return fallback;
            }
            return options.get(random.nextInt(options.size()));
        }
        List<GameModeType> top = new ArrayList<>();
        for (Map.Entry<GameModeType, Integer> entry : counts.entrySet()) {
            if (entry.getValue() == max) {
                top.add(entry.getKey());
            }
        }
        return top.get(random.nextInt(top.size()));
    }

    private void render() {
        slotToMode.clear();
        int count = options.size();
        int start = Math.max(0, (9 - count) / 2);
        for (int i = 0; i < count && start + i < 9; i++) {
            int slot = start + i;
            GameModeType type = options.get(i);
            slotToMode.put(slot, type);
            inventory.setItem(slot, buildItem(type));
        }
    }

    private ItemStack buildItem(GameModeType type) {
        GamemodeSettings settings = gamemodes.settings(type);
        ItemStack item = new ItemStack(settings.icon());
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(settings.coloredName());
            List<String> lore = new ArrayList<>();
            for (String line : settings.description()) {
                lore.add(ChatColor.GRAY + line);
            }
            lore.add("");
            lore.add(ChatColor.YELLOW + "Votes: " + ChatColor.WHITE + countVotes(type));
            lore.add(ChatColor.GREEN + "" + ChatColor.BOLD + "Click to vote!");
            meta.setLore(lore);
            item.setItemMeta(meta);
        }
        return item;
    }

    private int countVotes(GameModeType type) {
        int count = 0;
        for (GameModeType vote : votes.values()) {
            if (vote == type) {
                count++;
            }
        }
        return count;
    }
}
