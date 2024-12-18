package com.flyjingfish.universal_register.plugin

import com.flyjingfish.universal_register.bean.WovenClass
import com.flyjingfish.universal_register.utils.JsonUtils
import com.flyjingfish.universal_register.utils.RouterClassUtils
import com.flyjingfish.universal_register.utils.adapterOSPath
import com.flyjingfish.universal_register.utils.registerCompileTempDir
import com.flyjingfish.universal_register.utils.checkExist
import com.flyjingfish.universal_register.utils.computeMD5
import com.flyjingfish.universal_register.utils.dotToSlash
import com.flyjingfish.universal_register.utils.getRelativePath
import com.flyjingfish.universal_register.utils.registerCompileTempJson
import com.flyjingfish.universal_register.utils.registerCompileTempWovenJson
import com.flyjingfish.universal_register.utils.saveEntry
import com.flyjingfish.universal_register.utils.saveFile
import com.flyjingfish.universal_register.utils.slashToDot
import com.flyjingfish.universal_register.visitor.SearchRouterClassScanner
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking
import org.gradle.api.Project
import org.gradle.api.logging.Logger
import org.objectweb.asm.ClassReader
import org.objectweb.asm.ClassWriter
import org.objectweb.asm.Opcodes
import org.objectweb.asm.Type
import org.objectweb.asm.commons.AdviceAdapter
import org.objectweb.asm.commons.Method
import java.io.File
import java.io.FileInputStream
import java.util.jar.JarFile
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
        println("universal-register search code start")
        val scanTimeCost = measureTimeMillis {
            scanFile()
        }
        println("universal-register search code finish, current cost time ${scanTimeCost}ms")

    }

    private fun scanFile() = runBlocking {
        searchJoinPointLocation()

        if (isApp){
            val tmpOtherDir = File(registerCompileTempDir(project,variantName))
            createInitClass(tmpOtherDir)
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
        RouterClassUtils.clear(project.name)
        allDirectories.forEach { directory ->
            val directoryPath = directory.absolutePath
            directory.walk().forEach { file ->
                processFileForConfig(file, directory, directoryPath)
            }

        }

        allJars.forEach { file ->
            RouterClassUtils.clear(file.absolutePath)
            processJarForConfig(file)
        }
    }

    private fun processFileForConfig(file: File, directory: File, directoryPath: String) {
        if (file.isFile) {
            if (file.absolutePath.endsWith(_CLASS)) {
                FileInputStream(file).use { inputs ->
                    val bytes = inputs.readAllBytes()
                    if (bytes.isNotEmpty()) {
                        val classReader = ClassReader(bytes)
                        classReader.accept(
                            SearchRouterClassScanner(project.name),
                            ClassReader.SKIP_CODE or ClassReader.SKIP_DEBUG or ClassReader.SKIP_FRAMES
                        )
                    }
                }
            }

        }
    }

    private fun processJarForConfig(file: File) {
        val jarFile = JarFile(file)
        val enumeration = jarFile.entries()
        while (enumeration.hasMoreElements()) {
            val jarEntry = enumeration.nextElement()
            try {
                val entryName = jarEntry.name
                if (jarEntry.isDirectory || jarEntry.name.isEmpty()) {
                    continue
                }
                if (entryName.endsWith(_CLASS)) {
                    jarFile.getInputStream(jarEntry).use { inputs ->
                        val bytes = inputs.readAllBytes()
                        if (bytes.isNotEmpty()) {
                            val classReader = ClassReader(bytes)
                            classReader.accept(
                                SearchRouterClassScanner(file.absolutePath),
                                ClassReader.SKIP_CODE or ClassReader.SKIP_DEBUG or ClassReader.SKIP_FRAMES
                            )
                        }
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
        jarFile.close()
    }

    fun createInitClass(output:File) = runBlocking {
        val wovenCodeJobs = mutableListOf<Deferred<Unit>>()
        val list: List<WovenClass> = RouterClassUtils.getClasses()
        for (wovenClass in list) {
            val job = async(Dispatchers.IO) {
                val method = Method.getMethod(wovenClass.wovenMethod)
                val searchClass = wovenClass.searchClass
                val className = dotToSlash(wovenClass.wovenClass) +"\$Woven"+(method.name+method.descriptor).computeMD5()
                //新建一个类生成器，COMPUTE_FRAMES，COMPUTE_MAXS这2个参数能够让asm自动更新操作数栈
                val cw = ClassWriter(ClassWriter.COMPUTE_FRAMES or ClassWriter.COMPUTE_MAXS)
                //生成一个public的类，类路径是com.study.Human
                cw.visit(
                    Opcodes.V1_8,
                    Opcodes.ACC_PUBLIC, dotToSlash(className), null, "java/lang/Object", null)

                //生成默认的构造方法： public Human()
                var mv = cw.visitMethod(Opcodes.ACC_PUBLIC, "<init>", "()V", null, null)
                mv.visitVarInsn(Opcodes.ALOAD, 0)
                mv.visitMethodInsn(Opcodes.INVOKESPECIAL, "java/lang/Object", "<init>", "()V", false)
                mv.visitInsn(Opcodes.RETURN)
                mv.visitMaxs(0, 0) //更新操作数栈
                mv.visitEnd() //一定要有visitEnd


                //生成静态方法
                mv = cw.visitMethod(Opcodes.ACC_PUBLIC + Opcodes.ACC_STATIC, "init", method.descriptor, null, null)
                mv.visitCode()
                val argTypes = Type.getArgumentTypes(method.descriptor)
                //生成静态方法中的字节码指令
                val set = searchClass.getClassNames()
                if (set.isNotEmpty()) {
                    for (routeModuleClassName in set) {
                        if (searchClass.callType == "callee" && searchClass.callClass.isNotEmpty()){
                            val callClazz = dotToSlash(searchClass.callClass)
                            var callMethod :Method ?= null
                            val isMuchMethod = searchClass.callMethod.contains("#")
                            var muchCallClazz = callClazz
                            var callVirtual = false
                            if (isMuchMethod){
                                val callMethods = searchClass.callMethod.split("#")
                                for ((index,callMethodStr) in callMethods.withIndex()) {
                                    val method1 = Method.getMethod(callMethodStr)
                                    if (index < callMethods.size - 1){
                                        if (method1.descriptor.startsWith("()") && method1.descriptor != "()V"){
                                            if (index == 0){
                                                mv.visitMethodInsn(
                                                    AdviceAdapter.INVOKESTATIC,
                                                    muchCallClazz,
                                                    method1.name,
                                                    method1.descriptor,
                                                    false
                                                )
                                            }else{
                                                mv.visitMethodInsn(
                                                    AdviceAdapter.INVOKEVIRTUAL,
                                                    muchCallClazz,
                                                    method1.name,
                                                    method1.descriptor,
                                                    false
                                                )
                                            }
                                            callVirtual = true
                                            muchCallClazz = dotToSlash(method1.returnType.className)
                                        }else{
                                            throw IllegalArgumentException("不支持 $searchClass 的 callMethod")
                                        }
                                    }else{
                                        callMethod = method1
                                    }


                                }
                            }else{
                                callMethod = Method.getMethod(searchClass.callMethod)
                            }
                            if (callMethod == null) continue

//                            println("muchCallClazz=$muchCallClazz, callMethod=$callMethod")

                            val callValues = searchClass.callMethodValue.split(",")
                            var containValue = false
                            for (callValue in callValues) {
                                when (callValue) {
                                    "searchClass" -> {
                                        when (searchClass.useType) {
                                            "className" -> {
                                                mv.visitLdcInsn(slashToDot(routeModuleClassName))
                                                containValue = true
                                            }
                                            "new" -> {
                                                mv.visitTypeInsn(AdviceAdapter.NEW, routeModuleClassName)
                                                mv.visitInsn(AdviceAdapter.DUP)
                                                mv.visitMethodInsn(
                                                    AdviceAdapter.INVOKESPECIAL,
                                                    routeModuleClassName,
                                                    "<init>",
                                                    "()V",
                                                    false
                                                )
                                                containValue = true
                                            }
                                            "class" -> {
                                                mv.visitLdcInsn(Type.getObjectType(routeModuleClassName))
                                                containValue = true
                                            }
                                        }
                                    }
                                    else -> {
                                        val CALL_VALUE_REGEX = Regex("\\$(\\d+)")
                                        val matchResult = CALL_VALUE_REGEX.find(callValue)
                                        if (matchResult != null) {
                                            val number = matchResult.groupValues[1].toInt()
                                            mv.visitVarInsn(Opcodes.ALOAD, number)
//                                            println("muchCallClazz=$muchCallClazz, callMethod=$callMethod，number=$number")
                                            containValue = true
                                        }


                                    }
                                }
                            }
                            mv.visitMethodInsn(
                                if (callVirtual) AdviceAdapter.INVOKEVIRTUAL else AdviceAdapter.INVOKESTATIC,
                                muchCallClazz,
                                callMethod.name,
                                callMethod.descriptor,
                                false
                            )

                        }

                        if (searchClass.callType == "caller"){
                            val callClazz = dotToSlash(routeModuleClassName)
                            var callMethod :Method ?= null
                            val isMuchMethod = searchClass.callMethod.contains("#")
                            var muchCallClazz = callClazz
                            var callVirtual = false
                            if (isMuchMethod){
                                val callMethods = searchClass.callMethod.split("#")
                                for ((index,callMethodStr) in callMethods.withIndex()) {
                                    val method1 = Method.getMethod(callMethodStr)
                                    if (index < callMethods.size - 1){
                                        if (method1.descriptor.startsWith("()") && method1.descriptor != "()V"){
                                            if (index == 0){
                                                mv.visitMethodInsn(
                                                    AdviceAdapter.INVOKESTATIC,
                                                    muchCallClazz,
                                                    method1.name,
                                                    method1.descriptor,
                                                    false
                                                )
                                            }else{
                                                mv.visitMethodInsn(
                                                    AdviceAdapter.INVOKEVIRTUAL,
                                                    muchCallClazz,
                                                    method1.name,
                                                    method1.descriptor,
                                                    false
                                                )
                                            }
                                            callVirtual = true
                                            muchCallClazz = dotToSlash(method1.returnType.className)
                                        }else{
                                            throw IllegalArgumentException("不支持 $searchClass 的 callMethod")
                                        }
                                    }else{
                                        callMethod = method1
                                    }
                                }
                            }else{
                                callMethod = Method.getMethod(searchClass.callMethod)
                            }
                            if (callMethod == null) continue

                            val callValues = searchClass.callMethodValue.split(",")
                            var containValue = false
                            for (callValue in callValues) {
                                when (callValue) {
                                    "searchClass" -> {
                                        when (searchClass.useType) {
                                            "className" -> {
                                                mv.visitLdcInsn(slashToDot(routeModuleClassName))
                                                containValue = true
                                            }
                                            "new" -> {
                                                mv.visitTypeInsn(AdviceAdapter.NEW, routeModuleClassName)
                                                mv.visitInsn(AdviceAdapter.DUP)
                                                mv.visitMethodInsn(
                                                    AdviceAdapter.INVOKESPECIAL,
                                                    routeModuleClassName,
                                                    "<init>",
                                                    "()V",
                                                    false
                                                )
                                                containValue = true
                                            }
                                            "class" -> {
                                                mv.visitLdcInsn(Type.getObjectType(routeModuleClassName))
                                                containValue = true
                                            }
                                        }
                                    }
                                    else -> {
                                        val CALL_VALUE_REGEX = Regex("\\$(\\d+)")
                                        val matchResult = CALL_VALUE_REGEX.find(callValue)
                                        if (matchResult != null) {
                                            val number = matchResult.groupValues[1].toInt()
                                            mv.visitVarInsn(Opcodes.ALOAD, number)
//                                            println("muchCallClazz=$muchCallClazz, callMethod=$callMethod，number=$number")
                                            containValue = true
                                        }
                                    }
                                }
                            }
//                            println("muchCallClazz=$muchCallClazz, callMethod=$callMethod，callVirtual=$callVirtual")
                            mv.visitMethodInsn(
                                if (callVirtual) AdviceAdapter.INVOKEVIRTUAL else AdviceAdapter.INVOKESTATIC,
                                muchCallClazz,
                                callMethod.name,
                                callMethod.descriptor,
                                false
                            )
                        }

                    }
                }


                mv.visitInsn(Opcodes.RETURN)
                mv.visitMaxs(argTypes.size, argTypes.size+1)
                mv.visitEnd()
                //设置必要的类路径
                val path = output.absolutePath + File.separatorChar + dotToSlash(className).adapterOSPath()+".class"
                //获取类的byte数组
                val classByteData = cw.toByteArray()
                //把类数据写入到class文件,这样你就可以把这个类文件打包供其他的人使用
                val outFile = File(path)
                outFile.checkExist()
                classByteData.saveFile(outFile)
            }
            wovenCodeJobs.add(job)
        }
        wovenCodeJobs.awaitAll()

    }

}