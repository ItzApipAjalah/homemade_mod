package website.amwp.tab;

import net.minecraft.network.packet.s2c.play.PlayerListHeaderS2CPacket;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.stat.Stats;
import net.minecraft.stat.Stat;
import net.minecraft.stat.StatType;
import net.minecraft.util.Identifier;
import website.amwp.ServerStats;
import website.amwp.config.ModConfig;

import java.lang.management.ManagementFactory;
import java.util.concurrent.TimeUnit;
import java.util.LinkedList;
import java.util.Queue;

public class TabManager {
    private static long serverStartTime = System.currentTimeMillis();
    private static final Queue<Long> tickTimes = new LinkedList<>();
    private static long lastTickTime = System.currentTimeMillis();
    private static final int TPS_SAMPLE_SIZE = 120; // 6 seconds worth of ticks

    public static void updatePlayerTab(ServerPlayerEntity player) {
        String header = ModConfig.getInstance().getFormattedTabHeader()
            .replace("%player_name%", player.getName().getString())
            .replace("%player_ping%", String.valueOf(player.networkHandler.getLatency()))
            .replace("%playtime%", formatPlayTime(player.getStatHandler().getStat(Stats.CUSTOM, Stats.PLAY_TIME)));

        String footer = ModConfig.getInstance().getFormattedTabFooter()
            .replace("%tps%", formatTPS())
            .replace("%uptime%", formatUptime())
            .replace("%online%", String.valueOf(player.getServer().getCurrentPlayerCount()))
            .replace("%max_players%", String.valueOf(player.getServer().getMaxPlayerCount()));

        PlayerListHeaderS2CPacket packet = new PlayerListHeaderS2CPacket(
            Text.literal(header),
            Text.literal(footer)
        );
        
        player.networkHandler.sendPacket(packet);
    }

    public static void updateAllPlayers(net.minecraft.server.MinecraftServer server) {
        // Record tick time
        long currentTime = System.currentTimeMillis();
        tickTimes.add(currentTime - lastTickTime);
        lastTickTime = currentTime;
        
        // Keep only recent samples
        while (tickTimes.size() > TPS_SAMPLE_SIZE) {
            tickTimes.poll();
        }

        // Update all players
        for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
            updatePlayerTab(player);
        }
    }

    private static String formatPlayTime(int ticks) {
        long seconds = ticks / 20;
        long hours = TimeUnit.SECONDS.toHours(seconds);
        long minutes = TimeUnit.SECONDS.toMinutes(seconds) % 60;
        
        if (hours > 0) {
            return String.format("%dh %dm", hours, minutes);
        } else {
            return String.format("%dm", minutes);
        }
    }

    private static String formatTPS() {
        if (tickTimes.isEmpty()) return "20.0";

        double averageTickTime = tickTimes.stream()
            .mapToLong(Long::valueOf)
            .average()
            .orElse(50.0); // Default to 50ms (20 TPS) if no samples

        double tps = Math.min(20.0, 1000.0 / averageTickTime);
        
        // Color code based on TPS
        String color = "&a"; // Green for good TPS
        if (tps < 18.0) color = "&e"; // Yellow for slight lag
        if (tps < 15.0) color = "&c"; // Red for significant lag

        return color + String.format("%.1f", tps);
    }

    private static String formatUptime() {
        long uptime = System.currentTimeMillis() - serverStartTime;
        long days = TimeUnit.MILLISECONDS.toDays(uptime);
        long hours = TimeUnit.MILLISECONDS.toHours(uptime) % 24;
        long minutes = TimeUnit.MILLISECONDS.toMinutes(uptime) % 60;
        
        if (days > 0) {
            return String.format("%dd %dh %dm", days, hours, minutes);
        } else if (hours > 0) {
            return String.format("%dh %dm", hours, minutes);
        } else {
            return String.format("%dm", minutes);
        }
    }
}