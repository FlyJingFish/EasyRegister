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
import io.github.flyjingfish.easy_register.tasks.HintCleanTask
import io.github.flyjingfish.easy_register.utils.RegisterClassUtils
import io.github.flyjingfish.easy_register.visitor.MyClassVisitorFactory
import io.github.flyjingfish.fast_transform.toTransformAll
import org.gradle.api.Project
import org.gradle.api.file.FileCollection
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
            val allJars = ConcurrentHashMap.newKeySet<String>()
            val allDirectories = ConcurrentHashMap.newKeySet<String>()
            collectAllPaths(project, allJars, allDirectories)
            val androidComponents = project.extensions.getByType(AndroidComponentsExtension::class.java)
            androidComponents.onVariants { variant ->
                val buildTypeName = variant.buildType
                val debugMode = RegisterClassUtils.isDebugMode(buildTypeName,variant.name)
                try {
                    if (debugMode){
                        transformClassesWith(project,variant,allJars, allDirectories)
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
        variant.toTransformAll(task,RegisterClassUtils.fastDex)
    }

    fun transformClassesWith(project: Project, variant: Variant,allJars :Set<String>,allDirectories:Set<String>) {
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
                task.setFrom(allDirectories.map(::File),allJars.map(::File))
            }
        }
    }

    fun collectAllPaths(project: Project,allJars:MutableSet<String>,allDirectories :MutableSet<String>) {
        project.rootProject.gradle.taskGraph.addTaskExecutionGraphListener {
            for (task in it.allTasks) {
                if (task is AbstractCompile){
                    task.doLast {
                        collectJavaPaths(task,allJars, allDirectories)
                    }
                }else if (task is KotlinCompileTool){
                    task.doLast {
                        collectKotlinPaths(task,allJars, allDirectories)
                    }
                }
            }
        }
    }

    private fun collectKotlinPaths(kotlinCompile: KotlinCompileTool, allJars :MutableSet<String>, allDirectories:MutableSet<String>){
        collectPath(kotlinCompile.destinationDirectory.get().asFile, kotlinCompile.libraries,allJars, allDirectories)
    }


    private fun collectJavaPaths(javaCompile: AbstractCompile, allJars :MutableSet<String>, allDirectories:MutableSet<String>){
        collectPath(File(javaCompile.destinationDirectory.asFile.orNull.toString()),javaCompile.classpath,allJars, allDirectories)
    }

    private fun collectPath(outputDir: File, classpath :FileCollection, allJars :MutableSet<String>, allDirectories:MutableSet<String>){
        val localInput = mutableSetOf<String>()
        if (outputDir.exists()){
            localInput.add(outputDir.absolutePath)
        }

        val jarInput = mutableSetOf<String>()
        val bootJarPath = mutableSetOf<String>()
        for (file in localInput) {
            bootJarPath.add(file)
        }
        for (file in classpath) {
            if (file.absolutePath !in bootJarPath && file.exists()){
                if (file.isDirectory){
                    localInput.add(file.absolutePath)
                }else{
                    jarInput.add(file.absolutePath)
                }
            }
        }
        allJars.addAll(jarInput)
        allDirectories.addAll(localInput)
    }

}