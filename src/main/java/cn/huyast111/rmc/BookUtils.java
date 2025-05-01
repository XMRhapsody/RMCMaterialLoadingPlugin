package cn.huyast111.rmc;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.BookMeta;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;

/**
 * 书本工具类 - 用于创建带有点击功能的书本
 */
public class BookUtils {

    /**
     * 创建一个带有点击功能的书本
     */
    public static ItemStack createClickableBook(Player player) {
        try {
            // 服务器版本
            String version = getVersion();
            
            // 创建书本物品
            ItemStack book = new ItemStack(Material.WRITTEN_BOOK);
            BookMeta meta = (BookMeta) book.getItemMeta();
            meta.setTitle(ChatColor.GOLD + "CS材质包");
            meta.setAuthor("[RMC]服务器");
            meta.addPage("加载中...");
            book.setItemMeta(meta);
            
            // 转换为NMS物品
            Class<?> craftItemStackClass = Class.forName("org.bukkit.craftbukkit." + version + ".inventory.CraftItemStack");
            Method asNMSCopy = craftItemStackClass.getMethod("asNMSCopy", ItemStack.class);
            Object nmsBook = asNMSCopy.invoke(null, book);
            
            // 获取NBT标签
            Class<?> nmsItemStackClass = nmsBook.getClass();
            Method getTag = nmsItemStackClass.getMethod("getTag");
            Object tag = getTag.invoke(nmsBook);
            
            // 如果没有标签，创建一个
            Class<?> nbtTagCompoundClass = Class.forName("net.minecraft.server." + version + ".NBTTagCompound");
            if (tag == null) {
                tag = nbtTagCompoundClass.newInstance();
                Method setTag = nmsItemStackClass.getMethod("setTag", nbtTagCompoundClass);
                setTag.invoke(nmsBook, tag);
            }
            
            // 创建页面列表
            Class<?> nbtTagListClass = Class.forName("net.minecraft.server." + version + ".NBTTagList");
            Object pages = nbtTagListClass.newInstance();
            
            // 创建JSON文本
            String json = 
                "{\"text\":\"\",\"extra\":[" +
                    "{\"text\":\"材质包加载\",\"bold\":true,\"color\":\"dark_purple\"}," +
                    "{\"text\":\"\\n\\n亲爱的玩家,\\n\\n\",\"color\":\"black\"}," +
                    "{\"text\":\"服务器需要加载CS材质包以获得最佳游戏体验。\\n\\n\",\"color\":\"black\"}," +
                    "{\"text\":\"请选择:\\n\\n\",\"bold\":true,\"color\":\"dark_green\"}," +
                    "{\"text\":\"【点击这里加载材质包】\",\"bold\":true,\"color\":\"blue\",\"clickEvent\":{\"action\":\"run_command\",\"value\":\"/loadpack\"}}," +
                    "{\"text\":\"\\n\\n\",\"color\":\"reset\"}," +
                    "{\"text\":\"【点击这里暂不加载】\",\"bold\":true,\"color\":\"red\",\"clickEvent\":{\"action\":\"run_command\",\"value\":\"/declinepack\"}}" +
                "]}";
            
            // 添加页面
            Class<?> nbtTagStringClass = Class.forName("net.minecraft.server." + version + ".NBTTagString");
            Constructor<?> stringConstructor = nbtTagStringClass.getConstructor(String.class);
            Object page = stringConstructor.newInstance(json);
            
            // 添加到列表
            Method add = nbtTagListClass.getMethod("add", Class.forName("net.minecraft.server." + version + ".NBTBase"));
            add.invoke(pages, page);
            
            // 设置pages标签
            Method set = nbtTagCompoundClass.getMethod("set", String.class, Class.forName("net.minecraft.server." + version + ".NBTBase"));
            set.invoke(tag, "pages", pages);
            
            // 设置resolved标记
            Method setBoolean = nbtTagCompoundClass.getMethod("setBoolean", String.class, boolean.class);
            setBoolean.invoke(tag, "resolved", true);
            
            // 转回Bukkit物品
            Method asBukkitCopy = craftItemStackClass.getMethod("asBukkitCopy", nmsItemStackClass);
            return (ItemStack) asBukkitCopy.invoke(null, nmsBook);
            
        } catch (Exception e) {
            e.printStackTrace();
            // 如果失败，返回普通书本
            ItemStack book = new ItemStack(Material.WRITTEN_BOOK);
            BookMeta meta = (BookMeta) book.getItemMeta();
            meta.setTitle(ChatColor.GOLD + "CS材质包");
            meta.setAuthor("[RMC]服务器");
            meta.addPage(
                ChatColor.DARK_PURPLE + "" + ChatColor.BOLD + "材质包加载\n\n" +
                ChatColor.BLACK + "亲爱的玩家，\n\n" +
                "服务器需要加载CS材质包以获得最佳游戏体验。\n\n" +
                ChatColor.DARK_GREEN + "" + ChatColor.BOLD + "请输入命令:\n\n" +
                ChatColor.BLUE + "/loadpack" + ChatColor.BLACK + " - 加载材质包\n\n" +
                ChatColor.RED + "/declinepack" + ChatColor.BLACK + " - 暂不加载"
            );
            book.setItemMeta(meta);
            return book;
        }
    }
    
    /**
     * 尝试打开书本
     */
    public static void openBook(Player player, ItemStack book) {
        try {
            // 保存原来的物品
            ItemStack oldItem = player.getItemInHand();
            
            // 设置书本到玩家手中
            player.setItemInHand(book);
            
            // 版本
            String version = getVersion();
            
            // 获取CraftPlayer
            Class<?> craftPlayerClass = Class.forName("org.bukkit.craftbukkit." + version + ".entity.CraftPlayer");
            Object craftPlayer = craftPlayerClass.cast(player);
            
            // 尝试调用openBook方法
            try {
                Method openBook = craftPlayerClass.getMethod("openBook", ItemStack.class);
                openBook.invoke(craftPlayer, book);
            } catch (NoSuchMethodException e) {
                // 可能是其他方法名
                try {
                    Method openBook = craftPlayerClass.getDeclaredMethod("openBook", ItemStack.class);
                    openBook.setAccessible(true);
                    openBook.invoke(craftPlayer, book);
                } catch (Exception ex) {
                    // 通知玩家手动打开书本
                    player.sendMessage(ChatColor.YELLOW + "请右键打开书本选择是否加载材质包");
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
            player.sendMessage(ChatColor.YELLOW + "请右键打开物品栏中的书本");
        }
    }
    
    // 获取NMS版本
    private static String getVersion() {
        String packageName = Bukkit.getServer().getClass().getPackage().getName();
        return packageName.substring(packageName.lastIndexOf('.') + 1);
    }
} 