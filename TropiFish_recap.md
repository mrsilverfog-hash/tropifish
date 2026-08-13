# TropiFish — etat technique

Mod client Fabric **1.21.1** (Java 21) de peche automatique. A lire en debut de session.

## Fonctionnement

Machine a etats dans `AutoFisher.tick()`, appelee a chaque tick client :

1. **Pas de bouchon** (`player.fishHook == null`) -> programme un `CAST` apres un delai aleatoire.
2. **Bouchon en eau** -> attend une touche.
3. **Touche detectee** -> programme un `REEL` apres un delai de reaction aleatoire.
4. Apres chaque clic droit : `GRACE_TICKS` (8) de pause, le temps que le serveur reponde.

### Detection de la touche

- **Principale** : mixin sur `ClientPlayNetworkHandler.onPlaySound`, on ecoute
  `minecraft:entity.fishing_bobber.splash` et on verifie que le son est a moins de
  ~2.4 blocs du bouchon. Le mixin pose juste un drapeau volatile, tout le reste
  est traite dans le tick client.
- **Secours** (`useVelocityFallback`) : bouchon pose dans l'eau depuis >25 ticks
  et `getVelocity().y < -0.09`. Sert si le son est modifie/masque cote serveur.
- **Timeout** : si rien ne mord pendant `timeoutSeconds` (45 par defaut), on
  remonte et on relance (bouchon coince, touche ratee).

## Securites

- Coupe si aucune canne a peche en main (main ou seconde main).
- Coupe si la durabilite restante <= `minDurability` (15 par defaut).
- Pause quand un ecran/inventaire est ouvert (`pauseWhenScreenOpen`).
- Delais randomises entre min/max pour eviter un rythme parfaitement regulier.

## Controles

- Touche par defaut : **J** (rebindable dans Options > Commandes > TropiFish).
- HUD en haut a gauche : etat courant + compteur de prises.

## Config

`config/tropifish.json`, genere au premier lancement. Champs dans `FishConfig.java`.

## Build

Pas de build local : push sur `main` -> GitHub Actions (Gradle 8.8 + Java 21) ->
artefact `tropifish-jar`.

## Fichiers

- `TropiFishClient.java` — entrypoint, keybind, HUD
- `AutoFisher.java` — machine a etats
- `FishConfig.java` — config JSON
- `mixin/ClientPlayNetworkHandlerMixin.java` — detection du son
