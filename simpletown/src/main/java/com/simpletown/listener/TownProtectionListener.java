package com.simpletown.listener;

import com.simpletown.data.ChunkPosition;
import com.simpletown.data.PlotData;
import com.simpletown.data.Town;
import com.simpletown.data.TownFlag;
import com.simpletown.data.TownFlags;
import com.simpletown.data.TownManager;
import com.simpletown.service.MessageService;
import org.bukkit.Chunk;
import org.bukkit.entity.Animals;
import org.bukkit.entity.Monster;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.event.player.PlayerArmorStandManipulateEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.Event.Result;
import org.bukkit.event.player.PlayerInteractEntityEvent;

public class TownProtectionListener implements Listener {
    private final TownManager townManager;
    private final MessageService messages;

    public TownProtectionListener(TownManager townManager, MessageService messages) {
        this.townManager = townManager;
        this.messages = messages;
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.HIGH)
    public void onBlockBreak(BlockBreakEvent event) {
        Player player = event.getPlayer();
        if (!isActionAllowed(player, event.getBlock().getChunk(), TownFlag.BREAK)) {
            event.setCancelled(true);
            messages.sendError(player, "protection.break");
        }
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.HIGH)
    public void onBlockPlace(BlockPlaceEvent event) {
        Player player = event.getPlayer();
        if (!isActionAllowed(player, event.getBlock().getChunk(), TownFlag.PLACE)) {
            event.setCancelled(true);
            messages.sendError(player, "protection.place");
        }
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.HIGH)
    public void onInteract(PlayerInteractEvent event) {
        if (event.getClickedBlock() == null) {
            return;
        }
        Player player = event.getPlayer();
        if (!isActionAllowed(player, event.getClickedBlock().getChunk(), TownFlag.INTERACT)) {
            event.setCancelled(true);
            event.setUseInteractedBlock(Result.DENY);
            messages.sendError(player, "protection.interact");
        }
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.HIGH)
    public void onEntityInteract(PlayerInteractEntityEvent event) {
        Player player = event.getPlayer();
        if (!isActionAllowed(player, event.getRightClicked().getLocation().getChunk(), TownFlag.INTERACT)) {
            event.setCancelled(true);
            messages.sendError(player, "protection.interact");
        }
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.HIGH)
    public void onArmorStand(PlayerArmorStandManipulateEvent event) {
        Player player = event.getPlayer();
        if (!isActionAllowed(player, event.getRightClicked().getLocation().getChunk(), TownFlag.INTERACT)) {
            event.setCancelled(true);
            messages.sendError(player, "protection.interact");
        }
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.HIGH)
    public void onPlayerDamage(EntityDamageByEntityEvent event) {
        if (!(event.getEntity() instanceof Player victim) || !(event.getDamager() instanceof Player attacker)) {
            return;
        }

        if (!isActionAllowed(attacker, victim.getLocation().getChunk(), TownFlag.PVP)) {
            event.setCancelled(true);
            messages.sendError(attacker, "protection.pvp");
        }
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.HIGH)
    public void onCreatureSpawn(CreatureSpawnEvent event) {
        Chunk chunk = event.getLocation().getChunk();
        Town town = townManager.getTownAtChunk(chunk);
        if (town == null) {
            return;
        }

        TownFlags flags = getChunkFlags(town, chunk);
        if (event.getEntity() instanceof Monster && !flags.isEnabled(TownFlag.MONSTER_SPAWN)) {
            event.setCancelled(true);
            return;
        }

        if (event.getEntity() instanceof Animals && !flags.isEnabled(TownFlag.ANIMAL_SPAWN)) {
            event.setCancelled(true);
        }
    }

    private boolean isActionAllowed(Player player, Chunk chunk, TownFlag flag) {
        Town town = townManager.getTownAtChunk(chunk);
        if (town == null) {
            return true;
        }

        if (town.isMayor(player.getName())) {
            return true;
        }

        boolean isMember = town.isMember(player.getName());
        PlotData plot = town.getPlot(ChunkPosition.fromChunk(chunk));
        if (!isMember) {
            if (flag == TownFlag.BREAK || flag == TownFlag.PLACE || flag == TownFlag.INTERACT) {
                return false;
            }
            return town.getOutsiderFlags().isEnabled(flag);
        }

        TownFlags flags = getChunkFlags(plot, town);
        return flags.isEnabled(flag);
    }

    private TownFlags getChunkFlags(Town town, Chunk chunk) {
        PlotData plot = town.getPlot(ChunkPosition.fromChunk(chunk));
        return getChunkFlags(plot, town);
    }

    private TownFlags getChunkFlags(PlotData plot, Town town) {
        return plot == null || plot.isMunicipal() ? town.getCitizenFlags() : plot.getFlags();
    }
}