package website.amwp.mixin;

import net.minecraft.server.MinecraftServer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import website.amwp.ServerStats;
import java.util.concurrent.*;

@Mixin(MinecraftServer.class)
public class ServerShutdownMixin {
    @Inject(method = "shutdown", at = @At("HEAD"))
    private void onShutdown(CallbackInfo ci) {
        MinecraftServer server = (MinecraftServer) (Object) this;
        
        // Create a scheduled executor for timeouts
        ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);
        
        // Schedule force shutdown after 12 seconds
        scheduler.schedule(() -> {
            ServerStats.LOGGER.error("Server shutdown timed out after 30 seconds! Forcing exit...");
            Runtime.getRuntime().halt(1);
        }, 12, TimeUnit.SECONDS);

        // Schedule warning after 10 seconds
        scheduler.schedule(() -> {
            ServerStats.LOGGER.warn("Server shutdown is taking longer than expected...");
        }, 10, TimeUnit.SECONDS);

        // Shutdown the scheduler when done
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            scheduler.shutdownNow();
        }));
    }
} 