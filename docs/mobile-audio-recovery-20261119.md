# 手机无声和连接恢复（2026-11-19）

状态：v9 / versionCode 23 已覆盖安装，并通过本文列出的静音真机验证。基线为 voicehub.7 / versionCode 21；中间 v8 的验收失败记录保留。未卸载、未清数据。

## 证据

以下是手机日志时间（EST）；手机时钟比 NAS 约快 0.86 秒，逐事件关联时须校正。

- 04:41:59 音频焦点丢失，04:42:42 恢复，随后成功收到 PlaybackStarted。这一轮通话类中断恢复正常。
- 05:08:05 音频连接因三轮 server/time 无回复触发 Silent；05:08:06 缓冲耗尽，App 自动发送 Pause，控制连接正常，服务端却因播放器 unavailable 忽略暂停。该服务端缺口另行修复并通过三轮真实代理断线/重连测试。
- 05:08:11 重连后的流到达，但音频调度落后数秒，控制请求也曾等待约 13 秒。05:09:05 音频 SocketTimeout，05:09:07 FLAC MediaCodec 报 Pending dequeue cancelled，随后连续报 Released state；五次失败后管线终止，App 仅收到 Warning，没有停止事件。这解释了这一分支为何仍可能显示播放中。
- 05:56 五次专辑播放实际目标为离线电视 up28e856df。当时手机音频已连接。ADB 读取界面也确认选中电视。没有证据证明是手动选择还是页面同步造成；本轮只新增选择变化日志，不擅自自动换设备。

首次网络停顿的具体责任段未确定。Silent 代表应用层探测无回复，并不能独自证明 NAS 故障。此次未找到相应音源 403/502。严禁将 11:28 后人工部署/回滚导致的重连算作自发故障。

## 候选行为

- Android FLAC 在非法状态错误后，释放旧实例，按原格式与 STREAMINFO 重建并重试当前帧一次。每条流只允许一次重建，避免故障时无限重试。
- 修正 flush 失败时的 stop/start 不完整恢复。Android 官方要求 stop 后重新 configure 才能 start；本轮改由重建流程处理。stop 失败也继续尝试 release，配置失败释放半初始化实例。
- 重建后仍持续失败时，发送真正的 PlaybackStopped(SinkFailed)，通过现有暂停路径使服务器与界面停止，不只记 Warning。
- 保留当前 FLAC 偏好、采样率、缓存、蓝牙离车暂停和焦点恢复规则，不自动换音源或向其他音箱转播。

依据：[Android MediaCodec 状态与错误处理](https://developer.android.com/reference/android/media/MediaCodec#states)。首次导致 MediaCodec 进入 Released 状态的系统原因尚不能确认；恢复能力修复不等于证明该根因。

## 验证与回滚

新增解码器有界重建、原始帧及头信息、flush 异常、持续故障、主动释放、初始化失败释放测试，以及完整播放器终止事件与新流恢复测试。全部构建/测试使用 GitHub Actions，不在 hearth 编译。

保留 voicehub.7 APK、源码基线、私有原始日志。若需回滚 App，以原签名将基线重新构建为更高 versionCode 后覆盖安装，不通过卸载丢失配置。

v8 的 GitHub Actions 44570556516：156 个测试套件、958 项测试全通过，签名构建完成。首次构建被依赖仓库超时阻断，第二次缺少依赖；移除未使用的开发仓库并使用全新 Gradle 缓存后成功，没有更换依赖版本。APK SHA-256：`2404cb0f76d9f52ff6e019ec9941aaccf02dda3d23d52497d1c9584c53cc3967`，签名与 v7 相同。

v8 安装后，实际 UI Play 没有通过：11:58:55 EST 音频 hello/激活后马上发起的三次全量状态读取，未让 App 的 `playbackReady` 变为 true，11:59:04 Play 等待十秒后过期；重连后再次按 Play 仍同样失败。服务端当时报告该手机已连接可用。12:07 从服务端对同一手机明确 Play 后，实际 48kHz 音轨帧头从 218880 增至 322560、Underruns=0，随后手机自己的 Pause 成功且 audioReady=true。这证明音频输出本身可用，但不能用服务端启动替代 UI 的验收。

v9 为此增加连接已建立但服务端可用标记未同步时的有限只读核对：0.2/0.8/2/4 秒最多四次，状态一致或断连即取消，健康时不轮询，不自行播放。用户主动按 Play 而音频已连接时也请求一次权威状态刷新；依然等待真实的 connected + available，不伪造就绪。增加“早到的不可用快照、停止查询、断连取消、健康零轮询、后来的暂停撤销待播”五项测试，补充日志分别记录 audioConnected 与 serverAvailable。

## v9 完成验证

- 云端 [Actions 44573514772](https://github.com/behappy-my/voice-hub-mobile/actions/runs/44573514772)，源码 `4ed7dbc2`：157 个套件、963 项测试全部通过，零跳过；签名构建成功。APK SHA-256 为 `23c475257665176876f70341fb89397fb0b1c81e395dd3a937992bf0f0fc7b62`，证书与原包相同。
- 原机覆盖安装成功，保持账号、My Phone 选择、200 首队列和 FLAC 设置。通过 UI 的播放按钮启动，未用服务端启动来替代这轮验收。
- 五轮 Play/Pause，包括两轮强制结束 App 后重新启动；均实际输出音频帧。指令到 PlaybackStarted 分别 1248/860/854/854/839 ms，Pause 到 PlaybackStopped 为 24/18/18/17/17 ms；不包含 UI 自动化定位耗时，不是声学测量。
- 播放中将 App 切到 Android 设置页，音频帧持续输出；仅断开该手机的内部音频代理 TCP 连接，恢复后仍持续输出。暂停后再次断开同一连接，重连后保持停止，无活跃 AudioTrack；随后明确 Play 正常。没有触碰其他房间音箱。
- 实际 AudioFlinger 核对 48kHz 双声道轨道帧头前进，观察点 Underruns=0。其中重连后帧头 965760 → 1067520。12:15:54 EST 第一轮冷启动触发一次有限状态补查，此后 audioConnected/serverAvailable/audioReady 均为 true，UI Play 成功。
- 测试结束手机 idle，队列仍 200 首、索引 96；媒体音量仍为原来的 0，锁屏时间仍为原来的 1800000ms。两台 VP idle，小爱 paused。未自动恢复卧室播放。

APK、ZIP、构建及验收摘要已放 NAS `/mnt/user/Files/VoiceHub/Android/`，供 Nextcloud 外部存储入口读取。原始私有日志和逐轮结果在 hearth `/home/mk/.local/state/voicehub-diagnostics/mobile-20261119/phone/`，未公开录音、凭据或日志原文。

边界：这轮为家庭 Wi-Fi 上的静音测试，没有车载蓝牙真实行程或通话/其他 App 麦克风切换测试。原始网络停顿的具体跳点仍未确定；真实手机的历史 MediaCodec Released-state 故障未强行重现，其有界恢复和失败后停止由自动测试覆盖。不能将这些结果称作所有车载断流问题已根治。
