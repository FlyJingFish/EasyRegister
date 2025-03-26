package io.github.flyjingfish.easy_register

import io.github.flyjingfish.easy_register.config.RootStringConfig
import io.github.flyjingfish.easy_register.plugin.InitPlugin
import io.github.flyjingfish.easy_register.utils.RegisterClassUtils
import io.github.flyjingfish.easy_register.utils.printLog
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
        val fastStr = project.properties[RootStringConfig.FAST.propertyName]?: RootStringConfig.FAST.defaultValue
        RegisterClassUtils.fastDex = fastStr == "true"

        val mode = project.properties[RootStringConfig.MODE.propertyName]?.toString()?: RootStringConfig.MODE.defaultValue
        RegisterClassUtils.mode = if (mode !in RootStringConfig.MODE_SET){
            RootStringConfig.MODE.defaultValue
        }else{
            mode
        }
        InitPlugin.initFromFile(project)
        InitPlugin.rootPluginDeepApply(project)


        val isAnchorStr = project.properties[RootStringConfig.ANCHOR.propertyName]?: RootStringConfig.ANCHOR.defaultValue
        val isAnchor = isAnchorStr == "true"
        if (isAnchor){
            InitPlugin.registerApp(project,true,false)
        }else{
            InitPlugin.registerApp(project)
        }
    }
}
