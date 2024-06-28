
import dev.minn.jda.ktx.coroutines.await
import dev.minn.jda.ktx.messages.EmbedBuilder
import helpers.FEATURE_NOT_IMPLEMENTED
import helpers.INVISIBLE_EMBED_COLOR
import helpers.JDAVERSION
import net.dv8tion.jda.api.EmbedBuilder
import net.dv8tion.jda.api.JDAInfo
import net.dv8tion.jda.api.entities.Guild
import net.dv8tion.jda.api.entities.User
import net.dv8tion.jda.api.events.interaction.command.GenericCommandInteractionEvent
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent
import net.dv8tion.jda.api.events.message.MessageReceivedEvent
import net.dv8tion.jda.api.utils.TimeFormat
import org.ocpsoft.prettytime.PrettyTime
import oshi.SystemInfo
import java.lang.management.ManagementFactory
import java.text.DateFormat
import java.text.SimpleDateFormat
import java.util.*


object CommandsGeneral {

//todo: Help in the future should work like something that generates an embed automatically
// when the help command is called and allow the function to be overriden totally: but for now,
// lets remove it from shared code

    fun help(event: SlashCommandInteractionEvent) {
        event.deferReply(true).queue()
        event.hook.sendMessage(FEATURE_NOT_IMPLEMENTED).queue()
    }

    fun ping(event: SlashCommandInteractionEvent) {
        event.deferReply().queue()
        event.jda.restPing.queue { time ->
            event.hook.sendMessageFormat(
                "Ping: %d ms | Websocket: ${event.jda.gatewayPing} ms",
                time
            ).queue()
        }
    }

    fun uptime(event: SlashCommandInteractionEvent) {
        event.deferReply().queue()
        val runtimeMXBean = ManagementFactory.getRuntimeMXBean()
        val dateFormat: DateFormat = SimpleDateFormat("HH:mm:ss")
        dateFormat.timeZone = TimeZone.getTimeZone("UTC")
        val uptime = runtimeMXBean.uptime
        val finalUptime = (uptime / (3600 * 1000 * 24)).toString() + ":" + dateFormat.format(uptime)
        event.hook.sendMessage("Online for: $finalUptime").queue()
    }

    fun info(event: SlashCommandInteractionEvent) {
        event.deferReply().queue()
        //command that shows various information about the bot like Java version, JDA version, Kotlin version, Gradle version, etc.
        val sysInfo = SystemInfo()
        val runtimeMXBean = ManagementFactory.getRuntimeMXBean()
        val em = EmbedBuilder {
            color = INVISIBLE_EMBED_COLOR
            field {
                name = "Java VM"
                value = runtimeMXBean.vmVendor + "\n" + runtimeMXBean.vmName + "\n" + runtimeMXBean.vmVersion
                inline = true
            }
            field {
                name = "CPU"
                value =
                    sysInfo.hardware.processor.processorIdentifier.name + "\n" + sysInfo.hardware.processor.processorIdentifier.microarchitecture + "\nLoad: " + sysInfo.hardware.processor.getProcessorCpuLoad(1000).average().times(100).toInt() + "%"
                inline = true
            }
            field {
                name = "RAM"
                value = sysInfo.hardware.memory.toString()
                inline = true
            }

            field {
                name = "OS"
                value = sysInfo.operatingSystem.toString() + "\n" + System.getProperty("os.arch").toString()
                inline = true
            }
            field {
                name = "Libraries"
                value = "SparksEngine: EXPERIMENTAL\n[JDA: ${JDAVERSION}](${JDAInfo.GITHUB})\n[Kotlin: ${KotlinVersion.CURRENT}](https://kotlinlang.org/)"
                inline = true
            }
        }
        event.hook.sendMessageEmbeds(em.build()).queue()
    }

    suspend fun getMemberNumberInGuild(event: GenericCommandInteractionEvent, user: User): Int {
        val guild = event.guild ?: return 0
        val memberList = guild.loadMembers().await()
        // get each member joined time and sort them by joined time. From oldest (smaller epoch time) to newest (bigger epoch time)
        val sortedMemberList = memberList.sortedBy { it.timeJoined.toInstant().toEpochMilli() }

        // get the index of the user in the sorted list
        return sortedMemberList.indexOfFirst { it.idLong == user.idLong } + 1
    }

    fun serverinfo(event: SlashCommandInteractionEvent) {
        event.deferReply().queue()
        val hook = event.hook

        //The guild check is done in interfaces.SlashCommands.kt so there's no need to worry about getting a null pointer exception here
        val guild: Guild = event.guild!!
        val approximateOnlineMemberCount = guild.retrieveMetaData().complete().approximatePresences
        val owner = guild.retrieveOwner().complete()
        var boostTier = guild.boostTier.toString()
        when (boostTier) {
            "NONE" -> boostTier = "0"
            "TIER_1" -> boostTier = "1"
            "TIER_2" -> boostTier = "2"
            "TIER_3" -> boostTier = "3"
        }

        //TODO: This can be replaced with PrettyTime
        val createdInstant = guild.timeCreated.toInstant()
        val preciseDuration = PrettyTime().calculatePreciseDuration(createdInstant)
        val formattedTimeCreated = PrettyTime().formatDurationUnrounded(preciseDuration)
        var botCount = 0
        guild.loadMembers().onSuccess { membersList ->
            membersList.forEach { member ->
                if (member.user.isBot) {
                    botCount++
                }
            }
        }
        // Complete the solve function below.
        val guildMaxFileSize = guild.maxFileSize / 1024 / 1024
        val guildMaxBitrate = guild.maxBitrate / 1000
        val serverinfoMessage = EmbedBuilder().setTitle(guild.name)
            .setDescription(guild.description)
            .setColor(0x43b481)
            .setThumbnail(guild.iconUrl)
            .addField(
                "Creation Date",
                "" + TimeFormat.DATE_TIME_LONG.format(guild.timeCreated.toZonedDateTime()) + "\n($formattedTimeCreated ago)",
                true
            ).addField(
                "Online Members",
                "${approximateOnlineMemberCount}/${guild.memberCount}",
                true
            )
            .addField(
                "Channels",
                "Text: ${guild.textChannels.size}\nVoice: ${guild.voiceChannels.size}",
                true
            )
            .addField(
                "Owner",
                owner.user.name + " <:owner:823661010932989952>",
                true
            )
            .addField(
                "Custom Emojis",
                guild.emojis.size.toString(), false
            )
            .addField(
                "Stickers",
                guild.stickers.size.toString(), false
            )
            .addField(
                "Nitro Boosts",
                "Level: ${boostTier}\nMax file size: ${guildMaxFileSize}MBs\nMax emotes: ${guild.maxEmojis}\nMax bitrate: ${guildMaxBitrate}kbps",
                false
            )
            .setFooter("ServerID: ${guild.id}")
        hook.sendMessageEmbeds(serverinfoMessage.build()).queue()
    }
}
