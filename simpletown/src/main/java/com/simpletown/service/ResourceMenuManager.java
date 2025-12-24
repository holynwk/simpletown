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
import com.simpletown.data.TownManager;

import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ResourceMenuManager implements Listener {
    private static final int INVENTORY_SIZE = 54;
    private static final int PAGE_SIZE = 45;
    private static final int PREVIOUS_SLOT = 45;
    private static final int NEXT_SLOT = 53;
    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("dd.MM.yyyy");

    private record PageState(Town town, List<RichChunkEntry> entries, int page, int totalPages) {}

    private final RichChunkService richChunkService;
    private final MessageService messages;
    private final TownManager townManager;
    private final Map<Inventory, PageState> menus = new HashMap<>();

    public ResourceMenuManager(RichChunkService richChunkService, MessageService messages, TownManager townManager) {
        this.richChunkService = richChunkService;
        this.messages = messages;
        this.townManager = townManager;
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
        ItemStack stack = new ItemStack(entry.getType().getIcon());
        stack.setAmount(Math.min(richChunkService.getRewardAmount(), entry.getType().getIcon().getMaxStackSize()));
        ItemMeta meta = stack.getItemMeta();
        ChatColor color = entry.getType().getColor();
        boolean canClaim = richChunkService.canClaim(entry);
        meta.setDisplayName(color + "" + ChatColor.BOLD + entry.getType().getDisplayName());
        List<String> lore = new ArrayList<>();
        lore.add(ChatColor.DARK_GRAY + "- " + ChatColor.GRAY + messages.get("town.resources.type") + ChatColor.WHITE + " " + entry.getType().getDisplayName());
        lore.add(ChatColor.DARK_GRAY + "- " + ChatColor.GRAY + messages.get("town.resources.coords") + ChatColor.WHITE + " X=" + entry.getPosition().getX() + " Z=" + entry.getPosition().getZ());
        lore.add(ChatColor.DARK_GRAY + "- " + ChatColor.GRAY + messages.get("town.resources.reward") + ChatColor.WHITE + " " + richChunkService.getRewardAmount() + "x " + entry.getType().getRewardLabel());
        lore.add(ChatColor.DARK_GRAY + "- " + ChatColor.GRAY + messages.get("town.resources.discovered") + ChatColor.WHITE + " " + DATE_FORMAT.format(entry.getDiscovered()));
        lore.add(canClaim ? ChatColor.GREEN + "ПКМ Забрать добычу" : ChatColor.RED + "ПКМ Забрать добычу");
        meta.setLore(lore);
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
            return;
        }

        if (slot < PAGE_SIZE && event.isRightClick()) {
            int index = (state.page - 1) * PAGE_SIZE + slot;
            if (index < state.entries.size()) {
                RichChunkEntry entry = state.entries.get(index);
                handleClaim((Player) event.getWhoClicked(), state.town, entry);
                openResources((Player) event.getWhoClicked(), state.town, state.page);
            }
        }
    }

    @EventHandler
    public void onInventoryClose(InventoryCloseEvent event) {
        menus.remove(event.getInventory());
    }

    private void handleClaim(Player player, Town town, RichChunkEntry entry) {
        if (!town.isMayor(player.getName())) {
            messages.sendError(player, "town.not-mayor");
            return;
        }
        if (!richChunkService.canClaim(entry)) {
            messages.sendError(player, "town.resources.unavailable");
            return;
        }
        if (!richChunkService.claimReward(town, entry, townManager)) {
            messages.sendError(player, "town.resources.inventory-full");
            return;
        }
        messages.send(player, "town.resources.claimed", Map.of(
                "type", entry.getType().getDisplayName(),
                "x", String.valueOf(entry.getPosition().getX()),
                "z", String.valueOf(entry.getPosition().getZ()),
                "amount", String.valueOf(richChunkService.getRewardAmount())
        ));
    }
}