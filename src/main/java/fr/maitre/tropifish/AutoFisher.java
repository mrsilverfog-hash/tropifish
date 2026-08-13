package fr.maitre.tropifish;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.entity.projectile.FishingBobberEntity;
import net.minecraft.item.FishingRodItem;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.text.Text;
import net.minecraft.util.Hand;
import net.minecraft.util.Identifier;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Random;

/**
 * Machine a etats de la peche automatique.
 * Tout se passe sur le thread client (tick), sauf la notification de son
 * qui arrive du mixin reseau et se contente de poser un drapeau.
 */
public final class AutoFisher {

    private AutoFisher() {
    }

    private enum Action {
        NONE, CAST, REEL
    }

    private static final Random RANDOM = new Random();

    /** Ticks de securite apres chaque clic droit, le temps que le serveur reponde. */
    private static final int GRACE_TICKS = 8;

    private static boolean enabled = false;

    private static Action pending = Action.NONE;
    private static int cooldown = 0;

    /** Ticks ecoules depuis que le bouchon est en vol/dans l'eau. */
    private static int waitTicks = 0;
    /** Ticks pendant lesquels le bouchon est pose dans l'eau, sans bouger. */
    private static int settleTicks = 0;

    private static int catches = 0;
    private static boolean reelIsCatch = false;
    private static String stateLabel = "inactif";

    private static volatile boolean splashFlag = false;
    private static volatile double splashX;
    private static volatile double splashY;
    private static volatile double splashZ;

    /** File des sons captes en mode debug, videe dans le tick. */
    private static final Deque<String> DEBUG_SOUNDS = new ArrayDeque<>();

    // ------------------------------------------------------------------
    // API publique
    // ------------------------------------------------------------------

    public static boolean isEnabled() {
        return enabled;
    }

    public static int getCatchCount() {
        return catches;
    }

    public static String getStateLabel() {
        return stateLabel;
    }

    public static void toggle() {
        setEnabled(!enabled);
    }

    public static void setEnabled(boolean value) {
        enabled = value;
        resetState();
        if (enabled) {
            catches = 0;
            stateLabel = "demarrage";
            say("\u00a7bTropiFish \u00a7aactive\u00a77 (touche "
                    + TropiFishClient.getToggleKeyName() + " pour couper)");
        } else {
            stateLabel = "inactif";
            say("\u00a7bTropiFish \u00a7cdesactive");
        }
    }

    /** Appele depuis le mixin quand le serveur joue le son d'eclaboussure. */
    public static void notifySplash(double x, double y, double z) {
        splashX = x;
        splashY = y;
        splashZ = z;
        splashFlag = true;
    }

    /** Appele depuis le mixin en mode debug, pour tout son recu. */
    public static void notifyDebugSound(String id, double x, double y, double z) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player == null) {
            return;
        }
        FishingBobberEntity bobber = mc.player.fishHook;
        if (bobber == null) {
            return;
        }
        double dx = bobber.getX() - x;
        double dy = bobber.getY() - y;
        double dz = bobber.getZ() - z;
        double dist2 = dx * dx + dy * dy + dz * dz;
        if (dist2 > 64.0D) {
            return;
        }
        synchronized (DEBUG_SOUNDS) {
            if (DEBUG_SOUNDS.size() < 40) {
                DEBUG_SOUNDS.add(id + " \u00a78(" + String.format("%.1f", Math.sqrt(dist2)) + "m)");
            }
        }
    }

    // ------------------------------------------------------------------
    // Boucle principale
    // ------------------------------------------------------------------

    public static void tick(MinecraftClient mc) {
        drainDebugSounds();

        if (!enabled) {
            return;
        }

        ClientPlayerEntity player = mc.player;
        if (player == null || mc.world == null || mc.interactionManager == null) {
            resetState();
            return;
        }

        FishConfig cfg = TropiFishClient.config;

        if (cfg.pauseWhenScreenOpen && mc.currentScreen != null) {
            stateLabel = "en pause (menu)";
            return;
        }

        Hand hand = findRodHand(player);
        if (hand == null) {
            disable("\u00a7cAucune canne a peche en main.");
            return;
        }

        ItemStack rod = player.getStackInHand(hand);
        if (cfg.minDurability > 0 && rod.isDamageable()) {
            int left = rod.getMaxDamage() - rod.getDamage();
            if (left <= cfg.minDurability) {
                disable("\u00a7cCanne presque cassee (" + left + " points restants).");
                return;
            }
        }

        // Fenetre de securite apres un clic
        if (cooldown > 0) {
            cooldown--;
            return;
        }

        // Action programmee arrivee a echeance
        if (pending != Action.NONE) {
            Action action = pending;
            pending = Action.NONE;
            use(mc, player, hand);
            cooldown = GRACE_TICKS;
            waitTicks = 0;
            settleTicks = 0;
            splashFlag = false;
            if (action == Action.REEL && reelIsCatch) {
                catches++;
            }
            reelIsCatch = false;
            stateLabel = action == Action.REEL ? "remontee" : "lancer";
            return;
        }

        FishingBobberEntity bobber = player.fishHook;

        // Pas de bouchon : il faut lancer
        if (bobber == null || bobber.isRemoved()) {
            stateLabel = "lancer...";
            schedule(Action.CAST, cfg.castMinTicks, cfg.castMaxTicks);
            return;
        }

        waitTicks++;
        stateLabel = "attente";

        // 1) Detection principale : son d'eclaboussure pres du bouchon
        if (splashFlag) {
            splashFlag = false;
            double dx = bobber.getX() - splashX;
            double dy = bobber.getY() - splashY;
            double dz = bobber.getZ() - splashZ;
            if (dx * dx + dy * dy + dz * dz <= 6.0D) {
                reelIsCatch = true;
                stateLabel = "touche !";
                schedule(Action.REEL, cfg.reactionMinTicks, cfg.reactionMaxTicks);
                return;
            }
        }

        // 2) Filet de securite : le bouchon plonge d'un coup
        if (bobber.isTouchingWater()) {
            settleTicks++;
            if (cfg.useVelocityFallback
                    && settleTicks > 25
                    && bobber.getVelocity().y < -0.09D) {
                reelIsCatch = true;
                stateLabel = "touche ! (vitesse)";
                schedule(Action.REEL, cfg.reactionMinTicks, cfg.reactionMaxTicks);
                return;
            }
        } else {
            settleTicks = 0;
        }

        // 3) Bouchon coince / touche ratee : on relance
        if (waitTicks > cfg.timeoutSeconds * 20) {
            reelIsCatch = false;
            stateLabel = "relance (timeout)";
            schedule(Action.REEL, 1, 3);
        }
    }

    // ------------------------------------------------------------------
    // Interne
    // ------------------------------------------------------------------

    private static void schedule(Action action, int minTicks, int maxTicks) {
        pending = action;
        cooldown = randomBetween(minTicks, maxTicks);
    }

    private static int randomBetween(int min, int max) {
        int lo = Math.max(0, Math.min(min, max));
        int hi = Math.max(0, Math.max(min, max));
        if (hi <= lo) {
            return lo;
        }
        return lo + RANDOM.nextInt(hi - lo + 1);
    }

    private static void use(MinecraftClient mc, ClientPlayerEntity player, Hand hand) {
        mc.interactionManager.interactItem(player, hand);
        player.swingHand(hand);
    }

    /**
     * Reconnait les cannes vanilla ET les Poke Cannes Cobblemon
     * (cobblemon:roseate_rod, poke_rod, etc.), sans dependance de compilation
     * sur Cobblemon.
     */
    public static boolean isRod(ItemStack stack) {
        if (stack.isEmpty()) {
            return false;
        }
        if (stack.getItem() instanceof FishingRodItem) {
            return true;
        }
        Identifier id = Registries.ITEM.getId(stack.getItem());
        if (TropiFishClient.config.extraRodIds.contains(id.toString())) {
            return true;
        }
        return "cobblemon".equals(id.getNamespace()) && id.getPath().endsWith("_rod");
    }

    private static Hand findRodHand(ClientPlayerEntity player) {
        if (isRod(player.getMainHandStack())) {
            return Hand.MAIN_HAND;
        }
        if (isRod(player.getOffHandStack())) {
            return Hand.OFF_HAND;
        }
        return null;
    }

    private static void drainDebugSounds() {
        if (!TropiFishClient.config.debugSounds) {
            synchronized (DEBUG_SOUNDS) {
                DEBUG_SOUNDS.clear();
            }
            return;
        }
        while (true) {
            String entry;
            synchronized (DEBUG_SOUNDS) {
                entry = DEBUG_SOUNDS.poll();
            }
            if (entry == null) {
                return;
            }
            say("\u00a78[son] \u00a7f" + entry);
        }
    }

    private static void disable(String reason) {
        enabled = false;
        resetState();
        stateLabel = "inactif";
        say("\u00a7bTropiFish \u00a77coupe : " + reason);
    }

    private static void resetState() {
        pending = Action.NONE;
        cooldown = 0;
        waitTicks = 0;
        settleTicks = 0;
        reelIsCatch = false;
        splashFlag = false;
    }

    private static void say(String message) {
        if (!TropiFishClient.config.chatMessages) {
            return;
        }
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player != null) {
            mc.player.sendMessage(Text.literal(message), false);
        }
    }
}
