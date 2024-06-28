package commands

object AntiScam {

    //TODO: https://github.com/Discord-AntiScam/scam-links
    // The bot should download the list at startup

    data class AntiScamSettings(
        val list: List<Long>, // List of channels to track. In order.
        val guildId: Long
    )
}