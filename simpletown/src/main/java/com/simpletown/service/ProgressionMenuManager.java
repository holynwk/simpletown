package com.simpletown.service;

import com.simpletown.SimpleTownPlugin;
import com.simpletown.data.*;
import com.simpletown.service.PoliticalScoreService;
import com.simpletown.SimpleTownPlugin;
import com.simpletown.data.*;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.OfflinePlayer;
import net.milkbowl.vault.economy.Economy;

import java.util.*;

public class ProgressionMenuManager implements Listener {
    private static final int SCORE_AGE_UP = 35;
    private static final int SCORE_BUILDING_PURCHASE = 25;
    private enum MenuType {AGE, BUILD}

    private static class MenuHolder implements InventoryHolder {
        private final MenuType type;
        private final Town town;

        private MenuHolder(MenuType type, Town town) {
            this.type = type;
            this.town = town;
        }

        @Override
        public Inventory getInventory() {
            return null;
        }
    }

    private final TownManager townManager;
    private final ProgressionService progression;
    private final MessageService messages;
    private final SimpleTownPlugin plugin;
    private final PoliticalScoreService politicalScoreService;

    private static final Map<AgeTier, Integer> AGE_SLOTS = Map.of(
            AgeTier.AGE1, 0,
            AgeTier.AGE2, 10,
            AgeTier.AGE3, 20,
            AgeTier.AGE4, 12,
            AgeTier.AGE5, 4,
            AgeTier.AGE6, 14,
            AgeTier.AGE7, 24,
            AgeTier.AGE8, 16,
            AgeTier.AGE9, 8,
            AgeTier.AGE10, 31
    );

    private static final Map<BuildingType, Integer> BUILD_SLOTS = Map.of(
            BuildingType.WAREHOUSE, 10,
            BuildingType.UNIVERSITY, 12,
            BuildingType.LABORATORY, 13,
            BuildingType.WORKSHOP, 15,
            BuildingType.ARMORY, 16,
            BuildingType.BANK, 28,
            BuildingType.POWER_PLANT, 30,
            BuildingType.INDUSTRIAL_FACTORY, 31,
            BuildingType.MACHINE_FACTORY, 33,
            BuildingType.MILITARY_FACTORY, 34
    );

    public ProgressionMenuManager(SimpleTownPlugin plugin, TownManager townManager, ProgressionService progression, MessageService messages, PoliticalScoreService politicalScoreService) {
        this.plugin = plugin;
        this.townManager = townManager;
        this.progression = progression;
        this.messages = messages;
        this.politicalScoreService = politicalScoreService;
    }

    public void openAgeMenu(Player player, Town town) {
        Inventory inv = Bukkit.createInventory(new MenuHolder(MenuType.AGE, town), 36, "Века");
        for (AgeTier tier : AgeTier.values()) {
            ItemStack item = new ItemStack(tier.getIcon());
            ItemMeta meta = item.getItemMeta();
            meta.setDisplayName(ChatColor.GOLD + "" + ChatColor.BOLD + tier.getDisplayName());
            meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
            List<String> lore = new ArrayList<>();
            lore.add(ChatColor.DARK_GRAY + "[" + ChatColor.WHITE + "Возможности" + ChatColor.DARK_GRAY + "]" + ChatColor.GRAY + " " + tier.getDescription());
            ProgressionService.AgeRequirement req = progression.getRequirement(tier);
            lore.add(ChatColor.DARK_GRAY + "[" + ChatColor.WHITE + "Требования" + ChatColor.DARK_GRAY + "]:");
            boolean completed = town.getAgeLevel() >= tier.getLevel();
            if (tier != AgeTier.AGE1) {
                lore.add(formatRequirement("Казна: " + (int) req.getCost() + " монет", town.getBank() >= req.getCost(), completed));
                lore.add(formatRequirement("Граждане: " + req.getRequiredCitizens() + " шт.", totalMembers(town) >= req.getRequiredCitizens(), completed));
                lore.add(formatRequirement("Территории: " + req.getRequiredChunks() + " чанков", town.getChunks().size() >= req.getRequiredChunks(), completed));
                lore.add(formatRequirement("Здания: " + buildingList(req), buildingRequirementsMet(town, req), completed));
            }
            lore.add(ChatColor.DARK_GRAY + "[" + ChatColor.WHITE + "Награда" + ChatColor.DARK_GRAY + "]:");
            lore.add(ChatColor.GRAY + "- " + (req.getReward() > 0
                    ? ChatColor.YELLOW + "Мэру: " + (int) req.getReward() + " монет"
                    : ChatColor.GRAY + "нету"));
            int currentAge = town.getAgeLevel();
            if (currentAge == tier.getLevel()) {
                lore.add("");
                lore.add(ChatColor.GREEN + "Текущий век");
            } else if (currentAge > tier.getLevel()) {
                lore.add("");
                lore.add(ChatColor.GRAY + "Пройден");
            } else if (currentAge + 1 == tier.getLevel()) {
                lore.add("");
                lore.add(ChatColor.GREEN + "Нажмите ЛКМ чтобы перейти в следующий век");
            } else {
                lore.add("");
                lore.add(ChatColor.RED + "Недоступно");
            }
            meta.setLore(lore);
            item.setItemMeta(meta);
            Integer slot = AGE_SLOTS.get(tier);
            if (slot != null) {
                inv.setItem(slot, item);
            }
        }
        player.openInventory(inv);
    }

    public void openBuildMenu(Player player, Town town) {
        Inventory inv = Bukkit.createInventory(new MenuHolder(MenuType.BUILD, town), 45, "Городские здания");
        for (BuildingType type : BuildingType.values()) {
            int level = town.getBuildingLevel(type);
            ItemStack item = new ItemStack(resolveIcon(type, level));
            ItemMeta meta = item.getItemMeta();
            meta.setDisplayName(ChatColor.WHITE + "" + ChatColor.BOLD + type.getDisplayName());
            meta.setLocalizedName(type.name());
            meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
            List<String> lore = new ArrayList<>();
            int maxLevel = type.getMaxLevel();
            int targetLevel = type.isMultiLevel() ? Math.min(maxLevel, level + 1) : 1;
            lore.add(ChatColor.DARK_GRAY + "[" + ChatColor.WHITE + "Уровень" + ChatColor.DARK_GRAY + "]:" + ChatColor.GRAY + " " + level + "/" + maxLevel);
            lore.add(ChatColor.DARK_GRAY + "[" + ChatColor.WHITE + "Возможности" + ChatColor.DARK_GRAY + "]" + ChatColor.GRAY + " " + buildDescription(type, targetLevel));
            lore.add(ChatColor.DARK_GRAY + "[" + ChatColor.WHITE + "Требования" + ChatColor.DARK_GRAY + "]:");
            AgeTier unlockAge = unlockAge(type, targetLevel);
            lore.add(ChatColor.DARK_GRAY + "- " + statusColor(town.getAgeLevel() >= unlockAge.getLevel()) + "Век: " + unlockAge.getDisplayName());
            double cost = progression.getBuildingCost(type, targetLevel).getCost();
            lore.add(ChatColor.DARK_GRAY + "- " + statusColor(town.getBank() >= cost) + "Казна: " + (int) cost + " монет");
            lore.add(ChatColor.DARK_GRAY + "- " + statusColor(dependenciesMet(town, type, targetLevel)) + "Здания: " + dependencyList(type, targetLevel));
            if (type.isMultiLevel() && level >= maxLevel) {
                lore.add("");
                lore.add(ChatColor.RED + "Недоступно");
            } else if (town.getAgeLevel() < unlockAge.getLevel()) {
                lore.add("");
                lore.add(ChatColor.RED + "Недоступно");
            } else {
                lore.add("");
                lore.add(ChatColor.GREEN + "Нажмите ЛКМ чтобы приобрести здание");
            }
            meta.setLore(lore);
            item.setItemMeta(meta);
            int slot = BUILD_SLOTS.getOrDefault(type, 4);
            inv.setItem(slot, item);
        }
        player.openInventory(inv);
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getInventory().getHolder() instanceof MenuHolder holder)) {
            return;
        }
        if (event.getClickedInventory() != null && event.getClickedInventory().getHolder() instanceof MenuHolder) {
            event.setCancelled(true);
        } else if (event.isShiftClick()) {
            event.setCancelled(true);
            return;
        } else {
            return;
        }
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }
        Town town = holder.town;
        if (!town.isMayor(player.getName())) {
            messages.sendError(player, "town.not-mayor");
            player.closeInventory();
            return;
        }
        ItemStack clicked = event.getCurrentItem();
        if (clicked == null || clicked.getType() == Material.AIR) {
            return;
        }

        if (holder.type == MenuType.AGE) {
            handleAgeClick(player, town, clicked.getType());
        } else {
            handleBuildClick(player, town, clicked);
        }
    }

    private void handleAgeClick(Player player, Town town, Material material) {
        AgeTier target = Arrays.stream(AgeTier.values())
                .filter(t -> t.getIcon() == material)
                .findFirst()
                .orElse(null);
        if (target == null) {
            return;
        }
        int current = town.getAgeLevel();
        if (target.getLevel() != current + 1) {
            return;
        }
        ProgressionService.AgeRequirement req = progression.getRequirement(target);
        if (town.getChunks().size() < req.getRequiredChunks()) {
            messages.sendError(player, "town.age.chunks", Map.of("required", String.valueOf(req.getRequiredChunks())));
            return;
        }
        int members = totalMembers(town);
        if (members < req.getRequiredCitizens()) {
            messages.sendError(player, "town.age.citizens", Map.of("required", String.valueOf(req.getRequiredCitizens())));
            return;
        }
        for (Map.Entry<BuildingType, Integer> entry : req.getRequiredBuildings().entrySet()) {
            if (town.getBuildingLevel(entry.getKey()) < entry.getValue()) {
                messages.sendError(player, "town.age.building", Map.of("building", entry.getKey().getDisplayName()));
                return;
            }
        }
        if (town.getBank() < req.getCost()) {
            messages.sendError(player, "town.age.money", Map.of("cost", String.valueOf((int) req.getCost())));
            return;
        }
        if (!townManager.withdraw(town, req.getCost())) {
            messages.sendError(player, "town.age.money", Map.of("cost", String.valueOf((int) req.getCost())));
            return;
        }
        townManager.setAge(town, target.getLevel());
        payReward(player, town, req.getReward());
        messages.send(player, "town.age.success", Map.of("age", target.getDisplayName(), "reward", String.valueOf((int) req.getReward())));
        politicalScoreService.addScore(player.getUniqueId(), SCORE_AGE_UP);
        player.closeInventory();
    }

    private void handleBuildClick(Player player, Town town, ItemStack clicked) {
        ItemMeta meta = clicked != null ? clicked.getItemMeta() : null;
        BuildingType type = null;
        if (meta != null && meta.getLocalizedName() != null) {
            try {
                type = BuildingType.valueOf(meta.getLocalizedName());
            } catch (IllegalArgumentException ignored) {
            }
        }
        if (type == null && clicked != null) {
            Material material = clicked.getType();
            type = Arrays.stream(BuildingType.values())
                    .filter(t -> resolveIcon(t, 0) == material || t.getBaseIcon() == material)
                    .findFirst()
                    .orElse(null);
        }
        if (type == null) {
            return;
        }
        int level = town.getBuildingLevel(type);
        int targetLevel = type.isMultiLevel() ? level + 1 : 1;
        if (town.getAgeLevel() < unlockAge(type, targetLevel).getLevel()) {
            messages.sendError(player, "town.build.age");
            return;
        }
        for (BuildingType dep : dependencies(type, targetLevel)) {
            if (town.getBuildingLevel(dep) <= 0) {
                messages.sendError(player, "town.build.dep", Map.of("building", dep.getDisplayName()));
                return;
            }
        }
        if (type.isMultiLevel() && level >= type.getMaxLevel()) {
            return;
        }
        double cost = progression.getBuildingCost(type, targetLevel).getCost();
        if (town.getBank() < cost) {
            messages.sendError(player, "town.build.money", Map.of("cost", String.valueOf((int) cost)));
            return;
        }
        if (!townManager.withdraw(town, cost)) {
            messages.sendError(player, "town.build.money", Map.of("cost", String.valueOf((int) cost)));
            return;
        }
        townManager.setBuildingLevel(town, type, targetLevel);
        messages.send(player, "town.build.success", Map.of("building", type.getDisplayName(), "level", String.valueOf(targetLevel)));
        politicalScoreService.addScore(player.getUniqueId(), SCORE_BUILDING_PURCHASE);
        player.closeInventory();
    }

    private ChatColor statusColor(boolean ok) {
        return ok ? ChatColor.GREEN : ChatColor.RED;
    }

    private String formatRequirement(String text, boolean met, boolean completed) {
        if (completed) {
            return ChatColor.GRAY + "- " + ChatColor.GRAY + "" + ChatColor.STRIKETHROUGH + text;
        }
        return ChatColor.GRAY + "- " + statusColor(met) + text;
    }

    private int totalMembers(Town town) {
        return town.getCitizens().size() + 1;
    }

    private boolean buildingRequirementsMet(Town town, ProgressionService.AgeRequirement req) {
        for (Map.Entry<BuildingType, Integer> entry : req.getRequiredBuildings().entrySet()) {
            if (town.getBuildingLevel(entry.getKey()) < entry.getValue()) {
                return false;
            }
        }
        return true;
    }

    private String buildingList(ProgressionService.AgeRequirement req) {
        if (req.getRequiredBuildings().isEmpty()) {
            return "нет";
        }
        List<String> names = new ArrayList<>();
        for (Map.Entry<BuildingType, Integer> entry : req.getRequiredBuildings().entrySet()) {
            names.add(entry.getKey().getDisplayName() + " " + entry.getValue() + " ур.");
        }
        return String.join(", ", names);
    }

    private boolean dependenciesMet(Town town, BuildingType type, int targetLevel) {
        for (BuildingType dep : dependencies(type, targetLevel)) {
            if (town.getBuildingLevel(dep) <= 0) {
                return false;
            }
        }
        return true;
    }

    private String dependencyList(BuildingType type, int targetLevel) {
        Set<BuildingType> deps = dependencies(type, targetLevel);
        if (deps.isEmpty()) {
            return "нет";
        }
        List<String> list = new ArrayList<>();
        for (BuildingType dep : deps) {
            list.add(dep.getDisplayName());
        }
        return String.join(", ", list);
    }

    private String buildDescription(BuildingType type, int level) {
        return switch (type) {
            case WAREHOUSE -> "Расширяет склад города для /town inv.";
            case BANK -> "Прибавляет ежедневный доход в казну.";
            case WORKSHOP -> workshopDescription(level);
            case ARMORY -> armoryDescription(level);
            case UNIVERSITY -> "Позволяет покупать Лабораторию и Электростанцию.";
            case POWER_PLANT -> "Обеспечивает фабрики и лабораторию энергией.";
            case LABORATORY -> "Позволяет варить зелья и зачаровывать предметы.";
            case INDUSTRIAL_FACTORY -> "Создает базу для промышленных зданий.";
            case MACHINE_FACTORY -> "Открывает гражданский транспорт в /town vehicles.";
            case MILITARY_FACTORY -> "Открывает военный транспорт в /town vehicles.";
        };
    }

    private String workshopDescription(int level) {
        int normalized = Math.max(level, 1);
        return switch (normalized) {
            case 1 -> "Каменный станок: каменные инструменты без мечей и брони, каменные и полированные блоки, " +
                    "кирпичи, песчаник, светокамень и базовые механизмы (печь, точило, раздатчик, выбрасыватель, " +
                    "повторитель, компаратор, наблюдатель, рычаг).";
            case 2 -> "Железный станок: железные инструменты без мечей, железные блоки, ведра, вагонетки и рельсы, " +
                    "компас, воронка, котел, поршень, наковальня, стол кузнеца, плавильная печь, крюк, прутья, " +
                    "железная плита, цепи, фонарь, камнерез, огниво, железные двери и люки.";
            case 3 -> "Золотой станок: золотые инструменты без мечей и брони, золотые блоки, часы, золотая морковь " +
                    "и золотые яблоки.";
            case 4 -> "Алмазный станок: алмазные инструменты без мечей и брони, алмазные блоки.";
            default -> "Незеритовый станок: незеритовые инструменты без мечей и брони, незеритовые блоки и слитки.";
        };
    }

    private String armoryDescription(int level) {
        int normalized = Math.max(level, 1);
        return switch (normalized) {
            case 1 -> "Каменная оружейная: каменные мечи и кожаная броня.";
            case 2 -> "Железная оружейная: железные мечи, щиты и железная броня.";
            case 3 -> "Золотая оружейная: золотые мечи и золотая броня.";
            case 4 -> "Алмазная оружейная: алмазные мечи и алмазная броня.";
            default -> "Незеритовая оружейная: незеритовые мечи и незеритовая броня.";
        };
    }

    private void payReward(Player player, Town town, double reward) {
        if (reward <= 0 || !plugin.ensureEconomy()) {
            return;
        }
        Economy economy = plugin.getEconomy();
        if (economy == null) {
            return;
        }
        OfflinePlayer mayor = Bukkit.getOfflinePlayer(town.getMayor());
        economy.depositPlayer(mayor, reward);
        if (mayor.isOnline() && mayor.getPlayer() != null && !mayor.getPlayer().equals(player)) {
            String ageName = AgeTier.byLevel(town.getAgeLevel()).getDisplayName();
            messages.send(mayor.getPlayer(), "town.age.success", Map.of("age", ageName, "reward", String.valueOf((int) reward)));
        }
    }

    private AgeTier unlockAge(BuildingType type, int nextLevel) {
        return switch (type) {
            case WAREHOUSE -> switch (nextLevel) {
                case 1 -> AgeTier.AGE1;
                case 2 -> AgeTier.AGE3;
                default -> AgeTier.AGE7;
            };
            case BANK -> switch (nextLevel) {
                case 1 -> AgeTier.AGE5;
                case 2 -> AgeTier.AGE8;
                default -> AgeTier.AGE9;
            };
            case WORKSHOP -> switch (nextLevel) {
                case 1 -> AgeTier.AGE1;
                case 2 -> AgeTier.AGE3;
                case 3 -> AgeTier.AGE4;
                case 4 -> AgeTier.AGE5;
                case 5 -> AgeTier.AGE7;
                default -> AgeTier.AGE9;
            };
            case ARMORY -> switch (nextLevel) {
                case 1 -> AgeTier.AGE2;
                case 2 -> AgeTier.AGE3;
                case 3 -> AgeTier.AGE5;
                case 4 -> AgeTier.AGE7;
                default -> AgeTier.AGE9;
            };
            case LABORATORY, UNIVERSITY, POWER_PLANT -> AgeTier.AGE6;
            case INDUSTRIAL_FACTORY, MACHINE_FACTORY -> AgeTier.AGE8;
            case MILITARY_FACTORY -> AgeTier.AGE10;
        };
    }

    private Set<BuildingType> dependencies(BuildingType type, int targetLevel) {
        return switch (type) {
            case WORKSHOP, WAREHOUSE, BANK -> Collections.emptySet();
            case ARMORY -> targetLevel >= 4 ? Set.of(BuildingType.UNIVERSITY, BuildingType.POWER_PLANT) : Collections.emptySet();
            case INDUSTRIAL_FACTORY, LABORATORY -> Set.of(BuildingType.UNIVERSITY, BuildingType.POWER_PLANT);
            case MACHINE_FACTORY -> Set.of(BuildingType.UNIVERSITY, BuildingType.POWER_PLANT, BuildingType.INDUSTRIAL_FACTORY);
            case MILITARY_FACTORY -> Set.of(BuildingType.UNIVERSITY, BuildingType.POWER_PLANT, BuildingType.INDUSTRIAL_FACTORY, BuildingType.MACHINE_FACTORY);
            case UNIVERSITY, POWER_PLANT -> Collections.emptySet();
        };
    }

    private Material resolveIcon(BuildingType type, int level) {
        return switch (type) {
            case WORKSHOP -> workshopIcon(level);
            case ARMORY -> armoryIcon(level);
            default -> type.getBaseIcon();
        };
    }

    private Material workshopIcon(int level) {
        return switch (Math.min(Math.max(level + 1, 1), 6)) {
            case 1 -> Material.STONECUTTER;
            case 2 -> Material.IRON_BLOCK;
            case 3 -> Material.GOLD_BLOCK;
            case 4 -> Material.DIAMOND_BLOCK;
            default -> Material.NETHERITE_BLOCK;
        };
    }

    private Material armoryIcon(int level) {
        return switch (Math.min(Math.max(level + 1, 1), 5)) {
            case 1 -> Material.STONE_SWORD;
            case 2 -> Material.IRON_SWORD;
            case 3 -> Material.GOLDEN_SWORD;
            case 4 -> Material.DIAMOND_SWORD;
            default -> Material.NETHERITE_SWORD;
        };
    }

    private String currentLevelLabel(BuildingType type, int level) {
        int normalized = Math.max(level, 1);
        return switch (type) {
            case WORKSHOP -> switch (normalized) {
                case 1 -> "Каменный станок";
                case 2 -> "Железный станок";
                case 3 -> "Золотой станок";
                case 4 -> "Алмазный станок";
                default -> "Незеритовый станок";
            };
            case ARMORY -> switch (normalized) {
                case 1 -> "Каменная оружейная";
                case 2 -> "Железная оружейная";
                case 3 -> "Золотая оружейная";
                case 4 -> "Алмазная оружейная";
                default -> "Незеритовая оружейная";
            };
            case WAREHOUSE -> "Склад";
            case BANK -> "Банк";
            case UNIVERSITY -> "Университет";
            case POWER_PLANT -> "Электростанция";
            case LABORATORY -> "Лаборатория";
            case INDUSTRIAL_FACTORY -> "Промышленный Завод";
            case MACHINE_FACTORY -> "Машинный Завод";
            case MILITARY_FACTORY -> "Военный Завод";
        };
    }
}