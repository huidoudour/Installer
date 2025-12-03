package io.github.huidoudour.Installer;

import android.os.Bundle;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;
import io.github.huidoudour.Installer.utils.NativeHelper;

/**
 * 原生库测试 Activity
 * <p>
 * 用于演示和测试 C++ 原生库的功能
 */
public class NativeTestActivity extends AppCompatActivity {

    private TextView textViewResult;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        
        // 创建简单的布局
        textViewResult = new TextView(this);
        textViewResult.setPadding(40, 40, 40, 40);
        textViewResult.setTextSize(14);
        setContentView(textViewResult);
        
        // 运行测试
        runNativeLibraryTests();
    }

    private void runNativeLibraryTests() {
        StringBuilder result = new StringBuilder();
        result.append("=== 原生库测试 ===\n\n");
        
        // 检查库是否可用
        if (NativeHelper.isNativeLibraryAvailable()) {
            result.append("✅ 原生库加载成功!\n\n");
            
            NativeHelper helper = new NativeHelper();
            
            // 测试 1: 获取库信息
            result.append("【测试 1】库信息:\n");
            result.append(helper.getLibraryInfo());
            result.append("\n\n");
            
            // 测试 2: 性能对比
            result.append("【测试 2】性能对比:\n");
            result.append(helper.runPerformanceComparison());
            result.append("\n\n");
            
            // 测试 3: 哈希性能
            result.append("【测试 3】哈希性能:\n");
            result.append(helper.testHashPerformance());
            result.append("\n\n");
            
            // 测试 4: 简单哈希计算
            result.append("【测试 4】哈希计算示例:\n");
            String testInput = "Hello, Android Native!";
            String hash = helper.calculateSimpleHash(testInput);
            result.append("Input: \"").append(testInput).append("\"\n");
            result.append("Hash: ").append(hash).append("\n");
            
            result.append("\n✅ 所有测试完成!");
            
        } else {
            result.append("❌ 原生库加载失败!\n\n");
            result.append("错误信息:\n");
            result.append(NativeHelper.getLoadError());
            result.append("\n\n");
            result.append("请检查:\n");
            result.append("1. NDK 是否正确安装\n");
            result.append("2. CMake 配置是否正确\n");
            result.append("3. 架构是否匹配 (arm64-v8a 或 x86_64)\n");
        }
        
        textViewResult.setText(result.toString());
    }
}
