package cn.huyast111.rmc;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerResourcePackStatusEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.BookMeta;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.command.ConsoleCommandSender;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class RMCMaterialLoadingPlugin extends JavaPlugin implements Listener, CommandExecutor {

    private String resourcePackUrl;
    private String resourcePackHash;
    // 记录已经处理过材质包加载的玩家
    private Map<UUID, Boolean> resourcePackPrompted = new HashMap<>();
    // 服务器NMS版本
    private String nmsVersion;
    // 记录玩家的材质包加载状态
    private final Map<UUID, Boolean> playerResourcePackStatus = new HashMap<>();

    @Override
    public void onEnable() {
        // 获取NMS版本
        nmsVersion = getVersion();
        
        // 保存默认配置
        saveDefaultConfig();
        
        // 从配置中加载材质包URL和HASH
        resourcePackUrl = getConfig().getString("resource-pack-url", "https://example.com/your-resource-pack.zip");
        resourcePackHash = getConfig().getString("resource-pack-hash", "");
        
        // 注册事件监听器
        getServer().getPluginManager().registerEvents(this, this);
        
        // 注册命令
        getCommand("czb").setExecutor(this);
        
        // 启动定时任务，检查现有玩家
        Bukkit.getScheduler().runTaskTimer(this, this::checkOnlinePlayers, 100L, 6000L); // 5秒后开始，每5分钟运行一次
        
        getLogger().info("[RMC]材质包加载插件已启用！-Powered By XMRhapsody");
    }
    
    // 获取NMS版本
    private String getVersion() {
        String packageName = Bukkit.getServer().getClass().getPackage().getName();
        return packageName.substring(packageName.lastIndexOf('.') + 1);
    }

    @Override
    public void onDisable() {
        getLogger().info("材质包加载插件已禁用！-Powered By XMRhapsody");
    }

    // 检查所有在线玩家
    private void checkOnlinePlayers() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            // 如果玩家还没有被提示过材质包，尝试提示
            if (!resourcePackPrompted.containsKey(player.getUniqueId()) || !resourcePackPrompted.get(player.getUniqueId())) {
                checkAndPromptResourcePack(player);
            }
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        
        // 重置玩家状态
        resourcePackPrompted.put(player.getUniqueId(), false);
        
        // 延迟检查，等待玩家完全加载进服务器
        // 使用多个延迟时间点进行尝试，确保能够触发提示
        Bukkit.getScheduler().runTaskLater(this, () -> {
            if (!resourcePackPrompted.getOrDefault(player.getUniqueId(), false)) {
                checkAndPromptResourcePack(player);
            }
        }, 20L); // 1秒后尝试
        
        Bukkit.getScheduler().runTaskLater(this, () -> {
            if (!resourcePackPrompted.getOrDefault(player.getUniqueId(), false)) {
                checkAndPromptResourcePack(player);
            }
        }, 60L); // 3秒后再次尝试
        
        Bukkit.getScheduler().runTaskLater(this, () -> {
            if (!resourcePackPrompted.getOrDefault(player.getUniqueId(), false)) {
                checkAndPromptResourcePack(player);
            }
        }, 200L); // 10秒后最后尝试
    }
    
    // 玩家重生事件，用于捕获玩家从死亡状态回来的时刻
    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerRespawn(PlayerRespawnEvent event) {
        Player player = event.getPlayer();
        
        // 延迟执行，确保重生完成
        Bukkit.getScheduler().runTaskLater(this, () -> {
            if (!resourcePackPrompted.getOrDefault(player.getUniqueId(), false)) {
                checkAndPromptResourcePack(player);
            }
        }, 20L);
    }
    
    // 玩家切换世界事件，用于捕获玩家切换世界的时刻
    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerChangeWorld(PlayerChangedWorldEvent event) {
        Player player = event.getPlayer();
        
        // 延迟执行，确保世界切换完成
        Bukkit.getScheduler().runTaskLater(this, () -> {
            if (!resourcePackPrompted.getOrDefault(player.getUniqueId(), false)) {
                checkAndPromptResourcePack(player);
            }
        }, 20L);
    }
    
    // 玩家退出时清理数据
    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        resourcePackPrompted.remove(player.getUniqueId());
    }

    @EventHandler
    public void onResourcePackStatus(PlayerResourcePackStatusEvent event) {
        Player player = event.getPlayer();
        PlayerResourcePackStatusEvent.Status status = event.getStatus();
        
        getLogger().info("[RMC] 玩家 " + player.getName() + " 的材质包状态: " + status.toString());
        
        switch (status) {
            case SUCCESSFULLY_LOADED:
                // 材质包成功加载
                player.sendMessage(ChatColor.GREEN + "材质包加载成功！");
                // 记录玩家已加载材质包
                setPlayerResourcePackLoaded(player, true);
                break;
                
            case DECLINED:
                // 玩家拒绝加载材质包
                player.sendMessage(ChatColor.RED + "您拒绝了服务器材质包，部分功能可能无法正常显示。");
                player.sendMessage(ChatColor.YELLOW + "如需加载材质包，请输入 /czb reload");
                // 记录玩家拒绝加载
                setPlayerResourcePackLoaded(player, false);
                break;
                
            case FAILED_DOWNLOAD:
                // 材质包下载失败
                player.sendMessage(ChatColor.RED + "材质包下载失败！请检查您的网络连接。");
                player.sendMessage(ChatColor.YELLOW + "您可以尝试输入 /czb forcereload 重新加载");
                // 记录加载失败
                setPlayerResourcePackLoaded(player, false);
                
                // 5秒后提示重试
                Bukkit.getScheduler().runTaskLater(this, () -> {
                    if (player.isOnline()) {
                        player.sendMessage(ChatColor.YELLOW + "您可以输入 /czb reload 重新尝试加载材质包");
                    }
                }, 100L);
                break;
                
            case ACCEPTED:
                // 玩家接受了材质包，但尚未完成加载
                player.sendMessage(ChatColor.GOLD + "正在下载材质包...");
                
                // 添加下载检查任务
                Bukkit.getScheduler().runTaskLater(this, () -> {
                    if (player.isOnline() && !hasPlayerLoadedResourcePack(player)) {
                        // 10秒后仍未收到SUCCESSFULLY_LOADED状态，可能是下载缓慢或静默失败
                        player.sendMessage(ChatColor.YELLOW + "材质包似乎下载缓慢。如果长时间未加载，请尝试：");
                        player.sendMessage(ChatColor.YELLOW + "1. 输入 /czb forcereload 重新加载");
                        player.sendMessage(ChatColor.YELLOW + "2. 在设置中禁用再重新启用服务器资源包");
                    }
                }, 200L); // 10秒后检查
                break;
                
            default:
                getLogger().info("[RMC] 未处理的材质包状态: " + status);
                break;
        }
    }
    
    private void setPlayerResourcePackLoaded(Player player, boolean loaded) {
        playerResourcePackStatus.put(player.getUniqueId(), loaded);
    }
    
    private boolean hasPlayerLoadedResourcePack(Player player) {
        return playerResourcePackStatus.getOrDefault(player.getUniqueId(), false);
    }
    
    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) {
            if (sender instanceof Player) {
                Player player = (Player) sender;
                player.sendMessage(ChatColor.GOLD + "=== 材质包加载插件 ===");
                player.sendMessage(ChatColor.YELLOW + "/czb reload - 重新加载材质包");
                player.sendMessage(ChatColor.YELLOW + "/czb config - 重新加载配置文件");
                player.sendMessage(ChatColor.YELLOW + "/czb forcereload - 强制重新加载最新材质包");
                if (player.hasPermission("rmc.admin")) {
                    player.sendMessage(ChatColor.YELLOW + "/czb send <玩家名> - 向指定玩家发送材质包");
                    player.sendMessage(ChatColor.YELLOW + "/czb prompt <玩家名> - 向指定玩家发送材质包提示");
                }
            } else {
                sender.sendMessage(ChatColor.RED + "此命令只能由玩家执行！");
            }
            return true;
        }
        
        if (args[0].equalsIgnoreCase("forcereload")) {
            if (!(sender instanceof Player)) {
                sender.sendMessage(ChatColor.RED + "此命令只能由玩家执行！");
                return true;
            }
            Player player = (Player) sender;
            
            // 清除客户端缓存提示
            player.sendMessage(ChatColor.GOLD + "正在强制重新加载最新材质包...");
            player.sendMessage(ChatColor.YELLOW + "提示: 如果仍然加载旧材质包，请尝试在Minecraft设置中禁用并重新启用服务器资源包");
            
            // 发送带有随机参数的材质包URL
            sendResourcePack(player);
            resourcePackPrompted.put(player.getUniqueId(), true);
            return true;
        }
        
        if (args[0].equalsIgnoreCase("reload")) {
            if (!(sender instanceof Player)) {
                sender.sendMessage(ChatColor.RED + "此命令只能由玩家执行！");
                return true;
            }
            Player player = (Player) sender;
            sendResourcePack(player);
            resourcePackPrompted.put(player.getUniqueId(), true);
            return true;
        }
        
        if (args[0].equalsIgnoreCase("config") && sender.hasPermission("rmc.admin")) {
            reloadConfig();
            resourcePackUrl = getConfig().getString("resource-pack-url", "https://example.com/your-resource-pack.zip");
            resourcePackHash = getConfig().getString("resource-pack-hash", "");
            sender.sendMessage(ChatColor.GREEN + "配置已重新加载！");
            return true;
        }
        
        // 管理员命令 - 发送材质包给指定玩家
        if (args[0].equalsIgnoreCase("send") && sender.hasPermission("rmc.admin")) {
            if (args.length < 2) {
                sender.sendMessage(ChatColor.RED + "用法: /czb send <玩家名>");
                return true;
            }
            
            Player target = Bukkit.getPlayer(args[1]);
            if (target == null || !target.isOnline()) {
                sender.sendMessage(ChatColor.RED + "玩家 " + args[1] + " 不在线或不存在！");
                return true;
            }
            
            sendResourcePack(target);
            resourcePackPrompted.put(target.getUniqueId(), true);
            sender.sendMessage(ChatColor.GREEN + "材质包发送请求已发送给 " + target.getName());
            return true;
        }
        
        // 管理员命令 - 发送材质包提示给指定玩家
        if (args[0].equalsIgnoreCase("prompt") && sender.hasPermission("rmc.admin")) {
            if (args.length < 2) {
                sender.sendMessage(ChatColor.RED + "用法: /czb prompt <玩家名>");
                return true;
            }
            
            Player target = Bukkit.getPlayer(args[1]);
            if (target == null || !target.isOnline()) {
                sender.sendMessage(ChatColor.RED + "玩家 " + args[1] + " 不在线或不存在！");
                return true;
            }
            
            resourcePackPrompted.put(target.getUniqueId(), false);
            checkAndPromptResourcePack(target);
            sender.sendMessage(ChatColor.GREEN + "材质包提示已发送给 " + target.getName());
            return true;
        }
        
        return false;
    }
    
    private void checkAndPromptResourcePack(Player player) {
        // 检查玩家状态是否正常
        if (!player.isOnline()) {
            return;
        }
        
        // 标记已经提示过
        resourcePackPrompted.put(player.getUniqueId(), true);
        
        // 使用书本询问玩家是否需要加载材质包
        giveClickableBook(player);
    }
    
    private void giveClickableBook(Player player) {
        try {
            // 先清除之前可能存在的书本
            clearExistingBooks(player);
            
            // 使用工具类创建带点击功能的书本
            ItemStack book = BookUtils.createClickableBook(player);
            
            // 将书本放在第5格
            player.getInventory().setItem(4, book);
            
            // 使用工具类打开书本
            BookUtils.openBook(player, book);
            
            // 注册命令监听器
            registerPackCommands(player);
            
        } catch (Exception e) {
            getLogger().warning("材质包书本创建失败: " + e.getMessage());
            // 备用方案 - 直接发送命令提示
            player.sendMessage(ChatColor.GOLD + "==================================");
            player.sendMessage(ChatColor.GREEN + "[RMC]请选择是否加载CS材质包:");
            player.sendMessage(ChatColor.YELLOW + "输入 " + ChatColor.GREEN + "/loadpack" + ChatColor.YELLOW + " 加载材质包");
            player.sendMessage(ChatColor.YELLOW + "输入 " + ChatColor.RED + "/declinepack" + ChatColor.YELLOW + " 暂不加载");
            player.sendMessage(ChatColor.GOLD + "==================================");
            registerPackCommands(player);
        }
    }
    
    // 清除玩家背包中已有的材质包书本
    private void clearExistingBooks(Player player) {
        for (int i = 0; i < player.getInventory().getSize(); i++) {
            ItemStack item = player.getInventory().getItem(i);
            if (item != null && item.getType() == Material.WRITTEN_BOOK) {
                BookMeta meta = (BookMeta) item.getItemMeta();
                if (meta.getTitle().contains("CS材质包") || meta.getTitle().contains("材质包")) {
                    player.getInventory().setItem(i, null);
                }
            }
        }
    }
    
    private void registerPackCommands(Player player) {
        // 使用临时的命令映射来处理玩家点击响应
        Bukkit.getPluginManager().registerEvents(new ResourcePackCommandListener(this, player), this);
    }
    
    /**
     * 发送材质包给玩家
     * @param player 目标玩家
     */
    public void sendResourcePack(Player player) {
        try {
            // 重置玩家材质包状态
            setPlayerResourcePackLoaded(player, false);
            
            // 添加随机参数强制更新材质包
            String url = addForceUpdateParam(resourcePackUrl);
            
            // 发送材质包
            if (resourcePackHash.isEmpty()) {
                player.setResourcePack(url);
            } else {
                // 对于1.8.8，不支持带hash的setResourcePack方法
                player.setResourcePack(url);
            }
            
            player.sendMessage(ChatColor.GREEN + "正在发送材质包请求，请稍候...");
            player.sendMessage(ChatColor.GRAY + "如果长时间未显示加载提示，请尝试 /czb forcereload");
            
            // 创建定时任务检查材质包是否真正加载
            Bukkit.getScheduler().runTaskLater(this, () -> {
                if (player.isOnline() && !hasPlayerLoadedResourcePack(player)) {
                    // 30秒后仍未加载，提示玩家
                    player.sendMessage(ChatColor.YELLOW + "材质包可能未正确加载。如遇问题，请尝试：");
                    player.sendMessage(ChatColor.YELLOW + "1. 输入 /czb forcereload 重新加载");
                    player.sendMessage(ChatColor.YELLOW + "2. 重新进入服务器");
                    player.sendMessage(ChatColor.YELLOW + "3. 在设置中禁用再重新启用服务器资源包");
                }
            }, 600L); // 30秒后检查
            
        } catch (Exception e) {
            player.sendMessage(ChatColor.RED + "材质包发送失败：" + e.getMessage());
            getLogger().warning("材质包发送失败：" + e.getMessage());
        }
    }
    
    /**
     * 为材质包URL添加强制更新参数
     * @param url 原材质包URL
     * @return 带有更新参数的URL
     */
    private String addForceUpdateParam(String url) {
        // 添加时间戳参数强制更新
        String separator = url.contains("?") ? "&" : "?";
        return url + separator + "v=" + System.currentTimeMillis();
    }
} 