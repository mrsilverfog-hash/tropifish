package fr.maitre.tropifish;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.entity.Entity;
import net.minecraft.entity.projectile.FishingBobberEntity;
import net.minecraft.item.FishingRodItem;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.text.Text;
import net.minecraft.util.Hand;
import net.minecraft.util.Identifier;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Locale;
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
    private static boolean debug = false;

    private static Action pending = Action.NONE;
    private static int cooldown = 0;

    private static int waitTicks = 0;
    private static int settleTicks = 0;

    private static int catches = 0;
    private static boolean reelIsCatch = false;
    private static String stateLabel = "inactif";

    private static volatile boolean splashFlag = false;
    private static volatile double splashX;
    private static volatile double splashY;
    private static volatile double splashZ;

    // --- suivi du bouchon ---
    private static int trackedBobberId = -1;
    private static double lastY = Double.NaN;
    private static double lastDy = 0.0D;
    private static double minDy = 0.0D;
    private static double minVy = 0.0D;

    // --- diagnostics ---
    private static final Deque<String> SOUND_LOG = new ArrayDeque<>();
    private static String lastRodId = "-";
    private static String lastHookId = "-";
    private static String lastHookSource = "-";
    private static boolean lastInWater = false;

    // ------------------------------------------------------------------
    // API publique
    // ------------------------------------------------------------------

    public static boolean isEnabled() {
        return enabled;
    }

    public static boolean isDebug() {
        return debug;
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

    public static void toggleDebug() {
        debug = !debug;
        synchronized (SOUND_LOG) {
            SOUND_LOG.clear();
        }
        say("\u00a7bTropiFish \u00a77diagnostic : " + (debug ? "\u00a7aON" : "\u00a7cOFF"));
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

    /** Appele depuis le mixin pour chaque son recu. */
    public static void notifySound(String id, double x, double y, double z) {
        if (SPLASH_ID.equals(id)) {
            splashX = x;
            splashY = y;
            splashZ = z;
            splashFlag = true;
        }
        if (!debug) {
            return;
        }
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player == null) {
            return;
        }
        FishingBobberEntity bobber = findBobber(mc, mc.player);
        if (bobber == null) {
            return;
        }
        double dx = bobber.getX() - x;
        double dy = bobber.getY() - y;
        double dz = bobber.getZ() - z;
        double dist2 = dx * dx + dy * dy + dz * dz;
        if (dist2 > 100.0D) {
            return;
        }
        String line = String.format(Locale.ROOT, "%s (%.1fm)", id, Math.sqrt(dist2));
        synchronized (SOUND_LOG) {
            SOUND_LOG.addFirst(line);
            while (SOUND_LOG.size() > 6) {
                SOUND_LOG.removeLast();
            }
        }
    }

    public static final String SPLASH_ID = "minecraft:entity.fishing_bobber.splash";

    // ------------------------------------------------------------------
    // Boucle principale
    // ------------------------------------------------------------------

    public static void tick(MinecraftClient mc) {
        ClientPlayerEntity player = mc.player;

        if (player == null || mc.world == null || mc.interactionManager == null) {
            if (enabled) {
                resetState();
            }
            return;
        }

        FishConfig cfg = TropiFishClient.config;

        // Le suivi du bouchon tourne aussi en diagnostic seul, mod coupe.
        FishingBobberEntity bobber = findBobber(mc, player);
        updateTracking(bobber);

        if (debug) {
            ItemStack main = player.getMainHandStack();
            ItemStack off = player.getOffHandStack();
            ItemStack rod = isRod(main) ? main : (isRod(off) ? off : (main.isEmpty() ? off : main));
            lastRodId = rod.isEmpty() ? "(vide)" : Registries.ITEM.getId(rod.getItem()).toString();
        }

        if (!enabled) {
            return;
        }

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

        if (cooldown > 0) {
            cooldown--;
            return;
        }

        if (pending != Action.NONE) {
            Action action = pending;
            pending = Action.NONE;
            use(mc, player, hand);
            cooldown = GRACE_TICKS;
            waitTicks = 0;
            settleTicks = 0;
            splashFlag = false;
            resetTracking();
            if (action == Action.REEL && reelIsCatch) {
                catches++;
            }
            reelIsCatch = false;
            stateLabel = action == Action.REEL ? "remontee" : "lancer";
            return;
        }

        if (bobber == null || bobber.isRemoved()) {
            stateLabel = "lancer...";
            schedule(Action.CAST, cfg.castMinTicks, cfg.castMaxTicks);
            return;
        }

        waitTicks++;
        stateLabel = "attente";

        // 1) Son d'eclaboussure pres du bouchon
        if (splashFlag) {
            splashFlag = false;
            double dx = bobber.getX() - splashX;
            double dy = bobber.getY() - splashY;
            double dz = bobber.getZ() - splashZ;
            if (dx * dx + dy * dy + dz * dz <= 6.0D) {
                bite("son");
                return;
            }
        }

        boolean inWater = bobber.isTouchingWater();
        lastInWater = inWater;

        if (inWater) {
            settleTicks++;

            // 2) Chute soudaine de la position (le bouchon plonge)
            if (cfg.usePositionFallback
                    && settleTicks > cfg.settleTicksRequired
                    && lastDy < -cfg.positionDropThreshold) {
                bite("position");
                return;
            }

            // 3) Vitesse negative envoyee par le serveur
            if (cfg.useVelocityFallback
                    && settleTicks > cfg.settleTicksRequired
                    && bobber.getVelocity().y < -cfg.velocityThreshold) {
                bite("vitesse");
                return;
            }
        } else {
            settleTicks = 0;
        }

        // 4) Bouchon coince / touche ratee : on relance
        if (waitTicks > cfg.timeoutSeconds * 20) {
            reelIsCatch = false;
            stateLabel = "relance (timeout)";
            schedule(Action.REEL, 1, 3);
        }
    }

    // ------------------------------------------------------------------
    // Suivi / detection
    // ------------------------------------------------------------------

    private static void bite(String source) {
        reelIsCatch = true;
        stateLabel = "touche ! (" + source + ")";
        FishConfig cfg = TropiFishClient.config;
        schedule(Action.REEL, cfg.reactionMinTicks, cfg.reactionMaxTicks);
    }

    /**
     * Cherche le bouchon du joueur. Utilise player.fishHook quand il est
     * renseigne, sinon balaie les entites proches : certaines cannes moddees
     * ne remplissent pas ce champ cote client.
     */
    private static FishingBobberEntity findBobber(MinecraftClient mc, ClientPlayerEntity player) {
        FishingBobberEntity hook = player.fishHook;
        if (hook != null && !hook.isRemoved()) {
            lastHookSource = "fishHook";
            lastHookId = Registries.ENTITY_TYPE.getId(hook.getType()).toString();
            return hook;
        }
        if (mc.world == null) {
            lastHookSource = "-";
            lastHookId = "-";
            return null;
        }
        for (Entity entity : mc.world.getEntities()) {
            if (!(entity instanceof FishingBobberEntity candidate) || candidate.isRemoved()) {
                continue;
            }
            if (candidate.getOwner() == player || candidate.getPlayerOwner() == player) {
                lastHookSource = "scan";
                lastHookId = Registries.ENTITY_TYPE.getId(candidate.getType()).toString();
                return candidate;
            }
        }
        lastHookSource = "-";
        lastHookId = "-";
        return null;
    }

    private static void updateTracking(FishingBobberEntity bobber) {
        if (bobber == null) {
            resetTracking();
            return;
        }
        if (bobber.getId() != trackedBobberId) {
            trackedBobberId = bobber.getId();
            lastY = bobber.getY();
            lastDy = 0.0D;
            minDy = 0.0D;
            minVy = 0.0D;
            return;
        }
        double y = bobber.getY();
        if (!Double.isNaN(lastY)) {
            lastDy = y - lastY;
            if (lastDy < minDy) {
                minDy = lastDy;
            }
        }
        lastY = y;
        double vy = bobber.getVelocity().y;
        if (vy < minVy) {
            minVy = vy;
        }
    }

    private static void resetTracking() {
        trackedBobberId = -1;
        lastY = Double.NaN;
        lastDy = 0.0D;
        minDy = 0.0D;
        minVy = 0.0D;
    }

    // ------------------------------------------------------------------
    // Diagnostic
    // ------------------------------------------------------------------

    public static List<String> getDebugLines() {
        List<String> lines = new ArrayList<>();
        lines.add("\u00a7ecanne \u00a7f" + lastRodId);
        lines.add("\u00a7ehook  \u00a7f" + lastHookId + " \u00a78via " + lastHookSource);
        lines.add(String.format(Locale.ROOT,
                "\u00a7eeau   \u00a7f%s  \u00a7edy \u00a7f%+.4f  \u00a7evy \u00a7f%+.4f",
                lastInWater, lastDy, currentVy()));
        lines.add(String.format(Locale.ROOT,
                "\u00a7emin   \u00a7edy \u00a7f%+.4f  \u00a7evy \u00a7f%+.4f",
                minDy, minVy));
        lines.add("\u00a7eticks \u00a7fattente " + waitTicks + "  settle " + settleTicks);
        lines.add("\u00a7eetat  \u00a7f" + stateLabel);
        synchronized (SOUND_LOG) {
            if (SOUND_LOG.isEmpty()) {
                lines.add("\u00a78(aucun son capte pres du bouchon)");
            } else {
                for (String s : SOUND_LOG) {
                    lines.add("\u00a7b> \u00a7f" + s);
                }
            }
        }
        return lines;
    }

    private static double currentVy() {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player == null) {
            return 0.0D;
        }
        FishingBobberEntity bobber = findBobber(mc, mc.player);
        return bobber == null ? 0.0D : bobber.getVelocity().y;
    }

    // ------------------------------------------------------------------
    // Canne
    // ------------------------------------------------------------------

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
        resetTracking();
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
