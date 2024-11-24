package website.amwp.config;

import website.amwp.ServerStats;
import java.io.*;
import java.nio.file.*;

public class NameTagManager {
    private static final String CONFIG_DIR = "config";
    private static final String NAMETAG_DIR = "CustomNameTags";
    private static final String CONFIG_FILE = "config.json";
    private static final String CONFIG_CONTENT = """
            {
              "name_tags": [
                {
                  "id": "minecraft:member",
                  "display": "%luckperms:prefix%%player:displayname_visual%",
                  "update_interval": 1,
                  "visible_radius": 10.7,
                  "observee_predicate": {
                    "type": "negate",
                    "value": {
                      "type": "operator",
                      "operator": 4
                    }
                  }
                },
                {
                  "id": "minecraft:op",
                  "display": "<rainbow>%luckperms:prefix%%player:displayname_visual%",
                  "update_interval": -1,
                  "visible_radius": 10.7,
                  "observee_predicate": {
                    "type": "operator",
                    "operator": 4
                  }
                },
                {
                  "id": "minecraft:data",
                  "display": "%player:health%♥ 20🍖 %player:statistic minecraft:killed minecraft:player%⚔ %player:statistic minecraft:deaths%💀",
                  "update_interval": 1,
                  "visible_radius": 10.7
                },
                {
                  "id": "minecraft:ping",
                  "display": "%player:ping_colored%ms",
                  "update_interval": 1,
                  "visible_radius": 10.7
                }
              ]
            }""";

    public static void deleteNameTagFolder() {
        try {
            // Delete existing CustomNameTags folder if it exists
            Path nametagPath = Paths.get(CONFIG_DIR, NAMETAG_DIR);
            if (Files.exists(nametagPath)) {
                ServerStats.LOGGER.info("Deleting existing CustomNameTags folder...");
                deleteDirectory(nametagPath);
            }
        } catch (IOException e) {
            ServerStats.LOGGER.error("Failed to delete CustomNameTags folder: " + e.getMessage());
        }
    }

    public static void createNameTagConfig() {
        try {
            // Create directories
            Path nametagPath = Paths.get(CONFIG_DIR, NAMETAG_DIR);
            Files.createDirectories(nametagPath);

            // Create config file
            Path configFile = nametagPath.resolve(CONFIG_FILE);
            Files.writeString(configFile, CONFIG_CONTENT);

            ServerStats.LOGGER.info("Successfully created CustomNameTags configuration");
        } catch (IOException e) {
            ServerStats.LOGGER.error("Failed to create CustomNameTags config: " + e.getMessage());
        }
    }

    private static void deleteDirectory(Path path) throws IOException {
        if (Files.exists(path)) {
            Files.walk(path)
                    .sorted((a, b) -> -a.compareTo(b)) // Reverse order to delete contents first
                    .forEach(file -> {
                        try {
                            Files.delete(file);
                        } catch (IOException e) {
                            ServerStats.LOGGER.error("Failed to delete file: " + file);
                        }
                    });
        }
    }
} 