package com.simpletown.service;

import com.simpletown.data.Town;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

public class TownInventoryHolder implements InventoryHolder {
    private final Town town;

    public TownInventoryHolder(Town town) {
        this.town = town;
    }

    public Town getTown() {
        return town;
    }

    @Override
    public Inventory getInventory() {
        return null;
    }
}