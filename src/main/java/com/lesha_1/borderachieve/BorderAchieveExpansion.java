package com.lesha_1.borderachieve;

import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.UUID;

public class BorderAchieveExpansion extends PlaceholderExpansion {

    private final BorderAchievePlugin plugin;

    public BorderAchieveExpansion(BorderAchievePlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public @NotNull String getIdentifier() {
        return "borderachieve";
    }

    @Override
    public @NotNull String getAuthor() {
        return "lesha_1";
    }

    @Override
    public @NotNull String getVersion() {
        return plugin.getDescription().getVersion();
    }

    @Override
    public boolean persist() {
        return true;
    }

    @Override
    public String onRequest(OfflinePlayer player, @NotNull String params) {
        if (params.equals("count")) {
            int count = plugin.getPlayerAchievementCount(player.getUniqueId());
            return String.valueOf(count);
        }
        if (params.equals("rank")) {
            int rank = plugin.getPlayerRank(player.getUniqueId());
            return String.valueOf(rank);
        }
        if (params.equals("total")) {
            return String.valueOf(plugin.getTotalAchievementsCount());
        }
        if (params.startsWith("locked_")) {
            String worldName = params.substring(7);
            boolean locked = plugin.isWorldLocked(worldName, (player instanceof Player) ? (Player) player : null);
            return String.valueOf(locked);
        }
        return null;
    }
}