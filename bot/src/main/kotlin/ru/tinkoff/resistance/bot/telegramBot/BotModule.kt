package ru.tinkoff.resistance.bot.telegramBot

import com.github.kotlintelegrambot.Bot
import com.github.kotlintelegrambot.bot
import com.github.kotlintelegrambot.dispatch
import com.github.kotlintelegrambot.dispatcher.callbackQuery
import com.github.kotlintelegrambot.dispatcher.command
import com.github.kotlintelegrambot.entities.CallbackQuery
import com.github.kotlintelegrambot.entities.ChatId
import com.github.kotlintelegrambot.entities.ReplyMarkup
import com.github.kotlintelegrambot.webhook
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import kotlinx.coroutines.runBlocking
import ru.tinkoff.resistance.bot.AppConfig
import ru.tinkoff.resistance.errocodes.CommandErrorCode
import ru.tinkoff.resistance.model.request.*
import ru.tinkoff.resistance.model.response.InfoResponse

fun botModule(config: AppConfig, client: HttpClient): Bot {
    return bot {
        token = config.telegram.token

        webhook {
            url = config.telegram.webhookUrl
        }

        dispatch {
            command("start") {
                runBlocking {
                    val response = client.post<HttpResponse>(config.server.url + "player") {
                        method = HttpMethod.Post
                        contentType(ContentType.Application.Json)
                        body = PlayerCreateRequest(message.chat.id, message.from!!.firstName)
                    }
                    when (response.status) {
                        HttpStatusCode.Created -> {
                            bot.sendMsg(message.chat.id, "Добро пожаловать!", Buttons.START_BUTTONS)
                        }
                        HttpStatusCode.InternalServerError -> {
                            bot.sendMsg(message.chat.id, "Вы уже зарегистрированы!", Buttons.START_BUTTONS)
                        }
                        else -> {
                            bot.sendMsg(message.chat.id, "Что-то пошло не так")
                        }
                    }
                }
            }

            callbackQuery("create") {
                val id = callbackQuery.from.id
                runBlocking {
                    val response = client.post<HttpResponse>(config.server.url + "game/create") {
                        method = HttpMethod.Post
                        contentType(ContentType.Application.Json)
                        body = CreateGameRequest(id)
                    }
                    when (response.status) {
                        HttpStatusCode.Created -> {
                            val gameId = response.receive<Long>()
                            bot.sendMsg(id, "Игра успешно создана. Номер игры: $gameId", Buttons.START_GAME)
                            bot.deleteMsg(callbackQuery)
                        }
                        HttpStatusCode.InternalServerError -> {
                            val commandErrorCode = response.receive<CommandErrorCode>()
                            bot.sendMsg(id, commandErrorCode.getMessage())
                        }
                        HttpStatusCode.NotFound -> {
                            bot.sendMsg(id, "Вы не найдены в базе")
                        }
                        else -> {
                            bot.sendMsg(id, "Что-то пошло не так")
                        }
                    }
                }
            }

            callbackQuery("join") {
                this.bot.sendMsg(callbackQuery.from.id, "Введите id игры /join id")
                bot.deleteMsg(callbackQuery)
            }

            command("join") {
                val strings = message.text!!.split(" ")
                if (strings.size == 2) {
                    try {
                        val lobbyId = strings[1].toInt()
                        runBlocking {
                            val response = client.post<HttpResponse>(config.server.url + "game/join") {
                                method = HttpMethod.Post
                                contentType(ContentType.Application.Json)
                                body = JoinGameRequest(message.chat.id, lobbyId)
                            }
                            when (response.status) {
                                HttpStatusCode.OK -> {
                                    bot.sendMsg(message.chat.id, "Вы успешно зашли в игру. Номер игры: $lobbyId")
                                }
                                HttpStatusCode.InternalServerError -> {
                                    val commandErrorCode = response.receive<CommandErrorCode>()
                                    bot.sendMsg(message.chat.id, commandErrorCode.getMessage())
                                }
                                HttpStatusCode.NotFound -> {
                                    bot.sendMsg(message.chat.id, "Вы не найдены в базе")
                                }
                                else -> bot.sendMsg(message.chat.id, "Что-то пошло не так")
                            }
                        }

                    } catch (ex: NumberFormatException) {
                        bot.sendMsg(message.chat.id, "Неправильный ID игры")
                    }
                } else {
                    bot.sendMsg(message.chat.id, "Команда введена не правильно")
                }
            }

            callbackQuery("start") {
                val id = callbackQuery.from.id
                runBlocking {
                    val response = client.get<HttpResponse>(config.server.url + "game/start/$id") {
                        method = HttpMethod.Get
                        contentType(ContentType.Application.Json)
                    }
                    when (response.status) {
                        HttpStatusCode.OK -> {
                            bot.sendMsg(id, "Игра успешно запущена")
                            bot.deleteMsg(callbackQuery)
                            val infoResponse = response.receive<InfoResponse>()
                            bot.startGame(infoResponse)

                        }
                        HttpStatusCode.InternalServerError -> {
                            val commandErrorCode = response.receive<CommandErrorCode>()
                            bot.sendMsg(id, commandErrorCode.getMessage())
                        }
                        HttpStatusCode.NotFound -> {
                            bot.sendMsg(id, "Вы не найдены в базе")
                        }
                        else -> {
                            bot.sendMsg(id, "Что-то пошло не так")
                        }
                    }
                }
            }

            callbackQuery("invite") {
                val id = callbackQuery.from.id
                val candidateId = callbackQuery.data.split(" ")[1].toLong()
                runBlocking {
                    val response =
                        client.post<HttpResponse>(config.server.url + "game/chooseplayerformission") {
                            method = HttpMethod.Post
                            contentType(ContentType.Application.Json)
                            body = ChoosePlayerForMissionRequest(id, candidateId)
                        }
                    when (response.status) {
                        HttpStatusCode.OK -> {
                            val infoResponse = response.receive<InfoResponse>()
                            bot.choosePlayer(infoResponse)
                            bot.deleteMsg(callbackQuery)
                        }
                        HttpStatusCode.NotFound -> {
                            bot.sendMsg(id, "Игрок не найден в базе")
                        }
                        HttpStatusCode.InternalServerError -> {
                            val commandErrorCode = response.receive<CommandErrorCode>()
                            bot.sendMsg(id, commandErrorCode.getMessage())
                        }
                        else -> bot.sendMsg(id, "Что-то пошло не так")
                    }
                }
            }

            callbackQuery("voteYes") {
                val id = callbackQuery.from.id
                runBlocking {
                    val response = client.post<HttpResponse>(config.server.url + "game/voteforteam") {
                        method = HttpMethod.Post
                        contentType(ContentType.Application.Json)
                        body = VoteForTeamRequest(id, true)
                    }
                    when (response.status) {
                        HttpStatusCode.OK -> {
                            val infoResponse = response.receive<InfoResponse>()
                            bot.voteForTeam(infoResponse)
                            bot.deleteMsg(callbackQuery)
                        }
                        HttpStatusCode.InternalServerError -> {
                            val commandErrorCode = response.receive<CommandErrorCode>()
                            bot.sendMsg(id, commandErrorCode.getMessage())
                        }
                        else -> bot.sendMsg(id, "Что-то пошло не так")

                    }
                }
                bot.deleteMsg(callbackQuery)
            }

            callbackQuery("voteNo") {
                val id = callbackQuery.from.id
                runBlocking {
                    val response = client.post<HttpResponse>(config.server.url + "game/voteforteam") {
                        method = HttpMethod.Post
                        contentType(ContentType.Application.Json)
                        body = VoteForTeamRequest(id, false)
                    }
                    when (response.status) {
                        HttpStatusCode.OK -> {
                            val infoResponse = response.receive<InfoResponse>()
                            bot.voteForTeam(infoResponse)
                            bot.deleteMsg(callbackQuery)
                        }
                        HttpStatusCode.InternalServerError -> {
                            val commandErrorCode = response.receive<CommandErrorCode>()
                            bot.sendMsg(id, commandErrorCode.getMessage())
                        }
                        else -> bot.sendMsg(id, "Что-то пошло не так")
                    }
                }
            }

            callbackQuery("voteSuccess") {
                val id = callbackQuery.from.id
                runBlocking {
                    val response = client.post<HttpResponse>(config.server.url + "game/missionaction") {
                        method = HttpMethod.Post
                        contentType(ContentType.Application.Json)
                        body = MissionActionRequest(id, true)
                    }
                    when (response.status) {
                        HttpStatusCode.OK -> {
                            val infoResponse = response.receive<InfoResponse>()
                            bot.mission(infoResponse)
                            bot.deleteMsg(callbackQuery)
                        }
                        HttpStatusCode.InternalServerError -> {
                            val commandErrorCode = response.receive<CommandErrorCode>()
                            bot.sendMsg(id, commandErrorCode.getMessage())
                        }
                        else -> bot.sendMsg(id, "Что-то пошло не так")
                    }
                }
            }

            callbackQuery("voteFail") {
                val id = callbackQuery.from.id
                runBlocking {
                    val response = client.post<HttpResponse>(config.server.url + "game/missionaction") {
                        method = HttpMethod.Post
                        contentType(ContentType.Application.Json)
                        body = MissionActionRequest(id, false)
                    }
                    when (response.status) {
                        HttpStatusCode.OK -> {
                            val infoResponse = response.receive<InfoResponse>()
                            bot.mission(infoResponse)
                            bot.deleteMsg(callbackQuery)
                        }
                        HttpStatusCode.InternalServerError -> {
                            val commandErrorCode = response.receive<CommandErrorCode>()
                            bot.sendMsg(id, commandErrorCode.getMessage())
                        }
                        else -> bot.sendMsg(id, "Что-то пошло не так")
                    }
                }
            }
        }
    }
}

fun Bot.deleteMsg(callbackQuery: CallbackQuery) {
    this.deleteMessage(
        chatId = ChatId.fromId(callbackQuery.from.id),
        messageId = callbackQuery.message!!.messageId
    )
}

fun Bot.sendMsg(chatId: Long, text: String, replyMarkup: ReplyMarkup? = null) {
    this.sendMessage(
        chatId = ChatId.fromId(chatId),
        text = text,
        replyMarkup = replyMarkup
    )
}