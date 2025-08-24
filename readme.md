# Parkour Duels Practice V3

## Setup

For the first time setup:

1. Download the latest release
2. Unzip it and run `release-setup.bat`

To start it, simply run `start.bat`.

## Commands

| Command | Description                                       | Usage                     | Aliases            |
|---------|---------------------------------------------------|---------------------------|--------------------|
| rooms   | Switch to the rooms world or a specific room      | `/rooms`                  | rooms, room, r, il |
| prev    | Switch to the previous room                       | `/prev`                   | prev, p            |
| next    | Switch to the next room                           | `/next`                   | next, n            |
| lobby   | Switch to the lobby world                         | `/lobby`                  | lobby, l           |
| run     | Start a custom run                                | `/run <map> [# of rooms]` | play               |
| custom  | Start the room selection process for a custom run | `/custom`                 | —                  |
| config  | Get or set a config value                         | `/config <key> [value]`   | —                  |

## Configuration

These are the config values you can change with the `/config` command:

| Name             | Default Value | What is this?                                      |
|------------------|---------------|----------------------------------------------------|
| boostCooldown    | 60            | Default cooldown for boosts (in practice runs)     |
| pregameCountdown | 15            | Countdown in the pre-game lobby (in practice runs) |
| slimeLagback     | true          | Whether to simulate the 1.21 slime lagback         |

