package com.simpletown.service;

import com.simpletown.data.BuildingType;
import com.simpletown.data.Town;
import com.simpletown.data.TownManager;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import java.util.Arrays;
import java.util.List;

public class TownInventoryService implements Listener {
    private final TownManager townManager;
    private final MessageService messages;

    public TownInventoryService(TownManager townManager, MessageService messages) {
        this.townManager = townManager;
        this.messages = messages;
    }

    public void openInventory(Player player, Town town) {
        int level = town.getBuildingLevel(BuildingType.WAREHOUSE);
        if (level <= 0) {
            messages.sendError(player, "town.inventory.missing");
            return;
        }

        int rows = switch (level) {
            case 1 -> 2;
            case 2 -> 3;
            default -> 6;
        };

        Inventory inventory = Bukkit.createInventory(new TownInventoryHolder(town), rows * 9, ChatColor.DARK_GRAY + "Склад");
        List<ItemStack> contents = town.getInventoryContents();
        for (int i = 0; i < Math.min(inventory.getSize(), contents.size()); i++) {
            inventory.setItem(i, contents.get(i));
        }
        player.openInventory(inventory);
    }

    @EventHandler
    public void onInventoryClose(InventoryCloseEvent event) {
        if (!(event.getInventory().getHolder() instanceof TownInventoryHolder holder)) {
            return;
        }
        Town town = holder.getTown();
        ItemStack[] contents = Arrays.copyOf(event.getInventory().getContents(), event.getInventory().getContents().length);
        townManager.setInventory(town, Arrays.asList(contents));
    }
}