package com.flyjingfish.universal_register.plugin

import com.android.build.api.instrumentation.FramesComputationMode
import com.android.build.api.instrumentation.InstrumentationScope
import com.android.build.api.variant.AndroidComponentsExtension
import com.android.build.gradle.AppPlugin
import com.flyjingfish.universal_register.config.RootStringConfig
import com.flyjingfish.universal_register.utils.RouterClassUtils
import com.flyjingfish.universal_register.visitor.MyClassVisitorFactory
import org.gradle.api.Project

object InitPlugin{
    private fun deepSetDebugMode(project: Project){
        val childProjects = project.childProjects
        if (childProjects.isEmpty()){
            return
        }
        childProjects.forEach { (_,value)->
            value.afterEvaluate {
                val notApp = !it.plugins.hasPlugin(AppPlugin::class.java)
                val noneHasAop = !it.plugins.hasPlugin("universal.router")
                if (notApp && noneHasAop && it.hasProperty("android")){
                    CompilePlugin(true).apply(it)
                }
            }
            deepSetDebugMode(value)
        }
    }
    fun rootPluginDeepApply(project: Project) {
        if (project.rootProject == project){
            deepSetDebugMode(project.rootProject)
        }
        CompilePlugin(false).apply(project)
    }

    fun initFromFile(project: Project) {
        if (project.rootProject == project){
            RouterClassUtils.clearConfigJsonFile()
            val configJsonFileStrs = project.properties[RootStringConfig.CONFIG_JSON.propertyName]?.toString()
            configJsonFileStrs?.split(",")?.forEach {
                val configJsonFile = project.file(it)
                RouterClassUtils.addConfigJsonFile(configJsonFile)
            }
            RouterClassUtils.initConfig(project)
        }
    }

    fun initFromJson(jsons:List<String>) {
        RouterClassUtils.initConfig(jsons)
    }

    fun registerApp(project: Project, registerHint :Boolean = true, registerTransform :Boolean = true) {
        val isApp = project.plugins.hasPlugin(AppPlugin::class.java)
        if (!isApp) {
            return
        }
        if (registerHint){
            val taskName = "${project.name}HintCleanTask"
            project.tasks.register(taskName, HintCleanTask::class.java)
            project.afterEvaluate {
                project.tasks.findByName("preBuild")?.finalizedBy(taskName)
            }
        }
        if (registerTransform){
            registerTransformClassesWith(project,InstrumentationScope.ALL)
        }
    }

    fun registerTransformClassesWith(project: Project,scope: InstrumentationScope) {
        val androidComponents = project.extensions.getByType(AndroidComponentsExtension::class.java)
        androidComponents.onVariants { variant ->
            variant.instrumentation.transformClassesWith(
                MyClassVisitorFactory::class.java,
                scope
            ) { params ->
                params.myConfig.set("My custom config")
            }

            // 指定字节码修改生效
            variant.instrumentation.setAsmFramesComputationMode(
                FramesComputationMode.COPY_FRAMES
            )
        }
    }


}