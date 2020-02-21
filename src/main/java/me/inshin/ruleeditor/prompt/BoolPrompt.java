/*
 * Copyright (c) 2020. InShin. All rights reserved.
 */

package me.inshin.ruleeditor.prompt;

import org.bukkit.GameRule;
import org.bukkit.World;
import org.bukkit.conversations.BooleanPrompt;
import org.bukkit.conversations.ConversationContext;
import org.bukkit.conversations.Prompt;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class BoolPrompt extends BooleanPrompt {
    private final World world;
    private final GameRule<Object> gameRule;
    private boolean first = true;

    public BoolPrompt(@NotNull World world, @NotNull GameRule<Object> gameRule) {
        this.world = world;
        this.gameRule = gameRule;
    }

    @Override
    protected @Nullable Prompt acceptValidatedInput(@NotNull ConversationContext context, boolean input) {
        context.getForWhom().sendRawMessage(String.format("%s 变更值为 §a%s", this.gameRule.getName(), input));
        this.world.setGameRule(this.gameRule, input);
        return END_OF_CONVERSATION;
    }

    @Override
    public @NotNull String getPromptText(@NotNull ConversationContext context) {
        if (this.first) {
            this.first = false;
            context.getForWhom().sendRawMessage(String.format("§f%s: §e§n%5s§f/§a%5s",
                    gameRule.getName(),
                    this.world.getGameRuleValue(this.gameRule),
                    this.world.getGameRuleDefault(this.gameRule)
            ));
        }
        return "请输入一个布尔类型的值（false|true）：";
    }
}
