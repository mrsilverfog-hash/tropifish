package fr.maitre.tropifish.mixin;

import fr.maitre.tropifish.AutoFisher;
import fr.maitre.tropifish.TropiFishClient;
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

    private static final String SPLASH_ID = "minecraft:entity.fishing_bobber.splash";

    @Inject(method = "onPlaySound", at = @At("TAIL"))
    private void tropifish$onPlaySound(PlaySoundS2CPacket packet, CallbackInfo ci) {
        try {
            boolean debug = TropiFishClient.config != null && TropiFishClient.config.debugSounds;
            if (!AutoFisher.isEnabled() && !debug) {
                return;
            }
            String id = packet.getSound().value().getId().toString();
            if (SPLASH_ID.equals(id)) {
                AutoFisher.notifySplash(packet.getX(), packet.getY(), packet.getZ());
            }
            if (debug) {
                AutoFisher.notifyDebugSound(id, packet.getX(), packet.getY(), packet.getZ());
            }
        } catch (Exception ignored) {
            // jamais faire planter la boucle reseau
        }
    }
}
