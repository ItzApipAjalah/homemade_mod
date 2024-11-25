package website.amwp.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import website.amwp.discord.DiscordBot;
import net.minecraft.server.MinecraftServer;
import net.minecraft.text.Text;

@Mixin(MinecraftServer.class)
public class BackupLogMixin {
    @Inject(method = "sendMessage", at = @At("HEAD"))
    private void onServerMessage(Text message, CallbackInfo ci) {
        if (message != null) {
            String messageStr = message.getString();
            if (messageStr.contains("[Backup]")) {
                // Remove color codes before sending to Discord
                String cleanMessage = messageStr.replaceAll("§[0-9a-fklmnor]", "");
                DiscordBot.handleBackupMessage(cleanMessage);
            }
        }
    }
} 