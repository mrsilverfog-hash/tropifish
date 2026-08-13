# TropiFish — etat technique

Mod client Fabric **1.21.1** (Java 21) de peche automatique. A lire en debut de session.

## Fonctionnement

Machine a etats dans `AutoFisher.tick()`, appelee a chaque tick client :

1. **Pas de bouchon** (`player.fishHook == null`) -> programme un `CAST` apres un delai aleatoire.
2. **Bouchon en eau** -> attend une touche.
3. **Touche detectee** -> programme un `REEL` apres un delai de reaction aleatoire.
4. Apres chaque clic droit : `GRACE_TICKS` (8) de pause, le temps que le serveur reponde.

### Detection de la touche

- **Principale** : mixin sur `ClientPlayNetworkHandler.onPlaySound`. Le mixin pose
  juste un drapeau volatile, tout le reste est traite dans le tick client.

  Sons confirmes en jeu sur Tropimon (releves via le HUD diagnostic) :
  - touche : `cobblemon:fishing.notification` (le son vanilla
    `entity.fishing_bobber.splash` n'est **pas** utilise par les Poke Cannes)
  - amerrissage : `cobblemon:fishing.bobber_land`

  **Important** : Cobblemon joue le son de touche pres du **joueur**, pas du
  bouchon (releve ~9.8 m du bouchon). Le test de proximite accepte donc la plus
  courte des deux distances (bouchon ou joueur), rayon `biteSoundRadius` = 24.

  Les deux listes sont dans la config (`biteSoundIds`, `landSoundIds`) pour
  pouvoir en ajouter sans rebuild.
- **Secours** : bouchon pose depuis plus de `settleTicksRequired` ticks, puis
  chute de position (`usePositionFallback`, `lastDy < -positionDropThreshold`) ou
  vitesse verticale negative (`useVelocityFallback`, `vy < -velocityThreshold`).

  **Important** : le bouchon Cobblemon (`cobblemon:poke_bobber`) renvoie
  `isTouchingWater() == false` alors qu'il flotte bien. L'etat "pose" vient donc
  du son d'amerrissage (`landed`), avec `isTouchingWater()` en complement pour le
  vanilla — ne jamais rebrancher les secours sur `isTouchingWater()` seul.
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
