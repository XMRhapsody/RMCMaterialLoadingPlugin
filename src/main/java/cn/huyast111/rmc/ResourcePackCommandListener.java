package cn.huyast111.rmc;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.player.PlayerItemHeldEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.BookMeta;

/**
 * 临时命令监听器，用于处理材质包加载和拒绝命令
 */
public class ResourcePackCommandListener implements Listener {
    
    private final RMCMaterialLoadingPlugin plugin;
    private final Player targetPlayer;
    private boolean processed = false;
    private int reopenAttempts = 0;
    private final int MAX_REOPEN_ATTEMPTS = 5; // 最大重新打开次数，防止无限循环
    private boolean checkScheduled = false; // 标记是否已经安排了检查
    
    public ResourcePackCommandListener(RMCMaterialLoadingPlugin plugin, Player player) {
        this.plugin = plugin;
        this.targetPlayer = player;
        
        // 初始化时标记玩家尚未做出选择
        plugin.setPlayerMadeChoice(player.getUniqueId(), false);
        
        // 3秒后检查玩家是否做出了选择
        scheduleNextCheck(60L); // 3秒后检查
    }
    
    // 安排下一次检查，避免重复安排
    private void scheduleNextCheck(long delay) {
        if (!checkScheduled && !processed) {
            checkScheduled = true;
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                checkScheduled = false;
                if (!processed) {
                    checkAndReopenBook();
                }
            }, delay);
        }
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
            plugin.setPlayerMadeChoice(player.getUniqueId(), true);
            plugin.sendResourcePack(player);
            processed = true;
            HandlerList.unregisterAll(this);
        } else if (command.startsWith("/declinepack")) {
            event.setCancelled(true);
            plugin.setPlayerMadeChoice(player.getUniqueId(), true);
            player.sendMessage("§c您已选择暂不加载材质包，部分功能可能无法正常显示。");
            player.sendMessage("§e如果您改变主意，请使用 §b/czb reload §e命令加载材质包。");
            processed = true;
            HandlerList.unregisterAll(this);
        }
    }
    
    // 检测物品栏关闭事件，可能是书本被关闭
    @EventHandler
    public void onInventoryClose(InventoryCloseEvent event) {
        if (processed) return; // 如果已处理，不再响应事件
        
        if (event.getPlayer() instanceof Player) {
            Player player = (Player) event.getPlayer();
            if (player.equals(targetPlayer)) {
                // 如果玩家关闭了物品栏但没有做出选择，稍后再检查玩家状态
                scheduleNextCheck(5L); // 延迟5tick后检查
            }
        }
    }
    
    // 检测玩家交互事件，可能包括右键关闭书本
    @EventHandler
    public void onPlayerInteract(PlayerInteractEvent event) {
        if (processed) return; // 如果已处理，不再响应事件
        
        Player player = event.getPlayer();
        if (player.equals(targetPlayer)) {
            // 交互后延迟检查，避免过早检测
            scheduleNextCheck(5L);
        }
    }
    
    // 玩家切换手持物品，可能是通过按数字键切换离开了书本界面
    @EventHandler
    public void onPlayerChangeHeldItem(PlayerItemHeldEvent event) {
        if (processed) return; // 如果已处理，不再响应事件
        
        Player player = event.getPlayer();
        if (player.equals(targetPlayer)) {
            // 稍后检查玩家是否需要重新打开书本
            scheduleNextCheck(5L);
        }
    }
    
    // 检查并重新打开书本
    private void checkAndReopenBook() {
        // 如果玩家已离线或已处理，不再重新打开
        if (!targetPlayer.isOnline() || processed) {
            return;
        }
        
        // 如果玩家已经做出选择，则标记为已处理并注销监听器
        if (plugin.hasPlayerMadeChoice(targetPlayer.getUniqueId())) {
            processed = true;
            HandlerList.unregisterAll(this);
            return;
        }
        
        // 如果已达到最大尝试次数，放弃重新打开
        if (reopenAttempts >= MAX_REOPEN_ATTEMPTS) {
            targetPlayer.sendMessage("§e您似乎没有做出选择。如需加载材质包，请输入 §b/czb reload §e命令。");
            processed = true;
            HandlerList.unregisterAll(this);
            return;
        }
        
        // 再次尝试打开书本
        reopenAttempts++;
        if (targetPlayer.isOnline() && !processed) {
            // 重新打开书籍
            plugin.giveClickableBook(targetPlayer);
            
            // 通知玩家
            if (reopenAttempts > 1) {
                targetPlayer.sendMessage("§e请做出选择：是否加载材质包？");
            }
            
            // 安排下一次检查
            scheduleNextCheck(60L); // 3秒后再次检查
        }
    }
    
    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        if (event.getPlayer().equals(targetPlayer)) {
            HandlerList.unregisterAll(this);
        }
    }
} 