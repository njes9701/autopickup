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
import org.bukkit.ChatColor;
import org.bukkit.Sound;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

// Dialog API imports
import io.papermc.paper.dialog.Dialog;
import io.papermc.paper.registry.data.dialog.DialogRegistryEntry;
import io.papermc.paper.registry.data.dialog.DialogBase;
import io.papermc.paper.registry.data.dialog.ActionButton;
import io.papermc.paper.registry.data.dialog.action.DialogAction;
import io.papermc.paper.registry.data.dialog.type.DialogType;
import io.papermc.paper.registry.data.dialog.type.MultiActionType;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.event.ClickEvent;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.List;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.io.File;
import java.io.IOException;

public class Autopickup extends JavaPlugin implements Listener, CommandExecutor, TabCompleter {

    private final Map<UUID, Boolean> playerSettings = new HashMap<>();
    private final Map<UUID, Boolean> playerSoundSettings = new HashMap<>(); // 聲音設定
    private final Map<UUID, Boolean> playerShiftSettings = new HashMap<>(); // Shift設定
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
            // 顯示 Dialog 設定介面
            showDialogSettings(player);
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
            showDialogSettings(player);
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
            showDialogSettings(player);
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
            showDialogSettings(player);
            return true;
        }

        player.sendMessage(ChatColor.RED + "用法:");
        player.sendMessage(ChatColor.RED + "  /autopickup [true/false]");
        player.sendMessage(ChatColor.RED + "  /autopickup sound [true/false]");
        player.sendMessage(ChatColor.RED + "  /autopickup shift [true/false]");
        return true;
    }

    /**
     * 顯示 Dialog 設定介面（採用簡潔且與測試一致的 Builder 寫法）
     */
    private void showDialogSettings(Player player) {
        try {


            UUID uuid = player.getUniqueId();
            boolean enabled = playerSettings.getOrDefault(uuid, false);
            boolean soundEnabled = playerSoundSettings.getOrDefault(uuid, true);
            boolean shiftRequired = playerShiftSettings.getOrDefault(uuid, true);

            Dialog settingsDialog = Dialog.create(builderFactory -> {
                try {
                    DialogRegistryEntry.Builder builder = builderFactory.empty();

                    List<ActionButton> buttons = new ArrayList<>();

                    // 功能開關
                    Component functionText = enabled
                            ? Component.text("功能狀態: ", NamedTextColor.WHITE).append(Component.text("開啟", NamedTextColor.GREEN))
                            : Component.text("功能狀態: ", NamedTextColor.WHITE).append(Component.text("關閉", NamedTextColor.RED));
                    String functionCmd = enabled ? "/autopickup false" : "/autopickup true";
                    buttons.add(ActionButton.builder(functionText)
                            .action(DialogAction.staticAction(
                                    ClickEvent.clickEvent(ClickEvent.Action.RUN_COMMAND, functionCmd)))
                            .build());

                    // 聲音開關
                    Component soundText = soundEnabled
                            ? Component.text("聲音效果: ", NamedTextColor.WHITE).append(Component.text("開啟", NamedTextColor.GREEN))
                            : Component.text("聲音效果: ", NamedTextColor.WHITE).append(Component.text("關閉", NamedTextColor.RED));
                    String soundCmd = soundEnabled ? "/autopickup sound false" : "/autopickup sound true";
                    buttons.add(ActionButton.builder(soundText)
                            .action(DialogAction.staticAction(
                                    ClickEvent.clickEvent(ClickEvent.Action.RUN_COMMAND, soundCmd)))
                            .build());

                    // Shift 需求
                    Component shiftText = shiftRequired
                            ? Component.text("需要Shift: ", NamedTextColor.WHITE).append(Component.text("是", NamedTextColor.GREEN))
                            : Component.text("需要Shift: ", NamedTextColor.WHITE).append(Component.text("否", NamedTextColor.RED));
                    String shiftCmd = shiftRequired ? "/autopickup shift false" : "/autopickup shift true";
                    buttons.add(ActionButton.builder(shiftText)
                            .action(DialogAction.staticAction(
                                    ClickEvent.clickEvent(ClickEvent.Action.RUN_COMMAND, shiftCmd)))
                            .build());

                    // 三欄並排
                    MultiActionType dialogType = DialogType.multiAction(buttons, null, 3);

                    DialogBase dialogBase = DialogBase.builder(Component.text("AutoPickup 設定", NamedTextColor.YELLOW))
                            .canCloseWithEscape(true)
                            .pause(false)
                            .afterAction(DialogBase.DialogAfterAction.CLOSE)
                            .body(Collections.emptyList())
                            .inputs(Collections.emptyList())
                            .build();

                    builder.base(dialogBase).type(dialogType);

                } catch (Exception e) {
                    getLogger().severe("建立設定對話框時發生錯誤: " + e.getMessage());
                    e.printStackTrace();
                }
            });

            showDialogToPlayer(player, settingsDialog);

        } catch (Exception e) {
            getLogger().warning("Dialog API 不可用或顯示失敗，回退到聊天介面: " + e.getMessage());
            sendClickableSettings(player);
        }
    }

    /**
     * 檢查是否支持 Dialog API
     */
    private boolean hasDialogSupport() {
        try {
            Player.class.getMethod("showDialog", Dialog.class);
            return true;
        } catch (NoSuchMethodException e) {
            return false;
        }
    }

    /**
     * 顯示對話框給玩家
     */
    private void showDialogToPlayer(Player player, Dialog dialog) {
        try {
            player.showDialog(dialog);
        } catch (Exception e) {
            getLogger().warning("無法使用對話框 API: " + e.getMessage());
            getLogger().info("回退到傳統聊天界面");
            sendClickableSettings(player);
        }
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
     * 發送可點擊的設定介面 (保留作為後備方案)
     */
    private void sendClickableSettings(Player player) {
        UUID uuid = player.getUniqueId();
        boolean enabled = playerSettings.getOrDefault(uuid, false);
        boolean soundEnabled = playerSoundSettings.getOrDefault(uuid, true);
        boolean shiftRequired = playerShiftSettings.getOrDefault(uuid, true);

        // 使用 Adventure API 發送消息
        player.sendMessage(Component.text("=== AutoPickup 設定 ===", NamedTextColor.YELLOW));

        // 功能狀態行
        Component functionStatus = enabled
                ? Component.text("開啟", NamedTextColor.GREEN)
                .clickEvent(ClickEvent.runCommand("/autopickup false"))
                .hoverEvent(Component.text("點擊關閉自動撿取功能", NamedTextColor.RED))
                : Component.text("關閉", NamedTextColor.RED)
                .clickEvent(ClickEvent.runCommand("/autopickup true"))
                .hoverEvent(Component.text("點擊開啟自動撿取功能", NamedTextColor.GREEN));

        Component functionLine = Component.text("功能狀態: ", NamedTextColor.YELLOW)
                .append(functionStatus);
        player.sendMessage(functionLine);

        // 聲音效果行
        Component soundStatus = soundEnabled
                ? Component.text("開啟", NamedTextColor.GREEN)
                .clickEvent(ClickEvent.runCommand("/autopickup sound false"))
                .hoverEvent(Component.text("點擊關閉聲音效果", NamedTextColor.RED))
                : Component.text("關閉", NamedTextColor.RED)
                .clickEvent(ClickEvent.runCommand("/autopickup sound true"))
                .hoverEvent(Component.text("點擊開啟聲音效果", NamedTextColor.GREEN));

        Component soundLine = Component.text("聲音效果: ", NamedTextColor.YELLOW)
                .append(soundStatus);
        player.sendMessage(soundLine);

        // Shift需求行
        Component shiftStatus = shiftRequired
                ? Component.text("是", NamedTextColor.GREEN)
                .clickEvent(ClickEvent.runCommand("/autopickup shift false"))
                .hoverEvent(Component.text("點擊取消Shift需求", NamedTextColor.RED))
                : Component.text("否", NamedTextColor.RED)
                .clickEvent(ClickEvent.runCommand("/autopickup shift true"))
                .hoverEvent(Component.text("點擊開啟Shift需求", NamedTextColor.GREEN));

        Component shiftLine = Component.text("需要Shift: ", NamedTextColor.YELLOW)
                .append(shiftStatus);
        player.sendMessage(shiftLine);

        // 使用說明
        player.sendMessage(Component.empty());
        player.sendMessage(Component.text("點擊上方狀態即可切換設定!", NamedTextColor.GRAY));
        if (enabled) {
            if (shiftRequired) {
                player.sendMessage(Component.text("按住Shift挖掘即可自動撿取物品", NamedTextColor.GRAY));
            } else {
                player.sendMessage(Component.text("直接挖掘即可自動撿取物品", NamedTextColor.GRAY));
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
        player.playSound(player.getLocation(), Sound.ENTITY_ITEM_PICKUP, 0.5f, 1.0f);
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
