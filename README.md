# Music Player Reborn · 音乐机重生

[English](#english) | [简体中文](#简体中文)

<a id="english"></a>

## English

Author: **niumadadi520**

**First public release · 1.0.0 · Minecraft 1.21.1 / NeoForge**

Place a gramophone in your home, take a portable music player on an adventure, or build a small stage with friends. Music Player Reborn adds music devices, earphone sharing, lyrics, and karaoke to Minecraft.

![Creative tab icon](src/main/resources/assets/mengsamanetmusic/textures/item/mod_icon.png)

### Gramophones and portable music players

Gramophones provide background music for your builds. Portable players can be held or placed in the world. Devices feature pink and cream models with playback and interaction animations.

The player interface includes song search, device playlists, favorites, pause, previous/next track controls, and playback seeking. Song lists show up to 20 tracks per page, with previous/next page buttons and direct page navigation.

For a placed portable player, Shift + right-click its physical buttons to change tracks or pause. A normal right-click opens its interface.

### QQ Music and NetEase Cloud Music

The mod provides QR-code login and account playlist access for QQ Music and NetEase Cloud Music. Sign in to the corresponding service to load your liked songs and select music in-game.

Music, lyrics, cover art, and account playlists depend on the respective platform. Playback availability depends on account permissions, region, and the platform's catalog. Signing in does not bypass paid access or regional restrictions.

### Private listening and sharing

The portable player's earphone interface has three slots: wired earphones, a left Bluetooth earbud, and a right Bluetooth earbud. Right-click a Bluetooth earbud case to play its opening animation, then access the earbuds inside.

Insert earphones into the player for private listening. While holding the player, aim at another player and hold the middle mouse button to send a sharing invitation and choose which ear to share. Once they accept, both players can listen; other players cannot hear that device's private audio.

- Wired sharing range: 4 blocks, with a visible connecting cable.
- Bluetooth sharing range: 32 blocks.
- A warning appears near the range limit; sharing disconnects beyond it.
- Sharing requires the recipient's consent. Declining an invitation does not establish a connection.

### Karaoke and speakers

The mod includes a placeable vintage microphone, a handheld microphone, a microphone stand, and a speaker. The handheld microphone can be mounted on its stand but cannot be placed directly on the ground.

Each microphone has its own connection code. Enter the code into a speaker to connect it. A speaker can connect to multiple microphones, and each speaker has an independent volume setting. Shift + right-click a microphone to open its settings and manage its switch.

**Voice transmission requires Simple Voice Chat.** Ordinary music playback does not require that mod.

### Lyrics, themes, and listening rankings

The HUD in the upper-left corner can display track information, cover art, progress, and two lines of lyrics. Lyrics transition with fading and movement. Press F6 to configure which HUD elements are visible.

Choose from multiple pixel-art menu themes in the interface settings. Music lists, account screens, and related settings follow the mod's visual style.

Listening rankings are recorded per world or server, tracking song play counts and player listening time. Paused playback does not add listening time. Listening to multiple devices simultaneously counts only once for each player.

### Optional integrations

| Mod | Integration |
| --- | --- |
| Sophisticated Backpacks | Portable player upgrade, animated backpack attachment, player access from the backpack, and HUD support |
| Curios | Earphones in compatible accessory slots |
| Simple Voice Chat | Voice transmission for karaoke devices |
| Touhou Little Maid | Maid-related device integration |
| Jade | Device information display |
| Cloth Config | Configuration screen support |

Install the matching game and loader versions of integrations and their dependencies as needed. Ordinary music playback does not require every integration.

### Installation

This repository targets **Minecraft 1.21.1 / NeoForge** and uses **Java 21**. It is built against **NeoForge 21.1.248** and requires the matching **GeckoLib 4.x** release.

1. Install the matching Minecraft, NeoForge, and Java versions.
2. Place the mod's full JAR and GeckoLib in your `mods` folder.
3. Install the relevant optional mods and dependencies for backpack, accessory, or karaoke features.
4. In multiplayer, clients and the server need the same mod branch and required dependencies.

A separate Minecraft 1.20.1 / Forge branch is available. The branches are built separately, and their files are not interchangeable. Development artifacts containing `-sources` or `-slim` in their names are not intended for game installation.

### Controls

| Action | Function |
| --- | --- |
| Right-click a music player | Open the music interface |
| Shift + right-click a placed portable player's physical button | Previous track, next track, or pause |
| Earphone button in the portable player interface | Manage wired earphones and left/right Bluetooth earbuds |
| Hold a portable player, aim at another player, and hold the middle mouse button | Send an earphone sharing invitation |
| Right-click a Bluetooth earbud case | Play the opening animation and open the case interface |
| Shift + right-click a microphone | Open microphone settings |
| F6 | Configure the lyrics HUD |

### Frequently asked questions

**Why do some tracks fail to play?**

Check your connection, platform login status, and account permissions. Removed tracks, regional restrictions, paid access, and platform API changes can affect availability.

**Why is there no karaoke audio?**

Make sure Simple Voice Chat is installed and configured, the microphone is enabled, and the speaker's connection code and volume are set correctly.

**Why does earphone sharing disconnect?**

Wired and Bluetooth sharing have different range limits. Watch for distance warnings and make sure the player and earphones remain in a valid usage state.

**How do I report a problem?**

Include your Minecraft version, loader, mod version, reproduction steps, and relevant logs in an issue. Remove account cookies, tokens, and other login credentials before sharing logs publicly.

### Building from source

Install JDK 21 and run the following from the repository root.

Windows:

```powershell
.\gradlew.bat build
```

Linux / macOS:

```bash
chmod +x gradlew
./gradlew build
```

The first build downloads dependencies. Build artifacts are written to `build/libs/`. The repository includes the Gradle Wrapper, local build dependencies, resources, and test sources. Running `build` does not launch Minecraft.

### Acknowledgments and provenance

Thanks to the maintainers of components used by this mod: **GeckoLib** for models and animations, **ZXing** for generating NetEase login QR codes, and **MP3SPI, JLayer, Tritonus, jFLAC, and JavaSound AAC** for audio format support. Bundled components and separately installed dependencies are distinguished in the [third-party notices](THIRD-PARTY-NOTICES.txt), along with their uses and licensing information.

The optional integrations listed above indicate compatibility or API integration. They do not mean those mods' authors wrote code for this project.

The relationships with the four music projects are as follows:

| Project | Provenance and current status |
| --- | --- |
| NetMusic | A historical source of the base project. Playback, networking, and other implementations have been modified. BSD attribution is retained without attributing all current functionality to upstream. |
| netMusicListForge | Historical provenance records cover the old cache, pause controls, playlist helpers, and default cover. The relevant old classes and artwork have been replaced; historical MIT notices are retained. |
| NetMusic-BetterLogin | A historical reference for the old login path. That implementation has been removed. This review did not confirm direct use of its specific implementation in the current code, so it is not credited as the provider of the current QR-code login implementation. |
| NetMusicCanNeedQQ | A historical reference for QQ functionality. Related implementations have been reworked, with some compatibility entry points retained. The entire current QQ module is not attributed to that project. The reference archive's authorization status and review limitations are documented separately. |

See the [detailed provenance records](THIRD-PARTY-NOTICES.txt) for authors, scope, removed and current implementations, and reasons for retaining notices. Historical reference does not mean current direct use; the absence of identical files does not establish entirely independent provenance.

Model and interface contributions are described according to existing records. Some interface artwork and icons were created with generative-tool assistance. The four reference projects are not collectively credited as providers of the current artwork.

Code, assets, and third-party components have different licenses; see the [license overview](LICENSE.md). This project is not officially affiliated with or endorsed by the operators of Minecraft, QQ Music, or NetEase Cloud Music. It grants no rights to music or access to paid content.

---

## 简体中文

作者：**niumadadi520**

**首次公开发布 · 1.0.0 · Minecraft 1.21.1 / NeoForge**

给小屋放一台唱片机，带着随身听去探索，或者和朋友搭一座小舞台。音乐机重生为 Minecraft 加入音乐播放设备、耳机分享、歌词显示与 K 歌功能，让音乐成为日常游玩的一部分。

![创造标签图标](src/main/resources/assets/mengsamanetmusic/textures/item/mod_icon.png)

## 唱片机与随身听

唱片机适合放在建筑里播放背景音乐，随身听则可以手持使用或放置。设备采用粉色与奶油色搭配的模型，并配有播放及操作动画。

播放器界面提供歌曲搜索、设备歌单、收藏、暂停、上一首、下一首和播放进度控制。歌曲列表每页最多显示 20 首，支持上下页与指定页码跳转。

放置的随身听既可以通过界面操作，也可以对准对应实体按钮，使用 Shift + 右键切歌或暂停；普通右键仍用于打开界面。

## QQ 音乐与网易云音乐

模组提供 QQ 音乐、网易云音乐的扫码登录和账号歌单入口。登录对应平台账号后，可以读取喜欢的歌曲，并在游戏中选择播放。

音乐接口、歌词、封面与账号歌单依赖相应平台服务。歌曲能否播放取决于账号权限、地区和平台当前可用内容；登录不会解除付费或地区限制。

## 耳机私听与双人分享

随身听界面的耳机入口提供三个槽位：有线耳机、蓝牙左耳和蓝牙右耳。蓝牙耳机盒在右键后播放开盒动画，再打开界面取出耳机。

将耳机装入随身听后可以私听。手持随身听对准另一位玩家，按住鼠标中键发起共享邀请，并选择分享的耳侧；对方同意后，两位玩家共同收听，其他玩家不会听到该设备的私听音频。

- 有线共享范围：4 格，连接时显示耳机线。
- 蓝牙共享范围：32 格。
- 接近距离上限时提示，超出范围后断开共享。
- 分享需要对方同意，拒绝邀请不会建立连接。

## K 歌与音响

模组包含可放置的老式麦克风、手持麦克风、麦克风架和音响。手持麦克风可装到架子上，不能直接放在地面。

每个麦克风有独立连接码，在音响中输入即可建立连接；一个音响可以连接多个麦克风，每个音响可以单独调节音量。Shift + 右键麦克风进入设置，管理麦克风开关。

**语音传输需要安装 Simple Voice Chat。** 普通音乐播放不要求安装该语音模组。

## 歌词、主题与听歌排行

左上角 HUD 可显示歌曲信息、封面、进度及双行歌词。歌词切换采用淡出与位移动画，按 F6 可调整各部分的显示。

菜单提供多套像素风主题，可在界面设置中直接切换。音乐列表、账号界面和相关设置沿用模组的界面风格。

听歌排行榜按当前存档或服务器统计歌曲播放次数和玩家听歌时长。暂停不累计时长，同一玩家同时听多个设备时只累计一份时间。

## 可选联动

| 模组 | 提供的功能 |
| --- | --- |
| Sophisticated Backpacks / 精妙背包 | 随身听插件、背包挂件模型及动画、背包内播放器入口和 HUD 显示 |
| Curios | 支持耳机使用对应饰品槽佩戴 |
| Simple Voice Chat | K 歌设备的语音传输 |
| Touhou Little Maid / 车万女仆 | 女仆相关设备联动 |
| Jade | 设备信息展示 |
| Cloth Config | 配置界面支持 |

按需要安装对应游戏版本和加载器的联动模组及其前置，不必为了普通听歌安装全部联动。

## 安装

本仓库是 **Minecraft 1.21.1 / NeoForge** 分支，使用 **Java 21**。构建所用加载器为 NeoForge 21.1.248，必需前置为对应版本的 **GeckoLib 4.x**。

1. 安装对应版本的 Minecraft、NeoForge 和 Java。
2. 将本模组完整 JAR 与 GeckoLib 放入 `mods` 文件夹。
3. 如需背包、饰品或 K 歌功能，再安装对应联动模组和前置。
4. 多人游戏的客户端和服务端需要安装相同分支及必需前置。

另有 Minecraft 1.20.1 / Forge 分支。两个分支分别构建，不能混用文件。名字带 `-sources` 或 `-slim` 的开发产物不用于游戏安装。

## 常用操作

| 操作 | 功能 |
| --- | --- |
| 普通右键播放器 | 打开音乐界面 |
| Shift + 右键放置随身听的实体按钮 | 上一首、下一首或暂停 |
| 随身听界面中的耳机按钮 | 管理有线耳机及左右蓝牙耳机 |
| 手持随身听，对准玩家并按住鼠标中键 | 发起耳机共享邀请 |
| 右键蓝牙耳机盒 | 播放开盒动画并打开耳机盒界面 |
| Shift + 右键麦克风 | 打开麦克风设置 |
| F6 | 调整歌词 HUD 显示 |

## 常见问题

**为什么有些歌曲不能播放？**

请检查网络、平台登录状态及账号权限。歌曲下架、地区限制、付费权限和平台接口变化都可能影响可用性。

**为什么 K 歌没有声音？**

请确认已安装并配置 Simple Voice Chat、麦克风已开启，且音响连接码和音量设置正确。

**耳机共享为什么会断开？**

有线和蓝牙共享有各自距离限制。请留意接近范围边界的提示，并确认随身听和耳机仍处于有效使用状态。

**如何反馈问题？**

提交 Issue 时请提供游戏版本、加载器、模组版本、复现步骤与相关日志。公开日志前请移除账号 Cookie、令牌和其他登录凭据。

## 从源码构建

安装 JDK 21，在仓库根目录执行：

Windows：

```powershell
.\gradlew.bat build
```

Linux / macOS：

```bash
chmod +x gradlew
./gradlew build
```

首次构建需要下载依赖，产物位于 `build/libs/`。仓库包含 Gradle Wrapper、本地构建依赖、资源与测试源码。运行 `build` 不会启动游戏。

## 致谢与来源说明

感谢实际支撑本模组运行的组件维护者：**GeckoLib** 用于模型与动画，**ZXing** 用于生成网易云登录二维码，**MP3SPI、JLayer、Tritonus、jFLAC 和 JavaSound AAC** 提供音频格式支持。内置组件与独立安装的依赖有所区别，具体用途和许可资料见 [第三方声明](THIRD-PARTY-NOTICES.txt)。

上方“可选联动”列的是兼容对象，表示本模组接入它们的功能或 API，不表示这些模组的作者为本项目编写了代码。

关于四个音乐项目，关系分别是：

| 项目 | 来源关系与当前状态 |
| --- | --- |
| NetMusic | 基础工程的历史来源；播放、网络等实现经过修改，保留 BSD 来源声明，不把整个现有功能归为上游原作。 |
| netMusicListForge | 旧缓存、暂停控制、歌单辅助和默认封面有历史来源记录；相关旧类及封面已替换，保留历史 MIT 声明。 |
| NetMusic-BetterLogin | 旧登录路径的历史参考；旧实现已移除，本次未确认当前直接采用其专属实现，不列为现有扫码登录的代码提供者。 |
| NetMusicCanNeedQQ | QQ 功能的历史参考；相关实现已重构，部分兼容入口保留，不将整个现有 QQ 模块归为其原作。参考包的授权状态及核对局限单独说明。 |

作者、具体涉及范围、已删除实现、当前实现与保留声明的原因，见 [详细来源记录](THIRD-PARTY-NOTICES.txt)。历史参考不等于当前直接采用；未发现相同文件也不作为完全独立来源的证明。

模型与界面贡献按已有记录说明，部分界面和图标使用生成式工具辅助制作，不将四个参考项目统一列为当前美术资源提供者。

代码、资源和第三方组件适用不同许可，详见 [许可说明](LICENSE.md)。本项目与 Minecraft、QQ 音乐、网易云音乐的运营方无官方隶属或背书关系，不提供音乐版权或付费内容权限。
