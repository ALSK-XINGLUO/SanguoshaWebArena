# 开发计划

> 基于 Sanguosha-WebArena.md 和用户确认结果（2026-05-26）

## 确认结果

| 问题 | 选择 | 说明 |
|------|------|------|
| 项目结构 | A | monorepo 结构 |
| 技术栈 | A | Vue 3 + Vite + TS / Spring Boot 3 + Java + Maven / MySQL + MyBatis-Plus / JWT |
| MVP 范围 | A | 文档第 17 章范围 |
| Redis 方案 | A | 初版用 ConcurrentHashMap |
| 当前优先级 | B | 直接搭建，边做边调整 |

## 开发阶段

### 第一阶段：基础项目搭建

1. 创建 monorepo 目录结构
2. 搭建 Spring Boot 后端基础工程（Maven 项目结构、配置、统一返回结果、全局异常处理）
3. 搭建 Vue 3 + Vite 前端基础工程（路由、Pinia、Axios 封装）
4. 配置 MySQL 数据库和 MyBatis-Plus
5. 实现用户注册、登录、JWT 认证

### 第二阶段：大厅和房间功能

1. 房间 CRUD 接口（创建、加入、退出、列表、详情）
2. 玩家准备 / 取消准备
3. WebSocket 连接建立和用户身份识别
4. 房间内消息广播和聊天

### 第三阶段：1v1 对战流程

1. 游戏状态机：WAITING → INIT → DRAW → PLAY → DISCARD → TURN_END → GAME_OVER
2. 摸牌、出牌、弃牌、回合切换
3. 卡牌实现：杀、闪、桃、无中生有
4. PendingAction 响应机制（杀→闪、濒死→桃）
5. 胜负判定

### 第四阶段：数据持久化

1. 对局记录保存
2. 玩家战绩更新
3. 游戏日志记录

### 后续阶段（MVP 后）

1. 迁移到 Redis
2. 武将技能
3. 匹配系统
4. 排位、观战、回放