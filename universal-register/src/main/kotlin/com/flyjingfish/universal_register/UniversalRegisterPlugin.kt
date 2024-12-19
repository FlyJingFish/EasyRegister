package com.flyjingfish.universal_register

import com.flyjingfish.universal_register.config.RootStringConfig
import com.flyjingfish.universal_register.plugin.InitPlugin
import com.flyjingfish.universal_register.utils.RegisterClassUtils
import org.gradle.api.Plugin
import org.gradle.api.Project

class UniversalRegisterPlugin : Plugin<Project> {

    override fun apply(project: Project) {
        val enableStr = project.properties[RootStringConfig.ENABLE.propertyName]?:RootStringConfig.APP_INCREMENTAL.defaultValue
        val enable = enableStr == "true"
        RegisterClassUtils.enable = enable
        if (!enable){
            return
        }

        val mode = project.properties[RootStringConfig.MODE.propertyName]?.toString()?:RootStringConfig.MODE.defaultValue
        RegisterClassUtils.mode = if (mode !in RootStringConfig.MODE_SET){
            RootStringConfig.MODE.defaultValue
        }else{
            mode
        }
        InitPlugin.rootPluginDeepApply(project)
        InitPlugin.initFromFile(project)
        InitPlugin.registerApp(project)
    }
}
