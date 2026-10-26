package me.nickpackets;

import com.comphenix.protocol.PacketType;
import com.comphenix.protocol.ProtocolLibrary;
import com.comphenix.protocol.ProtocolManager;
import com.comphenix.protocol.events.ListenerPriority;
import com.comphenix.protocol.events.PacketAdapter;
import com.comphenix.protocol.events.PacketContainer;
import com.comphenix.protocol.events.PacketEvent;
import com.comphenix.protocol.wrappers.EnumWrappers;
import com.comphenix.protocol.wrappers.PlayerInfoData;
import com.comphenix.protocol.wrappers.WrappedChatComponent;
import com.comphenix.protocol.wrappers.WrappedGameProfile;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class NickPacketManager {

    private final JavaPlugin plugin;
    private final File file;
    private final ProtocolManager protocolManager;

    private final Map<UUID, String> originals = new ConcurrentHashMap<>();
    private final Map<UUID, String> nicks = new ConcurrentHashMap<>();

    public NickPacketManager(JavaPlugin plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "nicks.yml");
        this.protocolManager = ProtocolLibrary.getProtocolManager();
    }

    // ---------- Загрузка / сохранение ----------

    public void load() {
        if (file.exists()) {
            YamlConfiguration yml = YamlConfiguration.loadConfiguration(file);
            ConfigurationSection sec = yml.getConfigurationSection("players");
            if (sec != null) {
                for (String key : sec.getKeys(false)) {
                    try {
                        UUID id = UUID.fromString(key);
                        String original = sec.getString(key + ".original");
                        String nick = sec.getString(key + ".nick");
                        if (original != null && nick != null) {
                            originals.put(id, original);
                            nicks.put(id, nick);
                        }
                    } catch (IllegalArgumentException ignored) {}
                }
            }
        }
        // Регистрируем слушатель пакетов
        registerPacketListener();
    }

    public void save() {
        YamlConfiguration yml = new YamlConfiguration();
        for (UUID id : nicks.keySet()) {
            yml.set("players." + id + ".original", originals.get(id));
            yml.set("players." + id + ".nick", nicks.get(id));
        }
        try {
            plugin.getDataFolder().mkdirs();
            yml.save(file);
        } catch (IOException e) {
            plugin.getLogger().warning("Не удалось сохранить nicks.yml: " + e.getMessage());
        }
    }

    // ---------- Слушатель пакетов ----------

    private void registerPacketListener() {
        protocolManager.addPacketListener(new PacketAdapter(
                plugin,
                ListenerPriority.NORMAL,
                PacketType.Play.Server.PLAYER_INFO
        ) {
            @Override
            public void onPacketSending(PacketEvent event) {
                // Нас интересуют только пакеты с действием ADD_PLAYER
                // (при входе игрока или принудительном обновлении)
                EnumWrappers.PlayerInfoAction action = event.getPacket()
                        .getPlayerInfoActions().read(0);
                if (action != EnumWrappers.PlayerInfoAction.ADD_PLAYER) return;

                List<PlayerInfoData> newList = new ArrayList<>();
                List<PlayerInfoData> dataList = event.getPacket()
                        .getPlayerInfoDataLists().read(0);

                for (PlayerInfoData data : dataList) {
                    if (data == null || data.getProfile() == null) {
                        newList.add(data);
                        continue;
                    }
                    UUID id = data.getProfile().getUUID();
                    String customNick = nicks.get(id);
                    if (customNick == null) {
                        newList.add(data);
                        continue;
                    }

                    // Создаём новый профиль с тем же UUID и свойствами (скин),
                    // но с новым именем (с поддержкой цветовых кодов '&')
                    WrappedGameProfile profile = data.getProfile();
                    WrappedGameProfile newProfile = new WrappedGameProfile(
                            profile.getUUID(),
                            ChatColor.translateAlternateColorCodes('&', customNick)
                    );
                    newProfile.getProperties().putAll(profile.getProperties());

                    PlayerInfoData newData = new PlayerInfoData(
                            newProfile,
                            data.getLatency(),
                            data.getGameMode(),
                            WrappedChatComponent.fromText(customNick) // отображаемое имя в табе
                    );
                    newList.add(newData);
                }
                event.getPacket().getPlayerInfoDataLists().write(0, newList);
            }
        });
    }

    // ---------- Публичные методы ----------

    public boolean hasNick(UUID id) {
        return nicks.containsKey(id);
    }

    public String getOriginal(UUID id) {
        return originals.get(id);
    }

    public Player findOnline(String name) {
        for (Player p : Bukkit.getOnlinePlayers()) {
            if (p.getName().equalsIgnoreCase(name)
                    || name.equalsIgnoreCase(originals.get(p.getUniqueId()))
                    || name.equalsIgnoreCase(nicks.get(p.getUniqueId()))) {
                return p;
            }
        }
        return null;
    }

    public boolean isNameTaken(String name, UUID except) {
        for (Player p : Bukkit.getOnlinePlayers()) {
            if (!p.getUniqueId().equals(except) && p.getName().equalsIgnoreCase(name)) return true;
        }
        for (Map.Entry<UUID, String> e : nicks.entrySet()) {
            if (!e.getKey().equals(except) && e.getValue().equalsIgnoreCase(name)) return true;
        }
        for (Map.Entry<UUID, String> e : originals.entrySet()) {
            if (!e.getKey().equals(except) && e.getValue().equalsIgnoreCase(name)) return true;
        }
        return false;
    }

    public void setNick(Player player, String newName) {
        UUID id = player.getUniqueId();
        originals.putIfAbsent(id, player.getName());
        nicks.put(id, newName);
        save();
        refreshPlayer(player);
    }

    public boolean resetNick(Player player) {
        UUID id = player.getUniqueId();
        String original = originals.remove(id);
        nicks.remove(id);
        if (original == null) return false;
        save();
        refreshPlayer(player);
        return true;
    }

    public void applyStored(Player player) {
        if (nicks.containsKey(player.getUniqueId())) {
            refreshPlayer(player);
        }
    }

    /**
     * Принудительно обновляет пакеты информации об игроке для всех онлайн-игроков.
     * Именно это заставляет клиенты увидеть новый ник над головой и в табе.
     */
    public void refreshPlayer(Player player) {
        for (Player viewer : Bukkit.getOnlinePlayers()) {
            viewer.hidePlayer(plugin, player);
            viewer.showPlayer(plugin, player);
        }
    }
}
