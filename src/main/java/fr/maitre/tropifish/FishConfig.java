package fr.maitre.tropifish;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import net.fabricmc.loader.api.FabricLoader;

import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/** Config JSON : config/tropifish.json */
public class FishConfig {

    /** Version du format de config. Sert a migrer les anciens fichiers. */
    public static final int CURRENT_VERSION = 2;

    public int configVersion = CURRENT_VERSION;

    /** Affiche l'indicateur en haut a gauche. */
    public boolean hudEnabled = true;
    /** Met la peche en pause quand un ecran/inventaire est ouvert. */
    public boolean pauseWhenScreenOpen = true;

    /**
     * Detections de secours basees sur le mouvement du bouchon.
     * DESACTIVEES par defaut depuis la v2 : l'agitation de l'eau (Pokemon qui
     * nage, joueur qui saute, vagues) fait osciller le bouchon au-dela des
     * seuils et provoquait de fausses touches. Le son reste la detection
     * principale, le timeout sert de filet de securite.
     */
    public boolean useVelocityFallback = false;
    public boolean usePositionFallback = false;

    /**
     * Sons qui signalent une touche. Cobblemon utilise son propre son au lieu
     * du son vanilla, et le joue pres du joueur plutot que pres du bouchon.
     */
    public List<String> biteSoundIds = new ArrayList<>(List.of(
            "minecraft:entity.fishing_bobber.splash",
            "cobblemon:fishing.notification"
    ));

    /** Sons qui signalent que le bouchon vient de se poser sur l'eau. */
    public List<String> landSoundIds = new ArrayList<>(List.of(
            "cobblemon:fishing.bobber_land"
    ));

    // --- filtrage anti faux positifs -----------------------------------

    /**
     * Rayon (blocs) autour du JOUEUR pour accepter un son de touche.
     * Cobblemon joue la notification a la position du proprietaire de la
     * canne : un rayon serre suffit a ignorer les voisins qui pechent.
     */
    public double biteSoundPlayerRadius = 4.0D;
    /** Rayon (blocs) autour de MON bouchon pour accepter un son de touche. */
    public double biteSoundBobberRadius = 4.0D;
    /** Rayon (blocs) pour accepter un son d'amerrissage. */
    public double landSoundRadius = 6.0D;

    /**
     * Rejette un son si un autre joueur en est plus proche que moi (ou que mon
     * bouchon). C'est le filtre decisif quand quelqu'un peche colle a moi.
     */
    public boolean rejectSoundIfCloserToOtherPlayer = true;

    /**
     * Ticks minimum apres le lancer avant d'accepter un son de touche.
     * Une touche ne peut pas arriver dans la seconde qui suit le lancer.
     */
    public int minTicksBeforeBite = 15;

    /**
     * Exige que MON bouchon plonge reellement dans les `dipConfirmTicks` qui
     * suivent le son pour valider la touche. A activer si des faux positifs
     * subsistent malgre les filtres de distance (voisin colle a toi).
     * Attention : si le bouchon Cobblemon ne bouge pas visiblement a la
     * touche, cette option empeche toute prise.
     */
    public boolean requireDipConfirm = false;
    public int dipConfirmTicks = 12;

    // --- secours (uniquement si reactives ci-dessus) --------------------

    /** Ticks pose dans l'eau avant d'autoriser les detections de secours. */
    public int settleTicksRequired = 20;
    /** Secondes d'attente avant d'armer les secours (0 = tout de suite). */
    public int fallbackArmAfterSeconds = 15;
    /** Chute de position (blocs/tick) au-dela de laquelle on considere une touche. */
    public double positionDropThreshold = 0.08D;
    /** Vitesse verticale au-dela de laquelle on considere une touche. */
    public double velocityThreshold = 0.15D;

    // --- divers ---------------------------------------------------------

    /** Messages dans le chat lors des activations/coupures. */
    public boolean chatMessages = true;
    /**
     * Identifiants d'objets a traiter comme des cannes, en plus des cannes
     * vanilla et de toutes les cannes Cobblemon (cobblemon:*_rod).
     * Exemple : ["autremod:ma_canne"]
     */
    public List<String> extraRodIds = new ArrayList<>();

    /** Coupe automatiquement quand il reste ce nombre de points de durabilite (0 = jamais). */
    public int minDurability = 15;
    /** Relance si rien ne mord au bout de ce delai. */
    public int timeoutSeconds = 45;

    /** Delai avant de remonter apres une touche (en ticks). */
    public int reactionMinTicks = 3;
    public int reactionMaxTicks = 8;

    /** Delai avant de relancer la ligne (en ticks). */
    public int castMinTicks = 8;
    public int castMaxTicks = 18;

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private static Path path() {
        return FabricLoader.getInstance().getConfigDir().resolve("tropifish.json");
    }

    public static FishConfig load() {
        try {
            Path p = path();
            if (Files.exists(p)) {
                try (Reader reader = Files.newBufferedReader(p)) {
                    JsonObject root = JsonParser.parseReader(reader).getAsJsonObject();
                    FishConfig loaded = GSON.fromJson(root, FishConfig.class);
                    if (loaded != null) {
                        boolean legacy = !root.has("configVersion")
                                || loaded.configVersion < CURRENT_VERSION;
                        loaded.fillGaps();
                        if (legacy) {
                            loaded.migrateToV2();
                            loaded.save();
                        }
                        return loaded;
                    }
                }
            }
        } catch (Exception ignored) {
            // config illisible : on repart sur les valeurs par defaut
        }
        FishConfig fresh = new FishConfig();
        fresh.save();
        return fresh;
    }

    /** Complete les champs absents ou aberrants avec les valeurs par defaut. */
    private void fillGaps() {
        FishConfig d = new FishConfig();
        if (extraRodIds == null) {
            extraRodIds = new ArrayList<>();
        }
        if (biteSoundIds == null || biteSoundIds.isEmpty()) {
            biteSoundIds = d.biteSoundIds;
        }
        if (landSoundIds == null || landSoundIds.isEmpty()) {
            landSoundIds = d.landSoundIds;
        }
        if (biteSoundPlayerRadius <= 0.0D) {
            biteSoundPlayerRadius = d.biteSoundPlayerRadius;
        }
        if (biteSoundBobberRadius <= 0.0D) {
            biteSoundBobberRadius = d.biteSoundBobberRadius;
        }
        if (landSoundRadius <= 0.0D) {
            landSoundRadius = d.landSoundRadius;
        }
        if (dipConfirmTicks <= 0) {
            dipConfirmTicks = d.dipConfirmTicks;
        }
        if (positionDropThreshold <= 0.0D) {
            positionDropThreshold = d.positionDropThreshold;
        }
        if (velocityThreshold <= 0.0D) {
            velocityThreshold = d.velocityThreshold;
        }
        if (timeoutSeconds <= 0) {
            timeoutSeconds = d.timeoutSeconds;
        }
    }

    /**
     * Ancienne config (rayon unique de 24 blocs, secours actifs) : on force les
     * nouveaux reglages anti faux positifs, sinon le fichier existant sur le
     * disque annulerait le correctif.
     */
    private void migrateToV2() {
        FishConfig d = new FishConfig();
        usePositionFallback = false;
        useVelocityFallback = false;
        positionDropThreshold = d.positionDropThreshold;
        velocityThreshold = d.velocityThreshold;
        biteSoundPlayerRadius = d.biteSoundPlayerRadius;
        biteSoundBobberRadius = d.biteSoundBobberRadius;
        landSoundRadius = d.landSoundRadius;
        rejectSoundIfCloserToOtherPlayer = true;
        minTicksBeforeBite = d.minTicksBeforeBite;
        fallbackArmAfterSeconds = d.fallbackArmAfterSeconds;
        landSoundIds = d.landSoundIds;
        configVersion = CURRENT_VERSION;
    }

    public void save() {
        try (Writer writer = Files.newBufferedWriter(path())) {
            GSON.toJson(this, writer);
        } catch (Exception ignored) {
            // pas bloquant
        }
    }
}
