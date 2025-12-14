package com.simpletown.service;

import com.simpletown.data.Town;
import com.simpletown.service.RichChunkService.RichChunkEntry;
import net.md_5.bungee.api.ChatColor;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.time.format.DateTimeFormatter;
import java.util.*;

public class ResourceMenuManager implements Listener {
    private static final int INVENTORY_SIZE = 54;
    private static final int PAGE_SIZE = 45;
    private static final int PREVIOUS_SLOT = 45;
    private static final int NEXT_SLOT = 53;
    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("dd.MM.yyyy");

    private record PageState(Town town, List<RichChunkEntry> entries, int page, int totalPages) {}

    private final RichChunkService richChunkService;
    private final MessageService messages;
    private final Map<Inventory, PageState> menus = new HashMap<>();

    public ResourceMenuManager(RichChunkService richChunkService, MessageService messages) {
        this.richChunkService = richChunkService;
        this.messages = messages;
    }

    public void openResources(Player player, Town town, int page) {
        List<RichChunkEntry> entries = richChunkService.getRichChunksForTown(town);
        int totalPages = Math.max(1, (int) Math.ceil(entries.size() / (double) PAGE_SIZE));
        int targetPage = Math.min(Math.max(page, 1), totalPages);
        Inventory inventory = Bukkit.createInventory(null, INVENTORY_SIZE, messages.get("town.resources.title"));
        fillPage(inventory, entries, targetPage, totalPages);
        menus.put(inventory, new PageState(town, entries, targetPage, totalPages));
        player.openInventory(inventory);
    }

    private void fillPage(Inventory inventory, List<RichChunkEntry> entries, int page, int totalPages) {
        int start = (page - 1) * PAGE_SIZE;
        for (int slot = 0; slot < PAGE_SIZE; slot++) {
            int index = start + slot;
            if (index >= entries.size()) {
                inventory.setItem(slot, null);
                continue;
            }
            RichChunkEntry entry = entries.get(index);
            ItemStack stack = createEntryItem(entry);
            inventory.setItem(slot, stack);
        }

        inventory.setItem(PREVIOUS_SLOT, page > 1 ? createNavItem(messages.get("town.resources.previous")) : null);
        inventory.setItem(NEXT_SLOT, page < totalPages ? createNavItem(messages.get("town.resources.next")) : null);
    }

    private ItemStack createEntryItem(RichChunkEntry entry) {
        ItemStack stack = new ItemStack(entry.type().getIcon());
        stack.setAmount(Math.min(richChunkService.getRewardAmount(), entry.type().getIcon().getMaxStackSize()));
        ItemMeta meta = stack.getItemMeta();
        meta.setDisplayName(ChatColor.AQUA + entry.type().getDisplayName().toUpperCase(Locale.ROOT) + ChatColor.YELLOW + " ЧАНК");
        meta.setLore(List.of(
                ChatColor.GRAY + messages.get("town.resources.type") + ChatColor.WHITE + " " + entry.type().getDisplayName(),
                ChatColor.GOLD + "➜ " + ChatColor.GRAY + messages.get("town.resources.coords") + ChatColor.WHITE + " X=" + entry.position().getX() + " Z=" + entry.position().getZ(),
                ChatColor.GOLD + "➜ " + ChatColor.GRAY + messages.get("town.resources.reward") + ChatColor.WHITE + " " + richChunkService.getRewardAmount() + "x " + entry.type().getDisplayName(),
                ChatColor.GOLD + "➜ " + ChatColor.GRAY + messages.get("town.resources.discovered") + ChatColor.WHITE + " " + DATE_FORMAT.format(entry.discovered())
        ));
        meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES, ItemFlag.HIDE_ENCHANTS);
        stack.setItemMeta(meta);
        return stack;
    }

    private ItemStack createNavItem(String name) {
        ItemStack stack = new ItemStack(Material.ARROW);
        ItemMeta meta = stack.getItemMeta();
        meta.setDisplayName(ChatColor.YELLOW + name);
        stack.setItemMeta(meta);
        return stack;
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        Inventory inventory = event.getInventory();
        PageState state = menus.get(inventory);
        if (state == null) {
            return;
        }
        event.setCancelled(true);
        if (event.getRawSlot() >= inventory.getSize()) {
            return;
        }
        int slot = event.getRawSlot();
        if (slot == PREVIOUS_SLOT && state.page > 1) {
            openResources((Player) event.getWhoClicked(), state.town, state.page - 1);
            return;
        }
        if (slot == NEXT_SLOT && state.page < state.totalPages) {
            openResources((Player) event.getWhoClicked(), state.town, state.page + 1);
        }
    }

    @EventHandler
    public void onInventoryClose(InventoryCloseEvent event) {
        menus.remove(event.getInventory());
    }
}
