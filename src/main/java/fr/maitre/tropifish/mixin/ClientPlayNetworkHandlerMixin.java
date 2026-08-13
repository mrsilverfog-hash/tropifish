package fr.maitre.tropifish.mixin;

import fr.maitre.tropifish.AutoFisher;
import net.minecraft.client.network.ClientPlayNetworkHandler;
import net.minecraft.network.packet.s2c.play.PlaySoundS2CPacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Ecoute les paquets de son pour reperer l'eclaboussure du bouchon,
 * qui est le signal fiable d'une touche cote client.
 */
@Mixin(ClientPlayNetworkHandler.class)
public class ClientPlayNetworkHandlerMixin {

    @Inject(method = "onPlaySound", at = @At("TAIL"))
    private void tropifish$onPlaySound(PlaySoundS2CPacket packet, CallbackInfo ci) {
        try {
            if (!AutoFisher.isEnabled() && !AutoFisher.isDebug()) {
                return;
            }
            String id = packet.getSound().value().getId().toString();
            AutoFisher.notifySound(id, packet.getX(), packet.getY(), packet.getZ());
        } catch (Exception ignored) {
            // jamais faire planter la boucle reseau
        }
    }
}
