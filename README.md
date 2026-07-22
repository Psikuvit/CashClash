# Cash Clash

Cash Clash is a highly configurable Minecraft team-based combat plugin centered around a dynamic economy. Players compete in rounds to earn coins, buy equipment, and complete objectives across various gamemodes.

## 🎮 Gamemodes

Cash Clash features three unique gamemodes, each with its own mechanics and "Sudden Death" variations:

### 1. Capture The Flag (CTF)
*   **Objective:** Capture the enemy team's flag and return it to your base.
*   **Mechanics:**
    *   Flag pickup requires standing near the enemy flag for a short duration.
    *   Flag carriers are highlighted with a glow effect.
    *   Dropped flags can be returned by teammates or captured by enemies after a delay.
*   **Sudden Death:** If time runs out, the game enters a high-stakes sudden death where flag captures are even more critical.

### 2. Kill Confirm (KC)
*   **Objective:** Eliminate enemies and "confirm" the kill by capturing the zone dropped at their death location.
*   **Mechanics:**
    *   When a player dies, a capture zone appears.
    *   Teammates of the killer must stand in the zone to "Confirm" the kill for points.
    *   Teammates of the victim can stand in the zone to "Deny" the kill, preventing the enemy from scoring.
*   **Sudden Death:** Zones expire faster and points become more valuable.

### 3. Protect The President (PTP)
*   **Objective:** Each team has a designated "President". Eliminate the enemy President while keeping yours alive.
*   **Mechanics:**
    *   Presidents are selected at the start of each round.
    *   Presidents receive special buffs (selected via a GUI) and extra health.
    *   Assassinating the enemy President grants massive bonuses.
*   **Sudden Death:** Presidents are permanently revealed, and the play area may shrink.

---

## 🚀 Setup & Installation

1.  **Requirements:**
    *   Minecraft Server running Paper/Spigot 1.20.1+ (or compatible version).
    *   Java 17 or higher.
2.  **Installation:**
    *   Download the `CashClash.jar` and place it in your server's `plugins` folder.
    *   Restart your server to generate the default configuration files.
3.  **Initial Setup:**
    *   Set the global lobby: `/cc setlobby`
    *   Create or assign arenas using `/cc arenas` or `/cc arena assign`.

---

## ⚙️ Configuration

The plugin uses several YAML files for deep customization:

*   **`config.yml`**: Core game settings including player counts (min 8 by default), round durations, economy rewards, and "Cash Quake" random events.
*   **`items.yml`**: Define item statistics, custom names, and lore for all gear in the game.
*   **`shop.yml`**: Configure the in-game shop categories, items, and pricing.
*   **`messages.yml`**: Customize all player-facing messages, including MiniMessage formatting support.
*   **`sequences.yml`**: Define automated game sequences and event timings.

---

## 📜 Commands & Permissions

### Player Commands
*   `/cc join` - Quick join a match.
*   `/cc leave` - Leave the current game.
*   `/cc shop` - Open the in-game shop.
*   `/cc stats` - View your player statistics.
*   `/cc forfeit` - Vote to forfeit the current round.
*   `/cc layout` - Customize your kit item layout.
*   `/party <create|invite|join|leave|info|chat>` - Manage your player party.

### Admin Commands
*   `/cc reload [all|config|shop|items]` - Reload plugin configurations.
*   `/cc forcestart` - Start a match immediately.
*   `/cc stop` - Stop the current game session.
*   `/cc setlobby` - Set the main lobby spawn.
*   `/cc arena <tp|assign>` - Manage arena assignments.
*   `/cc debug` - Toggle debug logging.

**Permission:** `cashclash.admin` is required for all admin subcommands.

---

## 📊 Scoreboard Placeholders

Cash Clash provides a wide array of placeholders for use in scoreboards (or other compatible plugins):

*   `{time}`: Remaining phase time (MM:SS).
*   `{phase}`: Current game state (Shopping, Combat, etc.).
*   `{round}`: Current round number.
*   `{player_coins}`: Your current coin balance.
*   `{teamRed_coins}` / `{teamBlue_coins}`: Total team wealth.
*   `{your_team_alive}` / `{enemy_team_alive}`: Player count.
*   `{teamRed_captures}` / `{teamBlue_captures}`: CTF Capture counts.

*(For a full list, see `SCOREBOARD_PLACEHOLDERS.txt` in the plugin folder)*
