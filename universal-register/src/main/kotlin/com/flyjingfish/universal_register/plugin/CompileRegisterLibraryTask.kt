package com.flyjingfish.universal_register.plugin

import com.flyjingfish.universal_register.utils.JsonUtils
import com.flyjingfish.universal_register.utils.registerCompileTempDir
import com.flyjingfish.universal_register.utils.checkExist
import com.flyjingfish.universal_register.utils.getRelativePath
import com.flyjingfish.universal_register.utils.registerCompileTempJson
import com.flyjingfish.universal_register.utils.saveEntry
import com.flyjingfish.universal_register.utils.saveFile
import com.flyjingfish.universal_register.visitor.RegisterClassVisitor
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking
import org.gradle.api.Project
import org.gradle.api.logging.Logger
import org.objectweb.asm.ClassReader
import org.objectweb.asm.ClassWriter
import java.io.File
import java.io.FileInputStream
import kotlin.system.measureTimeMillis

class CompileRegisterLibraryTask(
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
        println("universal-register-library search code start")
        val scanTimeCost = measureTimeMillis {
            scanFile()
        }
        println("universal-register-library search code finish, current cost time ${scanTimeCost}ms")

    }

    private fun scanFile() {
        searchJoinPointLocation()

    }



    private fun searchJoinPointLocation() = runBlocking{
        //第一遍找配置文件
        val wovenCodeJobs = mutableListOf<Deferred<Unit>>()
        val needDeleteFiles = mutableListOf<String>()
        allDirectories.forEach { directory ->
            directory.walk().forEach { file ->
                if (file.isFile) {
                    if (file.absolutePath.endsWith(_CLASS)) {
                        val job = async(Dispatchers.IO) {
                            FileInputStream(file).use { inputs ->
                                val bytes = inputs.readAllBytes()
                                if (bytes.isNotEmpty()) {
                                    val relativePath = file.getRelativePath(directory)
                                    val tmpCompileDir = registerCompileTempDir(project,variantName)
                                    val outFile = File(tmpCompileDir+File.separatorChar+relativePath)
                                    outFile.checkExist()
                                    val cr = ClassReader(bytes)
                                    val cw = ClassWriter(cr,0)
                                    cr.accept(
                                        RegisterClassVisitor(cw),
                                        0
                                    )
                                    cw.toByteArray().saveFile(outFile)
                                    synchronized(needDeleteFiles){
                                        needDeleteFiles.add(file.absolutePath)
                                    }
                                    outFile.inputStream().use {
                                        file.saveEntry(it)
                                    }

                                }
                            }
                        }
                        wovenCodeJobs.add(job)

                    }

                }
            }

        }
        JsonUtils.exportCacheFile(File(registerCompileTempJson(project, variantName)),needDeleteFiles)

        wovenCodeJobs.awaitAll()

    }



}