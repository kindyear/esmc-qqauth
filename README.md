# ESMCQQAuth

ESMC QQ 白名单认证系统的 Paper 26.2 执行端。业务真源位于配套 Backend；本插件不保存 QQ 资料或长期绑定关系。

## 已实现

- Java 25、Paper API `26.2.build.124-stable`
- Paper 主动连接 KBot WebSocket，Bearer token 鉴权和指数退避重连
- versioned JSON 协议与断线期间的有界出站队列
- verification challenge/session 缓存与过期上报
- `ProfileWhitelistVerifyEvent` 临时旁路
- `/verify <code>` 游戏内所有权证明
- 验证码最多尝试 3 次（可配置）；第三次错误后 challenge 失效并踢出临时验证玩家
- 验证期间常驻 Title/Subtitle；白名单正式生效后播放玩家升级音效，并显示绿色成功 Title
- 验证中的玩家冻结：锁定水平移动但允许自然下落，并阻止聊天、非 verify 命令、交互、背包、拾取/丢弃、方块修改和伤害
- 执行 `whitelist.add`、`whitelist.remove`、`player.kick`、`reward.give`，并回传 `command.result`
- 签到奖励支持离线等待、背包满时掉落到脚边，以及按 request ID 持久化去重
- 所有网络 I/O 均在 Paper 主线程之外；Bukkit/Paper 状态修改会调度回主线程

## 构建

```bash
./gradlew clean test build --no-daemon
```

构建环境需要 JDK 25；如果系统默认 Java 不是 JDK 25，请先把 `JAVA_HOME` 指向本机的 JDK 25 安装目录。

产物：`build/libs/ESMCQQAuth-0.3.0.jar`。

## CI 与发布

GitHub Actions 会在推送到 `main` 及所有面向 `main` 的 Pull Request 时，以 JDK 25 执行构建和测试，并保存可下载的 JAR 构建产物。

要发布版本，先确保 `build.gradle.kts` 中的 `version` 已更新，再推送同名标签：

```bash
git tag v0.3.0
git push origin v0.3.0
```

发布工作流会校验标签与项目版本一致，构建并测试通过后自动创建 GitHub Release，上传 `ESMCQQAuth-*.jar`。

## 安装

1. 把 JAR 放入 Paper 26.2 服务端的 `plugins/`。
2. 首次启动生成配置后停止服务端。
3. 编辑 `plugins/ESMCQQAuth/config.yml`：
   - `backend.url` 指向配套 Backend 的 `/qqauth` WebSocket。
   - `backend.bearer-token` 与 Backend 的 Bearer token 完全一致，且至少 32 字节。
   - `backend.server-id` 必须列在 Backend 允许连接的服务器列表中。
4. 先启动 Backend，再启动 Paper。
5. Paper 的 `server.properties` 需要 `white-list=true`；否则不会触发所需的白名单认证流程。

默认 token 是不可用的 `CHANGE_ME`，未配置时插件会安全地拒绝启用。

Title 文案和持续时间可在 `verification.title` 下配置。`refresh-ticks` 控制验证提示续期频率，`bound-stay-ticks` 控制成功提示停留时间；20 ticks 等于 1 秒。

`verification.max-attempts` 控制验证码最大尝试次数，默认值为 3。奖励请求的去重状态保存在插件数据目录的 `processed-rewards.yml`。

## 与 Backend 的职责边界

QQ `/绑定`、Mojang 名称转 UUID、群成员校验、退群移除、周期核对及管理员换绑均由 Backend 实现。本插件只执行 Minecraft 侧认证与白名单状态，不应接触 QQ 凭据或长期绑定数据。

仓库内的 `config.yml` 仅包含不可用的占位 token。请勿提交实际 Bearer token 或生产服务器地址。
