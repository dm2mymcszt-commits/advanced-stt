# Advanced STT

Dictate your Minecraft chat messages with your voice. Hold a key, talk, and your words appear live,
only on your screen, then go into the chat box for you to check and send. French and English are
recognized automatically.

Advanced STT is a **client-side Fabric mod for Minecraft Java 26.2**. It works on any server, because
the server only ever receives the finished chat message, the same as if you had typed it.

You bring your own API key for one of two speech-to-text services:

- **Cartesia Ink-2**
- **AssemblyAI Universal-3.6 Pro** (streaming)

The mod has no server of its own. Your key and your voice go straight from your computer to the
service you picked, and nowhere else.

## Features

- **Hold to talk** (default key: Left Alt), or press once to start and again to stop.
- **Live text**, private to you, while you speak. Nothing is sent to the game server until the
  message is finished.
- **Manual sending** (default): your words go into the chat box, you check them and press Enter.
  Works while the chat box is open too: dictated words are added after what you typed.
- **Automatic sending** (optional): the message is sent once you stop talking. Commands are never
  sent automatically, the same message is never sent twice in a row, and messages are never sent
  faster than one every 2 seconds.
- **Microphone indicator**: a short sound and a small "Listening" label with a level meter.
- **Words to listen for**: player names and game words the service should spell right.
- **Usage panel**: what the mod measured, priced at each service's published rate, with an
  optional "balance left" gauge.
- **Safety limits**: the microphone turns off on its own after 2 minutes (configurable), when the
  game window loses focus, and when you open the pause menu, so a paid connection is never left
  open by mistake.
- Settings screen in English and French, opened with `/astt`, from Mod Menu, or with an optional key.

## Requirements

- Minecraft Java Edition **26.2**
- [Fabric Loader](https://fabricmc.net/use/) **0.19.5** or newer
- [Fabric API](https://modrinth.com/mod/fabric-api) for 26.2
- Java 25 (the Minecraft launcher and most third-party launchers provide it)
- Optional: [Mod Menu](https://modrinth.com/mod/modmenu), to open the settings from the mods list
- A microphone, and an API key from Cartesia or AssemblyAI

## Install

1. Install Fabric Loader for Minecraft 26.2 (in Prism, Freesm, MultiMC and similar launchers: edit
   the instance and add Fabric).
2. Put **Fabric API** and **Advanced STT** (`advanced-stt-<version>.jar`) in the instance's `mods`
   folder.
3. Start the game.

## Get an API key

- **Cartesia**: create a key at <https://play.cartesia.ai/keys>.
- **AssemblyAI**: open the dashboard at <https://www.assemblyai.com/dashboard> and go to
  API Keys.

Then, in game: type `/astt`, choose the service, paste the key, click **Save key**, then
**Test key**.

## Using it

1. Hold **Left Alt** and talk. A "Listening" label appears in the top-left corner and your words
   appear at the bottom of the screen.
2. Let go. The chat box opens with your text. Fix anything you like, then press **Enter**, or
   **Esc** to throw it away.

You can change the key in Options > Controls > Key Binds > Advanced STT, or from the settings
screen. Pick a key that doesn't type a letter, so it also works while the chat box is open.

On a Mac, Option (Alt) also types symbols in the chat box. Typing a symbol that way never turns the
microphone on.

Problems (no key, a refused key, no microphone) are shown in the chat, only to you.

## What it costs

Each service bills your own account. The mod only opens a connection while the microphone is on.

| Service | How it bills | Example |
| --- | --- | --- |
| Cartesia Ink-2 | 3 credits per second of audio sent, silence included | 1 minute of talking = 180 credits |
| AssemblyAI Universal-3.6 Pro | $0.45 per hour that the connection stays open | 1 minute ≈ $0.0075 |

These are the published rates at the time of writing. Check each service's pricing page, as they can
change. The rates the mod uses for its estimate are in `config/advanced-stt.json`
(`cartesiaCreditsPerSecond`, `assemblyAiDollarsPerHour`).

**About the usage panel:** neither service lets a normal API key read your real balance (Cartesia
only shows it to admin keys, which this mod never asks for). So the panel shows an **estimate**:
what the mod measured, priced at the rates above. If you type in your balance from the service's
dashboard, the gauge counts down from it. Your real balance is always on the dashboard.

## Languages

French and English are detected automatically by both services. There is no language setting.
AssemblyAI also handles French and English mixed in the same sentence.

## Privacy and security

- **Your API key** is encrypted on your computer: with your Windows account's data protection on
  Windows, and with a separate key file only you can read on macOS and Linux. It is stored
  **outside the game folder**, so sharing your game folder or exporting a modpack never shares it:
  - Windows: `%LOCALAPPDATA%\AdvancedSTT`
  - macOS: `~/Library/Application Support/AdvancedSTT`
  - Linux: `~/.config/advanced-stt` (or `$XDG_CONFIG_HOME/advanced-stt`)
- The key is only sent to the service you chose, in a request header over an encrypted connection.
  It is never put in a URL, the game log, the chat or the settings file.
- **Your voice** is only sent while the microphone indicator is on, and only to the service you
  chose. Nothing is recorded to disk.
- **Your words** stay on your screen until you send them. Dictated text is not written to the game
  log.
- The mod doesn't collect anything and has no server of its own.

## Microphone permissions

- **Windows**: Settings > Privacy & security > Microphone > allow desktop apps to use the
  microphone.
- **macOS**: the first time, macOS asks whether your launcher may use the microphone. If you said
  no, allow it in System Settings > Privacy & Security > Microphone.
- **Linux**: the mod uses your sound server's default input unless you pick another microphone in
  the settings.

If the level meter next to "Listening" doesn't move when you talk, choose your microphone in the
settings.

## Settings

| Setting | Default | What it does |
| --- | --- | --- |
| Service | Cartesia Ink-2 | Which service transcribes your voice |
| Talk key | Hold | Hold to talk, or press on/off |
| Mic indicator | Sound + HUD | Beep and/or on-screen label when the microphone turns on and off |
| Mic turns off after | 2 min | Safety limit for one dictation (15 s to 10 min) |
| Send | With Enter | Or automatically after a silence (0.5 to 5 s, default 1.5 s) |
| Words to listen for | (empty) | Names and game words, separated by commas (up to 100) |
| Microphone | System default | Which input device to use |

## Compatibility

- Client-only: nothing to install on servers.
- It does not change how chat messages are displayed, so it is meant to work alongside chat mods
  such as Chat Heads.
- It does not use or replace voice chat mods.

## Troubleshooting

- **"No API key yet"**: type `/astt` and save a key for the selected service.
- **"refused the API key"**: the key is wrong, revoked, or for the other service. Paste it again and
  use Test key.
- **"has no credits left"**: top up on the service's dashboard.
- **"Can't reach"**: check your internet connection or firewall.
- **Microphone problem**: pick your microphone in the settings, and check the permissions above.

## Building from source

You need JDK 25.

```
./gradlew build
```

The mod jar is written to `build/libs/`. `./gradlew test` runs the tests, including protocol tests
against local stand-ins for both services. `./gradlew runClient` starts a development game.

`./gradlew runClientGameTest` plays the mod in a real game with a fake microphone and a fake speech
service: settings screen, talk key, live text, chat box, sending and errors. It saves screenshots in
`build/run/clientGameTest/screenshots`, and keeps its test keys in a throwaway folder, never with
your real ones. On a machine without a screen, run it with `xvfb-run -a`.

## License

[MIT](LICENSE). Report problems at
<https://github.com/dm2mymcszt-commits/advanced-stt/issues>.

Advanced STT is not affiliated with Mojang, Microsoft, Cartesia or AssemblyAI.
