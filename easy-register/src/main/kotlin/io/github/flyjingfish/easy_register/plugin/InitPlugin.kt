package io.github.flyjingfish.easy_register.plugin

import com.android.build.api.artifact.ScopedArtifact
import com.android.build.api.instrumentation.FramesComputationMode
import com.android.build.api.instrumentation.InstrumentationScope
import com.android.build.api.variant.AndroidComponentsExtension
import com.android.build.api.variant.ScopedArtifacts
import com.android.build.api.variant.Variant
import com.android.build.gradle.AppPlugin
import io.github.flyjingfish.easy_register.config.RootStringConfig
import io.github.flyjingfish.easy_register.tasks.AddClassesTask
import io.github.flyjingfish.easy_register.tasks.AllClassesTask
import io.github.flyjingfish.easy_register.utils.RegisterClassUtils
import io.github.flyjingfish.easy_register.utils.RuntimeProject
import io.github.flyjingfish.easy_register.utils.collectJavaPaths
import io.github.flyjingfish.easy_register.utils.collectKotlinPaths
import io.github.flyjingfish.easy_register.visitor.MyClassVisitorFactory
import io.github.flyjingfish.fast_transform.toTransformAll
import org.gradle.api.Project
import org.gradle.api.Task
import org.gradle.api.tasks.compile.AbstractCompile
import org.gradle.configurationcache.extensions.capitalized
import org.jetbrains.kotlin.gradle.tasks.KotlinCompileTool
import java.io.File
import java.util.concurrent.ConcurrentHashMap

object InitPlugin{
    fun initFromFile(project: Project) {
        val rootProject = if (project.rootProject == project){
            project
        }else{
            project.rootProject
        }
        RegisterClassUtils.clearConfigJsonFile()
        val configJsonFileStrs = rootProject.properties[RootStringConfig.CONFIG_JSON.propertyName]?.toString()
        configJsonFileStrs?.split(",")?.forEach {
            val configJsonFile = rootProject.file(it)
            RegisterClassUtils.addConfigJsonFile(configJsonFile)
        }
        RegisterClassUtils.initConfig(rootProject)
    }

    fun initFromJson(jsons:List<String>) {
        RegisterClassUtils.initConfig(jsons)
    }

    fun registerApp(project: Project,registerTransform :Boolean = true) {
        val isApp = project.plugins.hasPlugin(AppPlugin::class.java)
        if (!isApp) {
            return
        }
        if (registerTransform){
            registerTransformClassesWith(project)
        }
    }

    fun registerTransformClassesWith(project: Project) {
        try {
            val compileTasks = mutableListOf<Task>()
            collectAllCompileTasks(project, compileTasks)
            val androidComponents = project.extensions.getByType(AndroidComponentsExtension::class.java)
            androidComponents.onVariants { variant ->
                val buildTypeName = variant.buildType
                val debugMode = RegisterClassUtils.isDebugMode(buildTypeName,variant.name)
                try {
                    if (debugMode){
                        transformClassesWith(project,variant,compileTasks)
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
        val runtimeProject = RuntimeProject.get(project)
        val task = project.tasks.register("${variant.name}EasyRegisterAllClasses", AllClassesTask::class.java){
            it.variant = variant.name
            it.runtimeProject = runtimeProject
        }
        variant.toTransformAll(task,RegisterClassUtils.fastDex)
    }

    fun transformClassesWith(project: Project, variant: Variant,compileTasks:MutableList<Task>) {
        variant.instrumentation.transformClassesWith(
            MyClassVisitorFactory::class.java,
            InstrumentationScope.ALL
        ) { params ->
            params.myConfig.set("My custom config")
        }

        variant.instrumentation.setAsmFramesComputationMode(
            FramesComputationMode.COPY_FRAMES
        )
        val runtimeProject = RuntimeProject.get(project)
        val taskProvider = project.tasks.register("${variant.name}EasyRegisterAddClasses",
            AddClassesTask::class.java){
            it.variant = variant.name
            it.runtimeProject = runtimeProject
        }

        variant.artifacts
            .forScope(ScopedArtifacts.Scope.PROJECT)
            .use(taskProvider)
            .toAppend(
                ScopedArtifact.CLASSES,
                AddClassesTask::output
            )

        taskProvider.configure { task ->
            task.output.set(project.layout.buildDirectory.file("intermediates/classes/${taskProvider.name}/").get().asFile)
            task.dependsOn("compile${variant.name.capitalized()}JavaWithJavac")
            task.outputs.upToDateWhen { return@upToDateWhen false }
            task.doFirst{
                val allJars = ConcurrentHashMap.newKeySet<String>()
                val allDirectories = ConcurrentHashMap.newKeySet<String>()
                collectAllPaths(compileTasks, allJars, allDirectories)
                task.setFrom(allDirectories.map(::File),allJars.map(::File))
            }
        }
    }
    fun collectAllPaths(compileTasks:MutableList<Task>,allJars:MutableSet<String>,allDirectories:MutableSet<String>) {
        for (compileTask in compileTasks) {
            if (compileTask is AbstractCompile){
                compileTask.collectJavaPaths(allJars, allDirectories)
            }else if (compileTask is KotlinCompileTool){
                compileTask.collectKotlinPaths(allJars, allDirectories)
            }
        }
    }
    fun collectAllCompileTasks(project: Project, compileTasks:MutableList<Task>) {
        project.rootProject.gradle.taskGraph.addTaskExecutionGraphListener {
            for (task in it.allTasks) {
                if (task is AbstractCompile || task is KotlinCompileTool){
                    compileTasks.add(task)
                }
            }
        }
    }


}