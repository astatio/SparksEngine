package commands

import com.mongodb.client.model.Filters.eq
import com.mongodb.client.model.FindOneAndUpdateOptions
import com.mongodb.client.model.UpdateOptions
import com.mongodb.client.model.Updates
import dev.minn.jda.ktx.interactions.components.button
import helpers.NeoSuperEmbed
import helpers.SuperEmbed
import helpers.database
import kotlinx.coroutines.flow.firstOrNull
import net.dv8tion.jda.api.entities.User
import net.dv8tion.jda.api.events.guild.member.update.GuildMemberUpdateNicknameEvent
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent
import net.dv8tion.jda.api.events.user.update.UserUpdateGlobalNameEvent
import net.dv8tion.jda.api.events.user.update.UserUpdateNameEvent
import net.dv8tion.jda.api.interactions.components.buttons.ButtonStyle
import kotlin.time.Duration.Companion.seconds

object NameTracker {

    data class NameHistoryNames(
        val id: Long,
        val pastUsername: List<String>, // limited to 15 entries
        val pastGlobalName: List<String>, // limited to 15 entries
    )

    data class NameHistoryNicknames(
        val id: Long,
        val pastNickname: List<String>, // limited to 25 entries
        val guildId: Long
    )

    data class NameHistorySettings(
        val toggle: Boolean, // default: false
        val guildId: Long
    )

    val nameHistoryNamesCollection = database!!.getCollection<NameHistoryNames>("nameHistoryNames")
    val nameHistoryNicknamesCollection = database!!.getCollection<NameHistoryNicknames>("nameHistoryNicknames")
    val nameHistorySettingsCollection = database!!.getCollection<NameHistorySettings>("nameHistorySettings")


    suspend fun getNameHistoryStatus(guildId: Long) =
        nameHistorySettingsCollection.find(eq(NameHistorySettings::guildId.name, guildId)).firstOrNull()?.toggle ?: false

    suspend fun switch(event: SlashCommandInteractionEvent, mode: Boolean) {
        val guildId = event.guild!!.idLong

        nameHistorySettingsCollection.updateOne(
            filter = eq(NameHistorySettings::guildId.name, guildId),
            update = Updates.combine(
                Updates.setOnInsert(NameHistorySettings::guildId.name, guildId),
                Updates.set(NameHistorySettings::toggle.name, mode)
            ),
            UpdateOptions().upsert(true)
        )

        event.hook.sendMessageEmbeds(NeoSuperEmbed {
            type = SuperEmbed.ResultType.SUCCESS
            text = "**Name Tracker** is now ${if (mode) "ON" else "OFF"}"
        }).queue()
    }

    suspend fun toggle(event: SlashCommandInteractionEvent) {
        val newToggleStatus = !(getNameHistoryStatus(event.guild!!.idLong))
        nameHistorySettingsCollection.updateOne(
            filter = eq(NameHistorySettings::guildId.name, event.guild!!.idLong),
            update = Updates.combine(
                Updates.setOnInsert(NameHistorySettings::guildId.name, event.guild!!.idLong),
                Updates.set(NameHistorySettings::toggle.name, newToggleStatus)
            ),
            UpdateOptions().upsert(true)
        )

        val toggleAsString = if (newToggleStatus) {
            "ON"
        } else {
            "OFF"
        }

        event.hook.sendMessageEmbeds(NeoSuperEmbed {
            type = SuperEmbed.ResultType.SUCCESS
            text = "**Name Tracker** is now $toggleAsString"
        }).queue()
    }

    suspend fun clear(event: SlashCommandInteractionEvent, user: User) {
        val userNicknames =
            nameHistoryNicknamesCollection.findOneAndDelete(eq(NameHistoryNicknames::id.name, user.idLong))
        if (userNicknames == null) {
            event.hook.sendMessageEmbeds(NeoSuperEmbed {
                type = SuperEmbed.ResultType.SIMPLE_ERROR
                text = "No nickname history found for ${user.asMention}"
            }).queue()
            return
        }

        event.hook.sendMessageEmbeds(NeoSuperEmbed {
            type = SuperEmbed.ResultType.SUCCESS
            text = "Cleared nickname history for ${user.asMention}\n"
        }).queue()
    }

    suspend fun clearSelf(event: SlashCommandInteractionEvent) {
        //instead of deleting nicknames, delete from the names collection
        val userNames =
            nameHistoryNamesCollection.find(eq(NameHistoryNames::id.name, event.user.idLong)).firstOrNull()
        if (userNames == null) {
            event.hook.sendMessageEmbeds(NeoSuperEmbed {
                type = SuperEmbed.ResultType.SIMPLE_ERROR
                text = "No name nor global display name history found for ${event.user.asMention}"
            }).queue()
            return
        }

        val `continue` = event.jda.button(
            label = "Continue", style = ButtonStyle.DANGER, user = event.user, expiration = 30.seconds
        ) {
            nameHistoryNamesCollection.deleteOne(
                eq(NameHistoryNames::id.name, event.user.idLong)
            )
            it.hook.editOriginalEmbeds(NeoSuperEmbed {
                type = SuperEmbed.ResultType.SUCCESS
                text = "Cleared name and global display name history for ${event.user.asMention}"
            }).queue()
        }
        val cancel = event.jda.button(
            label = "Cancel", style = ButtonStyle.SECONDARY, user = event.user, expiration = 30.seconds
        ) {
            it.hook.editOriginalEmbeds(NeoSuperEmbed {
                type = SuperEmbed.ResultType.SIMPLE
                text = "Cancelled"
            }).queue()
        }


        event.hook.sendMessageEmbeds(NeoSuperEmbed {
            type = SuperEmbed.ResultType.SUCCESS
            text =
                "Are you sure you want to clear your name and global display name history? This data can help others identify you and is useful for moderation purposes."
        }).setActionRow(`continue`, cancel).queue()

        event.hook.sendMessageEmbeds(NeoSuperEmbed {
            type = SuperEmbed.ResultType.SUCCESS
            text = "Cleared name and global display name history for ${event.user.asMention}"
        }).queue()
    }

    suspend fun flush(event: SlashCommandInteractionEvent) {
        val `continue` = event.jda.button(
            label = "Continue", style = ButtonStyle.DANGER, user = event.user, expiration = 30.seconds
        ) {
            nameHistoryNicknamesCollection.deleteMany(
                eq(NameHistoryNicknames::guildId.name, event.guild!!.idLong)
            )
            it.hook.editOriginalEmbeds(NeoSuperEmbed {
                type = SuperEmbed.ResultType.SUCCESS
                text = "Flushed nickname history for all members that ever joined the server"
            }).queue()
        }
        val cancel = event.jda.button(
            label = "Cancel", style = ButtonStyle.SECONDARY, user = event.user, expiration = 30.seconds
        ) {
            it.hook.editOriginalEmbeds(NeoSuperEmbed {
                type = SuperEmbed.ResultType.SIMPLE
                text = "Cancelled"
            }).queue()
        }

        event.hook.sendMessageEmbeds(NeoSuperEmbed {
            type = SuperEmbed.ResultType.SUCCESS
            text =
                "Are you sure you want to flush the nickname history for all members that ever joined the server? This data can help identify members."
        }).setActionRow(`continue`, cancel).queue()
    }


    suspend fun onGuildMemberUpdateNickname(event: GuildMemberUpdateNicknameEvent) {
        // if the user is a bot, ignore
        // if MemberNameTracker is disabled, ignore
        if (event.member.user.isBot || !getNameHistoryStatus(event.guild.idLong) || (event.oldNickname == null)) return
       val nicknameHistory = nameHistoryNicknamesCollection.findOneAndUpdate(
            filter = eq(NameHistoryNicknames::id.name, event.member.idLong),
            update = Updates.combine(
                Updates.setOnInsert(NameHistoryNicknames::id.name, event.member.idLong),
                Updates.setOnInsert(NameHistoryNicknames::guildId.name, event.guild.idLong),
                Updates.push(NameHistoryNicknames::pastNickname.name, event.oldNickname)
            ),
            options = FindOneAndUpdateOptions().upsert(true)
        ) ?: return
        if(nicknameHistory.pastNickname.count() > 25) {
            nameHistoryNicknamesCollection.updateOne(
                filter = eq(NameHistoryNicknames::id.name, event.member.idLong),
                update = Updates.combine(
                    Updates.setOnInsert(NameHistoryNicknames::id.name, event.member.idLong),
                    Updates.setOnInsert(NameHistoryNicknames::guildId.name, event.guild.idLong),
                    Updates.popFirst(NameHistoryNicknames::pastNickname.name)
                ),
                options = UpdateOptions().upsert(true)
            )
        }

    }

    suspend fun onUserUpdateGlobalName(event: UserUpdateGlobalNameEvent) {
        if (event.oldGlobalName == null || event.user.mutualGuilds.firstOrNull { guild -> getNameHistoryStatus(guild.idLong) } != null) return
        val nameHistory = nameHistoryNamesCollection.findOneAndUpdate(
            filter = eq(NameHistoryNames::id.name, event.user.idLong),
            update = Updates.combine(
                Updates.setOnInsert(NameHistoryNames::id.name, event.user.idLong),
                Updates.setOnInsert(NameHistoryNames::pastUsername.name, emptyList<String>()),
                Updates.push(NameHistoryNames::pastGlobalName.name, event.oldGlobalName)
            ),
            options = FindOneAndUpdateOptions().upsert(true)
        ) ?: return
        if(nameHistory.pastGlobalName.count() > 15) {
            nameHistoryNamesCollection.updateOne(
                filter = eq(NameHistoryNames::id.name, event.user.idLong),
                update = Updates.combine(
                    Updates.setOnInsert(NameHistoryNames::id.name, event.user.idLong),
                    Updates.setOnInsert(NameHistoryNames::pastGlobalName.name, event.oldGlobalName),
                    Updates.popFirst(NameHistoryNames::pastGlobalName.name)
                ),
                options = UpdateOptions().upsert(true)
            )
        }
    }

    suspend fun onUserUpdateName(event: UserUpdateNameEvent) {
        if (event.user.mutualGuilds.firstOrNull { guild -> getNameHistoryStatus(guild.idLong) } != null) return
        val nameHistory = nameHistoryNamesCollection.findOneAndUpdate(
            filter = eq(NameHistoryNames::id.name, event.user.idLong),
            update = Updates.combine(
                Updates.setOnInsert(NameHistoryNames::id.name, event.user.idLong),
                Updates.setOnInsert(NameHistoryNames::pastGlobalName.name, emptyList<String>()),
                Updates.push(NameHistoryNames::pastUsername.name, event.oldName)
            ),
            options = FindOneAndUpdateOptions().upsert(true)
        ) ?: return
        if(nameHistory.pastUsername.count() > 15) {
            nameHistoryNamesCollection.updateOne(
                filter = eq(NameHistoryNames::id.name, event.user.idLong),
                update = Updates.combine(
                    Updates.setOnInsert(NameHistoryNames::id.name, event.user.idLong),
                    Updates.setOnInsert(NameHistoryNames::pastUsername.name, event.oldName),
                    Updates.popFirst(NameHistoryNames::pastUsername.name)
                ),
                options = UpdateOptions().upsert(true)
            )
        }
    }
}