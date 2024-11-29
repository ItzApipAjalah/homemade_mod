package website.amwp.mixin;

import net.minecraft.server.network.ServerPlayNetworkHandler;
import net.minecraft.server.network.ServerPlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(ServerPlayerEntity.class)
public interface PlayerConnectionMixin {
    @Accessor("networkHandler")
    ServerPlayNetworkHandler getNetworkHandler();
} 