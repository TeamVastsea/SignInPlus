# SignInPlus

**插件介绍**
- 面向 Paper/Spigot 的现代化每日签到插件：支持默认奖励、累计签、连签、特殊日期与排行榜奖励；内置补签卡、积分、PAPI、可选 Web API。
- 颜色码友好；消息前缀可配；多语言内置。
- 版本：1.7.0
- 作者：[Snowball_233](https://github.com/SnowballXueQiu)，[zrll_](https://github.com/zrll12)

**配置与文档**
- 配置文件示例与完整注释：`src/main/resources/config.yml`
- Web API OpenAPI 文档：`openapi.json`

**1.7.0 更新重点**
- 玩家数据统一以 UUID 为主键，支持 UniversalAuth 经 Velocity modern forwarding 下发的永久 `profileUuid`。
- PostgreSQL 共库操作使用事务、唯一约束、行级原子更新与初始化 advisory lock，适合多个子服共享数据。
- 连签排行榜改为数据库窗口查询，不再把全部签到历史加载到 JVM；已验证 365,000 条签到记录规模。
- `[ITEM]` 奖励改用现代 `/give` 数据组件解析，移除已弃用的 Paper Unsafe NBT 接口。
- Web API 增加 Bearer API Key、限流、请求参数约束与有界工作线程，并默认仅监听本机回环地址。
- 加入 SQLite/PostgreSQL 并发与完整性测试；目标运行版本更新为 Paper 1.21.11。
- Release 改为 Lite JAR，运行库在服务器首次启动时自动下载并缓存，不再发布 27.6 MB 的依赖合集。

> 1.7.0 使用全新 UUID 数据结构，不提供旧数据库迁移。部署前请创建空数据库。

**模块一览**
- 基础签到/首签/连签/累计签
  - 基础签到：玩家执行 `/signin`（别名：`/checkin`、`/qd`）触发 `default.actions`。
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
  - 开启方式：设置至少 24 字符的 `web_api.api_key`，再启用 `web_api.enable_web_api`。请求必须携带 `Authorization: Bearer <api_key>`。
  - 默认仅监听 `127.0.0.1`；对外提供时必须放在 HTTPS 反向代理后，并保留限流。
  - 端点（GET）：`/ifsignin?player=...`、`/total?player=...`、`/streak?player=...`、`/last_check_in_time?player=...`、`/ranktoday?player=...`、`/points?player=...`、`/info?player=...`、`/amounttoday`。
  - 外部签到：待开发（WIP）。完整定义参见 `openapi.json`。
- 概率系统（详见配置文件）
  - `default.actions` 中支持：`[PROB=X]` 概率触发；`[RANDOM_WEIGHTED]` + `[WEIGHT=X]` 权重触发；`[RANDOM_PICK=N]` 互斥抽取。
  - 支持与其他动作组合（消息、标题、物品、音效、状态、延迟等）。
- 多语言
  - `locale: zh_CN / en_US`，语言文件位于 `src/main/resources/lang`。
  - 占位符 `{name}` 等按需替换；消息与标题文本支持 `&` 自动转换为 `§`。
- 排行榜
  - `/signinplus top [total|streak]` 展示前十；支持 `top` 模块为当日排名发放额外奖励（如 rank=1/2/3）。
- Debug 调试器
  - 需在配置 `debug: true` 并具备 `signinplus.admin` 权限。
  - 用法：`/signinplus debug trigger <default|cumulative|streak|top|special_dates> [previous_value]`（例如 `streak 7`、`top 1`、`special_dates Thursday`）。

**指令详解**
- `/signin` | `/checkin` | `/qd`：玩家签到；权限 `signinplus.user`。
- `/signinplus gui`：打开签到 GUI；权限 `signinplus.user`。
- `/signinplus status [player]`：查询状态；权限 `signinplus.user`。
- `/signinplus reload`：重载配置与语言并重启 Web API；权限 `signinplus.admin`。
- `/signinplus points set <player> <amount>`：设置积分；权限 `signinplus.admin`。
- `/signinplus points add|decrease <player> <amount>`：增/扣积分；权限 `signinplus.admin`。
- `/signinplus points clear <player>`：清空积分；权限 `signinplus.admin`。
- `/signinplus correction_slip give|decrease|clear <player> [amount]`：管理补签卡；权限 `signinplus.admin`。
- `/signinplus make_up [cards] [player] [force]`：补签自己或他人；权限 `signinplus.make_up`（给他人补签需 `signinplus.admin`）。
- `/signinplus top total|streak`：查看排行榜；权限 `signinplus.user`。
- `/signinplus debug trigger ...`：触发奖励用于验证；权限 `signinplus.admin` 且 `debug: true`。

**数据库支持**
- 支持数据库：`sqlite` / `mysql` / `postgresql`
- 配置键：`database.type`、`database.url`、`database.username`、`database.password`
- 驱动版本：
  - SQLite JDBC：3.46.0.0
  - MySQL Connector/J：9.2.0
  - PostgreSQL JDBC：42.7.13
  - 连接池 HikariCP：6.2.1
  - ORM 框架 Exposed：0.58.0
- 初始化：
  - SQLite：自动创建 `plugins/SignInPlus/signinplus.db`
  - MySQL/PostgreSQL：数据库需预先创建，建议使用最小权限账户；凭据可由环境变量提供

**多子服共享部署**
- 多个子服必须连接同一个 PostgreSQL 数据库；不要跨进程或跨机器共享 SQLite 文件。
- 各服可以复用同一份配置，但 `web_api.web_api_port` 不能在同一台机器上冲突；连接池总量约等于“子服数量 × `database.pool_size`”。
- 数据库内的签到唯一性、积分、补签卡、领取记录和补签扣卡均采用原子事务，可防止多服并发重复写入。
- 数据库提交与 Minecraft 指令、发物品等游戏内奖励无法组成同一个事务；极端宕机可能出现“已记录签到、奖励尚未执行”，重要奖励建议使用可幂等的外部发放方案。
- 数据库账号或密码可通过 `SIGNINPLUS_DB_USERNAME`、`SIGNINPLUS_DB_PASSWORD` 环境变量注入。

**构建与测试**
- 推荐构建方式：`./gradlew build`（产物位于 `build/libs`）
- 本地运行：`./gradlew runServer`，启动 Paper 1.21.11 测试服务器
- Release 仅提供一个 Lite JAR，不内嵌 Kotlin、GUI、JSON、数据库驱动及连接池。
- `build` 会校验 JAR 小于 1 MB 且不含运行库包，防止误发 Full JAR。

**运行依赖下载**
- 依赖声明位于 `plugin.yml` 的 `libraries`，由 Paper/Spigot 在插件加载前从 Maven Central 自动下载并加入插件 ClassPath。
- 第一次启动必须允许服务器访问 Maven 仓库；下载完成后依赖会缓存在服务端 `libraries` 目录，后续启动无需重复下载。
- 自动下载内容包括 Kotlin、Triumph GUI、Gson、Exposed、SQLite/PostgreSQL/MySQL 驱动及 HikariCP；其传递依赖也会一并解析。
- Paper 可通过 `PAPER_DEFAULT_CENTRAL_REPOSITORY` 配置 Maven Central 镜像，详见 [Paper plugin.yml libraries 文档](https://docs.papermc.io/paper/dev/plugin-yml/#libraries)。

**技术栈**
- Kotlin：2.2.20
- 目标 JVM：21
- Gradle 插件：xyz.jpenilla.run-paper 3.0.2

**兼容性**
- 服务器：Paper/Spigot `1.20+`（测试版本：Paper 1.21.11）
- Java：服务器运行环境 `Java 21+`
- 依赖：PlaceholderAPI 2.11.5（可选）；其余运行库由服务器在首次启动时自动下载
- UniversalAuth：无需在后端安装其 Velocity API。UniversalAuth 认证完成后下发的永久 `profileUuid` 会成为 Paper 的 `Player.uniqueId`，SignInPlus 直接以此 UUID 作为数据库主键。
- 使用 UniversalAuth 时必须正确启用 Velocity modern forwarding、保护 forwarding secret，并阻止公网绕过代理直连后端；确认后在 SignInPlus 设置 `identity.require_stable_uuid: true`。

**其他功能**
- 登录动作：`on_login_action` 支持 `signin`、`open_gui` 或 `none`。
- 消息前缀：`message_prefix`；统一转换 `&` → `§`。
- 物品组件：`[ITEM] <type> <amount> [components]` 使用 Minecraft `/give` 数据组件语法，不再调用已弃用的 Unsafe NBT 接口。
- 多子服：PostgreSQL 共库支持并发签到与原子余额更新；全新数据库首次建表使用 PostgreSQL advisory lock 串行初始化。SQLite 仅用于单服，不应由多个子服共享数据库文件。
- 时区：`timezone` 可指定服务器统计时区。
