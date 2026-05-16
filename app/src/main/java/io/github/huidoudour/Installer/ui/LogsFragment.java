package io.github.huidoudour.Installer.ui;

import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.core.content.FileProvider;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

import io.github.huidoudour.Installer.R;
import io.github.huidoudour.Installer.databinding.FragmentLogsBinding;
import io.github.huidoudour.Installer.util.LogManager;

public class LogsFragment extends Fragment implements LogManager.LogListener {

    private FragmentLogsBinding binding;
    private RecyclerView rvLogs;
    private LogAdapter logAdapter;
    private LinearLayoutManager layoutManager;
    private LogManager logManager;

    /** 标记位：用户是否已手动离开底部（滚上去看旧日志了） */
    private boolean userScrolledAwayFromBottom = false;

    public View onCreateView(@NonNull LayoutInflater inflater,
                             ViewGroup container, Bundle savedInstanceState) {
        binding = FragmentLogsBinding.inflate(inflater, container, false);
        View root = binding.getRoot();

        rvLogs = binding.rvLogs;

        // 初始化 RecyclerView（默认从顶部堆叠，新条目追加在底部）
        layoutManager = new LinearLayoutManager(requireContext());
        layoutManager.setStackFromEnd(false);
        rvLogs.setLayoutManager(layoutManager);

        logAdapter = new LogAdapter();
        rvLogs.setAdapter(logAdapter);

        // 监听用户滚动，判断是否离开了底部
        rvLogs.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrolled(@NonNull RecyclerView recyclerView, int dx, int dy) {
                // 能否继续向上滚（即是否已在最底部）
                boolean atBottom = !recyclerView.canScrollVertically(1);
                if (atBottom) {
                    // 用户滚到了底部 → 恢复自动跟随新日志
                    userScrolledAwayFromBottom = false;
                } else {
                    // 用户滚上去看旧日志了 → 暂停自动跟随
                    userScrolledAwayFromBottom = true;
                }
            }
        });

        // 获取日志管理器并加载已有日志
        logManager = LogManager.getInstance();
        logAdapter.setLogs(logManager.getLogsSnapshot());
        updateLogCountDisplay();

        // 注册监听器
        logManager.addListener(this);

        // 初始滚动到底部（显示最新日志）
        scrollToBottom(false);

        // 清空按钮
        binding.btnClearLog.setOnClickListener(v -> {
            logManager.clearLogs();
            Toast.makeText(requireContext(), R.string.log_cleared, Toast.LENGTH_SHORT).show();
        });

        // 导出按钮
        binding.btnExportLog.setOnClickListener(v -> exportLogs());

        return root;
    }

    // ── LogManager.LogListener 实现 ─────────────────────────────────────────

    @Override
    public void onLogAdded(String log, int index) {
        if (getActivity() == null || binding == null) return;
        getActivity().runOnUiThread(() -> {
            if (binding == null) return;
            logAdapter.appendLog(log);
            updateLogCountDisplay();
            // 仅当用户停留在底部时才自动跟随
            if (!userScrolledAwayFromBottom) {
                scrollToBottom(true);
            }
        });
    }

    @Override
    public void onLogCleared() {
        if (getActivity() == null || binding == null) return;
        getActivity().runOnUiThread(() -> {
            if (binding == null) return;
            logAdapter.clearLogs();
            userScrolledAwayFromBottom = false;
            updateLogCountDisplay();
        });
    }

    // ── 工具方法 ──────────────────────────────────────────────────────────────

    private void scrollToBottom(boolean smooth) {
        int count = logAdapter.getItemCount();
        if (count == 0) return;
        if (smooth) {
            rvLogs.smoothScrollToPosition(count - 1);
        } else {
            rvLogs.scrollToPosition(count - 1);
        }
    }

    private void updateLogCountDisplay() {
        if (binding == null) return;
        int count = logAdapter.getLogCount();
        if (count > 0) {
            binding.tvLogCount.setText(String.valueOf(count));
        } else {
            binding.tvLogCount.setText("");
        }
    }

    private void exportLogs() {
        try {
            String timestamp = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(new Date());
            String fileName = "installer_log_" + timestamp + ".txt";
            File logFile = new File(requireContext().getExternalCacheDir(), fileName);

            FileWriter writer = new FileWriter(logFile);
            writer.write(getString(R.string.log_export_header) + "\n");
            writer.write(getString(R.string.export_time,
                new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(new Date())) + "\n");
            writer.write(getString(R.string.total_logs, logManager.getLogCount()) + "\n");
            writer.write("\n" + getString(R.string.log_content_header) + "\n");
            writer.write(logManager.getAllLogs());
            writer.close();

            // 分享文件
            Intent shareIntent = new Intent(Intent.ACTION_SEND);
            shareIntent.setType("text/plain");
            shareIntent.putExtra(Intent.EXTRA_SUBJECT, getString(R.string.log_file_subject));
            shareIntent.putExtra(Intent.EXTRA_TEXT, getString(R.string.log_file_generated, fileName));
            shareIntent.putExtra(Intent.EXTRA_STREAM,
                FileProvider.getUriForFile(requireContext(),
                    requireContext().getPackageName() + ".provider", logFile));
            shareIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);

            startActivity(Intent.createChooser(shareIntent, getString(R.string.export_log)));
            Toast.makeText(requireContext(), getString(R.string.log_exported, fileName), Toast.LENGTH_LONG).show();
        } catch (IOException e) {
            Toast.makeText(requireContext(), getString(R.string.export_failed, e.getMessage()), Toast.LENGTH_LONG).show();
        } catch (Exception e) {
            Toast.makeText(requireContext(), R.string.log_saved_to_cache, Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        if (logManager != null) {
            logManager.removeListener(this);
        }
        binding = null;
    }
}
