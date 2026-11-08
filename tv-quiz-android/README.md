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

## 二、装到 Sony 电视（不需要电脑命令）

1. 在 Mac 上把 `tvquiz.apk` 复制到 U 盘，插到电视的 USB 口。
2. 电视上打开 Google Play，搜索安装一个文件管理器，例如 **File Commander** 或 **X-plore File Manager**。
3. 允许它安装 App：**设置 → 应用 → 安全与限制 → 未知来源**，把刚装的文件管理器打开。
   （不同型号也可能在 设置 → 隐私权 → 安全与限制，或者第一次安装时电视会自动弹出提示，点「设置」打开即可。）
4. 用文件管理器打开 U 盘，点 `tvquiz.apk` → 安装。

> 会用 adb 的话也可以 `adb install -r tvquiz.apk`。

## 三、打开「无障碍」开关（只要做一次）

答题电视靠「无障碍」功能来知道电视正在放哪个 App，并在需要时弹出答题界面：

1. **设置 → 系统 → 无障碍**（有的型号叫「辅助功能」，英文是 *Accessibility*）。
2. 找到 **答题电视** → 打开 → 确认。
3. 如果开关是灰色、按不了：到 **设置 → 应用 → 答题电视**，按遥控器的选项键（⋮ 或 ≡），选 **「允许受限制的设置」**，再回第 1 步。

打开后，在电视上打开「答题电视」，家长面板顶部的黄色提示消失，就说明开好了。

> 用 adb 的人也可以不开无障碍，改用这两条命令：
> `adb shell appops set com.nomishaw.tvquiz GET_USAGE_STATS allow`
> `adb shell appops set com.nomishaw.tvquiz SYSTEM_ALERT_WINDOW allow`

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
