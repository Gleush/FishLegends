package io.github.gleush;

import org.bukkit.Bukkit;
import org.bukkit.Particle;
import org.bukkit.command.ConsoleCommandSender;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.FishHook;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.event.player.PlayerFishEvent;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;
import org.bukkit.Sound;

import java.io.File;
import java.util.*;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public final class fishlegends extends JavaPlugin implements Listener {
    FileConfiguration config;
    File fishConfigFile;
    FileConfiguration fishConfig;

    String pluginName;
    String pluginPrefix;
    Integer hookbarLinesNumberMax;

    ConsoleCommandSender consoleSender = Bukkit.getConsoleSender();
    private final Random random = new Random();
    private final Set<UUID> bitingHooks = new HashSet<>();//Prevent new fish during hook bite or plunge
    private final Set<UUID> toReelInHooks = new HashSet<>();
    private final Set<UUID> plungingHooks = new HashSet<>();
    private final Map<UUID, Integer> hookbarHooks = new HashMap<>(); /** Contain the value of the hookbar power for each hooks (thrown by a player) **/

    @Override
    public void onEnable() {
        saveDefaultConfig();

        // Principal Config
        config = getConfig();
        pluginName = getDescription().getName();
        pluginPrefix = config.getString("pluginPrefix");
        hookbarLinesNumberMax = config.getInt("hookbarLinesNumberMax");

        //Others config
        createCustomConfig();

        getServer().getPluginManager().registerEvents(this, this);
        consoleSender.sendMessage(pluginPrefix + "Plugin " + pluginName + " enabled.");
    }

    @Override
    public void onDisable() {
        consoleSender.sendMessage(pluginPrefix + "Plugin" + pluginName + " disabled.");
    }

    private void createCustomConfig() {
        fishConfigFile = new File(getDataFolder(), "fish.yml");
        if (!fishConfigFile.exists()) {
            fishConfigFile.getParentFile().mkdirs();
            saveResource("fish.yml", false);
        }

        fishConfig = new YamlConfiguration();
        YamlConfiguration.loadConfiguration(fishConfigFile);
    }

    @EventHandler
    public void onPlayerFish(PlayerFishEvent event) {
        FishHook fishHook = event.getHook();
        UUID hookUUID = fishHook.getUniqueId();
        Player player = event.getPlayer();

        if (event.getState() == PlayerFishEvent.State.BITE) {
            if (bitingHooks.contains(hookUUID)) {
                event.setCancelled(true); // Cancel event if hook already is in bite or plunge (Prevent other fish appair)
            } else {
                event.getPlayer().sendMessage("Le poisson mord à l'hameçon");
                event.setCancelled(true);
                bitingHooks.add(hookUUID);
                int times = 1 + random.nextInt(4);// Amount of hook's bites
                biteHook(fishHook, times);
            }
        }

        if (event.getState() == PlayerFishEvent.State.REEL_IN) {
            bitingHooks.remove(hookUUID);
            if (toReelInHooks.contains(hookUUID)){// If last bite of 'bite Step'
                event.setCancelled(true);
                toReelInHooks.remove(hookUUID);
                plungingHooks.add(hookUUID);
                hookbarHooks.put(hookUUID, hookbarLinesNumberMax / 2);
                int times = 8 + random.nextInt(3);// Amount of hook's plounge
                plungeHook(player, fishHook, times);
                Bukkit.broadcastMessage("Passage au plonge");
            }
            if (plungingHooks.contains(hookUUID)){// If last bite of 'bite Step'
                event.setCancelled(true);
                computeHookBar(player, hookUUID, 1);
            }
        }
    }

    private void biteHook(FishHook fishHook, int times) {
        UUID hookUUID = fishHook.getUniqueId();
        if(bitingHooks.contains(hookUUID)){
            if (times > 1) {
                new BukkitRunnable() {
                    @Override
                    public void run() {
                        // Applique une vélocité vers le bas à l'hameçon
                        fishHook.setVelocity(new Vector(0, -0.1, 0));
                        fishHook.getWorld().playSound(fishHook.getLocation(), Sound.UI_LOOM_TAKE_RESULT, 1.0f, 1.2f);
                        fishHook.getWorld().spawnParticle(Particle.WATER_SPLASH, fishHook.getLocation(), 3, 0.2, 0.2, 0.2, 0.1);

                        long delay = 15 + random.nextInt(11);
                        new BukkitRunnable() {
                            @Override
                            public void run() {
                                biteHook(fishHook, times - 1);
                            }
                        }.runTaskLater(fishlegends.this, delay);
                    }
                }.runTask(fishlegends.this);
            } else {
                fishHook.setVelocity(new Vector(0, -0.5, 0));
                fishHook.getWorld().playSound(fishHook.getLocation(), Sound.UI_LOOM_TAKE_RESULT, 1.0f, 0.5f);
                fishHook.getWorld().spawnParticle(Particle.WATER_SPLASH, fishHook.getLocation(), 3, 0.2, 0.2, 0.2, 0.1);
                toReelInHooks.add(hookUUID);
                ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);
                scheduler.schedule(() -> {
                    toReelInHooks.remove(hookUUID);
                }, 2, TimeUnit.SECONDS);// 2 sec to reel_in while last bite of fish
                scheduler.shutdown();
            }
        }
    }

    private void plungeHook(Player player, FishHook fishHook, int times) {
        UUID hookUUID = fishHook.getUniqueId();
        if(times > 0){
            computeHookBar(player, hookUUID, -8);
        }
        if(plungingHooks.contains(hookUUID)) {
            if (times > 0) {
                new BukkitRunnable() {
                    @Override
                    public void run() {
                        // Applique une vélocité vers le bas à l'hameçon
                        fishHook.setVelocity(new Vector(0, -0.3, 0));
                        fishHook.getWorld().playSound(fishHook.getLocation(), Sound.ENTITY_FISHING_BOBBER_SPLASH, 1.0f, 1.0f);
                        fishHook.getWorld().spawnParticle(Particle.WATER_SPLASH, fishHook.getLocation(), 10, 0.2, 0.2, 0.2, 0.1);

                        long delay = 15 + random.nextInt(11);
                        new BukkitRunnable() {
                            @Override
                            public void run() {
                                plungeHook(player, fishHook, times - 1);
                            }
                        }.runTaskLater(fishlegends.this, delay);
                    }
                }.runTask(fishlegends.this);
            } else {
                plungingHooks.remove(hookUUID);
                hookbarHooks.remove(hookUUID);
            }
        }
    }

    private void computeHookBar(Player player, UUID hookUUID, Integer linesToAddOrRemove){
        Integer hookbarLinesNumber = hookbarHooks.get(hookUUID);
        Integer newHookbarLinesNumber = hookbarLinesNumber += linesToAddOrRemove;
        if (newHookbarLinesNumber >= 0) {
            if (newHookbarLinesNumber >= hookbarLinesNumberMax) {
                hookbarLinesNumber = hookbarLinesNumberMax;
            } else {
                hookbarLinesNumber = newHookbarLinesNumber;
            }
        } else{
            hookbarLinesNumber = 0;
        }
        hookbarHooks.put(hookUUID, hookbarLinesNumber);

        String playerLines = "§2-".repeat(hookbarLinesNumber);
        String fishLines = "§4-".repeat(hookbarLinesNumberMax - hookbarLinesNumber);
        String hookBar = playerLines + "§2|§4|" + fishLines;
        player.sendTitle("", hookBar, 0, 50, 10);
    }
}
