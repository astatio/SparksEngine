package interfaces

import net.dv8tion.jda.api.events.message.MessageReceivedEvent


interface RankMessageReplacement {
    fun toReplace(): String
    fun replacement(event: MessageReceivedEvent): String
    /**
     * In case a certain replacement requires a condition to be met, this function should be used to check it.
     * Useful when you want to create replacements for specific guilds.
     */
    fun condition(event: MessageReceivedEvent): Boolean
    /**
     * String that will be used to inform a moderator about the expected behaviour of the stored message.
     * Should follow the pattern used in the following examples.:
     *
     * `{user}` will be replaced with the user's mention"
     * `{level}` will be replaced with the user's level"
     * `{dog}` will be replaced with a random dog image"
     *
     * Note that \n in the begging or end of the string isn´t necessary
     */
    val thisWill : String
}

