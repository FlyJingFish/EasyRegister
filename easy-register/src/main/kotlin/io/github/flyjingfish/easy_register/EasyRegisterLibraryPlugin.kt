package io.github.flyjingfish.easy_register

import com.android.build.gradle.AppExtension
import com.android.build.gradle.AppPlugin
import com.android.build.gradle.BaseExtension
import com.android.build.gradle.DynamicFeaturePlugin
import com.android.build.gradle.LibraryExtension
import io.github.flyjingfish.easy_register.plugin.SearchCodePlugin
import io.github.flyjingfish.easy_register.tasks.AnchorRegisterLibraryTask
import io.github.flyjingfish.easy_register.utils.JsonUtils
import io.github.flyjingfish.easy_register.utils.adapterOSPath
import org.codehaus.groovy.runtime.DefaultGroovyMethods
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.tasks.compile.AbstractCompile
import org.gradle.configurationcache.extensions.capitalized
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile
import org.jetbrains.kotlin.gradle.tasks.KotlinCompileTool
import java.io.File

class EasyRegisterLibraryPlugin : Plugin<Project> {

    override fun apply(project: Project) {
        val isApp = project.plugins.hasPlugin(AppPlugin::class.java)


        val isDynamicLibrary = project.plugins.hasPlugin(DynamicFeaturePlugin::class.java)
        val androidObject: Any = project.extensions.findByName(SearchCodePlugin.ANDROID_EXTENSION_NAME) ?: return


        val kotlinCompileFilePathMap = mutableMapOf<String, KotlinCompileTool>()
        val android = androidObject as BaseExtension
        val variants = if (isApp or isDynamicLibrary) {
            (android as AppExtension).applicationVariants
        } else {
            (android as LibraryExtension).libraryVariants
        }
        variants.all { variant ->
            try {
                project.tasks.withType(KotlinCompile::class.java).configureEach { task ->
                    kotlinCompileFilePathMap[task.name] = task
                }
            } catch (_: Exception) {
            }
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
            javaCompile.doFirst{
                JsonUtils.deleteNeedDelFile(project, variantName)
            }
            javaCompile.doLast{

                val task = kotlinCompileFilePathMap["compile${variantName.capitalized()}Kotlin"]
                val cacheDir = try {
                    task?.destinationDirectory?.get()?.asFile
                } catch (e: Exception) {
                    null
                }
                val kotlinPath = cacheDir ?: File(project.buildDir.path + "/tmp/kotlin-classes/".adapterOSPath() + variantName)
                doAopTask(project, isApp, variantName, buildTypeName, javaCompile, kotlinPath)
            }
        }
    }

    private fun doAopTask(project: Project, isApp:Boolean, variantName: String, buildTypeName: String,
                          javaCompile:AbstractCompile, kotlinPath: File){
        val localInput = mutableListOf<File>()
        val javaPath = File(javaCompile.destinationDirectory.asFile.orNull.toString())
        if (javaPath.exists()){
            localInput.add(javaPath)
        }

        if (kotlinPath.exists()){
            localInput.add(kotlinPath)
        }
        val jarInput = mutableListOf<File>()
        val bootJarPath = mutableSetOf<String>()
        for (file in localInput) {
            bootJarPath.add(file.absolutePath)
        }
        for (file in javaCompile.classpath) {
            if (file.absolutePath !in bootJarPath && file.exists()){
                if (file.isDirectory){
                    localInput.add(file)
                }else{
                    jarInput.add(file)
                }
            }
        }
        if (localInput.isNotEmpty()){
            val output = File(javaCompile.destinationDirectory.asFile.orNull.toString())
            val task = AnchorRegisterLibraryTask(jarInput,localInput,output,project,isApp,
                variantName
            )
            task.taskAction()
        }
    }
}
