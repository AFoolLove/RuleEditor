/*
 * Copyright (c) 2020. InShin. All rights reserved.
 */

package me.inshin.ruleeditor;

import com.mojang.brigadier.CommandDispatcher;
import org.bukkit.Bukkit;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

public class Utils {
    public static final Class<?> ITEM_STACK_CLAZZ; // class org.bukkit.craftbukkit.v1_16_R3.CraftItemStack

    public static final Class<?> MC_SERVER_CLAZZ; // class net.minecraft.server.v1_16_R3.MinecraftServer
    public static final Class<?> MC_NBT_TAG_COMPOUND_CLAZZ; // class net.minecraft.server.v1_16_R3.NBTTagCompound
    public static final Class<?> MC_ITEM_STACK_CLAZZ; // class net.minecraft.server.v1_16_R3.ItemStack

    static {
        try {
            Class<?> serverClazz = Bukkit.getServer().getClass();
            // package org.bukkit.craftbukkit.v1_16_R3
            String _package = serverClazz.getPackage().getName();
            // class org.bukkit.craftbukkit.v1_16_R3.CraftItemStack
            ITEM_STACK_CLAZZ = serverClazz.getClassLoader().loadClass(_package + ".inventory.CraftItemStack");

            // package net.minecraft.server.v1_16_R3
            _package = _package.replaceFirst("org.bukkit.craftbukkit", "net.minecraft.server");
            // class net.minecraft.server.v1_16_R3.MinecraftServer
            MC_SERVER_CLAZZ = serverClazz.getClassLoader().loadClass(_package + ".MinecraftServer");
            // class net.minecraft.server.v1_16_R3.NBTTagCompound
            MC_NBT_TAG_COMPOUND_CLAZZ = serverClazz.getClassLoader().loadClass(_package + ".NBTTagCompound");
            // class net.minecraft.server.v1_16_R3.ItemStack
            MC_ITEM_STACK_CLAZZ = serverClazz.getClassLoader().loadClass(_package + ".ItemStack");
        } catch (ClassNotFoundException e) {
            throw new RuntimeException(e);
        }
    }


    @Nullable
    @SuppressWarnings("unchecked")
    public static CommandDispatcher<Object> getCommandDispatcher(@Nullable Object mcCommandDispatcher) {
        try {
            if (mcCommandDispatcher == null) {
                mcCommandDispatcher = getMcCommandDispatcher(false);
            }
            Class<?> mcCommandDispatcherClazz = mcCommandDispatcher.getClass();
            for (Method method : mcCommandDispatcherClazz.getDeclaredMethods()) {
                if (method.getReturnType().isAssignableFrom(CommandDispatcher.class)) {
                    return (CommandDispatcher<Object>) method.invoke(mcCommandDispatcher);
                }
            }
        } catch (IllegalAccessException | InvocationTargetException | NoSuchMethodException | NoSuchFieldException e) {
            e.printStackTrace();
        }
        return null;
    }

    @NotNull
    public static Object getMcCommandDispatcher(boolean vanilla) throws NoSuchMethodException, InvocationTargetException, IllegalAccessException, NoSuchFieldException {
        vanilla = true; // oh... 1.16及以上只能设置为true
        // object org.bukkit.craftbukkit.v1_16_R3.CraftServer
        Object mcServer = Bukkit.getServer().getClass().getMethod("getServer").invoke(Bukkit.getServer());
        // object net.minecraft.server.v1_16_R3.CommandDispatcher
        if (vanilla) {
            return MC_SERVER_CLAZZ.getField("vanillaCommandDispatcher").get(mcServer);
        }
        return MC_SERVER_CLAZZ.getField("commandDispatcher").get(mcServer);
    }
}
