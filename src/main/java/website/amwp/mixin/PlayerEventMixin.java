package website.amwp.mixin;

import net.minecraft.entity.damage.DamageSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import website.amwp.discord.DiscordBot;

@Mixin(ServerPlayerEntity.class)
public class PlayerEventMixin {
    
    @Inject(method = "onDeath", at = @At("HEAD"))
    private void onPlayerDeath(DamageSource damageSource, CallbackInfo ci) {
        ServerPlayerEntity player = (ServerPlayerEntity) (Object) this;
        Text deathMessage = player.getDamageTracker().getDeathMessage();
        DiscordBot.sendPlayerDeathMessage(player.getName().getString(), deathMessage);
    }
} 