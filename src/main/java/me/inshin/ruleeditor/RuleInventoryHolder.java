/*
 * Copyright (c) 2020. InShin. All rights reserved.
 */

package me.inshin.ruleeditor;

import me.inshin.ruleeditor.prompt.BoolPrompt;
import me.inshin.ruleeditor.prompt.IntPrompt;
import org.bukkit.*;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.conversations.Conversable;
import org.bukkit.conversations.ConversationFactory;
import org.bukkit.entity.HumanEntity;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

public class RuleInventoryHolder implements InventoryHolder, Listener {
    final Plugin plugin;

    final Inventory inventory = Bukkit.createInventory(null, 9, "\u7231\u4f60\u54e6\u2606~");
    final Map<String, Inventory> inventories = new ConcurrentHashMap<>();

    public RuleInventoryHolder(@NotNull Plugin plugin) {
        this.plugin = plugin;
    }

    @NotNull
    @Override
    public Inventory getInventory() {
        return this.inventory;
    }

    @EventHandler
    public void onInventoryClose(InventoryCloseEvent event) {
        if (this == event.getInventory().getHolder()) {
            List<HumanEntity> viewers = event.getInventory().getViewers();
            viewers.remove(event.getPlayer());
            if (viewers.isEmpty()) {
                this.inventories.remove(event.getPlayer().getWorld().getName(), event.getInventory());
            }
            if (this.inventories.isEmpty()) {
                tagEnd();
            }
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onInventoryClick(InventoryClickEvent event) {
        if (this.inventories.isEmpty() || event.getInventory().getHolder() != this) {
            return;
        }

        if (event.getClick() == ClickType.SHIFT_LEFT // SHIFT + 左键 切换 boolean 或增加 integer
                || event.getClick() == ClickType.DROP // Q键 恢复默认值
                || event.getClick() == ClickType.SHIFT_RIGHT // SHIFT + 右键 减少 integer
                || event.getClick() == ClickType.MIDDLE // 中键 关闭GUI并启动会话，聊天框设置指定值
        ) {
            ItemStack currentItem = event.getCurrentItem();
            if (currentItem != null) {
                try {
                    String rule = tagGet(currentItem, "rule");
                    if (rule != null) {
                        GameRule<?> gameRule = GameRule.getByName(rule);
                        if (gameRule != null) {
                            HumanEntity whoClicked = event.getWhoClicked();
                            World world = whoClicked.getWorld();
                            Object gameRuleValue = null;
                            switch (event.getClick()) {
                                case SHIFT_LEFT:
                                    if (gameRule.getType() == Boolean.class) {
                                        gameRuleValue = !(Boolean) world.getGameRuleValue(gameRule);
                                        world.setGameRule((GameRule<Boolean>) gameRule, (Boolean) gameRuleValue);
                                    } else if (gameRule.getType() == Integer.class) {
                                        gameRuleValue = (Integer) world.getGameRuleValue(gameRule) + 1;
                                        world.setGameRule((GameRule<Integer>) gameRule, (Integer) gameRuleValue);
                                    }
                                    break;
                                case SHIFT_RIGHT:
                                    if (gameRule.getType() == Integer.class) {
                                        gameRuleValue = (Integer) world.getGameRuleValue(gameRule) - 1;
                                        world.setGameRule((GameRule<Integer>) gameRule, (Integer) gameRuleValue);
                                    }
                                    break;
                                case DROP:
                                    gameRuleValue = world.getGameRuleDefault(gameRule);
                                    world.setGameRule((GameRule) gameRule, gameRuleValue);
                                    break;
                                case MIDDLE:
                                    if (whoClicked instanceof Conversable) {
                                        whoClicked.closeInventory();
                                        conversation(this.plugin, (Conversable) whoClicked, world, gameRule);
                                    }
                                    break;
                            }

                            if (event.getClick() != ClickType.MIDDLE && gameRuleValue != null) {
                                updateItem(currentItem, "rule", rule,
                                        "%PLAYER%", whoClicked.getName()
                                        , "%RULE%", gameRule.getName()
                                        , "%WORLD_NAME%", world.getName()
                                        , "%VALUE%", gameRuleValue.toString()
                                        , "%DEFAULT_VALUE%", String.valueOf(world.getGameRuleDefault(gameRule)));
                            }
                        }
                    }
                } catch (InvocationTargetException | IllegalAccessException | NoSuchMethodException e) {
                    e.printStackTrace();
                }
            }
        }
        event.setCancelled(true);
    }

    public void close() {
        if (!this.inventories.isEmpty()) {
            for (Inventory inventory : this.inventories.values()) {
                List<HumanEntity> viewers = inventory.getViewers();
                if (!viewers.isEmpty()) {
                    viewers = new ArrayList<>(viewers);
                    for (HumanEntity viewer : viewers) {
                        viewer.closeInventory();
                    }
                }
            }
        }
    }

    private static void conversation(@NotNull Plugin plugin, @NotNull Conversable conversable, @NotNull World world, @NotNull GameRule<?> gameRule) {
        if (conversable.isConversing()) {
            return;
        }
        ConversationFactory conversationFactory = new ConversationFactory(plugin);
        conversationFactory.withLocalEcho(false);
        conversationFactory.withTimeout(15);
        if (gameRule.getType() == Boolean.class) {
            conversationFactory.withFirstPrompt(new BoolPrompt(world, (GameRule) gameRule));
        } else if (gameRule.getType() == Integer.class) {
            conversationFactory.withFirstPrompt(new IntPrompt(world, (GameRule) gameRule));
        } else {
            return;
        }
        conversationFactory.addConversationAbandonedListener(abandonedEvent -> {
            if (!abandonedEvent.gracefulExit()) {
                abandonedEvent.getContext().getForWhom().sendRawMessage("取消更改： " + gameRule.getName());
            }
        });
        conversationFactory.buildConversation(conversable).begin();
    }

    private void updateItem(ItemStack itemStack, String key, String value, String... variables) throws IllegalAccessException, NoSuchMethodException, InvocationTargetException {
        String rule = tagGet(itemStack, key);
        if (rule != null) {
            ConfigurationSection ruleSection = this.plugin.getConfig().getConfigurationSection("rules." + rule);
            if (ruleSection != null) {
                String displayName = ruleSection.getString("title");
                if (displayName != null) {
                    for (int i = 0; i < variables.length; i += 2) {
                        displayName = displayName.replaceAll(variables[i], variables[i + 1]);
                    }
                    displayName = ChatColor.translateAlternateColorCodes('&', displayName);
                }
                List<String> descriptions = ruleSection.getStringList("description");
                for (int i = 0, size = descriptions.size(); i < size; i++) {
                    String description = descriptions.get(i);
                    if (description.isEmpty()) {
                        descriptions.set(i, "");
                        continue;
                    }
                    for (int j = 0; j < variables.length; j += 2) {
                        description = description.replaceAll(variables[j], variables[j + 1]);
                    }
                    description = ChatColor.translateAlternateColorCodes('&', description);
                    descriptions.set(i, description);
                }
                ItemMeta itemMeta = itemStack.getItemMeta();
                if (itemMeta != null) {
                    itemMeta.setDisplayName(displayName);
                    itemMeta.setLore(descriptions);
                    itemStack.setItemMeta(itemMeta);
                }
            }
        } else {
            tagSet(itemStack, key, value);
        }
    }

    public void openRule(@NotNull HumanEntity entity, @NotNull World world) {
        String worldName = world.getName();
        Inventory inventory = this.inventories.get(worldName);
        if (inventory != null) {
            entity.openInventory(inventory);
            return;
        }

        FileConfiguration config = this.plugin.getConfig();
        ConfigurationSection rules = config.getConfigurationSection("rules");
        if (rules != null && tagStart()) {
            int invRow = config.getInt("rules-row", 3);
            String invTitle = Optional.ofNullable(config.getString("rules-title")).orElse("Game Rule");
            if (!invTitle.isEmpty()) {
                invTitle = invTitle.replaceAll("%WORLD_NAME%", worldName);
                invTitle = ChatColor.translateAlternateColorCodes('&', invTitle);
            } else {
                invTitle = "Game Rule";
            }

            List<ItemStack> ruleItems = new ArrayList<>(GameRule.values().length);

            try {
                for (GameRule<?> gameRule : GameRule.values()) {
                    ConfigurationSection ruleSection = rules.getConfigurationSection(gameRule.getName());
                    if (ruleSection == null) {
                        continue;
                    }

                    String title = Optional.ofNullable(ruleSection.getString("title")).orElse(gameRule.getName());
                    String item = Optional.ofNullable(ruleSection.getString("item")).orElse("minecraft:book");
                    List<String> descriptions = new ArrayList<>(ruleSection.getStringList("description"));

                    String value = String.valueOf(world.getGameRuleValue(gameRule));
                    String defValue = String.valueOf(world.getGameRuleDefault(gameRule));

                    if (!title.isEmpty()) {
                        title = title.replaceAll("%PLAYER%", entity.getName());
                        title = title.replaceAll("%RULE%", gameRule.getName());
                        title = title.replaceAll("%VALUE%", value);
                        title = title.replaceAll("%DEFAULT_VALUE%", defValue);
                        title = title.replaceAll("%WORLD_NAME%", worldName);
                        title = ChatColor.translateAlternateColorCodes('&', title);
                    } else {
                        title = gameRule.getName();
                    }

                    for (int i = 0; i < descriptions.size(); i++) {
                        String description = descriptions.get(i);
                        if (description.isEmpty()) {
                            descriptions.set(i, "");
                            continue;
                        }
                        description = description.replaceAll("%PLAYER%", entity.getName());
                        description = description.replaceAll("%RULE%", gameRule.getName());
                        description = description.replaceAll("%VALUE%", value);
                        description = description.replaceAll("%DEFAULT_VALUE%", defValue);
                        description = description.replaceAll("%WORLD_NAME%", worldName);
                        description = ChatColor.translateAlternateColorCodes('&', description);
                        descriptions.set(i, description);
                    }
                    Material material = Material.getMaterial(item.toUpperCase());
                    ItemStack itemStack = new ItemStack(material != null ? material : Material.BOOK);
                    ItemMeta itemMeta = itemStack.getItemMeta();
                    if (itemMeta != null) {
                        itemMeta.setDisplayName(title);
                        itemMeta.setLore(descriptions);
                        itemStack.setItemMeta(itemMeta);
                    }
                    ruleItems.add(tagSet(itemStack, "rule", gameRule.getName()));
                }
            } catch (IllegalAccessException | InvocationTargetException e) {
                e.printStackTrace();
            }
            int size = ruleItems.size();
            if (invRow == 0 || invRow * 9 < ruleItems.size()) {
                invRow = size / 9;
                if (size % 9 != 0) {
                    invRow++;
                }
            }
            inventory = Bukkit.createInventory(this, invRow * 9, invTitle);
            inventory.setContents(ruleItems.toArray(new ItemStack[0]));
            tagEnd();
        }
        if (inventory == null) {
            inventory = Bukkit.createInventory(this, InventoryType.CHEST);
        }
        this.inventories.put(worldName, inventory);
        entity.openInventory(inventory);
    }

    public void openRule(@NotNull HumanEntity entity) {
        openRule(entity, entity.getWorld());
    }


    Method asNMSCopyMethod;
    Method getOrCreateTagMethod;
    Method setStringMethod;
    Method asBukkitCopyMethod;

    protected boolean tagStart() {
        if (asNMSCopyMethod != null
                && getOrCreateTagMethod != null
                && setStringMethod != null
                && asBukkitCopyMethod != null) {
            return true;
        }
        try {
            asNMSCopyMethod = Utils.ITEM_STACK_CLAZZ.getMethod("asNMSCopy", ItemStack.class);
            getOrCreateTagMethod = Utils.MC_ITEM_STACK_CLAZZ.getMethod("getOrCreateTag");
            setStringMethod = Utils.MC_NBT_TAG_COMPOUND_CLAZZ.getMethod("setString", String.class, String.class);
            asBukkitCopyMethod = Utils.ITEM_STACK_CLAZZ.getMethod("asBukkitCopy", Utils.MC_ITEM_STACK_CLAZZ);
            return true;
        } catch (NoSuchMethodException e) {
            e.printStackTrace();
        }
        tagEnd();
        return false;
    }

    protected void tagEnd() {
        asNMSCopyMethod = null;
        getOrCreateTagMethod = null;
        setStringMethod = null;
        asBukkitCopyMethod = null;
    }

    protected ItemStack tagSet(ItemStack itemStack, String key, String value) throws InvocationTargetException, IllegalAccessException {
        Object mcItem = asNMSCopyMethod.invoke(null, itemStack);
        Object mcNbt = getOrCreateTagMethod.invoke(mcItem);
        setStringMethod.invoke(mcNbt, key, value);
        return (ItemStack) asBukkitCopyMethod.invoke(null, mcItem);
    }

    @Nullable
    protected String tagGet(ItemStack itemStack, String key) throws InvocationTargetException, IllegalAccessException, NoSuchMethodException {
        Method asNMSCopyMethod = Utils.ITEM_STACK_CLAZZ.getMethod("asNMSCopy", ItemStack.class);
        Method getOrCreateTagMethod = Utils.MC_ITEM_STACK_CLAZZ.getMethod("getOrCreateTag");
        Object mcItem = asNMSCopyMethod.invoke(null, itemStack);
        Object mcNbt = getOrCreateTagMethod.invoke(mcItem);
        Method getStringMethod = Utils.MC_NBT_TAG_COMPOUND_CLAZZ.getMethod("getString", String.class);
        String value = String.valueOf(getStringMethod.invoke(mcNbt, key));
        return value.isEmpty() ? null : value;
    }
}
