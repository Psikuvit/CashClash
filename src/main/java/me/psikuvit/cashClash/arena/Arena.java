package me.psikuvit.cashClash.arena;

import me.psikuvit.cashClash.CashClashPlugin;
import org.bukkit.Bukkit;
import org.bukkit.GameRule;
import org.bukkit.World;
import org.bukkit.WorldCreator;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Comparator;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

/**
 * Represents a Cash Clash arena with world copying functionality
 * Arena now only stores name and templateId. All spawn/config data lives in TemplateWorld.
 */
public class Arena {

    private final String name;
    private String templateId;

    // Track recently-deleted worlds to avoid reattempt storms (worldName -> skipUntilEpochMs)
    private static final ConcurrentHashMap<String, Long> skipUnloadUntil = new ConcurrentHashMap<>();

    // Track worlds that have been deleted during this JVM run to prevent reattempts
    private static final Set<String> deletedWorlds = ConcurrentHashMap.newKeySet();

    // Track the active copied world for this arena and the session that created it.
    private World activeCopiedWorld = null;
    private UUID activeSessionId = null;

    // If true, the arena was loaded from a persisted arena file and should be treated as configured
    private boolean configuredFromFile = false;

    // deletion guard to avoid repeated unload attempts
    private volatile boolean deletionInProgress = false;

    public Arena(String name, String templateId) {
        this.name = name;
        this.templateId = templateId;
    }

    private World getTemplateWorldInternal() {
        TemplateWorld tpl = ArenaManager.getInstance().getTemplate(templateId);
        return tpl == null ? null : tpl.getWorld();
    }

    /**
     * Creates a copy of the template world for a game session
     * - If a copy already exists for the provided session it will be returned instead of creating a new one.
     * - If there is an active copied world that belongs to another session, it will not be reused.
     *
     * @param sessionId The unique session ID
     * @return The copied world or null on failure
     */
    public synchronized World createWorldCopy(UUID sessionId) {
        // If we already created a copy for this session, return it.
        if (activeSessionId != null && activeSessionId.equals(sessionId) && activeCopiedWorld != null) {
            if (Bukkit.getWorld(activeCopiedWorld.getName()) != null) {
                CashClashPlugin.getInstance().getLogger().info("Reusing existing copied world for arena " + name + " session " + sessionId);
                return activeCopiedWorld;
            } else {
                // World object stale, clear references and continue to recreate
                activeCopiedWorld = null;
                activeSessionId = null;
            }
        }

        World templateWorld = getTemplateWorldInternal();
        if (templateWorld == null) {
            CashClashPlugin.getInstance().getLogger().severe("Template world not set for arena " + name);
            return null;
        }

        String shortId = sessionId.toString().substring(0, 8);
        String copyWorldName = name + "_session_" + shortId;

        try {
            File serverWorldContainer = Bukkit.getWorldContainer();
            File templateWorldFolder = templateWorld.getWorldFolder();
            File copyWorldFolder = new File(serverWorldContainer, copyWorldName);

            // Try to reuse any already loaded world that matches our arena prefix
            for (World w : Bukkit.getWorlds()) {
                if (w.getName().startsWith(name + "_session_")) {
                    // If found reuse it for this session (helps across reloads where names match)
                    CashClashPlugin.getInstance().getLogger().info("Found already loaded copy for arena " + name + ": " + w.getName());
                    activeCopiedWorld = w;
                    activeSessionId = sessionId;
                    return activeCopiedWorld;
                }
            }

            // Cleanup stale copied folders for this arena (best-effort). This prevents accumulation of many copies
            File[] children = serverWorldContainer.listFiles();
            if (children != null) {
                for (File child : children) {
                    if (!child.isDirectory()) continue;
                    String fname = child.getName();
                    if (fname.startsWith(name + "_session_")) {
                        if (fname.equals(copyWorldName)) continue;

                        // If a world with this folder name is loaded -> try unloading it first
                        World loaded = Bukkit.getWorld(fname);
                        if (loaded != null) {
                            try {
                                CashClashPlugin.getInstance().getLogger().info("Unloading stale loaded world before pruning: " + fname);
                                Bukkit.unloadWorld(loaded, false);
                            } catch (Exception t) {
                                CashClashPlugin.getInstance().getLogger().warning("Failed to unload stale world " + fname + ": " + t.getMessage());
                                continue;
                            }
                        }

                        try {
                            deleteWorld(child);
                            CashClashPlugin.getInstance().getLogger().info("Pruned stale arena world folder: " + child.getAbsolutePath());
                        } catch (Exception t) {
                            CashClashPlugin.getInstance().getLogger().warning("Failed to prune stale folder " + child.getAbsolutePath() + ": " + t.getMessage());
                        }
                    }
                }
            }

            if (copyWorldFolder.exists()) {
                World maybeLoaded = Bukkit.getWorld(copyWorldName);
                if (maybeLoaded != null) {
                    CashClashPlugin.getInstance().getLogger().info("World copy already loaded: " + copyWorldName + " - reusing it.");
                    activeCopiedWorld = maybeLoaded;
                    activeSessionId = sessionId;
                    return activeCopiedWorld;
                }

                // Attempt a best-effort delete of the old folder before copying new
                deleteWorld(copyWorldFolder);
                if (copyWorldFolder.exists()) {
                    CashClashPlugin.getInstance().getLogger().warning("Existing world folder couldn't be deleted: " + copyWorldFolder.getAbsolutePath());
                }
            }

            // Copy template folder to target
            copyWorldFolder(templateWorldFolder.toPath(), copyWorldFolder.toPath());

            // Remove uid/session files if present
            File uidFile = new File(copyWorldFolder, "uid.dat");
            try {
                if (uidFile.exists()) Files.deleteIfExists(uidFile.toPath());
            } catch (IOException ignored) {}
            File sessionLock = new File(copyWorldFolder, "session.lock");
            try {
                if (sessionLock.exists()) Files.deleteIfExists(sessionLock.toPath());
            } catch (IOException ignored) {}

            WorldCreator creator = new WorldCreator(copyWorldName);
            creator.environment(templateWorld.getEnvironment());
            creator.generator(templateWorld.getGenerator());
            creator.seed(templateWorld.getSeed());

            // Create and load the world (this will register it with the server)
            World copiedWorld = creator.createWorld();

            if (copiedWorld != null) {
                copiedWorld.setAutoSave(false);
                copiedWorld.setDifficulty(templateWorld.getDifficulty());
                copiedWorld.setSpawnFlags(false, false); // No monsters or animals
                copiedWorld.setGameRule(GameRule.DO_DAYLIGHT_CYCLE, false);
                copiedWorld.setGameRule(GameRule.DO_WEATHER_CYCLE, false);
                copiedWorld.setGameRule(GameRule.DO_MOB_SPAWNING, false);
                copiedWorld.setGameRule(GameRule.ANNOUNCE_ADVANCEMENTS, false);
                copiedWorld.setGameRule(GameRule.KEEP_INVENTORY, true);

                CashClashPlugin.getInstance().getLogger().info("Created world copy: " + copyWorldName + " for arena " + name);

                // Track active copy for lifecycle management
                activeCopiedWorld = copiedWorld;
                activeSessionId = sessionId;
                return copiedWorld;
            } else {
                CashClashPlugin.getInstance().getLogger().severe("WorldCreator returned null when creating " + copyWorldName);
            }

        } catch (IOException e) {
            CashClashPlugin.getInstance().getLogger().severe("Failed to copy world for arena " + name + ": " + e.getMessage());
        } catch (Exception t) {
            CashClashPlugin.getInstance().getLogger().severe("Unexpected error while creating world copy for arena " + name + ": " + t.getMessage());
        }

        return null;
    }

    /**
     * Deletes a world copy after a game session ends
     * @param world The world to delete
     */
    public synchronized void deleteWorldCopy(World world) {
        if (world == null) return;

        String worldName = world.getName();

        // If we recently attempted deletion for this world, skip to avoid repeated unload attempts
        Long skipUntil = skipUnloadUntil.get(worldName);
        long now = System.currentTimeMillis();
        if (skipUntil != null && now < skipUntil) {
            CashClashPlugin.getInstance().getLogger().info("Skipping delete for world " + worldName + " because a recent attempt is in progress.");
            return;
        }

        // mark cooldown to avoid another simultaneous/quick retry for 60s
        skipUnloadUntil.put(worldName, now + TimeUnit.SECONDS.toMillis(60));

        // If this world equals the template world, ignore
        if (world.equals(getTemplateWorldInternal())) {
            skipUnloadUntil.remove(worldName);
            return;
        }

        // If the world was already fully deleted earlier in this runtime, skip
        if (deletedWorlds.contains(worldName)) {
            CashClashPlugin.getInstance().getLogger().info("World " + worldName + " already deleted earlier in this runtime - skipping.");
            skipUnloadUntil.remove(worldName);
            return;
        }

        // If another deletion is already in progress for this arena, skip
        if (deletionInProgress) {
            CashClashPlugin.getInstance().getLogger().info("Deletion already in progress for arena " + name + " - skipping duplicate delete for " + worldName);
            return;
        }

        deletionInProgress = true;

        // Mark as deleted now to avoid races; removal will be idempotent if deletion fails
        deletedWorlds.add(worldName);

        try {
            boolean isActive = activeCopiedWorld != null && worldName.equals(activeCopiedWorld.getName());
            File worldFolder = world.getWorldFolder();

            // If the world is already unloaded, just delete the folder (async if possible) and clear refs
            if (Bukkit.getWorld(worldName) == null) {
                CashClashPlugin.getInstance().getLogger().info("World " + worldName + " is not loaded - performing folder deletion.");
                if (CashClashPlugin.getInstance().isEnabled()) {
                    Bukkit.getScheduler().runTaskAsynchronously(CashClashPlugin.getInstance(), () -> {
                        deleteWorld(worldFolder);
                        CashClashPlugin.getInstance().getLogger().info("Deleted world folder: " + worldFolder.getAbsolutePath());
                        skipUnloadUntil.remove(worldName);
                        // world already marked deleted in deletedWorlds set
                    });
                } else {
                    // Server shutting down - delete synchronously
                    deleteWorld(worldFolder);
                    CashClashPlugin.getInstance().getLogger().info("Deleted world folder (shutdown path): " + worldFolder.getAbsolutePath());
                    skipUnloadUntil.remove(worldName);
                }

                if (isActive) {
                    activeCopiedWorld = null;
                    activeSessionId = null;
                }

                deletionInProgress = false;
                return;
            }

            // If plugin is still enabled we can schedule async deletion tasks safely.
            if (CashClashPlugin.getInstance().isEnabled()) {
                // Clear tracked refs early to avoid duplicate attempts from other threads
                if (isActive) {
                    activeCopiedWorld = null;
                    activeSessionId = null;
                }

                // Teleport players out and unload world on main thread
                Bukkit.getScheduler().runTask(CashClashPlugin.getInstance(), () -> {
                    try {
                        Bukkit.getWorlds().stream().findFirst().ifPresent(fallback -> {
                            try {
                                world.getPlayers().forEach(player -> player.teleport(fallback.getSpawnLocation()));
                            } catch (Exception ignored) {
                            }
                        });

                        boolean unloaded = Bukkit.unloadWorld(world, false);
                        if (!unloaded) {
                            CashClashPlugin.getInstance().getLogger().warning("Failed to unload world: " + worldName + ", will still attempt to delete folder.");
                        }

                        // Async delete files to avoid blocking server thread
                        Bukkit.getScheduler().runTaskAsynchronously(CashClashPlugin.getInstance(), () -> {
                            try {
                                deleteWorld(worldFolder);
                                CashClashPlugin.getInstance().getLogger().info("Deleted world copy folder: " + worldFolder.getAbsolutePath());
                            } catch (Exception t) {
                                CashClashPlugin.getInstance().getLogger().warning("Error deleting world folder " + worldFolder.getAbsolutePath() + ": " + t.getMessage());
                            } finally {
                                // ensure we clear the deletion flag on the main thread and clear skip entry
                                Bukkit.getScheduler().runTask(CashClashPlugin.getInstance(), () -> {
                                    deletionInProgress = false;
                                    skipUnloadUntil.remove(worldName);
                                });
                            }
                        });

                    } catch (Exception t) {
                        CashClashPlugin.getInstance().getLogger().warning("Error while unloading/deleting world " + worldName + ": " + t.getMessage());
                        deletionInProgress = false;
                        skipUnloadUntil.remove(worldName);
                        // if the deletion failed we should remove the deletedWorlds marker so retries are possible
                        deletedWorlds.remove(worldName);
                    }
                });

                return;
            }

            // Plugin is disabling or disabled: perform synchronous cleanup to avoid scheduling tasks
            Bukkit.getWorlds().stream().findFirst().ifPresent(fallback -> world.getPlayers().forEach(player -> player.teleport(fallback.getSpawnLocation())));

            boolean unloaded = Bukkit.unloadWorld(world, false);
            if (!unloaded) {
                CashClashPlugin.getInstance().getLogger().warning("Failed to unload world during shutdown: " + worldName);
            }


            try {
                // synchronous delete (may block but we're shutting down)
                deleteWorld(worldFolder);
                CashClashPlugin.getInstance().getLogger().info("Deleted world copy folder (shutdown path): " + worldFolder.getAbsolutePath());
            } catch (Exception t) {
                CashClashPlugin.getInstance().getLogger().warning("Error deleting world folder during shutdown " + worldFolder.getAbsolutePath() + ": " + t.getMessage());
            }

            // During shutdown skip reloading the template world; simply clear tracked refs
            if (isActive) {
                activeCopiedWorld = null;
                activeSessionId = null;
            }

        } finally {
            deletionInProgress = false;
            skipUnloadUntil.remove(worldName);
            // if synchronous path completed, the world is considered deleted and stays in deletedWorlds; if not, we already removed above
        }
    }

    /**
     * Copies a world folder recursively
     */
    private void copyWorldFolder(Path source, Path target) throws IOException {
        try (Stream<Path> stream = Files.walk(source)) {
            stream.forEach(sourcePath -> {
                try {
                    Path targetPath = target.resolve(source.relativize(sourcePath));

                    // Skip session.lock and uid.dat
                    String fileName = sourcePath.getFileName().toString();
                    if (fileName.equals("session.lock") || fileName.equals("uid.dat")) {
                        return;
                    }

                    if (Files.isDirectory(sourcePath)) {
                        Files.createDirectories(targetPath);
                    } else {
                        Files.copy(sourcePath, targetPath, StandardCopyOption.REPLACE_EXISTING);
                    }
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            });
        }
    }

    /**
     * Recursively deletes a world folder
     */
    private void deleteWorld(File worldFolder) {
        if (!worldFolder.exists()) {
            return;
        }

        try (Stream<Path> walk = Files.walk(worldFolder.toPath())) {
            walk.sorted(Comparator.reverseOrder())
                .map(Path::toFile)
                .forEach(file -> {
                    try {
                        if (!file.delete()) {
                            // On Windows, deletion can fail due to locking; try deleteOnExit as a fallback
                            file.deleteOnExit();
                        }
                    } catch (Exception ignored) {}
                });
        } catch (IOException e) {
            CashClashPlugin.getInstance().getLogger().warning("Error deleting world folder: " + e.getMessage());
        }
    }

    public boolean isReady() {
        // If this arena was loaded from a persisted arena file, consider it configured
        if (configuredFromFile) return true;

        TemplateWorld tpl = ArenaManager.getInstance().getTemplate(templateId);
        return tpl != null && tpl.isConfigured();
    }

    public void setConfiguredFromFile(boolean value) {
        this.configuredFromFile = value;
    }

    // Getters and setters
    public String getName() {
        return name;
    }

    public String getTemplateId() {
        return templateId;
    }

    public void setTemplateId(String templateId) {
        this.templateId = templateId;
    }
}
