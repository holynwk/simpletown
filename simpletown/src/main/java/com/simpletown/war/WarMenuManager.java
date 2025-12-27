package com.simpletown.war;

import com.simpletown.SimpleTownPlugin;
import com.simpletown.data.Town;
import com.simpletown.data.TownManager;
import com.simpletown.service.MessageService;
import com.simpletown.service.PoliticalScoreService;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.*;

public class WarMenuManager implements Listener {
    private static final String MAIN_TITLE = ChatColor.DARK_RED + "Война";
    private static final String TOWN_LIST_TITLE = ChatColor.DARK_RED + "Война с городом";
    private static final String TYPE_TITLE = ChatColor.DARK_RED + "Выбор типа";
    private static final String RESULT_TITLE = ChatColor.DARK_RED + "Исход войны";

    private final SimpleTownPlugin plugin;
    private final TownManager townManager;
    private final MessageService messages;
    private final PoliticalScoreService politicalScoreService;
    private final WarManager warManager;
    private final Map<UUID, Town> pendingTargets = new HashMap<>();
    private final Set<UUID> pendingResults = new HashSet<>();

    public WarMenuManager(SimpleTownPlugin plugin, TownManager townManager, MessageService messages, PoliticalScoreService politicalScoreService, WarManager warManager) {
        this.plugin = plugin;
        this.townManager = townManager;
        this.messages = messages;
        this.politicalScoreService = politicalScoreService;
        this.warManager = warManager;
    }

    public void openMain(Player player) {
        Inventory inventory = Bukkit.createInventory(null, 27, MAIN_TITLE);
        inventory.setItem(12, named(Material.CROSSBOW, ChatColor.RED + "Война с городом"));
        inventory.setItem(14, named(Material.BARRIER, ChatColor.DARK_GRAY + "Война со страной"));
        player.openInventory(inventory);
    }

    public void openResultMenu(Player player) {
        Inventory inventory = Bukkit.createInventory(null, 27, RESULT_TITLE);
        ItemStack claimAll = named(Material.NETHER_STAR, ChatColor.GOLD + "Забрать все территории",
                ChatColor.GRAY + "Удалить проигравший город",
                ChatColor.GRAY + "Передать его чанки и 25% казны");
        inventory.setItem(13, claimAll);
        pendingResults.add(player.getUniqueId());
        player.openInventory(inventory);
    }

    private ItemStack named(Material material, String name, String... lore) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(name);
            if (lore != null && lore.length > 0) {
                meta.setLore(Arrays.asList(lore));
            }
            item.setItemMeta(meta);
        }
        return item;
    }

    private void openTownList(Player player) {
        Town attacker = townManager.getTownByMayor(player.getName());
        if (attacker == null) {
            messages.sendError(player, "war.only-mayor");
            return;
        }
        Inventory inventory = Bukkit.createInventory(null, 54, TOWN_LIST_TITLE);
        for (Town town : townManager.getTowns()) {
            if (town.equals(attacker)) {
                continue;
            }
            int residents = town.getCitizens().size() + 1;
            ItemStack banner = named(Material.WHITE_BANNER, ChatColor.GOLD + town.getName(),
                    ChatColor.GRAY + "Мэр: " + ChatColor.YELLOW + town.getMayor(),
                    ChatColor.GRAY + "Жители: " + ChatColor.YELLOW + residents,
                    ChatColor.GRAY + "Территории: " + ChatColor.YELLOW + town.getChunks().size(),
                    ChatColor.GRAY + "Казна: " + ChatColor.YELLOW + formatBank(town.getBank()) + "$"
            );
            inventory.addItem(banner);
        }
        player.openInventory(inventory);
    }

    private void openTypeSelector(Player player, Town target) {
        pendingTargets.put(player.getUniqueId(), target);
        Inventory inventory = Bukkit.createInventory(null, 27, TYPE_TITLE);
        ItemStack war = named(Material.NETHERITE_SWORD, ChatColor.DARK_RED + "Война", ChatColor.GRAY + "ЛКМ - полит. очки", ChatColor.GRAY + "ПКМ - монеты");
        inventory.setItem(13, war);
        player.openInventory(inventory);
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }
        String title = event.getView().getTitle();
        if (!title.equals(MAIN_TITLE) && !title.equals(TOWN_LIST_TITLE) && !title.equals(TYPE_TITLE) && !title.equals(RESULT_TITLE)) {
            return;
        }
        event.setCancelled(true);
        if (title.equals(MAIN_TITLE)) {
            handleMainMenu(player, event.getRawSlot());
        } else if (title.equals(TOWN_LIST_TITLE)) {
            handleTownList(player, event.getCurrentItem());
        } else if (title.equals(TYPE_TITLE)) {
            handleTypeSelection(player, event.getRawSlot(), event.isRightClick());
        } else if (title.equals(RESULT_TITLE)) {
            handleResultSelection(player, event.getRawSlot());
        }
    }

    @EventHandler
    public void onInventoryClose(InventoryCloseEvent event) {
        if (RESULT_TITLE.equals(event.getView().getTitle())) {
            pendingResults.remove(event.getPlayer().getUniqueId());
        }
    }

    private void handleMainMenu(Player player, int slot) {
        if (slot == 12) {
            openTownList(player);
        }
    }

    private void handleTownList(Player player, ItemStack clicked) {
        if (clicked == null || !clicked.hasItemMeta() || !clicked.getItemMeta().hasDisplayName()) {
            return;
        }
        Town target = townManager.getTownByName(ChatColor.stripColor(clicked.getItemMeta().getDisplayName()));
        Town attacker = townManager.getTownByMayor(player.getName());
        if (attacker == null || target == null) {
            messages.sendError(player, "war.only-mayor");
            return;
        }
        if (!attacker.isMayor(player.getName())) {
            messages.sendError(player, "war.only-mayor");
            return;
        }
        openTypeSelector(player, target);
    }

    private void handleTypeSelection(Player player, int slot, boolean rightClick) {
        Town target = pendingTargets.get(player.getUniqueId());
        Town attacker = townManager.getTownByMayor(player.getName());
        if (attacker == null || target == null) {
            player.closeInventory();
            return;
        }
        if (slot == 13) {
            if (rightClick) {
                if (warManager.startWarWithMoney(player, attacker, target, plugin.getEconomy())) {
                    messages.send(player, "war.war-started", Map.of("target", target.getName()));
                    player.closeInventory();
                }
            } else {
                int points = politicalScoreService.getScore(player.getUniqueId());
                int cost = warManager.getWarPoliticalCost();
                if (points < cost) {
                    messages.sendError(player, "war.not-enough-points");
                    return;
                }
                if (warManager.startWarWithPolitics(player, attacker, target, points, () -> politicalScoreService.spendScore(player.getUniqueId(), cost))) {
                    messages.send(player, "war.war-started", Map.of("target", target.getName()));
                    player.closeInventory();
                }
            }
        }
    }

    private String formatBank(double amount) {
        return String.format(Locale.US, "%.2f", amount);
    }

    private void handleResultSelection(Player player, int slot) {
        if (slot != 13 || !pendingResults.contains(player.getUniqueId())) {
            return;
        }
        pendingResults.remove(player.getUniqueId());
        if (warManager.finishWithResult(player)) {
            player.closeInventory();
        }
    }
}