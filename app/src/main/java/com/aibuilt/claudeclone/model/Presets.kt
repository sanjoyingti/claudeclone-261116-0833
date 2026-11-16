package com.aibuilt.claudeclone.model

object Presets {

    val authHeaderPresets = listOf("Authorization", "x-api-key", "x-goog-api-key")
    val authPrefixPresets = listOf("Bearer", "raw")
    val responsePathPresets = listOf(
        "choices[0].message.content",
        "content[0].text",
        "candidates[0].content.parts[0].text"
    )

    const val SYSTEM_PROMPT = "You are Claude, a helpful AI assistant made by Anthropic."

    fun anthropicDefault(): Profile = Profile(
        name = "Anthropic",
        baseUrl = "https://api.anthropic.com",
        authHeaderName = "x-api-key",
        authValuePrefix = "",
        model = "claude-sonnet-4-5",
        pathTemplate = "{base}/v1/messages",
        dialect = Dialect.ANTHROPIC,
        responsePath = "content[0].text",
        stream = true
    )

    fun templateFor(dialect: Dialect): Profile = when (dialect) {
        Dialect.ANTHROPIC -> Profile(
            name = "Anthropic compatible",
            baseUrl = "",
            authHeaderName = "x-api-key",
            authValuePrefix = "",
            model = "",
            pathTemplate = "{base}/v1/messages",
            dialect = Dialect.ANTHROPIC,
            responsePath = "content[0].text"
        )

        Dialect.OPENAI -> Profile(
            name = "OpenAI compatible",
            baseUrl = "",
            authHeaderName = "Authorization",
            authValuePrefix = "Bearer",
            model = "",
            pathTemplate = "{base}/v1/chat/completions",
            dialect = Dialect.OPENAI,
            responsePath = "choices[0].message.content"
        )

        Dialect.GOOGLE -> Profile(
            name = "Google style",
            baseUrl = "",
            authHeaderName = "",
            authValuePrefix = "",
            model = "",
            pathTemplate = "{base}/v1beta/models/{model}:generateContent",
            dialect = Dialect.GOOGLE,
            responsePath = "candidates[0].content.parts[0].text"
        )

        Dialect.CUSTOM -> Profile(
            name = "Custom JSON",
            baseUrl = "",
            authHeaderName = "Authorization",
            authValuePrefix = "Bearer",
            model = "",
            pathTemplate = "{base}/v1/chat",
            dialect = Dialect.CUSTOM,
            responsePath = "",
            customBody = "{\n  \"model\": \"{model}\",\n  \"system\": \"{system}\",\n  \"prompt\": \"{text}\"\n}"
        )
    }

    fun applyDefaults(profile: Profile, dialect: Dialect): Profile {
        val preset = templateFor(dialect)
        return profile.copy(
            dialect = dialect,
            authHeaderName = preset.authHeaderName,
            authValuePrefix = preset.authValuePrefix,
            pathTemplate = preset.pathTemplate,
            responsePath = preset.responsePath,
            customBody = if (dialect == Dialect.CUSTOM && profile.customBody.isBlank()) preset.customBody else profile.customBody
        )
    }
}
