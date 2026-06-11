# LavaRising

Paper `26.1.2` lava-rising minigame plugin.

## Install

1. Put `LavaRising-1.2.0.jar` in your server `plugins` folder.
2. Start or restart the Paper server.
3. OPs can run the setup and game commands.

## Start The Game

```text
/lavastart
```

or:

```text
/lavarising start
```

Stop the game:

```text
/lavastop
/lavarising stop
```

Check status:

```text
/lavarising status
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

If all alive players stay at `Y>=100` for this many seconds before phase 5, phase 5 starts early and sudden death starts instantly. Set to `0` to disable.

```text
/lavarising settings noMoreBottomDwellers 60
```

### buildingBlockGiveRate

How many building blocks are given per lava rise. Players are capped at one stack of the round block.

```text
/lavarising settings buildingBlockGiveRate 16
```

### giveDirt

Whether players receive building blocks. Most rounds give dirt. There is a 10% chance the round gives sand instead.

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
- Lava rises to `Y=319`.
- Phase 5 starts at `Y=314`.
- Sudden death starts instantly when phase 5 starts.
- Difficulty changes to `HARD` when lava reaches `Y=60`, or when `noMoreBottomDwellers` starts phase 5 early.
- The original world difficulty is restored when the game ends, stops, or resets.
- Players cannot place blocks above `Y=100` before lava reaches `Y=60`.
- If a player tries to build above `Y=200` during phase 1 or 2, they get nausea.
- If they keep trying to build that high, they also get blindness.
- Dropped item entities are cleared every lava rise to reduce lag.

## Permissions

All commands use:

```text
lavarising.use
```

Default permission is `op`.
