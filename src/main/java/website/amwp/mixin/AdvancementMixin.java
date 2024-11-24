package website.amwp.mixin;

import net.minecraft.advancement.Advancement;
import net.minecraft.advancement.AdvancementDisplay;
import net.minecraft.advancement.AdvancementEntry;
import net.minecraft.advancement.PlayerAdvancementTracker;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import website.amwp.discord.DiscordBot;

@Mixin(PlayerAdvancementTracker.class)
public class AdvancementMixin {
    @Shadow private ServerPlayerEntity owner;

    @Inject(method = "grantCriterion", at = @At("RETURN"))
    private void onCriterionGrant(AdvancementEntry advancement, String criterionName, CallbackInfoReturnable<Boolean> cir) {
        if (owner != null && cir.getReturnValue()) {  // Only proceed if criterion was actually granted
            PlayerAdvancementTracker tracker = (PlayerAdvancementTracker) (Object) this;
            
            AdvancementDisplay display = advancement.value().display().orElse(null);
            if (display != null && display.shouldAnnounceToChat() && tracker.getProgress(advancement).isDone()) {
                String playerName = owner.getName().getString();
                Text message = Text.translatable("chat.type.advancement." + display.getFrame().toString().toLowerCase(),
                        owner.getDisplayName(),
                        display.getTitle());
                        
                DiscordBot.sendAchievementMessage(playerName, message);
            }
        }
    }
} 