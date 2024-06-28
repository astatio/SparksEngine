plugins {
    id("idea")
    alias(libs.plugins.kotlinJvm)
    embeddedKotlin("plugin.serialization")
    application
    `maven-publish`
}

version = (System.currentTimeMillis() - 1668273517039) / 1000 / 10 // 1668273517039 is the time when I implemented this

dependencies {

    runtimeOnly(libs.kotlin.scripting.jsr223)
    api(kotlin("reflect"))
    api(libs.facebook.ktfmt)
    api(libs.kotlinx.serialization.core)
    api(libs.kotlinx.serialization.json)
    api(libs.kotlinx.coroutines.core)
    api(libs.json.json)

    //DE-COROUTINATOR FOR STACKTRACES
    api(libs.reformator.stacktrace.decoroutinator)

    //ALGORITHM FOR AUTOCOMPLETE
    api(libs.debatty.java.string.similarity)

    //RNG
    api(libs.apache.commons.rng.simple)
    api(libs.apache.commons.rng.sampling)

    //IMAGE MANIPULATION
    api(libs.imgscalr)

    //DATABASE
    api(libs.mongodb.driver.kotlin)

    //JDA
    api(libs.jda) {
        exclude(module = "opus-java")
    }
    api(libs.minn.jda.ktx)
    api(libs.minn.discord.webhooks)

    //TIME
    api(libs.ocpsoft.prettytime)
    api(libs.ocpsoft.prettytime.nlp)
    api(libs.kotlinx.datetime)

    //SYSTEM INFO
    api(libs.oshi.core)

    //LOGGING
    implementation(libs.logback.classic)
    implementation(libs.kotlin.logging)
    implementation(libs.slf4j.api)

    //OUTPUT COLORING
    api(libs.jansi)

    //HTTP CLIENT
    api(libs.ktor.client.core)
    api(libs.ktor.client.cio)
    api(libs.ktor.client.json)
    api(libs.ktor.client.serialization)
    api(libs.ktor.client.content.negotiation)
    api(libs.ktor.serialization.kotlinx.json)
}

kotlin {
    jvmToolchain(21)
}
