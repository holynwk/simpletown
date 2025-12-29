package com.simpletown.jobs;

import org.bukkit.Material;

public enum JobType {
    LUMBERJACK("jobs.job.lumberjack", Material.IRON_AXE),
    MINER("jobs.job.miner", Material.IRON_PICKAXE),
    FARMER("jobs.job.farmer", Material.IRON_HOE),
    BUTCHER("jobs.job.butcher", Material.IRON_SWORD),
    KILLER("jobs.job.killer", Material.CROSSBOW),
    UNEMPLOYED("jobs.job.unemployed", Material.BARRIER);

    private final String messageKey;
    private final Material icon;

    JobType(String messageKey, Material icon) {
        this.messageKey = messageKey;
        this.icon = icon;
    }

    public String getMessageKey() {
        return messageKey;
    }

    public Material getIcon() {
        return icon;
    }
}