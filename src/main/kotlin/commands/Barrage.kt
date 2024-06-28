package commands

import com.mongodb.client.model.Filters.eq
import com.mongodb.client.model.UpdateOptions
import com.mongodb.client.model.Updates
import com.mongodb.client.model.Updates.combine
import com.mongodb.client.model.Updates.setOnInsert
import helpers.*
import kotlinx.coroutines.flow.firstOrNull
import net.dv8tion.jda.api.Permission
import net.dv8tion.jda.api.entities.channel.middleman.MessageChannel
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent
import net.dv8tion.jda.api.events.message.MessageReceivedEvent

object Barrage {

    //todo: this should have a cache of its own.

    /*
    * Allows or blocks commands from being executed in certain channels - unless they are administrators.
    * By default, it works on block mode and allows commands to be executed everywhere except in listed channels.
    * Block mode means that the channels listed are blocked from executing commands.
    * Allow mode means that the channels listed are the only ones allowed to execute commands.
    * It can be set on allow mode and do exactly the opposite of the aforementioned.
    * */

    data class BarrageSettings(
        val allowMode: Boolean, // true = allow, false = block. Default is false.
        val channelIDs: List<Long>, // The channel IDs of the allowed channels
        val guildId: Long
    )

    val barrageSettingsCollection = database!!.getCollection<BarrageSettings>("barrageSettings")

    suspend fun getBarrageSettings(guildId: Long) = barrageSettingsCollection.find(
        eq(BarrageSettings::guildId.name, guildId)
    ).firstOrNull()

    //- [X] /barrage add [channel]
    //- [x] /barrage remove [channel]
    //- [x] /barrage release
    //- [x] /barrage list
    //- [x] /barrage status
    //- [X] /barrage switch [allow|block] ## Switch between allow and block mode.

    suspend fun addChannel(event: SlashCommandInteractionEvent, channel: MessageChannel) {
        if (!permissionCheck(event.toWrapper(), Permission.ADMINISTRATOR)) {
            return
        }
        val guildId = event.guild!!.idLong
        val guildBarrage = getBarrageSettings(guildId)

        val status = if (guildBarrage?.allowMode == true) "allow" else "block"
        var channelIDs = (guildBarrage?.channelIDs ?: mutableListOf()) as MutableList<Long>
        channelIDs = channelIDs.filterInvalidIDsAndAnnounce(event.guild!!, event.channel.asGuildMessageChannel())
        if (channelIDs.contains(channel.idLong)) {
            event.replyEmbeds(
                NeoSuperEmbed {
                    type = SuperEmbed.ResultType.SIMPLE_ERROR
                    text =
                        "The channel is already in the list of ${status}ed channels."
                }).queue()
            return
        }
        channelIDs.add(channel.idLong)

        // create, if not exists, and add the channel to the list
        barrageSettingsCollection.updateOne(
            filter = eq(BarrageSettings::guildId.name, guildId),
            update = combine(
                setOnInsert(BarrageSettings::allowMode.name, false),
                setOnInsert(BarrageSettings::guildId.name, guildId),
                Updates.set(BarrageSettings::channelIDs.name, channelIDs)
            ),
            UpdateOptions().upsert(true)
        )

        event.hook.sendMessageEmbeds(
            NeoSuperEmbed {
                type = SuperEmbed.ResultType.SUCCESS
                text =
                    "Added the channel to the list of ${status}ed channels."
            }).queue()
    }

    suspend fun removeChannel(event: SlashCommandInteractionEvent, channel: MessageChannel) {
        if (!permissionCheck(event.toWrapper(), Permission.ADMINISTRATOR)) {
            return
        }
        val guildId = event.guild!!.idLong

        val guildBarrage = getBarrageSettings(guildId)
        val status = if (guildBarrage?.allowMode == true) "allow" else "block"
        var channelIDs = (guildBarrage?.channelIDs ?: emptyList()) as MutableList<Long>
        channelIDs = channelIDs.filterInvalidIDsAndAnnounce(event.guild!!, event.channel.asGuildMessageChannel())
        if (!channelIDs.contains(channel.idLong)) {
            event.replyEmbeds(
                NeoSuperEmbed {
                    type = SuperEmbed.ResultType.ERROR
                    text =
                        "The channel is not in the list of ${status}ed channels."
                }).queue()
            return
        }
        channelIDs.remove(channel.idLong)

        // remove the channel from the list
        barrageSettingsCollection.updateOne(
            filter = eq(BarrageSettings::guildId.name, guildId),
            update = combine(
                setOnInsert(BarrageSettings::allowMode.name, false),
                setOnInsert(BarrageSettings::guildId.name, guildId),
                Updates.set(BarrageSettings::channelIDs.name, channelIDs)
            ),
            UpdateOptions().upsert(true)
        )

        event.hook.sendMessageEmbeds(
            NeoSuperEmbed {
                type = SuperEmbed.ResultType.SUCCESS
                text = "Removed the channel from the list of ${status}ed channels."
            }).queue()
    }

    suspend fun release(event: SlashCommandInteractionEvent) {
        if (!permissionCheck(event.toWrapper(), Permission.ADMINISTRATOR)) {
            return
        }
        val guildId = event.guild!!.idLong
        val guildBarrage = getBarrageSettings(guildId)
        val status = if (guildBarrage?.allowMode == true) "allow" else "block"

        // clear the channel list
        barrageSettingsCollection.updateOne(
            filter = eq(BarrageSettings::guildId.name, guildId),
            update = combine(
                setOnInsert(BarrageSettings::allowMode.name, false),
                setOnInsert(BarrageSettings::guildId.name, guildId),
                Updates.set(BarrageSettings::channelIDs.name, emptyList<Long>())
            ),
            UpdateOptions().upsert(true)
        )

        event.hook.sendMessageEmbeds(
            NeoSuperEmbed {
                type = SuperEmbed.ResultType.SUCCESS
                text =
                    "Cleared the list of ${status}ed channels."
            }).queue()
    }

    suspend fun listChannels(event: SlashCommandInteractionEvent) {
        if (!permissionCheck(event.toWrapper(), Permission.ADMINISTRATOR)) {
            return
        }
        val guildId = event.guild!!.idLong

        val guildBarrage = getBarrageSettings(guildId)
        if (guildBarrage == null || guildBarrage.channelIDs.isEmpty()) {
            event.hook.sendMessageEmbeds(
                NeoSuperEmbed {
                    type = SuperEmbed.ResultType.ERROR
                    text = "There are no channels set."
                }).queue()
            return
        }
        val status = if (guildBarrage.allowMode) "allow" else "block"

        var channelIDs = guildBarrage.channelIDs as MutableList<Long>
        channelIDs = channelIDs.filterInvalidIDsAndAnnounce(event.guild!!, event.channel.asGuildMessageChannel())

        val channelList = channelIDs.joinToString("\n") {
            event.guild!!.getGuildChannelById(it)?.asMention ?: "Unknown channel"
        }
        val message = if (channelIDs.isEmpty()) {
            "There are no valid channels set."
        } else {
            "The following channels are set to be ${status}ed:\n$channelList"
        }

        event.hook.sendMessageEmbeds(
            NeoSuperEmbed {
                type = SuperEmbed.ResultType.SUCCESS
                text = message
            }).queue()

    }

    suspend fun status(event: SlashCommandInteractionEvent) {
        if (!permissionCheck(event.toWrapper(), Permission.ADMINISTRATOR)) {
            return
        }
        val guildId = event.guild!!.idLong

        val guildBarrage = getBarrageSettings(guildId)
        if (guildBarrage == null) {
            event.hook.sendMessageEmbeds(
                NeoSuperEmbed {
                    type = SuperEmbed.ResultType.SIMPLE
                    text = "The default status is set to block and this command has not been set up yet."
                }).queue()
            return
        }

        val status = if (guildBarrage.allowMode) "allow" else "block"
        val channelCount = guildBarrage.channelIDs.size

        event.hook.sendMessageEmbeds(
            NeoSuperEmbed {
                type = SuperEmbed.ResultType.SUCCESS
                text =
                    "The current status is set to $status and there are $channelCount channels in the list."
            }).queue()
    }

    // This is the function that checks if the channel is "ok" for command execution.
    // First it checks if the channel is in the list of allowed channels or blocked channels.
    // If it is, it checks if the mode is allow or block and returns true or false accordingly.
    suspend fun canContinue(event: MessageReceivedEvent): Boolean {
        val guildId = event.guild.idLong
        if (event.member?.let { permissionCheckMessageless(it, Permission.ADMINISTRATOR) } == true)
            return true
        val guildBarrage = getBarrageSettings(guildId) ?: return true
        // If no settings are found, we may default to the "block mode" and allow the command to continue.


        var channelIDs = guildBarrage.channelIDs as MutableList<Long>
        channelIDs = channelIDs.filterInvalidIDsAndAnnounce(
            event.guild,
            event.channel.asGuildMessageChannel()
        ) //todo: this should be using BotAlerts instead.
        channelIDs.contains(event.channel.idLong).let {
            if (guildBarrage.allowMode) {
                //if allowed mode, only those listed can execute commands.
                return it
            }
            //if blocked mode, everyone can execute commands except those listed.
            return !it
        }
    }

    suspend fun switchMode(event: SlashCommandInteractionEvent, chosenMode: Boolean) {
        val guildId = event.guild!!.idLong

        barrageSettingsCollection.updateOne(
            filter = eq(BarrageSettings::guildId.name, guildId),
            update = combine(
                setOnInsert(BarrageSettings::guildId.name, guildId),
                setOnInsert(BarrageSettings::channelIDs.name, emptyList<Long>()),
                Updates.set(BarrageSettings::allowMode.name, chosenMode)
            ),
            UpdateOptions().upsert(true)
        )

        val status = if (chosenMode) "allow" else "block"
        event.hook.sendMessageEmbeds(
            NeoSuperEmbed {
                type = SuperEmbed.ResultType.SUCCESS
                text = "**Barrage** is now set to $status mode."
            }).queue()
    }

}