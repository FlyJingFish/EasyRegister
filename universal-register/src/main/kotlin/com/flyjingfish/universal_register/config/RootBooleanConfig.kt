package com.flyjingfish.universal_register.config

enum class RootBooleanConfig(
    val propertyName: String,
    val defaultValue: Boolean,
) {
    ENABLE("universalRegister.enable", true),
    APP_INCREMENTAL("universalRegister.app.isIncremental", false),
}