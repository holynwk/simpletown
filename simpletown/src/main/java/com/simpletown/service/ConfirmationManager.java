package com.simpletown.service;

import net.md_5.bungee.api.chat.ClickEvent;
import net.md_5.bungee.api.chat.TextComponent;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import com.simpletown.service.MessageService;

public class ConfirmationManager {
    private final Map<UUID, ConfirmationRequest> pending = new HashMap<>();
    private final MessageService messages;

    public ConfirmationManager(MessageService messages) {
        this.messages = messages;
    }

    public void sendConfirmation(Player player, String actionLabel, Runnable onAccept, Runnable onDeny) {
        sendConfirmation(player, player, actionLabel, onAccept, onDeny);
    }

    public boolean sendConfirmation(Player player, org.bukkit.command.CommandSender notifier, String actionLabel, Runnable onAccept, Runnable onDeny) {
        ConfirmationRequest existing = pending.get(player.getUniqueId());
        if (existing != null && existing.getLabel().equalsIgnoreCase(actionLabel)) {
            messages.sendError(notifier, "confirmation.pending", Map.of("action", existing.getLabel()));
            return false;
        }
        if (existing != null) {
            messages.sendError(notifier, "confirmation.pending", Map.of("action", existing.getLabel()));
            return false;
        }

        pending.put(player.getUniqueId(), new ConfirmationRequest(actionLabel, onAccept, onDeny));

        TextComponent question = new TextComponent(messages.format("confirmation.prompt", Map.of("action", actionLabel)));
        messages.sendRaw(player, question.getText());

        TextComponent accept = new TextComponent("[Принять]");
        accept.setColor(net.md_5.bungee.api.ChatColor.GREEN);
        accept.setClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/town confirm accept"));

        TextComponent deny = new TextComponent("[Отклонить]");
        deny.setColor(net.md_5.bungee.api.ChatColor.RED);
        deny.setClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/town confirm deny"));

        TextComponent combined = new TextComponent("");
        combined.addExtra(accept);
        combined.addExtra(new TextComponent(" "));
        combined.addExtra(deny);
        player.spigot().sendMessage(combined);
    }

    public boolean handleResponse(CommandSender sender, String response) {
        if (!(sender instanceof Player player)) {
            messages.sendError(sender, "confirmation.player-only-response");
            return true;
        }

        ConfirmationRequest request = pending.remove(player.getUniqueId());
        if (request == null) {
            messages.sendError(player, "confirmation.no-requests");
            return true;
        }

        if (response.equalsIgnoreCase("accept")) {
            request.getOnAccept().run();
            messages.send(player, "confirmation.accepted", Map.of("action", request.getLabel()));
        } else {
            if (request.getOnDeny() != null) {
                request.getOnDeny().run();
            }
            messages.sendError(player, "confirmation.denied", Map.of("action", request.getLabel()));
        }
        return true;
    }

    public boolean hasPending(Player player) {
        return pending.containsKey(player.getUniqueId());
    }

    private static class ConfirmationRequest {
        private final String label;
        private final Runnable onAccept;
        private final Runnable onDeny;

        ConfirmationRequest(String label, Runnable onAccept, Runnable onDeny) {
            this.label = label;
            this.onAccept = onAccept;
            this.onDeny = onDeny;
        }

        public String getLabel() {
            return label;
        }

        public Runnable getOnAccept() {
            return onAccept;
        }

        public Runnable getOnDeny() {
            return onDeny;
        }
    }
}