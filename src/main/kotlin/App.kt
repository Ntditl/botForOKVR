import config.Config
import dev.inmo.tgbotapi.bot.ktor.telegramBot
import dev.inmo.tgbotapi.extensions.api.bot.getMe
import dev.inmo.tgbotapi.extensions.api.send.reply
import dev.inmo.tgbotapi.extensions.behaviour_builder.buildBehaviourWithLongPolling
import dev.inmo.tgbotapi.extensions.behaviour_builder.triggers_handling.onCommand
import io.ktor.client.*
import io.ktor.client.engine.okhttp.*
import java.io.File
import kotlinx.coroutines.*
import kotlinx.serialization.json.Json
import dev.inmo.tgbotapi.extensions.behaviour_builder.triggers_handling.onContentMessage
import java.time.Duration
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter


fun getCorrectFormForSeconds(seconds: Long): String {
    return when {
        seconds % 10 == 1L && seconds % 100 != 11L -> "$seconds секундаа"
        seconds % 10 in 2..4 && !(seconds % 100 in 12..14) -> "$seconds секунды"
        else -> "$seconds секунд"
    }
}

fun getCorrectFormForMinutes(minutes: Long): String {
    return when {
        minutes % 10 == 1L && minutes % 100 != 11L -> "$minutes минута"
        minutes % 10 in 2..4 && !(minutes % 100 in 12..14) -> "$minutes минуты"
        else -> "$minutes минут"
    }
}
fun getCorrectFormForHours(hours: Long): String {
    return when {
        hours % 10 == 1L && hours % 100 != 11L -> "$hours час"
        hours % 10 in 2..4 && !(hours % 100 in 12..14) -> "$hours часа"
        else -> "$hours часов"
    }
}


suspend fun main() {
    // create json to decode config
    val json = Json { ignoreUnknownKeys = true }
    // decode config
    val config: Config = json.decodeFromString(Config.serializer(), File("config.json").readText())
    // that is your bot
    val bot = telegramBot(config.token) {
        client = HttpClient(OkHttp) {
            config.client?.apply {
                // setting up telegram bot client
                setupConfig()
            }
        }
    }

    // that is kotlin coroutine scope which will be used in requests and parallel works under the hood
    val scope = CoroutineScope(Dispatchers.Default)

    // here should be main logic of your bot
    bot.buildBehaviourWithLongPolling(scope) {
        // in this lambda you will be able to call methods without "bot." prefix
        val me = getMe()
        var flagStartOfBypass = false
        val moscowZoneId = ZoneId.of("Europe/Moscow")
        val formatter = DateTimeFormatter.ofPattern("HH:mm:ss")
        // this method will create point to react on each /start command
        onCommand("start", requireOnlyCommandInMessage = true) {
            // simply reply :)
            reply(it, "Hello, I am ${me.firstName}")
            reply(it, "И я буду повторять все сообщения, которые вы мне отправите")
        }
        onContentMessage { // 1
            execute( // 2
                it.content.createResend(it.chat.id) // 3
            )
        }
        var startOfBypass: ZonedDateTime? = null
        onCommand("startbypass") {
            if (flagStartOfBypass) {
                reply(it, "Обход уже идёт, закончите предыдущий чтобы начать новый")
            } else {
                startOfBypass = ZonedDateTime.now(moscowZoneId)
                val formattedTimeOfStart = startOfBypass!!.format(formatter)
                reply(it, "Привет, обход начался\nНачало: $formattedTimeOfStart")
                flagStartOfBypass = true
            }
        }

        onCommand("toendbypass") {
            if (startOfBypass == null) {
                reply(it, "Обход еще не начался, чтобы завершить обход, сначала начните его")
            } else {
                val endOfBypass = ZonedDateTime.now(moscowZoneId)
                val duration = Duration.between(startOfBypass!!, endOfBypass)
                val hours = duration.toHours()
                val minutes = duration.toMinutes() % 60
                val seconds = duration.seconds % 60
                reply(it, "Обход завершен в: ${endOfBypass.format(formatter)} \nДлительность: ${getCorrectFormForHours(hours)} ${getCorrectFormForMinutes(minutes)} ${getCorrectFormForSeconds(seconds)}")
                startOfBypass = null
                flagStartOfBypass = false
            }
        }
        // That will be called on the end of bot initiation. After that println will be started long polling and bot will
        // react on your commands
        println(me)
    }.join()
}
