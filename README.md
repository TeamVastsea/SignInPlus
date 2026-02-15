# SignInPlus

**插件介绍**
- 面向 Paper/Spigot 的现代化每日签到插件：支持默认奖励、累计签、连签、特殊日期与排行榜奖励；内置补签卡、积分、PAPI、可选 Web API。
- 颜色码友好；消息前缀可配；多语言内置。
- 版本：1.3.7 | 项目组：cc.vastsea
- 作者：[Snowball_233](https://github.com/SnowballXueQiu)，[zrll_](https://github.com/zrll12)

**配置与文档**
- 配置文件示例与完整注释：`src/main/resources/config.yml`
- Web API OpenAPI 文档：`openapi.json`

**模块一览**
- 基础签到/首签/连签/累计签
  - 基础签到：玩家执行 `/signin`（别名：`/checkin`、`/qiandao`、`/qd`）触发 `default.actions`。
  - 首签：可用 `top` 模块配置当日首位签到（`rank: 1`）的额外奖励。
  - 连签与累计签：分别由 `streak` 与 `cumulative` 块定义阈值与奖励，支持多档配置与去重发放。
  - 特殊日期：`special_dates` 支持按具体日期、每年、每月或每周匹配触发，可设重复次数限制。
- PAPI（PlaceholderAPI）
  - 标识符：`signinplus`
  - 占位符（支持后缀玩家名，例如 `total_days_Steve`）：
    - `amount_today` 今日签到人数
    - `status` 今日是否签到（带颜色）
    - `total_days` 累计天数
    - `streak_days` 连签天数
    - `last_check_in_time` 上次签到时间
    - `rank_today` 今日排名
    - `points` 积分（两位小数显示）
    - `corr` 补签卡数量
- 签到积分
  - 存储为“分”（整数）以避免浮点误差，显示统一格式化为两位小数。
  - 奖励动作支持：`[POINTS] 64`、`[POINTS] 1..5 .2f`、`[POINTS] 1..5 z` 等（范围与格式说明见配置）。
  - 指令支持查看、设置、增加、扣减与清空积分（详见下文“指令详解”）。
- Web API（只读查询）
  - 开启方式：`web_api.enable_web_api: true` 后，插件启动 HTTP 服务，默认 `http://<addr>:<port><endpoint>`。
  - 端点（GET）：`/ifsignin?player=...`、`/total?player=...`、`/streak?player=...`、`/last_check_in_time?player=...`、`/ranktoday?player=...`、`/points?player=...`、`/info?player=...`、`/amounttoday`。
  - 外部签到：待开发（WIP）。完整定义参见 `openapi.json`。
- 概率系统（详见配置文件）
  - `default.actions` 中支持：`[PROB=X]` 概率触发；`[RANDOM_WEIGHTED]` + `[WEIGHT=X]` 权重触发；`[RANDOM_PICK=N]` 互斥抽取。
  - 支持嵌套与与其他动作组合（消息、标题、物品、音效、状态、延迟等）。
- 多语言
  - `locale: zh_CN / en_US`，语言文件位于 `src/main/resources/lang`。
  - 占位符 `{name}` 等按需替换；消息与标题文本支持 `&` 自动转换为 `§`。
- 排行榜
  - `/signinplus top [total|streak]` 展示前十；支持 `top` 模块为当日排名发放额外奖励（如 rank=1/2/3）。
- Debug 调试器
  - 需在配置 `debug: true` 并具备 `signinplus.admin` 权限。
  - 用法：`/signinplus debug trigger <default|cumulative|streak|top|special_dates> [previous_value]`（例如 `streak 7`、`top 1`、`special_dates Thursday`）。

**指令详解**
- `/signin` | `/checkin` | `/qiandao` | `/qd`：玩家签到；权限 `signinplus.user`。
- `/signinplus status [player]`：查询状态；权限 `signinplus.user`。
- `/signinplus reload`：重载配置与语言并重启 Web API；权限 `signinplus.admin`。
- `/signinplus points set <player> <amount>`：设置积分；权限 `signinplus.admin`。
- `/signinplus points add|decrease <player> <amount>`：增/扣积分；权限 `signinplus.admin`。
- `/signinplus points clear <player>`：清空积分；权限 `signinplus.admin`。
- `/signinplus correction_slip give|decrease|clear <player> [amount]`：管理补签卡；权限 `signinplus.admin`。
- `/signinplus make_up [cards] [player] [force]`：补签自己或他人；权限 `signinplus.make_up`（他人需 `signinplus.admin`）。
- `/signinplus top total|streak`：查看排行榜；权限 `signinplus.user`。
- `/signinplus debug trigger ...`：触发奖励用于验证；权限 `signinplus.admin` 且 `debug: true`。

**数据库支持**
- 支持数据库：`sqlite` / `mysql` / `postgresql`
- 配置键：`database.type`、`database.url`、`database.username`、`database.password`
- 驱动版本：
  - SQLite JDBC：3.46.0.0
  - MySQL Connector/J：9.2.0
  - PostgreSQL JDBC：42.7.5
  - 连接池 HikariCP：6.2.1
  - ORM 框架 Exposed：0.58.0
- 初始化：
  - SQLite：自动创建 `plugins/SignInPlus/signinplus.db`
  - MySQL/PostgreSQL：启动时尝试创建数据库（需账户具备建库权限）

**构建与测试**
- 推荐构建方式：`./gradlew build`（产物位于 `build/libs`）
- 产物说明：`./gradlew build` 会同时生成两种 JAR
  - `*-all.jar`：包含全部依赖，直接部署（Shadow 全量包）
  - 无 `-all` 后缀：精简包，依赖由 Spigot 在加载时自动下载
- 其他构建：只生成全量包 `./gradlew shadowJar`
- 本地测试：`./gradlew runServer` 启动 Paper 1.21 测试服（可用于快速验证指令与奖励）

**技术栈**
- Kotlin：2.2.20
- 目标 JVM：21
- Gradle 插件：Shadow 9.2.2, xyz.jpenilla.run-paper 2.3.1

**兼容性**
- 服务器：Paper/Spigot `1.20.4+`（测试版本：1.21）
- Java：服务器运行环境 `Java 21+`
- 依赖：PlaceholderAPI 2.11.5（推荐安装）；Kotlin runtime（自动包含）

**其他功能**
- 自动签到：`auto_check_in_on_login.enable` 可在玩家登录时自动签到。
- 消息前缀：`message_prefix`；统一转换 `&` → `§`。
- NBT 物品：`[ITEM] <type> <amount> <nbt>` 支持复杂 NBT 内容。
- 时区：`timezone` 可指定服务器统计时区。
- 统计：集成 bStats 3.0.2（可在配置中开关）
