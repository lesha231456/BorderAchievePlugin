package com.lesha_1.borderachieve;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import net.kyori.adventure.title.Title;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.advancement.Advancement;
import org.bukkit.advancement.AdvancementProgress;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.PlayerAdvancementDoneEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerPortalEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.SkullMeta;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;

import java.io.File;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public class BorderAchievePlugin extends JavaPlugin implements Listener {

    private File dataFile;
    private FileConfiguration dataConfig;
    private LanguageManager lang;
    private static final String TARGET_BORDER_PATH = "target_borders.";

    private final Map<World, BukkitTask> delayedTasks = new HashMap<>();
    private final Map<World, Double> pendingTargetSize = new HashMap<>();
    private final Map<World, Double> targetBorderSize = new HashMap<>();

    private static class SmoothMoveData {
        long endTime;
        double targetSize;
        long durationMillis;
    }
    private final Map<World, SmoothMoveData> smoothMoveData = new HashMap<>();

    private boolean worldLockingEnabled;
    private final Map<String, WorldLockInfo> lockedWorldsInfo = new HashMap<>();
    private final Set<String> unlockedWorlds = new HashSet<>();
    private final Map<UUID, Set<String>> temporaryUnlocks = new HashMap<>();

    private final Map<UUID, Integer> achievementCache = new ConcurrentHashMap<>();

    private BukkitTask saveTask = null;
    private boolean saveScheduled = false;

    private static class WorldLockInfo {
        int requiredAchievements;
    }

    private BorderAchieveExpansion papiExpansion = null;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        createDataFile();
        loadTargetBorders();
        saveDocumentation();
        lang = new LanguageManager(this);
        getServer().getPluginManager().registerEvents(this, this);

        loadWorldLockingSettings();
        loadTemporaryUnlocks();
        if (worldLockingEnabled) {
            checkAndUnlockWorlds();
        }

        checkAndSetInitialBorders();
        rebuildAchievementCache();
        for (Player p : Bukkit.getOnlinePlayers()) {
            updatePlayerAchievements(p);
        }

        if (Bukkit.getPluginManager().isPluginEnabled("PlaceholderAPI")) {
            papiExpansion = new BorderAchieveExpansion(this);
            papiExpansion.register();
            getLogger().info("PlaceholderAPI expansion registered.");
        }

        getLogger().info(lang != null ? lang.getConsole("log.enabled") : "BorderAchieve enabled!");

        new BukkitRunnable() {
            @Override
            public void run() {
                saveDataConfig();
            }
        }.runTaskTimerAsynchronously(this, 20 * 60 * 5, 20 * 60 * 5);
    }

    @Override
    public void onDisable() {
        if (papiExpansion != null) {
            papiExpansion.unregister();
        }
        saveDataConfig();
        for (BukkitTask task : delayedTasks.values()) task.cancel();
        delayedTasks.clear();
    }

    private void loadTemporaryUnlocks() {
        temporaryUnlocks.clear();
        ConfigurationSection tempSection = dataConfig.getConfigurationSection("temporary_unlocks");
        if (tempSection == null) return;
        for (String uuidStr : tempSection.getKeys(false)) {
            try {
                UUID uuid = UUID.fromString(uuidStr);
                Set<String> worlds = new HashSet<>(dataConfig.getStringList("temporary_unlocks." + uuidStr));
                temporaryUnlocks.put(uuid, worlds);
            } catch (IllegalArgumentException ignored) {}
        }
    }

    private void saveTemporaryUnlocks() {
        dataConfig.set("temporary_unlocks", null);
        for (Map.Entry<UUID, Set<String>> entry : temporaryUnlocks.entrySet()) {
            dataConfig.set("temporary_unlocks." + entry.getKey().toString(), new ArrayList<>(entry.getValue()));
        }
        scheduleSave();
    }

    public Map<UUID, Integer> getAchievementCache() {
        return Collections.unmodifiableMap(achievementCache);
    }

    public int getPlayerAchievementCount(UUID uuid) {
        return achievementCache.getOrDefault(uuid, 0);
    }

    private void rebuildAchievementCache() {
        achievementCache.clear();
        if (dataConfig.getConfigurationSection("achievements") == null) return;
        for (String key : dataConfig.getConfigurationSection("achievements").getKeys(false)) {
            try {
                UUID uuid = UUID.fromString(key);
                int count = dataConfig.getInt("achievements." + key);
                achievementCache.put(uuid, count);
            } catch (IllegalArgumentException ignored) {
                getLogger().warning("Invalid UUID in data.yml: " + key);
            }
        }
    }

    private void scheduleSave() {
        if (saveScheduled) return;
        saveScheduled = true;
        saveTask = new BukkitRunnable() {
            @Override
            public void run() {
                saveDataConfigInternal();
                saveScheduled = false;
            }
        }.runTaskLaterAsynchronously(this, 5 * 20);
    }

    private void saveDataConfigInternal() {
        try {
            dataConfig.save(dataFile);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void saveDataConfig() {
        if (saveTask != null) {
            saveTask.cancel();
            saveTask = null;
        }
        saveScheduled = false;
        saveDataConfigInternal();
    }

    private void loadWorldLockingSettings() {
        worldLockingEnabled = getConfig().getBoolean("world_locking.enabled", false);
        if (!worldLockingEnabled) return;

        boolean applyToAll = getConfig().getBoolean("world_locking.apply_to_all_worlds_for_locking", false);
        int defaultRequired = getConfig().getInt("world_locking.default_required", 64);
        if (defaultRequired > 200) defaultRequired = 200;
        if (defaultRequired < 1) defaultRequired = 1;

        ConfigurationSection worldsSection = getConfig().getConfigurationSection("world_locking.worlds");
        lockedWorldsInfo.clear();

        Set<String> worldsToLock = new HashSet<>();
        if (applyToAll) {
            for (World world : Bukkit.getWorlds()) {
                worldsToLock.add(world.getName());
            }
        } else if (worldsSection != null) {
            worldsToLock.addAll(worldsSection.getKeys(false));
        }

        for (String worldName : worldsToLock) {
            int required = defaultRequired;
            if (worldsSection != null && worldsSection.contains(worldName)) {
                required = worldsSection.getInt(worldName + ".required_achievements", defaultRequired);
                if (required > 200) required = 200;
                if (required < 1) required = 1;
            }
            WorldLockInfo info = new WorldLockInfo();
            info.requiredAchievements = required;
            lockedWorldsInfo.put(worldName, info);
        }

        unlockedWorlds.clear();
        List<String> unlockedList = dataConfig.getStringList("unlocked_worlds");
        if (unlockedList != null) {
            unlockedWorlds.addAll(unlockedList);
        }
    }

    private void saveUnlockedWorlds() {
        dataConfig.set("unlocked_worlds", new ArrayList<>(unlockedWorlds));
        scheduleSave();
    }

    public int getTotalAchievementsCount() {
        return achievementCache.values().stream().mapToInt(Integer::intValue).sum();
    }

    private void checkAndUnlockWorlds() {
        if (!worldLockingEnabled) return;
        int currentTotal = getTotalAchievementsCount();
        boolean changed = false;

        for (Map.Entry<String, WorldLockInfo> entry : lockedWorldsInfo.entrySet()) {
            String worldName = entry.getKey();
            WorldLockInfo info = entry.getValue();
            if (unlockedWorlds.contains(worldName)) continue;

            if (currentTotal >= info.requiredAchievements) {
                unlockedWorlds.add(worldName);
                changed = true;

                String broadcastMsg = lang.getRaw("world_locking.unlock_broadcast",
                        Map.of("%world%", worldName,
                               "%required%", String.valueOf(info.requiredAchievements)));
                Bukkit.broadcast(colorComponent(broadcastMsg));
                getLogger().info("World " + worldName + " unlocked! Total achievements: " +
                        currentTotal + "/" + info.requiredAchievements);
            }
        }

        if (changed) {
            saveUnlockedWorlds();
        }
    }

    public boolean isWorldLocked(String worldName, Player player) {
        if (!worldLockingEnabled) return false;
        if (unlockedWorlds.contains(worldName)) return false;
        if (player != null && temporaryUnlocks.containsKey(player.getUniqueId())) {
            if (temporaryUnlocks.get(player.getUniqueId()).contains(worldName)) return false;
        }
        return lockedWorldsInfo.containsKey(worldName);
    }

    private void handleLockedWorldTeleport(Player player, World targetWorld) {
        String worldName = targetWorld.getName();
        WorldLockInfo info = lockedWorldsInfo.get(worldName);
        if (info == null) return;
        int currentTotal = getTotalAchievementsCount();
        String denyMsg = lang.getRaw("world_locking.deny_message",
                Map.of("%world%", worldName,
                       "%required%", String.valueOf(info.requiredAchievements),
                       "%current%", String.valueOf(currentTotal)));
        player.sendMessage(colorComponent(denyMsg));
        if (getConfig().getBoolean("world_locking.show_deny_title", true)) {
            player.showTitle(Title.title(
                    colorComponent("&#c43d3dДоступ запрещён"),
                    colorComponent("&#e04e4eНужно " + info.requiredAchievements + " достижений (сейчас " + currentTotal + ")"),
                    Title.Times.times(Duration.ofMillis(500), Duration.ofMillis(2000), Duration.ofMillis(500))
            ));
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onPlayerPortal(PlayerPortalEvent event) {
        if (event.isCancelled()) return;
        World toWorld = event.getTo() != null ? event.getTo().getWorld() : null;
        if (toWorld == null) return;
        if (isWorldLocked(toWorld.getName(), event.getPlayer())) {
            event.setCancelled(true);
            handleLockedWorldTeleport(event.getPlayer(), toWorld);
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onPlayerTeleport(PlayerTeleportEvent event) {
        if (event.isCancelled()) return;
        World toWorld = event.getTo().getWorld();
        if (toWorld == null) return;
        if (isWorldLocked(toWorld.getName(), event.getPlayer())) {
            event.setCancelled(true);
            handleLockedWorldTeleport(event.getPlayer(), toWorld);
        }
    }

    private void loadTargetBorders() {
        for (World world : Bukkit.getWorlds()) {
            String path = TARGET_BORDER_PATH + world.getName();
            if (dataConfig.contains(path)) {
                double saved = dataConfig.getDouble(path);
                targetBorderSize.put(world, saved);
                if (getConfig().getBoolean("debug", false))
                    getLogger().info("Loaded target border for world " + world.getName() + ": " + saved);
            } else {
                targetBorderSize.put(world, world.getWorldBorder().getSize());
            }
        }
    }

    private void saveTargetBorder(World world) {
        dataConfig.set(TARGET_BORDER_PATH + world.getName(), targetBorderSize.get(world));
        scheduleSave();
    }

    private void saveDocumentation() {
        File docFile = new File(getDataFolder(), "documentation.yml");
        if (!docFile.exists() && getResource("documentation.yml") != null) {
            saveResource("documentation.yml", false);
            getLogger().info("documentation.yml created.");
        }
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        updatePlayerAchievements(event.getPlayer());
    }

    @EventHandler
    public void onAdvancementDone(PlayerAdvancementDoneEvent event) {
        Player player = event.getPlayer();
        Advancement advancement = event.getAdvancement();
        String advancementKey = advancement.getKey().getKey();

        if (advancementKey.startsWith("recipes/")) return;
        if (advancement.getDisplay() == null) return;

        updatePlayerAchievements(player);

        if (worldLockingEnabled) {
            checkAndUnlockWorlds();
        }

        if (!getConfig().getBoolean("settings.enable_border_expansion", true)) return;

        double baseExpand = getConfig().getDouble("settings.expand_amount", 15.0);
        String mode = getConfig().getString("settings.border_change_mode", "smooth");
        String timeString = getConfig().getString("settings.border_move_speed", "20t");
        long ticks = parseTimeToTicks(timeString);
        boolean applyToAll = getConfig().getBoolean("settings.apply_to_all_worlds", true);
        List<String> allowedWorlds = getConfig().getStringList("settings.allowed_worlds");
        boolean notifyOnlyPlayer = getConfig().getBoolean("settings.notify_only_player", true);

        if (getConfig().getBoolean("debug", false)) {
            getLogger().info(lang.getConsole("log.player_advancement",
                    Map.of("%player%", player.getName(), "%adv%", advancementKey)));
        }

        for (World world : Bukkit.getWorlds()) {
            if (!applyToAll && !allowedWorlds.contains(world.getName())) continue;

            double expand = baseExpand;
            if (world.getEnvironment() == World.Environment.NETHER || world.getName().toLowerCase().contains("nether")) {
                expand = baseExpand / 8.0;
            }

            double currentTarget = targetBorderSize.getOrDefault(world, world.getWorldBorder().getSize());
            if (Math.abs(currentTarget - world.getWorldBorder().getSize()) > 0.01) {
                currentTarget = world.getWorldBorder().getSize();
                targetBorderSize.put(world, currentTarget);
                saveTargetBorder(world);
            }

            double newTarget = currentTarget + expand;
            targetBorderSize.put(world, newTarget);
            saveTargetBorder(world);

            if (mode.equalsIgnoreCase("delay")) {
                pendingTargetSize.put(world, newTarget);
                if (delayedTasks.containsKey(world)) {
                    delayedTasks.get(world).cancel();
                }
                BukkitTask task = new BukkitRunnable() {
                    @Override
                    public void run() {
                        Double target = pendingTargetSize.get(world);
                        if (target == null) return;
                        world.getWorldBorder().setSize(target, 0);
                        sendBorderNotification(player, world, target, notifyOnlyPlayer);
                        pendingTargetSize.remove(world);
                        delayedTasks.remove(world);
                        targetBorderSize.put(world, world.getWorldBorder().getSize());
                        saveTargetBorder(world);
                        smoothMoveData.remove(world);
                    }
                }.runTaskLater(this, ticks);
                delayedTasks.put(world, task);

                String infoMsg = lang.getRaw("messages.border_delayed_info",
                        Map.of("%world%", world.getName(), "%new_size%", String.format("%.1f", newTarget)));
                player.sendMessage(colorComponent(infoMsg));
            } else {
                scheduleSmoothExpansion(world, expand, ticks, player, notifyOnlyPlayer);
            }
        }

        String expandedMsg = lang.getRaw("messages.expanded",
                Map.of("%player%", player.getName(), "%amount%", String.valueOf(baseExpand)));
        Bukkit.broadcast(colorComponent(expandedMsg));
    }

    private void scheduleSmoothExpansion(World world, double addBlocks, long ticks, Player player, boolean notifyOnlyPlayer) {
        long now = System.currentTimeMillis();
        double currentActualSize = world.getWorldBorder().getSize();
        SmoothMoveData data = smoothMoveData.get(world);

        if (data == null || now >= data.endTime) {
            double newTarget = currentActualSize + addBlocks;
            long durationSeconds = (long) Math.ceil(ticks / 20.0);
            world.getWorldBorder().setSize(newTarget, durationSeconds);
            SmoothMoveData newData = new SmoothMoveData();
            newData.targetSize = newTarget;
            newData.durationMillis = ticks * 50L;
            newData.endTime = now + newData.durationMillis;
            smoothMoveData.put(world, newData);
            sendBorderNotification(player, world, newTarget, notifyOnlyPlayer);
            return;
        }

        long remainingMillis = data.endTime - now;
        if (remainingMillis < 0) remainingMillis = 0;
        double remainingSeconds = remainingMillis / 1000.0;
        double newTarget = data.targetSize + addBlocks;
        world.getWorldBorder().setSize(newTarget, (long) Math.ceil(remainingSeconds));
        data.targetSize = newTarget;
        data.endTime = now + remainingMillis;
        sendBorderNotification(player, world, newTarget, notifyOnlyPlayer);
    }

    private void sendBorderNotification(Player player, World world, double newSize, boolean onlyPlayer) {
        String infoMsg = lang.getRaw("messages.border_expanded_info",
                Map.of("%world%", world.getName(), "%new_size%", String.format("%.1f", newSize)));
        String actionBarMsg = lang.getRaw("messages.border_expanded_actionbar",
                Map.of("%world%", world.getName(), "%new_size%", String.format("%.1f", newSize)));

        playExpandEffect(world, newSize);

        if (onlyPlayer) {
            player.sendMessage(colorComponent(infoMsg));
            if (actionBarMsg != null && !actionBarMsg.isEmpty())
                player.sendActionBar(colorComponent(actionBarMsg));
        } else {
            Bukkit.broadcast(colorComponent(infoMsg));
            if (actionBarMsg != null && !actionBarMsg.isEmpty()) {
                for (Player online : Bukkit.getOnlinePlayers())
                    online.sendActionBar(colorComponent(actionBarMsg));
            }
        }
    }

    private void playExpandEffect(World world, double newSize) {
        ConfigurationSection effectCfg = getConfig().getConfigurationSection("settings.effects");
        if (effectCfg == null) return;

        String soundName = effectCfg.getString("sound", "");
        if (!soundName.isEmpty()) {
            try {
                Sound sound = Sound.valueOf(soundName.toUpperCase());
                float volume = (float) effectCfg.getDouble("sound_volume", 0.8);
                float pitch = (float) effectCfg.getDouble("sound_pitch", 1.0);
                for (Player p : world.getPlayers()) {
                    p.playSound(p.getLocation(), sound, volume, pitch);
                }
            } catch (IllegalArgumentException e) {
                getLogger().warning("Invalid sound name in config: " + soundName);
            }
        }

        if (effectCfg.getBoolean("title", true)) {
            String titleText = lang.getRaw("effects.title",
                    Map.of("%world%", world.getName()));
            String subtitleText = lang.getRaw("effects.subtitle",
                    Map.of("%new_size%", String.format("%.1f", newSize)));
            Title title = Title.title(
                    colorComponent(titleText != null ? titleText : ""),
                    colorComponent(subtitleText != null ? subtitleText : ""),
                    Title.Times.times(Duration.ofMillis(500), Duration.ofMillis(2000), Duration.ofMillis(500))
            );
            for (Player p : world.getPlayers()) {
                p.showTitle(title);
            }
        }
    }

    private long parseTimeToTicks(String timeStr) {
        if (timeStr == null || timeStr.isEmpty()) {
            getLogger().warning(lang.getConsole("log.invalid_format", Map.of("%input%", "null")));
            return 20L;
        }
        timeStr = timeStr.trim().toLowerCase();
        Pattern pattern = Pattern.compile("^(\\d+(?:\\.\\d+)?)([tsd])$");
        Matcher matcher = pattern.matcher(timeStr);
        if (!matcher.matches()) {
            getLogger().warning(lang.getConsole("log.invalid_format", Map.of("%input%", timeStr)));
            return 20L;
        }
        double value;
        try {
            value = Double.parseDouble(matcher.group(1));
        } catch (NumberFormatException e) {
            getLogger().warning(lang.getConsole("log.invalid_format", Map.of("%input%", timeStr)));
            return 20L;
        }
        String unit = matcher.group(2);
        long ticks;
        switch (unit) {
            case "t": ticks = (long) value; break;
            case "s": ticks = (long) (value * 20); break;
            case "d": ticks = (long) (value * 20 * 86400); break;
            default: ticks = 20L;
        }
        if (getConfig().getBoolean("debug", false)) {
            getLogger().info(lang.getConsole("log.speed_parsed", Map.of("%input%", timeStr, "%ticks%", String.valueOf(ticks))));
        }
        return ticks;
    }

    private void checkAndSetInitialBorders() {
        boolean expansionEnabled = getConfig().getBoolean("settings.enable_border_expansion", true);
        boolean shouldSet = getConfig().getBoolean("settings.set_initial_border_on_first_start", true);

        if (!expansionEnabled || !shouldSet || dataConfig.getBoolean("initial_border_set", false)) {
            // Если уже ставили или отключено – всё равно заполняем отсутствующие миры текущим размером
            for (World world : Bukkit.getWorlds()) {
                if (!targetBorderSize.containsKey(world)) {
                    targetBorderSize.put(world, world.getWorldBorder().getSize());
                    saveTargetBorder(world);
                }
            }
            return;
        }

        double initialSize = getConfig().getDouble("settings.initial_border_size", 35.0);
        boolean applyToAll = getConfig().getBoolean("settings.apply_to_all_worlds", true);
        List<String> allowedWorlds = getConfig().getStringList("settings.allowed_worlds");

        for (World world : Bukkit.getWorlds()) {
            // Учитываем список миров, если не применяется ко всем
            if (!applyToAll && !allowedWorlds.contains(world.getName())) {
                // Для миров не из списка просто запоминаем текущий размер
                if (!targetBorderSize.containsKey(world)) {
                    targetBorderSize.put(world, world.getWorldBorder().getSize());
                    saveTargetBorder(world);
                }
                continue;
            }

            double size = initialSize;
            if (world.getEnvironment() == World.Environment.NETHER || world.getName().toLowerCase().contains("nether")) {
                size = initialSize / 8.0;
            }

            world.getWorldBorder().setCenter(0.0, 0.0);
            world.getWorldBorder().setSize(size);
            targetBorderSize.put(world, size);
            saveTargetBorder(world);

            if (getConfig().getBoolean("debug", false)) {
                getLogger().info(lang.getConsole("log.initial_border_set",
                        Map.of("%world%", world.getName(), "%size%", String.valueOf(size))));
            }
        }

        dataConfig.set("initial_border_set", true);
        scheduleSave();
        getLogger().info(lang.getConsole("log.initial_border_done",
                Map.of("%size%", String.valueOf(initialSize))));
    }

    private void updatePlayerAchievements(Player player) {
        int count = 0;
        Iterator<Advancement> it = Bukkit.advancementIterator();
        while (it.hasNext()) {
            Advancement adv = it.next();
            if (!adv.getKey().getKey().startsWith("recipes/") && adv.getDisplay() != null) {
                AdvancementProgress progress = player.getAdvancementProgress(adv);
                if (progress.isDone()) count++;
            }
        }
        dataConfig.set("achievements." + player.getUniqueId().toString(), count);
        achievementCache.put(player.getUniqueId(), count);
        scheduleSave();
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (cmd.getName().equalsIgnoreCase("borderreload")) {
            if (!sender.hasPermission("borderachieve.admin") && !sender.isOp()) {
                sender.sendMessage(colorComponent(lang.getRaw("messages.no_perm")));
                return true;
            }
            reloadConfig();
            dataConfig = YamlConfiguration.loadConfiguration(dataFile);
            loadTargetBorders();
            lang = new LanguageManager(this);
            loadWorldLockingSettings();
            loadTemporaryUnlocks();
            rebuildAchievementCache();
            if (worldLockingEnabled) checkAndUnlockWorlds();
            for (BukkitTask task : delayedTasks.values()) task.cancel();
            delayedTasks.clear();
            pendingTargetSize.clear();
            smoothMoveData.clear();
            for (World world : Bukkit.getWorlds()) {
                if (!targetBorderSize.containsKey(world)) {
                    targetBorderSize.put(world, world.getWorldBorder().getSize());
                    saveTargetBorder(world);
                }
            }
            // Повторно вызываем установку начальной границы, если она ещё не выполнялась
            checkAndSetInitialBorders();

            for (Player p : Bukkit.getOnlinePlayers()) updatePlayerAchievements(p);
            sender.sendMessage(colorComponent(lang.getRaw("messages.reloaded")));
            return true;
        }

        if (cmd.getName().equalsIgnoreCase("topborder") || cmd.getName().equalsIgnoreCase("bordertop")) {
            if (sender instanceof Player) {
                openTopGUI((Player) sender);
                return true;
            } else {
                sender.sendMessage("Эта команда только для игроков.");
                return true;
            }
        }

        if (cmd.getName().equalsIgnoreCase("borderunlock")) {
            if (!sender.hasPermission("borderachieve.admin")) {
                sender.sendMessage(colorComponent(lang.getRaw("messages.no_perm")));
                return true;
            }
            if (args.length == 1) {
                String worldName = args[0];
                World world = Bukkit.getWorld(worldName);
                if (world == null) {
                    sender.sendMessage(colorComponent("&cМир '" + worldName + "' не найден или не загружен."));
                    return true;
                }
                if (lockedWorldsInfo.containsKey(worldName)) {
                    unlockedWorlds.add(worldName);
                    saveUnlockedWorlds();
                    sender.sendMessage(colorComponent("&aМир " + worldName + " принудительно разблокирован."));
                } else {
                    sender.sendMessage(colorComponent("&cМир " + worldName + " отсутствует в списке заблокированных."));
                }
            } else if (args.length >= 2) {
                String worldName = args[0];
                Player target = Bukkit.getPlayer(args[1]);
                if (target == null) {
                    sender.sendMessage(colorComponent("&cИгрок не найден."));
                    return true;
                }
                if (!lockedWorldsInfo.containsKey(worldName)) {
                    sender.sendMessage(colorComponent("&cМир " + worldName + " не заблокирован."));
                    return true;
                }
                temporaryUnlocks.computeIfAbsent(target.getUniqueId(), k -> new HashSet<>()).add(worldName);
                saveTemporaryUnlocks();
                sender.sendMessage(colorComponent("&aИгроку " + target.getName() + " выдан временный доступ в мир " + worldName + "."));
                target.sendMessage(colorComponent("&aВам выдан временный доступ в мир " + worldName + "."));
            }
            return true;
        }

        if (cmd.getName().equalsIgnoreCase("borderlockstatus")) {
            if (!sender.hasPermission("borderachieve.admin")) {
                sender.sendMessage(colorComponent(lang.getRaw("messages.no_perm")));
                return true;
            }
            int total = getTotalAchievementsCount();
            sender.sendMessage(colorComponent("&#42aa49Статус блокировки миров:"));
            for (String worldName : lockedWorldsInfo.keySet()) {
                WorldLockInfo info = lockedWorldsInfo.get(worldName);
                boolean isOpen = unlockedWorlds.contains(worldName);
                String status = isOpen ? "&aОткрыт" : "&cЗакрыт";
                int req = info.requiredAchievements;
                sender.sendMessage(colorComponent(" &7- " + worldName + ": " + status + " &7(Нужно " + req + ", сейчас " + total + ")"));
            }
            return true;
        }

        if (cmd.getName().equalsIgnoreCase("borderdebug")) {
            if (!sender.hasPermission("borderachieve.admin")) {
                sender.sendMessage(colorComponent(lang.getRaw("messages.no_perm")));
                return true;
            }
            sender.sendMessage(colorComponent("&#42aa49[Debug] Информация о границах:"));
            for (World world : Bukkit.getWorlds()) {
                double cur = world.getWorldBorder().getSize();
                Double targ = targetBorderSize.get(world);
                sender.sendMessage(colorComponent(" &7" + world.getName() + ": размер=" + cur + " цель=" + targ));
            }
            sender.sendMessage(colorComponent("&#42aa49Кэш достижений:"));
            achievementCache.forEach((uuid, count) -> {
                OfflinePlayer offP = Bukkit.getOfflinePlayer(uuid);
                sender.sendMessage(colorComponent(" &7" + (offP.getName() != null ? offP.getName() : uuid) + ": " + count));
            });
            sender.sendMessage(colorComponent("&#42aa49Временные пропуски:"));
            temporaryUnlocks.forEach((uuid, worlds) -> {
                OfflinePlayer offP = Bukkit.getOfflinePlayer(uuid);
                sender.sendMessage(colorComponent(" &7" + (offP.getName() != null ? offP.getName() : uuid) + ": " + String.join(", ", worlds)));
            });
            return true;
        }

        if (cmd.getName().equalsIgnoreCase("borderachieve")) {
            sender.sendMessage(colorComponent(lang.getRaw("messages.help_header")));
            sender.sendMessage(colorComponent("/topborder - открыть топ достижений"));
            sender.sendMessage(colorComponent("/borderreload - перезагрузить плагин"));
            sender.sendMessage(colorComponent("/borderunlock <мир> [игрок] - разблокировать мир/игрока"));
            sender.sendMessage(colorComponent("/borderlockstatus - статус блокировок"));
            sender.sendMessage(colorComponent("/borderdebug - отладка"));
            return true;
        }

        return false;
    }

    private void openTopGUI(Player player) {
        updatePlayerAchievements(player);
        Inventory gui = Bukkit.createInventory(null, 54, lang.getGuiTitle());
        List<Integer> slots = getConfig().getIntegerList("gui.slots");
        if (slots.isEmpty()) {
            slots = Arrays.asList(10,11,12,13,14,15,16,19,20,21,22,23,24,25,28,29,30,31,32,33,34,37,38,39,40,41,42,43);
        }

        Map<UUID, Integer> allStats = getAllStats();
        List<Map.Entry<UUID, Integer>> sorted = allStats.entrySet().stream()
                .sorted(Map.Entry.<UUID, Integer>comparingByValue().reversed())
                .collect(Collectors.toList());

        for (int i = 0; i < Math.min(sorted.size(), slots.size()); i++) {
            OfflinePlayer target = Bukkit.getOfflinePlayer(sorted.get(i).getKey());
            int score = sorted.get(i).getValue();
            String displayName = lang.getRaw("gui.rank_format",
                    Map.of("%rank%", String.valueOf(i+1), "%player%", target.getName() != null ? target.getName() : "Unknown"));
            gui.setItem(slots.get(i), createHead(player, target, displayName, score, i+1, false));
        }

        int myScore = dataConfig.getInt("achievements." + player.getUniqueId(), 0);
        int myRank = getPlayerRank(player.getUniqueId());
        String myDisplayName = lang.getRaw("gui.self_rank_format",
                Map.of("%rank%", String.valueOf(myRank), "%player%", player.getName()));
        gui.setItem(4, createHead(player, player, myDisplayName, myScore, myRank, true));
        player.openInventory(gui);
    }

    private ItemStack createHead(Player viewer, OfflinePlayer target, String displayName, int score, int rank, boolean isSelf) {
        ItemStack head = new ItemStack(Material.PLAYER_HEAD);
        SkullMeta meta = (SkullMeta) head.getItemMeta();
        if (meta == null) return head;
        meta.setOwningPlayer(target);
        meta.displayName(colorComponent(displayName));

        List<String> loreStrings = lang.getLore(isSelf);
        List<Component> lore = new ArrayList<>();
        for (String line : loreStrings) {
            String processed = line.replace("%rank%", String.valueOf(rank)).replace("%score%", String.valueOf(score));
            lore.add(colorComponent(processed));
        }
        meta.lore(lore);
        head.setItemMeta(meta);
        return head;
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player)) return;
        Player p = (Player) event.getWhoClicked();
        Component expectedTitle = lang.getGuiTitle();
        if (event.getView().title().equals(expectedTitle)) {
            event.setCancelled(true);
            if (event.getCurrentItem() != null && event.getCurrentItem().getType() == Material.PLAYER_HEAD) {
                openTopGUI(p);
            }
        }
    }

    public int getPlayerRank(UUID uuid) {
        List<Map.Entry<UUID, Integer>> sorted = getAllStats().entrySet().stream()
                .sorted(Map.Entry.<UUID, Integer>comparingByValue().reversed())
                .collect(Collectors.toList());
        for (int i = 0; i < sorted.size(); i++)
            if (sorted.get(i).getKey().equals(uuid)) return i + 1;
        return sorted.size() + 1;
    }

    private Map<UUID, Integer> getAllStats() {
        Map<UUID, Integer> allStats = new HashMap<>(achievementCache);
        return allStats;
    }

    private Component colorComponent(String text) {
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

    private void createDataFile() {
        dataFile = new File(getDataFolder(), "data.yml");
        if (!dataFile.exists()) {
            try {
                dataFile.getParentFile().mkdirs();
                dataFile.createNewFile();
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        dataConfig = YamlConfiguration.loadConfiguration(dataFile);
    }
}