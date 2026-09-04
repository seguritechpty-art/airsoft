package com.airsoft.tracker.data.model

import kotlinx.serialization.Serializable

/** Usuario del escuadrón con su ubicación en tiempo real */
@Serializable
data class UserDto(
    val id: String = "",
    val nick: String = "",
    val color: String = "#4CAF50",
    val online: Boolean = false,
    val lat: Double? = null,
    val lng: Double? = null,
    val heading: Double? = null,
    val speed: Double? = null,
    val accuracy: Double? = null,
    val updated_at: Long? = null,
)

/** Objetivo/waypoint de la partida */
@Serializable
data class ObjectiveDto(
    val id: String = "",
    val squad_code: String = "",
    val name: String = "",
    val description: String = "",
    val lat: Double = 0.0,
    val lng: Double = 0.0,
    val color: String = "#FF0000",
    val radius: Int = 100,
    val completed: Int = 0,
    val created_by: String = "",
    val created_at: Long = 0,
)

/** Área de color (círculo o polígono) */
@Serializable
data class AreaDto(
    val id: String = "",
    val squad_code: String = "",
    val name: String = "",
    val color: String = "#00FF00",
    val opacity: Double = 0.5,
    val coordinates: String = "[]", // JSON: [[lat,lng],...]
    val type: String = "circle",
    val created_by: String = "",
    val created_at: Long = 0,
)

/** Mensaje de chat */
@Serializable
data class ChatMessageDto(
    val id: String = "",
    val nick: String = "",
    val text: String = "",
    val created_at: Long = 0,
)

/** Estado completo de la sala */
@Serializable
data class SquadStateDto(
    val squad: SquadInfoDto? = null,
    val users: List<UserDto> = emptyList(),
    val objectives: List<ObjectiveDto> = emptyList(),
    val areas: List<AreaDto> = emptyList(),
)

@Serializable
data class SquadInfoDto(
    val code: String = "",
    val name: String = "",
)

/** Respuesta de crear/unirse a partida */
@Serializable
data class JoinResponseDto(
    val squadCode: String = "",
    val squadName: String = "",
    val userId: String = "",
    val token: String = "",
    val user: UserDto? = null,
)