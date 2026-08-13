package fr.maitre.tropifish;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;

import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/** Config JSON : config/tropifish.json */
public class FishConfig {

    /** Affiche l'indicateur en haut a gauche. */
    public boolean hudEnabled = true;
    /** Met la peche en pause quand un ecran/inventaire est ouvert. */
    public boolean pauseWhenScreenOpen = true;
    /** Detection de secours basee sur la vitesse du bouchon. */
    public boolean useVelocityFallback = true;
    /** Detection de secours basee sur la chute de position du bouchon. */
    public boolean usePositionFallback = true;

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
            "cobblemon:fishing.bobber_land",
            "minecraft:entity.bobber.throw"
    ));

    /** Rayon (blocs) autour du bouchon OU du joueur pour accepter un son de touche. */
    public double biteSoundRadius = 24.0D;

    /** Ticks pose dans l'eau avant d'autoriser les detections de secours. */
    public int settleTicksRequired = 20;
    /** Chute de position (blocs/tick) au-dela de laquelle on considere une touche. */
    public double positionDropThreshold = 0.035D;
    /** Vitesse verticale au-dela de laquelle on considere une touche. */
    public double velocityThreshold = 0.09D;
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
                    FishConfig loaded = GSON.fromJson(reader, FishConfig.class);
                    if (loaded != null) {
                        FishConfig defaults = new FishConfig();
                        if (loaded.extraRodIds == null) {
                            loaded.extraRodIds = new ArrayList<>();
                        }
                        if (loaded.biteSoundIds == null || loaded.biteSoundIds.isEmpty()) {
                            loaded.biteSoundIds = defaults.biteSoundIds;
                        }
                        if (loaded.landSoundIds == null || loaded.landSoundIds.isEmpty()) {
                            loaded.landSoundIds = defaults.landSoundIds;
                        }
                        if (loaded.biteSoundRadius <= 0.0D) {
                            loaded.biteSoundRadius = defaults.biteSoundRadius;
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

    public void save() {
        try (Writer writer = Files.newBufferedWriter(path())) {
            GSON.toJson(this, writer);
        } catch (Exception ignored) {
            // pas bloquant
        }
    }
}
