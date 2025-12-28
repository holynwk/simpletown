package com.simpletown.service;

import com.simpletown.data.PlotData;
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
import java.util.stream.Collectors;

public class PlotInfoMenuManager implements Listener {
    private static class PlotInfoHolder implements InventoryHolder {
        private final PlotData plot;

        private PlotInfoHolder(PlotData plot) {
            this.plot = plot;
        }

        @Override
        public Inventory getInventory() {
            return null;
        }
    }

    private final MessageService messages;
    private final Map<Inventory, PlotData> inventories = new HashMap<>();

    public PlotInfoMenuManager(MessageService messages) {
        this.messages = messages;
    }

    public void open(Player player, PlotData plot) {
        Inventory inventory = Bukkit.createInventory(new PlotInfoHolder(plot), 9, messages.get("plot.info.title"));
        inventory.setItem(4, createInfoItem(plot));
        inventories.put(inventory, plot);
        player.openInventory(inventory);
    }

    private ItemStack createInfoItem(PlotData plot) {
        ItemStack stack = new ItemStack(Material.OAK_HANGING_SIGN);
        ItemMeta meta = stack.getItemMeta();
        String owner = plot.isMunicipal() ? messages.get("plot.info.owner-municipal") : plot.getOwner();
        String residents = plot.getResidents().isEmpty()
                ? messages.get("plot.info.residents-none")
                : plot.getResidents().stream().sorted(String.CASE_INSENSITIVE_ORDER).collect(Collectors.joining(", "));
        String sale = plot.isForSale()
                ? messages.format("plot.info.sale", Map.of("price", formatPrice(plot.getSalePrice())))
                : messages.get("plot.info.not-for-sale");
        meta.setDisplayName(ChatColor.GOLD + "" + ChatColor.BOLD + messages.get("plot.info.item-title"));
        meta.setLore(List.of(
                ChatColor.GRAY + messages.format("plot.info.owner", Map.of("owner", owner)),
                ChatColor.GRAY + messages.format("plot.info.residents", Map.of("residents", residents)),
                ChatColor.GRAY + sale
        ));
        meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES, ItemFlag.HIDE_ENCHANTS);
        stack.setItemMeta(meta);
        return stack;
    }

    private String formatPrice(double price) {
        return String.format(Locale.US, "%.2f", price);
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getInventory().getHolder() instanceof PlotInfoHolder)) {
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