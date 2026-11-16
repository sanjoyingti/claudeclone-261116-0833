package com.aibuilt.claudeclone.network

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull

/** Follows a simple JSON path ("a.b[0].c") through a parsed body. */
object JsonPath {

    private sealed interface Token {
        data class Key(val name: String) : Token
        data class Index(val position: Int) : Token
    }

    fun select(root: JsonElement, path: String): String? {
        if (path.isBlank()) return null
        var current: JsonElement = root
        for (token in tokenize(path)) {
            current = when (token) {
                is Token.Key -> (current as? JsonObject)?.get(token.name) ?: return null
                is Token.Index -> (current as? JsonArray)?.getOrNull(token.position) ?: return null
            }
        }
        return textOf(current)
    }

    fun selectAny(root: JsonElement, paths: List<String>): String? {
        for (p in paths) {
            val found = select(root, p)
            if (!found.isNullOrBlank()) return found
        }
        return null
    }

    private fun textOf(element: JsonElement): String? = when (element) {
        is JsonPrimitive -> element.contentOrNull
        is JsonArray -> element.mapNotNull { textOf(it) }.joinToString("\n").ifBlank { null }
        is JsonObject -> element.toString()
    }

    private fun tokenize(path: String): List<Token> {
        val out = mutableListOf<Token>()
        val buffer = StringBuilder()
        val cleaned = path.trim().removePrefix("$").removePrefix(".")
        var inBracket = false
        for (ch in cleaned) {
            when {
                ch == '[' -> {
                    if (buffer.isNotEmpty()) {
                        out.add(Token.Key(buffer.toString()))
                        buffer.clear()
                    }
                    inBracket = true
                }

                ch == ']' -> {
                    inBracket = false
                    out.add(Token.Index(buffer.toString().trim().trim('"').toIntOrNull() ?: 0))
                    buffer.clear()
                }

                ch == '.' -> {
                    if (!inBracket && buffer.isNotEmpty()) {
                        out.add(Token.Key(buffer.toString()))
                        buffer.clear()
                    }
                }

                else -> buffer.append(ch)
            }
        }
        if (buffer.isNotEmpty()) out.add(Token.Key(buffer.toString()))
        return out
    }
}
