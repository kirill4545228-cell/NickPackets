package me.nickpackets;

import org.bukkit.command.PluginCommand;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.plugin.java.JavaPlugin;

public class NickPacketsPlugin extends JavaPlugin implements Listener {

    private NickPacketManager packetManager;

    @Override
    public void onEnable() {
        packetManager = new NickPacketManager(this);
        packetManager.load();

        NickCommand command = new NickCommand(packetManager);
        PluginCommand cmd = getCommand("nick");
        if (cmd != null) {
            cmd.setExecutor(command);
            cmd.setTabCompleter(command);
        }

        // Регистрируем слушатель пакетов
        getServer().getPluginManager().registerEvents(this, this);
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        // При входе применяем сохранённый ник через 2 тика (когда профиль готов)
        getServer().getScheduler().runTaskLater(this, () -> {
            if (player.isOnline() && packetManager.hasNick(player.getUniqueId())) {
                packetManager.applyStored(player);
            }
        }, 2L);
    }

    public NickPacketManager getPacketManager() {
        return packetManager;
    }
}
