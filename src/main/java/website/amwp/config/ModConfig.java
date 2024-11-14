package website.amwp.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import website.amwp.ServerStats;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;

public class ModConfig {
    private String discordToken = "your-token-here";
    private String discordChannelId = "your-channel-id-here";
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final File CONFIG_FILE = new File("config/server-stats.json");
    private static ModConfig instance;

    public static ModConfig getInstance() {
        if (instance == null) {
            instance = loadConfig();
        }
        return instance;
    }

    private static ModConfig loadConfig() {
        if (!CONFIG_FILE.exists()) {
            ModConfig config = new ModConfig();
            config.saveConfig();
            return config;
        }

        try (FileReader reader = new FileReader(CONFIG_FILE)) {
            return GSON.fromJson(reader, ModConfig.class);
        } catch (IOException e) {
            ServerStats.LOGGER.error("Failed to load config: " + e.getMessage());
            return new ModConfig();
        }
    }

    public void saveConfig() {
        CONFIG_FILE.getParentFile().mkdirs();
        try (FileWriter writer = new FileWriter(CONFIG_FILE)) {
            GSON.toJson(this, writer);
        } catch (IOException e) {
            ServerStats.LOGGER.error("Failed to save config: " + e.getMessage());
        }
    }

    public String getDiscordToken() {
        return discordToken;
    }

    public String getDiscordChannelId() {
        return discordChannelId;
    }
} 