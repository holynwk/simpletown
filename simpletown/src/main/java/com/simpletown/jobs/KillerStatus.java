package com.simpletown.jobs;

import java.util.UUID;

public class KillerStatus {
    private UUID targetId;
    private String targetName;
    private UUID lastTargetId;
    private long assignedAt;
    private long lastCompletedAt;
    private long suspendedUntil;

    public UUID getTargetId() {
        return targetId;
    }

    public void setTarget(UUID targetId, String targetName, long assignedAt) {
        this.targetId = targetId;
        this.targetName = targetName;
        this.assignedAt = assignedAt;
    }

    public void clearTarget() {
        this.targetId = null;
        this.targetName = null;
        this.assignedAt = 0L;
    }

    public String getTargetName() {
        return targetName;
    }

    public UUID getLastTargetId() {
        return lastTargetId;
    }

    public void setLastTargetId(UUID lastTargetId) {
        this.lastTargetId = lastTargetId;
    }

    public long getAssignedAt() {
        return assignedAt;
    }

    public long getLastCompletedAt() {
        return lastCompletedAt;
    }

    public void setLastCompletedAt(long lastCompletedAt) {
        this.lastCompletedAt = lastCompletedAt;
    }

    public long getSuspendedUntil() {
        return suspendedUntil;
    }

    public void setSuspendedUntil(long suspendedUntil) {
        this.suspendedUntil = suspendedUntil;
    }

    public boolean isSuspended(long now) {
        return now < suspendedUntil;
    }

    public String serialize() {
        StringBuilder builder = new StringBuilder();
        builder.append(targetId != null ? targetId : "null").append(";");
        builder.append(targetName != null ? targetName : "null").append(";");
        builder.append(lastTargetId != null ? lastTargetId : "null").append(";");
        builder.append(assignedAt).append(";");
        builder.append(lastCompletedAt).append(";");
        builder.append(suspendedUntil);
        return builder.toString();
    }

    public static KillerStatus deserialize(String raw) {
        KillerStatus status = new KillerStatus();
        if (raw == null || raw.isBlank()) {
            return status;
        }
        String[] parts = raw.split(";");
        if (parts.length > 0 && !"null".equals(parts[0])) {
            status.targetId = UUID.fromString(parts[0]);
        }
        if (parts.length > 1 && !"null".equals(parts[1])) {
            status.targetName = parts[1];
        }
        if (parts.length > 2 && !"null".equals(parts[2])) {
            status.lastTargetId = UUID.fromString(parts[2]);
        }
        if (parts.length > 3) {
            status.assignedAt = parseLong(parts[3]);
        }
        if (parts.length > 4) {
            status.lastCompletedAt = parseLong(parts[4]);
        }
        if (parts.length > 5) {
            status.suspendedUntil = parseLong(parts[5]);
        }
        return status;
    }

    private static long parseLong(String value) {
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException e) {
            return 0L;
        }
    }
}