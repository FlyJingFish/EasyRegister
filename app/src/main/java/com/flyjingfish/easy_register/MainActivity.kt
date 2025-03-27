package com.flyjingfish.easy_register

import android.os.Bundle
import android.util.Log
import androidx.appcompat.app.AppCompatActivity
import com.flyjingfish.easy_register.databinding.ActivityMainBinding

class MainActivity: AppCompatActivity(){
    //    val haha = 1
    lateinit var binding:ActivityMainBinding
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        Log.e("MainActivity","=====>>>>22")
    }


}