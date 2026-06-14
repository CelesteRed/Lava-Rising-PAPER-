# LavaRising 2.5.31

Rebuilt Paper `26.1.2` lava rising plugin.

## Layout

- `../v1` contains the archived 1.x project.
- `v2` contains the rebuilt 2.5.31 project.

## Commands

```text
/lava start
/lava stop
/lava reset
/lava status
/lava bypass [seconds|clear]
/lava revive <player> [targetPlayer]
/lava setlobby
/lava get <config.path>
/lava set <config.path> <value>
/lava reload
```

## Notes

- Runtime arena history is stored in `data.yml`.
- Config is grouped by lobby, start, round, lava, deathmatch, celebration, arena selection, performance, and build limits.
- The lobby is a dedicated, EMPTY void dimension (default key `cr_lava:lobby`), separate from the arena world. No platform is generated — build your own spawn in the dimension, stand on it, and run `/lava setlobby`. Until a spawn is set, players use the arena world spawn so nobody drops into the void. Namespaces are lowercase, so `CR_Lava` becomes `cr_lava`.
- Players spawn in the lobby when they join while no round is running, and are sent back there when a round ends.
- Rounds use `round.world` by default, usually `world`. When `/lava start` runs, every online non-spectator joins the round.
- `/lava start` opens a **gamemode vote** (a chest GUI; players click to vote, highest wins, ties broken randomly, Classic is the fallback). `/lava start <gamemode>` skips the vote and forces a mode (admin only). Toggle/duration via `start.gamemodeVote` and `start.voteSeconds`. Modes: `classic`, `lucky-lava-rush`, `hardcore-massacre`, `range-rampage`, `sand-mayhem`.
- Each gamemode has its own file under `plugins/LavaRising/gamemodes/<mode>.yml` — enable/disable, display name/color/icon, description, and per-mode mechanics (lucky-item pool & interval, punch bow & arrows-per-kill, sand/village, blocks given, etc.).
- Difficulty follows PVP: peaceful while PVP is off (`round.countdownDifficulty`), the combat difficulty once PVP turns on (`round.surfaceDifficulty`, default easy). PVP enables when lava reaches `round.pvpEnableY`, default `60`.
- Lava rises through five phases, each with its own speed set via `round.phase.<1-5>` (`/lava set round.phase.3 1.0`). Entering a phase shows a "PHASE N" message and plays a distinct sound (Warden roar at Y0, Wither at Y60, Ender Dragon at Y100, Sculk shriek at deathmatch). Phase messages follow `round.milestoneMessages`; phase sounds follow `round.sounds`. The action bar shows only the lava Y level and PVP ON/OFF.
- During the winner celebration everyone (winner and spectators) is invulnerable with fire resistance, so no one can die.
- Any `/lava bypass <seconds>` fills each lava layer instantly so the chosen speed is exact.
- Deathmatch starts when lava reaches `round.deathmatchStartY` and lava continues rising to `round.maxLavaY`.
- Dropped-item clearing on lava rise is disabled by default.
- Speeds at or below `performance.fastLavaSpeedThreshold` use `performance.fastLavaChunksPerTick` so `/lava bypass 0.1` is not capped by normal anti-lag batching.
