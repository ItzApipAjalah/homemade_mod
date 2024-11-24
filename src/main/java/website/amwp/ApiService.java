package website.amwp;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.concurrent.CompletableFuture;

public class ApiService {
    private static final String BASE_URL = "https://stats-mod-backend.vercel.app/api";
    private static final String PLAYERS_URL = BASE_URL + "/players";
    private static final String ONLINE_JOIN_URL = BASE_URL + "/player-online/join";
    private static final String ONLINE_LEAVE_URL = BASE_URL + "/player-online/leave";
    private static final String ONLINE_LIST_URL = BASE_URL + "/player-online";
    private static final String CHAT_URL = BASE_URL + "/chat";
    private static final String MODPACK_URL = BASE_URL + "/modpack";
    
    private static final HttpClient client = HttpClient.newHttpClient();
    private static final Gson gson = new Gson();
    private static final String API_TOKEN = "kizu_mc_stats_9a8b7c6d5e4f3g2h1i";

    private static boolean playerExists(String playerName, String playersJson) {
        try {
            JsonArray players = gson.fromJson(playersJson, JsonArray.class);
            for (JsonElement player : players) {
                String existingName = player.getAsJsonObject().get("player_name").getAsString();
                if (existingName.equals(playerName)) {
                    return true;
                }
            }
        } catch (Exception e) {
            ServerStats.LOGGER.error("Error parsing players JSON: " + e.getMessage());
        }
        return false;
    }

    public static void createPlayer(String playerName) {
        try {
            // First, get the list of existing players
            HttpRequest getRequest = HttpRequest.newBuilder()
                    .uri(URI.create(PLAYERS_URL))
                    .header("x-api-token", API_TOKEN)
                    .GET()
                    .build();

            ServerStats.LOGGER.info("Checking if player exists: " + playerName);

            client.sendAsync(getRequest, HttpResponse.BodyHandlers.ofString())
                    .thenCompose(getResponse -> {
                        if (getResponse.statusCode() != 200) {
                            ServerStats.LOGGER.error("Failed to get player list. Status: " + getResponse.statusCode());
                            return CompletableFuture.completedFuture(null);
                        }

                        if (playerExists(playerName, getResponse.body())) {
                            ServerStats.LOGGER.info("Player " + playerName + " already exists, skipping creation");
                            return CompletableFuture.completedFuture(null);
                        }

                        // Player doesn't exist, create new player
                        JsonObject requestBody = new JsonObject();
                        requestBody.addProperty("player_name", playerName);

                        ServerStats.LOGGER.info("Creating new player: " + playerName);
                        ServerStats.LOGGER.info("Request body: " + gson.toJson(requestBody));

                        HttpRequest postRequest = HttpRequest.newBuilder()
                                .uri(URI.create(PLAYERS_URL))
                                .header("Content-Type", "application/json")
                                .header("x-api-token", API_TOKEN)
                                .POST(HttpRequest.BodyPublishers.ofString(gson.toJson(requestBody)))
                                .build();

                        return client.sendAsync(postRequest, HttpResponse.BodyHandlers.ofString());
                    })
                    .thenAccept(postResponse -> {
                        if (postResponse == null) {
                            return; // Player already exists or error occurred
                        }
                        
                        if (postResponse.statusCode() == 200 || postResponse.statusCode() == 201) {
                            ServerStats.LOGGER.info("✓ Successfully created new player:");
                            ServerStats.LOGGER.info("  - Player: " + playerName);
                            ServerStats.LOGGER.info("  - Response: " + postResponse.body());
                        } else {
                            ServerStats.LOGGER.error("✗ Failed to create player:");
                            ServerStats.LOGGER.error("  - Player: " + playerName);
                            ServerStats.LOGGER.error("  - Status code: " + postResponse.statusCode());
                            ServerStats.LOGGER.error("  - Response body: " + postResponse.body());
                        }
                    })
                    .exceptionally(e -> {
                        ServerStats.LOGGER.error("✗ Error during API request:");
                        ServerStats.LOGGER.error("  - Player: " + playerName);
                        ServerStats.LOGGER.error("  - Error message: " + e.getMessage());
                        e.printStackTrace();
                        return null;
                    });

        } catch (Exception e) {
            ServerStats.LOGGER.error("✗ Failed to create HTTP request:");
            ServerStats.LOGGER.error("  - Player: " + playerName);
            ServerStats.LOGGER.error("  - Error message: " + e.getMessage());
            e.printStackTrace();
        }
    }

    public static void playerJoinServer(String playerName) {
        try {
            JsonObject requestBody = new JsonObject();
            requestBody.addProperty("player_name", playerName);

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(ONLINE_JOIN_URL))
                    .header("Content-Type", "application/json")
                    .header("x-api-token", API_TOKEN)
                    .POST(HttpRequest.BodyPublishers.ofString(gson.toJson(requestBody)))
                    .build();

            ServerStats.LOGGER.info("Registering player join: " + playerName);

            client.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                    .thenAccept(response -> {
                        if (response.statusCode() == 200 || response.statusCode() == 201) {
                            ServerStats.LOGGER.info("✓ Successfully registered player join:");
                            ServerStats.LOGGER.info("  - Player: " + playerName);
                            updateAndLogOnlineList();
                        } else {
                            ServerStats.LOGGER.error("✗ Failed to register player join:");
                            ServerStats.LOGGER.error("  - Player: " + playerName);
                            ServerStats.LOGGER.error("  - Status: " + response.statusCode());
                        }
                    })
                    .exceptionally(e -> {
                        ServerStats.LOGGER.error("✗ Error registering player join: " + playerName, e);
                        return null;
                    });
        } catch (Exception e) {
            ServerStats.LOGGER.error("✗ Failed to create join request for: " + playerName, e);
        }
    }

    public static void playerLeaveServer(String playerName) {
        try {
            JsonObject requestBody = new JsonObject();
            requestBody.addProperty("player_name", playerName);

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(ONLINE_LEAVE_URL))
                    .header("Content-Type", "application/json")
                    .header("x-api-token", API_TOKEN)
                    .POST(HttpRequest.BodyPublishers.ofString(gson.toJson(requestBody)))
                    .build();

            ServerStats.LOGGER.info("Registering player leave: " + playerName);

            client.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                    .thenAccept(response -> {
                        if (response.statusCode() == 200 || response.statusCode() == 201) {
                            ServerStats.LOGGER.info("✓ Successfully registered player leave:");
                            ServerStats.LOGGER.info("  - Player: " + playerName);
                            updateAndLogOnlineList();
                        } else {
                            ServerStats.LOGGER.error("✗ Failed to register player leave:");
                            ServerStats.LOGGER.error("  - Player: " + playerName);
                            ServerStats.LOGGER.error("  - Status: " + response.statusCode());
                        }
                    })
                    .exceptionally(e -> {
                        ServerStats.LOGGER.error("✗ Error registering player leave: " + playerName, e);
                        return null;
                    });
        } catch (Exception e) {
            ServerStats.LOGGER.error("✗ Failed to create leave request for: " + playerName, e);
        }
    }

    public static void updateAndLogOnlineList() {
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(ONLINE_LIST_URL))
                    .header("x-api-token", API_TOKEN)
                    .GET()
                    .build();

            client.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                    .thenAccept(response -> {
                        if (response.statusCode() == 200) {
                            try {
                                JsonObject onlineData = gson.fromJson(response.body(), JsonObject.class);
                                int totalOnline = onlineData.get("total_online").getAsInt();
                                JsonArray playerList = onlineData.getAsJsonArray("player_list");

                                ServerStats.LOGGER.info("=== Online Players ===");
                                ServerStats.LOGGER.info("Total online: " + totalOnline);
                                ServerStats.LOGGER.info("Players:");
                                for (JsonElement player : playerList) {
                                    JsonObject playerObj = player.getAsJsonObject();
                                    String playerName = playerObj.get("player_name").getAsString();
                                    String joinedAt = playerObj.get("joined_at").getAsString();
                                    ServerStats.LOGGER.info("  - " + playerName + " (joined: " + joinedAt + ")");
                                }
                                ServerStats.LOGGER.info("==================");
                            } catch (Exception e) {
                                ServerStats.LOGGER.error("Error parsing online players response", e);
                            }
                        } else {
                            ServerStats.LOGGER.error("Failed to get online players list. Status: " + response.statusCode());
                        }
                    })
                    .exceptionally(e -> {
                        ServerStats.LOGGER.error("Error fetching online players list", e);
                        return null;
                    });
        } catch (Exception e) {
            ServerStats.LOGGER.error("Failed to create online list request", e);
        }
    }

    public static void sendChatMessage(String playerName, String message) {
        try {
            JsonObject requestBody = new JsonObject();
            requestBody.addProperty("player_name", playerName);
            requestBody.addProperty("message", message);

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(CHAT_URL))
                    .header("Content-Type", "application/json")
                    .header("x-api-token", API_TOKEN)
                    .POST(HttpRequest.BodyPublishers.ofString(gson.toJson(requestBody)))
                    .build();

            ServerStats.LOGGER.info("Sending chat message to API - Player: " + playerName + ", Message: " + message);

            client.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                    .thenAccept(response -> {
                        if (response.statusCode() == 200 || response.statusCode() == 201) {
                            ServerStats.LOGGER.info("Successfully sent chat message to API");
                        } else {
                            ServerStats.LOGGER.error("Failed to send chat message to API:");
                            ServerStats.LOGGER.error("  - Status code: " + response.statusCode());
                            ServerStats.LOGGER.error("  - Response: " + response.body());
                        }
                    })
                    .exceptionally(e -> {
                        ServerStats.LOGGER.error("Error sending chat message to API:", e);
                        return null;
                    });
        } catch (Exception e) {
            ServerStats.LOGGER.error("Failed to create chat message request:", e);
        }
    }

    public static void updateModpack(String url) {
        try {
            JsonObject requestBody = new JsonObject();
            requestBody.addProperty("name", "KizuServer Modpack");
            requestBody.addProperty("version", "1.0.0");
            requestBody.addProperty("url", url);

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(MODPACK_URL))
                    .header("Content-Type", "application/json")
                    .header("x-api-token", API_TOKEN)
                    .PUT(HttpRequest.BodyPublishers.ofString(gson.toJson(requestBody)))
                    .build();

            ServerStats.LOGGER.info("Updating modpack with URL: " + url);

            client.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                    .thenAccept(response -> {
                        if (response.statusCode() == 200 || response.statusCode() == 201) {
                            ServerStats.LOGGER.info("✓ Successfully updated modpack:");
                            ServerStats.LOGGER.info("  - URL: " + url);
                            ServerStats.LOGGER.info("  - Response: " + response.body());
                        } else {
                            ServerStats.LOGGER.error("✗ Failed to update modpack:");
                            ServerStats.LOGGER.error("  - Status: " + response.statusCode());
                            ServerStats.LOGGER.error("  - Response: " + response.body());
                        }
                    })
                    .exceptionally(e -> {
                        ServerStats.LOGGER.error("✗ Error updating modpack:", e);
                        return null;
                    });
        } catch (Exception e) {
            ServerStats.LOGGER.error("✗ Failed to create modpack update request:", e);
        }
    }
} 