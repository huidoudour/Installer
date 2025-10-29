package io.github.huidoudour.Installer.debug.ui.home

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import io.github.huidoudour.Installer.debug.R
import io.github.huidoudour.Installer.debug.databinding.FragmentHomeBinding

/**
 * 一个简单的Kotlin Fragment类，用于测试Kotlin和Java在项目中的共存
 */
class HomeFragmentKt : Fragment() {

    private var _binding: FragmentHomeBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentHomeBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        // 设置一些简单的文本
        binding.textHome.text = getWelcomeMessage()
    }

    /**
     * 一个简单的函数，返回欢迎消息
     */
    private fun getWelcomeMessage(): String {
        return "欢迎使用 Kotlin 版本的 Home Fragment!"
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}