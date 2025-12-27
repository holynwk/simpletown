package com.simpletown.war;

public class ReparationPlan {
    private final String winner;
    private final String loser;
    private final double dailyAmount;
    private int remainingDays;
    private long lastPaidAt;

    public ReparationPlan(String winner, String loser, double dailyAmount, int remainingDays, long lastPaidAt) {
        this.winner = winner;
        this.loser = loser;
        this.dailyAmount = dailyAmount;
        this.remainingDays = remainingDays;
        this.lastPaidAt = lastPaidAt;
    }

    public String getWinner() {
        return winner;
    }

    public String getLoser() {
        return loser;
    }

    public double getDailyAmount() {
        return dailyAmount;
    }

    public int getRemainingDays() {
        return remainingDays;
    }

    public void decrementDay() {
        if (remainingDays > 0) {
            remainingDays--;
        }
    }

    public long getLastPaidAt() {
        return lastPaidAt;
    }

    public void setLastPaidAt(long lastPaidAt) {
        this.lastPaidAt = lastPaidAt;
    }
}