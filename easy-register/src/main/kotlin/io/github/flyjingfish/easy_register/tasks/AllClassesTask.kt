package io.github.flyjingfish.easy_register.tasks

import io.github.flyjingfish.easy_register.utils.RegisterClassUtils
import io.github.flyjingfish.easy_register.utils.AsmUtils
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
import org.gradle.api.DefaultTask
import org.gradle.api.file.Directory
import org.gradle.api.file.RegularFile
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.ListProperty
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.TaskAction
import org.objectweb.asm.ClassReader
import org.objectweb.asm.ClassWriter
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.InputStream
import java.util.jar.JarEntry
import java.util.jar.JarFile
import java.util.jar.JarOutputStream
import kotlin.system.measureTimeMillis


abstract class AllClassesTask : DefaultTransformTask() {

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

    private fun loadJoinPointConfig(){
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
            val temporaryDir = File(registerTransformIgnoreJarDir(project,variant))
            for (path in ignoreJar) {
                val destDir = "${temporaryDir.absolutePath}${File.separatorChar}${File(path).name.computeMD5()}"
                val destFile = File(destDir)
                destFile.deleteRecursively()
                openJar(path,destDir)
                ignoreJarClassPaths.add(destFile)
            }
        }

        fun processFile(file : File){
            AsmUtils.processFileForConfig(project,file)
        }
        for (directory in ignoreJarClassPaths) {
            directory.walk().forEach { file ->
                processFile(file)
            }
        }

        //第一遍找配置文件
        allDirectoryFiles.forEach { directory ->
            directory.walk().forEach { file ->
                processFile(file)
            }
        }

        allJarFiles.forEach { file ->
            if (file.absolutePath in ignoreJar){
                return@forEach
            }
            AsmUtils.processJarForConfig(file)
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
        val wovenCodeFileJobs1 = mutableListOf<Deferred<Unit>>()
        for (directory in ignoreJarClassPaths) {
            directory.walk().sortedBy {
                it.name.length
            }.forEach { file ->
                val job = async(Dispatchers.IO) {
                    processFile(file, directory)
                }
                wovenCodeFileJobs1.add(job)
            }

        }
        wovenCodeFileJobs1.awaitAll()
        val wovenCodeFileJobs2 = mutableListOf<Deferred<Unit>>()
        allDirectoryFiles.forEach { directory ->
            directory.walk().forEach { file ->
                val job = async(Dispatchers.IO) {
                    processFile(file,directory)
                }
                wovenCodeFileJobs2.add(job)
            }
        }
        wovenCodeFileJobs2.awaitAll()



        allJarFiles.forEach { file ->
            if (file.absolutePath in ignoreJar){
                return@forEach
            }
            val jarFile = JarFile(file)
            val enumeration = jarFile.entries()
            val wovenCodeJarJobs = mutableListOf<Deferred<Unit>>()
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
                wovenCodeJarJobs.add(job)
            }

            wovenCodeJarJobs.awaitAll()
            jarFile.close()
        }
        val tmpOtherDir = File(registerCompileTempDir(project,variant))
        AsmUtils.createInitClass(tmpOtherDir)
        for (file in tmpOtherDir.walk()) {
            if (file.isFile) {
                val className = file.getFileClassname(tmpOtherDir)
                file.inputStream().use {
                    file.saveJarEntry(className,it)
                }
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