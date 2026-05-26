# 开发计划待确认问题

在制定详细开发计划之前，需要你确认以下 5 个关键问题：

---

## Q1: 项目目录结构

文档推荐 monorepo 结构：
```
Sanguosha-WebArena
├── sanguosha-webarena-client    前端项目
├── sanguosha-webarena-server    后端项目
├── docs                         项目文档
└── README.md
```

**你的选择：**
- [ ] A: 就按这个结构开始创建项目
- [ ] B: 结构有调整（请说明）

---

## Q2: 技术栈确认

| 端   | 技术                             |
| ---- | -------------------------------- |
| 前端 | Vue 3 + Vite + TypeScript        |
| 后端 | Spring Boot 3 + Java + Maven     |
| 数据库 | MySQL + MyBatis-Plus            |
| 缓存 | Redis（或初版先用内存）          |
| 认证 | JWT                              |

**你的选择：**
- [ ] A: 技术栈确认，按此方案执行
- [ ] B: 有调整（请说明）

---

## Q3: 初版 MVP 范围

文档第 17 章定义的初版范围：
```
用户登录注册
大厅房间列表
创建房间/加入房间
玩家准备
房间聊天
1v1 游戏开始
摸牌、出牌、弃牌、回合切换
杀、闪、桃、无中生有
胜负判定
对局记录保存
```

**你的选择：**
- [ ] A: 初版就做这些，不接受更多
- [ ] B: 需要增减功能（请说明）

---

## Q4: Redis 使用方案

文档第 8 章提到初版可以先使用 `ConcurrentHashMap` 管理房间和游戏状态，等核心功能完成后再迁移到 Redis。

**你的选择：**
- [ ] A: 初版先用内存（ConcurrentHashMap），简化开发
- [ ] B: 直接用 Redis，一步到位

---

## Q5: 当前优先级

**你的选择：**
- [ ] A: 我先写完整的开发计划文档，你审核通过后再开始编码
- [ ] B: 直接开始搭建项目骨架，边做边调整，有问题再问你

---

请在每个问题后面标注你的选择，或者直接修改/









---

## Q1: 项目目录结构

选择：**A**

就按文档推荐的 monorepo 结构开始创建项目：

```text
Sanguosha-WebArena
├── sanguosha-webarena-client
├── sanguosha-webarena-server
├── docs
└── README.md
```

这样前后端和文档都放在同一个仓库里，后续管理、提交和展示都比较方便。

---

## Q2: 技术栈确认

选择：**A**

技术栈确认，按以下方案执行：

```text
前端：Vue 3 + Vite + TypeScript
后端：Spring Boot 3 + Java + Maven
数据库：MySQL + MyBatis-Plus
缓存：Redis 或初版先用内存
认证：JWT
```

后端重点先完成 WebSocket 实时通信、房间管理、游戏状态机和基础卡牌结算。

---

## Q3: 初版 MVP 范围

选择：**A**

初版就做文档第17章定义的范围，不额外增加功能：

```text
用户登录注册
大厅房间列表
创建房间/加入房间
玩家准备
房间聊天
1v1 游戏开始
摸牌、出牌、弃牌、回合切换
杀、闪、桃、无中生有
胜负判定
对局记录保存
```

暂时不做完整身份局、复杂武将技能、排位、观战、好友系统等功能，先保证主流程完整可运行。

---

## Q4: Redis 使用方案

选择：**A**

初版先用内存 `ConcurrentHashMap` 管理房间状态和游戏状态，简化开发难度。

可以先设计好接口和数据结构，例如：

```text
roomMap
gameStateMap
userSessionMap
```

等核心对战流程稳定后，再逐步迁移到 Redis。

---

## Q5: 当前优先级

选择：**B**

直接开始搭建项目骨架，边做边调整，有问题再确认。

优先顺序建议为：

```text
1. 创建 monorepo 项目结构
2. 搭建 Spring Boot 后端基础工程
3. 搭建 Vue 3 前端基础工程
4. 完成登录注册和基础接口
5. 接入 WebSocket
6. 实现房间创建、加入、准备和聊天
7. 再进入 1v1 对战逻辑
```

---

整合后的确认回复可以直接发：

```text
Q1：A，就按 monorepo 结构开始创建项目。

Q2：A，技术栈确认，前端使用 Vue 3 + Vite + TypeScript，后端使用 Spring Boot 3 + Java + Maven，数据库使用 MySQL + MyBatis-Plus，认证使用 JWT。

Q3：A，初版 MVP 就做文档第 17 章定义的范围，不额外增加功能，先保证 1v1 简化版对战主流程完整可运行。

Q4：A，初版先用 ConcurrentHashMap 管理房间和游戏状态，降低开发复杂度。后续核心流程稳定后再迁移到 Redis。

Q5：B，直接开始搭建项目骨架，边做边调整，有问题再确认。
```
