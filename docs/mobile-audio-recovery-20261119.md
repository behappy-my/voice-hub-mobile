# 手机无声和连接恢复（2026-11-19）

状态：候选，等待云端测试、签名构建及真机验证。基线为 voicehub.7 / versionCode 21，候选为 voicehub.8 / versionCode 22；不卸载、不清数据。

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

真机、车载、弱网验证结果待补充。
