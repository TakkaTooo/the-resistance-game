package ru.tinkoff.resistance.game.commands

import ru.tinkoff.resistance.game.Game
import ru.tinkoff.resistance.game.GameState

/**
 * Команда для голосования за результат миссии
 * @property action результат миссии (true - success, false - fail)
 */
class MissionActionCommand(
    override val senderId: Int,
    override val senderName: String,
    private val action: Boolean
) : Command {
    override fun execute(game: Game) {
        if (game.gameState != GameState.MISSION) {
            throw CommandExecutionException("Попытка голосования в миссии в другой стадии игры ($senderId, $senderName).", CommandErrorCode.DO_MISSION_IN_NOT_MISSION_STATE)
        }

        if (!game.isInTeam(senderId)) {
            throw CommandExecutionException("Игрок не учавствует в миссии ($senderId, $senderName).", CommandErrorCode.PLAYER_DONT_DO_MISSION)
        }

        if (game.doneMission(senderId)) {
            throw CommandExecutionException("Игрок уже сделал миссию ($senderId, $senderName).", CommandErrorCode.ALREADY_DONE_MISSION)
        }

        game.setMissionVote(senderId, action)
    }
}