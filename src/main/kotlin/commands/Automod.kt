package commands

import dev.minn.jda.ktx.coroutines.await
import helpers.NeoSuperEmbed
import helpers.SuperEmbed
import helpers.throwEmbed
import net.dv8tion.jda.api.entities.automod.build.AutoModRuleData
import net.dv8tion.jda.api.entities.automod.build.TriggerConfig
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent

object Automod {

    //todo: Due to Discord markdown support, new problems for guilds were introduced.
    suspend fun blockHyperlinks(event: SlashCommandInteractionEvent) {
        val config = TriggerConfig.patternFilter("\\[(.*?)\\]\\((http|https)://[^\\s]+\\)")
        try {
            event.guild?.createAutoModRule(AutoModRuleData.onMessage("Block Hyperlinks", config))?.await()
            event.hook.editOriginalEmbeds(
                NeoSuperEmbed {
                    type = SuperEmbed.ResultType.SUCCESS
                    text = "Hyperlinks are now blocked."
                }).queue()
        } catch (e: Exception) {
            event.hook.editOriginalEmbeds(
                throwEmbed(e, "An error occurred while trying to block hyperlinks.")
            ).queue()
        }
    }
}
