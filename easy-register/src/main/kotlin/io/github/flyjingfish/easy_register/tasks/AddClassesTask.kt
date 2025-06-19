package io.github.flyjingfish.easy_register.tasks

import io.github.flyjingfish.easy_register.utils.AsmUtils
import io.github.flyjingfish.easy_register.utils.RegisterClassUtils
import io.github.flyjingfish.easy_register.utils.RuntimeProject
import io.github.flyjingfish.easy_register.utils.checkExist
import io.github.flyjingfish.easy_register.utils.getRelativePath
import io.github.flyjingfish.easy_register.utils.printLog
import io.github.flyjingfish.easy_register.utils.registerCompileTempDir
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.gradle.api.DefaultTask
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.TaskAction
import java.io.File
import java.util.jar.JarFile
import kotlin.system.measureTimeMillis


abstract class AddClassesTask : DefaultTask() {
    @get:Input
    abstract var runtimeProject : RuntimeProject
    @get:Input
    abstract var variant :String

    private lateinit var allDirectories :List<File>

    private lateinit var allJars :List<File>


    @get:OutputDirectory
    abstract val output: DirectoryProperty

    fun setFrom(allDirectories :List<File>,allJars :List<File>){
        this.allDirectories = allDirectories
        this.allJars = allJars
    }

    @TaskAction
    fun taskAction() {
        printLog("easy-register:debug search code start")
        val scanTimeCost = measureTimeMillis {
            addClass()
        }
        printLog("easy-register:debug search code finish, current cost time ${scanTimeCost}ms")
    }

    private fun addClass() = runBlocking {
        val searchJobs = mutableListOf<Deferred<Unit>>()
        //第一遍找配置文件
        allDirectories.forEach { directory ->
            RegisterClassUtils.clear(directory.absolutePath)
            directory.walk().forEach { file ->
                AsmUtils.processFileForConfig(directory,file,this@runBlocking,searchJobs)
            }
        }
        val jarFiles = mutableListOf<JarFile>()
        allJars.forEach { file ->
            RegisterClassUtils.clear(file.absolutePath)
            jarFiles.add(AsmUtils.processJarForConfig(file,this@runBlocking,searchJobs))
        }

        if (searchJobs.isNotEmpty()){
            searchJobs.awaitAll()
        }
        for (jarFile in jarFiles) {
            withContext(Dispatchers.IO) {
                jarFile.close()
            }
        }

        val tmpOtherDir = File(registerCompileTempDir(runtimeProject,variant))
        if (tmpOtherDir.exists()){
            tmpOtherDir.deleteRecursively()
        }
        AsmUtils.createInitClass(tmpOtherDir)
        val wovenCodeJobs = mutableListOf<Deferred<Any>>()
        val outputDir = File(output.get().asFile.absolutePath)
        val needDeleteFiles = outputDir.walk().filter { it.isFile }.map { it.absolutePath }.toMutableList()
        for (file in tmpOtherDir.walk()) {
            if (file.isFile) {
                val job = async(Dispatchers.IO) {
                    val relativePath = file.getRelativePath(tmpOtherDir)
                    val target = File(outputDir.absolutePath + File.separatorChar + relativePath)
                    target.checkExist()
                    synchronized(needDeleteFiles){
                        needDeleteFiles.remove(target.absolutePath)
                    }
                    file.copyTo(target,true)
                }
                wovenCodeJobs.add(job)
            }
        }
        wovenCodeJobs.awaitAll()
        withContext(Dispatchers.IO){
            for (needDeleteFile in needDeleteFiles) {
                val file = File(needDeleteFile)
                if (file.exists()){
                    file.delete()
                }
            }
        }
//        RegisterClassUtils.clearInputs()
    }

}