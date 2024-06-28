package commands

import CommandsGeneral.getMemberNumberInGuild
import ModLog
import com.mongodb.client.model.Filters
import com.mongodb.client.model.Filters.and
import dev.minn.jda.ktx.coroutines.await
import dev.minn.jda.ktx.messages.EmbedBuilder
import dev.minn.jda.ktx.messages.send
import helpers.FEATURE_NOT_IMPLEMENTED
import helpers.permissionCheckMessageless
import kotlinx.coroutines.flow.firstOrNull
import net.dv8tion.jda.api.Permission
import net.dv8tion.jda.api.entities.Activity
import net.dv8tion.jda.api.entities.Member
import net.dv8tion.jda.api.entities.User
import net.dv8tion.jda.api.events.interaction.command.GenericCommandInteractionEvent
import java.time.Instant
import java.util.*
import net.dv8tion.jda.api.utils.*
import net.dv8tion.jda.api.EmbedBuilder
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent
import  net.dv8tion.jda.api.interactions.components.buttons.*

object UserInfo {

    data class MemberPunishmentHistory(
        val id: Long,
        val timeoutsHistory: List<PastTimeouts>,
        val bansHistory: List<PastBans>,
        val kicksHistory: List<PastKicks>,
        val leavesHistory: List<PastLeaves>,
        val guildId: Long
    )

    data class PastTimeouts(
        val dateOfOcurrance: Instant,
        val duration: Int, //In seconds. This can then be converted to a human-readable format
        val reason: String, // This needs to be a max of 1000 characters
        val moderatorId: Long
    )

    data class PastBans(
        val dateOfOcurrance: Instant,
        val reason: String, // This needs to be a max of 1000 characters
        val duration: Int, //In seconds. This can then be converted to a human-readable format. If 0, then It's still banned.
        val moderatorId: Long
    )

    data class PastKicks(
        val dateOfOcurrance: Instant,
        val reason: String, // This needs to be a max of 1000 characters
        val moderatorId: Long
    )

    data class PastLeaves(
        val dateOfOcurrance: Instant,
        val duration: Int, //In seconds. This can then be converted to a human-readable format. If 0, then it hasn't rejoined.
    )


    suspend fun userinfoUser(event: GenericCommandInteractionEvent, theUser: User) {
        var theUserAsMember: Member? = null
        var isAdmin = false
        var lastTimeHere: Date? = null
        var footerAddOn = ""
        // Are we in a guild?
        if (event.isFromGuild) {
            try {
                theUserAsMember = event.guild?.retrieveMember(theUser)?.await()
            } // If it throws, we can continue without it
            catch (_: Throwable) {
            }
            try {
                if (ModLog.checkLTStatus(event.guild!!.idLong)) {
                    ModLog.membersLTSCollection.find(
                        and(
                            Filters.eq("guildId", event.guild!!.idLong),
                            Filters.eq("memberID", theUser.idLong)
                        )
                    ).firstOrNull()?.let { memberLTS ->
                        lastTimeHere = memberLTS.lastTime
                    }
                }
            } catch (_: Throwable) {
            }
            try {
                event.guild?.getMember(theUser)?.let { member ->
                    isAdmin = member.hasPermission(Permission.ADMINISTRATOR) || member.idLong in owners
                }
            } catch (_: Throwable) {
            }
        }

        //check if LTS is enabled

        val theMemberTimeBoosted = theUserAsMember?.timeBoosted
        val userBotInfo = if (theUser.isBot) {
            "Yes"
        } else {
            "No"
        }

        val timeCreatedDTS = TimeFormat.DATE_TIME_SHORT.format(theUser.timeCreated)
        val timeCreatedR = TimeFormat.RELATIVE.format(theUser.timeCreated)

        val userProfile = theUser.retrieveProfile().await()

        val userinfoMessage = EmbedBuilder {
            title = theUser.effectiveName
            color = userProfile.accentColorRaw
            thumbnail = theUser.effectiveAvatarUrl
            description = "${theUser.asMention}\n${theUser.name}"
            field {
                name = "Creation Date"
                value = "${timeCreatedDTS}\n(${timeCreatedR})"
                inline = true
            }
        }

        if (theUserAsMember != null) {

            val timeJoinedDTS = TimeFormat.DATE_TIME_SHORT.format(theUserAsMember.timeJoined)
            val timeJoinedR = TimeFormat.RELATIVE.format(theUserAsMember.timeJoined)

            //todo: i need to get the custom status. need to check how its done.

            theUserAsMember.activities.forEach { activity ->
                if (activity.type != Activity.ActivityType.CUSTOM_STATUS)
                    userinfoMessage.description += ("\n• • •\n" + activity.type + " " + activity.name)
                else
                    userinfoMessage.description += ("\n• • •\n" + activity.name)
            }

            userinfoMessage.title = theUserAsMember.effectiveName
            userinfoMessage.thumbnail = theUserAsMember.effectiveAvatarUrl
            userinfoMessage.field {
                name = "Join Date"
                value = "${timeJoinedDTS}\n(${timeJoinedR})"
                inline = true
            }

            if (theMemberTimeBoosted != null) {
                val boostedTimeDST = TimeFormat.DATE_TIME_SHORT.format(theMemberTimeBoosted)
                val boostedTimeR = TimeFormat.RELATIVE.format(theMemberTimeBoosted)

                userinfoMessage.field {
                    name = "Boost Date"
                    value = "${boostedTimeDST}\n(${boostedTimeR})"
                    inline = true
                }

            }
            footerAddOn = " • Member #${getMemberNumberInGuild(event, theUser)}"
        }
        if (lastTimeHere != null) {
            val lastTimeDST = TimeFormat.DATE_TIME_SHORT.format(lastTimeHere!!.toInstant())
            val lastTimeR = TimeFormat.RELATIVE.format(lastTimeHere!!.toInstant())

            userinfoMessage.field {
                name = "Last Time Here"
                value = "${lastTimeDST}\n(${lastTimeR})"
                inline = true
            }
        }
        if (NameTracker.getNameHistoryStatus(event.guild!!.idLong)) {
            val userHistory = NameTracker.nameHistoryNamesCollection.find(
                Filters.eq(NameTracker.NameHistoryNames::id.name, theUser.idLong)
            ).firstOrNull()
            if (userHistory?.pastUsername?.isNotEmpty() == true) {
                userinfoMessage.field {
                    name = "Previous names"
                    value = userHistory.pastUsername.joinToString(", ")
                    inline = false
                }
            }
            if (userHistory?.pastGlobalName?.isNotEmpty() == true) {
                userinfoMessage.field {
                    name = "Previous global display names"
                    value = userHistory.pastGlobalName.joinToString(", ")
                    inline = false
                }
            }
            val nicknameHistory = NameTracker.nameHistoryNicknamesCollection.find(
                and(
                    Filters.eq(NameTracker.NameHistoryNicknames::id.name, theUser.idLong),
                    Filters.eq(NameTracker.NameHistoryNicknames::guildId.name, event.guild!!.idLong)
                )
            ).firstOrNull()
            if (nicknameHistory?.pastNickname?.isNotEmpty() == true) {
                userinfoMessage.field {
                    name = "Previous nicknames"
                    value = nicknameHistory.pastNickname.joinToString(", ")
                    inline = false
                }
            }
        }

        userinfoMessage.footer {
            name = "User ID: ${theUser.id}$footerAddOn"
        }

        event.hook.editOriginalEmbeds(userinfoMessage.build()).also {
            if (isAdmin) {
                it.setActionRow(
                    Button.danger("super-userinfo-${theUser.idLong}", "Super Userinfo")
                )
            }
        }.mentionRepliedUser(false).queue()
    }

    suspend fun superUserInfo(event: ButtonInteractionEvent) {
        //todo: i was doing this
        val user = event.user
        val guild = event.guild ?: return
        val member = guild.retrieveMember(user).await()
        if (!permissionCheckMessageless(member, Permission.ADMINISTRATOR)) {
            event.reply("You don't have permission to use this button").setEphemeral(true).queue()
            return
        }
        val userId = event.button.id?.replace("super-userinfo-", "")?.toLong()
        if (userId == null) {
            event.hook.send("Something went wrong").setEphemeral(true).queue()
            return
        } else {
            event.hook.send(FEATURE_NOT_IMPLEMENTED).setEphemeral(true).queue()
            return
        }
        //stopper
        // val theUser = event.jda.retrieveUserById(userId).await()
        // userinfoUser(event, theUser)
    }
}
