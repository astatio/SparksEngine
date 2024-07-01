![GitHub last commit (branch)](https://img.shields.io/github/last-commit/astatio/SparksEngine) [![](https://jitpack.io/v/astatio/SparksEngine.svg)](https://jitpack.io/#astatio/SparksEngine) ![GitHub top language](https://img.shields.io/github/languages/top/astatio/SparksEngine?logo=kotlin&color=7F52FF) ![Code Climate maintainability](https://img.shields.io/codeclimate/maintainability/astatio/SparksEngine) ![Code Climate technical debt](https://img.shields.io/codeclimate/tech-debt/astatio/SparksEngine) ![Code Climate issues](https://img.shields.io/codeclimate/issues/astatio/SparksEngine)


> [!WARNING]
> At the moment, you might be unable to use this project. This is due to the project being made public before the author could finish working on a feature which, due to an oversight, was being developed on the main branch.

https://github.com/astatio/SparksEngine

# SparksEngine
SparksEngine is the core of the Discord bot DigitalSparks - It's what makes it work - and it's used exactly the same way it's provided here: just like a library. It provides all the tools you need to get started with a bot of your own. Make sure to check the wiki for more information.

## How to use
You add it just like any other typical library to your project. Check out [JitPack guide](https://jitpack.io/) on that. If you wish to know more on how to use make sure to check out the wiki and the Examples folder inside this repository.

### Logging
SparksEngine exposes a lot of the libraries it uses but not for logging. This is to avoid any potential version conflicts. If you wish to use a logger we recommend you add the following libraries: `slf4j-api`, `logback-classic`, `kotlin-logging`. You are free to use any logging library of your choice as long as it works. Check [JDA wiki](https://jda.wiki/setup/logging/) for more information on logging.

## Why is there no kick, ban, unban commands?
Features are made according to a defined priority basis. The moment this repository turns public, these features will be of high priority.

## The bot seems to censor some words in ModLogs
This happens due to liability in community guilds. Even if in the message sent by the bot is written that it was sent by someone else, what matters is that the bot is the one sending them right now. Some words can mean trouble for those guilds, and having a bot approved by the admins saying them is not very desirable. The bot is currently not designed to have this featured turned off or on, but, in the future that will be an option for the library users.
