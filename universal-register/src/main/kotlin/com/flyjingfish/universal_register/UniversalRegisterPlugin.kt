package com.flyjingfish.universal_register

import com.flyjingfish.universal_register.config.RootBooleanConfig
import com.flyjingfish.universal_register.config.RootStringConfig
import com.flyjingfish.universal_register.plugin.InitPlugin
import com.flyjingfish.universal_register.utils.RouterClassUtils
import org.gradle.api.Plugin
import org.gradle.api.Project

class UniversalRegisterPlugin : Plugin<Project> {

    override fun apply(project: Project) {
        val enableStr = project.properties[RootBooleanConfig.ENABLE.propertyName]?:RootBooleanConfig.APP_INCREMENTAL.defaultValue
        val enable = enableStr == "true"
        RouterClassUtils.enable = enable
        if (!enable){
            return
        }
        InitPlugin.rootPluginDeepApply(project)
        InitPlugin.initFromFile(project)
        InitPlugin.registerApp(project)
    }
}
