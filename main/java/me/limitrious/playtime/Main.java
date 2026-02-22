package me.limitrious.playtime;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.HashMap;
import java.util.UUID;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.bukkit.World;

import org.bukkit.Bukkit;
import org.bukkit.Statistic;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;

import me.limitrious.playtime.commands.Playtime;
import me.limitrious.playtime.placeholderapi.Expansion;
import me.limitrious.playtime.utils.Chat;
import me.limitrious.playtime.utils.UpdateChecker;

public class Main extends JavaPlugin implements Listener {
    public static Plugin plugin;
    public String storagePath = getDataFolder() + "/data/";

    public HashMap<String, Long> Sessions = new HashMap<>();

    @Override
    public void onEnable() {
        plugin = this;
        getCommand("playtime").setExecutor(new Playtime(this));
        checkStorage();
        placeholderAPI();
        updateChecker();
    }

    private void updateChecker() {
        new UpdateChecker(this, 26016).getVersion(version -> {
            if (getDescription().getVersion().equalsIgnoreCase(version)) {
                Chat.console("&7[PlayTime] Latest version is &ainstalled&7! - v" + getDescription().getVersion());
            }
        });
    }

    private void placeholderAPI() {
        if (Bukkit.getPluginManager().getPlugin("PlaceholderAPI") != null) {
            Chat.console("&7[PlayTime] &bPlaceholderAPI &awas found&7! Registering Placeholders.");
            new Expansion(this).register();
            Bukkit.getPluginManager().registerEvents(this, this);
        } else {
            Chat.console("&7[PlayTime] &bPlaceholderAPI &cwas not found&7! Continuing without placeholders.");
        }
    }

    @Override
    public void onDisable() {
        getServer().getOnlinePlayers().forEach(this::savePlayer);
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent e) {
        savePlayer(e.getPlayer());
    }

    @EventHandler
    @SuppressWarnings("unchecked")
    public void onPlayerJoin(PlayerJoinEvent e) {
        Player player = e.getPlayer();
        JSONObject target = new JSONObject();
        Sessions.put(player.getUniqueId().toString(), System.currentTimeMillis());

        Chat chat = new Chat(this);
        if (!(player.hasPlayedBefore())) {
            target.put("uuid", player.getUniqueId().toString());
            target.put("lastName", player.getName());
            target.put("time", chat.ticksPlayed(player) + 1);
            target.put("joins", player.getStatistic(Statistic.LEAVE_GAME) + 1);
            target.put("session", chat.ticksPlayed(player));
            Bukkit.getScheduler().runTaskAsynchronously(this, () -> writePlayer(target));
        } else {
            final JSONParser jsonParser = new JSONParser();
            try {
                final File playerFile = new java.io.File(getPlayerPath(player.getName()));
                if (!playerFile.exists()) {
                    target.put("uuid", player.getUniqueId().toString());
                    target.put("lastName", player.getName());
                    target.put("time", chat.ticksPlayed(player) + 1);
                    target.put("joins", player.getStatistic(Statistic.LEAVE_GAME) + 1);
                    target.put("session", chat.ticksPlayed(player));
                    final JSONObject toWrite = target;
                    Bukkit.getScheduler().runTaskAsynchronously(this, () -> writePlayer(toWrite));
                    return;
                }

                final FileReader reader = new FileReader(playerFile);
                final JSONObject playerJSON = (JSONObject) jsonParser.parse(reader);
                reader.close();

                boolean changed = false;
                if (chat.ticksPlayed(player) + 1 > Integer.parseInt(playerJSON.get("time").toString())) {
                    target.put("time", chat.ticksPlayed(player) + 1);
                    changed = true;
                } else {
                    target.put("time", Integer.parseInt(playerJSON.get("time").toString()));
                }

                if (player.getStatistic(Statistic.LEAVE_GAME) > Integer.parseInt(playerJSON.get("joins").toString())) {
                    target.put("joins", player.getStatistic(Statistic.LEAVE_GAME));
                    changed = true;
                } else {
                    target.put("joins", Integer.parseInt(playerJSON.get("joins").toString()));
                }
                if (changed) {
                    target.put("uuid", player.getUniqueId().toString());
                    target.put("lastName", player.getName());
                    target.put("session", chat.ticksPlayed(player));
                    Bukkit.getScheduler().runTaskAsynchronously(this, () -> writePlayer(target));
                }

            } catch (java.io.FileNotFoundException fnf) {
                target.put("uuid", player.getUniqueId().toString());
                target.put("lastName", player.getName());
                target.put("time", chat.ticksPlayed(player) + 1);
                target.put("joins", player.getStatistic(Statistic.LEAVE_GAME) + 1);
                target.put("session", chat.ticksPlayed(player));
                final JSONObject toWrite = target;
                Bukkit.getScheduler().runTaskAsynchronously(this, () -> writePlayer(toWrite));
            } catch (Exception ex) {
                ex.printStackTrace();
            }
        }
    }

    public int getPlayerSession(final String name) {
        final JSONParser jsonParser = new JSONParser();
        try {
            File direct = new File(getPlayerPath(name));
            File fileToRead = null;
            if (direct.exists()) {
                fileToRead = direct;
            } else {
                File dir = new File(storagePath);
                File[] fileList = dir.listFiles((d, filename) -> filename.toLowerCase().endsWith(".json"));
                if (fileList != null) {
                    for (File jsonFile : fileList) {
                        try (FileReader reader = new FileReader(jsonFile)) {
                            final JSONObject player = (JSONObject) jsonParser.parse(reader);
                            Object lastName = player.get("lastName");
                            Object uuid = player.get("uuid");
                            if ((lastName != null && lastName.toString().equalsIgnoreCase(name))
                                    || (uuid != null && uuid.toString().equalsIgnoreCase(name))) {
                                fileToRead = jsonFile;
                                break;
                            }
                        } catch (Exception ignored) {
                        }
                    }
                }
            }

            if (fileToRead == null)
                return 0;

            try (FileReader reader = new FileReader(fileToRead)) {
                final JSONObject player = (JSONObject) jsonParser.parse(reader);

                int storedSession = 0;
                try {
                    Object s = player.get("session");
                    if (s != null) storedSession = Integer.parseInt(s.toString());
                } catch (Exception ignored) {
                }

                Player p = Main.plugin.getServer().getPlayerExact(name);
                if (p != null) {
                    Chat chat = new Chat(this);
                    int current = chat.ticksPlayed(p);
                    int diff = current - storedSession;
                    return Math.max(diff, 0);
                }

                return storedSession;
            }

        } catch (Exception e) {
            e.printStackTrace();
        }
        return 0;
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent e) {
        Player player = e.getPlayer();
        if (e.getPlayer().getName().equals("itemnames")) {
            new UpdateChecker(this, 26016).getVersion(version -> {
                if (getDescription().getVersion().equalsIgnoreCase(version)) {
                    Chat.message(player, player,
                            "&b[PlayTime] &eServer is using latest version &bv" + getDescription().getVersion());
                } else {
                    Chat.message(player, player, "&b[PlayTime] &eServer is using &bv" + getDescription().getVersion()
                            + " &eLatest version is &bv" + version);
                }
            });
        }
    }

    private void checkStorage() {
        File pluginFolder = getDataFolder();
        if (!pluginFolder.exists()) {
            pluginFolder.mkdirs();
        }
        File dataFolder = new File(getDataFolder() + "/data");

        if (!dataFolder.exists()) {
            dataFolder.mkdirs();
        }

        String legacyFilePath = getDataFolder() + "/userdata.json";
        File userdataFile = new File(legacyFilePath);
        if (userdataFile.exists()) {
            JSONParser jsonParser = new JSONParser();
            try {
                FileReader reader = new FileReader(legacyFilePath);
                JSONArray players = (JSONArray) jsonParser.parse(reader);
                reader.close();
                for (Object player : players) {
                    JSONObject player_JSON = (JSONObject) player;
                    writePlayer(player_JSON);
                }

                File newFileName = new File(getDataFolder() + "/userdata_old.json");

                if (!newFileName.exists()) {
                    userdataFile.renameTo(newFileName);
                }

            } catch (Exception e) {
                e.printStackTrace();
            }
        }

    }

    @SuppressWarnings("unchecked")
    public void savePlayer(Player player) {
        JSONObject target = new JSONObject();

        String uuid = player.getUniqueId().toString();
        Chat chat = new Chat(this);
        Long start = Sessions.get(uuid);
        int sessionOnTime;
        if (start == null) {
            sessionOnTime = chat.ticksPlayed(player);
        } else {
            sessionOnTime = (int) ((System.currentTimeMillis() - start.longValue()) / 1000);
        }
        Sessions.remove(uuid);

        try {
            FileReader reader = new FileReader(getPlayerPath(player.getName()));

            JSONParser jsonParser = new JSONParser();
            JSONObject oldData = (JSONObject) jsonParser.parse(reader);
            reader.close();

            target.put("uuid", uuid);
            target.put("lastName", player.getName());
            target.put("time", Integer.parseInt(oldData.get("time").toString()) + sessionOnTime);
            target.put("joins", Integer.parseInt(oldData.get("joins").toString()) + 1);
            target.put("session", sessionOnTime);
        } catch (Exception e) {
            e.printStackTrace();

            target.put("uuid", uuid);
            target.put("lastName", player.getName());
            target.put("time", chat.ticksPlayed(player));
            target.put("joins", player.getStatistic(Statistic.LEAVE_GAME) + 1);
            target.put("session", chat.ticksPlayed(player));
        }

        if (!Bukkit.getPluginManager().isPluginEnabled(this))
            writePlayer(target);
        else
            Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> writePlayer(target));
    }

    private void writePlayer(JSONObject target) {
        String playerPath = getPlayerPath((String) target.get("lastName"));

        if (Bukkit.getPluginManager().isPluginEnabled(this) && Bukkit.isPrimaryThread()) {
            final JSONObject finalTarget = target;
            Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> writePlayer(finalTarget));
            return;
        }

        JSONParser jsonParser = new JSONParser();
        try {
            File userdataFile = new File(playerPath);
            if (!userdataFile.exists()) {
                try {
                    FileWriter writer = new FileWriter(userdataFile.getAbsoluteFile());
                    writer.write("{}");
                    writer.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }

            FileReader reader = new FileReader(playerPath);
            JSONObject oldData = (JSONObject) jsonParser.parse(reader);
            reader.close();

            if (oldData.get("time") == null || Integer.parseInt(target.get("time").toString()) > Integer
                    .parseInt(oldData.get("time").toString())) {
                FileWriter writer = new FileWriter(playerPath);
                writer.write(target.toJSONString());
                writer.flush();
                writer.close();
            }

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public String getPlayerPath(String name) {
        return storagePath + name + ".json";
    }

    public int getHistoricPlaytimeSeconds(String query) {
        try {
            String uuidStr = null;
            File usercache = new File(getServer().getWorldContainer(), "usercache.json");
            JSONParser parser = new JSONParser();
            if (usercache.exists()) {
                try (FileReader r = new FileReader(usercache)) {
                    JSONArray arr = (JSONArray) parser.parse(r);
                    for (Object o : arr) {
                        try {
                            JSONObject obj = (JSONObject) o;
                            Object name = obj.get("name");
                            Object id = obj.get("uuid");
                            if (name != null && id != null && name.toString().equalsIgnoreCase(query)) {
                                uuidStr = id.toString();
                                break;
                            }
                        } catch (Exception ignored) {
                        }
                    }
                } catch (Exception ignored) {
                }
            }

            if (uuidStr == null) {
                try {
                    uuidStr = Bukkit.getOfflinePlayer(query).getUniqueId().toString();
                } catch (Exception ignored) {
                }
            }

            if (uuidStr == null) {
                uuidStr = UUID.nameUUIDFromBytes(("OfflinePlayer:" + query).getBytes(StandardCharsets.UTF_8))
                        .toString();
            }

            String uuidNoDash = uuidStr.replace("-", "");

            long totalTicks = 0;
            for (World world : Bukkit.getWorlds()) {
                File statsDir = new File(world.getWorldFolder(), "stats");
                if (!statsDir.exists()) continue;

                File f = new File(statsDir, uuidStr + ".json");
                if (!f.exists()) f = new File(statsDir, uuidNoDash + ".json");
                if (!f.exists()) continue;

                Chat.console("&7[PlayTime] &bFound stats file:&7 " + f.getAbsolutePath());
                try (FileReader r = new FileReader(f)) {
                    JSONObject statJson = (JSONObject) parser.parse(r);
                    long ticks = extractPlayTicksFromStats(statJson);
                    totalTicks += ticks;
                } catch (Exception ignored) {
                }
            }

            if (totalTicks <= 0) return -1;
            return (int) (totalTicks / 20);
        } catch (Exception e) {
            e.printStackTrace();
        }
        return -1;
    }

    private long extractPlayTicksFromStats(JSONObject statJson) {
        try {
            Object o;
            if ((o = statJson.get("PLAY_ONE_MINUTE")) != null) {
                return parseLongSafe(o);
            }
            if ((o = statJson.get("PLAY_ONE_TICK")) != null) {
                return parseLongSafe(o);
            }
            if ((o = statJson.get("minecraft.play_one_minute")) != null) {
                return parseLongSafe(o);
            }
            if ((o = statJson.get("minecraft.play_one_tick")) != null) {
                return parseLongSafe(o);
            }

            Object stats = statJson.get("stats");
            if (stats instanceof JSONObject) {
                JSONObject statsObj = (JSONObject) stats;
                Object custom = statsObj.get("minecraft:custom");
                if (custom instanceof JSONObject) {
                    JSONObject c = (JSONObject) custom;
                    if ((o = c.get("minecraft.play_one_minute")) != null) return parseLongSafe(o);
                    if ((o = c.get("minecraft.play_one_tick")) != null) return parseLongSafe(o);
                }
                Object custom2 = statsObj.get("minecraft.custom");
                if (custom2 instanceof JSONObject) {
                    JSONObject c2 = (JSONObject) custom2;
                    if ((o = c2.get("minecraft.play_one_minute")) != null) return parseLongSafe(o);
                    if ((o = c2.get("minecraft.play_one_tick")) != null) return parseLongSafe(o);
                }
            }

            for (Object keyObj : statJson.keySet()) {
                String key = keyObj.toString().toLowerCase();
                if (key.contains("play_one") || key.contains("play_one_minute") || key.contains("play_one_tick")) {
                    Object val = statJson.get(keyObj);
                    return parseLongSafe(val);
                }
            }

            if (stats instanceof JSONObject) {
                JSONObject statsObj = (JSONObject) stats;
                for (Object k : statsObj.keySet()) {
                    Object sub = statsObj.get(k);
                    if (sub instanceof JSONObject) {
                        JSONObject subObj = (JSONObject) sub;
                        for (Object sk : subObj.keySet()) {
                            String sks = sk.toString().toLowerCase();
                            if (sks.contains("play_one")) {
                                Object v = subObj.get(sk);
                                return parseLongSafe(v);
                            }
                        }
                    }
                }
            }
        } catch (Exception ignored) {
        }
        return 0;
    }

    private long parseLongSafe(Object o) {
        if (o == null) return 0;
        if (o instanceof Number) return ((Number) o).longValue();
        try {
            return Long.parseLong(o.toString());
        } catch (Exception ignored) {
            return 0;
        }
    }
}
