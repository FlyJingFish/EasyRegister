package io.github.flyjingfish.easy_register

import io.github.flyjingfish.easy_register.config.RootStringConfig
import io.github.flyjingfish.easy_register.plugin.InitPlugin
import io.github.flyjingfish.easy_register.utils.RegisterClassUtils
import org.gradle.api.Plugin
import org.gradle.api.Project

class EasyRegisterPlugin : Plugin<Project> {

    override fun apply(project: Project) {
        val enableStr = project.properties[RootStringConfig.ENABLE.propertyName]?: RootStringConfig.ENABLE.defaultValue
        val enable = enableStr == "true"
        RegisterClassUtils.enable = enable
        if (!enable){
            return
        }

        val mode = project.properties[RootStringConfig.MODE.propertyName]?.toString()?: RootStringConfig.MODE.defaultValue
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
