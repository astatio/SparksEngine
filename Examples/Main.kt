
import com.mongodb.kotlin.client.coroutine.MongoClient
import commands.*
import commands.Avatar.getAvatar
import commands.core.BotStatuses
import commands.ranks.Ranks
import dev.minn.jda.ktx.jdabuilder.createJDA
import dev.reformator.stacktracedecoroutinator.runtime.DecoroutinatorRuntime
import helpers.*
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.*
import kotlinx.serialization.json.Json
import net.dv8tion.jda.api.JDAInfo
import net.dv8tion.jda.api.events.message.MessageReceivedEvent
import net.dv8tion.jda.api.requests.GatewayIntent
import net.dv8tion.jda.api.utils.MemberCachePolicy
import net.dv8tion.jda.api.utils.cache.CacheFlag
import java.util.*


//todo: this usage example is old and incomplete .

//suspend fun main() {
//    DecoroutinatorRuntime.load()
//    //Create a coroutine dispatcher and a supervisor job
//    val myScope = CoroutineScope(Dispatchers.Default + SupervisorJob())
//    // stop all the jobs when one of the coroutines fails
//
//    val token = System.getenv("BOT_TOKEN") ?: throw IllegalArgumentException("No token found")
//
//    val jda = createJDA(
//        token,
//        intents = listOf(
//            GatewayIntent.GUILD_MEMBERS,
//            GatewayIntent.GUILD_MODERATION,
//            GatewayIntent.GUILD_EMOJIS_AND_STICKERS,
//            GatewayIntent.GUILD_INVITES,
//            GatewayIntent.GUILD_MESSAGES,
//            GatewayIntent.DIRECT_MESSAGES,
//            GatewayIntent.MESSAGE_CONTENT,
//            GatewayIntent.GUILD_PRESENCES
//        )
//    ) {
//        setMemberCachePolicy(MemberCachePolicy.ALL) //necessary to make GuildMemberRemove work
//        setBulkDeleteSplittingEnabled(false) //required in order to enable MessageBulkDeleteEvent
//        enableCache(CacheFlag.ACTIVITY)
//        disableCache(CacheFlag.VOICE_STATE, CacheFlag.CLIENT_STATUS, CacheFlag.ONLINE_STATUS)
//    }.awaitReady()
//
//    Locale.setDefault(
//        Locale.Builder()
//            .setLanguage("en")
//            .setRegion("US")
//            .build()
//    )
//    TimeZone.setDefault(TimeZone.getTimeZone("UTC"))
//
//    // Listeners.start(jda) - This is replaced in the submodules
//
//    // myScope.launch {
//    //     SlashCommandUpdates.start(jda)
//    // }
//
//    myScope.launch(handler) {
//        BotStatuses.startBotStatuses(jda)
//    }
//    myScope.launch(handler) {
//        try {
//            mimeJob = EntryController.mimeCheckJob(jda)
//            returnJob = EntryController.returnCheckJob(jda)
//            EntryController.jobInit(jda)
//        } catch (e: Exception) {
//            // Log the exception, or handle it in some other way
//            logger.error(e) { "Exception caught in job: ${e.message}" }
//        }
//    }
//}
