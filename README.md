# RayCord

A next-generation Minecraft proxy based on Velocity, focused on powerful network tooling,
scalability, and quality-of-life features for real server stacks.

RayCord is licensed under the GPLv3 license.

## Highlights

* Rebranded core proxy package: `com.velocitypowered.proxy`
* Built-in `/raycord` root command
* Built-in `/raycord modules` command for module status and hot reload
* Built-in cross-server console bridge:
  `/raycord cmd <server> <command>`
* Extra RayCord configuration in `raycord.toml`
* Built-in modules loaded from `config/modules`

## Goals

* A codebase that is easy to dive into and consistently follows best practices
  for Java projects as much as reasonably possible.
* High performance: handle thousands of players on one proxy.
* A new, refreshing API built from the ground up to be flexible and powerful
  whilst avoiding design mistakes and suboptimal designs from other proxies.
* First-class support for Paper, Sponge, Fabric and Forge. (Other implementations
  may work, but we make every endeavor to support these server implementations
  specifically.)
  
## Building

RayCord is built with [Gradle](https://gradle.org). We recommend using the
wrapper script (`./gradlew`) as our CI builds using it.

It is sufficient to run `./gradlew build` to run the full build cycle.

## Running

Once you've built RayCord, you can copy and run the `-all` JAR from
`proxy/build/libs`. Velocity will generate a default configuration file
and you can configure it from there.

## Modules

RayCord can load built-in modules from `config/modules`.

Enable them in `raycord.toml`:

```toml
[modules]
enabled = true
motd = true
antibot = true
```

Then edit:

* `config/modules/motd.toml`
* `config/modules/antibot.toml`

Use `/raycord modules` to view the current module state, `/raycord modules reload`
to hot reload them, or `/raycord modules preset <balanced|performance|secure>`
to apply a bundled template.

The MOTD module supports:

* multiple MiniMessage lines
* custom shown online and max players
* custom hover sample text

The anti-bot module currently provides:

* IP flood detection with temporary bans
* proxy-side post-login verification timeouts

## Command Bridge

RayCord ships with a built-in backend console bridge over RCON.

1. Configure your backend servers in `velocity.toml` as usual.
2. Add matching RCON credentials in `raycord.toml`.
3. Use:
   ` /raycord cmd survival say RayCord is online `

Example `raycord.toml` section:

```toml
[command-bridge]
enabled = true
connect-timeout = 3000
read-timeout = 3000

[command-bridge.servers.survival]
host = "127.0.0.1"
port = 25575
password = "change-me"
```

# Localisation

Translations are handled using [Crowdin](https://papermc-io.crowdin.com/velocity).
If you want to translate a language not available on Crowdin,
you might want to ask in the [Discord](https://discord.gg/papermc) about it.
