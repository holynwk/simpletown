package com.simpletown.war;

import com.simpletown.SimpleTownPlugin;
import com.simpletown.data.ChunkPosition;
import com.simpletown.data.Town;
import com.simpletown.data.TownFlag;
import com.simpletown.data.TownManager;
import com.simpletown.service.MapService;
import com.simpletown.service.MessageService;
import com.simpletown.data.BuildingType;
import net.milkbowl.vault.economy.Economy;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;

import java.io.File;
import java.io.IOException;
import java.time.LocalTime;
import java.util.*;
import java.util.stream.Collectors;

public class WarManager {
    private static final long DEFAULT_WAR_PREPARATION_MS = 10 * 60 * 1000L;
    private static final long WAR_COOLDOWN_MS = 24 * 60 * 60 * 1000L;
    private static final int WAR_POLITICAL_COST = 125;
    private static final int WAR_MONEY_COST = 5000;
    private static final int FLAG_HEALTH = 60;
    private static final int CENTER_FLAG_HEALTH = 150;
    private static final long FLAG_CAPTURE_MS = 120 * 1000L;
    private static final long CENTER_FLAG_CAPTURE_MS = 300 * 1000L;
    private static final int FLAG_LIMIT = 10;
    private static final int KILL_POINTS = 5;
    private static final int CAPTURE_POINTS = 10;
    private static final int WIN_POINTS = 1200;
    private static final int QUIET_START_HOUR = 22;
    private static final int QUIET_END_HOUR = 10;
    private static final long KILL_COOLDOWN_MS = 15_000L;
    private static final long DAY_MS = 24 * 60 * 60 * 1000L;
    private static final int REPARATION_DAYS = 7;
    private static final double REPARATION_DAILY_AMOUNT = 100.0;
    private static final String BUILD_BLOCKED_MESSAGE = "war.build-blocked";
    private static final String FLAG_BUILD_BLOCKED_MESSAGE = "war.flag-build-blocked";
    private static final List<String> BLOCKED_COMMANDS = List.of(
            "/t withdraw", "/t delete", "/t kick", "/t leave", "/t claim", "/t unclaim", "/t spawn", "/t set spawn", "/t set center", "/t set color", "/rtp");

    private final SimpleTownPlugin plugin;
    private final MessageService messages;
    private final TownManager townManager;
    private final File storageFile;
    private final Map<String, WarConflict> conflicts = new HashMap<>();
    private final Map<String, Long> warCooldowns = new HashMap<>();
    private final List<ReparationPlan> reparationPlans = new ArrayList<>();
    private final Map<String, ChunkPosition> deadPlayers = new HashMap<>();
    private MapService mapService;
    private long warPreparationMs = DEFAULT_WAR_PREPARATION_MS;
    private boolean quietHoursEnabled = true;

    public WarManager(SimpleTownPlugin plugin, MessageService messages, TownManager townManager) {
        this.plugin = plugin;
        this.messages = messages;
        this.townManager = townManager;
        this.storageFile = new File(plugin.getDataFolder(), "wars.yml");
        reloadPreparationTime();
        reloadQuietHours();
        load();
        Bukkit.getScheduler().runTaskTimer(plugin, this::tickFlags, 20L, 20L);
        Bukkit.getScheduler().runTaskTimer(plugin, this::tickReparations, 20L * 60, 20L * 60);
    }

    public boolean isPactPending(WarConflict conflict) {
        return conflict != null && conflict.isPactPending();
    }

    public void markPactPending(WarConflict conflict, boolean pending) {
        if (conflict != null) {
            conflict.setPactPending(pending);
            save();
        }
    }

    public void setMapService(MapService mapService) {
        this.mapService = mapService;
        refreshMap();
    }

    public void reload() {
        reloadPreparationTime();
        reloadQuietHours();
        clearAllHolograms();
        conflicts.clear();
        warCooldowns.clear();
        load();
        refreshMap();
    }

    public Collection<WarConflict> getConflicts() {
        return getDistinctConflicts();
    }

    public Optional<WarConflict> getConflictForTown(String townName) {
        if (townName == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(conflicts.get(townName.toLowerCase(Locale.ROOT)));
    }

    public Optional<WarConflict> getAwaitingResultConflict(String townName) {
        return getConflictForTown(townName).filter(conflict -> conflict.getStatus() == WarStatus.AWAITING_RESULT);
    }

    public boolean isTownBusy(String townName) {
        return getConflictForTown(townName).isPresent();
    }

    public boolean hasCooldown(String townName) {
        long now = System.currentTimeMillis();
        Long until = warCooldowns.get(townName.toLowerCase(Locale.ROOT));
        return until != null && until > now;
    }

    public long getCooldownLeft(String townName) {
        long now = System.currentTimeMillis();
        Long until = warCooldowns.get(townName.toLowerCase(Locale.ROOT));
        return until == null ? 0 : Math.max(0, until - now);
    }

    public boolean startWarWithPolitics(Player initiator, Town attacker, Town defender, int politicalScore, java.util.function.BooleanSupplier chargeAction) {
        if (!canStart(initiator, attacker, defender)) {
            return false;
        }
        if (politicalScore < WAR_POLITICAL_COST) {
            messages.sendError(initiator, "war.not-enough-points");
            return false;
        }
        if (!chargeAction.getAsBoolean()) {
            return false;
        }
        return startWar(attacker, defender);
    }

    public int getWarPoliticalCost() {
        return WAR_POLITICAL_COST;
    }

    public int getWarMoneyCost() {
        return WAR_MONEY_COST;
    }

    public boolean startWarWithMoney(Player initiator, Town attacker, Town defender, Economy economy) {
        if (!canStart(initiator, attacker, defender)) {
            return false;
        }
        if (economy == null || plugin.getEconomy() == null) {
            messages.sendError(initiator, "town.economy-missing");
            return false;
        }
        if (attacker.getBank() < WAR_MONEY_COST) {
            messages.sendError(initiator, "war.not-enough-money");
            return false;
        }
        if (!townManager.withdraw(attacker, WAR_MONEY_COST)) {
            messages.sendError(initiator, "war.not-enough-money");
            return false;
        }
        return startWar(attacker, defender);
    }

    private boolean startWar(Town attacker, Town defender) {
        long now = System.currentTimeMillis();
        WarConflict conflict = new WarConflict(attacker, defender, WarType.WAR, WarStatus.PREPARATION, now + warPreparationMs, 0);
        conflict.addAttackerPoints(startingPoints(attacker));
        conflict.addDefenderPoints(startingPoints(defender));
        registerConflict(conflict);
        announcePreparation(conflict);
        scheduleTransition(conflict, warPreparationMs, WarStatus.ACTIVE, () -> {
            messages.broadcast("war.started", Map.of(
                    "attacker", conflict.getAttacker(),
                    "defender", conflict.getDefender()
            ));
        });
        return true;
    }

    private boolean canStart(Player initiator, Town attacker, Town defender) {
        if (attacker == null || defender == null) {
            return false;
        }
        if (attacker.equals(defender)) {
            return false;
        }
        if (isTownBusy(attacker.getName()) || isTownBusy(defender.getName())) {
            messages.sendError(initiator, "war.busy");
            return false;
        }
        if (hasCooldown(attacker.getName()) || hasCooldown(defender.getName())) {
            messages.sendError(initiator, "war.cooldown-active");
            return false;
        }
        if (!targetOnline(defender)) {
            messages.sendError(initiator, "war.target-offline");
            return false;
        }
        return true;
    }

    private boolean targetOnline(Town town) {
        return Bukkit.getOnlinePlayers().stream().anyMatch(p -> town.isMember(p.getName()));
    }

    private void registerConflict(WarConflict conflict) {
        conflicts.put(conflict.getAttacker().toLowerCase(Locale.ROOT), conflict);
        conflicts.put(conflict.getDefender().toLowerCase(Locale.ROOT), conflict);
        save();
    }

    private void scheduleTransition(WarConflict conflict, long delayMs, WarStatus targetStatus, Runnable callback) {
        long ticks = Math.max(1L, delayMs / 50L);
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            conflict.setStatus(targetStatus);
            save();
            callback.run();
        }, ticks);
    }

    private void announcePreparation(WarConflict conflict) {
        Map<String, String> placeholders = Map.of(
                "attacker", conflict.getAttacker(),
                "defender", conflict.getDefender(),
                "minutes", String.valueOf(getPreparationMinutes()),
                "type", "войне"
        );
        messages.broadcast("war.preparation", placeholders);
    }

    private void endConflict(WarConflict conflict) {
        conflict.setPactPending(false);
        conflicts.remove(conflict.getAttacker().toLowerCase(Locale.ROOT));
        conflicts.remove(conflict.getDefender().toLowerCase(Locale.ROOT));
        long now = System.currentTimeMillis();
        warCooldowns.put(conflict.getAttacker().toLowerCase(Locale.ROOT), now + WAR_COOLDOWN_MS);
        warCooldowns.put(conflict.getDefender().toLowerCase(Locale.ROOT), now + WAR_COOLDOWN_MS);
        messages.broadcast("war.finished", Map.of(
                "attacker", conflict.getAttacker(),
                "defender", conflict.getDefender()
        ));
        save();
        refreshMap();
    }

    private void applyOccupiedTransfers(WarConflict conflict) {
        Town attacker = townManager.getTownByName(conflict.getAttacker());
        Town defender = townManager.getTownByName(conflict.getDefender());
        if (attacker == null || defender == null) {
            return;
        }
        transferChunks(defender, attacker, conflict.getAttackerOccupied(), false);
        transferChunks(attacker, defender, conflict.getDefenderOccupied(), false);
        refreshMap();
    }

    private void applyTechnologyTransfer(Town from, Town to) {
        int desiredAge = Math.max(to.getAgeLevel(), from.getAgeLevel());
        if (desiredAge > to.getAgeLevel()) {
            townManager.setAge(to, desiredAge);
        }
        for (Map.Entry<BuildingType, Integer> entry : from.getBuildingLevels().entrySet()) {
            int current = to.getBuildingLevel(entry.getKey());
            if (entry.getValue() > current) {
                townManager.setBuildingLevel(to, entry.getKey(), entry.getValue());
            }
        }
    }

    private void queueReparations(Town winner, Town loser) {
        if (winner == null || loser == null) {
            return;
        }
        reparationPlans.add(new ReparationPlan(winner.getName(), loser.getName(), REPARATION_DAILY_AMOUNT, REPARATION_DAYS, System.currentTimeMillis()));
        save();
    }

    private void tickReparations() {
        long now = System.currentTimeMillis();
        Iterator<ReparationPlan> iterator = reparationPlans.iterator();
        while (iterator.hasNext()) {
            ReparationPlan plan = iterator.next();
            while (plan.getRemainingDays() > 0 && now - plan.getLastPaidAt() >= DAY_MS) {
                applyReparationCharge(plan);
                plan.setLastPaidAt(plan.getLastPaidAt() + DAY_MS);
                plan.decrementDay();
            }
            if (plan.getRemainingDays() <= 0) {
                iterator.remove();
            }
        }
        save();
    }

    private void applyReparationCharge(ReparationPlan plan) {
        Town loser = townManager.getTownByName(plan.getLoser());
        Town winner = townManager.getTownByName(plan.getWinner());
        if (loser == null || winner == null) {
            return;
        }
        townManager.setBank(loser, loser.getBank() - plan.getDailyAmount());
        townManager.deposit(winner, plan.getDailyAmount());
    }

    private void refreshMap() {
        if (mapService != null) {
            mapService.refreshAll();
        }
    }

    private void transferChunks(Town from, Town to, Set<ChunkPosition> positions, boolean includeCapital) {
        for (ChunkPosition position : new HashSet<>(positions)) {
            org.bukkit.World world = Bukkit.getWorld(position.getWorld());
            if (world == null) {
                continue;
            }
            org.bukkit.Chunk chunk = world.getChunkAt(position.getX(), position.getZ());
            if (from.getCapital().equals(position)) {
                continue;
            }
            if (townManager.unclaimChunk(from, chunk)) {
                townManager.claimChunk(to, chunk);
            }
        }
        townManager.save();
    }

    public void save() {
        FileConfiguration config = new YamlConfiguration();
        List<Map<String, Object>> list = new ArrayList<>();
        for (WarConflict conflict : getDistinctConflicts()) {
            Map<String, Object> map = new HashMap<>();
            map.put("attacker", conflict.getAttacker());
            map.put("defender", conflict.getDefender());
            map.put("type", conflict.getType().name());
            map.put("status", conflict.getStatus().name());
            map.put("prep", conflict.getPreparationEndsAt());
            map.put("end", conflict.getConflictEndsAt());
            map.put("attacker-points", conflict.getAttackerPoints());
            map.put("defender-points", conflict.getDefenderPoints());
            map.put("attacker-occupied", conflict.getAttackerOccupied().stream().map(ChunkPosition::serialize).toList());
            map.put("defender-occupied", conflict.getDefenderOccupied().stream().map(ChunkPosition::serialize).toList());
            map.put("pact-pending", conflict.isPactPending());
            List<Map<String, Object>> flags = new ArrayList<>();
            for (WarFlag flag : conflict.getActiveFlags()) {
                Map<String, Object> flagMap = new HashMap<>();
                flagMap.put("chunk", flag.getChunk().serialize());
                flagMap.put("town", flag.getOwningTown());
                flagMap.put("center", flag.isCenter());
                flagMap.put("health", flag.getHealth());
                flagMap.put("remaining", flag.getRemainingCaptureMs());
                flagMap.put("world", flag.getWorldName());
                flagMap.put("x", flag.getX());
                flagMap.put("y", flag.getY());
                flagMap.put("z", flag.getZ());
                flags.add(flagMap);
            }
            map.put("flags", flags);
            map.put("winner", conflict.getAwaitingResultWinner());
            list.add(map);
        }
        config.set("conflicts", list);
        config.set("war-cooldowns", warCooldowns);
        List<Map<String, Object>> reparations = new ArrayList<>();
        for (ReparationPlan plan : reparationPlans) {
            Map<String, Object> planMap = new HashMap<>();
            planMap.put("winner", plan.getWinner());
            planMap.put("loser", plan.getLoser());
            planMap.put("daily", plan.getDailyAmount());
            planMap.put("remaining", plan.getRemainingDays());
            planMap.put("last-paid", plan.getLastPaidAt());
            reparations.add(planMap);
        }
        config.set("reparations", reparations);
        try {
            config.save(storageFile);
        } catch (IOException e) {
            plugin.getLogger().warning("Не удалось сохранить данные войны: " + e.getMessage());
        }
    }

    private List<WarConflict> getDistinctConflicts() {
        return conflicts.values().stream().distinct().collect(Collectors.toList());
    }

    private void load() {
        if (!storageFile.exists()) {
            return;
        }
        FileConfiguration config = YamlConfiguration.loadConfiguration(storageFile);
        conflicts.clear();
        List<Map<?, ?>> list = config.getMapList("conflicts");
        for (Map<?, ?> entry : list) {
            String attacker = Objects.toString(entry.get("attacker"), null);
            String defender = Objects.toString(entry.get("defender"), null);
            String typeRaw = Objects.toString(entry.get("type"), "WAR");
            String statusRaw = Objects.toString(entry.get("status"), "PREPARATION");
            long prep = entry.get("prep") instanceof Number n1 ? n1.longValue() : 0L;
            long end = entry.get("end") instanceof Number n2 ? n2.longValue() : 0L;
            try {
                WarConflict conflict = new WarConflict(attacker, defender, WarType.valueOf(typeRaw), WarStatus.valueOf(statusRaw), prep, end);
                conflict.addAttackerPoints(asInt(entry.get("attacker-points")));
                conflict.addDefenderPoints(asInt(entry.get("defender-points")));
                conflict.setPactPending(Boolean.TRUE.equals(entry.get("pact-pending")) || "true".equals(String.valueOf(entry.get("pact-pending"))));
                Object attackerOccupiedRaw = entry.get("attacker-occupied");
                Object defenderOccupiedRaw = entry.get("defender-occupied");
                List<String> attackerOccupied = attackerOccupiedRaw instanceof List<?> attackerList ? (List<String>) attackerList : Collections.emptyList();
                List<String> defenderOccupied = defenderOccupiedRaw instanceof List<?> defenderList ? (List<String>) defenderList : Collections.emptyList();
                attackerOccupied.stream().map(ChunkPosition::deserialize).forEach(conflict.getAttackerOccupied()::add);
                defenderOccupied.stream().map(ChunkPosition::deserialize).forEach(conflict.getDefenderOccupied()::add);

                Object flagsRaw = entry.get("flags");
                List<Map<String, Object>> flags = flagsRaw instanceof List<?> flagList ? (List<Map<String, Object>>) flagList : Collections.emptyList();
                for (Map<String, Object> flagEntry : flags) {
                    String chunkKey = Objects.toString(flagEntry.get("chunk"), null);
                    String townKey = Objects.toString(flagEntry.get("town"), null);
                    boolean center = Boolean.TRUE.equals(flagEntry.get("center")) || "true".equals(String.valueOf(flagEntry.get("center")));
                    int health = asInt(flagEntry.get("health"));
                    long remaining = flagEntry.get("remaining") instanceof Number n ? n.longValue() : FLAG_CAPTURE_MS;
                    String worldName = Objects.toString(flagEntry.get("world"), null);
                    int x = asInt(flagEntry.get("x"));
                    int y = asInt(flagEntry.get("y"));
                    int z = asInt(flagEntry.get("z"));
                    if (chunkKey != null && townKey != null && worldName != null) {
                        WarFlag flag = new WarFlag(ChunkPosition.deserialize(chunkKey), townKey, center, health, remaining, worldName, x, y, z);
                        conflict.getActiveFlags().add(flag);
                        spawnHologram(flag);
                    }
                }
                conflict.setAwaitingResultWinner(Objects.toString(entry.get("winner"), null));
                registerConflict(conflict);
            } catch (Exception ignored) {
            }
        }
        warCooldowns.clear();
        ConfigurationSection warSection = config.getConfigurationSection("war-cooldowns");
        if (warSection != null) {
            for (String key : warSection.getKeys(false)) {
                Long value = warSection.getLong(key);
                warCooldowns.put(key.toLowerCase(Locale.ROOT), value);
            }
        }
        reparationPlans.clear();
        List<Map<?, ?>> reparations = config.getMapList("reparations");
        for (Map<?, ?> raw : reparations) {
            String winner = Objects.toString(raw.get("winner"), null);
            String loser = Objects.toString(raw.get("loser"), null);
            double daily = raw.get("daily") instanceof Number n ? n.doubleValue() : REPARATION_DAILY_AMOUNT;
            int remaining = raw.get("remaining") instanceof Number n2 ? n2.intValue() : REPARATION_DAYS;
            long lastPaid = raw.get("last-paid") instanceof Number n3 ? n3.longValue() : System.currentTimeMillis();
            if (winner != null && loser != null) {
                reparationPlans.add(new ReparationPlan(winner, loser, daily, remaining, lastPaid));
            }
        }
        refreshMap();
    }

    private void reloadPreparationTime() {
        long minutes = plugin.getConfig().getLong("war.preparation-minutes", DEFAULT_WAR_PREPARATION_MS / (60 * 1000L));
        if (minutes < 1) {
            minutes = DEFAULT_WAR_PREPARATION_MS / (60 * 1000L);
        }
        warPreparationMs = minutes * 60 * 1000L;
    }

    public long getPreparationMinutes() {
        return warPreparationMs / (60 * 1000L);
    }

    private void clearAllHolograms() {
        getDistinctConflicts().forEach(conflict -> conflict.getActiveFlags().forEach(this::clearHologram));
    }

    private int asInt(Object raw) {
        return raw instanceof Number n ? n.intValue() : 0;
    }

    private boolean isAllowedByTownFlags(Player player, Town owner, WarAction action) {
        TownFlag flag;
        switch (action) {
            case BREAK -> flag = TownFlag.BREAK;
            case PLACE -> flag = TownFlag.PLACE;
            default -> flag = TownFlag.INTERACT;
        }
        if (owner.isMayor(player.getName())) {
            return true;
        }
        return owner.isMember(player.getName())
                ? owner.getCitizenFlags().isEnabled(flag)
                : owner.getOutsiderFlags().isEnabled(flag);
    }

    private int startingPoints(Town town) {
        int members = town.getCitizens().size();
        if (members <= 15) {
            return 500;
        }
        if (members <= 30) {
            return 750;
        }
        return 1000;
    }

    public boolean isQuietHours() {
        if (!quietHoursEnabled) {
            return false;
        }
        LocalTime now = LocalTime.now();
        return now.getHour() >= QUIET_START_HOUR || now.getHour() < QUIET_END_HOUR;
    }

    private void reloadQuietHours() {
        quietHoursEnabled = plugin.getConfig().getBoolean("war.quiet-hours-enabled", true);
    }

    public boolean placeFlag(Player player, Block block, boolean centerFlag) {
        Town placerTown = townManager.getTownByMember(player.getName());
        if (placerTown == null) {
            messages.sendError(player, "war.not-in-town");
            return false;
        }
        Optional<WarConflict> conflictOpt = getConflictForTown(placerTown.getName());
        if (conflictOpt.isEmpty() || conflictOpt.get().getStatus() != WarStatus.ACTIVE) {
            messages.sendError(player, "war.not-active");
            return false;
        }
        if (isQuietHours()) {
            messages.sendError(player, "war.quiet-hours");
            return false;
        }
        ChunkPosition chunkPosition = ChunkPosition.fromChunk(block.getChunk());
        Town claimOwner = townManager.getTownAtPosition(chunkPosition);
        String controllerName = getChunkController(chunkPosition);
        Town controller = controllerName == null || controllerName.isBlank() ? null : townManager.getTownByName(controllerName);
        WarConflict conflict = conflictOpt.get();
        Town targetTown = controller != null && !controller.equals(placerTown) ? controller : claimOwner;
        if (targetTown == null || targetTown.equals(placerTown)) {
            messages.sendError(player, "war.invalid-flag-chunk");
            return false;
        }
        if (!conflict.getAttacker().equalsIgnoreCase(targetTown.getName()) && !conflict.getDefender().equalsIgnoreCase(targetTown.getName())) {
            messages.sendError(player, "war.invalid-flag-chunk");
            return false;
        }
        if (conflict.getActiveFlags().size() >= FLAG_LIMIT) {
            messages.sendError(player, "war.flag-limit");
            return false;
        }
        if (conflict.getActiveFlags().stream().anyMatch(f -> f.getChunk().equals(chunkPosition))) {
            messages.sendError(player, "war.flag-exists");
            return false;
        }
        if (!targetOnline(targetTown)) {
            messages.sendError(player, "war.target-offline");
            return false;
        }
        if (centerFlag && (claimOwner == null || !chunkPosition.equals(claimOwner.getCapital()))) {
            messages.sendError(player, "war.center-required");
            return false;
        }
        if (!centerFlag && claimOwner != null && chunkPosition.equals(claimOwner.getCapital())) {
            messages.sendError(player, "war.center-only-flag");
            return false;
        }
        int health = centerFlag ? CENTER_FLAG_HEALTH : FLAG_HEALTH;
        long timer = centerFlag ? CENTER_FLAG_CAPTURE_MS : FLAG_CAPTURE_MS;
        WarFlag flag = new WarFlag(chunkPosition, placerTown.getName(), centerFlag, health, timer,
                block.getWorld().getName(), block.getX(), block.getY(), block.getZ());
        conflict.getActiveFlags().add(flag);
        spawnHologram(flag);
        save();
        messages.broadcast("war.flag-placed", Map.of("attacker", placerTown.getName(), "defender", targetTown.getName()));
        refreshMap();
        return true;
    }

    public boolean damageFlag(Player player, Block block) {
        ChunkPosition pos = ChunkPosition.fromChunk(block.getChunk());
        WarFlag flag = findFlagAt(pos).orElse(null);
        if (flag == null) {
            return false;
        }
        Town controller = getControllerTown(pos);
        if (controller == null || !controller.isMember(player.getName())) {
            messages.sendError(player, "war.flag-not-yours");
            return true;
        }
        flag.damage();
        if (flag.getHealth() <= 0) {
            removeFlag(flag);
            block.setType(Material.AIR);
            messages.broadcast("war.flag-destroyed", Map.of("town", controller.getName()));
        } else {
            updateHologram(flag);
        }
        save();
        return true;
    }

    private Optional<WarFlag> findFlagAt(ChunkPosition position) {
        return conflicts.values().stream()
                .flatMap(c -> c.getActiveFlags().stream())
                .filter(f -> f.getChunk().equals(position))
                .findFirst();
    }

    private Optional<WarConflict> findConflictForChunk(ChunkPosition position) {
        return getDistinctConflicts().stream()
                .filter(conflict -> conflict.getStatus() == WarStatus.ACTIVE)
                .filter(conflict -> conflict.getAttackerOccupied().contains(position)
                        || conflict.getDefenderOccupied().contains(position)
                        || isChunkOwnedByParticipant(position, conflict))
                .findFirst();
    }

    private boolean isChunkOwnedByParticipant(ChunkPosition position, WarConflict conflict) {
        Town owner = townManager.getTownAtPosition(position);
        if (owner == null) {
            return false;
        }
        return owner.getName().equalsIgnoreCase(conflict.getAttacker())
                || owner.getName().equalsIgnoreCase(conflict.getDefender());
    }

    private void removeFlag(WarFlag flag) {
        clearHologram(flag);
        conflicts.values().forEach(c -> c.getActiveFlags().remove(flag));
        refreshMap();
    }

    private void removeFlagBlock(WarFlag flag) {
        Location location = flag.toLocation();
        if (location == null) {
            return;
        }
        if (location.getBlock().getType() == Material.RED_BANNER || location.getBlock().getType() == Material.GREEN_BANNER) {
            location.getBlock().setType(Material.AIR);
        }
    }

    private void spawnHologram(WarFlag flag) {
        Location base = flag.toLocation();
        if (base == null) {
            return;
        }
        World world = base.getWorld();
        if (world == null) {
            return;
        }
        clearHologram(flag);
        Location hologramLocation = base.clone().add(0, 2.25, 0);
        ArmorStand stand = world.spawn(hologramLocation, ArmorStand.class, armor -> {
            armor.setInvisible(true);
            armor.setMarker(true);
            armor.setGravity(false);
            armor.setCustomNameVisible(true);
            armor.setSmall(true);
            armor.setCollidable(false);
            armor.setPersistent(false);
            armor.setSilent(true);
        });
        stand.setCustomName(buildHologramText(flag));
        flag.setHologramId(stand.getUniqueId());
    }

    private void updateHologram(WarFlag flag) {
        Entity entity = findHologram(flag);
        if (entity instanceof ArmorStand armorStand) {
            armorStand.setCustomName(buildHologramText(flag));
            return;
        }
        spawnHologram(flag);
    }

    private Entity findHologram(WarFlag flag) {
        if (flag.getHologramId() == null) {
            return null;
        }
        return Bukkit.getEntity(flag.getHologramId());
    }

    private void clearHologram(WarFlag flag) {
        Entity entity = findHologram(flag);
        if (entity != null) {
            entity.remove();
        }
        flag.setHologramId(null);
    }

    private void clearConflictFlags(WarConflict conflict) {
        for (WarFlag active : new ArrayList<>(conflict.getActiveFlags())) {
            removeFlagBlock(active);
            clearHologram(active);
        }
        conflict.getActiveFlags().clear();
    }

    private String buildHologramText(WarFlag flag) {
        int maxHealth = flag.isCenter() ? CENTER_FLAG_HEALTH : FLAG_HEALTH;
        long seconds = Math.max(0, (flag.getRemainingCaptureMs() + 999) / 1000);
        return ChatColor.GREEN + "Здоровье: " + flag.getHealth() + "/" + maxHealth + " | Таймер: " + seconds + "с";
    }

    private void tickFlags() {
        long now = System.currentTimeMillis();
        for (WarConflict conflict : getDistinctConflicts()) {
            if (conflict.getStatus() != WarStatus.ACTIVE) {
                continue;
            }
            for (WarFlag flag : new ArrayList<>(conflict.getActiveFlags())) {
                long delta = now - flag.getLastTick();
                flag.setLastTick(now);
                updateHologram(flag);
                if (!canCaptureProgress(conflict, flag)) {
                    continue;
                }
                flag.setRemainingCaptureMs(Math.max(0, flag.getRemainingCaptureMs() - delta));
                updateHologram(flag);
                if (flag.getRemainingCaptureMs() <= 0) {
                    handleCapture(conflict, flag);
                    removeFlagBlock(flag);
                    removeFlag(flag);
                }
            }
        }
        save();
    }

    private boolean canCaptureProgress(WarConflict conflict, WarFlag flag) {
        Town claimOwner = townManager.getTownAtPosition(flag.getChunk());
        Town controller = getControllerTown(flag.getChunk());
        Town owningTown = townManager.getTownByName(flag.getOwningTown());
        Town defendingTown = controller != null ? controller : claimOwner;
        if (defendingTown == null || owningTown == null) {
            return false;
        }
        if (!targetOnline(defendingTown)) {
            return false;
        }
        int attackers = countPlayers(flag.getChunk(), owningTown);
        int defenders = countPlayers(flag.getChunk(), defendingTown);
        return attackers > 0 && attackers > defenders;
    }

    private int countPlayers(ChunkPosition position, Town town) {
        int attackers = 0;
        Set<String> counted = new HashSet<>();
        for (Player online : Bukkit.getOnlinePlayers()) {
            if (!town.isMember(online.getName())) {
                continue;
            }
            Chunk chunk = online.getLocation().getChunk();
            if (position.isSameChunk(chunk)) {
                attackers++;
                counted.add(online.getName().toLowerCase(Locale.ROOT));
            }
        }
        for (Map.Entry<String, ChunkPosition> entry : deadPlayers.entrySet()) {
            if (counted.contains(entry.getKey())) {
                continue;
            }
            if (!town.isMember(entry.getKey())) {
                continue;
            }
            if (entry.getValue().isSameChunk(position)) {
                attackers++;
            }
        }
        return attackers;
    }

    private void handleCapture(WarConflict conflict, WarFlag flag) {
        boolean attackerSide = conflict.getAttacker().equalsIgnoreCase(flag.getOwningTown());
        Town claimOwner = townManager.getTownAtPosition(flag.getChunk());
        boolean reclaimingOwner = claimOwner != null && claimOwner.getName().equalsIgnoreCase(flag.getOwningTown());
        if (reclaimingOwner) {
            conflict.getAttackerOccupied().remove(flag.getChunk());
            conflict.getDefenderOccupied().remove(flag.getChunk());
        } else if (attackerSide) {
            conflict.getAttackerOccupied().add(flag.getChunk());
            conflict.getDefenderOccupied().remove(flag.getChunk());
            conflict.addAttackerPoints(CAPTURE_POINTS);
            conflict.addDefenderPoints(-CAPTURE_POINTS);
        } else {
            conflict.getDefenderOccupied().add(flag.getChunk());
            conflict.getAttackerOccupied().remove(flag.getChunk());
            conflict.addDefenderPoints(CAPTURE_POINTS);
            conflict.addAttackerPoints(-CAPTURE_POINTS);
        }
        ChunkPosition pos = flag.getChunk();
        messages.broadcast("war.chunk-captured", Map.of(
                "x", String.valueOf(pos.getX()),
                "z", String.valueOf(pos.getZ())
        ));
        refreshMap();
        save();
        checkVictory(conflict);
    }

    public void handlePlayerDeath(Player victim, Player killer) {
        Town victimTown = townManager.getTownByMember(victim.getName());
        Optional<WarConflict> victimConflict = victimTown == null ? Optional.empty() : getConflictForTown(victimTown.getName());
        if (victimConflict.isPresent() && victimConflict.get().getStatus() == WarStatus.ACTIVE) {
            deadPlayers.put(victim.getName().toLowerCase(Locale.ROOT), ChunkPosition.fromChunk(victim.getLocation().getChunk()));
        } else {
            deadPlayers.remove(victim.getName().toLowerCase(Locale.ROOT));
        }
        if (killer == null) {
            return;
        }
        Town killerTown = townManager.getTownByMember(killer.getName());
        if (killerTown == null || victimTown == null || killerTown.equals(victimTown)) {
            return;
        }
        Optional<WarConflict> conflictOpt = getConflictForTown(killerTown.getName());
        if (conflictOpt.isEmpty()) {
            return;
        }
        WarConflict conflict = conflictOpt.get();
        if (!conflict.getAttacker().equalsIgnoreCase(victimTown.getName()) && !conflict.getDefender().equalsIgnoreCase(victimTown.getName())) {
            return;
        }
        if (conflict.getStatus() != WarStatus.ACTIVE) {
            return;
        }
        Map<String, Long> timestamps = conflict.getLastKillTimestamps();
        long now = System.currentTimeMillis();
        long last = timestamps.getOrDefault(victim.getName().toLowerCase(Locale.ROOT), 0L);
        if (now - last < KILL_COOLDOWN_MS) {
            return;
        }
        timestamps.put(victim.getName().toLowerCase(Locale.ROOT), now);
        if (conflict.getAttacker().equalsIgnoreCase(killerTown.getName())) {
            conflict.addAttackerPoints(KILL_POINTS);
            conflict.addDefenderPoints(-KILL_POINTS);
        } else {
            conflict.addDefenderPoints(KILL_POINTS);
            conflict.addAttackerPoints(-KILL_POINTS);
        }
        checkVictory(conflict);
        save();
    }

    public void handlePlayerRespawn(Player player) {
        deadPlayers.remove(player.getName().toLowerCase(Locale.ROOT));
    }

    public void handlePlayerQuit(Player player) {
        deadPlayers.remove(player.getName().toLowerCase(Locale.ROOT));
    }

    private void checkVictory(WarConflict conflict) {
        if (checkOccupationVictory(conflict)) {
            return;
        }
        if (conflict.getAttackerPoints() >= WIN_POINTS || conflict.getDefenderPoints() <= 0) {
            conflict.setStatus(WarStatus.AWAITING_RESULT);
            conflict.setAwaitingResultWinner(conflict.getAttacker());
            messages.broadcast("war.victory", Map.of("winner", conflict.getAttacker(), "loser", conflict.getDefender()));
            notifyMayorForResult(conflict.getAttacker());
            notifyAwaitingLoser(conflict.getDefender());
            save();
            return;
        }
        if (conflict.getDefenderPoints() >= WIN_POINTS || conflict.getAttackerPoints() <= 0) {
            conflict.setStatus(WarStatus.AWAITING_RESULT);
            conflict.setAwaitingResultWinner(conflict.getDefender());
            messages.broadcast("war.victory", Map.of("winner", conflict.getDefender(), "loser", conflict.getAttacker()));
            notifyMayorForResult(conflict.getDefender());
            notifyAwaitingLoser(conflict.getAttacker());
            save();
        }
    }

    private boolean checkOccupationVictory(WarConflict conflict) {
        Town attacker = townManager.getTownByName(conflict.getAttacker());
        Town defender = townManager.getTownByName(conflict.getDefender());
        if (attacker != null && !attacker.getChunks().isEmpty() && conflict.getDefenderOccupied().containsAll(attacker.getChunks())) {
            return concludeOccupationVictory(conflict, conflict.getDefender(), conflict.getAttacker());
        }
        if (defender != null && !defender.getChunks().isEmpty() && conflict.getAttackerOccupied().containsAll(defender.getChunks())) {
            return concludeOccupationVictory(conflict, conflict.getAttacker(), conflict.getDefender());
        }
        return false;
    }

    private boolean concludeOccupationVictory(WarConflict conflict, String winner, String loser) {
        conflict.setStatus(WarStatus.AWAITING_RESULT);
        conflict.setAwaitingResultWinner(winner);
        clearConflictFlags(conflict);
        messages.broadcast("war.victory", Map.of("winner", winner, "loser", loser));
        notifyMayorForResult(winner);
        notifyAwaitingLoser(loser);
        refreshMap();
        save();
        return true;
    }

    private void notifyMayorForResult(String townName) {
        Town town = townManager.getTownByName(townName);
        if (town == null) {
            return;
        }
        Player mayor = Bukkit.getPlayerExact(town.getMayor());
        if (mayor != null) {
            messages.send(mayor, "war.result-prompt");
        }
    }

    private void notifyAwaitingLoser(String townName) {
        Town town = townManager.getTownByName(townName);
        if (town == null) {
            return;
        }
        Player mayor = Bukkit.getPlayerExact(town.getMayor());
        if (mayor != null) {
            messages.send(mayor, "war.awaiting-result");
        }
    }

    public boolean finishWithResult(Player player, Set<WarResultOption> options) {
        Set<WarResultOption> safeOptions = options == null ? EnumSet.noneOf(WarResultOption.class) : EnumSet.copyOf(options);
        Town town = townManager.getTownByMayor(player.getName());
        if (town == null) {
            return false;
        }
        Optional<WarConflict> conflictOpt = getConflictForTown(town.getName());
        if (conflictOpt.isEmpty()) {
            return false;
        }
        WarConflict conflict = conflictOpt.get();
        if (conflict.getStatus() != WarStatus.AWAITING_RESULT) {
            return false;
        }
        if (!town.getName().equalsIgnoreCase(conflict.getAwaitingResultWinner())) {
            messages.sendError(player, "war.not-winner");
            return true;
        }
        if (safeOptions.contains(WarResultOption.TAKE_TERRITORIES) && safeOptions.contains(WarResultOption.REPARATIONS)) {
            messages.sendError(player, "war.result-exclusive");
            return true;
        }
        String loserName = conflict.getAttacker().equalsIgnoreCase(town.getName()) ? conflict.getDefender() : conflict.getAttacker();
        Town loser = townManager.getTownByName(loserName);
        Town winner = townManager.getTownByName(conflict.getAwaitingResultWinner());
        if (loser != null && winner != null) {
            if (safeOptions.contains(WarResultOption.TAKE_TERRITORIES)) {
                transferChunks(loser, winner, loser.getChunks(), true);
            }
            if (safeOptions.contains(WarResultOption.TAKE_TREASURY)) {
                double amount = loser.getBank();
                if (amount != 0) {
                    townManager.setBank(loser, 0);
                    townManager.deposit(winner, amount);
                }
            }
            if (safeOptions.contains(WarResultOption.TAKE_TECHNOLOGIES)) {
                applyTechnologyTransfer(loser, winner);
            }
            if (safeOptions.contains(WarResultOption.REPARATIONS)) {
                queueReparations(winner, loser);
            }
        }
        conflict.setStatus(WarStatus.ENDED);
        applyOccupiedTransfers(conflict);
        endConflict(conflict);
        return true;
    }

    public boolean isChunkOccupied(ChunkPosition position) {
        return getDistinctConflicts().stream()
                .filter(conflict -> conflict.getStatus() != WarStatus.ENDED)
                .anyMatch(conflict -> conflict.getAttackerOccupied().contains(position) || conflict.getDefenderOccupied().contains(position));
    }

    public String getChunkController(ChunkPosition position) {
        for (WarConflict conflict : getDistinctConflicts()) {
            if (conflict.getStatus() == WarStatus.ENDED) {
                continue;
            }
            if (conflict.getAttackerOccupied().contains(position)) {
                return conflict.getAttacker();
            }
            if (conflict.getDefenderOccupied().contains(position)) {
                return conflict.getDefender();
            }
        }
        Town owner = townManager.getTownAtPosition(position);
        return owner == null ? "" : owner.getName();
    }

    private Town getControllerTown(ChunkPosition position) {
        String controller = getChunkController(position);
        if (controller == null || controller.isBlank()) {
            return null;
        }
        return townManager.getTownByName(controller);
    }

    public int getOccupiedChunkCount(String townName) {
        if (townName == null) {
            return 0;
        }
        int count = 0;
        for (WarConflict conflict : getDistinctConflicts()) {
            if (conflict.getStatus() == WarStatus.ENDED) {
                continue;
            }
            count += conflict.getAttackerOccupied().stream()
                    .filter(pos -> conflict.getAttacker().equalsIgnoreCase(townName))
                    .count();
            count += conflict.getDefenderOccupied().stream()
                    .filter(pos -> conflict.getDefender().equalsIgnoreCase(townName))
                    .count();
        }
        return count;
    }

    public boolean surrender(Player player) {
        Town town = townManager.getTownByMayor(player.getName());
        if (town == null) {
            return false;
        }
        Optional<WarConflict> conflictOpt = getConflictForTown(town.getName());
        if (conflictOpt.isEmpty()) {
            return false;
        }
        WarConflict conflict = conflictOpt.get();
        if (conflict.getStatus() != WarStatus.ACTIVE) {
            return false;
        }
        String winner = conflict.getAttacker().equalsIgnoreCase(town.getName()) ? conflict.getDefender() : conflict.getAttacker();
        conflict.setAwaitingResultWinner(winner);
        conflict.setStatus(WarStatus.AWAITING_RESULT);
        messages.broadcast("war.surrender", Map.of("winner", winner, "loser", town.getName()));
        notifyMayorForResult(winner);
        notifyAwaitingLoser(town.getName());
        refreshMap();
        save();
        return true;
    }

    public boolean pact(Player player) {
        Town town = townManager.getTownByMayor(player.getName());
        if (town == null) {
            return false;
        }
        Optional<WarConflict> conflictOpt = getConflictForTown(town.getName());
        if (conflictOpt.isEmpty()) {
            return false;
        }
        WarConflict conflict = conflictOpt.get();
        if (conflict.getStatus() != WarStatus.ACTIVE) {
            return false;
        }
        conflict.setPactPending(false);
        applyOccupiedTransfers(conflict);
        messages.broadcast("war.pact", Map.of("attacker", conflict.getAttacker(), "defender", conflict.getDefender()));
        conflict.setStatus(WarStatus.ENDED);
        endConflict(conflict);
        refreshMap();
        return true;
    }

    public boolean isCommandBlocked(Player player, String rawCommand) {
        Town town = townManager.getTownByMember(player.getName());
        if (town == null) {
            return false;
        }
        Optional<WarConflict> conflictOpt = getConflictForTown(town.getName());
        if (conflictOpt.isEmpty()) {
            return false;
        }
        WarStatus status = conflictOpt.get().getStatus();
        if (status != WarStatus.PREPARATION && status != WarStatus.ACTIVE && status != WarStatus.AWAITING_RESULT) {
            return false;
        }
        String lower = rawCommand.toLowerCase(Locale.ROOT);
        return BLOCKED_COMMANDS.stream().anyMatch(lower::startsWith);
    }

    public boolean canPlace(Player player, Block block) {
        return checkAccess(player, block, WarAction.PLACE);
    }

    public boolean canBreak(Player player, Block block) {
        return checkAccess(player, block, WarAction.BREAK);
    }

    public boolean canInteract(Player player, Block block) {
        return checkAccess(player, block, WarAction.INTERACT);
    }


    private boolean checkAccess(Player player, Block block, WarAction action) {
        ChunkPosition position = ChunkPosition.fromChunk(block.getChunk());
        Optional<WarFlag> flagOpt = findFlagAt(position);
        Town playerTown = townManager.getTownByMember(player.getName());
        Town chunkOwner = townManager.getTownAtPosition(position);

        Optional<WarConflict> conflictOpt = getConflictForTown(playerTown == null ? null : playerTown.getName());
        if (conflictOpt.isEmpty()) {
            conflictOpt = chunkOwner == null ? Optional.empty() : getConflictForTown(chunkOwner.getName());
        }
        if (conflictOpt.isEmpty()) {
            conflictOpt = findConflictForChunk(position);
        }

        if (conflictOpt.isEmpty()) {
            return true;
        }

        WarConflict conflict = conflictOpt.get();
        if (conflict.getStatus() != WarStatus.ACTIVE) {
            return true;
        }

        if (flagOpt.isPresent()) {
            WarFlag flag = flagOpt.get();
            if (action == WarAction.PLACE) {
                messages.sendError(player, FLAG_BUILD_BLOCKED_MESSAGE);
                return false;
            }
            if (action == WarAction.BREAK && (flag.isFlagBlock(block.getLocation()) || flag.isSupportBlock(block.getLocation()))) {
                messages.sendError(player, FLAG_BUILD_BLOCKED_MESSAGE);
                return false;
            }
            return true;
        }

        String ownerName = chunkOwner == null ? null : chunkOwner.getName();
        String controllerName = ownerName;
        if (conflict.getAttackerOccupied().contains(position)) {
            controllerName = conflict.getAttacker();
        } else if (conflict.getDefenderOccupied().contains(position)) {
            controllerName = conflict.getDefender();
        }

        if (chunkOwner != null && chunkOwner.isMember(player.getName())) {
            if (!isAllowedByTownFlags(player, chunkOwner, action)) {
                messages.sendError(player, BUILD_BLOCKED_MESSAGE);
                return false;
            }
        }

        boolean playerAttacker = playerTown != null && playerTown.getName().equalsIgnoreCase(conflict.getAttacker());
        boolean playerDefender = playerTown != null && playerTown.getName().equalsIgnoreCase(conflict.getDefender());
        boolean participant = playerAttacker || playerDefender;

        if (chunkOwner != null && chunkOwner.isMayor(player.getName())) {
            return true;
        }

        if (!participant) {
            if (flagOpt.isPresent() && action == WarAction.PLACE) {
                messages.sendError(player, FLAG_BUILD_BLOCKED_MESSAGE);
                return false;
            }
            return true;
        }

        // Occupied by enemy controller
        if (controllerName != null && ownerName != null && !controllerName.equalsIgnoreCase(ownerName)) {
            if (playerTown != null && playerTown.getName().equalsIgnoreCase(controllerName)) {
                return true;
            }
            if (playerTown != null && playerTown.getName().equalsIgnoreCase(ownerName)) {
                if (action == WarAction.PLACE) {
                    messages.sendError(player, BUILD_BLOCKED_MESSAGE);
                    return false;
                }
                return true;
            }
            if (action == WarAction.PLACE) {
                messages.sendError(player, BUILD_BLOCKED_MESSAGE);
                return false;
            }
            return true;
        }

        // Not occupied or controlled by the original owner
        if (playerTown != null && playerTown.getName().equalsIgnoreCase(ownerName)) {
            if (action == WarAction.BREAK) {
                messages.sendError(player, BUILD_BLOCKED_MESSAGE);
                return false;
            }
            return true;
        }

        if (ownerName != null && participant) {
            if (action == WarAction.PLACE) {
                messages.sendError(player, BUILD_BLOCKED_MESSAGE);
                return false;
            }
            return true;
        }

        return true;
    }

    private enum WarAction {
        PLACE, BREAK, INTERACT
    }
}