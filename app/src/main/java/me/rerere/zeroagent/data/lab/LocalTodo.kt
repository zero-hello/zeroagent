package me.rerere.zeroagent.data.lab

import android.content.Context
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
internal data class LocalTodo(val id: String, val title: String, val checked: Boolean = false)

internal const val TODO_KEY = "lab_confirmed_todos"
internal fun localTodos(context: Context): List<LocalTodo> = decodeTodos(LabPreferences.get(context, TODO_KEY))
internal fun decodeTodos(value: String): List<LocalTodo> = runCatching { Json.decodeFromString<List<LocalTodo>>(value) }.getOrDefault(emptyList())
internal fun saveTodos(context: Context, todos: List<LocalTodo>) { LabPreferences.set(context, TODO_KEY, Json.encodeToString(todos.takeLast(250))) }

