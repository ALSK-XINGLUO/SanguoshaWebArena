# Sanguosha-WebArena 项目文档

## 1. 项目简介

Sanguosha-WebArena 是一个基于 Web 的三国杀在线对战平台，项目目标是实现一个支持用户登录、房间创建、实时对战、卡牌结算、回合控制和对局记录的网页版卡牌游戏系统。

本项目采用前后端分离架构。前端负责页面展示、玩家交互和对局状态渲染；后端负责用户认证、房间管理、WebSocket 实时通信、游戏状态机维护、卡牌规则校验与对局数据持久化。

项目初期以 1v1 简化对战模式为核心，优先实现基础游戏流程，包括创建房间、加入房间、玩家准备、开始游戏、摸牌、出牌、响应、弃牌、回合切换和胜负判定。后续可逐步扩展多人身份局、武将技能、匹配系统、战绩排行、对局回放等功能。

## 2. 项目定位

本项目不仅是一个普通的网页游戏项目，更重点体现后端系统设计能力，包括实时通信、状态同步、复杂业务规则建模和游戏流程控制。

项目核心亮点包括：

1. 基于 WebSocket 实现房间内实时消息推送。
2. 设计统一消息协议，支持出牌、响应、技能、聊天和状态同步。
3. 使用游戏状态机管理摸牌、出牌、弃牌、回合结束等阶段。
4. 使用规则引擎思想拆分卡牌效果和规则校验逻辑。
5. 使用 Redis 缓存在线用户、房间状态和对局临时状态。
6. 使用 MySQL 持久化用户信息、对局记录、战绩和游戏日志。

## 3. 技术栈

### 3.1 前端技术栈

| 技术           | 作用                      |
| ------------ | ----------------------- |
| Vue 3        | 前端核心框架                  |
| Vite         | 前端构建工具                  |
| TypeScript   | 提高代码类型安全性               |
| Pinia        | 状态管理，用于维护用户信息、房间状态和游戏状态 |
| Vue Router   | 前端路由管理                  |
| Element Plus | 后台管理和基础组件库              |
| WebSocket    | 与后端进行实时通信               |
| Axios        | 普通 HTTP 请求              |

### 3.2 后端技术栈

| 技术                         | 作用                      |
| -------------------------- | ----------------------- |
| Spring Boot 3              | 后端基础框架                  |
| Spring WebSocket           | 实时通信                    |
| Spring Security / Sa-Token | 登录认证和权限控制               |
| JWT                        | 用户身份令牌                  |
| MyBatis-Plus               | 数据库 ORM 框架              |
| MySQL                      | 持久化存储用户、房间、对局和日志数据      |
| Redis                      | 缓存在线用户、房间状态、匹配队列和对局临时状态 |
| Lombok                     | 简化实体类和 DTO 编写           |
| Knife4j / Swagger          | 接口文档                    |
| Maven                      | 项目构建和依赖管理               |

## 4. 总体架构设计

系统采用前后端分离架构，前端通过 HTTP 和 WebSocket 与后端通信。

HTTP 请求主要用于登录、注册、用户信息查询、房间列表查询、战绩查询等普通业务操作。WebSocket 主要用于实时对战相关操作，例如加入房间、玩家准备、开始游戏、出牌、响应、状态同步和房间聊天。

整体结构如下：

```text
Vue 3 前端
  |
  |-- HTTP：登录、注册、房间列表、用户信息、战绩查询
  |
  |-- WebSocket：房间消息、实时对战、出牌响应、状态同步
  |
Spring Boot 后端
  |
  |-- 用户认证模块
  |-- 大厅模块
  |-- 房间模块
  |-- WebSocket 通信模块
  |-- 游戏状态机模块
  |-- 卡牌规则模块
  |-- 对局记录模块
  |
MySQL + Redis
```

## 5. 仓库结构设计

推荐使用一个总仓库管理前后端代码：

```text
Sanguosha-WebArena
├── sanguosha-webarena-client    前端项目
├── sanguosha-webarena-server    后端项目
├── docs                         项目文档
└── README.md                    项目说明
```

其中后端目录建议如下：

```text
sanguosha-webarena-server
├── common
│   ├── result        统一返回结果
│   ├── exception     全局异常处理
│   ├── constant      常量定义
│   └── enums         通用枚举
│
├── config
│   ├── WebSocketConfig
│   ├── RedisConfig
│   ├── SecurityConfig
│   └── MyBatisPlusConfig
│
├── auth
│   ├── controller
│   ├── service
│   └── dto
│
├── user
│   ├── controller
│   ├── service
│   ├── mapper
│   └── entity
│
├── lobby
│   ├── controller
│   └── service
│
├── room
│   ├── controller
│   ├── service
│   ├── entity
│   ├── dto
│   └── vo
│
├── websocket
│   ├── handler
│   ├── session
│   ├── message
│   └── dispatcher
│
├── game
│   ├── engine        游戏主引擎
│   ├── state         游戏状态
│   ├── player        玩家状态
│   ├── card          卡牌模型与卡牌效果
│   ├── skill         武将技能
│   ├── rule          规则校验
│   ├── event         游戏事件
│   └── context       游戏上下文
│
├── record
│   ├── controller
│   ├── service
│   ├── mapper
│   └── entity
│
└── SanguoshaApplication.java
```

## 6. 功能模块设计

### 6.1 用户模块

用户模块主要负责平台用户的注册、登录、身份认证和个人信息管理。

主要功能包括：

1. 用户注册。
2. 用户登录。
3. JWT 令牌生成和校验。
4. 用户信息查询。
5. 用户战绩展示。
6. 退出登录。

核心数据包括用户名、密码、昵称、头像、等级、胜场、负场、创建时间等。

### 6.2 大厅模块

大厅模块是玩家进入游戏后的主要入口，负责展示在线用户、房间列表和快捷操作。

主要功能包括：

1. 获取房间列表。
2. 创建房间。
3. 加入房间。
4. 快速匹配。
5. 查看在线用户。
6. 查看个人战绩。

### 6.3 房间模块

房间模块负责管理游戏开始前的准备阶段。

主要功能包括：

1. 创建房间。
2. 加入房间。
3. 退出房间。
4. 玩家准备。
5. 取消准备。
6. 房主开始游戏。
7. 房间内聊天。

房间状态包括：

```text
WAITING：等待玩家加入
READY：玩家已准备
PLAYING：游戏中
FINISHED：游戏结束
```

### 6.4 WebSocket 通信模块

WebSocket 模块负责维护客户端与服务端之间的长连接，并将前端发送的实时操作分发给对应的业务处理器。

主要职责包括：

1. 建立 WebSocket 连接。
2. 根据 Token 识别用户身份。
3. 维护用户连接 Session。
4. 接收前端实时消息。
5. 根据消息类型进行分发。
6. 向指定用户推送消息。
7. 向房间内所有用户广播消息。

统一消息格式如下：

```json
{
  "type": "PLAY_CARD",
  "roomId": 1001,
  "userId": 1,
  "data": {
    "cardId": 12,
    "targetUserId": 2
  }
}
```

常见消息类型包括：

```text
ROOM_JOIN        加入房间
ROOM_LEAVE       离开房间
ROOM_READY       玩家准备
GAME_START       游戏开始
TURN_START       回合开始
PLAY_CARD        出牌
USE_SKILL        使用技能
DISCARD_CARD     弃牌
RESPONSE_CARD    响应卡牌
GAME_SYNC        状态同步
GAME_OVER        游戏结束
CHAT             房间聊天
ERROR            错误提示
```

### 6.5 游戏状态机模块

游戏状态机模块负责控制整个对局的阶段流转，是后端最核心的模块之一。

初版可以将游戏阶段设计为：

```text
WAITING_START：等待开始
INIT_GAME：初始化游戏
DRAW_PHASE：摸牌阶段
PLAY_PHASE：出牌阶段
DISCARD_PHASE：弃牌阶段
TURN_END：回合结束
GAME_OVER：游戏结束
```

基本流程如下：

```text
游戏开始
  ↓
初始化牌堆、玩家、手牌
  ↓
进入当前玩家回合
  ↓
摸牌阶段
  ↓
出牌阶段
  ↓
弃牌阶段
  ↓
回合结束
  ↓
切换下一名玩家
  ↓
判断游戏是否结束
```

### 6.6 卡牌规则模块

卡牌规则模块负责处理卡牌使用条件、目标合法性和卡牌效果结算。

初版建议实现以下基础牌：

| 卡牌   | 功能                    |
| ---- | --------------------- |
| 杀    | 对目标造成 1 点伤害，目标可以使用闪响应 |
| 闪    | 响应杀，使杀无效              |
| 桃    | 回复 1 点体力，或濒死时救援       |
| 决斗   | 双方轮流出杀，未出杀者受到 1 点伤害   |
| 无中生有 | 摸两张牌                  |
| 过河拆桥 | 弃置目标一张牌               |
| 顺手牵羊 | 获得目标一张牌               |

卡牌效果建议使用接口进行抽象：

```java
public interface CardEffect {
    void execute(GameContext context, Long sourceUserId, List<Long> targetUserIds);
}
```

这样每张牌可以单独实现自己的结算逻辑，便于后续扩展。

### 6.7 响应机制模块

三国杀中存在大量等待响应的场景，例如“杀”需要等待目标是否出“闪”，“濒死”需要等待玩家是否使用“桃”。

因此需要设计 PendingAction 表示当前等待响应的动作。

示例结构：

```java
public class PendingAction {
    private String actionType;
    private Long sourceUserId;
    private Long targetUserId;
    private CardType requiredCardType;
    private Long timeoutAt;
}
```

初版主要支持：

```text
杀 -> 等待闪
濒死 -> 等待桃
```

### 6.8 对局记录模块

对局记录模块负责在游戏结束后保存对局结果，并记录关键操作日志。

主要功能包括：

1. 保存对局基本信息。
2. 保存玩家胜负结果。
3. 保存每一步游戏日志。
4. 查询历史战绩。
5. 查询对局详情。

对局日志可以记录：

```text
第几回合
当前玩家
操作类型
使用卡牌
目标玩家
造成结果
操作时间
```

## 7. 数据库设计

### 7.1 用户表 sys_user

| 字段          | 类型       | 说明    |
| ----------- | -------- | ----- |
| id          | bigint   | 用户 ID |
| username    | varchar  | 用户名   |
| password    | varchar  | 密码    |
| nickname    | varchar  | 昵称    |
| avatar      | varchar  | 头像    |
| level       | int      | 等级    |
| win_count   | int      | 胜场    |
| lose_count  | int      | 负场    |
| create_time | datetime | 创建时间  |
| update_time | datetime | 更新时间  |

### 7.2 房间表 game_room

| 字段               | 类型       | 说明    |
| ---------------- | -------- | ----- |
| id               | bigint   | 房间 ID |
| room_name        | varchar  | 房间名称  |
| owner_id         | bigint   | 房主 ID |
| max_player_count | int      | 最大玩家数 |
| status           | varchar  | 房间状态  |
| create_time      | datetime | 创建时间  |
| update_time      | datetime | 更新时间  |

### 7.3 对局记录表 game_record

| 字段         | 类型       | 说明      |
| ---------- | -------- | ------- |
| id         | bigint   | 对局 ID   |
| room_id    | bigint   | 房间 ID   |
| winner_id  | bigint   | 胜利玩家 ID |
| game_mode  | varchar  | 游戏模式    |
| start_time | datetime | 开始时间    |
| end_time   | datetime | 结束时间    |
| status     | varchar  | 对局状态    |

### 7.4 玩家对局记录表 game_player_record

| 字段           | 类型       | 说明    |
| ------------ | -------- | ----- |
| id           | bigint   | 记录 ID |
| game_id      | bigint   | 对局 ID |
| user_id      | bigint   | 用户 ID |
| hero_id      | bigint   | 武将 ID |
| result       | varchar  | 胜负结果  |
| kill_count   | int      | 击杀数   |
| damage_count | int      | 造成伤害  |
| create_time  | datetime | 创建时间  |

### 7.5 游戏日志表 game_log

| 字段             | 类型       | 说明    |
| -------------- | -------- | ----- |
| id             | bigint   | 日志 ID |
| game_id        | bigint   | 对局 ID |
| round_no       | int      | 回合数   |
| user_id        | bigint   | 操作玩家  |
| action_type    | varchar  | 操作类型  |
| card_name      | varchar  | 使用卡牌  |
| target_user_id | bigint   | 目标玩家  |
| content        | varchar  | 日志内容  |
| create_time    | datetime | 创建时间  |

### 7.6 武将表 hero

| 字段         | 类型      | 说明    |
| ---------- | ------- | ----- |
| id         | bigint  | 武将 ID |
| name       | varchar | 武将名称  |
| max_hp     | int     | 最大体力  |
| kingdom    | varchar | 势力    |
| skill_desc | text    | 技能描述  |

### 7.7 卡牌表 card

| 字段          | 类型      | 说明    |
| ----------- | ------- | ----- |
| id          | bigint  | 卡牌 ID |
| name        | varchar | 卡牌名称  |
| type        | varchar | 卡牌类型  |
| suit        | varchar | 花色    |
| point       | varchar | 点数    |
| description | text    | 卡牌描述  |

## 8. Redis 设计

Redis 主要用于保存实时性较强、变化频繁的数据。

建议的 Key 设计如下：

```text
online:user:{userId}           用户在线状态
room:{roomId}                  房间临时状态
game:state:{roomId}            对局状态
match:queue                    匹配队列
ws:user:session:{userId}       用户 WebSocket 连接信息
```

初版为了降低开发难度，也可以先使用内存 Map 管理房间和对局状态：

```java
private final Map<Long, Room> roomMap = new ConcurrentHashMap<>();
private final Map<Long, GameState> gameStateMap = new ConcurrentHashMap<>();
```

等核心功能完成后，再逐步迁移到 Redis。

## 9. 核心接口设计

### 9.1 用户接口

```text
POST /api/auth/register        用户注册
POST /api/auth/login           用户登录
GET  /api/user/info            获取当前用户信息
GET  /api/user/record          获取个人战绩
```

### 9.2 房间接口

```text
POST /api/room/create          创建房间
POST /api/room/join/{roomId}   加入房间
POST /api/room/leave/{roomId}  退出房间
GET  /api/room/list            获取房间列表
GET  /api/room/{roomId}        获取房间详情
```

### 9.3 对局记录接口

```text
GET /api/record/list           查询对局记录
GET /api/record/{gameId}       查询对局详情
GET /api/record/log/{gameId}   查询对局日志
```

## 10. WebSocket 事件设计

### 10.1 房间事件

| 事件类型              | 说明   |
| ----------------- | ---- |
| ROOM_JOIN         | 加入房间 |
| ROOM_LEAVE        | 离开房间 |
| ROOM_READY        | 玩家准备 |
| ROOM_CANCEL_READY | 取消准备 |
| ROOM_CHAT         | 房间聊天 |

### 10.2 游戏事件

| 事件类型          | 说明   |
| ------------- | ---- |
| GAME_START    | 游戏开始 |
| GAME_SYNC     | 状态同步 |
| TURN_START    | 回合开始 |
| DRAW_CARD     | 摸牌   |
| PLAY_CARD     | 出牌   |
| RESPONSE_CARD | 响应卡牌 |
| DISCARD_CARD  | 弃牌   |
| USE_SKILL     | 使用技能 |
| TURN_END      | 回合结束 |
| GAME_OVER     | 游戏结束 |

## 11. 游戏流程设计

### 11.1 创建房间流程

```text
用户登录
  ↓
进入大厅
  ↓
创建房间
  ↓
等待其他玩家加入
  ↓
玩家准备
  ↓
房主开始游戏
```

### 11.2 对局开始流程

```text
校验房间人数
  ↓
校验玩家准备状态
  ↓
创建游戏状态 GameState
  ↓
初始化牌堆
  ↓
分配武将
  ↓
发放初始手牌
  ↓
广播游戏开始消息
```

### 11.3 出牌流程

```text
玩家选择卡牌和目标
  ↓
前端发送 PLAY_CARD 消息
  ↓
后端校验是否为当前玩家
  ↓
校验是否处于出牌阶段
  ↓
校验卡牌和目标是否合法
  ↓
执行卡牌效果
  ↓
更新游戏状态
  ↓
广播最新状态
```

### 11.4 杀闪响应流程

```text
A 对 B 使用杀
  ↓
后端创建 PendingAction，等待 B 出闪
  ↓
B 选择出闪或放弃响应
  ↓
如果 B 出闪，杀失效
  ↓
如果 B 不出闪，B 扣 1 点体力
  ↓
更新并广播游戏状态
```

## 12. 前端页面设计

### 12.1 页面结构

```text
登录页
注册页
大厅页
房间页
对战页
个人中心页
战绩页
```

### 12.2 对战页面布局

对战页面是系统核心页面，建议包括以下区域：

```text
顶部：当前回合、阶段、倒计时
左侧：游戏日志
中间：玩家区域、武将、血量、装备区、判定区
底部：当前用户手牌区
右侧：操作按钮、技能按钮、聊天区
```

### 12.3 前端状态管理

Pinia 中建议拆分以下 Store：

```text
userStore：用户信息、Token
roomStore：房间信息、玩家准备状态
gameStore：对局状态、手牌、当前阶段、当前玩家
wsStore：WebSocket 连接和消息处理
```

## 13. 开发计划

### 第一阶段：基础框架搭建

1. 创建前后端项目。
2. 完成数据库连接和基础配置。
3. 完成统一返回结果和全局异常处理。
4. 完成用户注册、登录和 JWT 认证。

### 第二阶段：大厅和房间功能

1. 实现房间创建。
2. 实现房间列表查询。
3. 实现加入房间和退出房间。
4. 实现玩家准备和取消准备。
5. 实现房间状态同步。

### 第三阶段：WebSocket 实时通信

1. 建立 WebSocket 连接。
2. 实现用户连接管理。
3. 实现房间内消息广播。
4. 实现房间聊天。
5. 实现统一消息分发机制。

### 第四阶段：简化版对战流程

1. 实现游戏开始。
2. 初始化牌堆和玩家状态。
3. 实现摸牌阶段。
4. 实现出牌阶段。
5. 实现弃牌阶段。
6. 实现回合切换。
7. 实现胜负判定。

### 第五阶段：基础卡牌实现

1. 实现杀。
2. 实现闪。
3. 实现桃。
4. 实现无中生有。
5. 实现决斗。
6. 实现过河拆桥和顺手牵羊。

### 第六阶段：数据持久化和优化

1. 保存对局记录。
2. 保存玩家战绩。
3. 保存游戏日志。
4. 使用 Redis 优化房间和对局状态。
5. 完善异常处理和断线重连。

### 第七阶段：扩展功能

1. 增加武将技能。
2. 增加多人模式。
3. 增加匹配系统。
4. 增加排行榜。
5. 增加对局回放。
6. 增加观战功能。

## 14. 项目难点

### 14.1 实时状态同步

三国杀对战需要多个玩家看到一致的游戏状态。后端需要作为唯一可信状态源，所有玩家操作都必须先发送到后端，由后端校验和结算后再广播给客户端，避免前端自行修改导致状态不一致。

### 14.2 游戏状态机设计

游戏流程包含多个阶段，不同阶段允许的操作不同。例如摸牌阶段不能随意出牌，弃牌阶段需要判断手牌数是否超过体力值。因此需要使用状态机明确控制游戏流程。

### 14.3 卡牌规则扩展

三国杀卡牌和技能数量较多，如果所有规则都写在一个方法中，后期会非常难维护。因此需要将卡牌效果、规则校验和事件处理拆分成独立模块。

### 14.4 响应机制处理

出牌后可能需要等待其他玩家响应，例如“杀”需要等待“闪”，“濒死”需要等待“桃”。这类流程不是简单的请求响应模式，需要设计 PendingAction 记录当前等待的操作。

### 14.5 断线重连

玩家断线后重新进入房间时，需要从后端恢复当前房间状态、玩家状态和游戏阶段。因此后端需要保存完整的 GameState，并提供重新同步能力。

## 15. 项目可扩展方向

后续可以从以下方向扩展：

1. 多人身份局模式。
2. 武将技能系统。
3. 人机 AI 玩家。
4. 排位匹配系统。
5. 对局回放系统。
6. 观战系统。
7. 好友系统。
8. 皮肤和卡牌收藏系统。
9. 后台管理系统。
10. 移动端适配。

## 16. 简历描述示例

基于 Spring Boot 和 Vue 3 设计并实现三国杀网页版对战平台，支持用户登录、房间创建、实时对战、卡牌结算、回合控制和对局记录等功能。项目采用前后端分离架构，后端通过 WebSocket 实现房间内低延迟消息同步，设计统一消息协议完成出牌、响应、聊天和状态同步。核心对战逻辑采用游戏状态机管理摸牌、出牌、弃牌和回合切换流程，并通过规则引擎思想拆分卡牌效果与规则校验逻辑，提高复杂卡牌规则的可维护性。系统使用 Redis 维护在线用户、房间状态和对局临时状态，使用 MySQL 持久化用户信息、对局记录、战绩和游戏日志。

## 17. 初版范围建议

为了保证项目能够顺利完成，初版不建议直接实现完整三国杀。建议先实现一个可运行的 1v1 简化版，核心目标是跑通完整对局流程。

初版功能范围建议如下：

```text
用户登录注册
大厅房间列表
创建房间
加入房间
玩家准备
房间聊天
1v1 游戏开始
摸牌、出牌、弃牌、回合切换
杀、闪、桃、无中生有
胜负判定
对局记录保存
```

暂时不做或后期再做的功能：

```text
完整身份局
复杂武将技能
锦囊完整结算
装备区复杂距离计算
观战系统
好友系统
排位系统
完整 AI 玩家
```

这样可以优先保证项目主流程完整，并且后续有清晰的扩展空间。


