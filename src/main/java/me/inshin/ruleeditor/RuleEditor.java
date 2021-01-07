/*
 * Copyright (c) 2020. InShin. All rights reserved.
 */

package me.inshin.ruleeditor;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.mojang.brigadier.tree.CommandNode;
import net.minecraft.server.v1_16_R3.CommandOp;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.Configuration;
import org.bukkit.configuration.MemoryConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.configuration.file.YamlConstructor;
import org.bukkit.entity.HumanEntity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;
import org.yaml.snakeyaml.Yaml;

import java.io.*;
import java.lang.reflect.InvocationTargetException;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

public final class RuleEditor extends JavaPlugin {
    private RuleInventoryHolder holder;

    @Override
    public void onLoad() {
        saveDefaultConfig();
//        File config = new File(getDataFolder(), "config.yml");
//        if (!config.exists()) {
//            InputStream resource = getResource("_config.yml");
//            if (resource != null) {
//                try (FileOutputStream fileOutputStream = new FileOutputStream(config)) {
//                    byte[] buffer = new byte[Math.min(resource.available(), 0x0FFF)];
//
//                    while (resource.read(buffer) != -1) {
//                        if (resource.available() < 0x0FFF) {
//                            buffer = new byte[resource.available()];
//                        }
//                        fileOutputStream.write(buffer);
//                    }
//                    resource.close();
//                } catch (IOException e) {
//                    e.printStackTrace();
//                }
//            }
//            saveResource("_config.yml", false);
//        }
    }

    @Override
    public void onEnable() {
        try {
            CommandDispatcher<Object> dispatcher = Utils.getCommandDispatcher(null);
            if (dispatcher == null) {
                // 反射获取失败，可能无法在这个版本的服务器上运行
                getServer().getConsoleSender().sendMessage("§4[RuleEditor] 如果你看到了这条信息，那真是太糟糕了。");
                getServer().getConsoleSender().sendMessage("§4[RuleEditor] 非常遗憾，本插件无法在当前服务器版本中运行。");
                getServer().getConsoleSender().sendMessage("§4[RuleEditor] 不妨试试在这里反馈请求兼容这个服务器。 https://github.com/AFoolLove/RuleEditor/issues");
                getServer().getConsoleSender().sendMessage("§4[RuleEditor] 当前了，自己修复编译也是个不错的选择。 https://github.com/AFoolLove/RuleEditor");

                getPluginLoader().disablePlugin(this);
                return;
            }
            this.holder = new RuleInventoryHolder(this);
            Bukkit.getPluginManager().registerEvents(this.holder, this);
            Bukkit.getScheduler().runTaskAsynchronously(this, () -> {
                try {
                    if (this.holder.tagStart()) {
                        this.holder.tagSet(new ItemStack(Material.BOOK), "text", "\u7231\u4f60\u54e6\u2606~");
                        this.holder.tagEnd();
                    }
                } catch (InvocationTargetException | IllegalAccessException e) {
                    e.printStackTrace();
                }
            });

            CommandNode<Object> gamerule = dispatcher.getRoot().getChild("gamerule");
            gamerule.addChild(LiteralArgumentBuilder.literal("editor")
                    .executes(commandContext -> {
                        Optional<CommandSender> senderOptional = IExecutes.getContextSender(commandContext);
                        senderOptional.ifPresent(sender -> {
                            if (!(sender instanceof Player)) {
                                sender.sendMessage("[RuleEditor] 你不是一个玩家。");
                                return;
                            }
                            this.holder.openRule((Player) sender);
                        });
                        return 0;
                    })
                    .then(LiteralArgumentBuilder.literal("reload")
                            .executes(commandContext -> {
                                Optional<CommandSender> senderOptional = IExecutes.getContextSender(commandContext);
                                senderOptional.ifPresent(sender -> {
                                    for (Inventory inventory : this.holder.inventories.values()) {
                                        inventory.clear();
                                        for (HumanEntity viewer : inventory.getViewers()) {
                                            viewer.sendMessage("[RuleEditor] 插件配置被重载。");
                                            viewer.closeInventory();
                                        }
                                    }
                                    this.holder.inventories.clear();

                                    saveDefaultConfig();
                                    reloadConfig();
                                    getConfig().setDefaults(new MemoryConfiguration());
                                    sender.sendMessage("[RuleEditor] 已重新加载配置。");
                                });
                                return 0;
                            }))
                    .then(LiteralArgumentBuilder.literal("disable")
                            .executes(commandContext -> {
                                Optional<CommandSender> senderOptional = IExecutes.getContextSender(commandContext);
                                senderOptional.ifPresent(sender -> {
                                    if (sender instanceof Player) {
                                        sender.sendMessage("[RuleEditor] 无法卸载本插件，请到后台执行该命令。");
                                        return;
                                    }
                                    sender.sendMessage("[RuleEditor] 卸载本插件...");
                                    getPluginLoader().disablePlugin(this);
                                    sender.sendMessage(String.format("[RuleEditor] %s", !isEnabled() ? "完成" : "失败"));
                                });
                                return 0;
                            }))
                    .then(RequiredArgumentBuilder.argument("world", StringArgumentType.word())
                            .executes(commandContext -> {
                                Optional<CommandSender> senderOptional = IExecutes.getContextSender(commandContext);
                                senderOptional.ifPresent(sender -> {
                                    if (!(sender instanceof Player)) {
                                        sender.sendMessage("[RuleEditor] 你不是一个玩家。");
                                        return;
                                    }
                                    Optional<String> worldOpt = IExecutes.getContextArgument(commandContext, "world", String.class);
                                    if (worldOpt.isPresent()) {
                                        World world = Bukkit.getWorld(worldOpt.get());
                                        if (world == null) {
                                            sender.sendMessage(String.format("[RuleEditor] 没有找到名为 %s 的世界。", worldOpt.get()));
                                            return;
                                        }
                                        this.holder.openRule((Player) sender, world);
                                    }
                                });
                                return 0;
                            })
                            .suggests((commandContext, suggestionsBuilder) -> {
                                switch (suggestionsBuilder.getRemaining()) {
                                    case "reload":
                                    case "help":
                                        return suggestionsBuilder.buildFuture();
                                }
                                Optional<String> worldOpt = IExecutes.getContextArgument(commandContext, "world", String.class);
                                List<String> collect = Bukkit.getWorlds().stream().map(World::getName).collect(Collectors.toList());
                                for (String suggest : collect) {
                                    if (!worldOpt.isPresent() || suggest.contains(worldOpt.get())) {
                                        suggestionsBuilder.suggest(suggest);
                                    }
                                }
                                return suggestionsBuilder.buildFuture();
                            }))
                    .then(LiteralArgumentBuilder.literal("help")
                            .executes(commandContext -> {
                                IExecutes.getContextSender(commandContext).ifPresent(sender -> {
                                    sender.sendMessage(String.format("[RuleEditor] -------------- Rule Editor(%s) Help", getDescription().getVersion()));
                                    sender.sendMessage(String.format("[RuleEditor] %s 显示当前信息", "/gamerule editor help"));
                                    sender.sendMessage(String.format("[RuleEditor] %s 打开当前世界的规则", "/gamerule editor"));
                                    sender.sendMessage(String.format("[RuleEditor] %s 重载配置文件", "/gamerule editor reload"));
                                    sender.sendMessage(String.format("[RuleEditor] %s 卸载本插件", "/gamerule editor disable"));
                                    sender.sendMessage(String.format("[RuleEditor] %s 加载指定世界的规则", "/gamerule editor [world]"));
                                    sender.sendMessage(String.format("[RuleEditor] -------------- Rule Editor Button action"));
                                    sender.sendMessage(String.format("[RuleEditor] %s 切换布尔值或为整数加一", "SHIFT + 左键"));
                                    sender.sendMessage(String.format("[RuleEditor] %s 为整数减一", "SHIFT + 右键"));
                                    sender.sendMessage(String.format("[RuleEditor] %s 在%d秒内输入自定义值", "中键", getConfig().getInt("rules-timeout", 15)));
                                    sender.sendMessage(String.format("[RuleEditor] %s 恢复为默认值", "Q键"));
                                });
                                return 0;
                            }))
                    .build()
            );
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Override
    public void onDisable() {
        this.holder.close();
        try {
            // 关闭已打开规则GUI的玩家的GUI
            for (Inventory inventory : this.holder.inventories.values()) {
                inventory.clear();
                for (HumanEntity viewer : inventory.getViewers()) {
                    viewer.closeInventory();
                }
            }
            this.holder.inventories.clear();

            // 移除命令
            CommandDispatcher<Object> dispatcher = Utils.getCommandDispatcher(null);
            if (dispatcher != null) {
                // 似乎，算了，反射
//                dispatcher.getRoot().getChild("gamerule").removeCommand("editor");

                CommandNode<Object> gamerule = dispatcher.getRoot().getChild("gamerule");
                CommandNode.class.getMethod("removeCommand", String.class).invoke(gamerule, "editor");
            } 
        } catch (Exception e) {
            e.printStackTrace();
            getServer().getConsoleSender().sendMessage("[RuleEditor] 命令可能移除失败了。");
        }
    }
}
