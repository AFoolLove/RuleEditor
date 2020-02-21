/*
 * Copyright (c) 2020. InShin. All rights reserved.
 */

package me.inshin.ruleeditor;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Entity;
import org.jetbrains.annotations.NotNull;

import java.lang.reflect.InvocationTargetException;
import java.util.Optional;

@FunctionalInterface
public interface IExecutes<S> {
    int run(@NotNull CommandContext<Object> context, @NotNull Optional<S> senderOpt) throws CommandSyntaxException;

    /**
     * 创建一个命令解析器，只能转换为 {@link org.bukkit.entity.Entity} 能转换的类
     *
     * @param clazz    要转换的类
     * @param executes 命令
     * @param <T>      类泛型
     * @return 命令
     */
    @NotNull
    static <T> Command<Object> executes(@NotNull final Class<T> clazz, @NotNull final IExecutes<T> executes) {
        return context -> {
            if (clazz == CommandSender.class) {
                return executes.run(context, (Optional<T>) getContextSender(context));
            }
            Optional<Entity> contextEntity = getContextEntity(context);
            if (contextEntity.isPresent()) {
                return executes.run(context, (Optional<T>) contextEntity);
            }
            return executes.run(context, Optional.empty());
        };
    }

    /**
     * 获取到执行命令的实体
     * 在 suggests 中也能获取到
     *
     * @param context Context
     * @return 执行命令的实体
     */
    static Optional<Entity> getContextEntity(@NotNull CommandContext<Object> context) {
        Class<?> sourceClazz = context.getSource().getClass();
        try {
            // object net.minecraft.server.v1_15_R1.CommandListenerWrapper#getEntity
            Object entity = sourceClazz.getMethod("getEntity").invoke(context.getSource());
            if (entity instanceof Entity) {
                // object net.minecraft.server.v1_15_R1.Entity#getBukkitEntity
                return Optional.ofNullable((Entity) entity.getClass().getMethod("getBukkitEntity").invoke(entity));
            }
        } catch (IllegalAccessException | InvocationTargetException | NoSuchMethodException e) {
            e.printStackTrace();
        }
        return Optional.empty();
    }

    /**
     * 获取到命令发送者
     * 在 suggests 中也能获取到
     *
     * @param context Context
     * @return 命令发送者
     */
    @NotNull
    static Optional<CommandSender> getContextSender(@NotNull CommandContext<Object> context) {
        try {
            Class<?> sourceClazz = context.getSource().getClass();
            // object net.minecraft.server.v1_15_R1.CommandListenerWrapper#getBukkitSender
            Object bukkitSender = sourceClazz.getMethod("getBukkitSender").invoke(context.getSource());
            if (bukkitSender instanceof CommandSender) {
                return Optional.of((CommandSender) bukkitSender);
            }
        } catch (IllegalAccessException | InvocationTargetException | NoSuchMethodException e) {
            e.printStackTrace();
        }
        return Optional.empty();
    }

    /**
     * 获取参数的值，并转换成指定的类型
     * <p>
     * 说实话这个挺糟糕的，在原方法中
     * 参数还没有值（玩家正打算输入）的时候，这时获取参数值无效（玩家还没输入，还是空的）就会直接抛出异常
     * 还不给判断有没有的方法，我当时就***了
     *
     * @param context Context
     * @param name    参数名
     * @param clazz   参数值的类型
     * @return 参数的值
     */
    static <T> Optional<T> getContextArgument(@NotNull CommandContext<Object> context, @NotNull String name, Class<T> clazz) {
        try {
            return Optional.of(context.getArgument(name, clazz));
        } catch (IllegalArgumentException e) {
            if (!e.getMessage().startsWith("No such argument")) {
                e.printStackTrace();
            }
        }
        return Optional.empty();
    }
}

