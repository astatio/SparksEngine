import com.mongodb.client.model.Filters.and
import com.mongodb.client.model.Filters.eq
import com.mongodb.client.model.UpdateOptions
import com.mongodb.client.model.Updates
import com.mongodb.client.model.Updates.setOnInsert
import commands.core.Alerts
import commands.core.sendAlert
import dev.minn.jda.ktx.coroutines.await
import dev.minn.jda.ktx.events.await
import dev.minn.jda.ktx.messages.Embed
import dev.minn.jda.ktx.messages.InlineEmbed
import helpers.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.withTimeoutOrNull
import net.dv8tion.jda.api.audit.ActionType
import net.dv8tion.jda.api.audit.AuditLogEntry
import net.dv8tion.jda.api.entities.Guild
import net.dv8tion.jda.api.entities.Member
import net.dv8tion.jda.api.entities.Message
import net.dv8tion.jda.api.entities.User
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel
import net.dv8tion.jda.api.entities.channel.middleman.MessageChannel
import net.dv8tion.jda.api.entities.channel.unions.GuildChannelUnion
import net.dv8tion.jda.api.events.guild.GuildAuditLogEntryCreateEvent
import net.dv8tion.jda.api.events.guild.member.GuildMemberJoinEvent
import net.dv8tion.jda.api.events.guild.member.GuildMemberRemoveEvent
import net.dv8tion.jda.api.events.guild.member.update.GuildMemberUpdateNicknameEvent
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent
import net.dv8tion.jda.api.events.message.MessageBulkDeleteEvent
import net.dv8tion.jda.api.events.message.MessageDeleteEvent
import net.dv8tion.jda.api.events.message.MessageUpdateEvent
import net.dv8tion.jda.api.events.message.react.MessageReactionAddEvent
import net.dv8tion.jda.api.interactions.components.buttons.Button
import net.dv8tion.jda.api.utils.TimeFormat
import net.dv8tion.jda.api.utils.messages.MessageEditData
import java.time.Instant
import java.util.*
import kotlin.time.Duration.Companion.seconds

object ModLog {


    data class Ping(
        val userToPing: Long, // The user ID that is going to be pinged
        val userToReturn: Long, // The user ID that triggers this ping
        val guildId: Long
    )

    data class Modlog(
        val toggle: Boolean, // Whether or not the modlog is enabled. By default, it's false.
        val ltsToggle: Boolean, // Whether the LastTimeStorage is enabled. By default, it's false.
        val one: Long, // Channel for joins, leaves, kicks, bans and unbans.
        val two: Long, // General Modlog channel for everything not included in getChannel1
        val guildId: Long
    )

    data class MembersLTS(
        val memberID: Long,
        val lastTime: Date,
        val rolesList: List<Long>,
        val guildId: Long
    )

    val modlogCollection = database!!.getCollection<Modlog>("modlog")
    val pingCollection = database!!.getCollection<Ping>("ping")
    val membersLTSCollection = database!!.getCollection<MembersLTS>("memberLts")

    val recentlyLeftMembersCache = ScheduledDeque<Member>()


    suspend fun setModlogStatus(newStatus: Boolean, guildId: Long) {
        modlogCollection.updateOne(
            filter = eq(Modlog::guildId.name, guildId),
            update = Updates.combine(
                setOnInsert(Modlog::ltsToggle.name, false),
                setOnInsert(Modlog::one.name, null),
                setOnInsert(Modlog::two.name, null),
                setOnInsert(Modlog::guildId.name, guildId),
                Updates.set(Modlog::toggle.name, newStatus)
            ),
            UpdateOptions().upsert(true)
        )
    }

    suspend fun getModlog(guildId: Long) = modlogCollection.find(eq(Modlog::guildId.name, guildId)).firstOrNull()

    /**
     * Get modlog status. Returns false if the guild is not in the database.
     *
     * @param guildId The guild ID as a Long to get the status for
     */
    private suspend fun getModlogStatus(guildId: Long) =
        modlogCollection.find(eq(Modlog::guildId.name, guildId)).firstOrNull()?.toggle ?: false

    /**
     * Get modlog Last Time Storage status. Returns false if the guild is not in the database.
     *
     * @param guildId The guild ID as a Long to get the status for
     */
    suspend fun checkLTStatus(guildId: Long) =
        modlogCollection.find(eq(Modlog::guildId.name, guildId)).firstOrNull()?.ltsToggle ?: false

    /**
     * Meant to store a member's roles when they leave the server.
     * Can be used to store roles at any time upon usage.
     * It's advised to use [checkLTStatus] before using this function as it will store the roles regardless of the toggle status.
     * @param member The member to store roles for.
     */
    suspend fun storeMemberLT(member: Member) {
        // Check if the member exists in the collection
        membersLTSCollection.updateOne(
            filter = and(
                eq(MembersLTS::guildId.name, member.guild.idLong),
                eq(MembersLTS::memberID.name, member.idLong)
            ),
            update = Updates.combine(
                setOnInsert(MembersLTS::memberID.name, member.idLong),
                Updates.set(MembersLTS::lastTime.name, Date()),
                Updates.set(MembersLTS::rolesList.name, member.roles.map { it.idLong }),
                setOnInsert(MembersLTS::guildId.name, member.guild.idLong)
            ),
            UpdateOptions().upsert(true)
        )
    }

    /**
     * Get a member's roles in a pretty list used in ModLogs embeds.
     *
     * @param member Member
     * @return List-like roles as mentions with a line for each one
     */
    private fun roles(member: Member?): String {
        if (member == null) {
            return "\n**Roles Not Cached**"
        }
        var roles = ""
        for (i in member.roles) {
            roles += "${i.asMention}\n"
        }
        return "\n**Roles (${member.roles.size}):**\n${roles}"
    }

    /**
     * Get a returning member's roles in a pretty list used in ModLogs embeds.
     * This only works if LastTimeStorage is enabled.
     *
     * **Note: It will be empty if it's the first time the member joins the guild.**
     *
     * @param member Member
     * @return List-like roles as mentions with a line for each one
     */
    private suspend fun getStoredLTRoles(member: Member): String {
        // Query for the LastTimeStorageSettings toggle
        val settingsToggle =
            modlogCollection.find(
                eq(Modlog::guildId.name, member.guild.idLong)
            ).firstOrNull()?.ltsToggle

        // If toggle is true, retrieve the roles for the member
        if (settingsToggle == true) {
            membersLTSCollection.find(
                and(
                    eq(MembersLTS::guildId.name, member.guild.idLong),
                    eq(MembersLTS::memberID.name, member.idLong)
                )
            ).firstOrNull()?.rolesList.let { rolesList ->
                if (rolesList != null) {
                    return "\n**Roles (${rolesList.size}):**\n${rolesList.joinToString("\n") { "<@&$it>" }}"
                }
            }
        }
        return ""
    }


    /**
     * Get a returning member's last time in the guild. This only works if LastTimeStorage is enabled.
     *
     * **Note: It will be null if it's the first time the member joins the guild OR if the toggle is OFF.**
     *
     * @param member Member
     * @return Nullable String meant to be parsed
     */
    private suspend fun getMemberLT(member: Member): Date? {
        // Check the LastTimeStorageSettings toggle
        val settingsToggle = modlogCollection.find(
            eq(Modlog::guildId.name, member.guild.idLong)
        ).firstOrNull()?.ltsToggle

        // If toggle is true, retrieve the lastTime for the member
        if (settingsToggle == true) {
            return membersLTSCollection.find(
                and(
                    eq(MembersLTS::guildId.name, member.guild.idLong),
                    eq(MembersLTS::memberID.name, member.idLong)
                )
            ).firstOrNull()?.lastTime
        }
        return null
    }

    /**
     * Get the channel ID for the given channel number.
     *
     * @param channelNumber The channel number to get the ID for. Must be 1 or 2.
     */
    private suspend fun getChannel(channelNumber: Int, guildId: Long): Long? {
        if (channelNumber !in 1..2)
            throw IllegalArgumentException("Channel number must be 1 or 2")

        // Query the collection based on guildId
        val channelData = modlogCollection.find(eq(Modlog::guildId.name, guildId)).firstOrNull()

        // Return the corresponding channel based on channelNumber
        return when (channelNumber) {
            1 -> channelData?.one
            2 -> channelData?.two
            else -> throw IllegalArgumentException("Channel number must be 1 or 2")  // This line is technically redundant due to the earlier check, but I've kept it for consistency with your original code.
        }
    }

    private val GET_MOD_REGEX by lazy { "([a-z]+)#0".toRegex() }
    private val GET_MOD_ID_REGEX by lazy { "\\(ID ([1-9]+)\\)".toRegex() }
    private val GET_REASON_REGEX by lazy { "Reason: (.+)\\z".toRegex() }

    /**
     * Get the author's tag from an Audit Log Entry reason.
     *
     * @param reason String containing the author's tag
     * @return Nullable string containing the author's tag. Null if a username ending with #0 is not found.
     */
    private fun getModeratorTag(reason: String) = GET_MOD_REGEX.find(reason)?.groups?.get(1)?.value

    private fun getModeratorId(reason: String) = GET_MOD_ID_REGEX.find(reason)?.groups?.get(1)?.value

    /**
     * Get the reason from an Audit Log Entry reason.
     *
     * @param reason String containing the reason
     * @return Nullable string containing the reason. Null if a reason is not found.
     */
    private fun getReason(reason: String) = GET_REASON_REGEX.find(reason)?.groups?.get(1)?.value


    private inline fun embedBuilder(member: Member?, user: User?, block: InlineEmbed.() -> Unit) = Embed {
        timestamp = Instant.now()
        thumbnail = member?.user?.effectiveAvatarUrl ?: user?.effectiveAvatarUrl
        footer(name = "User ID: " + (member?.id ?: user?.id))
        block()
    }


    private inline fun embedBuilder(member: Member, block: InlineEmbed.() -> Unit) = Embed {
        timestamp = Instant.now()
        thumbnail = member.user.effectiveAvatarUrl
        footer(name = "User ID: ${member.id}")
        block()
    }

    inline fun embedBuilder(message: Message, block: InlineEmbed.() -> Unit) = Embed {
        timestamp = Instant.now()
        thumbnail = message.author.effectiveAvatarUrl
        footer(name = "User ID: ${message.author.id} • Message ID: ${message.id}")
        block()
    }

    private inline fun embedBuilder(message: MessageLite?, block: InlineEmbed.() -> Unit) = Embed {
        timestamp = Instant.now()
        thumbnail = message?.effectiveAvatarUrl
        footer(name = "User ID: ${message?.authorID ?: "Unknown"} • Message ID: ${message?.id ?: "Unknown"}")
        block()
    }

    suspend fun switch(event: SlashCommandInteractionEvent, mode: Boolean) {
        modlogCollection.updateOne(
            filter = eq(Modlog::guildId.name, event.guild!!.idLong),
            update = Updates.combine(
                setOnInsert(Modlog::ltsToggle.name, false),
                setOnInsert(Modlog::one.name, null),
                setOnInsert(Modlog::two.name, null),
                setOnInsert(Modlog::guildId.name, event.guild!!.idLong),
                Updates.set(Modlog::toggle.name, mode)
            ),
            UpdateOptions().upsert(true)
        )

        event.hook.sendMessageEmbeds(NeoSuperEmbed {
            type = SuperEmbed.ResultType.SUCCESS
            text = "**Modlogs** is now ${if (mode) "ON" else "OFF"}"
        }).queue()
    }

    suspend fun switchLT(event: SlashCommandInteractionEvent, mode: Boolean) {
        val guildId = event.guild!!.idLong
        val settings = getModlog(guildId)

        if (settings == null || !settings.toggle) {
            event.hook.sendMessageEmbeds(NeoSuperEmbed {
                type = SuperEmbed.ResultType.SIMPLE_ERROR
                text = "Modlogs is not enabled. Please enable it first."
            }).queue()
            return
        }

        modlogCollection.updateOne(
            filter = eq(Modlog::guildId.name, event.guild!!.idLong),
            update = Updates.set(
                Modlog::ltsToggle.name, mode
            ),
            UpdateOptions().upsert(true)
        )

        event.hook.sendMessageEmbeds(NeoSuperEmbed {
            type = SuperEmbed.ResultType.SUCCESS
            text = "**LastTimeStorage** is now ${if (mode) "ON" else "OFF"}"
        }).queue()
    }

    private val censoredWords = listOf("nigg", "retard", "chink", "kike", "fag", "tard") // swear words
    private val censoredPatternRegex = censoredWords.map { Regex(it, RegexOption.IGNORE_CASE) }

    fun censor(string: String): String {
        // What should happen to a message with n-word: n--ga bitch lasagna
        // Return an empty string if msg is empty. Example: empty nicknames
        if (string.isEmpty()) return ""

        var censoredString = string
        censoredPatternRegex.forEach { pattern ->
            censoredString = pattern.replace(censoredString) { match ->
                val word = match.value
                if (word.length <= 2)
                    word
                else
                    word.first() + "-".repeat(word.length - 2) + word.last()

            }
        }
        return censoredString
    }

    fun storeMemberInCache(event: GuildMemberRemoveEvent) {
        recentlyLeftMembersCache.addScheduled(event.member!!, 30)
    }

    private fun Member.nicknameValidation() =
        if (this.nickname != null)
            "- ${censor(this.nickname!!)}"
        else
            ""


    private val AuditLogEntry.moderator: String
        get() {
            val author = this.jda.getUserById(this.userIdLong)
            if (author?.isBot == true)
                return this.reason?.let { getModeratorTag(it ) } ?: author.name
            return author?.name ?: "Unknown"
        }

    private val AuditLogEntry.moderatorId: String
        get() {
            val author = this.jda.getUserById(this.userIdLong)
            if (author?.isBot == true)
                return this.reason?.let { getModeratorId(it) } ?: author.id
            return author?.name ?: 0.toString()
        }

    private val AuditLogEntry.extractedReason: String?
        get() {
            val author = this.jda.getUserById(this.userIdLong)
            if (author?.isBot == true)
                return this.reason?.let { getReason(it) }
            return this.reason
        }

    private fun AuditLogEntry.reasonValidation() : String = this.extractedReason?.let {
            "\n**Reason:** $it"
        } ?: ""

    private suspend fun invalidChannelFound(channelNumber: Int, invalidChannelID: Long, guild: Guild) {
        sendAlert {
            command = this::class
            severity = Alerts.Severity.IMPORTANT
            message =
                "The modlog channel #$channelNumber for this guild is no longer valid. Please set a new one with `/modlog set`. ModLog will now be disabled."
            additionalInfo {
                "Invalid Channel ID" to invalidChannelID
            }
            this.guild = guild
        }.andThen { setModlogStatus(false, guild.idLong) }
    }

    private fun User.nicknameValidation() =
        if (this.globalName != null)
            "- ${censor(this.globalName!!)}"
        else
            ""

    suspend fun onGuildAuditLogEntryCreate(event: GuildAuditLogEntryCreateEvent) {
        val guildId = event.guild.idLong
        if (!getModlogStatus(guildId)) return
        val targetID = event.entry.targetId
        when (event.entry.type) {
            ActionType.BAN -> {
                //delay execution so there's enough time to store the member in the recentlyLeftMemberCache()
                //2 seconds should be enough
                delay(1000)
                val member = recentlyLeftMembersCache.deque.find { it.id == targetID }

                // Member is null if they were not cached but thats unlikely to happen.
                // Shadow bans obviously won't be cached.
                member?.let {
                    recentlyLeftMembersCache.deque.remove(member)
                }

                // Only do the following if it Member Last Time Storage is enabled. We don't want to store information for
                // servers that don't have it enabled. This should become a premium feature in DigitalSparks.
                if (checkLTStatus(guildId)) { // If LTS is enabled, store the member's roles
                    if (member != null) {
                        storeMemberLT(member)
                    }
                }

                val pee = event.entry.reasonValidation()
                println(pee)

                println(event.entry.reasonValidation())

                val emb = member?.let {
                    embedBuilder(it) {
                        title = "Member Banned"
                        description =
                            "**${member.asMention} - ${member.user.name} ${member.nicknameValidation()}** got banned by ${event.entry.moderator}" +
                                    "${event.entry.reasonValidation()}${
                                        roles(member)
                                    }"
                        color = 0xD0021B
                    }
                } ?: run {
                    val user = event.jda.retrieveUserById(targetID).await()
                    embedBuilder(null, user) {
                        title = "User Banned"
                        description =
                            "**${user.asMention} - ${user.name} ${user.nicknameValidation()}** got banned by ${event.entry.moderator}${event.entry.reasonValidation()}"
                        color = 0xD0021B
                    }
                }

                val channel: TextChannel = getChannel(1, guildId)?.let { channelID ->
                    event.guild.getTextChannelById(channelID) ?: run {
                        invalidChannelFound(1, channelID, event.guild)
                        null
                    }
                } ?: return
                channel.sendMessageEmbeds(emb)
                    .setActionRow(
                        Button.secondary("modify-reason-$targetID-${event.entry.moderatorId}", "Modify reason")
                    )
                    .queue()
            }

            ActionType.KICK -> {
                delay(1000)
                val member = recentlyLeftMembersCache.deque.find { it.id == targetID }

                if (member == null) {
                    logger.throwing(
                        IllegalStateException(
                            "This is unexpected - the member is null. " +
                                    "The member was kicked but is not in the recentlyLeftMembersCache." +
                                    "Is the event being fired with over 30 seconds of delay?"
                        ),
                    )
                    return
                }
                recentlyLeftMembersCache.deque.remove(member)

                if (checkLTStatus(guildId)) { // If LTS is enabled, store the member's roles
                    storeMemberLT(member)
                }
                val emb = embedBuilder(member) {
                    title = "Member Kicked"
                    description =
                        "**${member.asMention} - ${member.user.name} ${member.nicknameValidation()}** got kicked by ${event.entry.moderator}" +
                                "${event.entry.reasonValidation()}${
                                    roles(member)
                                }"
                    color = 0xF5A623
                }

                // Kicked members need to be added to the EntryController in order to be considered returning members.
                val channel: TextChannel = getChannel(1, guildId)?.let { channelID ->
                    event.guild.getTextChannelById(channelID) ?: run {
                        invalidChannelFound(1, channelID, event.guild)
                        null
                    }
                } ?: return
                channel.sendMessageEmbeds(emb)
                    .setActionRow(
                        Button.primary("ping-on-return-$targetID", "Ping on return"),
                        Button.secondary("modify-reason-$targetID-${event.entry.moderatorId}", "Modify reason")
                    ).queue()
            }

            ActionType.UNBAN -> {
                val user = event.jda.retrieveUserById(targetID).await()
                //Unbanned users are meant to become members again, so they should be added to Returning Members.
                val emb = embedBuilder(null, user) {
                    title = "User Unbanned"
                    description =
                        "**${user.asMention} - ${user.name} ${user.nicknameValidation()}** got unbanned by ${event.entry.moderator}"
                    color = 0x4A90E2
                }

                val channel: TextChannel = getChannel(1, guildId)?.let { channelID ->
                    event.guild.getTextChannelById(channelID) ?: run {
                        invalidChannelFound(1, channelID, event.guild)
                        null
                    }
                } ?: return
                channel.sendMessageEmbeds(emb)
                    .setActionRow(Button.primary("ping-on-return-$targetID", "Ping on return")).queue()
            }

            ActionType.MESSAGE_PIN -> {
                val modlogChannel: TextChannel = getChannel(2, guildId)?.let { channelID ->
                    event.guild.getTextChannelById(channelID) ?: run {
                        invalidChannelFound(2, channelID, event.guild)
                        null
                    }
                } ?: return

                val channel =
                    event.guild.getGuildChannelById(event.entry.options["channel_id"] as String) as MessageChannel
                val message =
                    channel.retrieveMessageById(event.entry.options["message_id"] as String).await()
                val mod = event.jda.getUserById(event.entry.userIdLong)
                val messageAuthor = message.author

                val emb = embedBuilder(message) {
                    title = "Message Pinned"
                    description =
                        "**${messageAuthor.asMention} - ${messageAuthor.name}** had its [message](${message.jumpUrl}) pinned in ${channel.asMention} by ${mod?.asMention} - ${mod?.name}"
                    color = 0x417505
                    field {
                        name = "Message"
                        value = checkAndReduce(message.contentDisplay)
                        inline = false
                    }
                    if (message.attachments.isNotEmpty()) {
                        val attachmentCheck = message.attachments.reduceAndNewlineUrls()
                        field {
                            name = "Attachments"
                            value = attachmentCheck.first
                            inline = false
                        }
                        if (attachmentCheck.second) {
                            field {
                                name = "Note"
                                value =
                                    "One or more attachments couldn't be included in the embed due to Discord's character limit."
                                inline = false
                            }
                        }
                    }
                }
                modlogChannel.sendMessageEmbeds(emb).queue()
            }

            ActionType.MESSAGE_UNPIN -> {
                val modlogChannel: TextChannel = getChannel(2, guildId)?.let { channelID ->
                    event.guild.getTextChannelById(channelID) ?: run {
                        invalidChannelFound(2, channelID, event.guild)
                        null
                    }
                } ?: return

                val channel =
                    event.guild.getGuildChannelById(event.entry.options["channel_id"] as String) as MessageChannel
                val message =
                    channel.retrieveMessageById(event.entry.options["message_id"] as String).await()
                val mod = event.jda.getUserById(event.entry.userIdLong)
                val messageAuthor = message.author

                val emb = embedBuilder(message) {
                    title = "Message Unpinned"
                    description =
                        "**${messageAuthor.asMention} - ${messageAuthor.name}** had its [message](${message.jumpUrl}) unpinned in ${channel.asMention} by ${mod?.asMention} - ${mod?.name}"
                    color = 0x417505
                    field {
                        name = "Message"
                        value = checkAndReduce(message.contentDisplay)
                        inline = false
                    }
                    if (message.attachments.isNotEmpty()) {
                        val attachmentCheck = message.attachments.reduceAndNewlineUrls()
                        field {
                            name = "Attachments"
                            value = attachmentCheck.first
                            inline = false
                        }
                        if (attachmentCheck.second) {
                            field {
                                name = "Note"
                                value =
                                    "One or more attachments couldn't be included in the embed due to Discord's character limit."
                                inline = false
                            }
                        }
                    }
                }
                modlogChannel.sendMessageEmbeds(emb).queue()
            }

            ActionType.THREAD_UPDATE -> return
            ActionType.THREAD_DELETE -> return
            ActionType.AUTO_MODERATION_MEMBER_TIMEOUT -> {
                val modlog: TextChannel = getChannel(2, guildId)?.let { channelID ->
                    event.guild.getTextChannelById(channelID) ?: run {
                        invalidChannelFound(2, channelID, event.guild)
                        null
                    }
                } ?: return

                val rulename = event.entry.options["auto_moderation_rule_name"]?.toString() ?: "unknown"

                val member = event.guild.getMemberById(event.entry.targetIdLong)!!

                val emb = embedBuilder(member) {
                    title = "Automod Timeout"
                    description =
                        "**${member.asMention} - ${member.user.name}** was timed out for triggering the rule **$rulename**"
                    color = 0xE0363F
                }
                modlog.sendMessageEmbeds(emb).queue()
            }

            else -> return
        }
    }


    suspend fun onGuildMemberRemove(event: GuildMemberRemoveEvent) {
        // Only appliable to kicks and leaves. Bans have their own event.
        val guildId = event.guild.idLong
        if (!getModlogStatus(guildId)) return
        storeMemberInCache(event)

        // Create a small delay to make sure the audit log entry is created. We will need to make sure they weren't
        // banned or kicked.
        val member = event.member ?: return

        val eventIsNotLeave = withTimeoutOrNull(10.seconds) {
            val auditLogEvent = event.jda.await<GuildAuditLogEntryCreateEvent> { auditLogEvent ->
                // check for correct audit log event
                auditLogEvent.guild.idLong == guildId &&
                        auditLogEvent.entry.targetIdLong == event.user.idLong &&
                        (auditLogEvent.entry.type == ActionType.KICK ||
                                auditLogEvent.entry.type == ActionType.BAN)
            } //This will get the next event that matches this predicate
            auditLogEvent.entry
        }
        if (eventIsNotLeave != null) return
        // if the entry is null, then the user was not kicked or banned. but need further checks.
        recentlyLeftMembersCache.deque.find { it.id == member.id } ?: return


        // Only do the following if it Member Last Time Storage is enabled. We don't want to store information for
        // servers that don't have it enabled. This should become a premium feature in DigitalSparks.
        // This will only be triggered and work if the member is cached.
        if (checkLTStatus(guildId)) {
            event.member?.let { storeMemberLT(it) }
        }
        val channel: TextChannel = getChannel(1, event.guild.idLong)?.let { channelID ->
            event.guild.getTextChannelById(channelID) ?: run {
                invalidChannelFound(1, channelID, event.guild)
                null
            }
        } ?: return

        val emb = embedBuilder(member) {
            title = "Member Left"
            description =
                "**${member.asMention} - ${member.user.name} ${member.nicknameValidation()}** left the server ${
                    roles(member)
                }"
            color = 0xF8E71C
        }
        channel.sendMessageEmbeds(emb)
            .setActionRow(Button.primary("ping-on-return-${member.id}", "Ping on return")).queue()
    }

    private suspend fun pingUsersOnReturn(userReturnedId: Long, guildId: Long, channel: TextChannel) {
        // Query for all pings where the userToReturn matches and guildId matches
        val pingsList = pingCollection.find(
            and(
                eq(Ping::userToReturn.name, userReturnedId),
                eq(Ping::guildId.name, guildId)
            )
        ).toList()

        // Construct the mention string
        val mentionsList = pingsList.joinToString("") {
            channel.jda.getUserById(it.userToPing)?.asMention ?: ""
        }

        // Delete all matched pings
        pingCollection.deleteMany(
            and(
                eq(Ping::userToReturn.name, userReturnedId),
                eq(Ping::guildId.name, guildId)
            )
        )

        // Send a message if there were any mentions
        if (mentionsList.isNotBlank()) {
            channel.sendMessage("${mentionsList}\nA user you wanted to be notified about has returned").queue()
        }
    }


    suspend fun onGuildMemberJoin(event: GuildMemberJoinEvent) {
        if (!getModlogStatus(event.guild.idLong)) return
        val guildId = event.guild.idLong

        val channel: TextChannel = getChannel(1, event.guild.idLong)?.let { channelID ->
            event.guild.getTextChannelById(channelID) ?: run {
                invalidChannelFound(1, channelID, event.guild)
                null
            }
        } ?: return

        val member = event.member

        // This is for EntryController. It will check if the member is an exception to the 5-minute rule.
        if (guildId != 81384788765712384) {
            if (EntryController.readFromDatabase(member.idLong, guildId)) {
                //apply an exception role
                member.guild.addRoleToMember(member, event.guild.getRoleById("1040306896340537374")!!).queue()
            }
        }

        pingUsersOnReturn(userReturnedId = member.idLong, guildId = guildId, channel = channel)

        val timeCreatedDTS = TimeFormat.DATE_TIME_SHORT.format(member.timeCreated)
        val timeCreatedR = TimeFormat.RELATIVE.format(member.timeCreated)
        // Getting from LastTimeStorage. It will say "New member" if it is the first time the member joined.
        val lastTimeInstant = getMemberLT(member)?.toInstant()
        var lastTimeDTS = "New member"
        var lastTimeR = "Never"
        if (lastTimeInstant != null) {
            lastTimeDTS = TimeFormat.DATE_TIME_SHORT.format(lastTimeInstant)
            lastTimeR = TimeFormat.RELATIVE.format(lastTimeInstant)
        } else {
            // We should check if discord kept track with the flags
            logger.trace {
                "getMemberLT() returned null. Checking flags for ${member.user.name} (ID: \"${member.id}\")"
                "Current flags: ${member.flags.joinToString(", ")}"
            }
            if (member.flags.contains(Member.MemberFlag.DID_REJOIN)) {
                lastTimeDTS = "Rejoining member"
                lastTimeR = "Unknown"
            }
        }

        val emb = embedBuilder(member) {
            title = "Member Joined"
            description =
                "**${member.asMention} - ${member.user.name}** joined the server" + "\n**Creation Date:**\n${timeCreatedDTS} (${timeCreatedR})" + "\n**Last Time Here:**\n${lastTimeDTS} (${lastTimeR})" + getStoredLTRoles(
                    member
                )
            color = 0x7ED321
        }
        channel.sendMessageEmbeds(emb).queue()
    }

    suspend fun onGuildMemberUpdateNickname(event: GuildMemberUpdateNicknameEvent) {
        if (!getModlogStatus(event.guild.idLong)) return
        val guildId = event.guild.idLong
        val channel: TextChannel = getChannel(2, guildId)?.let { channelID ->
            event.guild.getTextChannelById(channelID) ?: run {
                invalidChannelFound(2, channelID, event.guild)
                null
            }
        } ?: return

        val cenTag = censor(event.user.name)
        val cenOldNick = event.oldNickname?.let { censor(it) }
        val cenNewNick = event.newNickname?.let { censor(it) }

        val member = event.member
        if (event.oldNickname != event.newNickname) {
            val emb = embedBuilder(member) {
                title = "Nickname Changed"
                description =
                    "**${member.asMention} - ${cenTag}** nickname has been changed\n**Nickname Before:**\n${cenOldNick}\n**Nickname After:**\n${cenNewNick}"
                color = 0xF5A623
            }
            channel.sendMessageEmbeds(emb).queue()
        }
    }

    suspend fun onMessageUpdate(event: MessageUpdateEvent) {
        if (!event.isFromGuild) return // ignore events that are not from guild
        if (event.author.isBot || !getModlogStatus(event.guild.idLong)) return
        val guildId = event.guild.idLong

        val channel: TextChannel = getChannel(2, guildId)?.let { channelID ->
            event.guild.getTextChannelById(channelID) ?: run {
                invalidChannelFound(2, channelID, event.guild)
                null
            }
        } ?: return

        val messageBefore = messageCacheCaffeine.getIfPresent(event.messageId) // this is nullable
        MessageCache.onMessageUpdateEvent(event) // update the cache with the new message
        val messageAfter = event.message
        val member = event.member

        //todo: Do the events for piined and upin always after afterwards? if so, we can wait for the event to be fired.
        // if it fires, dont send the "Message Edited" embed. if it doesnt fire, send the embed.

        val emb = embedBuilder(messageAfter) {
            title = "Message Edited"
            description =
                "**${member?.asMention} - ${member?.user?.name}** edited a [message](${messageAfter.jumpUrl}) in ${event.channel.asMention}."
            color = 0x417505
            field {
                name = "Before message"
                value = messageBefore?.contentDisplay?.let { checkAndReduce(censor(it)) } ?: "`Message not cached.`"
                inline = false
            }
            if (messageBefore != null) {
                if (messageBefore.attachmentsURLs.isNotEmpty()) {
                    val attachmentCheck = messageBefore.attachmentsURLs.reduceAndNewlineUrls()
                    field {
                        name = "Before Attachments"
                        value = attachmentCheck.first
                        inline = false
                    }
                    if (attachmentCheck.second) {
                        field {
                            name = "Before Note"
                            value =
                                "One or more attachments couldn't be included in the embed due to Discord's character limit."
                            inline = false
                        }
                    }
                }
            }
            field {
                name = "After message"
                value = checkAndReduce(censor(messageAfter.contentDisplay))
                inline = false
            }
            if (messageAfter.attachments.isNotEmpty()) {
                val attachmentCheck = messageAfter.attachments.reduceAndNewlineUrls()
                field {
                    name = "After Attachments"
                    value = attachmentCheck.first
                    inline = false
                }
                if (attachmentCheck.second) {
                    field {
                        name = "After Note"
                        value =
                            "One or more attachments couldn't be included in the embed due to Discord's character limit."
                        inline = false
                    }
                }
            }
        }
        channel.sendMessageEmbeds(emb).queue()
    }

    suspend fun onMessageDelete(event: MessageDeleteEvent) {
        if (!event.isFromGuild) return // ignore events that are not from guild
        if (!getModlogStatus(event.guild.idLong)) return
        val guildId = event.guild.idLong
        // We can not check yet if the message was from a bot or not. We need to check cache first.
        val channel: TextChannel = getChannel(2, guildId)?.let { channelID ->
            event.guild.getTextChannelById(channelID) ?: run {
                invalidChannelFound(2, channelID, event.guild)
                null
            }
        } ?: return

        val messageCached = messageCacheCaffeine.getIfPresent(event.messageId) // this is nullable
        MessageCache.onMessageDeleteEvent(event) // delete the message from the cache
        val user = messageCached?.authorID?.let { event.jda.retrieveUserById(it).await() }

        val emb = embedBuilder(messageCached) {
            title = "Message Deleted"
            color = 0x9013FE
            if (messageCached != null) {
                description =
                    "**${user?.asMention} - ${user?.name}** had its message deleted in ${event.channel.asMention}."
                field {
                    name = "Message"
                    value =
                            //todo: if its a sticker, it will be empty. we don't want to send an empty message. we want to send the sticker
                        checkAndReduce(censor(messageCached.contentDisplay))
                }
                if (messageCached.attachmentsURLs.isNotEmpty()) {
                    val attachmentCheck = messageCached.attachmentsURLs.reduceAndNewlineUrls()
                    field {
                        name = "Attachments"
                        value = attachmentCheck.first
                        inline = false
                    }
                    if (attachmentCheck.second) {
                        field {
                            name = "Note"
                            value =
                                "One or more attachments couldn't be included in the embed due to Discord's character limit."
                            inline = false
                        }
                    }
                }
            } else
                return // The message is not cached. No log should be sent to the channel.
        }
        channel.sendMessageEmbeds(emb).queue()
    }


    suspend fun onMessageBulkDelete(event: MessageBulkDeleteEvent) {
        if (!getModlogStatus(event.guild.idLong)) return
        // We can not check yet if the message was from a bot or not. We need to check cache first.
        val channel: TextChannel = getChannel(2, event.guild.idLong)?.let { channelID ->
            event.guild.getTextChannelById(channelID) ?: run {
                invalidChannelFound(2, channelID, event.guild)
                null
            }
        } ?: return

        val deletedQuantity = event.messageIds.size // this is the amount of messages deleted
        var messageNumber = 1
        var uncachedMessagesQuantity = 0

        event.messageIds.forEach { messageID ->
            val messageCached = messageCacheCaffeine.getIfPresent(messageID) // this is nullable
            // we will only delete from the cache afterward to avoid doing 2 tasks at the same time
            val user = messageCached?.authorID?.let { event.jda.retrieveUserById(it).await() }

            val emb = embedBuilder(messageCached) {
                title = "Message Deleted in Bulk (${messageNumber++}/$deletedQuantity)"
                color = 0x9013FE
                if (messageCached != null) {
                    description =
                        "**${user?.asMention} - ${user?.name}** had its message deleted in ${event.channel.asMention}."
                    field {
                        name = "Message"
                        value = checkAndReduce(censor(messageCached.contentDisplay))
                    }
                    if (messageCached.attachmentsURLs.isNotEmpty()) {
                        val attachmentCheck = messageCached.attachmentsURLs.reduceAndNewlineUrls()
                        field {
                            name = "Attachments"
                            value = attachmentCheck.first
                            inline = false
                        }
                        if (attachmentCheck.second) {
                            field {
                                name = "Note"
                                value =
                                    "One or more attachments couldn't be included in the embed due to Discord's character limit."
                                inline = false
                            }
                        }
                    }

                } else {
                    uncachedMessagesQuantity++
                }
            }
            channel.sendMessageEmbeds(emb).queue()
        }
        if (uncachedMessagesQuantity > 0) {
            val newEmb = embedBuilder(message = null) {
                title = "Message Deleted in Bulk ($deletedQuantity/$deletedQuantity)"
                color = 0x9013FE
                description =
                    "$uncachedMessagesQuantity out of $deletedQuantity messages were deleted in ${event.channel.asMention} but were not in the cache."
            }
            channel.sendMessageEmbeds(newEmb).queue()
        }
        MessageCache.onMessageBulkDeleteEvent(event)
    }

    //TODO: i was here
    suspend fun onMessageReactionAdd(event: MessageReactionAddEvent) {
        if (!event.isFromGuild) return // ignore events that are not from guild
        if (!getModlogStatus(event.guild.idLong)) return
        val channel = getChannel(2, event.guild.idLong).let {
            if (it != null)
                event.guild.getTextChannelById(it)
            else
                null
        } ?: return //If the actual channel with that ID doesn't
        val message = event.retrieveMessage().await()
        val member = event.member
        val reaction = event.reaction
        val emote = reaction.emoji
        val emb = embedBuilder(message) {
            title = "Reaction Added"
            description =
                "**${member?.asMention} - ${member?.user?.name}** added a reaction to a [message](${message?.jumpUrl}) in ${event.channel.asMention}"
            color = 0x417505
            field {
                name = "Reaction"
                value = emote.formatted
            }
        }
        channel.sendMessageEmbeds(emb).queue()
    }


    suspend fun setChannel(
        channelNumber: Int,
        event: SlashCommandInteractionEvent,
        channel: GuildChannelUnion
    ) {
        // channelNumber must be 1 or 2
        if (channelNumber !in 1..2)
            throw IllegalArgumentException("Channel number must be 1 or 2")

        // Update the existing Modlog
        when (channelNumber) {
            1 -> modlogCollection.updateOne(
                filter = eq(Modlog::guildId.name, event.guild!!.idLong),
                update = Updates.combine(
                    setOnInsert(Modlog::toggle.name, false),
                    setOnInsert(Modlog::ltsToggle.name, false),
                    Updates.set(Modlog::one.name, channel.idLong),
                    setOnInsert(Modlog::two.name, null),
                    setOnInsert(Modlog::guildId.name, event.guild!!.idLong),
                ), UpdateOptions().upsert(true)
            )

            2 -> modlogCollection.updateOne(
                filter = eq(Modlog::guildId.name, event.guild!!.idLong),
                update = Updates.combine(
                    setOnInsert(Modlog::toggle.name, false),
                    setOnInsert(Modlog::ltsToggle.name, false),
                    setOnInsert(Modlog::one.name, null),
                    Updates.set(Modlog::two.name, channel.idLong),
                    setOnInsert(Modlog::guildId.name, event.guild!!.idLong),
                ), UpdateOptions().upsert(true)
            )
        }

        val emb = NeoSuperEmbed {
            type = SuperEmbed.ResultType.SUCCESS
            text = "**Modlogs** ${if (channelNumber == 1) "primary" else "secondary"} channel set!"
        }
        event.hook.editOriginal(MessageEditData.fromEmbeds(emb)).queue()
    }
}
