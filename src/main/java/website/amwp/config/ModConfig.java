package website.amwp.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import website.amwp.ServerStats;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.ArrayList;

public class ModConfig {
    private String discordToken = "your-token-here";
    private List<String> discordChannelIds = Arrays.asList("your-channel-id-here");
    private List<String> serverMotd = Arrays.asList("&6Welcome to Minecraft Server", "&eSecond line of MOTD");
    private List<String> tabHeader = Arrays.asList(
        "&6&lPlayer Statistics",
        "&fName: &e%player_name%",
        "&fPing: &e%player_ping%ms",
        "&fPlaytime: &e%playtime%"
    );
    private List<String> tabFooter = Arrays.asList(
        "&6&lServer Information",
        "&fTPS: &e%tps%",
        "&fUptime: &e%uptime%",
        "&fOnline: &e%online%&7/&e%max_players%",
        "&fServer IP: &eplay.example.com"
    );
    
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final File CONFIG_FILE = new File("config/server-stats.json");
    private static ModConfig instance;

    public static ModConfig getInstance() {
        if (instance == null) {
            instance = loadConfig();
        }
        return instance;
    }

    public static void reloadConfig() {
        instance = loadConfig();
    }

    private static ModConfig loadConfig() {
        if (!CONFIG_FILE.exists()) {
            ModConfig config = new ModConfig();
            config.saveConfig();
            return config;
        }

        try (FileReader reader = new FileReader(CONFIG_FILE)) {
            ModConfig config = GSON.fromJson(reader, ModConfig.class);
            if (config == null) {
                ServerStats.LOGGER.warn("Invalid config file, creating new one");
                config = new ModConfig();
            }
            config.saveConfig();
            return config;
        } catch (Exception e) {
            ServerStats.LOGGER.error("Failed to load config: " + e.getMessage());
            ServerStats.LOGGER.info("Creating new config file");
            ModConfig config = new ModConfig();
            config.saveConfig();
            return config;
        }
    }

    public void saveConfig() {
        CONFIG_FILE.getParentFile().mkdirs();
        try (FileWriter writer = new FileWriter(CONFIG_FILE)) {
            GSON.toJson(this, writer);
            ServerStats.LOGGER.info("Config saved successfully");
        } catch (IOException e) {
            ServerStats.LOGGER.error("Failed to save config: " + e.getMessage());
        }
    }

    public String getDiscordToken() {
        return discordToken;
    }

    public List<String> getDiscordChannelIds() {
        return discordChannelIds != null ? discordChannelIds : new ArrayList<>();
    }

    public void addDiscordChannelId(String channelId) {
        if (discordChannelIds == null) {
            discordChannelIds = new ArrayList<>();
        }
        if (!discordChannelIds.contains(channelId)) {
            discordChannelIds.add(channelId);
            saveConfig();
        }
    }

    public void removeDiscordChannelId(String channelId) {
        if (discordChannelIds != null) {
            discordChannelIds.remove(channelId);
            saveConfig();
        }
    }

    public List<String> getServerMotd() {
        return serverMotd;
    }

    public String getFormattedMotd() {
        StringBuilder motd = new StringBuilder();
        for (int i = 0; i < serverMotd.size(); i++) {
            if (i > 0) motd.append("\n");
            motd.append(serverMotd.get(i).replace("&", "§"));
        }
        return motd.toString();
    }

    public void setServerMotd(List<String> motd) {
        this.serverMotd = motd;
        saveConfig();
    }

    public String getFormattedTabHeader() {
        return String.join("\n", tabHeader).replace("&", "§");
    }

    public String getFormattedTabFooter() {
        return String.join("\n", tabFooter).replace("&", "§");
    }

    public void setTabHeader(List<String> header) {
        this.tabHeader = header;
        saveConfig();
    }

    public void setTabFooter(List<String> footer) {
        this.tabFooter = footer;
        saveConfig();
    }
} 