package fr.maitre.tropifish;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.text.Text;
import org.lwjgl.glfw.GLFW;

public class TropiFishClient implements ClientModInitializer {

    public static final String MOD_ID = "tropifish";

    public static FishConfig config;

    private static KeyBinding toggleKey;

    /** Nom lisible de la touche actuellement assignee (suit les rebinds). */
    public static String getToggleKeyName() {
        if (toggleKey == null) {
            return "?";
        }
        if (toggleKey.isUnbound()) {
            return "non assignee";
        }
        return toggleKey.getBoundKeyLocalizedText().getString();
    }

    @Override
    public void onInitializeClient() {
        config = FishConfig.load();

        toggleKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.tropifish.toggle",
                InputUtil.Type.KEYSYM,
                GLFW.GLFW_KEY_J,
                "category.tropifish"
        ));

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            while (toggleKey.wasPressed()) {
                AutoFisher.toggle();
            }
            AutoFisher.tick(client);
        });

        HudRenderCallback.EVENT.register((context, tickCounter) -> {
            if (!config.hudEnabled || !AutoFisher.isEnabled()) {
                return;
            }
            MinecraftClient mc = MinecraftClient.getInstance();
            if (mc.player == null || mc.options.hudHidden) {
                return;
            }
            String line = "\u00a7bTropiFish \u00a7aON \u00a77| " + AutoFisher.getStateLabel()
                    + " \u00a77| \u00a7f" + AutoFisher.getCatchCount() + " prises"
                    + " \u00a78[" + getToggleKeyName() + "]";
            context.drawTextWithShadow(mc.textRenderer, Text.literal(line), 6, 6, 0xFFFFFF);
        });
    }
}
