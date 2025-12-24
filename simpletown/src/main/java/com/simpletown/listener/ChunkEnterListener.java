package com.simpletown.listener;

import com.simpletown.data.ChunkPosition;
import com.simpletown.data.Town;
import com.simpletown.data.TownManager;
import com.simpletown.service.MessageService;
import net.md_5.bungee.api.ChatMessageType;
import net.md_5.bungee.api.chat.TextComponent;
import org.bukkit.Chunk;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerMoveEvent;

import java.util.Map;

public class ChunkEnterListener implements Listener {
    private final TownManager townManager;
    private final MessageService messages;

    public ChunkEnterListener(TownManager townManager, MessageService messages) {
        this.townManager = townManager;
        this.messages = messages;
    }

    @EventHandler
    public void onPlayerMove(PlayerMoveEvent event) {
        if (event.getFrom() == null || event.getTo() == null) {
            return;
        }

        Chunk fromChunk = event.getFrom().getChunk();
        Chunk toChunk = event.getTo().getChunk();
        if (fromChunk.getX() == toChunk.getX() && fromChunk.getZ() == toChunk.getZ() && fromChunk.getWorld().equals(toChunk.getWorld())) {
            return;
        }

        Town fromTown = townManager.getTownAtChunk(fromChunk);
        Town toTown = townManager.getTownAtChunk(toChunk);
        ChunkPosition toPos = ChunkPosition.fromChunk(toChunk);
        ChunkPosition fromPos = ChunkPosition.fromChunk(fromChunk);
        boolean sameTown = fromTown != null && toTown != null && fromTown.getName().equalsIgnoreCase(toTown.getName());
        boolean toCapital = toTown != null && toTown.getCapital().equals(toPos);
        boolean fromCapital = fromTown != null && fromTown.getCapital().equals(fromPos);
        if (fromTown == null && toTown == null) {
            return;
        }

        Player player = event.getPlayer();
        if (toTown == null) {
            sendActionBar(player, messages.get("chunk.enter.wilderness"));
            return;
        }

        if (sameTown) {
            if (toCapital && !fromCapital) {
                sendActionBar(player, messages.format("chunk.enter.center", Map.of(
                        "town", toTown.getName(),
                        "nation", ""
                )));
            } else if (fromCapital && !toCapital) {
                sendActionBar(player, messages.format("chunk.enter.town", Map.of(
                        "town", toTown.getName(),
                        "nation", ""
                )));
            }
            return;
        }

        String path = toCapital ? "chunk.enter.center" : "chunk.enter.town";
        sendActionBar(player, messages.format(path, Map.of(
                "town", toTown.getName(),
                "nation", ""
        )));
    }

    private void sendActionBar(Player player, String message) {
        player.spigot().sendMessage(ChatMessageType.ACTION_BAR, new TextComponent(message));
    }
}