package io.github.flyjingfish.easy_register.plugin

import io.github.flyjingfish.easy_register.utils.JsonUtils
import io.github.flyjingfish.easy_register.utils.RegisterClassUtils
import io.github.flyjingfish.easy_register.utils.ScannerUtils
import io.github.flyjingfish.easy_register.utils.checkExist
import io.github.flyjingfish.easy_register.utils.getRelativePath
import io.github.flyjingfish.easy_register.utils.registerCompileTempDir
import io.github.flyjingfish.easy_register.utils.registerCompileTempWovenJson
import io.github.flyjingfish.easy_register.utils.saveEntry
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking
import org.gradle.api.Project
import org.gradle.api.logging.Logger
import java.io.File
import kotlin.system.measureTimeMillis

class CompileRegisterTask(
    private val allJars: MutableList<File>,
    private val allDirectories: MutableList<File>,
    private val output: File,
    private val project: Project,
    private val isApp:Boolean,
    private val variantName:String,
) {
    companion object{
        const val _CLASS = ".class"

    }

    private lateinit var logger: Logger
    fun taskAction() {
        println("easy-register search code start")
        val scanTimeCost = measureTimeMillis {
            scanFile()
        }
        println("easy-register search code finish, current cost time ${scanTimeCost}ms")

    }

    private fun scanFile() = runBlocking {
        searchJoinPointLocation()

        if (isApp){
            val tmpOtherDir = File(registerCompileTempDir(project,variantName))
            ScannerUtils.createInitClass(tmpOtherDir)
            val wovenCodeJobs = mutableListOf<Deferred<Unit>>()
            val needDeleteFiles = mutableListOf<String>()
            for (file in tmpOtherDir.walk()) {
                if (file.isFile) {
                    val job = async(Dispatchers.IO) {
                        val relativePath = file.getRelativePath(tmpOtherDir)
                        val target = File(output.absolutePath + File.separatorChar + relativePath)
                        target.checkExist()
                        synchronized(needDeleteFiles){
                            needDeleteFiles.add(target.absolutePath)
                        }
                        file.inputStream().use {
                            target.saveEntry(it)
                        }
                    }
                    wovenCodeJobs.add(job)
                }
            }
            wovenCodeJobs.awaitAll()
            JsonUtils.exportCacheFile(File(registerCompileTempWovenJson(project, variantName)),needDeleteFiles)

        }
    }



    private fun searchJoinPointLocation(){
        //第一遍找配置文件
        RegisterClassUtils.clear(project.name)
        allDirectories.forEach { directory ->
            directory.walk().forEach { file ->
                ScannerUtils.processFileForConfig(project,file)
            }

        }

        allJars.forEach { file ->
            RegisterClassUtils.clear(file.absolutePath)
            ScannerUtils.processJarForConfig(file)
        }
    }





}