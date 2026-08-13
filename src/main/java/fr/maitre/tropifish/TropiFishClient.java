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

import java.util.List;

public class TropiFishClient implements ClientModInitializer {

    public static final String MOD_ID = "tropifish";

    public static FishConfig config;

    private static KeyBinding toggleKey;
    private static KeyBinding debugKey;

    /** Nom lisible de la touche actuellement assignee (suit les rebinds). */
    public static String getToggleKeyName() {
        return nameOf(toggleKey);
    }

    public static String getDebugKeyName() {
        return nameOf(debugKey);
    }

    private static String nameOf(KeyBinding binding) {
        if (binding == null) {
            return "?";
        }
        if (binding.isUnbound()) {
            return "non assignee";
        }
        return binding.getBoundKeyLocalizedText().getString();
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

        // Non assignee par defaut : outil de diagnostic uniquement.
        // A rebinder dans Options > Commandes > TropiFish si besoin.
        debugKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.tropifish.debug",
                InputUtil.Type.KEYSYM,
                GLFW.GLFW_KEY_UNKNOWN,
                "category.tropifish"
        ));

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            while (toggleKey.wasPressed()) {
                AutoFisher.toggle();
            }
            while (debugKey.wasPressed()) {
                AutoFisher.toggleDebug();
            }
            AutoFisher.tick(client);
        });

        HudRenderCallback.EVENT.register((context, tickCounter) -> {
            MinecraftClient mc = MinecraftClient.getInstance();
            if (mc.player == null || mc.options.hudHidden) {
                return;
            }

            int y = 6;

            if (config.hudEnabled && AutoFisher.isEnabled()) {
                String line = "\u00a7bTropiFish \u00a7aON \u00a77| " + AutoFisher.getStateLabel()
                        + " \u00a77| \u00a7f" + AutoFisher.getCatchCount() + " prises"
                        + " \u00a78[" + getToggleKeyName() + "]";
                context.drawTextWithShadow(mc.textRenderer, Text.literal(line), 6, y, 0xFFFFFF);
                y += 12;
            }

            if (AutoFisher.isDebug()) {
                context.drawTextWithShadow(mc.textRenderer,
                        Text.literal("\u00a76== TropiFish diagnostic =="), 6, y, 0xFFFFFF);
                y += 10;
                List<String> lines = AutoFisher.getDebugLines();
                for (String line : lines) {
                    context.drawTextWithShadow(mc.textRenderer, Text.literal(line), 6, y, 0xFFFFFF);
                    y += 10;
                }
            }
        });
    }
}
