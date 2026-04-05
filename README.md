# Cash Clash - Minecraft Minigame Plugin

A comprehensive 4v4 PvP strategy minigame for Paper/Spigot servers featuring an economy system, custom items, rounds progression, and cosmetic rewards.

## Overview

Cash Clash is a team-based PvP minigame where two teams of 4 players compete across 5 rounds to earn, spend, and manage money. The team with the most coins at the end wins!

### Economy & Progression
- **Loss Bonus**: Losing teams earn progressively more money per consecutive loss (5k → 7.5k → 10k)
- **Kill Bonus**: Each kill grants the entire team split money (1.875k per player)
- **Starting Money**: 3,000 coins per player each round
- **Spending Strategy**: Buy strategically or save for future rounds

### Round System
- **Round 1**: Kit selection + base items (balanced starter phase)
- **Rounds 2-5**: Base items only (economic emphasis)
- **Shopping Phase**: 60 seconds to buy/upgrade gear
- **Combat Phase**: Defeat enemies or control time to secure victory
- **Lives**: Infinite (no respawn limit within a round)

### Map Grief Prevention
- **Water/Lava Limiting**: Maximum 4 sources per team; water won't spread except to break webs
- **Bucket Refill**: Water bucket refills automatically after 10 seconds if drained; lava doesn't refill
- **Web Limits**: Maximum 4 webs purchasable per round; bought individually (1 at a time); despawn after 5 seconds without contact
- **Leaf Blocks**: Maximum 64 per player; stack limit of 3; decay after 6 seconds
- **Enchantment Limits**: Sharpness III max, Fire Aspect I max, Piercing III max

### Key Features

- **5 Round System**: Each round has a shopping phase and combat phase
- **Economy System**: Earn money through kills, bonuses, and objectives - spend it on gear or save it to win
- **12 Starter Kits**: Unique kits for round 1 (Archer, Builder, Healer, Tank, Scout, etc.)
- **Custom Items**: Grenades, Bounce Pads, Medic Pouches, and more
- **Legendary Weapons**: Powerful unique weapons like Wind Bow, Electric Eel Sword, Warden Gloves
- **Custom Armor Sets**: Special armor with unique abilities (Investor's Set, Dragon Set, Flamebridgers, etc.)
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
- `/cc forcenextround` - Force the next round to begin
- `/cc selectkit <player> <kit>` - Select a specific kit for a player

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
- **Capture the Flag**: Steal the enemy flag and return it to your base's capture zone (first to 2 captures wins)
- **King of the Hill**: Control the capture point to earn points

## Capture the Flag Mode

Capture the Flag is a strategic gamemode where teams must steal and return the enemy flag to earn points.

### Mechanics
- **Flag Stealing**: Step on the enemy flag location to pick it up (automatically detect position)
- **Flag Carrying**: Carrier receives glowing effect every 5 seconds; silenced (no custom abilities)
- **Flag Capture**: Carry flag to your team's capture plate and stand on it for 3 seconds
- **Capture Bonus**: Teams earn 15,000 coins split among all players if flag is held for 45+ seconds
- **Win Condition**: First team to 2 captures wins (normal mode) or 4 captures (sudden death)
- **Sudden Death**: Triggered at 3-3 captures; requires 4 captures to win; extra heart mechanic replaces money bonuses
- **Flag Return**: When flag holder dies, flag automatically returns to enemy base

### Pressure Plate Configuration
Pressure plate locations are configured in arena template files:
```yaml
ctf:
  capture-team1:
    world: arena_world
    x: -50.5
    y: 64
    z: 0.5
    pitch: 0
    yaw: 0
  capture-team2:
    world: arena_world
    x: 50.5
    y: 64
    z: 0.5
    pitch: 0
    yaw: 0
```

The game automatically places heavy weighted pressure plates (gold) at these locations when CTF mode starts.

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
- Team system with green team outlines (visible through walls, no team damage)
- Round progression (5 rounds with shopping and combat phases)
- Economy system with transfers and fees
- Loss bonus system (5k-10k per consecutive loss, 1.875k per team kill)
- Investment system (Wallet, Purse, Ender Bag)
- Kit system (12 starter kits with base items)
- Bonus tracking (First Blood, Rampage, Close Calls, etc.)
- Death handling (keep inventory, infinite lives system)
- Configuration system
- Command framework with admin commands (FORCE NEXT ROUND, SELECT KIT)
- Team Dislodge system
- Cash Quake event scheduler
- Custom consumables (Speed Carrot, Golden Chicken, Cookie of Life, Sunscreen, Spinach)
- Map grief prevention (web despawn, leaf block decay, water/lava limiting, bucket refill)
- Water/lava spread restrictions (only to break webs or extinguish fire)
- Dead player ability usage prevention (no cooldowns when dead)
- Respawn protection (no respawning during shopping phase)
- Infinite lives system (removed per-round lives limit)
- Tax Evasion Pants bonus (only triggers during combat phase)
- **Capture the Flag Gamemode**: Pressure plate captures, flag mechanics, 45-second bonuses, sudden death

### In Progress 🚧
- Shop GUI system (item lore and pricing display)
- Custom item implementations (Goblin Spear, Warden Gloves fixes)
- Legendary weapon balance (Bows: Power -50%, Strength nerf -50%)
- Custom armor effects and enchantment fixes
- Arena management
- Block regeneration
- Forfeit voting system
- Spectator mode
- Refund system for custom armor pieces
- Shield logic for kit system

### Planned 📋
- Cosmetics system (kill effects, arrow trails, armor trims)
- Rank system (VIP, MVP, Clasher)
- Statistics tracking and persistence
- Competitive mode with ELO
- Arcade game modes
- Custom resource pack support
- Multi-language support

## Known Issues & Exploits

### Bugs 🐛
- ~~Warden Gloves shouldn't replace entire sword when upgrading from iron→diamond~~ ✅ FIXED
- ~~Invisibility Cloak removes all armor when dying and persists in buy phase~~ - NEEDS VERIFICATION
- ~~Some items with multiple quantities don't display correct stack pricing~~ - NEEDS VERIFICATION
- ~~Tax Evasion Pants don't apply 3k bonus after 30 seconds~~ ✅ FIXED (now only triggers during combat phase)
- ~~Dragon Set causes invincibility after dash~~ - PARTIALLY FIXED (removed "no marked target" message)
- ~~Goblin Spear causes invincibility on charge attack~~ - NEEDS INVESTIGATION
- ~~Protection enchantment not applying across all armor throughout game~~ - PARTIALLY FIXED (Flamebridgers Speed set to 12s)
- ~~Leaf blocks can exceed stack height of 3~~ ✅ FIXED
- ~~Some players not teleported to game in buy phase~~ - NEEDS VERIFICATION

### Exploits ⚠️
- Goblin Spear unable to be thrown like trident; melee also not working
- Warden Gloves: Speed stacking instead of consistent Speed I; hit speed increase needs to be more gradual
- Golden Carrots, Mutton, Pork Chops, Golden/Enchanted Golden Apples don't go into inventory when purchased

### Minor Issues 📝
- Start of buy phase full heals hunger and health
- No hunger loss in lobby, pre-game, or buy phase
- Going into game kicks player out of shop
- Ready indicator should allow clicks with items in hand and unready on combat phase
- Randomly incorrect cooldown behaviors for dead players

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

