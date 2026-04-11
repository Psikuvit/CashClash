package me.psikuvit.cashClash.gamemode.impl;

import java.util.UUID;

/**
 * Record representing a president in Protect the President gamemode
 * Consolidates president-related data: UUID, team, deaths, and selected buff
 */
public record President(
        UUID uuid,
        int team,
        int deaths,
        ProtectThePresidentGamemode.PresidentialBuff selectedBuff
) {
    /**
     * Create a new president with default values
     */
    public static President create(UUID uuid, int team) {
        return new President(uuid, team, 0, null);
    }

    /**
     * Create a president with incremented death count
     */
    public President withDeath() {
        return new President(this.uuid, this.team, this.deaths + 1, this.selectedBuff);
    }

    /**
     * Create a president with selected buff
     */
    public President withBuff(ProtectThePresidentGamemode.PresidentialBuff buff) {
        return new President(this.uuid, this.team, this.deaths, buff);
    }

    /**
     * Create a president with reset deaths
     */
    public President withResetDeaths() {
        return new President(this.uuid, this.team, 0, this.selectedBuff);
    }

    /**
     * Create a president with reset selected buff
     */
    public President withResetBuff() {
        return new President(this.uuid, this.team, this.deaths, null);
    }

    /**
     * Check if this president has a selected buff
     */
    public boolean hasSelectedBuff() {
        return selectedBuff != null;
    }
}



