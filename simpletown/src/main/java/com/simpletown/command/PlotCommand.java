package com.simpletown.command;

import com.simpletown.SimpleTownPlugin;
import com.simpletown.data.ChunkPosition;
import com.simpletown.data.PlotData;
import com.simpletown.data.Town;
import com.simpletown.data.TownManager;
import com.simpletown.service.MessageService;
import com.simpletown.service.PlotInfoMenuManager;
import com.simpletown.service.PlotSettingsMenuManager;
import net.milkbowl.vault.economy.Economy;
import net.milkbowl.vault.economy.EconomyResponse;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.*;
import java.util.stream.Collectors;

public class PlotCommand implements CommandExecutor, TabCompleter {
    private final SimpleTownPlugin plugin;
    private final TownManager townManager;
    private final MessageService messages;
    private final PlotSettingsMenuManager plotSettingsMenuManager;
    private final PlotInfoMenuManager plotInfoMenuManager;

    public PlotCommand(SimpleTownPlugin plugin, TownManager townManager, MessageService messages, PlotSettingsMenuManager plotSettingsMenuManager, PlotInfoMenuManager plotInfoMenuManager) {
        this.plugin = plugin;
        this.townManager = townManager;
        this.messages = messages;
        this.plotSettingsMenuManager = plotSettingsMenuManager;
        this.plotInfoMenuManager = plotInfoMenuManager;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            messages.sendError(sender, "general.only-player");
            return true;
        }
        if (args.length == 0) {
            messages.sendError(player, "plot.usage");
            return true;
        }
        String sub = args[0].toLowerCase(Locale.ROOT);
        switch (sub) {
            case "sell" -> handleSell(player, args);
            case "buy" -> handleBuy(player);
            case "reclaim" -> handleReclaim(player);
            case "settings" -> handleSettings(player);
            case "invite" -> handleInvite(player, args);
            case "kick" -> handleKick(player, args);
            case "info" -> handleInfo(player);
            default -> messages.sendError(player, "plot.usage");
        }
        return true;
    }

    private void handleSell(Player player, String[] args) {
        if (args.length < 2) {
            messages.sendError(player, "plot.sell-usage");
            return;
        }
        Town town = getTownAtPlayerChunk(player);
        if (town == null) {
            messages.sendError(player, "plot.not-in-town");
            return;
        }
        PlotData plot = town.getPlot(ChunkPosition.fromChunk(player.getLocation().getChunk()));
        if (plot == null) {
            messages.sendError(player, "plot.not-in-town");
            return;
        }
        if (!plot.isMunicipal() && !plot.isOwner(player.getName())) {
            messages.sendError(player, "plot.not-owner");
            return;
        }
        if (plot.isMunicipal() && !town.isMayor(player.getName())) {
            messages.sendError(player, "plot.not-mayor");
            return;
        }
        double price = parseAmount(args[1]);
        if (price <= 0) {
            messages.sendError(player, "plot.invalid-price");
            return;
        }
        plot.setSalePrice(price);
        townManager.save();
        messages.send(player, "plot.sell-success", Map.of("price", formatPrice(price)));
    }

    private void handleBuy(Player player) {
        Town town = getTownAtPlayerChunk(player);
        if (town == null) {
            messages.sendError(player, "plot.not-in-town");
            return;
        }
        if (!town.isMember(player.getName())) {
            messages.sendError(player, "plot.not-member");
            return;
        }
        PlotData plot = town.getPlot(ChunkPosition.fromChunk(player.getLocation().getChunk()));
        if (plot == null) {
            messages.sendError(player, "plot.not-in-town");
            return;
        }
        if (!plot.isForSale()) {
            messages.sendError(player, "plot.not-for-sale");
            return;
        }
        if (plot.isOwner(player.getName())) {
            messages.sendError(player, "plot.already-owner");
            return;
        }
        if (!plugin.ensureEconomy()) {
            messages.sendError(player, "town.economy-missing");
            return;
        }
        Economy economy = plugin.getEconomy();
        double price = plot.getSalePrice();
        double balance = economy.getBalance(player);
        if (price > balance) {
            messages.sendError(player, "plot.not-enough-money");
            return;
        }
        EconomyResponse response = economy.withdrawPlayer(player, price);
        if (!response.transactionSuccess()) {
            messages.sendError(player, "plot.buy-failed", Map.of("error", response.errorMessage));
            return;
        }

        if (plot.isMunicipal()) {
            townManager.deposit(town, price);
        } else {
            economy.depositPlayer(plot.getOwner(), price);
            Player ownerOnline = Bukkit.getPlayerExact(plot.getOwner());
            if (ownerOnline != null) {
                messages.send(ownerOnline, "plot.sold-notify", Map.of("buyer", player.getName(), "price", formatPrice(price)));
            }
        }

        plot.setOwner(player.getName());
        plot.clearSale();
        plot.clearResidents();
        townManager.save();
        messages.send(player, "plot.buy-success", Map.of("price", formatPrice(price)));
    }

    private void handleReclaim(Player player) {
        Town town = getTownAtPlayerChunk(player);
        if (town == null) {
            messages.sendError(player, "plot.not-in-town");
            return;
        }
        if (!town.isMayor(player.getName())) {
            messages.sendError(player, "plot.not-mayor");
            return;
        }
        PlotData plot = town.getPlot(ChunkPosition.fromChunk(player.getLocation().getChunk()));
        if (plot == null) {
            messages.sendError(player, "plot.not-in-town");
            return;
        }
        plot.setOwner(null);
        plot.clearResidents();
        plot.clearSale();
        townManager.save();
        messages.send(player, "plot.reclaim-success");
    }

    private void handleSettings(Player player) {
        Town town = getTownAtPlayerChunk(player);
        if (town == null) {
            messages.sendError(player, "plot.not-in-town");
            return;
        }
        if (!town.isMayor(player.getName())) {
            messages.sendError(player, "plot.not-mayor");
            return;
        }
        PlotData plot = town.getPlot(ChunkPosition.fromChunk(player.getLocation().getChunk()));
        if (plot == null) {
            messages.sendError(player, "plot.not-in-town");
            return;
        }
        plotSettingsMenuManager.open(player, plot);
    }

    private void handleInvite(Player player, String[] args) {
        if (args.length < 2) {
            messages.sendError(player, "plot.invite-usage");
            return;
        }
        Town town = getTownAtPlayerChunk(player);
        if (town == null) {
            messages.sendError(player, "plot.not-in-town");
            return;
        }
        PlotData plot = town.getPlot(ChunkPosition.fromChunk(player.getLocation().getChunk()));
        if (plot == null || plot.isMunicipal()) {
            messages.sendError(player, "plot.not-owner");
            return;
        }
        if (!plot.isOwner(player.getName())) {
            messages.sendError(player, "plot.not-owner");
            return;
        }
        String targetName = args[1];
        if (!town.isMember(targetName)) {
            messages.sendError(player, "plot.invite.not-member");
            return;
        }
        if (plot.addResident(targetName)) {
            townManager.save();
            messages.send(player, "plot.invite-success", Map.of("player", targetName));
            Player targetOnline = Bukkit.getPlayerExact(targetName);
            if (targetOnline != null) {
                messages.send(targetOnline, "plot.invite-notify", Map.of("owner", player.getName()));
            }
        } else {
            messages.sendError(player, "plot.invite-already");
        }
    }

    private void handleKick(Player player, String[] args) {
        if (args.length < 2) {
            messages.sendError(player, "plot.kick-usage");
            return;
        }
        Town town = getTownAtPlayerChunk(player);
        if (town == null) {
            messages.sendError(player, "plot.not-in-town");
            return;
        }
        PlotData plot = town.getPlot(ChunkPosition.fromChunk(player.getLocation().getChunk()));
        if (plot == null || !plot.isOwner(player.getName())) {
            messages.sendError(player, "plot.not-owner");
            return;
        }
        String targetName = args[1];
        if (!plot.removeResident(targetName)) {
            messages.sendError(player, "plot.kick-missing");
            return;
        }
        townManager.save();
        messages.send(player, "plot.kick-success", Map.of("player", targetName));
    }

    private void handleInfo(Player player) {
        Town town = getTownAtPlayerChunk(player);
        if (town == null) {
            messages.sendError(player, "plot.not-in-town");
            return;
        }
        PlotData plot = town.getPlot(ChunkPosition.fromChunk(player.getLocation().getChunk()));
        if (plot == null) {
            messages.sendError(player, "plot.not-in-town");
            return;
        }
        plotInfoMenuManager.open(player, plot);
    }

    private Town getTownAtPlayerChunk(Player player) {
        return townManager.getTownAtChunk(player.getLocation().getChunk());
    }

    private double parseAmount(String raw) {
        try {
            return Double.parseDouble(raw);
        } catch (NumberFormatException ex) {
            return -1;
        }
    }

    private String formatPrice(double price) {
        return String.format(Locale.US, "%.2f", price);
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            return Arrays.asList("sell", "buy", "reclaim", "settings", "invite", "kick", "info")
                    .stream()
                    .filter(it -> it.startsWith(args[0].toLowerCase(Locale.ROOT)))
                    .collect(Collectors.toList());
        }
        if (args.length == 2) {
            String sub = args[0].toLowerCase(Locale.ROOT);
            if (!(sender instanceof Player player)) {
                return Collections.emptyList();
            }
            Town town = townManager.getTownByMember(player.getName());
            if (town == null) {
                return Collections.emptyList();
            }
            return switch (sub) {
                case "invite", "kick" -> town.getCitizens().stream()
                        .map(Object::toString)
                        .filter(name -> name.toLowerCase(Locale.ROOT).startsWith(args[1].toLowerCase(Locale.ROOT)))
                        .collect(Collectors.toList());
                default -> Collections.emptyList();
            };
        }
        return Collections.emptyList();
    }
}