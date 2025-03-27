package io.github.flyjingfish.easy_register

import com.android.build.gradle.AppExtension
import com.android.build.gradle.AppPlugin
import com.android.build.gradle.BaseExtension
import com.android.build.gradle.DynamicFeaturePlugin
import com.android.build.gradle.LibraryExtension
import io.github.flyjingfish.easy_register.bean.VariantBean
import io.github.flyjingfish.easy_register.plugin.SearchCodePlugin
import io.github.flyjingfish.easy_register.tasks.AnchorRegisterLibraryTask
import io.github.flyjingfish.easy_register.utils.JsonUtils
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
        val kotlinCompileVariantMap = mutableMapOf<String, VariantBean>()
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
        } catch (_: Throwable) {
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


            kotlinCompileVariantMap["compile${variantName.capitalized()}Kotlin"] = VariantBean(variantName,buildTypeName)

            javaCompile.doFirst{
                JsonUtils.deleteNeedDelFile(project, variantName)
            }
            javaCompile.doLast{

                val task = kotlinCompileFilePathMap["compile${variantName.capitalized()}Kotlin"]
                val cacheDir = try {
                    task?.destinationDirectory?.get()?.asFile
                } catch (e: Throwable) {
                    null
                }
                val kotlinPath = cacheDir ?: File(project.buildDir.path + "/tmp/kotlin-classes/".adapterOSPath() + variantName)
                doAopTask(project, isApp, variantName, buildTypeName, javaCompile, kotlinPath)
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
        if (localInput.isNotEmpty()){
            val output = File(kotlinCompile.destinationDirectory.asFile.orNull.toString())
            val task = AnchorRegisterLibraryTask(localInput.map(::File),output,project,
                variantName
            )
            task.taskAction()
        }
    }

    private fun doAopTask(project: Project, isApp:Boolean, variantName: String, buildTypeName: String,
                          javaCompile:AbstractCompile, kotlinPath: File){
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
