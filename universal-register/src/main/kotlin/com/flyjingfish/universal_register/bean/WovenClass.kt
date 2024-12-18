package com.flyjingfish.universal_register.bean

data class WovenClass(
    val wovenClass: String,
    val wovenMethod: String,
    val searchClass: SearchClass
){
    fun clear(moduleName:String){
        searchClass.clear(moduleName)
    }
}