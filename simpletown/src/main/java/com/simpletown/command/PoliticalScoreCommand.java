package com.simpletown.command;

import com.simpletown.service.MessageService;
import com.simpletown.service.PoliticalScoreService;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.Map;

public class PoliticalScoreCommand implements CommandExecutor {
    private final MessageService messages;
    private final PoliticalScoreService politicalScoreService;

    public PoliticalScoreCommand(MessageService messages, PoliticalScoreService politicalScoreService) {
        this.messages = messages;
        this.politicalScoreService = politicalScoreService;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            messages.sendError(sender, "general.only-player");
            return true;
        }

        int score = politicalScoreService.getScore(player.getUniqueId());
        messages.send(player, "political-score.self", Map.of("score", String.valueOf(score)));
        return true;
    }
}