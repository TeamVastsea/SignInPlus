# SignInPlus

**简介**
- 面向 Paper/Spigot 的每日签到插件，支持累计/连续/特殊日期/排行榜奖励。
- 内置本地化与可配置前缀、可选 Web API，只读查询接口。
- 支持 PlaceholderAPI 扩展用于看板、全息与记分板显示。

**特性**
- 日常签到：`/signin` 别名包括 `/checkin`、`/qiandao`、`/qd`。
- 奖励类型：默认奖励、累计签到（`cumulative`）、连续签到（`streak`）、特殊日期（`special_dates`）、当日排行榜（`top`）。
- 奖励动作：`[MESSAGE]`、`[TITLE]`、`[BROADCAST]`（颜色码使用 `§`）。
- 本地化：从 `config.yml` 的 `locale` 加载语言文件，命令提示与消息支持占位符替换。
- 统计与积分：提供总天数、连签天数、今日排行与积分管理。
- PlaceholderAPI：前缀 `signinplus`，支持玩家上下文与后缀玩家名查询。

**安装**
- 将构建出的插件包复制到服务器 `plugins/` 目录并重启服务器。
- 若使用占位符，请安装 PlaceholderAPI 插件。

**指令**
- 玩家签到：
  - `/signin` | `/checkin` | `/qiandao` | `/qd`
- 查看状态：
  - `/signinplus status [player]`
- 重新加载配置与语言：
  - `/signinplus reload`
- 积分管理（管理员）：
  - `/signinplus points set <player> <amount>`
  - `/signinplus points clear <player>`
  - `/signinplus points decrease <player> <amount>`
  - `/signinplus points add <player> <amount>`
- 排行榜：
  - `/signinplus top [streak|total]`
- 强制签到（管理员）：
  - `/signinplus force_check_in <player>`
- 调试触发（管理员，需 `config.yml` 中 `debug: true`）：
  - `/signinplus debug trigger <type> [value]`
  - `type` 可为：`default` | `cumulative` | `streak` | `top` | `special_dates`

**权限**
- `signinplus.user`：允许使用签到与查询相关命令。
- `signinplus.admin`：允许使用管理员与调试命令。

**PlaceholderAPI**
- 标识符：`signinplus`
- 无玩家上下文：
  - ``%signinplus_amount_today%`` — 今日总签到人数。
- 玩家上下文（可用后缀 `_玩家名`，或使用请求者）：
  - ``%signinplus_status%``
  - ``%signinplus_total_days%``
  - ``%signinplus_streak_days%``
  - ``%signinplus_last_check_in_time%``
  - ``%signinplus_rank_today%``
  - ``%signinplus_points%``
- 示例：
  - ``%signinplus_total_days_Steve%``
  - ``%signinplus_status%``（使用请求者）

**配置**
- `message_prefix`：消息前缀。支持 `&`（将自动转换为 `§`），默认 `§7[§a签到Plus§7] `。
- `locale`：语言文件（默认 `en_US`）。
- `timezone`：时区（默认 `Asia/Shanghai`）。
- `debug`：是否允许 `/signinplus debug` 调试命令。
- 奖励配置（示例结构）：
  - `default`：玩家每天首次签到时触发。
  - `cumulative`：按累计签到天数触发，示例键：`times: 7, 30, 100`。
  - `streak`：按连续签到天数触发，示例键：`times: 7, 14, 28`。
  - `special_dates`：指定日期触发，示例键：`date: 12-31`、`repeat: true`、`repeat_time: 3`。
  - `top`：当日排行榜奖励，示例键：`rank: 1, 2, 3`。

**奖励动作语法**
- `[MESSAGE] <text>`：向玩家发送一条消息。
- `[TITLE] <title> | <subtitle>`：显示标题与副标题。
- `[BROADCAST] <text>`：全服广播一条消息。
- 颜色码：奖励文本请使用 `§`（不再自动将 `&` 转换为 `§`）。

**Web API（可选）**
- 当 `web_api` 配置节启用时，插件会启动只读查询服务，用于外部读取签到统计。
- 具体端口与令牌等字段请根据你的 `config.yml` 设置（若禁用或未配置则不启动）。

**兼容性与要求**
- 运行环境：Paper/Spigot 1.16+（推荐 1.18+）。
- 依赖（可选）：PlaceholderAPI。
- 构建环境：JDK 17 与 Gradle Wrapper。

**从源码构建**
- 在项目根目录执行：``./gradlew build -x test``
- 构建产物位于：`build/libs/SignInPlus-<version>.jar`

**迁移与颜色码说明**
- 配置示例已统一使用 `§` 颜色码；如仍使用 `&`，仅 `message_prefix` 会自动转换，其它奖励文本请手动改为 `§`。
- 控制台调试输出支持 ANSI 颜色转换（通过内部工具）。

**常见问题**
- 占位符查询未加入服务器的玩家时，插件会优先使用在线/缓存玩家；离线模式下按离线 UUID 计算；解析失败返回默认值，避免 NPE。
- 奖励未生效或排行榜为空时，请检查对应配置段是否存在且键名与值合法。

**反馈与贡献**
- 欢迎提交 Issue 与 PR 来改进插件功能与文档。