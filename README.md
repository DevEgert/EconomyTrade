# EconomyTrade

Player-to-player trading for Minecraft Forge 1.20.1. The mod is intended to run alongside the [Specialized Economy](https://github.com/DevEgert/specialized-economy) KubeJS profession scripts.

## Why I built this
The main economy system depended on player trading, but existing solutions didn’t support the restrictions and flow I needed. I built a separate mod to handle secure real-time trading between players.

## Current State

The mod builds and has the core trading flow implemented. In-game testing and balance work are still ongoing.

## Features

- Right-click a player with an empty hand to send a trade request
- `/trade <player>` command support
- 10 block distance check before a trade can start
- Pending requests with `/trade accept`, `/trade decline`, and `/trade cancel`
- Live offer updates in the trade screen
- Both players must confirm before completion
- Short countdown before the trade is finalized
- Request timeout, disconnect handling, and trade cooldown
- Server-side validation before items are transferred

## Commands

| Command | Description |
|---------|-------------|
| `/trade <player>` | Send a trade request |
| `/trade accept` | Accept an incoming request |
| `/trade decline` | Decline an incoming request |
| `/trade cancel` | Cancel an active trade |

## Project Layout

| Path | Purpose |
|------|---------|
| `EconomyTrade.java` | Mod entry point |
| `handler/TradeHandler.java` | Server-side trade sessions and transfer logic |
| `handler/TradeCommand.java` | Commands and right-click trade requests |
| `network/NetworkHandler.java` | Forge network channel setup |
| `network/TradePacket.java` | Packet serialization |
| `network/ClientPacketHandler.java` | Client packet handling |
| `screen/TradeScreen.java` | Trade screen UI |

## Build

```bash
./gradlew build
```

The compiled jar is written to `build/libs/`.

## Install

1. Build the mod.
2. Copy the jar from `build/libs/` into the server `mods/` folder.
3. Install the same jar on each client that connects to the server.

## License

MIT
