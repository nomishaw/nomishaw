# 📺 答题电视 · Sony / Google TV 版

孩子在电视上看 YouTube、Netflix 等 App 时自动计时，**每看满 10 分钟就弹出全屏答题**：

- 答对了就回到原来的 App 继续看。
- 答错了要等 58 秒才换新题，还要多答一道（最多累计 3 道）。
- 按「返回」键退不出答题界面。按「主页」键能退到桌面，但一打开视频 App，答题界面马上又会弹出来。
- 家长可以用 **手机遥控**：大人解锁 2 小时、立即锁屏、取消解锁、改规则、看今天答题记录。

适用于 Sony Bravia（Google TV / Android TV 7.0 以上）。其他 Android TV / 电视盒子 / 安卓平板也能用。

---

## 一、下载 APK

最新版固定下载链接：

```
https://github.com/nomishaw/nomishaw/releases/download/tvquiz-latest/tvquiz.apk
```

（每次修改代码后 GitHub Actions 会自动重新编译。）

## 二、装到 Sony 电视

任选一种方法：

**方法 A：U 盘**
1. 把 `tvquiz.apk` 复制到 U 盘，插到电视上。
2. 电视：设置 → 系统 → 关于 → 连按「Android TV 操作系统版本号」7 次，打开开发者模式。
3. 设置 → 应用 → 安全与限制 → 未知来源 → 允许「文件管理器」类 App 安装。
4. 用电视的文件管理器（没有的话，在 Google Play 装一个「File Commander」或「X-plore」）打开 U 盘里的 APK 安装。

**方法 B：电脑 adb（推荐，第三步也要用）**
1. 电视打开开发者模式（同上），再到 设置 → 系统 → 开发者选项，打开「USB 调试」和「网络调试 / ADB 调试」。
2. 电视 设置 → 网络 里看电视的 IP，比如 `192.168.1.50`。
3. 电脑装好 [Android platform-tools](https://developer.android.com/tools/releases/platform-tools)，然后：
   ```
   adb connect 192.168.1.50
   adb install -r tvquiz.apk
   ```
   第一次连接时，电视上会弹出「允许 USB 调试吗」，选允许。

## 三、开两个权限（只要做一次）

答题电视要知道「现在在看哪个 App」，还要能「盖在别的 App 上面」。Sony 电视的设置里一般找不到这两个开关，用电脑执行：

```
adb shell appops set com.nomishaw.tvquiz GET_USAGE_STATS allow
adb shell appops set com.nomishaw.tvquiz SYSTEM_ALERT_WINDOW allow
```

然后在电视上打开「答题电视」一次。家长面板顶部的黄色提示消失，就说明权限开好了。

> 没有电脑？可以用安卓手机上的 adb 工具 App（例如「Bugjaeger」）连接电视的 IP，执行同样的命令。

## 四、设置

1. 在电视上打开「答题电视」，输入默认密码 **0000**，**马上在「修改家长密码」里改掉**。
2. 在「哪些 App 算看视频」里勾选要管的 App。YouTube、Netflix 等已经自动勾好了。
3. 把面板底部显示的网址（例如 `http://192.168.1.50:8686`）在手机浏览器打开，加到主屏幕，以后就用手机遥控。

## 规则（家长面板里可改）

| 设置 | 默认 |
|---|---|
| 每看几分钟答一次题 | 10 分钟（只在勾选的 App 打开、屏幕亮着时计时） |
| 答错后等待 | 58 秒 |
| 答错追加题数 | 1 道 |
| 最多累计题数 | 3 道 |
| 每日观看上限 | 不限 |
| 题库 | 初一数学 / 小学高年级数学 / 数学+英语单词 / 自定义 |

自定义题目每行一题：`题目 | 正确答案 | 错误答案1 | 错误答案2 …`

## 已知限制

- 计的是「视频 App 打开着」的时间，不区分是在播放还是在选片。
- 孩子如果会进 设置 → 应用 → 答题电视 → 强行停止，就能把它关掉。可以在 Sony 的「家长控制 / 受限个人资料」里把「设置」锁上。
- 手机遥控只在家里同一个 Wi-Fi 下可用，走的是不加密的 HTTP，靠家长密码保护。
- 电视有线电视信号（直播频道）是否算在内取决于 Sony 的直播 App 是否出现在 App 列表里；出现的话勾上就行。

## 开发

- 代码：`app/src/main/java/com/nomishaw/tvquiz/`（Kotlin，无第三方依赖）
- 答题画面：`app/src/main/assets/quiz.html`；家长面板：`app/src/main/assets/parent.html`（电视和手机共用）
- 本地编译：`gradle assembleRelease`（需要 Android SDK 34、JDK 17）
- 签名 key `app/tvquiz.keystore` 随项目提交，这样每次编译的新版本都能直接覆盖安装。只适合家庭自用。
