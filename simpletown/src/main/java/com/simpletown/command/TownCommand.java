package com.simpletown.command;

import com.simpletown.SimpleTownPlugin;
import com.simpletown.data.*;
import com.simpletown.service.ConfirmationManager;
import com.simpletown.service.MessageService;
import com.simpletown.service.*;
import net.milkbowl.vault.economy.Economy;
import net.milkbowl.vault.economy.EconomyResponse;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Chunk;
import org.bukkit.Material;
import org.bukkit.Tag;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import com.simpletown.jobs.JobsMenuManager;
import com.simpletown.service.*;
import com.simpletown.data.AgeTier;

import java.util.*;
import java.util.stream.Collectors;

import com.simpletown.data.ChunkPosition;
import com.simpletown.data.Town;
import com.simpletown.data.TownManager;


public class TownCommand implements CommandExecutor, TabCompleter {
    private static final int SCORE_TOWN_CREATE = 15;
    private static final int SCORE_AGE_UP = 35;
    private static final int SCORE_BUILDING_PURCHASE = 25;
    private static final long TOWN_SPAWN_COOLDOWN_MS = 5 * 60 * 1000;

    private final int cobblestoneRequired;
    private final int ironRequired;
    private final int woodRequired;
    private final double moneyRequired;
    private final double claimBaseCost;
    private final double claimStepIncrease;
    private final int claimStepSize;

    private final SimpleTownPlugin plugin;
    private final TownManager townManager;
    private final ConfirmationManager confirmationManager;
    private final MessageService messages;
    private final SettingsMenuManager settingsMenuManager;
    private final ProgressionMenuManager progressionMenuManager;
    private final TownInventoryService inventoryService;
    private final RichChunkService richChunkService;
    private final ResourceMenuManager resourceMenuManager;
    private final InfoMenuManager infoMenuManager;
    private final MapService mapService;
    private final PoliticalScoreService politicalScoreService;
    private final Map<UUID, String> pendingInvites = new HashMap<>();
    private final JobsMenuManager jobsMenuManager;
    private final Map<UUID, Long> townSpawnCooldowns = new HashMap<>();

    public TownCommand(SimpleTownPlugin plugin, TownManager townManager, ConfirmationManager confirmationManager, MessageService messages, SettingsMenuManager settingsMenuManager, ProgressionMenuManager progressionMenuManager, TownInventoryService inventoryService, RichChunkService richChunkService, ResourceMenuManager resourceMenuManager, InfoMenuManager infoMenuManager, MapService mapService, PoliticalScoreService politicalScoreService, JobsMenuManager jobsMenuManager) {
        this.plugin = plugin;
        this.townManager = townManager;
        this.confirmationManager = confirmationManager;
        this.messages = messages;
        this.settingsMenuManager = settingsMenuManager;
        this.progressionMenuManager = progressionMenuManager;
        this.inventoryService = inventoryService;
        this.richChunkService = richChunkService;
        this.resourceMenuManager = resourceMenuManager;
        this.infoMenuManager = infoMenuManager;
        this.mapService = mapService;
        this.politicalScoreService = politicalScoreService;
        this.jobsMenuManager = jobsMenuManager;

        this.cobblestoneRequired = plugin.getConfig().getInt("town.creation.cobblestone", 256);
        this.ironRequired = plugin.getConfig().getInt("town.creation.iron", 64);
        this.woodRequired = plugin.getConfig().getInt("town.creation.wood", 128);
        this.moneyRequired = plugin.getConfig().getDouble("town.creation.money", 500.0);
        this.claimBaseCost = plugin.getConfig().getDouble("town.claim.base-cost", 20.0);
        this.claimStepIncrease = plugin.getConfig().getDouble("town.claim.step-increase", 20.0);
        this.claimStepSize = Math.max(1, plugin.getConfig().getInt("town.claim.step-size", 10));
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) {
            messages.send(sender, "town.usage");
            return true;
        }

        switch (args[0].toLowerCase(Locale.ROOT)) {
            case "new" -> handleCreate(sender, args);
            case "delete" -> handleDelete(sender);
            case "claim" -> handleClaim(sender);
            case "unclaim" -> handleUnclaim(sender);
            case "join" -> handleJoin(sender, args);
            case "leave" -> handleLeave(sender);
            case "invite" -> handleInvite(sender, args);
            case "kick" -> handleKick(sender, args);
            case "bank" -> handleBank(sender);
            case "deposit" -> handleDeposit(sender, args);
            case "withdraw" -> handleWithdraw(sender, args);
            case "settings" -> handleSettings(sender);
            case "age" -> handleAge(sender);
            case "builds" -> handleBuilds(sender);
            case "inv" -> handleInventory(sender);
            case "confirm" -> handleConfirm(sender, args);
            case "resources" -> handleResources(sender);
            case "info" -> handleInfo(sender, args);
            case "jobs" -> handleJobs(sender);
            case "spawn" -> handleTownSpawn(sender);
            case "set" -> handleSet(sender, args);
            default -> messages.sendError(sender, "general.unknown-subcommand");
        }
        return true;
    }

    private void handleInfo(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            messages.sendError(sender, "general.only-player");
            return;
        }

        Town targetTown;
        if (args.length >= 2) {
            String name = String.join(" ", Arrays.copyOfRange(args, 1, args.length)).trim();
            targetTown = townManager.getTownByName(name);
            if (targetTown == null) {
                messages.sendError(player, "town.not-found");
                return;
            }
        } else {
            targetTown = townManager.getTownAtChunk(player.getLocation().getChunk());
            if (targetTown == null) {
                messages.sendError(player, "town.info.no-town");
                return;
            }
        }

        infoMenuManager.open(player, targetTown);
    }

    private void handleCreate(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            messages.sendError(sender, "general.only-player");
            return;
        }

        if (args.length < 2) {
            messages.sendError(player, "town.create-usage");
            return;
        }

        String name = String.join(" ", Arrays.copyOfRange(args, 1, args.length)).trim();
        if (name.isEmpty()) {
            messages.sendError(player, "town.empty-name");
            return;
        }

        if (townManager.getTownByName(name) != null) {
            messages.sendError(player, "town.exists");
            return;
        }

        if (townManager.getTownByMayor(player.getName()) != null) {
            messages.sendError(player, "town.already-mayor");
            return;
        }

        Chunk chunk = player.getLocation().getChunk();
        if (townManager.isChunkClaimed(chunk)) {
            messages.sendError(player, "town.chunk-claimed");
            return;
        }

        if (!plugin.ensureEconomy()) {
            messages.sendError(player, "town.economy-missing");
            return;
        }

        Economy economy = plugin.getEconomy();

        RequirementResult result = checkRequirements(player, economy);
        if (!result.isMet()) {
            messages.send(player, "town.missing-resources-header");
            for (String line : result.getMessages()) {
                messages.sendRaw(player, line);
            }
            return;
        }

        confirmationManager.sendConfirmation(player, "создать город \"" + name + "\"", () -> finalizeCreation(player, name, chunk, economy), null);
    }

    private void finalizeCreation(Player player, String name, Chunk chunk, Economy economy) {
        if (townManager.getTownByName(name) != null) {
            messages.sendError(player, "town.name-taken-during-confirm");
            return;
        }

        if (townManager.getTownByMayor(player.getName()) != null) {
            messages.sendError(player, "town.mayor-changed-during-confirm");
            return;
        }

        if (townManager.isChunkClaimed(chunk)) {
            messages.sendError(player, "town.chunk-claimed-during-confirm");
            return;
        }

        removeMaterials(player.getInventory(), Material.COBBLESTONE, cobblestoneRequired);
        removeMaterials(player.getInventory(), Material.IRON_INGOT, ironRequired);
        removeWood(player.getInventory(), woodRequired);

        EconomyResponse response = economy.withdrawPlayer(player, moneyRequired);
        if (!response.transactionSuccess()) {
            messages.sendError(player, "town.withdraw-failed", Map.of("error", response.errorMessage));
            return;
        }

        ChunkPosition capital = ChunkPosition.fromChunk(chunk);
        Set<ChunkPosition> claimed = new HashSet<>();
        claimed.add(capital);
        Town town = new Town(
                name,
                player.getName(),
                Collections.emptySet(),
                claimed,
                capital,
                0.0,
                townManager.getDefaultOpen(),
                townManager.getDefaultCitizenFlags(),
                townManager.getDefaultOutsiderFlags(),
                townManager.generateRandomColor(),
                AgeTier.AGE1.getLevel(),
                new HashMap<>(),
                new ArrayList<>(),
                new HashMap<>(),
                null,
                System.currentTimeMillis()
        );
        townManager.addTown(town);

        messages.send(player, "town.created", Map.of("name", name));
        politicalScoreService.addScore(player.getUniqueId(), SCORE_TOWN_CREATE);
        mapService.refreshTown(town);
    }

    private RequirementResult checkRequirements(Player player, Economy economy) {
        PlayerInventory inventory = player.getInventory();

        int cobblestone = countMaterial(inventory, Material.COBBLESTONE);
        int iron = countMaterial(inventory, Material.IRON_INGOT);
        int wood = countWood(inventory);
        double balance = economy.getBalance(player);

        List<String> lines = new ArrayList<>();
        lines.add(formatRequirement("town.requirements.cobblestone", cobblestone, cobblestoneRequired));
        lines.add(formatRequirement("town.requirements.wood", wood, woodRequired));
        lines.add(formatRequirement("town.requirements.iron", iron, ironRequired));
        lines.add(formatMoneyRequirement("town.requirements.money", balance, moneyRequired));

        boolean met = cobblestone >= cobblestoneRequired
                && iron >= ironRequired
                && wood >= woodRequired
                && balance >= moneyRequired;

        return new RequirementResult(met, lines);
    }

    private int countMaterial(PlayerInventory inventory, Material material) {
        int count = 0;
        for (ItemStack stack : inventory.getContents()) {
            if (stack != null && stack.getType() == material) {
                count += stack.getAmount();
            }
        }
        return count;
    }

    private int countWood(PlayerInventory inventory) {
        int count = 0;
        for (ItemStack stack : inventory.getContents()) {
            if (stack == null) {
                continue;
            }
            Material type = stack.getType();
            if (Tag.LOGS.isTagged(type) || Tag.PLANKS.isTagged(type)) {
                count += stack.getAmount();
            }
        }
        return count;
    }

    private String formatRequirement(String path, int current, int required) {
        ChatColor color = current >= required ? ChatColor.GREEN : ChatColor.RED;
        return color + messages.format(path, Map.of(
                "current", String.valueOf(current),
                "required", String.valueOf(required)
        ));
    }

    private String formatMoneyRequirement(String path, double current, double required) {
        ChatColor color = current >= required ? ChatColor.GREEN : ChatColor.RED;
        return color + messages.format(path, Map.of(
                "current", String.valueOf((int) current),
                "required", String.valueOf((int) required)
        ));
    }

    private void removeMaterials(PlayerInventory inventory, Material material, int amount) {
        int toRemove = amount;
        for (ItemStack stack : inventory.getContents()) {
            if (stack == null || stack.getType() != material) {
                continue;
            }
            int remove = Math.min(toRemove, stack.getAmount());
            stack.setAmount(stack.getAmount() - remove);
            toRemove -= remove;
            if (toRemove <= 0) {
                return;
            }
        }
    }

    private void removeWood(PlayerInventory inventory, int amount) {
        int toRemove = amount;
        for (ItemStack stack : inventory.getContents()) {
            if (stack == null) {
                continue;
            }
            if (Tag.LOGS.isTagged(stack.getType()) || Tag.PLANKS.isTagged(stack.getType())) {
                int remove = Math.min(toRemove, stack.getAmount());
                stack.setAmount(stack.getAmount() - remove);
                toRemove -= remove;
                if (toRemove <= 0) {
                    return;
                }
            }
        }
    }

    private void handleDelete(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            messages.sendError(sender, "general.only-player");
            return;
        }

        Town town = townManager.getTownByMayor(player.getName());
        if (town == null) {
            messages.sendError(player, "town.not-mayor-delete");
            return;
        }

        confirmationManager.sendConfirmation(player, "удалить город \"" + town.getName() + "\"", () -> finalizeDeletion(player, town), null);
    }

    private void finalizeDeletion(Player player, Town town) {
        Town current = townManager.getTownByMayor(player.getName());
        if (current == null || !current.getName().equalsIgnoreCase(town.getName())) {
            messages.sendError(player, "town.mayor-changed-during-confirm");
            return;
        }
        townManager.deleteTown(town);
        messages.send(player, "town.deleted", Map.of("name", town.getName()));
        mapService.refreshAll();
    }

    private void handleConfirm(CommandSender sender, String[] args) {
        if (args.length < 2) {
            return;
        }
        confirmationManager.handleResponse(sender, args[1]);
    }

    private void handleClaim(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            messages.sendError(sender, "general.only-player");
            return;
        }

        Town town = townManager.getTownByMayor(player.getName());
        if (town == null) {
            messages.sendError(player, "town.not-mayor-claim");
            return;
        }

        Chunk chunk = player.getLocation().getChunk();
        Town existing = townManager.getTownAtChunk(chunk);
        if (existing != null) {
            if (existing.getName().equalsIgnoreCase(town.getName())) {
                messages.sendError(player, "town.claim.already-owned");
            } else {
                messages.sendError(player, "town.claim.other-owned", Map.of("name", existing.getName()));
            }
            return;
        }

        double cost = calculateClaimCost(town);
        if (town.getBank() < cost) {
            messages.sendError(player, "town.claim.not-enough-bank", Map.of(
                    "cost", String.format(Locale.ROOT, "%.2f", cost)
            ));
            return;
        }

        if (!townManager.claimChunk(town, chunk)) {
            messages.sendError(player, "town.claim.failed");
            return;
        }

        if (!townManager.withdraw(town, cost)) {
            townManager.unclaimChunk(town, chunk);
            messages.sendError(player, "town.claim.failed");
            return;
        }

        messages.send(player, "town.claim.success", Map.of(
                "cost", String.format(Locale.ROOT, "%.2f", cost)
        ));
        Optional<RichChunkService.RichChunkEntry> rich = richChunkService.recordRichChunkIfEligible(chunk);
        rich.ifPresent(entry -> {
            String typeLabel = entry.getType().getColoredName();
            messages.send(player, "town.rich-chunk-found", Map.of(
                    "x", String.valueOf(entry.getPosition().getX()),
                    "z", String.valueOf(entry.getPosition().getZ()),
                    "type", typeLabel
            ));
            messages.send(player, "town.rich-chunk-type", Map.of("type", typeLabel));
        });
        mapService.refreshTown(town);
    }

    private void handleUnclaim(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            messages.sendError(sender, "general.only-player");
            return;
        }

        Town town = townManager.getTownByMayor(player.getName());
        if (town == null) {
            messages.sendError(player, "town.not-mayor-unclaim");
            return;
        }

        Chunk chunk = player.getLocation().getChunk();
        Town owner = townManager.getTownAtChunk(chunk);
        ChunkPosition position = ChunkPosition.fromChunk(chunk);

        if (owner == null) {
            messages.sendError(player, "town.unclaim.not-owned");
            return;
        }

        if (!owner.getName().equalsIgnoreCase(town.getName())) {
            messages.sendError(player, "town.unclaim.other-town", Map.of("name", owner.getName()));
            return;
        }

        if (town.getCapital().equals(position)) {
            messages.sendError(player, "town.unclaim.capital");
            return;
        }

        if (!townManager.unclaimChunk(town, chunk)) {
            messages.sendError(player, "town.unclaim.failed");
            return;
        }

        messages.send(player, "town.unclaim.success");
        mapService.refreshTown(town);
    }

    private void handleResources(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            messages.sendError(sender, "general.only-player");
            return;
        }

        Town town = townManager.getTownByMember(player.getName());
        if (town == null) {
            messages.sendError(player, "town.not-in-town");
            return;
        }

        if (!town.isMayor(player.getName())) {
            messages.sendError(player, "town.not-mayor");
            return;
        }

        resourceMenuManager.openResources(player, town, 1);
    }

    private void handleSet(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            messages.sendError(sender, "general.only-player");
            return;
        }
        Town town = townManager.getTownByMayor(player.getName());
        if (town == null) {
            messages.sendError(player, "town.not-mayor");
            return;
        }

        if (args.length < 2) {
            messages.sendError(player, "town.set.usage");
            return;
        }

        String type = args[1].toLowerCase(Locale.ROOT);
        switch (type) {
            case "center" -> setTownCenter(player, town);
            case "color" -> setTownColor(player, town, args);
            case "name" -> setTownName(player, town, args);
            case "spawn" -> setTownSpawn(player, town);
            default -> messages.sendError(player, "town.set.usage");
        }
    }


    private void setTownCenter(Player player, Town town) {
        Chunk chunk = player.getLocation().getChunk();
        Town owner = townManager.getTownAtChunk(chunk);
        if (owner == null || !owner.getName().equalsIgnoreCase(town.getName())) {
            messages.sendError(player, "town.set.center-not-claimed");
            return;
        }

        ChunkPosition position = ChunkPosition.fromChunk(chunk);
        if (!townManager.setCapital(town, position)) {
            messages.sendError(player, "town.set.center-failed");
            return;
        }

        messages.send(player, "town.set.center-success");
        mapService.refreshTown(town);
    }

    private void setTownColor(Player player, Town town, String[] args) {
        if (args.length < 3) {
            messages.sendError(player, "town.set.color-usage");
            return;
        }

        String parsed = parseColorArgs(Arrays.copyOfRange(args, 2, args.length));
        if (parsed == null) {
            messages.sendError(player, "town.set.color-invalid");
            return;
        }

        townManager.setColor(town, parsed);
        messages.send(player, "town.set.color-success", Map.of("color", parsed));
        mapService.refreshTown(town);
    }

    private void setTownName(Player player, Town town, String[] args) {
        if (args.length < 3) {
            messages.sendError(player, "town.set.name-usage");
            return;
        }

        String newName = String.join(" ", Arrays.copyOfRange(args, 2, args.length)).trim();
        if (newName.isEmpty()) {
            messages.sendError(player, "town.set.name-usage");
            return;
        }

        Town existing = townManager.getTownByName(newName);
        if (existing != null && existing != town) {
            messages.sendError(player, "town.set.name-taken");
            return;
        }

        String oldName = town.getName();
        if (!townManager.renameTown(town, newName)) {
            messages.sendError(player, "town.set.name-failed");
            return;
        }

        messages.send(player, "town.set.name-success", Map.of("name", newName));
        String broadcast = messages.prefixed(messages.format("town.set.name-broadcast", Map.of(
                "old", oldName,
                "new", newName
        )));
        Bukkit.broadcastMessage(broadcast);
        mapService.refreshTown(town);
    }

    private void setTownSpawn(Player player, Town town) {
        Chunk chunk = player.getLocation().getChunk();
        Town owner = townManager.getTownAtChunk(chunk);
        if (owner == null || !owner.getName().equalsIgnoreCase(town.getName())) {
            messages.sendError(player, "town.set.spawn-not-claimed");
            return;
        }

        if (!townManager.setSpawn(town, player.getLocation())) {
            messages.sendError(player, "town.set.spawn-failed");
            return;
        }

        messages.send(player, "town.set.spawn-success");
    }

    private String parseColorArgs(String[] args) {
        if (args.length == 1) {
            String value = args[0].trim();
            if (!value.startsWith("#")) {
                value = "#" + value;
            }
            if (value.matches("#[0-9a-fA-F]{6}")) {
                return value.toUpperCase(Locale.ROOT);
            }
            return null;
        }

        if (args.length >= 3) {
            try {
                int r = Integer.parseInt(args[0]);
                int g = Integer.parseInt(args[1]);
                int b = Integer.parseInt(args[2]);
                if (isValidColorComponent(r) && isValidColorComponent(g) && isValidColorComponent(b)) {
                    return String.format(Locale.ROOT, "#%02X%02X%02X", r, g, b);
                }
            } catch (NumberFormatException ignored) {
                return null;
            }
        }
        return null;
    }

    private boolean isValidColorComponent(int value) {
        return value >= 0 && value <= 255;
    }


    private void handleJoin(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            messages.sendError(sender, "general.only-player");
            return;
        }

        if (args.length < 2) {
            messages.sendError(player, "town.join-usage");
            return;
        }

        if (townManager.getTownByMember(player.getName()) != null) {
            messages.sendError(player, "town.already-in-town");
            return;
        }

        String name = String.join(" ", Arrays.copyOfRange(args, 1, args.length)).trim();
        Town town = townManager.getTownByName(name);
        if (town == null) {
            messages.sendError(player, "town.not-found");
            return;
        }

        if (!town.isOpen()) {
            messages.sendError(player, "town.closed");
            return;
        }

        finalizeJoin(player, town);
    }

    private void handleTownSpawn(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            messages.sendError(sender, "general.only-player");
            return;
        }

        Town town = townManager.getTownByMember(player.getName());
        if (town == null) {
            messages.sendError(player, "town.not-in-town");
            return;
        }

        org.bukkit.Location spawn = town.getSpawn();
        if (spawn == null) {
            messages.sendError(player, "town.spawn.not-set");
            return;
        }

        long now = System.currentTimeMillis();
        long readyAt = townSpawnCooldowns.getOrDefault(player.getUniqueId(), 0L);
        if (readyAt > now) {
            long remaining = readyAt - now;
            messages.sendError(player, "town.spawn.cooldown", Map.of("time", formatCooldown(remaining)));
            return;
        }

        if (spawn.getWorld() == null) {
            messages.sendError(player, "town.spawn.not-set");
            return;
        }

        Chunk spawnChunk = spawn.getChunk();
        Town owner = townManager.getTownAtChunk(spawnChunk);
        if (owner == null || !owner.getName().equalsIgnoreCase(town.getName())) {
            messages.sendError(player, "town.spawn.not-owned");
            return;
        }

        townSpawnCooldowns.put(player.getUniqueId(), now + TOWN_SPAWN_COOLDOWN_MS);
        player.teleport(spawn);
        messages.send(player, "town.spawn.success");
    }

    private String formatCooldown(long millis) {
        long totalSeconds = (long) Math.ceil(millis / 1000.0);
        long minutes = totalSeconds / 60;
        long seconds = totalSeconds % 60;
        if (minutes > 0) {
            return minutes + "м " + seconds + "с";
        }
        return seconds + "с";
    }

    private void handleLeave(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            messages.sendError(sender, "general.only-player");
            return;
        }

        Town town = townManager.getTownByMember(player.getName());
        if (town == null) {
            messages.sendError(player, "town.not-in-town");
            return;
        }

        if (town.isMayor(player.getName())) {
            messages.sendError(player, "town.mayor-cannot-leave");
            return;
        }

        if (!townManager.removeCitizen(town, player.getName())) {
            messages.sendError(player, "town.leave-failed");
            return;
        }

        messages.send(player, "town.left", Map.of("name", town.getName()));
    }

    private void handleInvite(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            messages.sendError(sender, "general.only-player");
            return;
        }

        if (args.length < 2) {
            messages.sendError(player, "town.invite-usage");
            return;
        }

        Town town = townManager.getTownByMember(player.getName());
        if (town == null) {
            messages.sendError(player, "town.not-in-town");
            return;
        }

        Player target = Bukkit.getPlayerExact(args[1]);
        if (target == null) {
            messages.sendError(player, "town.invite.offline");
            return;
        }

        if (townManager.getTownByMember(target.getName()) != null) {
            messages.sendError(player, "town.invite.already-in-town");
            return;
        }

        if (pendingInvites.containsKey(target.getUniqueId())) {
            messages.sendError(player, "confirmation.pending", Map.of("action", "вступить в город"));
            return;
        }
        if (!confirmationManager.sendConfirmation(target, player, "вступить в город \"" + town.getName() + "\"", () -> finalizeJoin(target, town), () -> pendingInvites.remove(target.getUniqueId()))) {
            return;
        }
        pendingInvites.put(target.getUniqueId(), town.getName().toLowerCase(Locale.ROOT));
        messages.send(player, "town.invite.sent", Map.of("player", target.getName()));
    }

    private void handleKick(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            messages.sendError(sender, "general.only-player");
            return;
        }

        if (args.length < 2) {
            messages.sendError(player, "town.kick-usage");
            return;
        }

        Town town = townManager.getTownByMayor(player.getName());
        if (town == null) {
            messages.sendError(player, "town.not-mayor-kick");
            return;
        }

        String targetName = args[1];
        if (town.isMayor(targetName)) {
            messages.sendError(player, "town.kick.cannot-kick-mayor");
            return;
        }

        if (!town.isMember(targetName)) {
            messages.sendError(player, "town.kick.not-member");
            return;
        }

        if (!townManager.removeCitizen(town, targetName)) {
            messages.sendError(player, "town.kick.failed");
            return;
        }

        messages.send(player, "town.kick.success", Map.of("player", targetName));

        Player kickedOnline = Bukkit.getPlayerExact(targetName);
        if (kickedOnline != null) {
            messages.send(kickedOnline, "town.kick.notify", Map.of("name", town.getName()));
        }
    }

    private void handleBank(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            messages.sendError(sender, "general.only-player");
            return;
        }

        Town town = townManager.getTownByMember(player.getName());
        if (town == null) {
            messages.sendError(player, "town.not-in-town");
            return;
        }

        messages.send(player, "town.bank.balance", Map.of(
                "amount", String.format(Locale.ROOT, "%.2f", town.getBank())
        ));
    }

    private void handleDeposit(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            messages.sendError(sender, "general.only-player");
            return;
        }

        if (args.length < 2) {
            messages.sendError(player, "town.deposit-usage");
            return;
        }

        Town town = townManager.getTownByMember(player.getName());
        if (town == null) {
            messages.sendError(player, "town.not-in-town");
            return;
        }

        double amount = parseAmount(args[1]);
        if (amount <= 0) {
            messages.sendError(player, "town.deposit.invalid-amount");
            return;
        }

        if (!plugin.ensureEconomy()) {
            messages.sendError(player, "town.economy-missing");
            return;
        }

        Economy economy = plugin.getEconomy();
        double balance = economy.getBalance(player);
        if (amount > balance) {
            messages.sendError(player, "town.deposit.not-enough");
            return;
        }

        EconomyResponse response = economy.withdrawPlayer(player, amount);
        if (!response.transactionSuccess()) {
            messages.sendError(player, "town.deposit.failed", Map.of("error", response.errorMessage));
            return;
        }

        townManager.deposit(town, amount);
        messages.send(player, "town.deposit.success", Map.of(
                "amount", String.format(Locale.ROOT, "%.2f", amount),
                "name", town.getName()
        ));
    }

    private void handleWithdraw(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            messages.sendError(sender, "general.only-player");
            return;
        }

        if (args.length < 2) {
            messages.sendError(player, "town.withdraw-usage");
            return;
        }

        Town town = townManager.getTownByMayor(player.getName());
        if (town == null) {
            messages.sendError(player, "town.not-mayor-withdraw");
            return;
        }

        double amount = parseAmount(args[1]);
        if (amount <= 0) {
            messages.sendError(player, "town.withdraw.invalid-amount");
            return;
        }

        if (town.getBank() < amount) {
            messages.sendError(player, "town.withdraw.not-enough");
            return;
        }

        if (!plugin.ensureEconomy()) {
            messages.sendError(player, "town.economy-missing");
            return;
        }

        if (!townManager.withdraw(town, amount)) {
            messages.sendError(player, "town.withdraw.failed");
            return;
        }

        Economy economy = plugin.getEconomy();
        EconomyResponse response = economy.depositPlayer(player, amount);
        if (!response.transactionSuccess()) {
            messages.sendError(player, "town.withdraw.economy-failed", Map.of("error", response.errorMessage));
            townManager.deposit(town, amount);
            return;
        }

        messages.send(player, "town.withdraw.success", Map.of(
                "amount", String.format(Locale.ROOT, "%.2f", amount),
                "name", town.getName()
        ));
    }

    private void handleSettings(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            messages.sendError(sender, "general.only-player");
            return;
        }

        Town town = townManager.getTownByMayor(player.getName());
        if (town == null) {
            messages.sendError(player, "town.not-mayor-settings");
            return;
        }

        settingsMenuManager.openCitizenSettings(player, town, false);
    }

    private void handleAge(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            messages.sendError(sender, "general.only-player");
            return;
        }

        Town town = townManager.getTownByMayor(player.getName());
        if (town == null) {
            messages.sendError(player, "town.not-mayor");
            return;
        }

        progressionMenuManager.openAgeMenu(player, town);
    }

    private void handleBuilds(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            messages.sendError(sender, "general.only-player");
            return;
        }

        Town town = townManager.getTownByMayor(player.getName());
        if (town == null) {
            messages.sendError(player, "town.not-mayor");
            return;
        }

        progressionMenuManager.openBuildMenu(player, town);
    }

    private void handleInventory(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            messages.sendError(sender, "general.only-player");
            return;
        }

        Town town = townManager.getTownByMember(player.getName());
        if (town == null) {
            messages.sendError(player, "town.not-member");
            return;
        }

        inventoryService.openInventory(player, town);
    }

    private void handleJobs(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            messages.sendError(sender, "general.only-player");
            return;
        }
        jobsMenuManager.openMenu(player);
    }

    private void finalizeJoin(Player player, Town town) {
        Town latest = townManager.getTownByName(town.getName());
        if (latest == null) {
            messages.sendError(player, "town.not-found");
            pendingInvites.remove(player.getUniqueId());
            return;
        }

        if (townManager.getTownByMember(player.getName()) != null) {
            messages.sendError(player, "town.already-in-town");
            pendingInvites.remove(player.getUniqueId());
            return;
        }

        if (!townManager.addCitizen(latest, player.getName())) {
            messages.sendError(player, "town.join-failed");
            return;
        }

        pendingInvites.remove(player.getUniqueId());
        messages.send(player, "town.joined", Map.of("name", latest.getName()));
    }

    private double calculateClaimCost(Town town) {
        int owned = town.getChunks().size();
        int stepMultiplier = owned / claimStepSize;
        return claimBaseCost + (claimStepIncrease * stepMultiplier);
    }

    private double parseAmount(String raw) {
        try {
            return Double.parseDouble(raw);
        } catch (NumberFormatException ex) {
            return -1;
        }
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            return Arrays.asList("new", "delete", "claim", "unclaim", "join", "leave", "invite", "kick", "bank", "deposit", "withdraw", "settings", "age", "builds", "inv", "resources", "info", "set", "jobs", "spawn")
                    .stream()
                    .filter(it -> it.startsWith(args[0].toLowerCase(Locale.ROOT)))
                    .collect(Collectors.toList());
        }
        if (args.length == 2) {
            String sub = args[0].toLowerCase(Locale.ROOT);
            return switch (sub) {
                case "join" -> townManager.getAllTownNames().stream()
                        .filter(name -> name.toLowerCase(Locale.ROOT).startsWith(args[1].toLowerCase(Locale.ROOT)))
                        .collect(Collectors.toList());
                case "info" -> townManager.getAllTownNames().stream()
                        .filter(name -> name.toLowerCase(Locale.ROOT).startsWith(args[1].toLowerCase(Locale.ROOT)))
                        .collect(Collectors.toList());
                case "invite" -> {
                    if (sender instanceof Player player) {
                        Town town = townManager.getTownByMember(player.getName());
                        if (town != null) {
                            yield Bukkit.getOnlinePlayers().stream()
                                    .map(Player::getName)
                                    .filter(name -> !town.isMember(name))
                                    .filter(name -> name.toLowerCase(Locale.ROOT).startsWith(args[1].toLowerCase(Locale.ROOT)))
                                    .collect(Collectors.toList());
                        }
                    }
                    yield Collections.emptyList();
                }
                case "kick" -> {
                    if (sender instanceof Player player) {
                        Town town = townManager.getTownByMayor(player.getName());
                        if (town != null) {
                            yield town.getCitizens().stream()
                                    .filter(name -> name.toLowerCase(Locale.ROOT).startsWith(args[1].toLowerCase(Locale.ROOT)))
                                    .collect(Collectors.toList());
                        }
                    }
                    yield Collections.emptyList();
                }
                case "set" -> Arrays.asList("center", "color", "name", "spawn").stream()
                        .filter(opt -> opt.startsWith(args[1].toLowerCase(Locale.ROOT)))
                        .collect(Collectors.toList());
                default -> Collections.emptyList();
            };
        }
        return Collections.emptyList();
    }

    private static class RequirementResult {
        private final boolean met;
        private final List<String> messages;

        public RequirementResult(boolean met, List<String> messages) {
            this.met = met;
            this.messages = messages;
        }

        public boolean isMet() {
            return met;
        }

        public List<String> getMessages() {
            return messages;
        }
    }
}