# Bingo Parachute

![Bingo Parachute Logo](docs/images/logo.png)

[![Modrinth Downloads](https://img.shields.io/modrinth/dt/bingo-parachute?logo=modrinth&label=Modrinth)
](https://modrinth.com/mod/bingo-parachute)


English | [中文](docs/README_zh.md)

`Bingo Parachute` is a server-side Fabric addon for `Yet Another Bingo`.

It replaces the normal ground spawn opening with a configurable airdrop flow at the start of a Bingo match. The current feature set includes:

- High-altitude match start
- `BAT` and `ELYTRA` airdrop modes
- Early-match PVP protection
- Temporary inventory and equipment custody during descent
- Short fall-damage immunity after `BAT` manual release and timeout cleanup
- Automatic finish on ground, water, or lava contact

## Screenshots

### Game Start Countdown
![Game Start Countdown](docs/images/game_start_countdown.png)

### Mid-Descent
![Mid-Descent](docs/images/mid_descent.png)

### Landing Timeout Warning
![Landing Timeout Warning](docs/images/landing_timeout_warning.png)

## Current Status

The project is already usable for in-game testing. The main implemented flow includes:

- Hooking into the Bingo lifecycle
- Pinning players to future airdrop anchors during Bingo `COUNTDOWN`
- Entering the actual airdrop flow after `PLAYING`
- `BAT` airdrop mode
- `ELYTRA` airdrop mode
- Early-match PVP protection
- Timeout handling
- Fall-damage immunity after timeout and `BAT` manual release
- Cleanup and restoration after landing

The mod is server-side only and does not require clients to install anything.

## Supported Versions

- `mc1.21.11`
- `mc1.21.8`

Project layout:

- `common`
  Shared flow, state, configuration, and logic that avoids direct Minecraft coupling where possible
- `mc1.21.11`
  Version-specific implementation for `1.21.11`
- `mc1.21.8`
  Version-specific implementation for `1.21.8`

## Flow Overview

The current gameplay flow is:

1. After Bingo initializes, this mod registers and waits for lifecycle events
2. When Bingo enters `COUNTDOWN`, the mod computes and caches high-altitude airdrop anchors
3. During `COUNTDOWN`, players are pinned at those high-altitude anchors instead of their normal ground spawn positions
4. After Bingo enters `PLAYING`, the mod creates the airdrop session for the current match
5. When activation time is reached:
   - The player's inventory and equipment are captured and cleared
   - The player enters `BAT` or `ELYTRA` mode
6. During the airdrop, the mod continuously handles:
   - Flight control
   - PVP protection
   - Timeout countdown
   - Finish checks for ground, water, lava, or death
7. After the airdrop finishes:
   - Temporary carriers or abilities are cleaned up
   - Short fall-damage immunity is applied when needed
   - The original inventory and equipment are restored
   - The player is removed from active airdrop tracking

For a more detailed internal flow description, see:

- `.ai_doc/current_strategy_and_flow.md`

## Build

Compile both versions:

```powershell
.\gradlew.bat :mc1.21.8:compileKotlin :mc1.21.11:compileKotlin
```

Build `1.21.11`:

```powershell
.\gradlew.bat :mc1.21.11:build
```

Artifacts are generated under:

- `mc1.21.11/build/libs`
- `mc1.21.8/build/libs`

## Configuration

Main configuration fields:

- `enabled`
  Enables or disables the addon entirely.
- `debugLogging`
  Enables more verbose debug logging.
- `mode`
  Selects the active airdrop mode, `BAT` or `ELYTRA`.
- `startDelayTicks`
  Delay after `PLAYING` before the airdrop begins when countdown anchors are not being reused.
- `spawnHeight`
  Target height of the airdrop start position.
- `pvpProtectionSeconds`
  Length of early-match PVP protection. This also defines the base airdrop timeout duration.
- `timeoutFallImmunitySeconds`
  Extra fall-damage immunity duration after forced `timeout` cleanup. `BAT` manual `Shift` release currently reuses the same duration.
- `bat.descentSpeed`
  Fixed base descent speed in `BAT` mode.
- `bat.flightSpeed`
  Flight speed scalar in `BAT` mode. The player's look direction changes how this scalar is distributed into horizontal and vertical components.
- `bat.maxHorizontalRadiusChunks`
  Maximum horizontal distance from the origin in `BAT` mode, measured in chunks. Values below `0` mean unlimited.
- `elytra.glideSpeedScale`
  Glide speed scale for `ELYTRA` mode.
- `elytra.maxDiveSpeed`
  Maximum dive speed for `ELYTRA` mode.
- `elytra.maxHorizontalRadiusChunks`
  Maximum horizontal distance from the origin in `ELYTRA` mode, measured in chunks. Values below `0` mean unlimited.

Current default examples:

- `mode = BAT`
- `spawnHeight = 196`
- `pvpProtectionSeconds = 30`
- `timeoutFallImmunitySeconds = 10`
- `bat.descentSpeed = 0.33`
- `bat.flightSpeed = 0.6`
- `bat.maxHorizontalRadiusChunks = 10.0`

## Dependencies

This mod requires the server to also have:

- `Yet Another Bingo`
- `Fabric API`

## Acknowledgements

- Target addon project: `Yet Another Bingo`
- The code and docs were developed with substantial assistance from `OpenAI Codex`

## Disclaimer

- This project is a typical `vibe coding` product, built with a bias toward fast iteration and experimentation
- The current implementation has only received limited compile-time verification and in-game testing, and should not be treated as a fully audited, fully validated, or long-term supported production release
- No guarantees are provided for code quality, edge-case behavior, cross-version compatibility, performance, or potential data/save risks; evaluate carefully and back up your environment before using it on a production server
- If you find a bug, feel free to open an issue; vulnerabilities and clear functional defects will be fixed when possible, but new feature development, behavior expansion, and long-term version support are not guaranteed

## TODO

These goals are clear, but not finished yet:

- Test the full in-game behavior and edge cases of `ELYTRA` mode
- Test full in-game compatibility on `1.21.8`
- Support more Minecraft versions
