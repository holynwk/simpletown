package com.simpletown.service;

import com.simpletown.data.Town;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class InfoMenuManager implements Listener {
    private static class InfoHolder implements InventoryHolder {
        private final Town town;

        private InfoHolder(Town town) {
            this.town = town;
        }

        @Override
        public Inventory getInventory() {
            return null;
        }
    }

    private final MessageService messages;
    private final Map<Inventory, Town> inventories = new HashMap<>();

    public InfoMenuManager(MessageService messages) {
        this.messages = messages;
    }

    public void open(Player player, Town town) {
        Inventory inventory = Bukkit.createInventory(new InfoHolder(town), 9, messages.get("town.info.title"));
        inventory.setItem(4, createInfoItem(town));
        inventories.put(inventory, town);
        player.openInventory(inventory);
    }

    private ItemStack createInfoItem(Town town) {
        ItemStack stack = new ItemStack(Material.OAK_HANGING_SIGN);
        ItemMeta meta = stack.getItemMeta();
        int citizens = town.getCitizens().size() + 1;
        meta.setDisplayName(ChatColor.GOLD + town.getName());
        meta.setLore(List.of(
                ChatColor.YELLOW + messages.format("town.info.name", Map.of("name", town.getName())),
                ChatColor.GRAY + messages.format("town.info.chunks", Map.of("count", String.valueOf(town.getChunks().size()))),
                ChatColor.GRAY + messages.format("town.info.citizens", Map.of("count", String.valueOf(citizens))),
                ChatColor.GRAY + messages.format("town.info.bank", Map.of("amount", formatBank(town.getBank())))
        ));
        meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES, ItemFlag.HIDE_ENCHANTS);
        stack.setItemMeta(meta);
        return stack;
    }

    private String formatBank(double amount) {
        return String.format(Locale.US, "%.2f", amount);
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getInventory().getHolder() instanceof InfoHolder)) {
            return;
        }
        if (!inventories.containsKey(event.getInventory())) {
            return;
        }
        event.setCancelled(true);
    }

    @EventHandler
    public void onInventoryClose(InventoryCloseEvent event) {
        inventories.remove(event.getInventory());
    }
}
