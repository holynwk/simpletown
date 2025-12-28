package com.simpletown.war;

import com.simpletown.service.MessageService;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;

import java.util.Locale;

public class WarListener implements Listener {
    private final WarManager warManager;
    private final MessageService messages;

    public WarListener(WarManager warManager, MessageService messages) {
        this.warManager = warManager;
        this.messages = messages;
    }

    @EventHandler
    public void onBlockPlace(BlockPlaceEvent event) {
        Block block = event.getBlockPlaced();
        if (block.getType() == Material.RED_BANNER) {
            if (!warManager.placeFlag(event.getPlayer(), block, false)) {
                event.setCancelled(true);
            }
        } else if (block.getType() == Material.GREEN_BANNER) {
            if (!warManager.placeFlag(event.getPlayer(), block, true)) {
                event.setCancelled(true);
            }
        } else if (!warManager.canPlace(event.getPlayer(), block)) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onBlockBreak(BlockBreakEvent event) {
        Block block = event.getBlock();
        if (block.getType() == Material.RED_BANNER || block.getType() == Material.GREEN_BANNER) {
            if (warManager.damageFlag(event.getPlayer(), block)) {
                event.setCancelled(true);
            }
        } else if (!warManager.canBreak(event.getPlayer(), block)) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onDeath(PlayerDeathEvent event) {
        Player victim = event.getEntity();
        Player killer = victim.getKiller();
        warManager.handlePlayerDeath(victim, killer);
    }

    @EventHandler
    public void onRespawn(PlayerRespawnEvent event) {
        warManager.handlePlayerRespawn(event.getPlayer());
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        warManager.handlePlayerQuit(event.getPlayer());
    }

    @EventHandler
    public void onCommand(PlayerCommandPreprocessEvent event) {
        String msg = event.getMessage().toLowerCase(Locale.ROOT);
        if (warManager.isCommandBlocked(event.getPlayer(), msg)) {
            messages.sendError(event.getPlayer(), "war.command-blocked");
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onInteract(PlayerInteractEvent event) {
        if (event.getClickedBlock() == null) {
            return;
        }
        if (!warManager.canInteract(event.getPlayer(), event.getClickedBlock())) {
            event.setCancelled(true);
        }
    }
}