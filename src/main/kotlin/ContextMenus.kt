import commands.UserInfo
import commands.ranks.Ranks
import net.dv8tion.jda.api.events.interaction.command.UserContextInteractionEvent

object ContextMenus {
    suspend fun onUserContextInteractionEvent(event: UserContextInteractionEvent) {
        when (event.fullCommandName) {
            "Report" -> event.reply("Sorry, this command is not yet implemented!").setEphemeral(true).queue()
            "Rank" -> {
                event.deferReply().queue()
                Ranks.levelCheckContextVersion(event, event.target)
            }
            "User info" -> {
                event.deferReply().queue()
                UserInfo.userinfoUser(event, event.target)
            }
        }

    }

}