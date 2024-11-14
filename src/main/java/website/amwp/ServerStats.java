package website.amwp;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import website.amwp.config.ModConfig;
import website.amwp.discord.DiscordBot;
import website.amwp.command.GameModeCommand;
import website.amwp.command.ModsCommand;
import website.amwp.command.TeleportCommand;

public class ServerStats implements ModInitializer {
	public static final String MOD_ID = "statsmod";
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	@Override
	public void onInitialize() {
		// Load config and initialize Discord bot
		ModConfig.getInstance();
		DiscordBot.initialize();

		// Register commands
		CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
			GameModeCommand.register(dispatcher);
			ModsCommand.register(dispatcher);
			TeleportCommand.register(dispatcher);
		});

		// Set server instance when it starts
		ServerLifecycleEvents.SERVER_STARTING.register(server -> {
			DiscordBot.setServer(server);
		});

		// Register server shutdown event
		ServerLifecycleEvents.SERVER_STOPPING.register(server -> {
			DiscordBot.sendServerStopMessage();
		});

		// Player join event
		ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
			String playerName = handler.getPlayer().getName().getString();
			ApiService.createPlayer(playerName);
			ApiService.playerJoinServer(playerName);
			DiscordBot.sendPlayerJoinMessage(playerName);
		});

		// Player leave event
		ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
			String playerName = handler.getPlayer().getName().getString();
			ApiService.playerLeaveServer(playerName);
			DiscordBot.sendPlayerLeaveMessage(playerName);
		});
	}
}