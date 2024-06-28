
import BookHandler.bookRegex
import BookHandler.registeredBookRouters
import ModLog.pingCollection
import com.mongodb.client.model.Filters
import helpers.FEATURE_NOT_IMPLEMENTED
import helpers.logger
import interfaces.ButtonInteractions
import kotlinx.coroutines.flow.firstOrNull
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent


// Button Interactions are meant to be shared. As such, they are defined in the shared module.
object ButtonInteractionsImpl : ButtonInteractions {

    override suspend fun onButtonInteraction(event: ButtonInteractionEvent) {
        // I can't put event.deferEdit() here as the other ephemeral button interactions will also be
        // acknowledged and it might cause issues
        if (event.componentId.startsWith("ping-on-return-")) {
            event.deferEdit().queue() // acknowledge the button was clicked; otherwise the interaction will fail

            val userToReturnId = event.componentId.replace("ping-on-return-", "").toLong()
            val userToPingId = event.user.idLong
            val guildId = event.guild!!.idLong

            // Try to find the ping in MongoDB
            val existingPing = pingCollection.find(
                Filters.and(
                    Filters.eq(ModLog.Ping::userToPing.name, userToPingId),
                    Filters.eq(ModLog.Ping::userToReturn.name, userToReturnId),
                    Filters.eq(ModLog.Ping::guildId.name, guildId)
                )
            ).firstOrNull()

            if (existingPing != null) {
                pingCollection.deleteOne(
                    Filters.and(
                        Filters.eq(ModLog.Ping::userToPing.name, userToPingId),
                        Filters.eq(ModLog.Ping::userToReturn.name, userToReturnId),
                        Filters.eq(ModLog.Ping::guildId.name, guildId)
                    )
                )
                event.hook.setEphemeral(true)
                    .sendMessage("You will no longer be notified when this user returns").queue()
            } else {
                // Insert a new ping into MongoDB
                val newPing = ModLog.Ping(userToPingId, userToReturnId, guildId)
                pingCollection.insertOne(newPing)
                event.hook.setEphemeral(true).sendMessage("You will be notified when this user returns")
                    .queue()
            }
            return
        }
        if (event.componentId.startsWith("modify-reason-")) {
            event.deferEdit().queue() // acknowledge the button was clicked; otherwise the interaction will fail
            event.hook.setEphemeral(true).sendMessage(FEATURE_NOT_IMPLEMENTED).queue()
            return
        }
        if (event.componentId.startsWith("super-userinfo-")) {
            event.deferEdit().queue()
            event.hook.setEphemeral(true).sendMessage(FEATURE_NOT_IMPLEMENTED).queue()
            // CommandsGeneral.superUserInfo(event)
            return
        }
        //the below is for the role picker conf buttons. Here will be routing to the corresponding functions.
        if (event.componentId.startsWith("book-")) {
            event.deferEdit().queue()
            ///typicall component id: book-<bookName>-<pageName>
            //the book cant contain hyphens, numbers nor special characters it must be purely alphabetical
            val match = bookRegex.find(event.componentId)
            val (book, page) = match?.destructured ?: run {
                logger.error { "Book regex failed to match for componentId: ${event.componentId}" }
                return
            }

            registeredBookRouters.firstOrNull {
                it.supportedBooks.contains(book)
            }?.route(event, book, page)
            return
        }
        if (event.componentId.startsWith("rp-")) {
            event.deferEdit().queue()
            event.hook.setEphemeral(true).sendMessage(FEATURE_NOT_IMPLEMENTED).queue()
            return
        }
    }
}
