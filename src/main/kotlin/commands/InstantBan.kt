package commands

import com.mongodb.client.model.Filters.and
import com.mongodb.client.model.Filters.eq
import com.mongodb.client.model.Updates.set
import dev.minn.jda.ktx.coroutines.await
import dev.minn.jda.ktx.interactions.components.button
import helpers.*
import helpers.ScheduledExpiration.expire
import info.debatty.java.stringsimilarity.JaroWinkler
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.toList
import net.dv8tion.jda.api.Permission
import net.dv8tion.jda.api.events.interaction.command.CommandAutoCompleteInteractionEvent
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent
import net.dv8tion.jda.api.events.message.MessageReceivedEvent
import net.dv8tion.jda.api.interactions.components.buttons.ButtonStyle
import java.util.*
import java.util.concurrent.TimeUnit
import kotlin.time.Duration.Companion.minutes

object InstantBan {

    val wordCache = mutableMapOf<Long, Cache<List<Word>>>()

    suspend fun fetchWords(guildId: Long): List<Word> {
        return instantBanCollection.find(eq(Word::guildId.name, guildId)).toList()
    }

    data class Word(
        val name: String, //The word to be censored
        val action: String, //By default, is "ban". Can only be "ban", "kick", "timeout", or "warn".
        val guildId: Long
    )

    // TODO: Implementation for "timeout" and "warn" is not done yet.
    /* New data class:
    data class Word(
        val name: String, //The word to be censored
        val action: String, //By default, is "ban". Can only be "ban", "kick", "timeout", or "warn".
        val timeout: Int, //The amount of time to timeout the user for. Only used if action is "timeout". If not set, will use guilds default timeout.
        val warn: Int, //The amount of warns to give the user. Only used if action is "warn". If not set, will use guilds default warn amount.
        val onMaxWarn: String, //The action to take when the user reaches the max amount of warns. Only used if action is "warn". Can only be "ban", "kick", "timeout", or "none".
        val alert: Boolean, //Whether to send an alert in a channel when this word is triggered.
        val severeAlert: Boolean, //Whether a predefined role should be pinged alongside the alert.
        val guildId: Long
    )
    */

    val instantBanCollection = database!!.getCollection<Word>("word")

    // [x] /instantban add <word> <action> (action is optional)
    // [-] /instantban remove <word> - removes the word from the list. Needs to use autocomplete.
    // [x] /instantban addmany <word> <word> ... - adds many words at once. They'll all have the default action of "ban".
    // [x] /instantban list - lists all the words and their actions. It's sent through DMs.
    // [x] /instantban change <word> <action> - changes the action of the word. It can only be "ban" or "kick". Needs to use autocomplete.
    // [x] /instantban flush - flushes the entire list of words. Needs confirmation.

    suspend fun addWord(event: SlashCommandInteractionEvent, word: String, action: String) {
        if (!permissionCheck(event.toWrapper(), Permission.ADMINISTRATOR)) {
            return
        }
        val guildId = event.guild!!.idLong

        try {
            // Reject words with more than 100 characters
            if (word.length > 100) {
                event.hook.editOriginalEmbeds(
                    NeoSuperEmbed {
                        type = SuperEmbed.ResultType.ERROR
                        text = "The word is too long. It must be less than 100 characters."
                    }).queue()
                return
            }
            // Check if the word already exists. Lowercase is needed to avoid duplicates.
            val lowerWord = word.lowercase(Locale.getDefault())
            if (instantBanCollection.find(
                    and(eq(Word::guildId.name, guildId),
                    eq(Word::name.name, lowerWord))
            ).firstOrNull() != null) {
                event.hook.editOriginalEmbeds(
                    NeoSuperEmbed {
                        type = SuperEmbed.ResultType.ERROR
                        text =
                            "The word is already in the list. If you want to change the action, use `/instantban change`."
                    }).queue()
                return
            }
            // Create new word
            val newWord = Word(name = lowerWord, action = action, guildId = guildId)

            // Insert a new word into the collection
            instantBanCollection.insertOne(newWord)

            //Force update the cache
            wordCache[guildId]?.forceUpdate { fetchWords(guildId) }

            event.hook.sendMessageEmbeds(
                NeoSuperEmbed {
                    type = SuperEmbed.ResultType.SUCCESS
                    text = "Added the word to the list with the action of `$action`."
                }).queue()
        } catch (t: Throwable) {
            logger.trace(t) {
                "Error while adding a word to an InstantBan collection."
            }
            event.hook.sendMessageEmbeds(
                throwEmbed(t, "Error while adding a word to InstantBan.")
            ).queue()
            return
        }
    }


    //Precompiled regex pattern
    private val spacePatternRegex = "\\s+".toRegex()

    suspend fun addMany(event: SlashCommandInteractionEvent, manyWords: String) {
        if (!permissionCheck(event.toWrapper(), Permission.ADMINISTRATOR)) {
            return
        }
        val guildId = event.guild!!.idLong

        // Split words by space
        val wordsList = manyWords.split(spacePatternRegex)

	// Check for words longer than 100 characters.
        wordsList.forEach {
            if (it.length > 100) {
                event.hook.editOriginalEmbeds(
                    NeoSuperEmbed {
                        type = SuperEmbed.ResultType.ERROR
                        text = "The word `$it` is too long. It must be less than 100 characters. No words were added."
                    }).queue()
                return
            }
        }

        if (wordsList.isEmpty()) {
            event.hook.editOriginalEmbeds(
                NeoSuperEmbed {
                    type = SuperEmbed.ResultType.ERROR
                    text = "There are no words to add."
                }
            ).queue()
            return
        }

        try {
            val confirm = event.jda.button(
                label = "Confirm",
                style = ButtonStyle.PRIMARY,
                user = event.user,
                expiration = 5.minutes
            ) { butt ->
                // Add the words to the database
                wordsList.forEach { word ->
                    instantBanCollection.insertOne(
                        Word(
                            name = word.lowercase(Locale.getDefault()),
                            action = "ban",
                            guildId = guildId
                        )
                    )
                }
                event.hook.editOriginalEmbeds(
                    NeoSuperEmbed {
                        type = SuperEmbed.ResultType.SUCCESS
                        text =
                            "All words have been added. If you wish to change the action of them, use `/instantban change`."
                    }
                ).setComponents().queue()
            }

            val cancel = event.jda.button(
                label = "Cancel",
                style = ButtonStyle.SECONDARY,
                user = event.user,
                expiration = 5.minutes
            ) { butt ->
                event.hook.editOriginalEmbeds(
                    NeoSuperEmbed {
                        type = SuperEmbed.ResultType.SIMPLE
                        text = "Addition cancelled."
                    }
                ).setComponents().queue()
            }

            event.hook.editOriginalEmbeds(
                NeoSuperEmbed {
                    type = SuperEmbed.ResultType.ALERT
                    text =
                        "Are you sure you want to add all these words? They will all have the default action of `ban` which can only be changed afterwards." +
                                "\n" + wordsList.joinToString("\n") { "`$it`" }
                }
            ).setActionRow(confirm, cancel).queue {
                it.expire(5.minutes)
            }
        } catch (e: Throwable) {
            e.printStackTrace()
            event.hook.sendMessageEmbeds(
                throwEmbed(e)
            ).queue()
        }
    }

    suspend fun handleAutoComplete(event: CommandAutoCompleteInteractionEvent) {
        if (event.focusedOption.name == "word") {
            if (!wordCache.containsKey(event.guild!!.idLong)) {
                wordCache[event.guild!!.idLong] = Cache()
            }
            val words = wordCache[event.guild!!.idLong]!!.request { fetchWords(event.guild!!.idLong) }
                .map { it.name }
                .filter { JaroWinkler().similarity(it, event.focusedOption.value) > 0.6 }
                .take(25)
            event.replyChoiceStrings(words).queue()
        }
    }

    suspend fun removeWord(event: SlashCommandInteractionEvent, word: String) {
        if (!permissionCheck(event.toWrapper(), Permission.ADMINISTRATOR)) {
            return
        }
        val guildId = event.guild!!.idLong

        try {
            // Check if word exists
            val lowerWord = word.lowercase(Locale.getDefault())
            val existingWord = instantBanCollection.find(and(
                eq(Word::guildId.name, guildId),
                eq(Word::name.name, lowerWord)
            )
            ).firstOrNull()
            if (existingWord == null) {
                event.hook.editOriginalEmbeds(
                    NeoSuperEmbed {
                        type = SuperEmbed.ResultType.ERROR
                        text = "The word is not in the list."
                    }).queue()
                return
            }

            // Remove the word from the collection
            instantBanCollection.deleteOne(and(eq(Word::guildId.name, guildId), eq(Word::name.name, lowerWord)))

            event.hook.sendMessageEmbeds(
                NeoSuperEmbed {
                    type = SuperEmbed.ResultType.SUCCESS
                    text = "Removed the word from the list."
                }).queue()
        } catch (e: Throwable) {
            e.printStackTrace()
            event.hook.sendMessageEmbeds(
            throwEmbed(e)
            ).queue()
            return
        }
    }

    suspend fun listWords(event: SlashCommandInteractionEvent) {
        if (!permissionCheck(event.toWrapper(), Permission.ADMINISTRATOR)) {
            return
        }
        val guildId = event.guild!!.idLong

        try {
            val guildWords = fetchWords(guildId)
            if (guildWords.isEmpty()) {
                event.hook.editOriginalEmbeds(
                    NeoSuperEmbed {
                        type = SuperEmbed.ResultType.ERROR
                        text = "There are no words set."
                    }
                ).queue()
                return
            }

            // Build the string for each word and its action
            val wordList = guildWords.joinToString("\n") {
                "`${it.name} (${it.action})`"
            }
            try {
                event.user.openPrivateChannel().await().sendMessage("## WORDS LIST\n${event.guild!!.name}\n$wordList")
                    .await()
            } catch (e: Throwable) {
                event.hook.editOriginalEmbeds(
                    NeoSuperEmbed {
                        type = SuperEmbed.ResultType.ERROR
                        text = "I couldn't send you a DM. Please enable DMs from server members and try again."
                    }
                ).queue()
                return
            }
            event.hook.editOriginalEmbeds(
                NeoSuperEmbed {
                    type = SuperEmbed.ResultType.SUCCESS
                    text = "I've sent you a DM with the list of words."
                }
            ).queue()
        } catch (e: Throwable) {
            e.printStackTrace()
            event.hook.sendMessageEmbeds(
                throwEmbed(e)
            ).queue()
        }
    }

    suspend fun changeWord(event: SlashCommandInteractionEvent, wordToChange: String, newAction: String) {
        if (!permissionCheck(event.toWrapper(), Permission.ADMINISTRATOR)) {
            return
        }
        val lowercaseAction = newAction.lowercase(Locale.getDefault())
        if (lowercaseAction != "ban" && lowercaseAction != "kick") {
            event.hook.editOriginalEmbeds(
                NeoSuperEmbed {
                    type = SuperEmbed.ResultType.ERROR
                    text = "Invalid action specified. Can only be 'ban' or 'kick'."
                }
            ).queue()
            return
        }
        val guildId = event.guild!!.idLong

        try {
            val lowerWord = wordToChange.lowercase(Locale.getDefault())
            val word = instantBanCollection.find(and
                (eq(Word::guildId.name, guildId),
                eq(Word::name.name, lowerWord))
            ).firstOrNull()
            if (word == null) {
                event.hook.editOriginalEmbeds(
                    NeoSuperEmbed {
                        type = SuperEmbed.ResultType.ERROR
                        text = "The specified word does not exist."
                    }
                ).queue()
                return
            }
            if (word.action == lowercaseAction) {
                event.hook.editOriginalEmbeds(
                    NeoSuperEmbed {
                        type = SuperEmbed.ResultType.ERROR
                        text = "The word is already set to that action."
                    }
                ).queue()
                return
            }

            // update the word action
            instantBanCollection.updateOne(
                filter =
                    and(eq(Word::guildId.name, guildId), eq(Word::name.name, lowerWord)),
                update = set(Word::action.name, lowercaseAction)
            )

            event.hook.sendMessageEmbeds(
                NeoSuperEmbed {
                    type = SuperEmbed.ResultType.SUCCESS
                    text = "Changed action for word '$wordToChange' to '$lowercaseAction'."
                }
            ).queue()
        } catch (e: Throwable) {
            e.printStackTrace()
            event.hook.sendMessageEmbeds(
                throwEmbed(e)
            ).queue()
        }
    }

    suspend fun flush(event: SlashCommandInteractionEvent) {
        if (!permissionCheck(event.toWrapper(), Permission.ADMINISTRATOR)) {
            return
        }
        val guildId = event.guild!!.idLong

        try {
            val wordCount = instantBanCollection.find(eq(Word::guildId.name, guildId)).toList()

            if (wordCount.isEmpty()) {
                event.hook.editOriginalEmbeds(
                    NeoSuperEmbed {
                        type = SuperEmbed.ResultType.ERROR
                        text = "There are no words to flush."
                    }
                ).setComponents().queue()
                return
            }

            val accept = event.jda.button(
                label = "Cancel",
                style = ButtonStyle.SECONDARY,
                user = event.user,
                expiration = 5.minutes
            ) { butt ->
                event.hook.editOriginalEmbeds(
                    NeoSuperEmbed {
                        type = SuperEmbed.ResultType.SIMPLE
                        text = "Flush cancelled."
                    }
                ).setComponents().queue()
            }

            val deny = event.jda.button(
                label = "Confirm",
                style = ButtonStyle.PRIMARY,
                user = event.user,
                expiration = 5.minutes
            ) { butt ->
                instantBanCollection.deleteMany(eq(Word::guildId.name, guildId))
                event.hook.editOriginalEmbeds(
                    NeoSuperEmbed {
                        type = SuperEmbed.ResultType.SUCCESS
                        text = "All words have been flushed."
                    }
                ).setComponents().queue()
            }

            event.hook.editOriginalEmbeds(
                NeoSuperEmbed {
                    type = SuperEmbed.ResultType.ALERT
                    text = "Are you sure you want to flush all words? This cannot be undone."
                }
            ).setActionRow(accept, deny).queue()
            delay(5.minutes)
        } catch (e: Throwable) {
            e.printStackTrace()
            event.hook.sendMessageEmbeds(
                throwEmbed(e)
            ).queue()
        }
    }

    suspend fun findWords(event: MessageReceivedEvent) {
        if (!event.isFromGuild) return
        if (event.author.isBot) {
            return
        }
        val guildId = event.guild.idLong
        if (!wordCache.containsKey(guildId)) {
            wordCache[guildId] = Cache()
        }
        try {
            val wordObjects = wordCache[guildId]?.request { fetchWords(guildId) }
            wordObjects?.forEach { swear ->
                val matched = event.message.contentDisplay.contains(swear.name, true)
                if (matched) {
                    when (swear.action) {
                        "ban" -> {
                            kotlin.runCatching {
                                event.member?.ban(0, TimeUnit.DAYS)
                                    ?.reason("Filtered by InstantBan (prohibited link or word)")?.await()
                            }.onSuccess {
                                event.channel.sendMessage("A user was banned for sending a prohibited link or word.")
                                    .queue()
                            }.onFailure {
                                event.channel.sendMessageEmbeds(
                                    throwEmbed(it, "I was unable to ban due to:")
                                ).queue()
                            }
                        }

                        "kick" -> {
                            event.member?.kick()?.reason("Filtered by InstantBan (prohibited link or word)")?.queue({
                                event.channel.sendMessage("A user was kicked for sending a prohibited link or word.")
                                    .queue()
                            },
                                {
                                    queueFailure(event.message, "I was unable to kick due to:")
                                })
                        }
                    }
                    event.message.delete().queue()
                }
            }
        } catch (e: Throwable) {
            logger.error(e) {
                "Error while checking for prohibited words in InstantBan"
            }
        }
    }
}
