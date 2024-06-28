package helpers

import kotlinx.coroutines.*
import net.dv8tion.jda.api.entities.Message
import kotlin.time.Duration

//todo: only RolePicker is using this for now. I think other commands could benefit from it.
object ScheduledExpiration {
    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())

    /**
     * Schedules a message to expire after a certain amount of time.
     * The message will be edited to show that it has expired and its components will be removed. However, if the message was already edited, only the components will be removed.
     * The buttons defined expiration time when using [dev.minn.jda.ktx.interactions.components.button] is not taken into account.
     * It's recommended to set the buttons expiration time to the same value as the message expiration time.
     *
     * @param expirationTime The amount of time to wait before expiring the message
     */
    fun Message.expire(expirationTime: Duration) {
        scope.launch {
            delay(expirationTime.inWholeMilliseconds)
            this@expire.editMessageComponents().queue()
        }
    }
}