package io.github.flyjingfish.easy_register.plugin

import com.android.build.api.artifact.ScopedArtifact
import com.android.build.api.instrumentation.FramesComputationMode
import com.android.build.api.instrumentation.InstrumentationScope
import com.android.build.api.variant.AndroidComponentsExtension
import com.android.build.api.variant.ScopedArtifacts
import com.android.build.gradle.AppPlugin
import io.github.flyjingfish.easy_register.config.RootStringConfig
import io.github.flyjingfish.easy_register.utils.RegisterClassUtils
import io.github.flyjingfish.easy_register.visitor.MyClassVisitorFactory
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
            RegisterClassUtils.clearConfigJsonFile()
            val configJsonFileStrs = project.properties[RootStringConfig.CONFIG_JSON.propertyName]?.toString()
            configJsonFileStrs?.split(",")?.forEach {
                val configJsonFile = project.file(it)
                RegisterClassUtils.addConfigJsonFile(configJsonFile)
            }
            RegisterClassUtils.initConfig(project)
        }
    }

    fun initFromJson(jsons:List<String>) {
        RegisterClassUtils.initConfig(jsons)
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
        try {
            val androidComponents = project.extensions.getByType(AndroidComponentsExtension::class.java)
            androidComponents.onVariants { variant ->
                val buildTypeName = variant.buildType
                val debugMode = RegisterClassUtils.isDebugMode(buildTypeName,variant.name)
                try {
                    if (debugMode){
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
                    }else{
                        val task = project.tasks.register("${variant.name}AssembleEasyRegisterTask", AssembleRegisterTask::class.java){
                            it.variant = variant.name
                        }
                        variant.artifacts
                            .forScope(ScopedArtifacts.Scope.ALL)
                            .use(task)
                            .toTransform(
                                ScopedArtifact.CLASSES,
                                AssembleRegisterTask::allJars,
                                AssembleRegisterTask::allDirectories,
                                AssembleRegisterTask::output
                            )
                    }
                } catch (_: Throwable) {
                }

            }
        } catch (_: Throwable) {
        }
    }


}