package io.github.flyjingfish.easy_register.plugin

import com.android.build.api.artifact.ScopedArtifact
import com.android.build.api.instrumentation.FramesComputationMode
import com.android.build.api.instrumentation.InstrumentationScope
import com.android.build.api.variant.AndroidComponentsExtension
import com.android.build.api.variant.ApplicationVariant
import com.android.build.api.variant.ScopedArtifacts
import com.android.build.api.variant.Variant
import com.android.build.gradle.AppPlugin
import io.github.flyjingfish.easy_register.config.RootStringConfig
import io.github.flyjingfish.easy_register.tasks.AddClassesTask
import io.github.flyjingfish.easy_register.tasks.AllClassesTask
import io.github.flyjingfish.easy_register.tasks.HintCleanTask
import io.github.flyjingfish.easy_register.utils.RegisterClassUtils
import io.github.flyjingfish.easy_register.visitor.MyClassVisitorFactory
import io.github.flyjingfish.fast_transform.toTransformAll
import org.gradle.api.Project
import org.gradle.configurationcache.extensions.capitalized

object InitPlugin{
    private fun deepSetAllModuleSearchCode(project: Project){
        val childProjects = project.childProjects
        if (childProjects.isEmpty()){
            return
        }
        childProjects.forEach { (_,value)->
            value.afterEvaluate {
                val notApp = !it.plugins.hasPlugin(AppPlugin::class.java)
                val noneHasPlugin = !it.plugins.hasPlugin("easy.register")
                if (notApp && noneHasPlugin && it.hasProperty("android")){
                    SearchCodePlugin(true).apply(it)
                }
            }
            deepSetAllModuleSearchCode(value)
        }
    }
    fun rootPluginDeepApply(project: Project) {
        if (project.rootProject == project){
            deepSetAllModuleSearchCode(project.rootProject)
        }
        SearchCodePlugin(false).apply(project)
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
            registerTransformClassesWith(project)
        }
    }

    fun registerTransformClassesWith(project: Project) {
        try {
            val androidComponents = project.extensions.getByType(AndroidComponentsExtension::class.java)
            androidComponents.onVariants { variant ->
                val buildTypeName = variant.buildType
                val debugMode = RegisterClassUtils.isDebugMode(buildTypeName,variant.name)
                try {
                    if (debugMode){
                        transformClassesWith(project,variant)
                    }else{
                        transform(project,variant)
                    }
                } catch (_: Throwable) {
                }

            }
        } catch (_: Throwable) {
        }
    }

    fun transform(project: Project,variant: Variant) {
        val task = project.tasks.register("${variant.name}EasyRegisterAllClasses", AllClassesTask::class.java){
            it.variant = variant.name
        }
        variant.toTransformAll(task)
    }

    fun transformClassesWith(project: Project, variant: Variant) {
        variant.instrumentation.transformClassesWith(
            MyClassVisitorFactory::class.java,
            InstrumentationScope.ALL
        ) { params ->
            params.myConfig.set("My custom config")
        }

        variant.instrumentation.setAsmFramesComputationMode(
            FramesComputationMode.COPY_FRAMES
        )

        val taskProvider = project.tasks.register("${variant.name}EasyRegisterAddClasses",
            AddClassesTask::class.java){
            it.variant = variant.name
        }
        taskProvider.configure {
            it.dependsOn("compile${variant.name.capitalized()}JavaWithJavac")
            it.outputs.upToDateWhen { return@upToDateWhen false }
        }
        variant.artifacts
            .forScope(ScopedArtifacts.Scope.PROJECT)
            .use(taskProvider)
            .toAppend(
                ScopedArtifact.CLASSES,
                AddClassesTask::output
            )
    }


}