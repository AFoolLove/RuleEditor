/*
 * Copyright (c) 2020. InShin. All rights reserved.
 */

package me.inshin.ruleeditor;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.mojang.brigadier.tree.CommandNode;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

import java.lang.reflect.InvocationTargetException;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

public final class RuleEditor extends JavaPlugin {
    private RuleInventoryHolder holder;

    @Override
    public void onLoad() {
        saveDefaultConfig();
    }

    @Override
    public void onEnable() {
        try {
            CommandDispatcher<Object> dispatcher = Utils.getCommandDispatcher(null);
            if (dispatcher == null) {
                // 反射获取失败，可能无法在这个版本的服务器上运行
                getServer().getConsoleSender().sendMessage("§4[RuleEditor] reflection failed.");
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
                                    reloadConfig();
                                    sender.sendMessage("[RuleEditor] 已重新加载配置。");
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
                                Optional<CommandSender> senderOptional = IExecutes.getContextSender(commandContext);
                                senderOptional.ifPresent(sender -> {
                                    sender.sendMessage("[RuleEditor] -------------- Rule Editor Help");
                                    sender.sendMessage(String.format("[RuleEditor] %s 打开GUI", "/gamerule editor"));
                                    sender.sendMessage(String.format("[RuleEditor] %s 重载配置文件", "/gamerule editor reload"));
                                    sender.sendMessage(String.format("[RuleEditor] %s 加载指定世界的规则", "/gamerule editor [world]"));
                                    sender.sendMessage(String.format("[RuleEditor] %s 显示当前信息", "/gamerule editor help"));
                                    sender.sendMessage("[RuleEditor] -------------- Rule Editor Button action");
                                    sender.sendMessage(String.format("[RuleEditor] %s 切换布尔值或为整数加一", "SHIFT + 左键"));
                                    sender.sendMessage(String.format("[RuleEditor] %s 为整数减一", "SHIFT + 右键"));
                                    sender.sendMessage(String.format("[RuleEditor] %s 在15秒内输入自定义值", "中键"));
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
            CommandDispatcher<Object> dispatcher = Utils.getCommandDispatcher(null);
            if (dispatcher != null) {
                dispatcher.getRoot().getChild("gamerule").removeCommand("editor");
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
