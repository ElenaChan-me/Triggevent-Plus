# Triggevent JP

[Triggevent](https://github.com/xpdota/event-trigger) を日本語化・最適化した版です。
A Japanese-localized and optimized fork of [Triggevent](https://github.com/xpdota/event-trigger).

## 注意 / Warning

FINAL FANTASY XIV の利用規約では外部ツールの使用が禁止されています。利用は自己責任で行ってください。
Third-party tools are prohibited by the FINAL FANTASY XIV Terms of Service. Use at your own risk.

## 本家からの変更点 / Changes from the original

変更日 / Date of modification: 2026-10-13（ベース / based on: upstream `master` `44aca1b`）

- **日本語化 / Localization**: 画面の文字列を辞書（`xivsupport/src/main/resources/i18n/ja.tsv`）で日本語に置き換える仕組みを追加。本家のソースの文字列は変更していません。`-Dtriggevent.translate=false` で英語表示に戻せます。
  UI strings are replaced at display time from a dictionary; upstream source strings are untouched.
- **自動更新の無効化 / Updater disabled**: 本家の配布ファイルで日本語版が上書きされないよう、本家サーバーからの更新を停止。更新は XIVToolHub から行います。
  The upstream auto-updater is disabled so the Japanese build is not overwritten; updates come via XIVToolHub.
- **配布サイズの削減 / Smaller package**: 同梱 JRE を jlink で作成（Windows 用 zip: 225MB → 146MB）。
  The bundled JRE is built with jlink (Windows zip: 225MB → 146MB).
- **負荷の削減 / Lower overhead**: ログの既定レベルを info に変更、文字列の重複排除を有効化、非表示のマップタブの更新処理を停止。
  Default log level set to info, string deduplication enabled, hidden map tab no longer refreshes.
- **日本語の表示 / Japanese text**: コールアウトの既定フォントを日本語を表示できる論理フォント（Dialog）に変更。
  The default callout font now supports Japanese.
- **多言語 / Languages**: 表示言語を日本語・英語・韓国語・中国語（簡体字）から選択可能（辞書は `ja.tsv`・`ko.tsv`・`zh.tsv`）。XIVToolHub でインストール時に選び、日本語の場合は追加フォントも選べます。
  Display language can be Japanese, English, Korean or Simplified Chinese, chosen in XIVToolHub (with an optional font for Japanese).
- **テーマ / Themes**: テーマを「黒・赤」と「白・水色」の2種類に整理し、XIVToolHub・Glamaholic JP と同じ配色に統一。ハブから起動した場合はハブのテーマに合わせます。
  Themes reduced to Black/Red and White/Light Blue, shared with XIVToolHub and Glamaholic JP; the hub's theme is used when installed through it.
- **ボイスパック / Voice packs**: [Triggevent-VoiceGen](https://github.com/ElenaChan-me/Triggevent-VoiceGen) で作ったボイスパックで読み上げを差し替える「ボイスパック」タブを追加しました。
  - 収録した音声があれば Triggevent が直接再生します。名前や方角などの部品はつなげて鳴らします。
  - プレイヤー名が入るなど収録が揃わない読み上げは、日本語にした文を ACT の TTS で読みます。そのため、ACT の TTS には日本語の音声（例: Microsoft Haruka）を選んでください。
  - 台本に無い読み上げ（自分で書き換えたコールアウトなど）は、今までどおりの動作です。
  - 同じフォルダに複数のパック（本気用・かわいい話し方など）を並べておくと、「ボイスパックの切り替え」で選び直せます。
  A "Voice Pack" tab plays recorded clips from a Triggevent-VoiceGen pack instead of TTS. Lines that cannot be fully covered by clips are read in Japanese via ACT TTS, and callouts not in the script behave as before. Packs placed in the same parent folder can be switched from the tab.
- **黄金のレガシーの極・幻・メインストーリー / Dawntrail Extremes, Unreals and story duties**: 本家が未対応のボス（極：スフェーン・ゼレニア・ネクロン・ドゥームトレイン・エヌオー、幻：白虎・朱雀・青龍・ツクヨミ・神龍、ダンジョン11か所、ノーマル討伐戦5つ）のトリガーを、[cactbot](https://github.com/OverlayPlugin/cactbot)（Apache License 2.0）のトリガーをもとに移植しました。ライセンス文は `licenses/cactbot-LICENSE.txt` にあります。実際の戦闘ログでの確認はまだのため、読み上げのタイミングがずれる場合があります。
  Triggers for Dawntrail Extremes, Unreals, dungeons and normal trials not covered upstream were ported from cactbot (Apache License 2.0); see `licenses/cactbot-LICENSE.txt`. They have not been verified against real combat logs yet.
- **CI**: 本家専用のワークフローを削除し、GitHub のランナーで動くビルド・リリース用のワークフローに置き換え。
  Upstream-only workflows were replaced with ones that run on GitHub-hosted runners.

## 必要なもの / Requirements

ACT（Advanced Combat Tracker）、FFXIV_ACT_Plugin、OverlayPlugin が必要です。XIVToolHub で Triggevent JP を選ぶと、これらも一緒にインストールされます。ACT の OverlayPlugin の設定で WebSocket サーバーを開始してから起動してください。

ACT, FFXIV_ACT_Plugin and OverlayPlugin are required; XIVToolHub installs them together with Triggevent JP. Start the OverlayPlugin WebSocket server before launching.

## ビルド / Build

```powershell
mvn -B -DskipTests install
```

`launcher/target/triggevent-jp-win.zip` が配布用ファイルです。jlink を使うため、jmods を含む JDK 21 で実行してください。
The distributable is `launcher/target/triggevent-jp-win.zip`. Use a JDK 21 that includes jmods.

## ライセンス / License

本家と同じく GPL-3.0 です（[LICENSE](LICENSE)）。
Licensed under GPL-3.0, the same as the original ([LICENSE](LICENSE)).

---

# Triggevent

Triggevent is a comprehensive FFXIV addon that provides triggers, automarkers, overlays, log analysis, and more for 
FFXIV. Whether you're an end user, a developer yourself, or a world prog 9th man, there's something for everyone.

Want to have a multi target DoT tracker without going through all the manual setup pain of Hojoring/Special Spell
Timer (SpeSpe)?

Maybe you just want a Titan Gaol plugin that isn't a pain to set up, and doesn't require manual name entry, or 
breaking updates just to customize priority?

Want to make triggers without dealing with nasty regular expressions and arcane syntax in a clunky UI?

You've come to the right place!

Even if you don't plan to use it for gameplay, its log analysis and replay functions can be invaluable for 
making other addons and triggers.

Most documentation is in the app, or on the [Wiki](https://github.com/xpdota/event-trigger/wiki/).

## How to Download/Install

READ THIS: See [Installation](https://github.com/xpdota/event-trigger/wiki/Installation-and-Setup)

Short version: Install OverlayPlugin in your ACT, then start OverlayPlugin WSServer from within ACT.

## Support

File bugs and feature requests on GitHub, or visit the [Discord](https://discord.gg/jxk24jC66r).

## Features

### Overlays

There are several overlays built in. Perhaps the most interesting is a multi-target DoT tracker, since nothing else
seems to have good multi-target support. It even has tick display built-in if you want to optimize:

![image](https://user-images.githubusercontent.com/14287379/158668694-38697d1f-3e3a-4afb-8b68-bb8c9ff531fb.png)

### Cooldown Tracker

![CD Tracker](https://i.imgur.com/FgSHZY8.png)

Shows remaining cooldown and active time. You can have two separate overlays - one for party, one for personal.

### Jail Plugin/Automarkers

Triggevent has by far the most powerful Titan Jail plugin. You choose a priority by dragging and dropping jobs - no
typing names. It supports both automarker and personal callouts, so you don't have to configure two plugins separately.

![Jail Plugin](https://user-images.githubusercontent.com/14287379/142813080-c44d1ff7-873b-4119-9c15-1212c9e31133.png)

Nowadays, it supports auto marks for many duties other than just UWU, such as TOP and DSR.

You can even make your own auto markers, using Easy Triggers, scripts, or a complete code module.

### Triggers

Triggers can be written as code (like Cactbot) or with a simple point-and-click interface. Both options let you write
clear conditions, rather than stuffing everything into a hard-to-understand regex.

For example, consider this small bit of code here:

![image](https://user-images.githubusercontent.com/14287379/158666921-7bcc2ee2-f80c-44c3-9750-7e09c0f2b8f2.png)

The conditions are all easier to read than a regex - it triggers on a buff application, with an ID of 0xACA, where the
target is the player, and it is not a buff refresh. Then, we want to switch our callout based on the number of stacks of
the buff.

On top of that, it puts configurable callouts on the UI: will result in you having four easily configurable callouts,
which support both on-screen text and TTS:

![image](https://user-images.githubusercontent.com/14287379/158667132-b2d816a9-34c7-414a-9079-93dab4703760.png)

You can also make triggers using simple conditions - no regex required! No more worrying about future patches breaking
regexes, since everything is parsed for you:

![image](https://user-images.githubusercontent.com/14287379/158667347-286d6afa-3392-4526-a8ff-0fe7a7879144.png)

You can even right-click an event and select "Make Easy Trigger", and it will try to fill out the data for you:

![image](https://cdn.discordapp.com/attachments/551476873717088279/956345688432721920/unknown.png)

In addition, any trigger attached to an event with a duration (buffs, castbars) can easily display a countdown until the
event takes place, if you have the on-screen callout overlay:

![image](https://user-images.githubusercontent.com/14287379/158667758-97a47fec-5ff6-476d-a511-b868b43086f3.png)

Callouts can be further customized, as they support Groovy expressions:

![image](https://user-images.githubusercontent.com/14287379/158668357-6ad34ac9-42ec-4cd0-8177-80275ef9ebc0.png)

### Scripting and Addons

On top of the Easy Triggers interface, you can write
[scripts](https://github.com/xpdota/event-trigger/wiki/Groovy-Examples) which allow you to do almost everything that
a code module could do, but right from the UI.

You can also make [an entire module](https://github.com/xpdota/triggevent-example-module), complete with
auto-updates.

### Analysis

Easy access to combatants and events data

![Overview](https://user-images.githubusercontent.com/14287379/142812965-7666c15e-12b3-4b6c-91a6-ed38820a7aa8.png)

Makes it easier to create and test reliable, extensible overlays and triggers. Even if you're developing triggers for a
different plugin like Cactbot or Triggernometry.

### Replay Support

You can import a previous session, or an ACT log. You can even force-on overlays to see what your overlays *would*
have looked like at that point. FFLogs is also supported to some degree. This is a great way to rapidly test new
triggers - if fflogs exposes enough data, you don't even *need to have the log file*!

To open a replay, simple run triggevent-import.exe instead of triggevent.exe, and then choose a file or FFLogs URL. Note
that FFLogs support requires you to supply your own API key - I don't have a way of securely distributing a key.

![image](https://user-images.githubusercontent.com/14287379/158670751-3252a0d1-854b-47a7-8a47-2a74a2cf30bd.png)

## Future Features

See [Feature Ideas](https://github.com/xpdota/event-trigger/wiki/Feature-Ideas) for what I plan to implement down the
line.

## Why Would I Use This? (Short Version)

### For Normal Users

* Lots of built-in features - jail plugin that doesn't suck, multi target dot+tick tracking, and a lot more
  - Ever wanted to put your entire mit plan, with icons, on a timeline? Now you can.
* Better customizability of callouts without needing to edit triggers
  - Don't have to miss out on update to customize!
* Extremely easy to make basic triggers - making triggers doesn't require any knowledge of log lines nor regular expressions (and shouldn't - [because it was never the right tool for the job](https://github.com/xpdota/event-trigger/wiki/Why-is-Regex-Bad-for-Triggers%3F)!)
  - Most of it can be done by right clicking on the event you want to make a trigger from, and selecting "Make Easy Trigger". It's not perfect all the time, but you can edit the trigger if anything is off.
* Events are significantly easier to understand than raw log lines.

### For Developers and Power Users
* Better tools for creating and testing triggers
  - Certain types of triggers can be tested without a log file - you can use an FFLogs report instead!
  - You can write test cases for triggers in code
* Zero regex means much more readable conditions and triggers
* Much better abstraction of incoming data out of the box
  - Specifically, log lines are parsed into rich objects that can be queried as needed
  - Everything is converted to appropriate data types, both in terms of primitive values (i.e. no more worrying about hex/dec conversions), as well as rich types (such as combatants and abilities)
  - Events can be further abstracted into more refined events - for example, Titan Jails have their own event, so anyone wanting to provide more forms of Jail plugins need only listen for that specific event.
  - The trigger shouldn't need to worry about any low-level details - it should be abstracted to that absolute minimum (e.g. `call out "Raidwide" when ability 0x123 starts casting`
* Plugins can have their own custom configuration GUIs (see the jail config GUI for a great example)
* Run scripts within the app, for any purpose:
  - Prototyping code that will go inside the app
  - Triggers
  - Automarkers with whatever logic or priority you need
  - Log analysis
  - Bulk changing of settings and the like
