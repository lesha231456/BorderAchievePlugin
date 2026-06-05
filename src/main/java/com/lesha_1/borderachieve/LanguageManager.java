package com.lesha_1.borderachieve;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.InputStream;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class LanguageManager {

    private final JavaPlugin plugin;
    private final String languageCode;
    private final Map<String, Object> messages = new HashMap<>();

    public LanguageManager(JavaPlugin plugin) {
        this.plugin = plugin;
        String configLang = plugin.getConfig().getString("language", "en").toLowerCase();
        if (!configLang.equals("ru") && !configLang.equals("en")) {
            plugin.getLogger().warning("Unknown language '" + configLang + "', using English.");
            this.languageCode = "en";
        } else {
            this.languageCode = configLang;
        }
        loadLanguage();
        plugin.getLogger().info("Language set to: " + languageCode);
    }

    private void loadLanguage() {
        File langFolder = new File(plugin.getDataFolder(), "language");
        if (!langFolder.exists()) langFolder.mkdirs();

        String fileName = languageCode + ".yml";
        File langFile = new File(langFolder, fileName);

        if (!langFile.exists()) {
            String resourcePath = "language/" + fileName;
            try (InputStream in = plugin.getResource(resourcePath)) {
                if (in != null) {
                    plugin.saveResource(resourcePath, false);
                    plugin.getLogger().info("Copied default language file: " + resourcePath);
                } else {
                    langFile.createNewFile();
                    plugin.getLogger().warning("Language resource not found: " + resourcePath + ". Creating empty file.");
                }
            } catch (Exception e) {
                plugin.getLogger().warning("Failed to copy language file: " + e.getMessage());
            }
        }

        YamlConfiguration cfg;
        try {
            cfg = YamlConfiguration.loadConfiguration(langFile);
        } catch (Exception e) {
            plugin.getLogger().warning("Failed to load language file " + fileName + ": " + e.getMessage());
            cfg = new YamlConfiguration();
        }

        for (String key : cfg.getKeys(true)) {
            messages.put(key, cfg.get(key));
        }

        if (messages.isEmpty()) {
            plugin.getLogger().warning("Language file " + fileName + " is empty or invalid, using built-in English messages.");
            loadBuiltinEnglish();
        }
    }

    private void loadBuiltinEnglish() {
        Map<String, Object> builtin = new HashMap<>();
        builtin.put("messages.expanded", "&#42aa49[!] &e%player% &#9dcb44completed an achievement! Border expanded by +%amount% blocks.");
        builtin.put("messages.border_expanded_info", "&#42aa49[!] &#9dcb44Border of world %world% expanded to %new_size% blocks!");
        builtin.put("messages.border_expanded_actionbar", "&#42aa49Border of %world% → %new_size% blocks");
        builtin.put("messages.border_delayed_info", "&aBorder of world %world% will expand to %new_size% blocks after delay!");
        builtin.put("messages.reloaded", "&#42aa49[BorderAchieve] Plugin reloaded successfully!");
        builtin.put("messages.no_perm", "&cYou do not have permission to use this command.");
        builtin.put("gui.title", "          &#42aa49Top Achievements");
        builtin.put("gui.rank_format", "&#42aa49#%rank% &#9dcb44%player%");
        builtin.put("gui.self_rank_format", "&#42aa49#0 &#9dcb44%player%");
        builtin.put("gui.lore.self", List.of("&#264933&m                                            ", "&#42aa49Your score: &#9dcb44%score%", "", "&#42aa49Click to refresh", "&#264933&m                                            "));
        builtin.put("gui.lore.other", List.of("&#264933&m                                            ", "&#42aa49Score: &#9dcb44%score%", "", "&#42aa49Click to refresh", "&#264933&m                                            "));
        builtin.put("log.enabled", "BorderAchieve has been enabled and is ready!");
        builtin.put("log.player_advancement", "Player %player% got advancement: %adv%");
        builtin.put("log.mode_info", "Mode: %mode%, +%amount% blocks, time: %ticks% ticks");
        builtin.put("log.world_border_status", "World %world%: current size = %current%, target = %target%, expand = %expand%");
        builtin.put("log.world_instant_set", "World %world%: border instantly set to %target%");
        builtin.put("log.world_expanding", "World %world%: border expanding to %target% over %seconds% seconds");
        builtin.put("log.world_expanding_corrected", "World %world%: adding %added% blocks, new target %new_target%, remaining time %remaining_sec% sec");
        builtin.put("log.initial_border_set", "Initial border set for world %world%: %size%");
        builtin.put("log.initial_border_done", "Initial world borders set to %size% blocks.");
        builtin.put("log.invalid_format", "Invalid format for border_move_speed: '%input%'. Only t/s/d supported. Using 20t");
        builtin.put("log.speed_parsed", "border_move_speed = %input% → %ticks% ticks");
        builtin.put("world_locking.deny_message", "&#c43d3d[!] &#e04e4eWorld %world% is locked! Need %required% total achievements (currently %current%).");
        builtin.put("world_locking.unlock_broadcast", "&#42aa49[!] &#9dcb44World %world% unlocked! %required% achievements reached.");
        builtin.put("effects.title", "&#42aa49Border Expanded");
        builtin.put("effects.subtitle", "New size: %new_size% blocks");
        builtin.put("messages.help_header", "&#42aa49[BorderAchieve] Commands:");
        builtin.put("world_locking.deny_title", "&#c43d3dAccess Denied");
        builtin.put("world_locking.deny_subtitle", "&#e04e4eNeed %required% achievements (currently %current%)");
        messages.putAll(builtin);
    }

    public String getRaw(String key, Map<String, String> placeholders) {
        Object obj = messages.get(key);
        String msg = (obj != null) ? obj.toString() : key;
        if (placeholders != null) {
            for (Map.Entry<String, String> e : placeholders.entrySet()) {
                msg = msg.replace(e.getKey(), e.getValue());
            }
        }
        return msg;
    }

    public String getRaw(String key) {
        return getRaw(key, null);
    }

    @SuppressWarnings("unchecked")
    public List<String> getStringList(String key) {
        Object obj = messages.get(key);
        if (obj instanceof List) {
            return (List<String>) obj;
        }
        return Collections.emptyList();
    }

    public Component getMessage(Player player, String key, Map<String, String> placeholders) {
        return colorComponent(getRaw(key, placeholders));
    }

    public Component getMessage(Player player, String key) {
        return getMessage(player, key, null);
    }

    public String getConsole(String key, Map<String, String> placeholders) {
        return stripColors(getRaw(key, placeholders));
    }

    public String getConsole(String key) {
        return getConsole(key, null);
    }

    private String stripColors(String input) {
        if (input == null) return "";
        String noHex = input.replaceAll("&#[A-Fa-f0-9]{6}", "");
        noHex = noHex.replaceAll("&([0-9a-fklmnor])", "");
        return org.bukkit.ChatColor.stripColor(org.bukkit.ChatColor.translateAlternateColorCodes('&', noHex));
    }

    public Component colorComponent(String text) {
        if (text == null) return Component.empty();
        Pattern hexPattern = Pattern.compile("&#([A-Fa-f0-9]{6})");
        Matcher matcher = hexPattern.matcher(text);
        StringBuilder sb = new StringBuilder();
        while (matcher.find()) {
            String hex = matcher.group(1);
            StringBuilder replacement = new StringBuilder("§x");
            for (char c : hex.toCharArray()) replacement.append("§").append(c);
            matcher.appendReplacement(sb, replacement.toString());
        }
        matcher.appendTail(sb);
        String colored = org.bukkit.ChatColor.translateAlternateColorCodes('&', sb.toString());
        return LegacyComponentSerializer.legacySection().deserialize(colored).decoration(TextDecoration.ITALIC, false);
    }

    public Component getGuiTitle() {
        return colorComponent(getRaw("gui.title"));
    }

    public List<String> getLore(boolean isSelf) {
        String key = isSelf ? "gui.lore.self" : "gui.lore.other";
        List<String> lore = getStringList(key);
        if (lore.isEmpty()) {
            if (isSelf) {
                return Arrays.asList(
                        "&#264933&m                                            ",
                        "&#42aa49Your score: &#9dcb44%score%",
                        "",
                        "&#42aa49Click to refresh",
                        "&#264933&m                                            "
                );
            } else {
                return Arrays.asList(
                        "&#264933&m                                            ",
                        "&#42aa49Score: &#9dcb44%score%",
                        "",
                        "&#42aa49Click to refresh",
                        "&#264933&m                                            "
                );
            }
        }
        return lore;
    }
}