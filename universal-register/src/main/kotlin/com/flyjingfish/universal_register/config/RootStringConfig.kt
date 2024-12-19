package com.flyjingfish.universal_register.config

enum class RootStringConfig(
    val propertyName: String,
    val defaultValue: String,
) {
    ENABLE("universalRegister.enable", "true"),
    APP_INCREMENTAL("universalRegister.app.isIncremental", "false"),
    MODE("universalRegister.mode", "debug"),
    CONFIG_JSON("universalRegister.configJson", "");

    companion object{
        val MODE_SET = setOf("debug","release","auto")
    }
}