package NJ.autopickup;

import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.event.Listener;
import org.bukkit.event.EventHandler;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.TabCompleter;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.entity.Item;
import org.bukkit.entity.Entity;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.World;
import org.bukkit.Material;
import org.bukkit.util.BoundingBox;
import org.bukkit.ChatColor;
import org.bukkit.Sound;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import net.md_5.bungee.api.chat.ClickEvent;
import net.md_5.bungee.api.chat.ComponentBuilder;
import net.md_5.bungee.api.chat.HoverEvent;
import net.md_5.bungee.api.chat.TextComponent;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.List;
import java.util.ArrayList;
import java.util.Arrays;
import java.io.File;
import java.io.IOException;

public class Autopickup extends JavaPlugin implements Listener, CommandExecutor, TabCompleter {

    private Map<UUID, Boolean> playerSettings = new HashMap<>();
    private Map<UUID, Boolean> playerSoundSettings = new HashMap<>(); // 聲音設定
    private Map<UUID, Boolean> playerShiftSettings = new HashMap<>(); // Shift設定
    private File dataFile;
    private FileConfiguration dataConfig;

    @Override
    public void onEnable() {
        getServer().getPluginManager().registerEvents(this, this);
        this.getCommand("autopickup").setExecutor(this);
        this.getCommand("autopickup").setTabCompleter(this); // 設定Tab補全

        // 創建數據文件
        createDataFile();
        loadPlayerData();

        getLogger().info("AutoPickup插件已啟用!");
    }

    @Override
    public void onDisable() {
        savePlayerData();
        getLogger().info("AutoPickup插件已停用!");
    }

    /**
     * 創建數據文件
     */
    private void createDataFile() {
        dataFile = new File(getDataFolder(), "playerdata.yml");
        if (!dataFile.exists()) {
            dataFile.getParentFile().mkdirs();
            try {
                dataFile.createNewFile();
            } catch (IOException e) {
                getLogger().severe("無法創建數據文件: " + e.getMessage());
            }
        }
        dataConfig = YamlConfiguration.loadConfiguration(dataFile);
    }

    /**
     * 載入玩家數據
     */
    private void loadPlayerData() {
        for (String uuidString : dataConfig.getKeys(false)) {
            try {
                UUID uuid = UUID.fromString(uuidString);
                boolean enabled = dataConfig.getBoolean(uuidString + ".enabled", false);
                boolean soundEnabled = dataConfig.getBoolean(uuidString + ".sound", true); // 預設開啟聲音
                boolean shiftRequired = dataConfig.getBoolean(uuidString + ".shift", true); // 預設需要Shift
                playerSettings.put(uuid, enabled);
                playerSoundSettings.put(uuid, soundEnabled);
                playerShiftSettings.put(uuid, shiftRequired);
            } catch (IllegalArgumentException e) {
                getLogger().warning("無效的UUID: " + uuidString);
            }
        }
        getLogger().info("載入了 " + playerSettings.size() + " 個玩家的設定");
    }

    /**
     * 保存玩家數據
     */
    private void savePlayerData() {
        for (Map.Entry<UUID, Boolean> entry : playerSettings.entrySet()) {
            UUID uuid = entry.getKey();
            dataConfig.set(uuid.toString() + ".enabled", entry.getValue());
            dataConfig.set(uuid.toString() + ".sound", playerSoundSettings.getOrDefault(uuid, true));
            dataConfig.set(uuid.toString() + ".shift", playerShiftSettings.getOrDefault(uuid, true));
        }
        try {
            dataConfig.save(dataFile);
        } catch (IOException e) {
            getLogger().severe("無法保存玩家數據: " + e.getMessage());
        }
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        UUID uuid = player.getUniqueId();

        // 如果是新玩家，預設為關閉功能，開啟聲音，需要Shift
        if (!playerSettings.containsKey(uuid)) {
            playerSettings.put(uuid, false);
            playerSoundSettings.put(uuid, true);
            playerShiftSettings.put(uuid, true);
        }
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        // 玩家離線時保存數據
        savePlayerData();
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage(ChatColor.RED + "此指令只能由玩家執行!");
            return true;
        }

        Player player = (Player) sender;
        UUID uuid = player.getUniqueId();

        if (args.length == 0) {
            // 顯示可點擊的設定介面
            sendClickableSettings(player);
            return true;
        }

        if (args.length == 1) {
            String arg = args[0].toLowerCase();
            boolean newState;

            if (arg.equals("true") || arg.equals("on") || arg.equals("開") || arg.equals("開啟")) {
                newState = true;
            } else if (arg.equals("false") || arg.equals("off") || arg.equals("關") || arg.equals("關閉")) {
                newState = false;
            } else {
                player.sendMessage(ChatColor.RED + "無效參數! 請使用 true 或 false");
                return true;
            }

            playerSettings.put(uuid, newState);

            // 立即保存設定
            savePlayerData();

            // 重新顯示設定介面
            sendClickableSettings(player);
            return true;
        }

        if (args.length == 2 && args[0].toLowerCase().equals("sound")) {
            String arg = args[1].toLowerCase();
            boolean newSoundState;

            if (arg.equals("true") || arg.equals("on") || arg.equals("開") || arg.equals("開啟")) {
                newSoundState = true;
            } else if (arg.equals("false") || arg.equals("off") || arg.equals("關") || arg.equals("關閉")) {
                newSoundState = false;
            } else {
                player.sendMessage(ChatColor.RED + "無效參數! 請使用 sound true 或 sound false");
                return true;
            }

            playerSoundSettings.put(uuid, newSoundState);

            // 立即保存設定
            savePlayerData();

            // 重新顯示設定介面
            sendClickableSettings(player);
            return true;
        }

        if (args.length == 2 && args[0].toLowerCase().equals("shift")) {
            String arg = args[1].toLowerCase();
            boolean newShiftState;

            if (arg.equals("true") || arg.equals("on") || arg.equals("開") || arg.equals("開啟")) {
                newShiftState = true;
            } else if (arg.equals("false") || arg.equals("off") || arg.equals("關") || arg.equals("關閉")) {
                newShiftState = false;
            } else {
                player.sendMessage(ChatColor.RED + "無效參數! 請使用 shift true 或 shift false");
                return true;
            }

            playerShiftSettings.put(uuid, newShiftState);

            // 立即保存設定
            savePlayerData();

            // 重新顯示設定介面
            sendClickableSettings(player);
            return true;
        }

        player.sendMessage(ChatColor.RED + "用法:");
        player.sendMessage(ChatColor.RED + "  /autopickup [true/false]");
        player.sendMessage(ChatColor.RED + "  /autopickup sound [true/false]");
        player.sendMessage(ChatColor.RED + "  /autopickup shift [true/false]");
        return true;
    }

    @EventHandler
    public void onBlockBreak(BlockBreakEvent event) {
        if (event.isCancelled()) return;

        Player player = event.getPlayer();
        UUID uuid = player.getUniqueId();

        // 檢查玩家是否啟用了自動撿取
        if (!playerSettings.getOrDefault(uuid, false)) {
            return;
        }

        // 檢查是否需要Shift，如果需要則檢查玩家是否按住Shift
        boolean shiftRequired = playerShiftSettings.getOrDefault(uuid, true);
        if (shiftRequired && !player.isSneaking()) {
            return;
        }

        Location blockLocation = event.getBlock().getLocation();

        // 在下一個tick執行物品收集，因為掉落物是在事件後生成的
        new BukkitRunnable() {
            @Override
            public void run() {
                moveItemsToInventory(player, blockLocation);
            }
        }.runTaskLater(this, 1L); // 延遲1 tick
    }

    /**
     * 將指定位置的掉落物移動到玩家背包
     */
    private void moveItemsToInventory(Player player, Location coords) {
        World world = coords.getWorld();
        if (world == null) return;

        Collection<Entity> nearbyEntities = world.getNearbyEntities(
                coords.clone().add(0.5, 0.5, 0.5), // 中心點
                1.5, 1.5, 1.5 // x, y, z 範圍
        );

        boolean hasPickedUp = false;

        for (Entity entity : nearbyEntities) {
            if (!(entity instanceof Item)) continue; // 只處理 Item 實體

            Item item = (Item) entity;

            // 檢查物品是否在正確的撿取延遲狀態
            if (item.getPickupDelay() != 10) continue;

            ItemStack itemStack = item.getItemStack();

            try {
                // 嘗試添加到玩家背包
                if (addItemToInventory(player, itemStack)) {
                    // 成功添加到背包，移除掉落物
                    item.remove();
                    hasPickedUp = true;
                }
            } catch (Exception e) {
                item.remove();
                hasPickedUp = true;
            }
        }

        if (hasPickedUp) {
            playPickupSound(player);
        }
    }

    /**
     * 發送可點擊的設定介面
     */
    private void sendClickableSettings(Player player) {
        UUID uuid = player.getUniqueId();
        boolean enabled = playerSettings.getOrDefault(uuid, false);
        boolean soundEnabled = playerSoundSettings.getOrDefault(uuid, true);
        boolean shiftRequired = playerShiftSettings.getOrDefault(uuid, true);

        // 標題
        player.sendMessage(ChatColor.YELLOW + "=== AutoPickup 設定 ===");

        // 功能狀態行
        TextComponent functionLine = new TextComponent(ChatColor.YELLOW + "功能狀態: ");
        TextComponent functionStatus = new TextComponent();
        if (enabled) {
            functionStatus.setText(ChatColor.GREEN + "開啟");
            functionStatus.setClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/autopickup false"));
            functionStatus.setHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT,
                    new ComponentBuilder(ChatColor.RED + "點擊關閉自動撿取功能").create()));
        } else {
            functionStatus.setText(ChatColor.RED + "關閉");
            functionStatus.setClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/autopickup true"));
            functionStatus.setHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT,
                    new ComponentBuilder(ChatColor.GREEN + "點擊開啟自動撿取功能").create()));
        }
        functionLine.addExtra(functionStatus);
        player.spigot().sendMessage(functionLine);

        // 聲音效果行
        TextComponent soundLine = new TextComponent(ChatColor.YELLOW + "聲音效果: ");
        TextComponent soundStatus = new TextComponent();
        if (soundEnabled) {
            soundStatus.setText(ChatColor.GREEN + "開啟");
            soundStatus.setClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/autopickup sound false"));
            soundStatus.setHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT,
                    new ComponentBuilder(ChatColor.RED + "點擊關閉聲音效果").create()));
        } else {
            soundStatus.setText(ChatColor.RED + "關閉");
            soundStatus.setClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/autopickup sound true"));
            soundStatus.setHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT,
                    new ComponentBuilder(ChatColor.GREEN + "點擊開啟聲音效果").create()));
        }
        soundLine.addExtra(soundStatus);
        player.spigot().sendMessage(soundLine);

        // Shift需求行
        TextComponent shiftLine = new TextComponent(ChatColor.YELLOW + "需要Shift: ");
        TextComponent shiftStatus = new TextComponent();
        if (shiftRequired) {
            shiftStatus.setText(ChatColor.GREEN + "是");
            shiftStatus.setClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/autopickup shift false"));
            shiftStatus.setHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT,
                    new ComponentBuilder(ChatColor.RED + "點擊取消Shift需求").create()));
        } else {
            shiftStatus.setText(ChatColor.RED + "否");
            shiftStatus.setClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/autopickup shift true"));
            shiftStatus.setHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT,
                    new ComponentBuilder(ChatColor.GREEN + "點擊開啟Shift需求").create()));
        }
        shiftLine.addExtra(shiftStatus);
        player.spigot().sendMessage(shiftLine);

        // 使用說明
        player.sendMessage("");
        player.sendMessage(ChatColor.GRAY + "點擊上方狀態即可切換設定!");
        if (enabled) {
            if (shiftRequired) {
                player.sendMessage(ChatColor.GRAY + "按住Shift挖掘即可自動撿取物品");
            } else {
                player.sendMessage(ChatColor.GRAY + "直接挖掘即可自動撿取物品");
            }
        }
    }
    private void playPickupSound(Player player) {
        UUID uuid = player.getUniqueId();

        // 檢查玩家是否啟用聲音效果
        if (!playerSoundSettings.getOrDefault(uuid, true)) {
            return; // 聲音已關閉，不播放
        }

        // 播放撿取物品的聲音效果
        // 使用 Minecraft 原版的撿取聲音，音量 0.5，音調 1.0
        player.playSound(player.getLocation(), Sound.ENTITY_ITEM_PICKUP, 0.5f, 1.0f);

        // 可選：也可以使用其他聲音效果，比如：
        // player.playSound(player.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 0.3f, 1.2f);
        // player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.3f, 1.5f);
    }

    /**
     * 嘗試將物品添加到玩家背包
     * @param player 玩家
     * @param itemStack 要添加的物品
     * @return 是否成功添加
     */
    private boolean addItemToInventory(Player player, ItemStack itemStack) {
        PlayerInventory inventory = player.getInventory();
        Material itemType = itemStack.getType();
        int count = itemStack.getAmount();

        // 特殊處理潜影盒 (如果有組件數據則跳過堆疊邏輯)
        if (isShulkerBox(itemType) && hasComponents(itemStack)) {
            // 對於有組件的潜影盒，直接尋找空位
            int emptySlot = findEmptySlot(inventory);
            if (emptySlot != -1 && emptySlot < 36) { // 跳過快捷欄最後一格(#40)
                inventory.setItem(emptySlot, itemStack);
                return true;
            }
            return false;
        }

        // 嘗試堆疊到現有物品
        for (int slot = 0; slot < 36; slot++) { // 只檢查主背包，跳過快捷欄最後一格
            ItemStack existing = inventory.getItem(slot);
            if (existing != null && existing.getType() == itemType) {

                // 檢查NBT數據是否匹配
                if (itemStacksMatch(existing, itemStack)) {
                    int maxStack = itemType.getMaxStackSize();
                    int existingAmount = existing.getAmount();

                    if (existingAmount + count <= maxStack) {
                        // 可以完全堆疊
                        existing.setAmount(existingAmount + count);
                        return true;
                    }
                }
            }
        }

        // 尋找空的槽位
        int emptySlot = findEmptySlot(inventory);
        if (emptySlot != -1 && emptySlot < 36) { // 跳過快捷欄最後一格
            inventory.setItem(emptySlot, itemStack);
            return true;
        }

        return false; // 背包已滿
    }

    /**
     * 檢查是否為潜影盒
     */
    private boolean isShulkerBox(Material material) {
        return material.name().contains("SHULKER_BOX");
    }

    /**
     * 檢查物品是否有組件數據 (簡化版檢查)
     */
    private boolean hasComponents(ItemStack itemStack) {
        // 在PaperMC中，可以檢查物品是否有自定義數據
        return itemStack.hasItemMeta() &&
                (itemStack.getItemMeta().hasDisplayName() ||
                        itemStack.getItemMeta().hasLore() ||
                        itemStack.getItemMeta().hasCustomModelData());
    }

    /**
     * 檢查兩個物品堆是否可以堆疊 (類型和NBT數據匹配)
     */
    private boolean itemStacksMatch(ItemStack item1, ItemStack item2) {
        if (item1.getType() != item2.getType()) return false;

        // 簡化的NBT比較 - 比較ItemMeta
        if (item1.hasItemMeta() != item2.hasItemMeta()) return false;

        if (item1.hasItemMeta()) {
            return item1.getItemMeta().equals(item2.getItemMeta());
        }

        return true;
    }

    /**
     * 尋找背包中的空槽位
     */
    private int findEmptySlot(PlayerInventory inventory) {
        for (int slot = 0; slot < 36; slot++) { // 只檢查主背包
            if (inventory.getItem(slot) == null) {
                return slot;
            }
        }
        return -1; // 沒有空位
    }

    /**
     * Tab自動補全功能
     */
    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> completions = new ArrayList<>();

        if (args.length == 1) {
            // 第一個參數的補全選項
            List<String> options = Arrays.asList(
                    "true", "false", "sound", "shift"
            );

            String input = args[0].toLowerCase();
            for (String option : options) {
                if (option.toLowerCase().startsWith(input)) {
                    completions.add(option);
                }
            }
        } else if (args.length == 2) {
            // 第二個參數的補全選項
            String firstArg = args[0].toLowerCase();

            if (firstArg.equals("sound") || firstArg.equals("shift")) {
                List<String> booleanOptions = Arrays.asList(
                        "true", "false"
                );

                String input = args[1].toLowerCase();
                for (String option : booleanOptions) {
                    if (option.toLowerCase().startsWith(input)) {
                        completions.add(option);
                    }
                }
            }
        }

        return completions;
    }
}