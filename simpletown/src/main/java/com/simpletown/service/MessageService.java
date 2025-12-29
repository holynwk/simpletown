package com.simpletown.service;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

public class MessageService {
    private static final String PREFIX = ChatColor.GOLD.toString() + ChatColor.BOLD + "[Города]" + ChatColor.RESET + " ";
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

    public java.util.List<String> getList(String path) {
        java.util.List<String> list = messages.getStringList(path);
        java.util.List<String> formatted = new java.util.ArrayList<>();
        for (String line : list) {
            formatted.add(ChatColor.translateAlternateColorCodes('&', line));
        }
        return formatted;
    }

    public void send(CommandSender sender, String path) {
        clearLastError(sender);
        sender.sendMessage(prefixed(get(path)));
    }

    public void send(CommandSender sender, String path, Map<String, String> placeholders) {
        clearLastError(sender);
        sender.sendMessage(prefixed(format(path, placeholders)));
    }

    public void sendError(CommandSender sender, String path) {
        sendError(sender, path, Map.of());
    }

    public void sendError(CommandSender sender, String path, Map<String, String> placeholders) {
        String message = prefixed(format(path, placeholders));
        if (sender instanceof Player player) {
            UUID uuid = player.getUniqueId();
            String lastMessage = lastError.get(uuid);
            if (message.equals(lastMessage)) {
                return;
            }
            lastError.put(uuid, message);
        }
        sender.sendMessage(message);
    }

    public void sendRaw(CommandSender sender, String message) {
        sender.sendMessage(prefixed(message));
    }

    public void clearLastError(CommandSender sender) {
        if (sender instanceof Player player) {
            lastError.remove(player.getUniqueId());
        }
    }
    public void broadcast(String path) {
        Bukkit.broadcastMessage(prefixed(get(path)));
    }

    public void broadcast(String path, Map<String, String> placeholders) {
        Bukkit.broadcastMessage(prefixed(format(path, placeholders)));
    }
    public String prefixed(String message) {
        return PREFIX + message;
    }

    private void load() {
        File messagesFile = new File(plugin.getDataFolder(), "messages.yml");
        if (!messagesFile.exists()) {
            plugin.saveResource("messages.yml", false);
        }

        YamlConfiguration defaults = new YamlConfiguration();
        try (InputStream stream = plugin.getResource("messages.yml")) {
            if (stream != null) {
                defaults.load(new InputStreamReader(stream, StandardCharsets.UTF_8));
            }
        } catch (IOException | InvalidConfigurationException e) {
            plugin.getLogger().warning("Не удалось загрузить встроенный messages.yml: " + e.getMessage());
        }

        messages = new YamlConfiguration();
        try {
            messages.load(messagesFile);
        } catch (IOException | InvalidConfigurationException e) {
            plugin.getLogger().warning("Не удалось загрузить messages.yml: " + e.getMessage());
            messages = new YamlConfiguration();
        }

        if (!defaults.getKeys(true).isEmpty()) {
            messages.setDefaults(defaults);
            messages.options().copyDefaults(true);
            try {
                messages.save(messagesFile);
            } catch (IOException e) {
                plugin.getLogger().warning("Не удалось сохранить обновленный messages.yml: " + e.getMessage());
            }
        }
    }
}