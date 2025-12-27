package com.simpletown.war;

import com.simpletown.data.Town;

public class WarConflict {
    private final String attacker;
    private final String defender;
    private final WarType type;
    private WarStatus status;
    private long preparationEndsAt;
    private long conflictEndsAt;
    private int attackerPoints;
    private int defenderPoints;
    private final java.util.Set<com.simpletown.data.ChunkPosition> attackerOccupied = new java.util.HashSet<>();
    private final java.util.Set<com.simpletown.data.ChunkPosition> defenderOccupied = new java.util.HashSet<>();
    private final java.util.List<WarFlag> activeFlags = new java.util.ArrayList<>();
    private final java.util.Map<String, Long> lastKillTimestamps = new java.util.HashMap<>();
    private String awaitingResultWinner;
    private boolean pactPending;

    public WarConflict(Town attacker, Town defender, WarType type, WarStatus status, long preparationEndsAt, long conflictEndsAt) {
        this(attacker.getName(), defender.getName(), type, status, preparationEndsAt, conflictEndsAt);
    }

    public WarConflict(String attacker, String defender, WarType type, WarStatus status, long preparationEndsAt, long conflictEndsAt) {
        this.attacker = attacker;
        this.defender = defender;
        this.type = type;
        this.status = status;
        this.preparationEndsAt = preparationEndsAt;
        this.conflictEndsAt = conflictEndsAt;
        this.attackerPoints = 0;
        this.defenderPoints = 0;
    }

    public String getAttacker() {
        return attacker;
    }

    public String getDefender() {
        return defender;
    }

    public WarType getType() {
        return type;
    }

    public WarStatus getStatus() {
        return status;
    }

    public void setStatus(WarStatus status) {
        this.status = status;
    }

    public long getPreparationEndsAt() {
        return preparationEndsAt;
    }

    public void setPreparationEndsAt(long preparationEndsAt) {
        this.preparationEndsAt = preparationEndsAt;
    }

    public long getConflictEndsAt() {
        return conflictEndsAt;
    }

    public void setConflictEndsAt(long conflictEndsAt) {
        this.conflictEndsAt = conflictEndsAt;
    }

    public java.util.Map<String, Long> getLastKillTimestamps() {
        return lastKillTimestamps;
    }

    public int getAttackerPoints() {
        return attackerPoints;
    }

    public int getDefenderPoints() {
        return defenderPoints;
    }

    public void addAttackerPoints(int delta) {
        attackerPoints += delta;
    }

    public void addDefenderPoints(int delta) {
        defenderPoints += delta;
    }

    public java.util.Set<com.simpletown.data.ChunkPosition> getAttackerOccupied() {
        return attackerOccupied;
    }

    public java.util.Set<com.simpletown.data.ChunkPosition> getDefenderOccupied() {
        return defenderOccupied;
    }

    public java.util.List<WarFlag> getActiveFlags() {
        return activeFlags;
    }

    public String getAwaitingResultWinner() {
        return awaitingResultWinner;
    }

    public void setAwaitingResultWinner(String awaitingResultWinner) {
        this.awaitingResultWinner = awaitingResultWinner;
    }

    public boolean isPactPending() {
        return pactPending;
    }

    public void setPactPending(boolean pactPending) {
        this.pactPending = pactPending;
    }
}