package helpers

import net.dv8tion.jda.api.EmbedBuilder
import net.dv8tion.jda.api.Permission
import net.dv8tion.jda.api.entities.Member
import net.dv8tion.jda.api.entities.Message
import net.dv8tion.jda.api.entities.MessageEmbed
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent
import net.dv8tion.jda.api.events.message.MessageReceivedEvent
import org.jetbrains.kotlin.util.classNameAndMessage
import java.util.*
import kotlin.reflect.KFunction
import kotlin.reflect.full.callSuspend

/**
 * Command 1st argument member validation. It will go through the following steps:
 * 1. Check if a member is mentioned. If so, execute [ifMentioned] and pass [event] and the member.
 * 2. Check if the message contains a user ID after a whitespace. If so, execute [ifUserID] and pass [event] and the user ID.
 * 3. If none of the above is true, execute [ifNoMentionNorID]. This is different from the other two in that it is a suspend function.
 * @param ifMentioned The function to be executed if the member is mentioned.
 * @param ifUserID The function to be executed if the user ID is found.
 * @param ifNoMentionNorID The function to be executed if the member is not mentioned and no user ID is found.
 *
 *
 * **Note: This is designed to validate members! If you want to validate users, use [commandUserValidation] instead.**
 * @see commandUserValidation
 */
suspend fun commandMemberValidation(
    event: MessageReceivedEvent,
    ifMentioned: KFunction<Unit>,
    ifUserID: KFunction<Unit>,
    ifNoMentionNorID: suspend () -> Unit
) {
    if (event.message.mentions.members.isNotEmpty()) {
        // If the user mentions someone.
        val member = event.message.mentions.members[0]!!
        ifMentioned.callSuspend(event, member)
    } else {
        //If it does not have an userID after a whitespace, then it will return the original message
        //the original message should then be either "!a" or "!avatar"
        if (event.message.contentDisplay.substringAfter(" ") != event.message.contentDisplay) {
            val userID = event.message.contentDisplay.substringAfter(" ")
            ifUserID.callSuspend(event, userID)
        } else {
            ifNoMentionNorID()
        }
    }
}

/**
 * Command 1st and so on argument member validation. It will go through the following steps:
 * 1. Check if a member is mentioned. If so, execute [ifMentioned] and pass [event] and the member.
 * 2. Check if the message contains a user ID after a whitespace. If so, execute [ifUserID] and pass [event] and the user ID.
 * 3. If none of the above is true, execute [ifNoMentionNorID]. This is different from the other two in that it is a suspend function.
 * @param ifMentioned The function to be executed if members are mentioned. *Must support a list of members*
 * @param ifUserID The function to be executed if user IDs are found. *Must support a list of strings*
 * @param ifNoMentionNorID The function to be executed if the member is not mentioned and no user ID is found.
 *
 * Based on [commandMemberValidation] but meant to be used when many member arguments are expected.
 *
 * **Note: This is designed to validate members! If you want to validate users, use [commandUserValidation] instead.**
 * @see commandUserValidation
 */
suspend fun commandManyMembersValidation(
    event: MessageReceivedEvent,
    ifMentioned: KFunction<Unit>,
    ifUserID: KFunction<Unit>,
    ifNoMentionNorID: suspend () -> Unit
) {
    if (event.message.mentions.members.isNotEmpty()) {
        // If the user mentions someone.
        val members = event.message.mentions.members
        ifMentioned.callSuspend(event, members)
    } else {
        if (event.message.contentDisplay.substringAfter(" ") != event.message.contentDisplay) {
            val userIDs = mutableListOf<String>()
            event.message.contentDisplay.split(" ").forEach {
                userIDs.add(it)
            }
            ifUserID.callSuspend(event, userIDs)
        } else {
            ifNoMentionNorID()
        }
    }
}

/**
 *
 * Command 1st argument user validation. It will go through the following steps:
 * 1. Check if a user is mentioned. If so, execute [ifMentioned] and pass [event] and the user.
 * 2. Check if the message contains a user ID after a whitespace. If so, execute [ifUserID] and pass [event] and the user ID.
 * 3. If none of the above is true, execute [ifNoMentionNorID]. This is different from the other two in that it is a suspend function.
 * @param ifMentioned The function to be executed if the user is mentioned.
 * @param ifUserID The function to be executed if the user ID is found.
 * @param ifNoMentionNorID The function to be executed if the user is not mentioned and no user ID is found.
 *
 *
 * **Note: This is designed to validate users! If you want to validate members, use [commandMemberValidation] instead.**
 * @see commandMemberValidation
 */
suspend fun commandUserValidation(
    event: MessageReceivedEvent,
    ifMentioned: KFunction<Unit>,
    ifUserID: KFunction<Unit>,
    ifNoMentionNorID: suspend () -> Unit
) {
    if (event.message.mentions.users.isNotEmpty()) {
        // If the user mentions someone, then the bot will get the avatar of the mentioned user.
        // This works for people that already left the server. Shouldn't be used frequently but it's foolproof.
        val user = event.message.mentions.users[0]
        ifMentioned.callSuspend(event, user)
    } else {
        //If it does not have an userID after a whitespace, then it will return the original message
        //the original message should then be either "!a" or "!avatar"
        if (event.message.contentDisplay.substringAfter(" ") != event.message.contentDisplay) {
            val userID = event.message.contentDisplay.substringAfter(" ")
            ifUserID.callSuspend(event, userID)
        } else {
            ifNoMentionNorID()
        }
    }
}


/**
 * Checks if the message contains more than 1024 characters (the max allowed in embed fields) and reduces it to 1024 characters if it does.
 * The last 3 characters of [string] are replaced with "..." if shortening was necessary.
 *
 * @param string The message to check
 */
fun checkAndReduce(string: String): String {
    return if (string.length > 1024) {
        string.substring(0, 1020) + "..."
    } else {
        string
    }
}

/**
 * Checks if the message contains more than [quantity] characters and reduces it to [quantity] characters if it does.
 * The last 3 characters of [string] are replaced with "..." if shortening was necessary.
 *
 * @param string The message to check
 * @param quantity The maximum amount of characters allowed
 */
fun checkAndReduce(string: String, quantity: Int): String {
    return if (string.length > quantity) {
        string.substring(0, (quantity - 4)) + "..."
    } else {
        string
    }
}



/**
 * Checks if the URLs combined are more than 1024 characters (the max allowed in embed fields) and reduces it to 1024 characters if it does.
 * If the combination with the upcoming URL is more than 1024 characters, it will return the string as it is without adding such URL.
 * Each URL is separated by a newline.
 *
 * @return Pair<String, Boolean> The first value is the string with the URLs separated by a newline and the second value is a boolean indicating if the string was reduced.
 */
@JvmName("reduceAndNewlineMessageAttachmentUrls")
fun List<Message.Attachment>.reduceAndNewlineUrls(): Pair<String, Boolean> {
    var trackStringSize = 0
    var attachmentsString = ""
    var reductionCheck = false
    // the value cant be longer than 1024 characters
    var i = 0
    while (trackStringSize <= 1024 && i <= this.lastIndex) {
        this[i].let {
            trackStringSize += it.url.length + 1
            if (trackStringSize <= 1024) {
                attachmentsString += "${it.url}\n"
            }
            else {
                reductionCheck = true
                return@let
            }
        }
        i++
    }
    return attachmentsString to reductionCheck
}

/**
 * Checks if the URLs combined are more than 1024 characters (the max allowed in embed fields) and reduces it to 1024 characters if it does.
 * If the combination with the upcoming URL is more than 1024 characters, it will return the string as it is without adding such URL.
 * Each URL is separated by a newline.
 *
 * @return Pair<String, Boolean> The first value is the string with the URLs separated by a newline and the second value is a boolean indicating if the string was reduced.
 */
fun List<String>.reduceAndNewlineUrls(): Pair<String, Boolean> {
    var trackStringSize = 0
    var attachmentsString = ""
    var reductionCheck = false
    // the value cant be longer than 1024 characters
    var i = 0
    while (trackStringSize <= 1024 && i <= this.lastIndex) {
        this[i].let {
            trackStringSize += it.length + 1
            if (trackStringSize <= 1024) {
                attachmentsString += "$it\n"
            } else {
                reductionCheck = true
                return@let
            }
        }
        i++
    }
    return attachmentsString to reductionCheck
}


inline fun NeoSuperEmbed(block: SuperEmbed.() -> Unit): MessageEmbed = SuperEmbed().apply(block).build()

/**
 * ThrowEmbed was designed to be inside a try-catch to make the user aware of a problem with their command.
 * It requires 2 arguments:
 *
 * **throwable** : Throwable?
 *
 * **text** : String
 */
fun throwEmbed(
    throwable: Throwable?,
    textMsg: String = "An error occurred."
) = NeoSuperEmbed {
    val maxSize = 1024 - (textMsg.length + 36) //its 33 if we take into account \n as 2 characters and ```as 3 characters
    type = SuperEmbed.ResultType.ERROR
    text = "$textMsg The error message is below.\n```${checkAndReduce(throwable?.classNameAndMessage ?: "No error message.", maxSize)}```"
}

fun NeoSuperCommand(block: SuperCommand.() -> Unit) = SuperCommand().apply(block).build()

open class SuperEmbed : EmbedBuilder() {

    // Make a simpler enum for the result type
    enum class ResultType {
        ERROR,
        SIMPLE_ERROR,
        SIMPLE,
        SUCCESS,
        ALERT
    }

    private var title: String? = null
    private var color: Int = 0
    internal var name: String = ""
    internal var description: String? = null

    // the following variables must be set in order to work
    open var text: String = ""
        set(value) {
            field = when (type) {
                ResultType.SIMPLE -> {
                    description = "\uD83D\uDCDD $value"
                    ""
                }
                ResultType.SUCCESS -> {
                    description = "<a:acceptedbox:755935963875901471> $value"
                    ""
                }
                ResultType.SIMPLE_ERROR -> {
                    description = "<a:deniedbox:755935838851956817> $value"
                    ""
                }
                else -> {
                    value
                }
            }
        }

    open var type: ResultType = ResultType.SIMPLE
        set(value) {
            field = value
            when (value) {
                ResultType.ERROR -> {
                    title = "<a:deniedbox:755935838851956817>"
                    color = 0xFF0000
                    name = "Error"
                }

                ResultType.SIMPLE_ERROR -> {
                    color = 0xFF0000
                }

                ResultType.SIMPLE -> {
                    // This is the simple type and works similar to Dyno's embeds
                    // Dyno uses description with empty title
                    // This is also colorless on dark mode
                    color = 0x36393F
                }

                ResultType.SUCCESS -> {
                    color = 0x43b481
                }

                ResultType.ALERT -> {
                    color = 0xF58220
                }
            }
        }

    override fun build(): MessageEmbed = EmbedBuilder().apply {
        title?.let { setTitle(title) }
        setDescription(description)
        setColor(color)
        if (text.isNotEmpty()) {
            addField(name, checkAndReduce(text), false)
        }
    }.build()

}

class SuperCommand : EmbedBuilder() {
    var triggers: Array<String> = arrayOf()
    var name: String = ""
    var description = ""
    private var subCommands: MutableMap<String, String> = mutableMapOf()

    fun subcommands(init: MutableMap<String, String>.() -> Unit): MutableMap<String, String> {
        subCommands = mutableMapOf()
        subCommands.init()
        return subCommands
    }

    override fun build(): MessageEmbed = EmbedBuilder().apply {
        setTitle(name.replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.getDefault()) else it.toString() })
        setDescription(description)
        setColor(0x04ab04) // This is my other bot's characteristic green color
        for (command in subCommands) {
            // example: "!timeout default [time]"
            // "Change default timeout for the server."
            addField("${triggers[0]} ${command.key}", command.value, false)
        }
        setAuthor(
            "$name Command Menu",
            null,
            "https://cdn.discordapp.com/avatars/294972852837548034/6aa8a8898ea313787fda95a9cf450274.webp"
        )
    }.build()

}

//create a permission check without message
fun permissionCheckMessageless(member: Member, permission: Permission): Boolean {
    // im now included as if I was an administrator
    return if (!member.hasPermission(permission)) {
        if (member.user.idLong in owners) {
            return true
        }
        false
    } else {
        true
    }
}

fun SlashCommandInteractionEvent.toWrapper(): EventWrapper.SlashCommandEvent =
    EventWrapper.SlashCommandEvent(this)

fun MessageReceivedEvent.toWrapper(): EventWrapper.MessageEvent =
    EventWrapper.MessageEvent(this)

sealed class EventWrapper {
    abstract val member: Member?

    data class MessageEvent(val event: MessageReceivedEvent) : EventWrapper() {
        override val member get() = event.member
        fun replyEmbeds(embed: MessageEmbed) = event.message.replyEmbeds(embed)
    }

    data class SlashCommandEvent(val event: SlashCommandInteractionEvent) : EventWrapper() {
        override val member get() = event.member
        fun editOriginalEmbeds(embed: MessageEmbed) = event.hook.editOriginalEmbeds(embed)
    }
}

fun permissionCheck(wrapper: EventWrapper, permission: Permission): Boolean {
    // im now included as if I was an administrator
    return if (wrapper.member?.hasPermission(permission) == false) {
        if (wrapper.member?.user?.idLong in owners) {
            return true
        }
        val embed = NeoSuperEmbed {
            text = "You don't have the **${permission.getName()}** permission to use this command."
            type = SuperEmbed.ResultType.SIMPLE_ERROR
        }
        when (wrapper) {
            is EventWrapper.MessageEvent -> wrapper.replyEmbeds(embed).queue()
            is EventWrapper.SlashCommandEvent -> wrapper.editOriginalEmbeds(embed).queue()
        }
        false
    } else {
        true
    }
}
