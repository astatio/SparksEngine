package commands

import dev.minn.jda.ktx.coroutines.await
import helpers.*
import net.dv8tion.jda.api.Permission
import net.dv8tion.jda.api.entities.Message
import net.dv8tion.jda.api.entities.User
import net.dv8tion.jda.api.events.message.MessageReceivedEvent


suspend fun runChecks(event: MessageReceivedEvent, permission: Permission): User? {
    if (!permissionCheck(event.toWrapper(), permission))
        return null
    var user: User? = null
    try {
        user = event.message.mentions.members[0].user
    } catch (e: IndexOutOfBoundsException) {
        // after the first space and before the next space
        val id = event.message.contentDisplay.substringAfter(" ").substringBefore(" ")
        try {
            user = event.jda.retrieveUserById(id).await()
        } catch (e: Throwable) {
            throwEmbed(e, "No mention or valid ID provided")
        }
    }
    return user
}

fun queueSuccess(message: Message, toText: String) {
    message.replyEmbeds(
        NeoSuperEmbed {
            type = SuperEmbed.ResultType.SUCCESS
            text = toText
        }
    ).queue()
}

fun queueFailure(message: Message, toText: String): (Throwable) -> Unit {
    return {
        message.replyEmbeds(
            throwEmbed(it, toText)
        ).queue()
    }
}


suspend fun kick(event: MessageReceivedEvent) {
    val user = runChecks(event, Permission.KICK_MEMBERS) ?: return
    return
}

fun ban(event: MessageReceivedEvent) {
    return
/*    val user = runChecks(event, Permission.BAN_MEMBERS) ?: return
    val message = event.message.contentDisplay
    val banSubstring = // after the first space and before the next space
        if (message.substringAfter(" ").substringBefore(" ") != message)
            message.substringAfter(" ").substringBefore(" ")
        else
            null
    val number = banSubstring?.substring(0, 1)?.toIntOrNull() // if there's no number, it will be null

    // examples: !ban @user 1 adios
    // examples: !ban @user adios
    // examples: !ban @user 1

    //first, check if the user is already banned
    var alreadyBanned = false
    event.guild.retrieveBanList().queue(
        {
            if (it.any { ban -> ban.user == user }) {
                event.channel.sendMessageEmbeds(
                    NeoSuperEmbed {
                        type = SuperEmbed.ResultType.ERROR
                        text = "User is already banned"
                    }
                ).queue()
                alreadyBanned = true
            }
        },
        {
            queueFailure(event.channel, "Error while retrieving ban list")
            alreadyBanned = true // we will assume that the user is already banned
        }
    )
    if (alreadyBanned) return

    // If reasonOrNumber starts with a number, ban for that number of days. Otherwise, ban for 0 days and set the reason as the reasonOrNumber.
    // If reasonOrNumber is more than just a number, ban for the number of days and set the reason as the rest of the string.
    // If reasonOrNumber is null, ban for 0 days and set the reason as "No reason provided".

    if (banSubstring != null && number == null) {
        // example: !ban @user adios
        event.guild.ban(user, 0, banSubstring).queue(
            queueSuccess(event.channel, "User banned"),
            queueFailure(event.channel, "Error while banning user")
        )
        return
    }
    if (banSubstring != null && number != null) {
        // example: !ban @user 1 adios
        event.guild.ban(user, number, banSubstring.substringAfter(" ")).queue(
            queueSuccess(event.channel, "User banned"),
            queueFailure(event.channel, "Error while banning user")
        )
        return
    }
    // since we've returned the other possibilities, we dont need another if statement
    // example: !ban @user
    event.guild.ban(user, 0, "No reason provided").queue(
        queueSuccess(event.channel, "User banned"),
        queueFailure(event.channel, "Error while banning user")
    )
    return*/
}

suspend fun unban(event: MessageReceivedEvent) {
    val user = runChecks(event, Permission.BAN_MEMBERS) ?: return
    // examples: !unban [id]
    val channel = event.channel
    event.guild.unban(user).queue(
        {
            //todo: Ask if they would like to send an invite to the user. It was one of my original ideas in my
            // first bot.
            queueSuccess(event.message, "User unbanned")

        },
        { queueFailure(event.message, "Error while unbanned user") }
    )
}