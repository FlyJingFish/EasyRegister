package com.flyjingfish.easy_register.base

import android.app.Application

object InitCollect {
    private val Collects = mutableListOf<SubApplication>()
    @JvmStatic
    fun testEasyRegister(application: Application){
        for (collect in Collects) {
            collect.onCreate(application)
        }
    }

    @JvmStatic
    fun addEasyRegister(sub:SubApplication){
        Collects.add(sub)
    }
}