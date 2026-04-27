# EconomyTrade

A player-to-player trade UI mod for Minecraft Forge 1.20.1, designed as a companion to the [Specialized Economy](https://github.com/DevEgert/specialized-economy) profession system.

> **Status:** Beta — fully implemented and compiled, in-game testing in progress.

## Overview

EconomyTrade adds a real-time trading window for players, similar to the trade systems found in MMOs. It's built specifically to support economy-driven servers where players need to exchange items safely without dropping them on the ground.

## Features

- **Right-click to trade** — punch a player with an empty hand to send a trade request
- **Command-based trades** — `/trade <player>` works too
- **Distance check** — players must be within 10 blocks to initiate a trade
- **Pending request system** — receiving player gets `/trade accept` or `/trade decline`
- **Real-time offer updates** — see what your partner is offering as they add items
- **Confirmation lock** — both players must confirm before the trade goes through
- **3-second countdown** — protects against last-second bait-and-switch
- **Auto-cancel safeguards:**
  - Trade requests expire after 30 seconds
  - Disconnecting partner cancels the trade
  - Either side can cancel any time before completion
- **Cooldown** — 30 seconds between successful trades to prevent spam
- **Items returned safely** — if inventory is full, items drop next to the player

## Commands

| Command | Description |
|---------|-------------|
| `/trade <player>` | Send a trade request |
| `/trade accept` | Accept incoming request |
| `/trade decline` | Decline incoming request |
| `/trade cancel` | Cancel an active trade |

## Architecture

- **`EconomyTrade.java`** — main mod entry point, lifecycle hooks
- **`handler/TradeHandler.java`** — server-side session management, tick loop, trade execution
- **`handler/TradeCommand.java`** — command registration, right-click event handler
- **`network/NetworkHandler.java`** — Forge SimpleChannel registration and packet routing
- **`network/TradePacket.java`** — packet serialization (action + items)
- **`network/ClientPacketHandler.java`** — client-side packet handling
- **`screen/TradeScreen.java`** — trading UI (in-progress)

## Tech Stack

- **Java 17**
- **Minecraft Forge 1.20.1**
- **Gradle** build system

## Building

```bash
./gradlew build
```

The compiled `.jar` will be in `build/libs/`.

## Installation

1. Build the mod (or download from Releases)
2. Drop the `.jar` into your Minecraft server's `mods/` folder
3. Make sure both server and clients have the mod installed

## Development Notes

Built as a learning exercise in Minecraft Forge mod development, networking, and client-server synchronization. AI tools were used in the development workflow, but all design decisions and architecture are my own.

## License

MIT
