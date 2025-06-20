package io.github.flyjingfish.easy_register

import com.android.build.gradle.AppExtension
import com.android.build.gradle.AppPlugin
import com.android.build.gradle.BaseExtension
import com.android.build.gradle.DynamicFeaturePlugin
import com.android.build.gradle.LibraryExtension
import io.github.flyjingfish.easy_register.bean.VariantBean
import io.github.flyjingfish.easy_register.tasks.AnchorRegisterLibraryTask
import io.github.flyjingfish.easy_register.utils.JsonUtils
import io.github.flyjingfish.easy_register.utils.RegisterClassUtils
import io.github.flyjingfish.easy_register.utils.RuntimeProject
import io.github.flyjingfish.easy_register.utils.adapterOSPath
import org.codehaus.groovy.runtime.DefaultGroovyMethods
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.execution.TaskExecutionGraph
import org.gradle.api.execution.TaskExecutionGraphListener
import org.gradle.api.tasks.compile.AbstractCompile
import org.gradle.configurationcache.extensions.capitalized
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile
import org.jetbrains.kotlin.gradle.tasks.KotlinCompileTool
import java.io.File
import java.util.concurrent.ConcurrentHashMap

class EasyRegisterLibraryPlugin : Plugin<Project> {
    companion object{
        const val ANDROID_EXTENSION_NAME = "android"
        private val kotlinCompileFilePathMap = ConcurrentHashMap<String, File>()
    }
    override fun apply(project: Project) {
        val isApp = project.plugins.hasPlugin(AppPlugin::class.java)
        val runtimeProject = RuntimeProject.get(project)
        project.rootProject.gradle.taskGraph.addTaskExecutionGraphListener(object :
            TaskExecutionGraphListener {
            override fun graphPopulated(it: TaskExecutionGraph) {
                try {
                    for (task in it.allTasks) {
                        if (task is KotlinCompileTool){
                            val destinationDirectory = task.destinationDirectory.get().asFile
                            val key = task.project.buildDir.absolutePath+"@"+task.name
                            val oldDirectory = kotlinCompileFilePathMap[key]
                            if (oldDirectory == null || oldDirectory.absolutePath != destinationDirectory.absolutePath){
                                kotlinCompileFilePathMap[key] = destinationDirectory
                            }
                        }
                    }
                } catch (_: Throwable) {
                }
                project.rootProject.gradle.taskGraph.removeTaskExecutionGraphListener(this)
            }

        })


        val isDynamicLibrary = project.plugins.hasPlugin(DynamicFeaturePlugin::class.java)
        val androidObject: Any = project.extensions.findByName(ANDROID_EXTENSION_NAME) ?: return


        val android = androidObject as BaseExtension
        val variants = if (isApp or isDynamicLibrary) {
            (android as AppExtension).applicationVariants
        } else {
            (android as LibraryExtension).libraryVariants
        }
        val kotlinCompileVariantMap = mutableMapOf<String, VariantBean>()
        try {
            project.tasks.withType(KotlinCompile::class.java).configureEach { task ->
                task.doLast {
                    val variantBean = kotlinCompileVariantMap[it.name]
                    if (variantBean != null){
                        doKotlinSearchTask(runtimeProject, isApp, variantBean.variantName, variantBean.buildTypeName, task)
                    }
                }
            }
        } catch (_: Throwable) {
        }
        variants.all { variant ->
            val javaCompile: AbstractCompile =
                if (DefaultGroovyMethods.hasProperty(variant, "javaCompileProvider") != null) {
                    //gradle 4.10.1 +
                    variant.javaCompileProvider.get()
                } else if (DefaultGroovyMethods.hasProperty(variant, "javaCompiler") != null) {
                    variant.javaCompiler as AbstractCompile
                } else {
                    variant.javaCompile as AbstractCompile
                }
            val variantName = variant.name
            val buildTypeName = variant.buildType.name


            kotlinCompileVariantMap["compile${variantName.capitalized()}Kotlin"] = VariantBean(variantName,buildTypeName)

            javaCompile.doFirst{
                JsonUtils.deleteNeedDelFile(runtimeProject, variantName)
            }
            val kotlinBuildPath = File(project.buildDir.path + "/tmp/kotlin-classes/".adapterOSPath() + variantName)
            javaCompile.doLast{
                doAopTask(runtimeProject, isApp, variantName, buildTypeName, javaCompile, kotlinBuildPath)
            }
        }
    }


    private fun doKotlinSearchTask(project: RuntimeProject, isApp:Boolean, variantName: String, buildTypeName: String,
                                   kotlinCompile:KotlinCompileTool){

        val localInput = mutableSetOf<String>()
        val javaPath = kotlinCompile.destinationDirectory.get().asFile
        if (javaPath.exists()){
            localInput.add(javaPath.absolutePath)
        }

        val jarInput = mutableSetOf<String>()
        val bootJarPath = mutableSetOf<String>()
        for (file in localInput) {
            bootJarPath.add(file)
        }
        for (file in kotlinCompile.libraries) {
            if (file.absolutePath !in bootJarPath && file.exists()){
                if (file.isDirectory){
                    localInput.add(file.absolutePath)
                }else{
                    jarInput.add(file.absolutePath)
                }
            }
        }
        if (localInput.isNotEmpty()){
            val output = File(kotlinCompile.destinationDirectory.asFile.orNull.toString())
            val task = AnchorRegisterLibraryTask(localInput.map(::File),output,project,
                variantName
            )
            task.taskAction()
        }
    }

    private fun doAopTask(project: RuntimeProject, isApp:Boolean, variantName: String, buildTypeName: String,
                          javaCompile:AbstractCompile, kotlinDefaultPath: File){

        val cacheDir = kotlinCompileFilePathMap[project.buildDir.absolutePath+"@"+"compile${variantName.capitalized()}Kotlin"]
        val kotlinPath = cacheDir
            ?:kotlinDefaultPath

        val localInput = mutableSetOf<String>()
        val javaPath = File(javaCompile.destinationDirectory.asFile.orNull.toString())
        if (javaPath.exists()){
            localInput.add(javaPath.absolutePath)
        }

        if (kotlinPath.exists()){
            localInput.add(kotlinPath.absolutePath)
        }
        val jarInput = mutableSetOf<String>()
        val bootJarPath = mutableSetOf<String>()
        for (file in localInput) {
            bootJarPath.add(file)
        }
        for (file in javaCompile.classpath) {
            if (file.absolutePath !in bootJarPath && file.exists()){
                if (file.isDirectory){
                    localInput.add(file.absolutePath)
                }else{
                    jarInput.add(file.absolutePath)
                }
            }
        }
        if (localInput.isNotEmpty()){
            val output = File(javaCompile.destinationDirectory.asFile.orNull.toString())
            val task = AnchorRegisterLibraryTask(localInput.map(::File),output,project,
                variantName
            )
            task.taskAction()
        }
    }
}
