package io.github.huidoudour.Installer.debug.ui.home

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import io.github.huidoudour.Installer.debug.R

/**
 * 一个简单的Kotlin Fragment类，用于测试Kotlin和Java在项目中的共存
 */
class HomeFragmentKt : Fragment() {

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_home, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        // 不再引用不存在的textHome
    }

    private fun getWelcomeMessage(): String {
        return "欢迎使用 Kotlin 版本的 Home Fragment!"
    }
}