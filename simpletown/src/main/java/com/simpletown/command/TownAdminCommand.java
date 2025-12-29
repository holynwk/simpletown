package com.simpletown.command;

import com.simpletown.data.AgeTier;
import com.simpletown.data.BuildingType;
import com.simpletown.data.Town;
import com.simpletown.data.TownManager;
import com.simpletown.service.MessageService;
import com.simpletown.service.SettingsMenuManager;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import com.simpletown.service.*;
import org.bukkit.plugin.Plugin;
import com.simpletown.war.WarManager;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class TownAdminCommand implements CommandExecutor, TabCompleter {
    private final TownManager townManager;
    private final MessageService messages;
    private final SettingsMenuManager settingsMenuManager;
    private final RichChunkService richChunkService;
    private final MapService mapService;
    private final Plugin plugin;
    private final PoliticalScoreService politicalScoreService;
    private final WarManager warManager;

    private static final Map<String, BuildingType> BUILDING_ALIASES = Map.ofEntries(
            Map.entry("sklad", BuildingType.WAREHOUSE),
            Map.entry("warehouse", BuildingType.WAREHOUSE),
            Map.entry("bank", BuildingType.BANK),
            Map.entry("stanok", BuildingType.WORKSHOP),
            Map.entry("workshop", BuildingType.WORKSHOP),
            Map.entry("oruzheina", BuildingType.ARMORY),
            Map.entry("armory", BuildingType.ARMORY),
            Map.entry("univer", BuildingType.UNIVERSITY),
            Map.entry("university", BuildingType.UNIVERSITY),
            Map.entry("power", BuildingType.POWER_PLANT),
            Map.entry("electro", BuildingType.POWER_PLANT),
            Map.entry("lab", BuildingType.LABORATORY),
            Map.entry("laboratory", BuildingType.LABORATORY),
            Map.entry("prom", BuildingType.INDUSTRIAL_FACTORY),
            Map.entry("industrial", BuildingType.INDUSTRIAL_FACTORY),
            Map.entry("machine", BuildingType.MACHINE_FACTORY),
            Map.entry("voen", BuildingType.MILITARY_FACTORY),
            Map.entry("military", BuildingType.MILITARY_FACTORY)
    );
    private static final Pattern BUILDING_TOKEN = Pattern.compile("([\\p{L}_-]+?)(\\d+)?$");

    private record BuildingSelection(BuildingType type, int level) {
    }

    public TownAdminCommand(Plugin plugin, TownManager townManager, MessageService messages, SettingsMenuManager settingsMenuManager, RichChunkService richChunkService, MapService mapService, PoliticalScoreService politicalScoreService, WarManager warManager) {
        this.plugin = plugin;
        this.townManager = townManager;
        this.messages = messages;
        this.settingsMenuManager = settingsMenuManager;
        this.richChunkService = richChunkService;
        this.mapService = mapService;
        this.politicalScoreService = politicalScoreService;
        this.warManager = warManager;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) {
            messages.sendError(sender, "admin.usage");
            return true;
        }

        if (args[0].equalsIgnoreCase("reload")) {
            if (sender instanceof Player player && !player.isOp()) {
                messages.sendError(player, "admin.only");
                return true;
            }
            plugin.reloadConfig();
            messages.reload();
            richChunkService.reload();
            townManager.reload();
            politicalScoreService.reload();
            if (warManager != null) {
                warManager.reload();
            }
            mapService.refreshAll();
            messages.send(sender, "admin.reload-success");
            return true;
        }

        if (args[0].equalsIgnoreCase("settings")) {
            if (!(sender instanceof Player player)) {
                messages.sendError(sender, "general.only-player");
                return true;
            }
            if (!player.isOp()) {
                messages.sendError(player, "admin.only");
                return true;
            }
            if (args.length == 1) {
                settingsMenuManager.openDefaultOutsiderSettings(player);
                return true;
            }

            String townName = String.join(" ", Arrays.copyOfRange(args, 1, args.length)).trim();
            Town town = townManager.getTownByName(townName);
            if (town == null) {
                messages.sendError(player, "town.not-found");
                return true;
            }

            settingsMenuManager.openCitizenSettings(player, town, true);
            return true;
        }

        if (sender instanceof Player player && !player.isOp()) {
            messages.sendError(player, "admin.only");
            return true;
        }

        if (args.length < 3 || !args[1].equalsIgnoreCase("set")) {
            messages.sendError(sender, "admin.set-usage");
            return true;
        }

        Town town = townManager.getTownByName(args[0]);
        if (town == null) {
            messages.sendError(sender, "town.not-found");
            return true;
        }

        String mode = args[2].toLowerCase(Locale.ROOT);
        switch (mode) {
            case "age" -> handleSetAge(sender, town, args);
            case "builds" -> handleSetBuild(sender, town, args);
            case "bank" -> handleSetBank(sender, town, args);
            default -> messages.sendError(sender, "admin.set-usage");
        }
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            List<String> suggestions = new ArrayList<>();
            suggestions.add("reload");
            suggestions.add("settings");
            suggestions.addAll(townManager.getAllTownNames());
            return suggestions.stream()
                    .filter(it -> it.toLowerCase(Locale.ROOT).startsWith(args[0].toLowerCase(Locale.ROOT)))
                    .collect(Collectors.toList());
        }

        if (args.length >= 2 && args[0].equalsIgnoreCase("settings")) {
            String search = String.join(" ", Arrays.copyOfRange(args, 1, args.length)).toLowerCase(Locale.ROOT);
            return townManager.getAllTownNames().stream()
                    .filter(name -> name.toLowerCase(Locale.ROOT).startsWith(search))
                    .collect(Collectors.toList());
        }

        if (args.length == 2) {
            return Collections.singletonList("set").stream()
                    .filter(it -> it.startsWith(args[1].toLowerCase(Locale.ROOT)))
                    .collect(Collectors.toList());
        }

        if (args.length == 3 && args[1].equalsIgnoreCase("set")) {
            return Arrays.asList("age", "builds", "bank").stream()
                    .filter(it -> it.startsWith(args[2].toLowerCase(Locale.ROOT)))
                    .collect(Collectors.toList());
        }

        if (args.length == 4 && args[1].equalsIgnoreCase("set")) {
            return switch (args[2].toLowerCase(Locale.ROOT)) {
                case "age" -> Arrays.stream(AgeTier.values())
                        .map(t -> String.valueOf(t.getLevel()))
                        .filter(it -> it.startsWith(args[3]))
                        .collect(Collectors.toList());
                case "builds" -> buildingSuggestions(args[3]);
                case "bank" -> Arrays.asList("+100", "-100", "500").stream()
                        .filter(it -> it.startsWith(args[3]))
                        .collect(Collectors.toList());
                default -> Collections.emptyList();
            };
        }

        return Collections.emptyList();
    }

    private void handleSetAge(CommandSender sender, Town town, String[] args) {
        if (args.length < 4) {
            messages.sendError(sender, "admin.set-usage");
            return;
        }
        try {
            int level = Math.max(1, Integer.parseInt(args[3]));
            int clamped = Math.min(level, AgeTier.values().length);
            townManager.setAge(town, clamped);
            messages.send(sender, "admin.set.age", Map.of(
                    "town", town.getName(),
                    "age", AgeTier.byLevel(clamped).getDisplayName()
            ));
        } catch (NumberFormatException ex) {
            messages.sendError(sender, "admin.set.age-invalid");
        }
    }

    private void handleSetBuild(CommandSender sender, Town town, String[] args) {
        if (args.length < 4) {
            messages.sendError(sender, "admin.set-usage");
            return;
        }
        BuildingSelection selection = parseBuilding(args[3]);
        if (selection == null) {
            messages.sendError(sender, "admin.set.build-invalid");
            return;
        }
        int level = Math.min(selection.level(), selection.type().getMaxLevel());
        townManager.setBuildingLevel(town, selection.type(), level);
        messages.send(sender, "admin.set.build", Map.of(
                "town", town.getName(),
                "building", selection.type().getDisplayName(),
                "level", String.valueOf(level)
        ));
    }

    private void handleSetBank(CommandSender sender, Town town, String[] args) {
        if (args.length < 4) {
            messages.sendError(sender, "admin.set-usage");
            return;
        }
        try {
            double delta = Double.parseDouble(args[3]);
            double newBalance = Math.max(0, town.getBank() + delta);
            townManager.setBank(town, newBalance);
            messages.send(sender, "admin.set.bank", Map.of(
                    "town", town.getName(),
                    "amount", String.valueOf((int) newBalance)
            ));
        } catch (NumberFormatException ex) {
            messages.sendError(sender, "admin.set.bank-invalid");
        }
    }

    private BuildingSelection parseBuilding(String token) {
        if (token == null || token.isEmpty()) {
            return null;
        }
        String cleaned = token.toLowerCase(Locale.ROOT).replace(" ", "").replace("_", "");
        Matcher matcher = BUILDING_TOKEN.matcher(cleaned);
        if (!matcher.matches()) {
            return null;
        }
        String namePart = matcher.group(1);
        int level = matcher.group(2) != null ? Integer.parseInt(matcher.group(2)) : 1;
        BuildingType type = resolveBuilding(namePart);
        if (type == null) {
            return null;
        }
        int clamped = Math.max(0, Math.min(level, type.getMaxLevel()));
        return new BuildingSelection(type, clamped);
    }

    private BuildingType resolveBuilding(String key) {
        if (BUILDING_ALIASES.containsKey(key)) {
            return BUILDING_ALIASES.get(key);
        }
        for (BuildingType type : BuildingType.values()) {
            if (normalize(type.name()).equals(key) || normalize(type.getDisplayName()).equals(key)) {
                return type;
            }
        }
        return null;
    }

    private List<String> buildingSuggestions(String prefix) {
        String lower = prefix.toLowerCase(Locale.ROOT);
        List<String> options = new ArrayList<>();
        options.addAll(BUILDING_ALIASES.keySet().stream().map(key -> key + "1").toList());
        for (BuildingType type : BuildingType.values()) {
            options.add(normalize(type.getDisplayName()) + "1");
        }
        return options.stream()
                .filter(opt -> opt.startsWith(lower))
                .collect(Collectors.toList());
    }

    private String normalize(String input) {
        return input.toLowerCase(Locale.ROOT).replace(" ", "").replace("_", "").replace("-", "");
    }
}