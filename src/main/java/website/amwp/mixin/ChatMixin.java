package website.amwp.mixin;

import net.minecraft.network.message.SignedMessage;
import net.minecraft.server.network.ServerPlayNetworkHandler;
import net.minecraft.server.network.ServerPlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import website.amwp.ApiService;
import website.amwp.discord.DiscordBot;
import website.amwp.ServerStats;

@Mixin(ServerPlayNetworkHandler.class)
public class ChatMixin {
    @Shadow
    public ServerPlayerEntity player;

    @Inject(method = "handleDecoratedMessage", at = @At("HEAD"))
    private void onGameMessage(SignedMessage message, CallbackInfo ci) {
        try {
            String playerName = player.getName().getString();
            String messageContent = message.getContent().getString();
            
            // Send to Discord
            DiscordBot.sendChatMessage(playerName, messageContent);
            
            // Send to API
            ApiService.sendChatMessage(playerName, messageContent);
            
        } catch (Exception e) {
            ServerStats.LOGGER.error("Error in chat mixin:", e);
        }
    }
} 