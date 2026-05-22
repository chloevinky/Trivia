<h1>Trivia - A Fabric 1.21.1 Question & Reward Plugin (Server-Side)</h1>

<h3>Originally created for the Roanoke Cobblemon Server to make Cobblemon items more obtainable</h2>

A **server-side-only** Fabric mod: clients do not need to install it. Once added to the
server, Trivia automatically posts a question in chat every **10 minutes** (configurable),
in **English and Brazilian Portuguese simultaneously**. The first player to answer in chat
wins an item reward from a configurable pool.

On first run, the plugin will generate a Trivia folder with both questions & rewards JSON files.

Questions/Rewards are added under "pools", which could be treated as categories or "difficulty ranges".

A quiz question will be put in chat every 10 minutes, and will time out if not answered in 2 minutes.

If a player's inventory is full, or inserting the item goes wrong for some other reason, it is dropped in front of them.

![img.png](img.png)

<h2>Bilingual Display (English + pt-BR)</h2>

By default, Trivia posts each question on two lines: the original English text, followed by
a Brazilian-Portuguese rendering tagged with the language code. Pokemon names, type names
(Fire, Water, Grass, Fairy, etc.), abilities and move names are preserved in English when
the Claude translation provider is used; for MyMemory the translation is best-effort and
relies on the service treating proper nouns as untranslatable.

Translations are cached on disk at `config/Trivia/translations.json`. The first time a
question is asked the mod blocks briefly (up to `translationTimeoutMs`, default 3000ms)
while it fetches the translation; subsequent uses are instant. If the translation can't be
obtained in time the question is posted in English only and the result is cached for next
time.

<h3>Translation providers</h3>

- **`mymemory`** *(default)* - uses the free [MyMemory](https://mymemory.translated.net/)
  HTTP API. No API key needed. Anonymous traffic is rate limited to ~5,000 words/day per IP;
  setting `mymemoryEmail` in the config raises this to ~50,000.
- **`claude`** - uses Anthropic's Claude API for higher-quality translations that respect
  protected terms. Set `claudeApiKey` (and optionally `claudeModel`).
- **`disabled`** - turns off translation; only English is displayed.

If `bilingualMode=false`, no translation is attempted.

<h2>Configuration</h2>

`config/Trivia/config.properties` (generated on first run):

```
quizInterval=600              # seconds between questions (default 10 minutes)
quizTimeOut=120               # seconds before an unanswered question times out
bilingualMode=true            # post each question in two languages
secondaryLanguage=pt-BR       # language to translate into
translationProvider=mymemory  # mymemory | claude | disabled
translationTimeoutMs=3000     # max ms to wait synchronously for a translation
mymemoryEmail=                # optional, raises MyMemory rate limit
claudeApiKey=                 # required if translationProvider=claude
claudeModel=claude-haiku-4-5-20251001
```

<h2>Commands</h2>
<li><b>/trivia reload [trivia.reload]</b> - reload config, questions, rewards & messages</li>
<li><b>/trivia interval (seconds) [trivia.interval]</b> - set the amount of time that should pass between questions</li>
<li><b>/trivia timeout (seconds) [trivia.timeout]</b> - after this many seconds, the question is "timed out" and not answerable</li>
<li><b>/trivia start [trivia.start]</b> - force start a quiz, useful for testing questions/rewards</li>

<h2>Questions & Rewards Files</h2>

On first run, these should be generated automatically, you can change them under /config/Trivia/

Both questions & rewards are under "pools", think of these as categories or difficulty types. Questions under the "easy" pool will give rewards from the "easy" pool, but the same could be done for Pokemon vs Minecraft trivia.

<h3>Example questions.json</h3>

```json
{
  "easy": [
    {
      "question": "What type is Bulbasaur?",
      "answers": [
        "Grass",
        "Poison"
      ]
    }
  ],
  "medium": [
    {
      "question": "What ability does Bulbasaur have?",
      "answers": [
        "Overgrow",
        "Chlorophyll"
      ]
    }
  ]
}
```

<h3>Example rewards.json</h3>

```json
{
  "easy": [
    {
      "item_name": "cobblemon:dawn_stone",
      "display_name": "Dawn Stone",
      "quantity": 1
    }
  ]
}
```

<h3>Editing translations manually</h3>

`config/Trivia/translations.json` is a plain JSON map of source text to translated text,
keyed by language code. You can hand-edit any entry to override the auto-translation:

```json
{
  "pt-BR": {
    "What type is Bulbasaur?": "Qual o tipo de Bulbasaur?"
  }
}
```

Trivia reads the cache on startup and on `/trivia reload`.

<h2>Building from source</h2>

The mod is built with [Fabric Loom](https://fabricmc.net/wiki/tutorial:loom) / Gradle.
Output is a single remapped `.jar` ready to drop into a server's `mods/` directory.

<h3>Ubuntu quickstart (copy-paste)</h3>

End-to-end on a fresh Ubuntu 22.04 / 24.04 install. The Gradle wrapper is committed,
so you only need a JDK - no system Gradle install required. Adjust the repo URL if
you're building from a fork:

```bash
# 1. Install JDK 21 (works for the Java 17 target) and git
sudo apt update
sudo apt install -y openjdk-21-jdk git

# 2. Clone the repo
git clone https://github.com/chloevinky/Trivia.git
cd Trivia

# 3. Build the deployable jar
./gradlew build

# 4. The jar to upload to your server:
ls -lh build/libs/Trivia-*.jar
```

Copy `build/libs/Trivia-1.2.5+1.21.1.jar` into your Fabric server's `mods/` folder
and restart the server. On first launch, `config/Trivia/` is created with the
default questions, rewards and config files.

<h3>Prerequisites</h3>

- **JDK 17 or newer** (the build targets Java 17 bytecode). Verify with `java -version`.
- **Internet access** for the first build - Gradle will download Minecraft, Yarn mappings,
  Fabric API and Adventure into its local cache (a few hundred MB; subsequent builds are fast).

<h3>Build the jar</h3>

```bash
# Linux / macOS
./gradlew build

# Windows
gradlew.bat build
```

The first run downloads dependencies and can take several minutes. The build produces:

```
build/libs/Trivia-<version>.jar           <- the one to deploy
build/libs/Trivia-<version>-sources.jar   <- source attachment, not for runtime
```

With the version in `gradle.properties` (currently `1.2.5+1.21.1`) the deployable file is:

```
build/libs/Trivia-1.2.5+1.21.1.jar
```

<h3>Install on a server</h3>

1. Copy `build/libs/Trivia-<version>.jar` into the server's `mods/` directory.
2. Ensure the server already has **Fabric Loader 0.14.10+** and **Fabric API**
   (`fabric_version` in `gradle.properties` lists the version this build was tested against).
3. Start the server. On first launch, `config/Trivia/` is generated with `config.properties`,
   `questions.json`, `rewards.json` and `messages.json`.

<h3>Troubleshooting the build</h3>

- **`error: invalid target release: 17`** - your `JAVA_HOME` points at JDK < 17. Install a
  newer JDK (Temurin, Adoptium, etc.) and re-export `JAVA_HOME`.
- **Slow first build / partial download failures** - re-run `./gradlew build --refresh-dependencies`.

