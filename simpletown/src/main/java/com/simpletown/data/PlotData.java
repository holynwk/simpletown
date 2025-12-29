package com.simpletown.data;

import java.util.*;

public class PlotData {
    private String owner;
    private final Set<String> residents;
    private double salePrice;
    private final TownFlags flags;

    public PlotData(String owner, Collection<String> residents, double salePrice, TownFlags flags) {
        this.owner = normalize(owner);
        this.residents = new HashSet<>();
        if (residents != null) {
            for (String resident : residents) {
                String normalized = normalize(resident);
                if (!normalized.isEmpty()) {
                    this.residents.add(normalized);
                }
            }
        }
        this.salePrice = Math.max(0, salePrice);
        this.flags = new TownFlags(flags == null ? new TownFlags() : flags);
    }

    public static PlotData createMunicipal(TownFlags defaults) {
        return new PlotData(null, Collections.emptySet(), 0, defaults);
    }

    public boolean isMunicipal() {
        return owner == null || owner.isEmpty();
    }

    public String getOwner() {
        return owner;
    }

    public void setOwner(String owner) {
        this.owner = normalize(owner);
    }

    public boolean isOwner(String playerName) {
        if (isMunicipal()) {
            return false;
        }
        return owner.equals(normalize(playerName));
    }

    public Set<String> getResidents() {
        return Collections.unmodifiableSet(residents);
    }

    public boolean addResident(String playerName) {
        String normalized = normalize(playerName);
        if (normalized.isEmpty()) {
            return false;
        }
        return residents.add(normalized);
    }

    public boolean removeResident(String playerName) {
        return residents.remove(normalize(playerName));
    }

    public void clearResidents() {
        residents.clear();
    }

    public double getSalePrice() {
        return salePrice;
    }

    public void setSalePrice(double salePrice) {
        this.salePrice = Math.max(0, salePrice);
    }

    public boolean isForSale() {
        return salePrice > 0;
    }

    public void clearSale() {
        salePrice = 0;
    }

    public TownFlags getFlags() {
        return flags;
    }

    private String normalize(String name) {
        return name == null ? "" : name.toLowerCase(Locale.ROOT);
    }
}