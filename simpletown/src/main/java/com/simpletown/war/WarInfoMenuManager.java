package com.simpletown.war;

import com.simpletown.data.Town;
import com.simpletown.service.MessageService;
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

import java.util.*;

public class WarInfoMenuManager implements Listener {
    private static class WarInfoHolder implements InventoryHolder {
        private final WarConflict conflict;

        private WarInfoHolder(WarConflict conflict) {
            this.conflict = conflict;
        }

        @Override
        public Inventory getInventory() {
            return null;
        }
    }

    private final MessageService messages;
    private final Map<Inventory, WarConflict> inventories = new HashMap<>();

    public WarInfoMenuManager(MessageService messages) {
        this.messages = messages;
    }

    public void open(Player player, WarConflict conflict, Town attacker, Town defender) {
        Inventory inventory = Bukkit.createInventory(new WarInfoHolder(conflict), 9, ChatColor.DARK_RED + "Очки войны");
        inventory.setItem(3, createInfoItem(attacker, conflict.getAttackerPoints()));
        inventory.setItem(5, createInfoItem(defender, conflict.getDefenderPoints()));
        inventories.put(inventory, conflict);
        player.openInventory(inventory);
    }

    private ItemStack createInfoItem(Town town, int points) {
        ItemStack stack = new ItemStack(Material.OAK_HANGING_SIGN);
        ItemMeta meta = stack.getItemMeta();
        int citizens = town.getCitizens().size() + 1;
        meta.setDisplayName(ChatColor.GOLD + "" + ChatColor.BOLD + town.getName());
        meta.setLore(List.of(
                ChatColor.GRAY + messages.format("town.info.chunks", Map.of("count", String.valueOf(town.getChunks().size()))),
                ChatColor.GRAY + messages.format("town.info.citizens", Map.of("count", String.valueOf(citizens))),
                ChatColor.GRAY + messages.format("town.info.bank", Map.of("amount", formatBank(town.getBank()))),
                ChatColor.RED + messages.format("war.info.points", Map.of("points", String.valueOf(points)))
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
        if (!(event.getInventory().getHolder() instanceof WarInfoHolder)) {
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