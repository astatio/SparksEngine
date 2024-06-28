package helpers

import com.mongodb.kotlin.client.coroutine.MongoClient
import com.mongodb.kotlin.client.coroutine.MongoDatabase
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.serialization.json.Json
import net.dv8tion.jda.api.JDA
import net.dv8tion.jda.api.JDAInfo
import net.dv8tion.jda.api.entities.Guild
import net.dv8tion.jda.api.entities.Member
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent
import kotlin.Error

const val INVISIBLE_EMBED_COLOR = 0x2b2d31
const val FEATURE_NOT_IMPLEMENTED = "This feature is not implemented yet. Try again in a few days!"

/**
 * Send an ephemeral message to the hook with the [FEATURE_NOT_IMPLEMENTED] text.
 */
fun SlashCommandInteractionEvent.featureNotImplemented() =
    this.hook.sendMessage(FEATURE_NOT_IMPLEMENTED).setEphemeral(true).queue()

val JDAVERSION = "%s.%s.%s%s".format(
    JDAInfo.VERSION_MAJOR, JDAInfo.VERSION_MINOR, JDAInfo.VERSION_REVISION,
    "-" + JDAInfo.VERSION_CLASSIFIER
)

val BOT_NAME = System.getenv("BOT_NAME") ?: "BOT_NAME"

val logger = KotlinLogging.logger {}

val mjson: Json by lazy {
    Json {
        prettyPrint = true
        ignoreUnknownKeys = true
    }
}

var mimeJob: JobProducerScheduler? = null
var returnJob: JobProducerScheduler? = null


/**
 * The database variable. This is a variable that needs to be initialized before its first use.
 * If it is not initialized, the whole program will halt!
 */
var database: MongoDatabase? = null
    get() {
        if (field == null) throw Error("Database variable not initialized! Halted.")
        else return field
    }
    set(value) {
        if (databaseWriteAttempts > 0) {
            logger.warn {
                "There was an attempt to write to the \"database\" variable after it was initialized. This is not allowed. Ignoring the write attempt."
            }
            return
        }
        field = value
        databaseWriteAttempts++
    }

/**
 * The variable that will be checked everytime the bot receives a [MemberJoinEvent].
 * By default, this is set to false, but it can be changed to true.
 *
 * The reason why it's set to false is because the implementation is not it it's desired state.
 * For the time being, it's recommended to make an implementation of your own.
 */
var onMemberJoin = false

/**
 * This variable will be used to check if SpamPrevention should be enabled or not.
 *
 * By default, this is set to false, but it can be changed to true.
 *
 * The reason why it's set to false is because the implementation is not it it's desired state.
 * One of the cases in which it fails is when a user sends an image.
 * The user might be spamming images, but the bot won't be able to detect that the image is the same or not - in case its being uploaded each time.
 *
 * This default value might change in the future so its recommended to always override this value.
 */
var spamPrevention = false

/**
 * This variable will be used to check if the user is allowed to use the "!eval" command or not.
 *
 * This array is empty, meaning that no one can use the command.
 *
 * If you want to allow someone to use the command, you can add their ID to the array.
 * You don't need to do it at startup. You can do it at any moment.
 */
var owners = arrayOf()

val OMDB_KEY = System.getenv("OMDB_KEY") ?: logger.error {"The OMDB_KEY environment variable is not set. The OMDB API will not be available."}

internal var databaseWriteAttempts = 0

var jda : JDA? = null
    get() {
        if (field == null) throw Error("JDA variable not initialized! Halted.")
        else return field
    }
    set(value) {
        if (jdaWriteAttempts > 0) {
            logger.warn {
                "There was an attempt to write to the \"jda\" variable after it was initialized. This is not allowed. Ignoring the write attempt."
            }
            return
        }
        field = value
        jdaWriteAttempts++
    }

internal var jdaWriteAttempts = 0

val handler = CoroutineExceptionHandler { context, exception ->
    logger.trace(exception) {
        "Exception caught in $context: $exception"
    }
}
