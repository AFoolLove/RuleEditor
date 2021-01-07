# RuleEditor  
支持版本： ```1.13 - 1.16.4```  更高版本未测试  
构建环境： ```java 8、spigot 1.16.4```

## 描述  
```RuleGame``` 可以以物品GUI的方式修改世界的游戏规则（gamerule）  
- GUI的操作方式  
```SHIFT + 鼠标左键``` 切换布尔值或增加整数值（+1）  
```SHIFT + 鼠标右键``` 减少整数值（-1）  
```鼠标中间``` 关闭GUI并启动自定义值会话，聊天框设置指定值（15秒内输入）  
```Q键（丢出物品）``` 恢复值为默认值  
GUI物品信息可根据规则自定义

# 命令  
命令插入到原版的命令中，权限依赖于原版权限  
```/gamerule editor``` 打开编辑当前世界规则的GUI  
```/gamerule editor reload``` 重新加载配置文件  
```/gamerule editor disable``` (v1.1) 卸载本插件，只能非玩家执行  
```/gamerule editor help``` 显示命令和GUI操作方式  
```/gamerule editor [world]``` 打开编辑指定世界规则的GUI ```[world]``` 为世界名称

# 配置文件  
```config.yml```  
规则描述来源于 [WIKI](https://minecraft-zh.gamepedia.com/%E5%91%BD%E4%BB%A4/gamerule)  
默认配置为已写好的一个通过 WIKI 规则的描述（虽然不好看）

```yaml
# 支持 & 转 §
# 仅支持单个变量 '%WORLD_NAME%
rules-title: '%WORLD_NAME% 的游戏规则'

# GUI 格子大小，0 或小于规则数量为自适应，最大为 6(6*9)
rules-row: 3

# 支持 & 转 §
# 变量 %RULE% 当前规则名称
# 变量 %WORLD_NAME% 世界名
# 变量 %VALUE% 当前规则的值
# 变量 %DEFAULT_VALUE% 当前规则的默认值
# 变量 %PLAYER% 当前玩家
# 支持以上变量的节点
# rules.<rule>.title
# rules.<rule>.description

rules:
  # rule 为规则名称
  <rule>:
    # 该规则的物品显示名称
    title: '§f%RULE%'
    # 该规则的物品类型，book 为 书
    item: 'book'
    # 该规则的物品描述
    description:
      - '§7%RULE%'
```

# 图片  
```*图中游戏版本为1.13```  
![image](./screenshots/screenshots2.png)
![image](./screenshots/screenshots3.png)


# 更新
### v1.1
0. 新增配置文件属性 ```rules-timeout``` 变更使用 ```中键``` 自定义数值时的等待时间，默认15秒  
0. 新增配置文件属性 ```rules-show-all``` 是否显示未配置的规则，可自定义物品  
0. 新增配置文件属性 ```rules-out-item``` 未配置规则的物品  
0. 新增命令 ```/gamerule editor disable``` 卸载本插件，卸载后未退出的玩家仍然持有命令提示，但没有命令执行  
   *只能非玩家执行  
0. 修复使用命令重载配置时，没有配置文件时不会重新生成配置文件  
0. 修复如果玩家不在所编辑的地图时，无法正确移除GUI数据  
0. 修复未配置（有效）的规则会读取默认配置显示  
0. 补全2个 ```1.16.4``` 规则配置  

适配到了 ```1.16.4```  

# Copyright  
```Copyright (c) 2020. InShin. All rights reserved.```
