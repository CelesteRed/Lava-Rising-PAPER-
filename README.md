# LavaRising

Paper `26.1.2` lava-rising minigame plugin.

## Install

1. Put `LavaRising-1.2.18-paper26.1.2-phase5-only-death.jar` in your server `plugins` folder.
2. Start or restart the Paper server.
3. OPs can run the setup and game commands.

## Start The Game

```text
/start
/lavastart
```

or:

```text
/lavarising start
```

When no round is active, players are kept in the lobby at the configured lobby X/Z. A round can start when at least `minPlayersToStart` players are in the lobby. If no OP/admin is online, any player with `lavarising.start` can use `/start`. If an OP/admin is online, an OP/admin must start it.

Stop the game:

```text
/lavastop
/lavarising stop
```

Check status:

```text
/lavarising status
```

Temporarily speed up or slow down the current lava phase during a running game:

```text
/lavaspeedbypass <seconds-per-block>
```

Reset the current phase back to its configured speed:

```text
/lavaspeedbypass
```

Reset players and stop the current round:

```text
/reset
```

## Settings Command

Show all settings:

```text
/lavarising settings
```

Show one setting:

```text
/lavarising settings <option>
```

Set one setting:

```text
/lavarising settings <option> <value>
```

Settings can only be changed while no game is active.

## Settings

### arenaDiameter

Diameter of the playable arena and starting world border.

```text
/lavarising settings arenaDiameter 100
```

### lavaRisingSpeed

Seconds per lava layer for each phase.

Show all phase speeds:

```text
/lavarising settings lavaRisingSpeed
```

Set one phase:

```text
/lavarising settings lavaRisingSpeed <phase> <seconds>
```

Set all phases:

```text
/lavarising settings lavaRisingSpeed all 2
```

Phases:

```text
1 start       Lava from Y=-64
2 reach0      Lava from Y=0
3 reach60     Lava from Y=60
4 reach100    Lava from Y=100
5 reach314    Lava from Y=314
6 heightLimit Lava reaches Y=319
```

Default:

```yaml
lavaRisingSpeed:
  start: 2.0
  reach0: 2.0
  reach60: 1.0
  reach100: 1.0
  reach314: 10.0
  heightLimit: 10.0
```

### suddenDeathSpeed

How many seconds the border takes to shrink during sudden death.

```text
/lavarising settings suddenDeathSpeed 300
```

### suddenDeathRadius

Radius of the final sudden-death zone. `1` means a `3x3` final zone.

```text
/lavarising settings suddenDeathRadius 1
```

### suddenDeathDelay

Legacy delay setting. Current behavior starts sudden death instantly when phase 5 begins.

```text
/lavarising settings suddenDeathDelay 0
```

### noMoreBottomDwellers

Legacy setting. It is forced to `0`; sudden death no longer starts early from player height.

```text
/lavarising settings noMoreBottomDwellers 0
```

### buildingBlockGiveRate

How many building blocks are given per lava rise. Players are capped at one stack of the round block.

```text
/lavarising settings buildingBlockGiveRate 16
```

### sandMayhemChance

Integer chance from `0` to `100` for a round to use sand instead of dirt.

```text
/lavarising settings sandMayhemChance 25
```

### villageStartChance

Integer chance from `0` to `100` for a round to try starting at a village before falling back to normal arena search.

```text
/lavarising settings villageStartChance 25
```

### minPlayersToStart

How many players must be inside the lobby radius before `/start` can begin a round.

```text
/lavarising settings minPlayersToStart 2
```

### arenaBiomeWhitelist

Biomes allowed for fresh arena selection. The selector also requires nearby trees by default.

```text
/lavarising settings arenaBiomeWhitelist FOREST,BIRCH_FOREST,FLOWER_FOREST
```

Config-only arena search options:

```yaml
lobby:
  x: 0.5
  z: 0.5
  radius: 32
arenaSelection:
  minDistanceFromLobby: 512
  minDistanceFromUsedArenas: 512
  searchMinRadius: 700
  searchMaxRadius: 5000
  maxAttempts: 96
  treeCheckRadius: 32
  villageSearchRadiusChunks: 320
  villageSearchAttempts: 12
  biomeWhitelist:
    - FOREST
    - BIRCH_FOREST
    - SAVANNA
usedArenaCenters: []
```

### giveDirt

Whether players receive building blocks. Most rounds give dirt. `sandMayhemChance` controls the chance for a round to give sand instead.

```text
/lavarising settings giveDirt true
```

### soundsEnabled

Enables milestone and sudden-death sounds.

```text
/lavarising settings soundsEnabled true
```

### milestoneMessages

Enables lava-height chat messages.

```text
/lavarising settings milestoneMessages true
```

### pvpAtSurface

Enables PVP when lava reaches `Y=60`.

```text
/lavarising settings pvpAtSurface true
```

### borderDamage

Controls whether the world border damages players during sudden death.

```text
/lavarising settings borderDamage true
```

## Gameplay Rules

- Lava starts at `Y=-64`.
- Players wait in the lobby when no round is active.
- Starting a round picks a fresh arena center in a whitelisted, tree-capable surface biome.
- `villageStartChance` controls whether the fresh arena selector first tries to use a nearby valid village.
- Used arena centers are saved in `usedArenaCenters` so future rounds avoid old lava zones.
- Round players spawn around the selected arena center in a circle before countdown.
- Lava rises to `Y=319`.
- Phase 5 starts at `Y=314`.
- Sudden death starts instantly when phase 5 starts at `Y=314`.
- Difficulty changes to `HARD` when lava reaches `Y=60`.
- The original world difficulty is restored when the game ends, stops, or resets.
- Most rounds give dirt as the building block.
- `sandMayhemChance` controls how often Sand Mayhem rounds happen; players get a `Sand Mayhem` title and receive sand for the entire round.
- Players cannot place blocks above `Y=100` before lava reaches `Y=60`.
- If a player tries to build above `Y=200` during phase 1 or 2, they get nausea.
- If they keep trying to build that high, they also get blindness.
- Dropped item entities are not cleared by the lava rise loop.

## Admin Bypass

Use this while lava is actively rising:

```text
/lavaspeedbypass <seconds-per-block>
```

This temporarily changes the speed for the current phase only. It does not edit the config.

Use this with no number to reset the current phase back to the configured speed:

```text
/lavaspeedbypass
```

## Permissions

Admin commands use:

```text
lavarising.use
```

Default permission is `op`.

Public round start:

```text
lavarising.start
```

Default permission is `true`.
