# Cash Clash - Minecraft Minigame Plugin

A comprehensive 4v4 PvP strategy minigame for Paper/Spigot servers featuring an economy system, custom items, rounds progression, and cosmetic rewards.

## Overview

Cash Clash is a team-based PvP minigame where two teams of 4 players compete across 5 rounds to earn, spend, and manage money. The team with the most coins at the end wins!

### Key Features

- **5 Round System**: Each round has a shopping phase and combat phase
- **Economy System**: Earn money through kills, bonuses, and objectives - spend it on gear or save it to win
- **Lives System**: 3 lives in rounds 1-3, 1 life in rounds 4-5
- **12 Starter Kits**: Unique kits for round 1 (Archer, Builder, Healer, Tank, Scout, etc.)
- **Custom Items**: Grenades, Bounce Pads, Medic Pouches, and more
- **Legendary Weapons**: Powerful unique weapons like Wind Bow, Electric Eel Sword, Warden Gloves
- **Custom Armor Sets**: Special armor with unique abilities (Investor's Set, Dragon Set, etc.)
- **Cash Quake Events**: Random events during rounds (Lottery, Life Steal, Tax Time, etc.)
- **Objectives**: Zombie Dungeon, Evil Torches, Three Little Piggies
- **Supply Drops**: Random loot spawns on the map
- **Team Dislodge**: Anti-camping mechanic that forces teams to spread out
- **Investment System**: Wallet, Purse, and Ender Bag with risk/reward mechanics
- **Forfeit System**: Team voting to end rounds early
- **Cosmetics**: Kill effects, arrow trails, armor trims (VIP/MVP/Clasher ranks)

## Requirements

- **Server**: Paper 1.21.4+ (or compatible fork)
- **Java**: 21+
- **Players**: 8 (4v4)

## Installation

1. Download the latest `CashClash.jar` from releases
2. Place in your server's `plugins/` folder
3. Restart the server
4. Configure `plugins/CashClash/config.yml` as needed
5. Set up arenas using `/cc setarena <name>`

## Commands

### Player Commands
- `/cc join` - Join the queue
- `/cc leave` - Leave your current game
- `/cc stats` - View your statistics
- `/cc forfeit` - Vote to forfeit the current round
- `/cc transfer <player> <amount>` - Transfer money to a teammate

### Admin Commands
- `/cc start <arena>` - Start a new game session
- `/cc stop` - End the current game
- `/cc setarena <name>` - Set up a new arena

## Permissions

- `cashclash.use` - Basic command access (default: true)
- `cashclash.admin` - Admin commands (default: op)
- `cashclash.vip` - VIP rank perks
- `cashclash.mvp` - MVP rank perks
- `cashclash.clasher` - Clasher rank perks

## Game Modes

### Casual
- 5 rounds with shopping and combat phases
- 3 lives in rounds 1-3, 1 life in rounds 4-5
- Team with most coins at the end wins

### Competitive (Planned)
- Same as Casual but with ELO ranking
- Bronze → Silver → Gold → Platinum → Diamond → Master → Cash Clasher

### Arcade Modes (Planned)
- **Hardcore**: Only 1 life per round
- **Endless**: Infinite lives, 30-minute continuous combat
- **Capture the Flag**: Bonus money for flag captures
- **King of the Hill**: Control the capture point to earn points

## Configuration

All values are configurable in `config.yml`:
- Round timings
- Economy values (starting money, kill rewards, transfer fees)
- Cash Quake event settings
- Team Dislodge parameters
- Damage zone settings
- Custom messages

## Arena Setup

Each arena requires:
- 3 spawn points for Team 1 (marked with gold blocks)
- 3 spawn points for Team 2 (marked with gold blocks)
- Spectator spawn
- Lobby spawn
- Defined world boundaries

## Development Status

### Implemented ✅
- Core game loop and state management
- Team system
- Round progression
- Economy system with transfers and fees
- Investment system (Wallet, Purse, Ender Bag)
- Kit system (12 starter kits)
- Bonus tracking (First Blood, Rampage, Close Calls, etc.)
- Death handling (keep inventory, lives system)
- Configuration system
- Command framework
- Team Dislodge system
- Cash Quake event scheduler

### In Progress 🚧
- Shop GUI system
- Custom item implementations
- Legendary weapon abilities
- Custom armor effects
- Arena management
- Block regeneration
- Forfeit voting system
- Y-level damage zones
- Spectator mode

### Planned 📋
- Cosmetics system (kill effects, arrow trails, armor trims)
- Rank system (VIP, MVP, Clasher)
- Statistics tracking and persistence
- Competitive mode with ELO
- Arcade game modes
- Custom resource pack support
- Multi-language support

## Project Structure

```
src/main/java/me/psikuvit/cashClash/
├── arena/          # Arena management
├── command/        # Command handlers
├── config/         # Configuration management
├── game/           # Core game logic
├── items/          # Custom items and legendaries
├── kit/            # Starter kits
├── listener/       # Event listeners
├── manager/        # Game managers (economy, rounds, events)
├── player/         # Player data and wrappers
└── shop/           # Shop system
```

## Contributing

Contributions are welcome! Please:
1. Fork the repository
2. Create a feature branch
3. Make your changes
4. Submit a pull request

## License

[Add your license here]

## Credits

**Developer**: psikuvit  
**Version**: 1.0  
**API**: Paper 1.21.4

---

*Cash Clash - Where strategy meets economy in epic 4v4 PvP battles!*

