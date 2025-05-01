package cn.huyast111.rmc;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerQuitEvent;

/**
 * 临时命令监听器，用于处理材质包加载和拒绝命令
 */
public class ResourcePackCommandListener implements Listener {
    
    private final RMCMaterialLoadingPlugin plugin;
    private final Player targetPlayer;
    private boolean processed = false;
    
    public ResourcePackCommandListener(RMCMaterialLoadingPlugin plugin, Player player) {
        this.plugin = plugin;
        this.targetPlayer = player;
    }
    
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onCommand(PlayerCommandPreprocessEvent event) {
        Player player = event.getPlayer();
        
        // 只处理目标玩家的命令
        if (!player.equals(targetPlayer) || processed) {
            return;
        }
        
        String command = event.getMessage().toLowerCase();
        
        if (command.startsWith("/loadpack")) {
            event.setCancelled(true);
            plugin.sendResourcePack(player);
            processed = true;
            HandlerList.unregisterAll(this);
        } else if (command.startsWith("/declinepack")) {
            event.setCancelled(true);
            player.sendMessage("§c您已选择暂不加载材质包，部分功能可能无法正常显示。");
            player.sendMessage("§e如果您改变主意，请使用 §b/czb reload §e命令加载材质包。");
            processed = true;
            HandlerList.unregisterAll(this);
        }
    }
    
    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        if (event.getPlayer().equals(targetPlayer)) {
            HandlerList.unregisterAll(this);
        }
    }
} 