package vip.creatio.reloader;

import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.PluginCommand;
import org.bukkit.command.SimpleCommandMap;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.event.HandlerList;
import org.bukkit.plugin.InvalidPluginException;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.SimplePluginManager;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.lang.reflect.Field;
import java.net.URISyntaxException;
import java.util.*;

public class Main extends JavaPlugin {

    private static final String prefix = "§7§l[§6§lP§c§lR§7§l] §7";

    private static final Field plugins;
    private static final Field lookupNames;
    private static final Map<String, Command> knownCommands;

    private static final SimplePluginManager mgr = (SimplePluginManager) Bukkit.getPluginManager();

    private static final List<String> TC_MAIN = Arrays.asList("load", "unload", "reload", "query");
    private static final List<String> TC_EMPTY = Collections.emptyList();

    private static YamlConfiguration config;

    private static File plugins_folder;

    static {
        try {

            plugins = SimplePluginManager.class.getDeclaredField("plugins");
            plugins.setAccessible(true);
            lookupNames = SimplePluginManager.class.getDeclaredField("lookupNames");
            lookupNames.setAccessible(true);

            Class<?> craftServer = Bukkit.getServer().getClass();

            Field map = craftServer.getDeclaredField("commandMap");
            map.setAccessible(true);

            Field cmd = SimpleCommandMap.class.getDeclaredField("knownCommands");
            cmd.setAccessible(true);

            knownCommands = (Map<String, Command>) cmd.get(map.get(Bukkit.getServer()));
        } catch (NoSuchFieldException | IllegalAccessException e) {
            throw new RuntimeException(e);
        }
    }

    private static Main instance;

    public Main() {
        if (instance != null) throw new RuntimeException("plugin already initialized!");
        instance = this;

        plugins_folder = getDataFolder().getParentFile();

        this.saveDefaultConfig();

        config = (YamlConfiguration) this.getConfig();
    }

    @Override
    public void onEnable() {
        Bukkit.getPluginCommand("pr").setExecutor(((sender, command, label, args) -> {
            if (!sender.isOp()) {
                sender.sendMessage(prefix + config.getString("language.no_perm"));
                return true;
            }

            int l = args.length - 1;

            if (l >= 1) {
                if (args[0].equalsIgnoreCase(TC_MAIN.get(1))) {
                    String name = connect(1, args);
                    Plugin p = mgr.getPlugin(name);

                    if (p != null) {

                        if (p == this) {
                            sender.sendMessage(prefix + config.getString("language.unload_self"));
                            return true;
                        }

                        sender.sendMessage(prefix + config.getString("language.unloading") + p.getName());
                        unload(sender, name, p);
                    } else sender.sendMessage(prefix + config.getString("language.plugin_not_found") + name);
                    return true;
                }

                if (args[0].equalsIgnoreCase(TC_MAIN.get(0))) {
                    String name = connect(1, args);
                    File f = new File(instance.getDataFolder().getParentFile(), name);
                    for (Plugin pl : mgr.getPlugins()) {
                        try {
                            if (f.toURI().equals(pl.getClass().getProtectionDomain().getCodeSource().getLocation().toURI())) {
                                sender.sendMessage(prefix + config.getString("language.already_loaded").replaceAll("%0%", pl.getName()));
                                return true;
                            }
                        } catch (URISyntaxException e) {
                            e.printStackTrace();
                        }
                    }

                    if (f.exists()) {
                        sender.sendMessage(prefix + config.getString("language.loading") + f.getName());
                        load(sender, f);
                    } else sender.sendMessage(prefix + config.getString("language.file_not_found") + name);
                    return true;
                }

                if (args[0].equalsIgnoreCase(TC_MAIN.get(2))) {
                    String name = connect(1, args);
                    Plugin p = mgr.getPlugin(name);

                    if (p != null) {

                        if (p == this) {
                            sender.sendMessage(prefix + config.getString("language.unload_self"));
                            return true;
                        }

                        sender.sendMessage(prefix + config.getString("language.reloading") + p.getName());
                        File f;
                        try {
                            f = new File(p.getClass().getProtectionDomain().getCodeSource().getLocation().toURI());
                        } catch (URISyntaxException e) {
                            sender.sendMessage(prefix + config.getString("language.reload_failed") + name);
                            e.printStackTrace();
                            return true;
                        }
                        unload(sender, name, p);
                        load(sender, f);
                    } else sender.sendMessage(prefix + config.getString("plugin_not_found") + name);
                    return true;
                }

            }

            if (l == 0 && args[0].equalsIgnoreCase(TC_MAIN.get(3))) {
                sender.sendMessage(prefix + config.getString("language.listing_plugins"));
                for (Plugin p : mgr.getPlugins()) {
                    if (p instanceof JavaPlugin) sender.sendMessage("   §3" + p.toString());
                    else sender.sendMessage("   §9" + p.getName());
                }
                return true;
            }

            sender.sendMessage(prefix + "Usage: /pr <load/unload/reload/query> [<...>]");
            return true;
        }));

        Bukkit.getPluginCommand("pr").setTabCompleter((sender, command, label, args) -> {
            int l = args.length - 1;
            if (l >= 1) {
                if (args[0].equalsIgnoreCase(TC_MAIN.get(0))) {
                    File[] files = plugins_folder.listFiles((f, n) -> !f.isDirectory() && n.endsWith(".jar"));
                    List<String> names = new ArrayList<>();
                    for (File f : files) {
                        boolean flag = true;
                        for (Plugin p : mgr.getPlugins()) {
                            try {
                                if (f.toURI().equals(p.getClass().getProtectionDomain().getCodeSource().getLocation().toURI())) {
                                    flag = false;
                                    break;
                                }
                            } catch (URISyntaxException ignored) {}
                        }
                        if (flag) names.add(f.getName());
                    }
                    return names;
                }

                if (args[0].equalsIgnoreCase(TC_MAIN.get(1)) || args[0].equalsIgnoreCase(TC_MAIN.get(2))) {
                    List<String> names = new ArrayList<>();
                    Arrays.stream(mgr.getPlugins()).map(Plugin::getName).forEach(names::add);
                    return names;
                }
                return TC_EMPTY;
            }
            return TC_MAIN;
        });
    }

    private static String connect(int start, String... args) {
        StringBuilder sb = new StringBuilder();
        for (int i = start; i < args.length; i++) {
            sb.append(args[i]).append(' ');
        }
        return sb.toString().trim();
    }

    private void unload(CommandSender sender, String name, Plugin p) {
        try {

            //disable plugins
            mgr.disablePlugin(p);

            //delete all commands
            List<String> list = new ArrayList<>();
            for (Map.Entry<String, Command> entry : knownCommands.entrySet()) {
                if (entry.getValue() instanceof PluginCommand
                        && ((PluginCommand) entry.getValue()).getPlugin() == p) list.add(entry.getKey());
            }
            list.stream().forEach(knownCommands::remove);

            //remove from list
            ((List<Plugin>) plugins.get(mgr)).remove(p);
            ((Map<String, Plugin>) lookupNames.get(mgr)).remove(p.getName().replace(' ', '_'));

            //remove all handlers
            HandlerList.unregisterAll(p);

            System.gc();
            sender.sendMessage(prefix + config.getString("language.unload_success").replaceAll("%0%", name));
        } catch (Exception e) {
            sender.sendMessage(prefix + config.getString("language.unload_failed") + p.getName());
            getLogger().warning("Failed to unload " + p.getName() + ", stacktrace:");
            e.printStackTrace();
        }
    }

    private void load(CommandSender sender, File f) {
        try {
            Plugin c = mgr.loadPlugin(f);
            c.onLoad();
            mgr.enablePlugin(c);
            sender.sendMessage(prefix + config.getString("language.load_success").replaceAll("%0%", c.getName()));
        } catch (InvalidPluginException e) {
            sender.sendMessage(prefix + config.getString("language.invalid_plugin"));
            getLogger().warning("Failed to load plugin " + f.getName() + ", stacktrace:");
            e.printStackTrace();
        }
    }
}
