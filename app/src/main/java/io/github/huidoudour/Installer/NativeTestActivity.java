package io.github.huidoudour.Installer;

import android.os.Bundle;
import android.widget.ScrollView;
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
        
        // 创建可滚动的布局
        ScrollView scrollView = new ScrollView(this);
        scrollView.setFillViewport(true);
        
        textViewResult = new TextView(this);
        textViewResult.setPadding(40, 40, 40, 40);
        textViewResult.setTextSize(14);
        textViewResult.setTextIsSelectable(true); // 允许选中文字
        
        scrollView.addView(textViewResult);
        setContentView(scrollView);
        
        // 运行测试
        runNativeLibraryTests();
    }

    private void runNativeLibraryTests() {
        StringBuilder result = new StringBuilder();
        result.append(getString(R.string.native_test_title)).append("\n\n");
        
        // 检查库是否可用
        if (NativeHelper.isNativeLibraryAvailable()) {
            result.append(getString(R.string.native_lib_loaded_success)).append("\n\n");
            
            NativeHelper helper = new NativeHelper();
            
            // 测试 1: 获取库信息
            result.append(getString(R.string.native_lib_test_1)).append("\n");
            result.append(helper.getLibraryInfo());
            result.append("\n\n");
            
            // 测试 2: 性能对比
            result.append(getString(R.string.native_lib_test_2)).append("\n");
            result.append(helper.runPerformanceComparison());
            result.append("\n\n");
            
            // 测试 3: 哈希性能
            result.append(getString(R.string.native_lib_test_3)).append("\n");
            result.append(helper.testHashPerformance());
            result.append("\n\n");
            
            // 测试 4: 简单哈希计算
            result.append(getString(R.string.native_lib_test_4)).append("\n");
            String testInput = "Hello, Android Native!";
            String hash = helper.calculateSimpleHash(testInput);
            result.append(getString(R.string.native_lib_test_input, testInput)).append("\n");
            result.append(getString(R.string.native_lib_test_hash, hash)).append("\n");
            
            result.append("\n").append(getString(R.string.native_lib_test_completed));
            
        } else {
            result.append(getString(R.string.native_lib_load_failed)).append("\n\n");
            result.append(getString(R.string.native_lib_error_message)).append("\n");
            result.append(NativeHelper.getLoadError());
            result.append("\n\n");
            result.append(getString(R.string.native_lib_check_list)).append("\n");
            result.append(getString(R.string.native_lib_check_ndk)).append("\n");
            result.append(getString(R.string.native_lib_check_cmake)).append("\n");
            result.append(getString(R.string.native_lib_check_arch)).append("\n");
        }
        
        textViewResult.setText(result.toString());
    }
}
