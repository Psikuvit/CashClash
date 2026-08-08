package me.psikuvit.cashClash.chat;

/**
 * The available chat channels. A sealed interface of singleton records rather than an enum, but
 * behaves identically at call sites: {@code ChatChannel.GLOBAL} etc. are the shared instances to
 * compare/store, and a {@code switch} over a ChatChannel is exhaustively checked by the compiler
 * against the {@code permits} list, same as an enum switch would be.
 */
public sealed interface ChatChannel {

    ChatChannel GLOBAL = new Global();
    ChatChannel PARTY = new Party();
    ChatChannel TEAM = new Team();
    ChatChannel GAME = new Game();

    String getDisplayName();

    String getNameColor();

    String getPrefix();

    /** Global chat - visible to all players. */
    record Global() implements ChatChannel {
        public String getDisplayName() { return "Global"; }
        public String getNameColor() { return "<white>"; }
        public String getPrefix() { return ""; }
    }

    /** Party chat - visible only to party members. */
    record Party() implements ChatChannel {
        public String getDisplayName() { return "Party"; }
        public String getNameColor() { return "<aqua>"; }
        public String getPrefix() { return "<dark_aqua>[Party] </dark_aqua>"; }
    }

    /** Team chat - visible only to team members during a game. */
    record Team() implements ChatChannel {
        public String getDisplayName() { return "Team"; }
        public String getNameColor() { return "<green>"; }
        public String getPrefix() { return "<dark_green>[Team] </dark_green>"; }
    }

    /** Game chat - visible to all players in the same game. */
    record Game() implements ChatChannel {
        public String getDisplayName() { return "Game"; }
        public String getNameColor() { return "<yellow>"; }
        public String getPrefix() { return "<gold>[Game] </gold>"; }
    }
}
