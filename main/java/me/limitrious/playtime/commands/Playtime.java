package me.limitrious.playtime.commands;

import java.io.File;
import java.io.FileReader;
import java.time.Duration;
import java.time.temporal.ChronoUnit;
import java.util.*;

import me.limitrious.playtime.Main;
import me.limitrious.playtime.utils.ConfigWrapper;
import me.limitrious.playtime.utils.TimeFormat;
import me.limitrious.playtime.utils.TopPlayers;

import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.util.StringUtil;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import me.limitrious.playtime.utils.Chat;

public class Playtime implements TabExecutor {
    static Main plugin;
    public static ConfigWrapper config;

    public Playtime(Main instance) {
        plugin = instance;
        Playtime.config = new ConfigWrapper(instance, null, "config.yml");
        Playtime.config.createFile(null,
                "Playtime By limitrious - Need Help? PM me on Spigot or post in the discussion.\r\n" + "\r\n"
                        + " =================\r\n" + " | CONFIGURATION |\r\n" + " =================\r\n" + "\r\n"
                        + " available placeholders\r\n" + " %playtime_player% - returns the player name\r\n"
                        + " %offlineplayer% - returns the offline player name\r\n"
                        + " %offlinetime% - shows offline time of a player\r\n"
                        + " %offlinetimesjoined% - shows the amount of joins a player has had\r\n"
                        + " %playtime_time% - shows time played\r\n"
                        + " %playtime_timesjoined% - shows the amount of times the player has joined the server\r\n"
                        + " %playtime_serveruptime% - shows the uptime of the server\r\n"
                        + " %playtime_position% - shows the players current position\r\n"
                        + " %playtime_top_#_name% - shows the name of the top 10\r\n"
                        + " %playtime_top_#_time% - shows the time of the top 10\r\n"
                        + " You can also use any other placeholder that PlaceholderAPI supports :) \r\n" + "");
        FileConfiguration c = Playtime.config.getConfig();
        c.addDefault("time.second.enabled", true);
        c.addDefault("time.second.prefix", "s");
        c.addDefault("time.minute.enabled", true);
        c.addDefault("time.minute.prefix", "m");
        c.addDefault("time.hour.enabled", true);
        c.addDefault("time.hour.prefix", "h");
        c.addDefault("time.day.enabled", true);
        c.addDefault("time.day.prefix", "d");
        c.addDefault("time.week.enabled", true);
        c.addDefault("time.week.prefix", "w");
        c.addDefault("messages.no_permission", Arrays.asList("&8[&bPlayTime&8] &cYou don't have permission."));
        c.addDefault("messages.doesnt_exist",
                Arrays.asList("&8[&bPlayTime&8] &cPlayer %offlineplayer% has not joined before!"));
        c.addDefault("messages.player", Arrays.asList("&b%playtime_player%'s Stats are:",
                "&bPlayTime: &7%playtime_time%", "&bTimes Joined: &7%playtime_timesjoined%"));
        c.addDefault("messages.offline_players", Arrays.asList("&b%offlineplayer%'s Stats are:",
                "&bPlayTime: &7%offlinetime%", "&bTimes Joined: &7%offlinetimesjoined%"));
        c.addDefault("messages.other_players", Arrays.asList("&b%playtime_player%'s Stats are:",
                "&bPlayTime: &7%playtime_time%", "&bTimes Joined: &7%playtime_timesjoined%"));
        c.addDefault("messages.playtimetop.header", Arrays.asList("&bTop &e10 &bplayers playtime:", ""));
        c.addDefault("messages.playtimetop.message", Arrays.asList("&a%position%. &b%player%: &e%playtime%"));
        c.addDefault("messages.playtimetop.footer", Arrays.asList(""));
        c.addDefault("messages.server_uptime",
                Arrays.asList("&8[&bPlayTime&8] &bServer's total uptime is %playtime_serveruptime%"));
        c.addDefault("messages.reload_config",
                Arrays.asList("&8[&bPlayTime&8] &bYou have successfully reloaded the config."));
        c.addDefault("placeholder.top.name", "none");
        c.addDefault("placeholder.top.time", "-");
        c.options().copyDefaults(true);
        Playtime.config.saveConfig();
    }

    public String getPlayerTime(String name) {
        JSONObject player = readPlayerJson(name);
        if (player == null) {
            int historic = plugin.getHistoricPlaytimeSeconds(name);
            return historic <= 0 ? null : String.valueOf(historic);
        }
        Object t = player.get("time");
        return t == null ? null : t.toString();
    }

    public String getPlayerJoins(String name) {
        JSONObject player = readPlayerJson(name);
        if (player == null) return null;
        Object j = player.get("joins");
        return j == null ? null : j.toString();
    }

    private JSONObject readPlayerJson(String nameOrUuid) {
        JSONParser jsonParser = new JSONParser();
        try {
            String path = resolvePlayerPath(nameOrUuid);
            if (path == null) return null;
            try (FileReader reader = new FileReader(path)) {
                return (JSONObject) jsonParser.parse(reader);
            }
        } catch (Exception ignored) {
        }
        return null;
    }

    private String resolvePlayerPath(String nameOrUuid) {
        if (nameOrUuid == null) return null;
        try {
            File directFile = new File(plugin.getPlayerPath(nameOrUuid));
            if (directFile.exists()) return directFile.getAbsolutePath();

            if (nameOrUuid.endsWith(".json")) {
                File maybe = new File(plugin.storagePath + nameOrUuid);
                if (maybe.exists()) return maybe.getAbsolutePath();
            }

            File dir = new File(plugin.storagePath);
            File[] fileList = dir.listFiles((d, name) -> name.toLowerCase().endsWith(".json"));
            if (fileList == null) return null;

            JSONParser jsonParser = new JSONParser();
            final String query = nameOrUuid.trim();

            for (File jsonFile : fileList) {
                String filename = jsonFile.getName();
                String base = filename.substring(0, filename.length() - 5);
                if (base.equalsIgnoreCase(query)) return jsonFile.getAbsolutePath();

                try (FileReader reader = new FileReader(jsonFile)) {
                    JSONObject player = (JSONObject) jsonParser.parse(reader);
                    Object lastName = player.get("lastName");
                    Object uuid = player.get("uuid");
                    if (lastName != null && lastName.toString().equalsIgnoreCase(query)) return jsonFile.getAbsolutePath();
                    if (uuid != null && uuid.toString().equalsIgnoreCase(query)) return jsonFile.getAbsolutePath();
                    if (lastName != null && lastName.toString().toLowerCase().contains(query.toLowerCase()))
                        return jsonFile.getAbsolutePath();
                } catch (Exception ignored) {
                }
            }
        } catch (Exception ignored) {
        }
        return null;
    }

    public static TopPlayers[] getTopTen() {
        TopPlayers[] topTen = new TopPlayers[0];
        try {
            JSONParser jsonParser = new JSONParser();

            File dir = new File(plugin.storagePath);

            File[] fileList = dir.listFiles((d, name) -> name.toLowerCase().endsWith(".json"));

            if (fileList != null) {
                ArrayList<TopPlayers> allPlayers = new ArrayList<>();

                for (File jsonFile : fileList) {
                    try (FileReader reader = new FileReader(jsonFile)) {
                        JSONObject player = (JSONObject) jsonParser.parse(reader);
                        Object last = player.get("lastName");
                        Object uid = player.get("uuid");
                        Object time = player.get("time");
                        if (last == null || uid == null || time == null) continue;
                        try {
                            int t = Integer.parseInt(time.toString());
                            allPlayers.add(new TopPlayers(last.toString(), uid.toString(), t));
                        } catch (NumberFormatException nfe) {
                        }
                    } catch (Exception ignored) {
                    }
                }

                if (!allPlayers.isEmpty()) {
                    allPlayers.sort(Comparator.comparingInt(e -> e.time));
                    Collections.reverse(allPlayers);

                    int len = Math.min(allPlayers.size(), 10);
                    topTen = new TopPlayers[len];
                    for (int i = 0; i < len; ++i) {
                        topTen[i] = allPlayers.get(i);
                    }
                }
            }
            return topTen;
        } catch (Exception e) {
            e.printStackTrace();
        }
        return topTen;
    }

    public static TopPlayers[] checkOnlinePlayers(TopPlayers[] top10) {
        Chat chat = new Chat(plugin);
        for (Player player : plugin.getServer().getOnlinePlayers())
            if (chat.ticksPlayed(player) > (top10.length == 0 ? 0 : top10[top10.length - 1].time)) {
                TopPlayers top = new TopPlayers(player.getName(), player.getUniqueId().toString(),
                        chat.ticksPlayed(player));
                for (int i = 0; i < top10.length; ++i)
                    if (top10[i].time <= top.time)
                        if (top10[i].uuid.equals(top.uuid)) {
                            top10[i] = top;
                            break;
                        } else {
                            TopPlayers temp = top10[i];
                            top10[i] = (top = temp);
                        }
            }
        return top10;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String commandLabel, String[] args) {
        FileConfiguration c = Playtime.config.getConfig();
        if (cmd.getName().equalsIgnoreCase("playtime")) {
            if (sender instanceof Player) {
                Player player = (Player) sender;
                if (!(sender.hasPermission("playtime.check"))) {
                    for (String noPermission : c.getStringList("messages.no_permission"))
                        Chat.message(sender, player, noPermission);
                    return true;
                }
                if (args.length == 0) {
                    for (String thisPlayer : c.getStringList("messages.player"))
                        Chat.message(sender, player, thisPlayer);
                    return true;
                }
            }

            if (args.length > 0 && args[0].equals("reload")) {
                if (!(sender.hasPermission("playtime.reload"))) {
                    for (String noPermission : c.getStringList("messages.no_permission")) {
                        if (sender instanceof Player)
                            Chat.message(sender, (Player) sender, noPermission);
                        else
                            Chat.message(sender, null, noPermission);
                    }
                    return true;
                }
                for (String reloadConfig : c.getStringList("messages.reload_config"))
                    if (sender instanceof Player)
                        Chat.message(sender, (Player) sender, reloadConfig);
                    else
                        Chat.message(sender, null, reloadConfig);
                Playtime.config.reloadConfig();
                return true;
            }

            if (args.length > 0 && args[0].equals("uptime")) {
                if (!(sender.hasPermission("playtime.uptime"))) {
                    for (String noPermission : c.getStringList("messages.no_permission")) {
                        if (sender instanceof Player)
                            Chat.message(sender, (Player) sender, noPermission);
                        else
                            Chat.message(sender, null, noPermission);
                    }
                    return true;
                }
                for (String serverUptime : c.getStringList("messages.server_uptime"))
                    if (sender instanceof Player)
                        Chat.message(sender, (Player) sender, serverUptime);
                    else
                        Chat.message(sender, null, serverUptime.replace("%playtime_serveruptime%", String.valueOf(TimeFormat.Uptime())));
                return true;
            }

            if (args.length > 0 && args[0].equals("top")) {
                if (!(sender.hasPermission("playtime.checktop"))) {
                    for (String noPermission : c.getStringList("messages.no_permission")) {
                        if (sender instanceof Player)
                            Chat.message(sender, (Player) sender, noPermission);
                        else
                            Chat.message(sender, null, noPermission);
                    }
                    return true;
                }
                TopPlayers[] top10 = getTopTen();
                top10 = checkOnlinePlayers(top10);
                for (String header : c.getStringList("messages.playtimetop.header"))
                    if (sender instanceof Player)
                        Chat.message(sender, (Player) sender, header);
                    else
                        Chat.message(sender, null, header);
                for (int i = 0; i < top10.length; i++) {
                    if (top10[i].time == 0) {
                        break;
                    }
                    for (String message : c.getStringList("messages.playtimetop.message")) {
                        String out = message.replace("%position%", Integer.toString(i + 1))
                                .replace("%player%", top10[i].name).replace("%playtime%",
                                        TimeFormat.getTime(Duration.of(top10[i].time, ChronoUnit.SECONDS)));
                        if (sender instanceof Player)
                            Chat.message(sender, (Player) sender, out);
                        else
                            Chat.message(sender, null, out);
                    }
                }
                for (String footer : c.getStringList("messages.playtimetop.footer"))
                    if (sender instanceof Player)
                        Chat.message(sender, (Player) sender, footer);
                    else
                        Chat.message(sender, null, footer);
                return true;
            }

            if (args.length > 0) {
                if (!(sender.hasPermission("playtime.checkothers"))) {
                    for (String noPermission : c.getStringList("messages.no_permission")) {
                        if (sender instanceof Player)
                            Chat.message(sender, (Player) sender, noPermission);
                        else
                            Chat.message(sender, null, noPermission);
                    }
                    return true;
                }
                Player target = plugin.getServer().getPlayer(args[0]);
                if (target == null) {
                    JSONObject stored = readPlayerJson(args[0]);
                    if (stored == null) {
                        for (String notOnline : c.getStringList("messages.doesnt_exist")) {
                            if (sender instanceof Player)
                                Chat.message(sender, null, notOnline.replace("%offlineplayer%", args[0]));
                            else
                                Chat.message(sender, null, notOnline.replace("%offlineplayer%", args[0]));
                        }
                    } else {
                        String displayName = stored.get("lastName") != null ? stored.get("lastName").toString() : args[0];
                        String storedTime = stored.get("time") != null ? stored.get("time").toString() : null;
                        String storedJoins = stored.get("joins") != null ? stored.get("joins").toString() : null;
                        for (String offlinePlayers : c.getStringList("messages.offline_players")) {
                            String out = offlinePlayers.replace("%offlineplayer%", displayName)
                                    .replace("%offlinetime%",
                                            TimeFormat.getTime(Duration.of(Integer.valueOf(storedTime),
                                                    ChronoUnit.SECONDS)))
                                    .replace("%offlinetimesjoined%", storedJoins);
                            if (sender instanceof Player)
                                Chat.message(sender, null, out);
                            else
                                Chat.message(sender, null, out);
                        }
                    }
                } else {
                    for (String otherPlayer : c.getStringList("messages.other_players"))
                        Chat.message(sender, target, otherPlayer);
                }
                return true;
            }

            return true;
        }
        return false;
    }

    @Override
    public List<String> onTabComplete(CommandSender commandSender, Command command, String label, String[] args) {
        List<String> tabComplete = new ArrayList<>();
        tabComplete.add("reload");
        tabComplete.add("uptime");
        tabComplete.add("top");
        for (Player p : plugin.getServer().getOnlinePlayers())
            tabComplete.add(p.getName());
        if (args.length == 1) {
            return StringUtil.copyPartialMatches(args[0], tabComplete, new ArrayList<>());
        }
        return null;
    }
}
