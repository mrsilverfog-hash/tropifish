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

  **Important** : Cobblemon joue le son de touche a la position du **joueur**
  proprietaire de la canne, pas du bouchon (releve ~9.8 m du bouchon).

#### Filtrage anti faux positifs (v2)

Le son est diffuse a tous les clients : sans filtre, la touche d'un voisin
declenchait la canne. Trois gardes cumulees dans `soundIsMine()` :

1. **Distance** : le son doit tomber a moins de `biteSoundPlayerRadius` (4 blocs)
   de moi **ou** `biteSoundBobberRadius` (4 blocs) de mon bouchon. L'ancien
   rayon unique de 24 blocs etait la cause n°1 des faux positifs.
2. **Proprietaire** : `rejectSoundIfCloserToOtherPlayer` rejette le son si un
   autre joueur en est plus proche que moi (ou que mon bouchon). C'est le filtre
   decisif quand quelqu'un peche colle a moi, puisque le son part de *sa*
   position.
3. **Chronologie** : `minTicksBeforeBite` (15) ignore un son arrive trop tot
   apres le lancer.

Option `requireDipConfirm` (false par defaut) : n'accepte le son que si mon
bouchon plonge reellement dans les `dipConfirmTicks` suivants. A activer si des
faux positifs subsistent — mais si le bouchon Cobblemon ne bouge pas a la
touche, cette option bloque toutes les prises.

Le son d'amerrissage passe par le meme filtre (`landSoundRadius`), sinon le
lancer d'un voisin remettait `landed`/`settleTicks` a zero chez moi.

#### Secours (desactives par defaut depuis la v2)

`usePositionFallback` / `useVelocityFallback` : chute de position
(`lastDy < -positionDropThreshold`) ou vitesse verticale negative
(`vy < -velocityThreshold`). **Cause n°2 des faux positifs** : l'agitation de
l'eau (Pokemon qui nage, joueur qui saute) faisait osciller le bouchon au-dela
des anciens seuils (0.035 / 0.09). Desormais off par defaut, seuils releves a
0.08 / 0.15, et armes seulement apres `fallbackArmAfterSeconds` (15 s) en plus
de `settleTicksRequired`.

**Important** : le bouchon Cobblemon (`cobblemon:poke_bobber`) renvoie
`isTouchingWater() == false` alors qu'il flotte bien. L'etat "pose" vient donc
du son d'amerrissage (`landed`), avec `isTouchingWater()` en complement pour le
vanilla — ne jamais rebrancher les secours sur `isTouchingWater()` seul.

- **Timeout** : si rien ne mord pendant `timeoutSeconds` (45 par defaut), on
  remonte et on relance (bouchon coince, touche ratee).

#### Migration de config

`FishConfig.load()` lit d'abord le JSON brut : si `configVersion` est absent ou
< 2, les reglages anti faux positifs sont forces et le fichier est reecrit.
Sans ca, l'ancien `config/tropifish.json` sur le disque annulerait le correctif.

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

## Mode diagnostic (touche NON ASSIGNEE par defaut)

Desactive de base depuis la 1.1.0 : le mod fonctionne, l'overlay n'a plus lieu
d'etre en jeu courant. Pour le reactiver, assigner une touche a
"Afficher le diagnostic" dans Options > Commandes > TropiFish.

Affiche en surimpression : id de la canne en main, id/type du bouchon et voie de
detection, presence dans l'eau, `dy` (delta de position par tick) et `vy`
(vitesse verticale) instantanes, leurs minimums depuis le lancer, les compteurs
de ticks, l'etat courant, le nombre de sons rejetes avec le motif du dernier
rejet, et les 6 derniers sons proches avec leur distance a moi et au bouchon.

Sert a calibrer : les minimums de `dy`/`vy` releves au moment ou le bouchon
plonge donnent les seuils reels a mettre dans `positionDropThreshold` /
`velocityThreshold`, et la liste des sons donne l'id de touche a hardcoder.

## Securites

- Coupe si aucune canne a peche en main (main ou seconde main).
- Coupe si la durabilite restante <= `minDurability` (15 par defaut).
- Pause quand un ecran/inventaire est ouvert (`pauseWhenScreenOpen`).
- Delais randomises entre min/max pour eviter un rythme parfaitement regulier.

## Controles

- Peche auto : **J** par defaut (l'utilisateur l'a rebindee sur **F4**).
- Diagnostic : **non assignee** par defaut.
- Rebindables dans Options > Commandes > TropiFish.
- Pour masquer aussi la ligne d'etat : `hudEnabled: false` dans la config.
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
