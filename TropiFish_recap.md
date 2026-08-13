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

## Cannes reconnues

`AutoFisher.isRod()` accepte, sans dependance de compilation sur Cobblemon :

- tout item `instanceof FishingRodItem` (vanilla),
- tout `cobblemon:*_rod` (Poke Cannes : `roseate_rod`, `poke_rod`, etc.),
- tout id liste dans `extraRodIds` de la config.

**Attention** : les Poke Cannes Cobblemon peuvent jouer un son de touche
different de `entity.fishing_bobber.splash`. Voir le mode diagnostic ci-dessous.

## Recherche du bouchon

`findBobber()` utilise `player.fishHook` en priorite, et bascule sur un balayage
des entites du monde (`FishingBobberEntity` dont l'owner est le joueur) si le
champ est vide — certaines cannes moddees ne le remplissent pas cote client.
Le HUD diagnostic indique laquelle des deux voies a servi (`via fishHook` /
`via scan`).

## Mode diagnostic (touche F6)

Affiche en surimpression : id de la canne en main, id/type du bouchon et voie de
detection, presence dans l'eau, `dy` (delta de position par tick) et `vy`
(vitesse verticale) instantanes, leurs minimums depuis le lancer, les compteurs
de ticks, l'etat courant, et les 6 derniers sons joues a moins de 10 blocs du
bouchon avec leur distance.

Sert a calibrer : les minimums de `dy`/`vy` releves au moment ou le bouchon
plonge donnent les seuils reels a mettre dans `positionDropThreshold` /
`velocityThreshold`, et la liste des sons donne l'id de touche a hardcoder.

## Securites

- Coupe si aucune canne a peche en main (main ou seconde main).
- Coupe si la durabilite restante <= `minDurability` (15 par defaut).
- Pause quand un ecran/inventaire est ouvert (`pauseWhenScreenOpen`).
- Delais randomises entre min/max pour eviter un rythme parfaitement regulier.

## Controles

- Touche par defaut : **J** (rebindable dans Options > Commandes > TropiFish).
  Les messages de chat et le HUD affichent la touche reellement assignee
  via `TropiFishClient.getToggleKeyName()` — ne jamais hardcoder "J".
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
