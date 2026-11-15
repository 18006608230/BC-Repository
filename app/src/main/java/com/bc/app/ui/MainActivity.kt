package com.bc.app.ui

import com.bc.app.ui.base.BaseActivity

import android.content.Intent
import android.os.Bundle
import com.bc.app.databinding.ActivityMainBinding
import com.bc.app.ui.home.HomeActivity

/**
 * 启动页Activity
 * 跳转到首页
 */
class MainActivity : BaseActivity() {

    private lateinit var binding: ActivityMainBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // 直接跳转到首页
        startActivity(Intent(this, HomeActivity::class.java))
        finish()
    }
}
