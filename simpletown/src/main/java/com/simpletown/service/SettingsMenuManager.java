package com.simpletown.service;

import com.simpletown.data.Town;
import com.simpletown.data.TownFlag;
import com.simpletown.data.TownManager;
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

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

public class SettingsMenuManager implements Listener {
    private record SettingEntry(String name, Material icon, BooleanSupplier getter, Consumer<Boolean> setter, Runnable afterChange, boolean editable) {}

    private final TownManager townManager;
    private final MessageService messages;
    private final Map<Inventory, Map<Integer, SettingEntry>> menus = new HashMap<>();

    public SettingsMenuManager(TownManager townManager, MessageService messages) {
        this.townManager = townManager;
        this.messages = messages;
    }

    public void openCitizenSettings(Player player, Town town, boolean adminOverride) {
        boolean editable = adminOverride || town.isMayor(player.getName());
        Inventory inventory = createInventory(messages.format("settings.title.citizens", Map.of("town", town.getName())));
        Map<Integer, SettingEntry> entries = new HashMap<>();
        int slot = 1;
        for (TownFlag flag : TownFlag.values()) {
            entries.put(slot, new SettingEntry(
                    flag.getDisplayName(),
                    flag.getIcon(),
                    () -> town.getCitizenFlags().isEnabled(flag),
                    value -> town.getCitizenFlags().set(flag, value),
                    townManager::save,
                    editable
            ));
            inventory.setItem(slot, createSettingItem(flag.getDisplayName(), flag.getIcon(), town.getCitizenFlags().isEnabled(flag)));
            slot++;
        }
        entries.put(slot, new SettingEntry(
                messages.get("settings.label.open"),
                Material.OAK_DOOR,
                town::isOpen,
                town::setOpen,
                townManager::save,
                editable
        ));
        inventory.setItem(slot, createSettingItem(messages.get("settings.label.open"), Material.OAK_DOOR, town.isOpen()));
        inventory.setItem(0, createLabel(messages.get("settings.label.citizens")));
        menus.put(inventory, entries);
        player.openInventory(inventory);
    }

    public void openOutsiderSettings(Player player, Town town) {
        Inventory inventory = createInventory(messages.format("settings.title.outsiders", Map.of("town", town.getName())));
        Map<Integer, SettingEntry> entries = new HashMap<>();
        int slot = 1;
        for (TownFlag flag : TownFlag.values()) {
            entries.put(slot, new SettingEntry(
                    flag.getDisplayName(),
                    flag.getIcon(),
                    () -> town.getOutsiderFlags().isEnabled(flag),
                    value -> town.getOutsiderFlags().set(flag, value),
                    townManager::save,
                    true
            ));
            inventory.setItem(slot, createSettingItem(flag.getDisplayName(), flag.getIcon(), town.getOutsiderFlags().isEnabled(flag)));
            slot++;
        }
        inventory.setItem(0, createLabel(messages.get("settings.label.outsiders")));
        menus.put(inventory, entries);
        player.openInventory(inventory);
    }

    public void openDefaultOutsiderSettings(Player player) {
        Inventory inventory = createInventory(messages.get("settings.title.defaults"));
        Map<Integer, SettingEntry> entries = new HashMap<>();
        int slot = 1;
        for (TownFlag flag : TownFlag.values()) {
            boolean enabled = townManager.isDefaultFlagEnabled(flag, false);
            entries.put(slot, new SettingEntry(
                    flag.getDisplayName(),
                    flag.getIcon(),
                    () -> townManager.isDefaultFlagEnabled(flag, false),
                    value -> townManager.setDefaultFlag(flag, false, value),
                    () -> {},
                    true
            ));
            inventory.setItem(slot, createSettingItem(flag.getDisplayName(), flag.getIcon(), enabled));
            slot++;
        }
        inventory.setItem(0, createLabel(messages.get("settings.label.defaults")));
        menus.put(inventory, entries);
        player.openInventory(inventory);
    }

    private Inventory createInventory(String title) {
        return Bukkit.createInventory(null, 9, title);
    }

    private ItemStack createSettingItem(String name, Material icon, boolean enabled) {
        ItemStack stack = new ItemStack(icon);
        ItemMeta meta = stack.getItemMeta();
        meta.setDisplayName(ChatColor.DARK_PURPLE + name);
        meta.setLore(List.of(enabled ? messages.get("settings.state.enabled") : messages.get("settings.state.disabled")));
        meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES, ItemFlag.HIDE_ENCHANTS);
        stack.setItemMeta(meta);
        return stack;
    }

    private ItemStack createLabel(String name) {
        ItemStack pane = new ItemStack(Material.BLUE_STAINED_GLASS_PANE);
        ItemMeta meta = pane.getItemMeta();
        meta.setDisplayName(ChatColor.AQUA + name);
        pane.setItemMeta(meta);
        return pane;
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        Inventory inventory = event.getInventory();
        Map<Integer, SettingEntry> entries = menus.get(inventory);
        if (entries == null) {
            return;
        }
        event.setCancelled(true);
        SettingEntry entry = entries.get(event.getRawSlot());
        if (entry == null || !entry.editable()) {
            return;
        }
        boolean newValue = !entry.getter().getAsBoolean();
        entry.setter().accept(newValue);
        entry.afterChange().run();
        inventory.setItem(event.getRawSlot(), createSettingItem(entry.name(), entry.icon(), newValue));
    }

    @EventHandler
    public void onInventoryClose(InventoryCloseEvent event) {
        menus.remove(event.getInventory());
    }
}