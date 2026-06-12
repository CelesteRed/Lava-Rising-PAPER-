# LavaRising 2.0.0

Rebuilt Paper `26.1.2` lava rising plugin.

## Layout

- `../v1` contains the archived 1.x project.
- `v2` contains the rebuilt 2.0.0 project.

## Commands

```text
/lava start
/lava stop
/lava reset
/lava status
/lava bypass [seconds|clear]
/lava revive <player> [targetPlayer]
/lava setlobby [radius]
/lava setlobby <x> <z> [radius]
/lava get <config.path>
/lava set <config.path> <value>
/lava reload
```

## Notes

- Runtime arena history is stored in `data.yml`.
- Config is grouped by lobby, start, round, lava, deathmatch, celebration, arena selection, performance, and build limits.
- The waiting lobby is a separate void world named `lobby`, kept at daytime with a generated platform.
- Rounds still use `round.world` by default, usually `world`.
- OP/admin players bypass waiting-lobby pulls so the lobby can be inspected and moved.
- Deathmatch starts when lava reaches `round.deathmatchStartY` and lava continues rising to `round.maxLavaY`.
- Dropped-item clearing on lava rise is disabled by default.
- Speeds at or below `performance.fastLavaSpeedThreshold` use `performance.fastLavaChunksPerTick` so `/lava bypass 0.1` is not capped by normal anti-lag batching.
