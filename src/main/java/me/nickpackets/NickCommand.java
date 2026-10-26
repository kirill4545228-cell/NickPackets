package me.nickpackets;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

public class NickCommand implements CommandExecutor, TabCompleter {

    // Разрешены латиница, цифры, '_' и '&' для цветов
    private static final Pattern VALID = Pattern.compile("[A-Za-z0-9_&]{1,32}");
    private final NickPacketManager manager;

    public NickCommand(NickPacketManager manager) {
        this.manager = manager;
    }

    private void msg(CommandSender s, String text, ChatColor color) {
        s.sendMessage(color + text);
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (args.length != 2) {
            msg(sender, "Использование: /nick <игрок> <новый ник|reset>", ChatColor.RED);
            return true;
        }

        Player target = manager.findOnline(args[0]);
        if (target == null) {
            msg(sender, "Игрок не найден (он должен быть онлайн).", ChatColor.RED);
            return true;
        }

        String oldName = target.getName();
        String newName = args[1];
        String original = manager.getOriginal(target.getUniqueId());

        // Сброс ника
        if (newName.equalsIgnoreCase("reset") 
                || (original != null && original.equalsIgnoreCase(newName))) {
            if (manager.resetNick(target)) {
                msg(sender, "Ник игрока " + oldName + " сброшен на " + target.getName() + ".", ChatColor.GREEN);
            } else {
                msg(sender, "У этого игрока нет изменённого ника.", ChatColor.YELLOW);
            }
            return true;
        }

        if (!VALID.matcher(newName).matches()) {
            msg(sender, "Ник: 1–32 символа, латиница, цифры, '_' и '&' для цветов.", ChatColor.RED);
            return true;
        }

        if (manager.isNameTaken(newName, target.getUniqueId())) {
            msg(sender, "Этот ник уже занят.", ChatColor.RED);
            return true;
        }

        manager.setNick(target, newName);
        msg(sender, "Ник игрока " + oldName + " изменён на " + newName + ".", ChatColor.GREEN);
        if (!target.equals(sender)) {
            msg(target, "Ваш ник теперь " + newName + ".", ChatColor.GOLD);
        }
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command cmd, String alias, String[] args) {
        List<String> result = new ArrayList<>();
        if (args.length == 1) {
            for (Player p : Bukkit.getOnlinePlayers()) {
                if (p.getName().toLowerCase().startsWith(args[0].toLowerCase())) {
                    result.add(p.getName());
                }
            }
        } else if (args.length == 2 && "reset".startsWith(args[1].toLowerCase())) {
            result.add("reset");
        }
        return result;
    }
}
