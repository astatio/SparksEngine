package helpers

import net.dv8tion.jda.api.interactions.components.buttons.ButtonInteraction

/**
 * Makes new interactions impossible on the message this button is attached to and edits to show a "Canceled" message.
 * This works well in combination with [ScheduledExpiration.expire], as the "Canceled" message will persist after the expiration time.
 */
fun ButtonInteraction.cancel() {
    this.hook.editOriginalEmbeds(
        NeoSuperEmbed {
            type = SuperEmbed.ResultType.SIMPLE
            text = "Canceled"
        }
    ).setComponents().queue()
}