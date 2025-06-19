package io.github.flyjingfish.easy_register.tasks

import io.github.flyjingfish.easy_register.utils.RegisterClassUtils
import io.github.flyjingfish.easy_register.utils.AsmUtils
import io.github.flyjingfish.easy_register.utils.RuntimeProject
import io.github.flyjingfish.easy_register.utils.computeMD5
import io.github.flyjingfish.easy_register.utils.getFileClassname
import io.github.flyjingfish.easy_register.utils.getRelativePath
import io.github.flyjingfish.easy_register.utils.isJarSignatureRelatedFiles
import io.github.flyjingfish.easy_register.utils.openJar
import io.github.flyjingfish.easy_register.utils.printLog
import io.github.flyjingfish.easy_register.utils.registerCompileTempDir
import io.github.flyjingfish.easy_register.utils.registerTransformIgnoreJarDir
import io.github.flyjingfish.easy_register.utils.slashToDot
import io.github.flyjingfish.easy_register.utils.toClassPath
import io.github.flyjingfish.easy_register.visitor.RegisterClassVisitor
import io.github.flyjingfish.fast_transform.tasks.DefaultTransformTask
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.gradle.api.tasks.Input
import org.objectweb.asm.ClassReader
import org.objectweb.asm.ClassWriter
import java.io.File
import java.io.FileInputStream
import java.io.InputStream
import java.util.jar.JarFile
import kotlin.system.measureTimeMillis


abstract class AllClassesTask : DefaultTransformTask() {
    @get:Input
    abstract var runtimeProject : RuntimeProject
    @get:Input
    abstract var variant :String


    private val ignoreJar = mutableSetOf<String>()
    private val ignoreJarClassPaths = mutableListOf<File>()

    private val allJarFiles = mutableListOf<File>()

    private val allDirectoryFiles = mutableListOf<File>()
    override fun startTask() {
        printLog("easy-register:release search code start")
        val scanTimeCost = measureTimeMillis {
            scanFile()
        }
        printLog("easy-register:release search code finish, current cost time ${scanTimeCost}ms")

    }

    override fun endTask() {
    }

    private fun scanFile() {
        allJarFiles.addAll(allJars())
        allDirectoryFiles.addAll(allDirectories())
        loadJoinPointConfig()
        wovenIntoCode()
    }

    private fun loadJoinPointConfig() = runBlocking{
        val isClassesJar = singleClassesJar()
        ignoreJar.clear()
        ignoreJarClassPaths.clear()
        allJarFiles.forEach { file ->
            if (isClassesJar){
                ignoreJar.add(file.absolutePath)
                return@forEach
            }
            val jarFile = JarFile(file)
            val enumeration = jarFile.entries()
            while (enumeration.hasMoreElements()) {
                val jarEntry = enumeration.nextElement()
                try {
                    val entryName = jarEntry.name
                    if (entryName.isJarSignatureRelatedFiles()){
                        ignoreJar.add(file.absolutePath)
                        break
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
            jarFile.close()
        }
        if (ignoreJar.isNotEmpty()){
            val temporaryDir = File(registerTransformIgnoreJarDir(runtimeProject,variant))
            for (path in ignoreJar) {
                val destDir = "${temporaryDir.absolutePath}${File.separatorChar}${File(path).name.computeMD5()}"
                val destFile = File(destDir)
                destFile.deleteRecursively()
                openJar(path,destDir)
                ignoreJarClassPaths.add(destFile)
            }
        }
        val searchJobs = mutableListOf<Deferred<Unit>>()
        fun processFile(directory:File,file : File){
            AsmUtils.processFileForConfig(directory,file,this@runBlocking,searchJobs)
        }
        for (directory in ignoreJarClassPaths) {
            RegisterClassUtils.clear(directory.absolutePath)
            directory.walk().forEach { file ->
                processFile(directory,file)
            }
        }

        //第一遍找配置文件
        allDirectoryFiles.forEach { directory ->
            RegisterClassUtils.clear(directory.absolutePath)
            directory.walk().forEach { file ->
                processFile(directory,file)
            }
        }
        val jarFiles = mutableListOf<JarFile>()
        allJarFiles.forEach { file ->
            RegisterClassUtils.clear(file.absolutePath)
            if (file.absolutePath in ignoreJar){
                return@forEach
            }
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
    }

    private fun wovenIntoCode() = runBlocking{

        fun processFile(file : File,directory:File){
            if (file.isFile) {
                val relativePath = file.getRelativePath(directory)
                val jarEntryName: String = relativePath.toClassPath()

                if (isInstrumentable(jarEntryName)){
                    FileInputStream(file).use { inputs ->
                        saveClasses(inputs,jarEntryName,directory)
                    }

                }else{
                    file.inputStream().use {
                        directory.saveJarEntry(jarEntryName,it)
                    }
                }
            }

        }
        val wovenCodeJobs = mutableListOf<Deferred<Unit>>()
        for (directory in ignoreJarClassPaths) {
            directory.walk().sortedBy {
                it.name.length
            }.forEach { file ->
                val job = async(Dispatchers.IO) {
                    processFile(file, directory)
                }
                wovenCodeJobs.add(job)
            }

        }
        allDirectoryFiles.forEach { directory ->
            directory.walk().forEach { file ->
                val job = async(Dispatchers.IO) {
                    processFile(file,directory)
                }
                wovenCodeJobs.add(job)
            }
        }

        val jarFiles = mutableListOf<JarFile>()

        allJarFiles.forEach { file ->
            if (file.absolutePath in ignoreJar){
                return@forEach
            }
            val jarFile = JarFile(file)
            val enumeration = jarFile.entries()
            while (enumeration.hasMoreElements()) {
                val jarEntry = enumeration.nextElement()
                val entryName = jarEntry.name
                if (jarEntry.isDirectory || entryName.isEmpty() || entryName.startsWith("META-INF/") || "module-info.class" == entryName || !entryName.endsWith(".class")) {
                    continue
                }
                val job = async(Dispatchers.IO) {
                    try {
                        if (isInstrumentable(entryName)){
                            jarFile.getInputStream(jarEntry).use { inputs ->
                                saveClasses(inputs,entryName,file)
                            }
                        }else{
                            jarFile.getInputStream(jarEntry).use {
                                file.saveJarEntry(entryName,it)
                            }
                        }
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }
                wovenCodeJobs.add(job)
            }
            jarFiles.add(jarFile)
        }

        val tmpOtherDir = File(registerCompileTempDir(runtimeProject,variant))
        AsmUtils.createInitClass(tmpOtherDir)
        for (file in tmpOtherDir.walk()) {
            if (file.isFile) {
                val className = file.getFileClassname(tmpOtherDir)
                val job = async(Dispatchers.IO) {
                    file.inputStream().use {
                        file.saveJarEntry(className,it)
                    }
                }
                wovenCodeJobs.add(job)

            }
        }
        wovenCodeJobs.awaitAll()
        for (jarFile in jarFiles) {
            withContext(Dispatchers.IO) {
                jarFile.close()
            }
        }
    }

    private fun isInstrumentable(className: String): Boolean {
        // 指定哪些类可以被修改，例如过滤某些包名
        return RegisterClassUtils.isWovenClass(slashToDot(className).replace(Regex("\\.class$"), ""))  || RegisterClassUtils.isCallClass(slashToDot(className).replace(Regex("\\.class$"), ""))
    }

    fun saveClasses(inputs: InputStream,jarEntryName:String,jarFile: File){
        val cr = ClassReader(inputs)
        val cw = ClassWriter(cr,0)
        cr.accept(
            RegisterClassVisitor(cw),
            ClassReader.EXPAND_FRAMES
        )
        cw.toByteArray().inputStream().use {
            jarFile.saveJarEntry(jarEntryName,it)
        }
    }


}