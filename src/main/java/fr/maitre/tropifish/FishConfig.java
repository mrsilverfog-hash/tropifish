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
                        if (loaded.extraRodIds == null) {
                            loaded.extraRodIds = new ArrayList<>();
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
