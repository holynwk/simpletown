package com.simpletown.service;

import org.bukkit.ChatColor;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

public class MessageService {
    private final Plugin plugin;
    private FileConfiguration messages;
    private final Map<UUID, String> lastError = new HashMap<>();

    public MessageService(Plugin plugin) {
        this.plugin = plugin;
        load();
    }

    public void reload() {
        load();
    }

    public String get(String path) {
        String raw = messages.getString(path, path);
        return ChatColor.translateAlternateColorCodes('&', raw);
    }

    public String format(String path, Map<String, String> placeholders) {
        String message = get(path);
        for (Map.Entry<String, String> entry : placeholders.entrySet()) {
            message = message.replace("{" + entry.getKey() + "}", entry.getValue());
        }
        return message;
    }

    public void send(CommandSender sender, String path) {
        clearLastError(sender);
        sender.sendMessage(get(path));
    }

    public void send(CommandSender sender, String path, Map<String, String> placeholders) {
        clearLastError(sender);
        sender.sendMessage(format(path, placeholders));
    }

    public void sendError(CommandSender sender, String path) {
        sendError(sender, path, Map.of());
    }

    public void sendError(CommandSender sender, String path, Map<String, String> placeholders) {
        if (sender instanceof Player player) {
            String key = path.toLowerCase(Locale.ROOT);
            UUID uuid = player.getUniqueId();
            if (key.equals(lastError.get(uuid))) {
                return;
            }
            lastError.put(uuid, key);
        }
        sender.sendMessage(format(path, placeholders));
    }

    public void clearLastError(CommandSender sender) {
        if (sender instanceof Player player) {
            lastError.remove(player.getUniqueId());
        }
    }

    private void load() {
        File messagesFile = new File(plugin.getDataFolder(), "messages.yml");
        if (!messagesFile.exists()) {
            plugin.saveResource("messages.yml", false);
        }

        messages = new YamlConfiguration();
        try {
            messages.load(messagesFile);
        } catch (IOException | InvalidConfigurationException e) {
            plugin.getLogger().warning("Не удалось загрузить messages.yml: " + e.getMessage());
        }
    }
}