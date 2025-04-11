package femboys.are.cute;

import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.logging.Level;

public class EntityManager extends JavaPlugin implements Listener {

    private Set<EntityType> whitelist = new HashSet<>();
    private Set<EntityType> blacklist = new HashSet<>();
    private boolean useWhitelist;
    private int cleanupInterval;
    private List<String> enabledWorlds;

    @Override
    public void onEnable() {
        loadConfigs();

        getServer().getPluginManager().registerEvents(this, this);
        getCommand("entitymanager").setExecutor(this);

        performCleanup();

        if (cleanupInterval > 0) {
            Bukkit.getScheduler().scheduleSyncRepeatingTask(
                    this,
                    this::performCleanup,
                    cleanupInterval * 20L,
                    cleanupInterval * 20L
            );
        }

        getLogger().info("EntityManager has been enabled!");
        getLogger().info("Active in worlds: " + (enabledWorlds.isEmpty() ? "ALL" : enabledWorlds.toString()));
    }

    private void loadConfigs() {
        saveDefaultConfig();
        reloadConfig();

        saveResource("whitelist.yml", false);
        saveResource("blacklist.yml", false);

        FileConfiguration config = getConfig();
        useWhitelist = config.getBoolean("use-whitelist", true);
        cleanupInterval = config.getInt("cleanup-interval", 300);
        enabledWorlds = config.getStringList("per-world");

        loadEntityList(
                YamlConfiguration.loadConfiguration(new File(getDataFolder(), "whitelist.yml")),
                whitelist,
                "whitelist"
        );
        loadEntityList(
                YamlConfiguration.loadConfiguration(new File(getDataFolder(), "blacklist.yml")),
                blacklist,
                "blacklist"
        );
    }

    private void loadEntityList(FileConfiguration config, Set<EntityType> target, String listName) {
        List<String> mobNames = config.getStringList("entities");
        for (String name : mobNames) {
            try {
                EntityType type = EntityType.valueOf(name.toUpperCase());
                target.add(type);
                getLogger().info("Added " + type + " to " + listName);
            } catch (IllegalArgumentException e) {
                getLogger().warning("Invalid entity type in " + listName + ": " + name);
            }
        }
    }

    private void performCleanup() {
        int removed = 0;
        for (World world : Bukkit.getWorlds()) {
            if (!isWorldEnabled(world)) continue;

            for (Entity entity : world.getEntities()) {
                if (entity instanceof Player || !(entity instanceof LivingEntity)) {
                    continue;
                }

                if (shouldRemove(entity.getType())) {
                    entity.remove();
                    removed++;
                }
            }
        }
        if (removed > 0) {
            getLogger().info("Cleaned up " + removed + " entities");
        }
    }

    private boolean isWorldEnabled(World world) {
        return enabledWorlds.isEmpty() || enabledWorlds.contains(world.getName());
    }

    private boolean shouldRemove(EntityType type) {
        if (useWhitelist) {
            return !whitelist.contains(type);
        } else {
            return blacklist.contains(type);
        }
    }

    @EventHandler
    public void onEntitySpawn(CreatureSpawnEvent event) {
        if (!isWorldEnabled(event.getLocation().getWorld())) {
            return;
        }

        EntityType type = event.getEntityType();
        boolean cancel = shouldRemove(type);

        if (getConfig().getBoolean("debug", false)) {
            getLogger().info("Entity spawn in " + event.getLocation().getWorld().getName() +
                    ": " + type + " - " + (cancel ? "CANCELLED" : "ALLOWED"));
        }

        event.setCancelled(cancel);
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (args.length > 0 && args[0].equalsIgnoreCase("reload")) {
            if (!sender.hasPermission("entitymanager.reload")) {
                sender.sendMessage("§cYou don't have permission to do that!");
                return true;
            }
            loadConfigs();
            sender.sendMessage("§aEntityManager configuration reloaded!");
            sender.sendMessage("§7Active in worlds: " + (enabledWorlds.isEmpty() ? "ALL" : enabledWorlds.toString()));
            return true;
        }

        sender.sendMessage("§6EntityManager §ev" + getDescription().getVersion());
        sender.sendMessage("§7Current mode: " + (useWhitelist ? "§aWhitelist" : "§cBlacklist"));
        sender.sendMessage("§7Active worlds: " + (enabledWorlds.isEmpty() ? "§aALL" : "§e" + enabledWorlds.toString()));
        sender.sendMessage("§7Cleanup interval: §f" + cleanupInterval + " seconds");
        sender.sendMessage("§7Usage: /entitymanager reload");
        return true;
    }
}