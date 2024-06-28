import net.dv8tion.jda.api.events.message.MessageReceivedEvent

fun onPrivateCommand(event: MessageReceivedEvent) {
    if (event.author.idLong !in owners) {
        return
    }
    val content = event.message.contentDisplay
    val parts = content.split(" ", limit = 2)
    when (content.substringBefore(" ")) {
        "!guilds" -> CommandsMisc.getGuilds(event)
        "!leaveguild" -> CommandsMisc.leaveGuild(parts.getOrNull(1), event)
        "!shutdown" -> CommandsMisc.shutdown(event)
        "!experiments" -> CommandsMisc.experiments(event)
        "!throw" -> {
            event.jda.retrieveUserById("null").queue()
        }
        else -> return
    }
}
