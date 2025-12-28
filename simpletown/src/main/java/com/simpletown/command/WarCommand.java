package com.simpletown.command;

import com.simpletown.data.Town;
import com.simpletown.data.TownManager;
import com.simpletown.service.MessageService;
import com.simpletown.service.ConfirmationManager;
import com.simpletown.war.WarConflict;
import com.simpletown.war.WarInfoMenuManager;
import com.simpletown.war.WarMenuManager;
import com.simpletown.war.WarManager;
import com.simpletown.war.WarStatus;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.stream.Collectors;

public class WarCommand implements CommandExecutor, TabCompleter {
    private final TownManager townManager;
    private final MessageService messages;
    private final WarMenuManager menuManager;
    private final WarManager warManager;
    private final WarInfoMenuManager warInfoMenuManager;
    private final ConfirmationManager confirmationManager;

    public WarCommand(TownManager townManager, MessageService messages, WarMenuManager menuManager, WarManager warManager, WarInfoMenuManager warInfoMenuManager, ConfirmationManager confirmationManager) {
        this.townManager = townManager;
        this.messages = messages;
        this.menuManager = menuManager;
        this.warManager = warManager;
        this.warInfoMenuManager = warInfoMenuManager;
        this.confirmationManager = confirmationManager;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            messages.sendError(sender, "general.only-player");
            return true;
        }
        if (args.length > 0) {
            String sub = args[0].toLowerCase();
            if (sub.equals("surrender")) {
                sendSurrenderRequest(player);
                return true;
            }
            if (sub.equals("pact")) {
                requestPact(player);
                return true;
            }
            if (sub.equals("result")) {
                Town town = townManager.getTownByMayor(player.getName());
                if (town == null) {
                    messages.sendError(player, "war.no-war");
                    return true;
                }
                WarConflict conflict = warManager.getAwaitingResultConflict(town.getName()).orElse(null);
                if (conflict == null) {
                    WarConflict any = warManager.getConflictForTown(town.getName()).orElse(null);
                    if (any != null && any.getStatus() != WarStatus.ENDED) {
                        messages.sendError(player, "war.in-progress");
                    } else {
                        messages.sendError(player, "war.no-war");
                    }
                    return true;
                }
                if (!town.getName().equalsIgnoreCase(conflict.getAwaitingResultWinner())) {
                    messages.sendError(player, "war.not-winner");
                    return true;
                }
                menuManager.openResultMenu(player);
                return true;
            }
            if (sub.equals("info")) {
                openInfo(player);
                return true;
            }
        }
        Town town = townManager.getTownByMayor(player.getName());
        if (town == null || !town.isMayor(player.getName())) {
            messages.sendError(player, "war.only-mayor");
            return true;
        }
        menuManager.openMain(player);
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            return List.of("pact", "surrender", "result", "info").stream()
                    .filter(sub -> sub.startsWith(args[0].toLowerCase()))
                    .collect(Collectors.toList());
        }
        return List.of();
    }

    private void openInfo(Player player) {
        Town town = townManager.getTownByMember(player.getName());
        if (town == null) {
            messages.sendError(player, "war.no-war");
            return;
        }
        WarConflict conflict = warManager.getConflictForTown(town.getName()).orElse(null);
        if (conflict == null || conflict.getStatus() == null || conflict.getStatus() == WarStatus.ENDED) {
            messages.sendError(player, "war.no-war");
            return;
        }
        Town attacker = townManager.getTownByName(conflict.getAttacker());
        Town defender = townManager.getTownByName(conflict.getDefender());
        if (attacker == null || defender == null) {
            messages.sendError(player, "war.no-war");
            return;
        }
        warInfoMenuManager.open(player, conflict, attacker, defender);
    }

    private void requestPact(Player player) {
        Town town = townManager.getTownByMayor(player.getName());
        if (town == null) {
            messages.sendError(player, "war.only-mayor");
            return;
        }
        WarConflict conflict = warManager.getConflictForTown(town.getName()).orElse(null);
        if (conflict == null || conflict.getStatus() != WarStatus.ACTIVE) {
            messages.sendError(player, "war.no-war");
            return;
        }
        String enemyName = conflict.getAttacker().equalsIgnoreCase(town.getName()) ? conflict.getDefender() : conflict.getAttacker();
        Town enemy = townManager.getTownByName(enemyName);
        if (enemy == null) {
            messages.sendError(player, "war.no-war");
            return;
        }
        Player enemyMayor = org.bukkit.Bukkit.getPlayerExact(enemy.getMayor());
        if (enemyMayor == null) {
            messages.sendError(player, "war.enemy-offline");
            return;
        }
        if (warManager.isPactPending(conflict)) {
            messages.sendError(player, "war.pact-waiting");
            return;
        }
        if (!confirmationManager.sendConfirmation(enemyMayor, player, "заключить мир с городом \"" + town.getName() + "\"", () -> warManager.pact(enemyMayor), () -> warManager.markPactPending(conflict, false))) {
            return;
        }
        warManager.markPactPending(conflict, true);
        messages.send(player, "war.pact-request-sent", java.util.Map.of("town", enemy.getName()));
    }

    private void sendSurrenderRequest(Player player) {
        Town town = townManager.getTownByMayor(player.getName());
        if (town == null) {
            messages.sendError(player, "war.only-mayor");
            return;
        }
        WarConflict conflict = warManager.getConflictForTown(town.getName()).orElse(null);
        if (conflict == null || conflict.getStatus() != WarStatus.ACTIVE) {
            messages.sendError(player, "war.no-war");
            return;
        }
        confirmationManager.sendConfirmation(player, "сдаться в войне", () -> {
            if (!warManager.surrender(player)) {
                messages.sendError(player, "war.no-war");
            }
        }, null);
    }
}