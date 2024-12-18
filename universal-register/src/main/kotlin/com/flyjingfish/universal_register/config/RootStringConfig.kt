package com.flyjingfish.universal_register.config

enum class RootStringConfig(
    val propertyName: String,
    val defaultValue: String,
) {
    CONFIG_JSON("universalRouter.configJson", ""),
}