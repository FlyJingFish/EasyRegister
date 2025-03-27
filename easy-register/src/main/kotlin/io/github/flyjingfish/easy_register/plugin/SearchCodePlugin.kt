package io.github.flyjingfish.easy_register.plugin

import com.android.build.gradle.AppExtension
import com.android.build.gradle.AppPlugin
import com.android.build.gradle.BaseExtension
import com.android.build.gradle.DynamicFeaturePlugin
import com.android.build.gradle.LibraryExtension
import io.github.flyjingfish.easy_register.bean.VariantBean
import io.github.flyjingfish.easy_register.utils.RegisterClassUtils
import io.github.flyjingfish.easy_register.utils.adapterOSPath
import org.codehaus.groovy.runtime.DefaultGroovyMethods
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.tasks.compile.AbstractCompile
import org.gradle.configurationcache.extensions.capitalized
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile
import org.jetbrains.kotlin.gradle.tasks.KotlinCompileTool
import java.io.File

/**
 * root 是指通过根目录build.gradle 设置的
 */
class SearchCodePlugin(private val root:Boolean): Plugin<Project> {
    companion object{
        const val ANDROID_EXTENSION_NAME = "android"
    }

    override fun apply(project: Project) {
        val isApp = project.plugins.hasPlugin(AppPlugin::class.java)


        val isDynamicLibrary = project.plugins.hasPlugin(DynamicFeaturePlugin::class.java)
        val androidObject: Any = project.extensions.findByName(ANDROID_EXTENSION_NAME) ?: return


        val kotlinCompileFilePathMap = mutableMapOf<String, KotlinCompileTool>()
        val kotlinCompileVariantMap = mutableMapOf<String, VariantBean>()
        val android = androidObject as BaseExtension
        val variants = if (isApp or isDynamicLibrary) {
            (android as AppExtension).applicationVariants
        } else {
            (android as LibraryExtension).libraryVariants
        }
        try {
            project.tasks.withType(KotlinCompile::class.java).configureEach { task ->
                kotlinCompileFilePathMap[task.name] = task
                task.doLast {
                    val variantBean = kotlinCompileVariantMap[it.name]
                    if (variantBean != null){
                        doKotlinSearchTask(project, isApp, variantBean.variantName, variantBean.buildTypeName, task)
                    }
                }
            }
        } catch (_: Exception) {
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
            javaCompile.doLast{
                val task = try {
                    kotlinCompileFilePathMap["compile${variantName.capitalized()}Kotlin"]
                } catch (_: Throwable) {
                    null
                }
                val cacheDir = try {
                    task?.destinationDirectory?.get()?.asFile
                } catch (e: Throwable) {
                    null
                }
                val kotlinPath = cacheDir ?: File(project.buildDir.path + "/tmp/kotlin-classes/".adapterOSPath() + variantName)
                doSearchTask(project, isApp, variantName, buildTypeName, javaCompile,kotlinPath)
            }
        }
    }

    private fun doKotlinSearchTask(project: Project, isApp:Boolean, variantName: String, buildTypeName: String,
                                   kotlinCompile:KotlinCompileTool){

        val debugMode = RegisterClassUtils.isDebugMode(buildTypeName,variantName)
        if (!debugMode){
            return
        }

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
        RegisterClassUtils.addJars(jarInput)
        RegisterClassUtils.addDirectories(localInput)
    }


    private fun doSearchTask(project: Project, isApp:Boolean, variantName: String, buildTypeName: String,
                             javaCompile:AbstractCompile, kotlinPath: File){

        val debugMode = RegisterClassUtils.isDebugMode(buildTypeName,variantName)
        if (!debugMode){
            return
        }

        val logger = project.logger
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
        RegisterClassUtils.addJars(jarInput)
        RegisterClassUtils.addDirectories(localInput)
    }

}