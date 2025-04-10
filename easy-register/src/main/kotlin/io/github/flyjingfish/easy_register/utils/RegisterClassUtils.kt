package io.github.flyjingfish.easy_register.utils

import io.github.flyjingfish.easy_register.bean.SearchClass
import io.github.flyjingfish.easy_register.bean.WovenClass
import io.github.flyjingfish.easy_register.config.RootStringConfig
import org.gradle.api.Project
import org.objectweb.asm.commons.Method
import java.io.File
import java.util.regex.Matcher
import java.util.regex.Pattern

object RegisterClassUtils {
    var log = true
    var enable = true
    var fastDex = false
    var mode = RootStringConfig.MODE.defaultValue
    private val configJsonFileList = mutableListOf<File>()
    private val searchWovenClasses = mutableListOf<WovenClass>()
    private val searchClasses = mutableListOf<SearchClass>()
    private val wovenClasses = HashSet<String>()
    private const val NOT_SET = 0
    private const val MATCH = 1
    private const val NOT_MATCH = 2

    fun addClass(moduleName:String,className:String,superClassName:String?,interfaces: Array<String>?){
        for (searchClass in searchClasses) {
            val matchExtends = if (searchClass.extendsClass.isNotEmpty()){
                var extends = searchClass.extendsClass == superClassName?.let { slashToDot(it) }
                if (!extends){
                    interfaces?.let {
                        for (s in it) {
                            if (searchClass.extendsClass == slashToDot(s)){
                                extends = true
                                break
                            }
                        }
                    }
                }
                if (extends){
                    MATCH
                }else{
                    NOT_MATCH
                }
            }else{
                NOT_SET
            }

            val matchName = if (searchClass.regex.isNotEmpty()){
                val classnameArrayPattern: Pattern = Pattern.compile(searchClass.regex)
                val matcher: Matcher = classnameArrayPattern.matcher(slashToDot(className))
                if (matcher.find()){
                    MATCH
                }else{
                    NOT_MATCH
                }
            }else{
                NOT_SET
            }

            if ((matchExtends == NOT_SET && matchName == MATCH)||
                (matchName == NOT_SET && matchExtends == MATCH)||
                (matchName == MATCH && matchExtends == MATCH)){
                searchClass.addClass(moduleName, className)
            }

        }
    }

    fun clear(moduleName: String){
        for (searchWovenClass in searchWovenClasses) {
            searchWovenClass.clear(moduleName)
        }
    }

    fun getClasses():List<WovenClass>{
        return searchWovenClasses
    }

    fun addConfigJsonFile(file:File){
        configJsonFileList.add(file)
    }

    fun clearConfigJsonFile(){
        configJsonFileList.clear()
    }

    fun initConfig(project: Project){
        initConfigClass(JsonUtils.getJson(configJsonFileList))
    }

    fun initConfig(jsons:List<String>){
        initConfigClass(JsonUtils.getJson4Str(jsons))
    }

    private fun initConfigClass(searchCls:List<WovenClass>){
        searchWovenClasses.clear()
        wovenClasses.clear()
        searchClasses.clear()
        searchWovenClasses.addAll(searchCls)
        for (searchCl in searchCls) {
            if (!searchCl.createWovenClass){
                wovenClasses.add(searchCl.wovenClass)
            }
            searchClasses.add(searchCl.searchClass)
        }
    }

    fun isWovenClass(className: String): Boolean {
        return className in wovenClasses
    }

    fun isCallClass(className: String): Boolean {
        for (searchClass in searchClasses) {
            if (searchClass.callClass.isNotEmpty() && searchClass.callClass == slashToDot(className)){
                return true
            }
        }
        return false
    }

    fun isCallClassMethod(className: String,methodName:String,methodDesc:String): Boolean {
        for (searchClass in searchClasses) {
            if (searchClass.callClass.isNotEmpty() && searchClass.callClass == slashToDot(className)){
                val isMuchMethod = searchClass.callMethod.contains("#")
                if (isMuchMethod){
                    val callMethods = searchClass.callMethod.split("#")
                    for ((index,callMethodStr) in callMethods.withIndex()) {
                        val method = Method.getMethod(callMethodStr)
                        if (method.name == methodName && method.descriptor == methodDesc){
                            return true
                        }
                    }
                }else{
                    val method = Method.getMethod(searchClass.callMethod)
                    if (method.name == methodName && method.descriptor == methodDesc){
                        return true
                    }
                }
            }
        }
        return false
    }

    fun getWovenClass(className: String,methodName:String,methodDesc:String): WovenClass? {
        for (searchWovenClass in searchWovenClasses) {
            if (searchWovenClass.wovenClass == slashToDot(className)){
                val method = Method.getMethod(searchWovenClass.wovenMethod)
                if (method.name == methodName && method.descriptor == methodDesc){
                    return searchWovenClass
                }
            }
        }
        return null
    }

    fun isDebugMode(buildTypeName :String?,variantName :String):Boolean{
        return if (mode == "auto" || mode == "debug"){
            if (mode == "auto"){
                if (buildTypeName != null){
                    buildTypeName.lowercase() == "debug"
                }else{
                    variantName.lowercase().contains("debug")
                }
            }else{
                true
            }
        }else{
            false
        }
    }
}