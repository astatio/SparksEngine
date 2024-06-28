
import commands.*
import commands.ranks.Ranks
import dev.minn.jda.ktx.events.listener
import interfaces.ButtonInteractions
import interfaces.SlashCommands
import net.dv8tion.jda.api.JDA
import net.dv8tion.jda.api.entities.channel.ChannelType
import net.dv8tion.jda.api.events.guild.GuildAuditLogEntryCreateEvent
import net.dv8tion.jda.api.events.guild.member.GuildMemberJoinEvent
import net.dv8tion.jda.api.events.guild.member.GuildMemberRemoveEvent
import net.dv8tion.jda.api.events.guild.member.GuildMemberRoleAddEvent
import net.dv8tion.jda.api.events.guild.member.update.GuildMemberUpdateNicknameEvent
import net.dv8tion.jda.api.events.interaction.command.CommandAutoCompleteInteractionEvent
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent
import net.dv8tion.jda.api.events.interaction.command.UserContextInteractionEvent
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent
import net.dv8tion.jda.api.events.interaction.component.StringSelectInteractionEvent
import net.dv8tion.jda.api.events.message.MessageBulkDeleteEvent
import net.dv8tion.jda.api.events.message.MessageDeleteEvent
import net.dv8tion.jda.api.events.message.MessageReceivedEvent
import net.dv8tion.jda.api.events.message.MessageUpdateEvent
import net.dv8tion.jda.api.events.user.update.UserUpdateGlobalNameEvent
import net.dv8tion.jda.api.events.user.update.UserUpdateNameEvent

object Listeners {
    private lateinit var slashCommands: SlashCommands
    private lateinit var guildCommands: GuildCommands
    private lateinit var buttonInteractions: ButtonInteractions

    fun start(jda: JDA, slshCommands: SlashCommands, gldCommands: GuildCommands, bttnInteractions: ButtonInteractions) {
        slashCommands = slshCommands
        guildCommands = gldCommands
        buttonInteractions = bttnInteractions

        jda.listener<MessageReceivedEvent> {
            // checks if the message comes from an allowed channel
            if (it.author.isBot) {
                return@listener
            }
            if (it.isFromType(ChannelType.PRIVATE)) {
                onPrivateCommand(it)
            }
            if (it.isFromGuild && Barrage.canContinue(it)) {
                guildCommands.onGuildCommand(it)
            }
        }
        jda.listener<MessageReceivedEvent> {
            MessageCache.onMessageReceived(it)
        }
        jda.listener<MessageReceivedEvent> {
            InstantBan.findWords(it)
        }
        jda.listener<MessageReceivedEvent> {
            Ranks.onMessageReceived(it)
        }
        jda.listener<MessageReceivedEvent> {
            SpamPrevention.onMessageReceived(it)
        }
        jda.listener<GuildMemberJoinEvent> {
            Ranks.onMemberJoin(it)
        }
        jda.listener<SlashCommandInteractionEvent> {
            slashCommands.onSlashCommandInteraction(it)
        }
        jda.listener<CommandAutoCompleteInteractionEvent> {
            slashCommands.onCommandAutoCompleteInteraction(it)
        }
        jda.listener<UserContextInteractionEvent> {
            ContextMenus.onUserContextInteractionEvent(it)
        }
        jda.listener<StringSelectInteractionEvent> {
            slashCommands.onStringSelectInteraction(it)
        }
        jda.listener<ButtonInteractionEvent> {
            buttonInteractions.onButtonInteraction(it)
        }
        jda.listener<MessageDeleteEvent> {
            ModLog.onMessageDelete(it)
        }
        jda.listener<MessageBulkDeleteEvent> {
            ModLog.onMessageBulkDelete(it)
        }
        jda.listener<GuildMemberRemoveEvent> {
            ModLog.onGuildMemberRemove(it)
        }
        jda.listener<GuildMemberJoinEvent> {
            ModLog.onGuildMemberJoin(it)
        }
        jda.listener<GuildMemberUpdateNicknameEvent> {
            ModLog.onGuildMemberUpdateNickname(it)
        }
        jda.listener<MessageUpdateEvent> {
            ModLog.onMessageUpdate(it)
        }
        jda.listener<GuildAuditLogEntryCreateEvent> {
            ModLog.onGuildAuditLogEntryCreate(it)
        }
        jda.listener<GuildMemberUpdateNicknameEvent> {
            NameTracker.onGuildMemberUpdateNickname(it)
        }
        jda.listener<UserUpdateGlobalNameEvent> {
            NameTracker.onUserUpdateGlobalName(it)
        }
        jda.listener<UserUpdateNameEvent> {
            NameTracker.onUserUpdateName(it)
        }
        jda.listener<MessageReceivedEvent> {
            Tagging.onMessageReceived(it)
        }

    }
}
