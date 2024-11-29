package website.amwp.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import net.minecraft.network.ClientConnection;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.timeout.ReadTimeoutException;

@Mixin(ClientConnection.class)
public class NettyLoggerMixin {
    @Inject(method = "exceptionCaught(Lio/netty/channel/ChannelHandlerContext;Ljava/lang/Throwable;)V", at = @At("HEAD"), cancellable = true)
    private void onException(ChannelHandlerContext context, Throwable throwable, CallbackInfo ci) {
        if (throwable instanceof ReadTimeoutException) {
            // Silently close the connection without logging
            if (context.channel().isOpen()) {
                context.close();
            }
            ci.cancel();
        }
    }
} 