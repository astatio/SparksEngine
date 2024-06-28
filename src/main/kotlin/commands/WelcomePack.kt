package commands


/*
object WelcomePack {


    */
/*
    Sends a welcome message when member gain access to a specific channel.
    Can be used to send welcome messages to new members and returning members, for example.
    Note: The roles that restrict access must already exist.
    The bot will just add them to the members accordingly.

    This is independent from other commands.

    How it works:
    - Whenever someone gets access to specific channel, the bot will send a message to that channel according to the settings.
    *//*



//todo: perhaps i should add a way to make it work on other channels? A guild could have many channels.
    data class WelcomeSettings(
        val status: Boolean, //by default, this is false
        val firstTimeChannel: Long, //channel ID for the first time members. This is where the bot will send a message when a new member joins (if given access to the channel)

        val returningChannel: Long, //channel ID for the returning members. This is where the bot will send a message when a returning member joins (if given access to the channel)
        val guildId: Long
    ) {
        companion object {
            fun create(guildId: Long): Array<org.bson.conversions.Bson> {
                return arrayOf(
                    setOnInsert(WelcomeSettings::status, false),
                    setOnInsert(WelcomeSettings::guildId, guildId)
                ) //todo: doesnt have the channel IDs as that cant be null
            }
        }
    }

    //todo: i should transform the !leaderboard navigational embed into a class or an interface

    data class WelcomeChannels(
        val id: Long, //channel ID
        val message: String //message to be sent
    )


    class Chamber : RealmObject {
        var channel: String = "" // The channel ID of the channel to send the message to.
        var role: String = "" // The role id
        var message: String = ""
    }

    class Gate : RealmObject {
        //New member settings
        var newMemberChamber: Pair<Long, Long>? =
            null // A pair of ChannelID and RoleID. This restricts access to the new member and allows the bot to send a message on the channel meant for new members
        var newMemberStatus = false // If true, the bot will send a message to the new member channel.
        var newMemberMessage = ""

        //Returning member settings
        var returningMemberChannel: Long? =
            null // A pair of Channel and Role Id. This restricts access to the returning member and allows the bot to send a message on the channel meant for returning members
        var returningMemberRole: Long? = null
        var returningMemberStatus = false // If true, the bot will send a message to the returning member channel.
        var returningMemberMessage = ""

        var minRank: Int =
            0 // The minimum rank level required to be considered a returning member. 0 means no rank required and a small ammount of xp will be enough to be considered a returning member.
        var realmGuild: RealmGuild? = null
    }

    private val configuration =
        RealmConfiguration.Builder(
            schema = setOf(RealmGuild::class, Gate::class, Chamber::class)
        ).build()


}

*/

