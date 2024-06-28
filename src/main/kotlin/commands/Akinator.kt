package commands

/*
object Akinator {

    val timeout = 1.minutes // If the user doesn't reply within this amount of time, the session ends.

    suspend fun onMessageReceived(event: MessageReceivedEvent) {
        val channel = event.channel

        val akinator =
            Akotlinator.initialize()  // Begins the initialization process. This will return a GameSessionInitializer object.
                // The GameSessionInitializer object that as for now only one function: thenLanguagePrompt. This function requires a lambda function as a parameter.
                // The lambda function will be called to ask the user to choose a language. This isn't mandatory as the default language is English.
                .thenLanguagePrompt(event.message, Language.valueOf(event.guild.locale.toString())) {
                    EmbedBuilder {
                        title = "Akinator - Language"
                        field {
                            name = "Do you wish to change the language for this session?"
                            value =
                                "Select a language in the dropdown menu below. Select \"Default\" to use the default language."
                        }
                    }
                }.start(
                    onFailure = {
                        // An error occurred
                        event.message.replyEmbeds(
                            ThrowEmbed {
                                throwable = it
                                text = "An error occurred while initializing the game session."
                            }
                        ).queue()
                    }
                )!!

        while (akinator.isRunning) {
            while (!akinator.readyToGuess && akinator.question.question != null) {
                val question = akinator.question // Asks a question to the user. This will return a Question object.
                val qMessage =event.message.replyEmbeds(
                    Embed {
                        title = "Akinator - Question"
                        field {
                            name = "Question #${question.step}"
                            value = question.question.toString()
                        }
                    }
                ).await()
                //TODO: Need to add the buttons

                // Wait for the user to answer the question
                val answer = withTimeoutOrNull(timeout) {
                    channel.jda.await<ButtonInteractionEvent> {
                        (it.message.idLong == qMessage.idLong) && (it.user.idLong == qMessage.author.idLong)
                    }
                }
                akinator.prepareNextQuestion()
            }
            if(akinator.question.question == null && akinator.guesses.isEmpty()) {
                // Akinator ran out of questions, but couldn't guess the character.
            }
            while (akinator.guesses.isNotEmpty()) {

            }
            akinator.shutdown()
        }
    }
}*/
