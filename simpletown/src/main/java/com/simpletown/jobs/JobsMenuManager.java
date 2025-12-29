
package com.simpletown.jobs;

import com.simpletown.service.MessageService;
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
import java.util.UUID;

public class JobsMenuManager implements Listener {
    private final JobsService jobsService;
    private final JobsManager jobsManager;
    private final MessageService messages;
    private final Map<Inventory, Map<Integer, JobType>> menus = new HashMap<>();

    public JobsMenuManager(JobsService jobsService, JobsManager jobsManager, MessageService messages) {
        this.jobsService = jobsService;
        this.jobsManager = jobsManager;
        this.messages = messages;
    }

    public void openMenu(Player player) {
        Inventory inventory = Bukkit.createInventory(null, 27, messages.get("jobs.menu.title"));
        Map<Integer, JobType> entries = new HashMap<>();
        int slot = 11;
        for (JobType type : JobType.values()) {
            if (type == JobType.UNEMPLOYED) {
                continue;
            }
            ItemStack item = createJobItem(player, type);
            inventory.setItem(slot, item);
            entries.put(slot, type);
            slot++;
            if ((slot + 1) % 9 == 0) {
                slot += 2;
            }
        }
        inventory.setItem(22, createJobItem(player, JobType.UNEMPLOYED));
        entries.put(22, JobType.UNEMPLOYED);
        menus.put(inventory, entries);
        player.openInventory(inventory);
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        Map<Integer, JobType> entries = menus.get(event.getInventory());
        if (entries == null) {
            return;
        }
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }
        JobType type = entries.get(event.getRawSlot());
        if (type == null) {
            return;
        }
        JobType currentJob = jobsService.getJob(player);
        if (currentJob == type) {
            if (currentJob == JobType.UNEMPLOYED) {
                messages.send(player, "jobs.unemployed-already");
            } else {
                messages.send(player, "jobs.already-working");
            }
            return;
        }
        if (type == JobType.KILLER) {
            KillerStatus status = jobsManager.getKillerStatus(player.getUniqueId());
            if (status != null && status.isSuspended(System.currentTimeMillis())) {
                messages.send(player, "jobs.killer.suspended");
                return;
            }
            if (!jobsService.canTakeKillerJob(player)) {
                messages.send(player, "jobs.killer.unavailable");
                return;
            }
        }
        if (type == JobType.UNEMPLOYED) {
            jobsService.setJob(player, type);
            messages.send(player, "jobs.fired");
            player.closeInventory();
            return;
        }
        jobsService.setJob(player, type);
        if (type == JobType.KILLER) {
            jobsService.assignKillerTarget(player);
        }
        messages.send(player, "jobs.selected", Map.of("job", messages.get(type.getMessageKey())));
        player.closeInventory();
    }

    @EventHandler
    public void onInventoryClose(InventoryCloseEvent event) {
        menus.remove(event.getInventory());
    }

    private ItemStack createJobItem(Player player, JobType type) {
        ItemStack stack = new ItemStack(type.getIcon() == null ? Material.BARRIER : type.getIcon());
        ItemMeta meta = stack.getItemMeta();
        meta.setDisplayName(ChatColor.GOLD + messages.get(type.getMessageKey()));
        meta.setLore(getJobLore(type));
        if (jobsService.getJob(player) == type) {
            meta.addEnchant(org.bukkit.enchantments.Enchantment.DURABILITY, 1, true);
            meta.addItemFlags(ItemFlag.HIDE_ENCHANTS);
        }
        stack.setItemMeta(meta);
        return stack;
    }

    private List<String> getJobLore(JobType type) {
        return switch (type) {
            case LUMBERJACK -> messages.getList("jobs.lore.lumberjack");
            case MINER -> messages.getList("jobs.lore.miner");
            case FARMER -> messages.getList("jobs.lore.farmer");
            case BUTCHER -> messages.getList("jobs.lore.butcher");
            case KILLER -> messages.getList("jobs.lore.killer");
            case UNEMPLOYED -> messages.getList("jobs.lore.unemployed");
        };
    }
}