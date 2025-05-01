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
            
            boolean bookOpened = false;
            
            // 尝试调用openBook方法
            try {
                Method openBook = craftPlayerClass.getMethod("openBook", ItemStack.class);
                openBook.invoke(craftPlayer, book);
                bookOpened = true;
            } catch (NoSuchMethodException e) {
                // 可能是其他方法名
                try {
                    Method openBook = craftPlayerClass.getDeclaredMethod("openBook", ItemStack.class);
                    openBook.setAccessible(true);
                    openBook.invoke(craftPlayer, book);
                    bookOpened = true;
                } catch (Exception ex) {
                    // 通知玩家手动打开书本
                    player.sendMessage(ChatColor.YELLOW + "请右键打开书本选择是否加载材质包");
                }
            }
            
            // 恢复玩家原来的物品
            if (bookOpened) {
                // 延迟一下再恢复物品，确保书本能正常打开
                Bukkit.getScheduler().runTaskLater(
                    RMCMaterialLoadingPlugin.getInstance(), 
                    () -> {
                        player.setItemInHand(oldItem);
                        player.updateInventory();
                    }, 
                    2L
                );
            } else {
                // 如果书本没有成功打开，直接恢复
                player.setItemInHand(oldItem);
                player.updateInventory();
            }
        } catch (Exception e) {
            e.printStackTrace();
            player.sendMessage(ChatColor.YELLOW + "请右键打开物品栏中的书本");
        }
    }
    
    /**
     * 直接通过数据包打开书籍界面（不需要在物品栏放置书籍）
     * @param player 目标玩家
     * @return 是否成功打开书籍界面
     */
    public static boolean directOpenBook(Player player) {
        try {
            // 服务器版本
            String version = getVersion();
            
            // 创建带点击功能的书本
            ItemStack book = createClickableBook(player);
            
            // 1.8版本没有直接打开书本的数据包，使用替代方法
            if (version.startsWith("v1_8")) {
                // 获取CraftPlayer
                Class<?> craftPlayerClass = Class.forName("org.bukkit.craftbukkit." + version + ".entity.CraftPlayer");
                Object craftPlayer = craftPlayerClass.cast(player);
                
                // 保存玩家原来的物品
                ItemStack oldItem = player.getItemInHand();
                
                // 临时设置书本到玩家手中
                player.setItemInHand(book);
                
                // 尝试调用openBook方法
                boolean bookOpened = false;
                try {
                    Method openBook = craftPlayerClass.getMethod("openBook", ItemStack.class);
                    openBook.invoke(craftPlayer, book);
                    bookOpened = true;
                } catch (NoSuchMethodException e) {
                    try {
                        // 1.8可能有其他方式打开书本，尝试反射调用各种可能的方法
                        Method openBook = craftPlayerClass.getDeclaredMethod("openBook", ItemStack.class);
                        openBook.setAccessible(true);
                        openBook.invoke(craftPlayer, book);
                        bookOpened = true;
                    } catch (Exception ex) {
                        // 获取玩家对应的NMS实体
                        Method getHandleMethod = craftPlayerClass.getMethod("getHandle");
                        Object entityPlayer = getHandleMethod.invoke(craftPlayer);
                        
                        // 尝试发送自定义数据包
                        try {
                            // 在1.8中，使用MC|BOpen自定义数据包来打开书本
                            Class<?> packetPlayOutCustomPayloadClass = Class.forName("net.minecraft.server." + version + ".PacketPlayOutCustomPayload");
                            
                            // 构造ByteBuf数据
                            Class<?> packetDataSerializerClass = Class.forName("net.minecraft.server." + version + ".PacketDataSerializer");
                            Class<?> byteBufClass = Class.forName("io.netty.buffer.ByteBuf");
                            Class<?> unpooledClass = Class.forName("io.netty.buffer.Unpooled");
                            Method wrappedBufferMethod = unpooledClass.getMethod("wrappedBuffer", byte[].class);
                            Object byteBuf = wrappedBufferMethod.invoke(null, new byte[]{0});
                            
                            Constructor<?> packetDataSerializerConstructor = packetDataSerializerClass.getConstructor(byteBufClass);
                            Object packetDataSerializer = packetDataSerializerConstructor.newInstance(byteBuf);
                            
                            // 创建一个打开书本的自定义数据包
                            Constructor<?> packetCtor = packetPlayOutCustomPayloadClass.getConstructor(String.class, packetDataSerializerClass);
                            Object packet = packetCtor.newInstance("MC|BOpen", packetDataSerializer);
                            
                            // 发送数据包
                            Class<?> playerConnectionClass = Class.forName("net.minecraft.server." + version + ".PlayerConnection");
                            Field playerConnectionField = entityPlayer.getClass().getField("playerConnection");
                            Object playerConnection = playerConnectionField.get(entityPlayer);
                            Method sendPacketMethod = playerConnectionClass.getMethod("sendPacket", Class.forName("net.minecraft.server." + version + ".Packet"));
                            sendPacketMethod.invoke(playerConnection, packet);
                            
                            bookOpened = true;
                        } catch (Exception exc) {
                            // 记录错误但继续执行
                            exc.printStackTrace();
                        }
                    }
                }
                
                // 恢复玩家原来的物品
                if (bookOpened) {
                    // 延迟一下再恢复物品，确保书本能正常打开
                    Bukkit.getScheduler().runTaskLater(
                        RMCMaterialLoadingPlugin.getInstance(), 
                        () -> {
                            player.setItemInHand(oldItem);
                            player.updateInventory();
                        }, 
                        2L
                    );
                } else {
                    // 如果书本没有成功打开，直接恢复
                    player.setItemInHand(oldItem);
                    player.updateInventory();
                }
                
                return bookOpened;
            } else {
                // 非1.8版本使用原方法
                try {
                    // 获取NMS物品栈
                    Class<?> craftItemStackClass = Class.forName("org.bukkit.craftbukkit." + version + ".inventory.CraftItemStack");
                    Method asNMSCopy = craftItemStackClass.getMethod("asNMSCopy", ItemStack.class);
                    Object nmsBook = asNMSCopy.invoke(null, book);
                    
                    // 获取玩家对应的NMS实体
                    Class<?> craftPlayerClass = Class.forName("org.bukkit.craftbukkit." + version + ".entity.CraftPlayer");
                    Object craftPlayer = craftPlayerClass.cast(player);
                    Method getHandleMethod = craftPlayerClass.getMethod("getHandle");
                    Object entityPlayer = getHandleMethod.invoke(craftPlayer);
                    
                    // 创建打开书籍界面的数据包
                    Class<?> packetPlayOutOpenBookClass = Class.forName("net.minecraft.server." + version + ".PacketPlayOutOpenBook");
                    
                    // 尝试获取构造函数和发送数据包
                    boolean packetSent = false;
                    
                    // 尝试新版本方法 (1.14+)
                    try {
                        // PacketPlayOutOpenBook(EnumHand) 构造方法
                        Class<?> enumHandClass = Class.forName("net.minecraft.server." + version + ".EnumHand");
                        Object mainHand = enumHandClass.getField("MAIN_HAND").get(null);
                        
                        Constructor<?> packetCtor = packetPlayOutOpenBookClass.getConstructor(enumHandClass);
                        Object packet = packetCtor.newInstance(mainHand);
                        
                        // 设置玩家手持物品为书本（暂时替换）
                        Method setItemInHandMethod = entityPlayer.getClass().getMethod("a", enumHandClass, nmsBook.getClass());
                        Object originalItem = entityPlayer.getClass().getMethod("b", enumHandClass).invoke(entityPlayer, mainHand);
                        setItemInHandMethod.invoke(entityPlayer, mainHand, nmsBook);
                        
                        // 发送数据包
                        Class<?> playerConnectionClass = Class.forName("net.minecraft.server." + version + ".PlayerConnection");
                        Field playerConnectionField = entityPlayer.getClass().getField("playerConnection");
                        Object playerConnection = playerConnectionField.get(entityPlayer);
                        Method sendPacketMethod = playerConnectionClass.getMethod("sendPacket", Class.forName("net.minecraft.server." + version + ".Packet"));
                        sendPacketMethod.invoke(playerConnection, packet);
                        
                        // 恢复原来的物品
                        setItemInHandMethod.invoke(entityPlayer, mainHand, originalItem);
                        
                        packetSent = true;
                    } catch (Exception e1) {
                        // 尝试旧版本方法 (1.9 - 1.13)
                        try {
                            Constructor<?> packetCtor = packetPlayOutOpenBookClass.getConstructor();
                            Object packet = packetCtor.newInstance();
                            
                            // 发送数据包前需要临时设置玩家手持物品为书本
                            ItemStack oldItem = player.getItemInHand();
                            player.setItemInHand(book);
                            
                            // 发送数据包
                            Class<?> playerConnectionClass = Class.forName("net.minecraft.server." + version + ".PlayerConnection");
                            Field playerConnectionField = entityPlayer.getClass().getField("playerConnection");
                            Object playerConnection = playerConnectionField.get(entityPlayer);
                            Method sendPacketMethod = playerConnectionClass.getMethod("sendPacket", Class.forName("net.minecraft.server." + version + ".Packet"));
                            sendPacketMethod.invoke(playerConnection, packet);
                            
                            // 恢复原来的物品
                            Bukkit.getScheduler().runTaskLater(
                                RMCMaterialLoadingPlugin.getInstance(),
                                () -> {
                                    player.setItemInHand(oldItem);
                                    player.updateInventory();
                                },
                                1L
                            );
                            
                            packetSent = true;
                        } catch (Exception e2) {
                            // 尝试直接使用openBook方法
                            try {
                                Method openBookMethod = craftPlayerClass.getMethod("openBook", ItemStack.class);
                                openBookMethod.invoke(craftPlayer, book);
                                packetSent = true;
                            } catch (Exception e3) {
                                try {
                                    Method openBookMethod = craftPlayerClass.getDeclaredMethod("openBook", ItemStack.class);
                                    openBookMethod.setAccessible(true);
                                    openBookMethod.invoke(craftPlayer, book);
                                    packetSent = true;
                                } catch (Exception e4) {
                                    // 所有方法都失败了
                                    e4.printStackTrace();
                                }
                            }
                        }
                    }
                    
                    return packetSent;
                } catch (Exception e) {
                    e.printStackTrace();
                    return false;
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }
    
    // 获取NMS版本
    private static String getVersion() {
        String packageName = Bukkit.getServer().getClass().getPackage().getName();
        return packageName.substring(packageName.lastIndexOf('.') + 1);
    }
} 