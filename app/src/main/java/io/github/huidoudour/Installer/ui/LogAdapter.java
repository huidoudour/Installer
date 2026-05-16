package io.github.huidoudour.Installer.ui;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.List;

import io.github.huidoudour.Installer.R;

/**
 * 日志列表 RecyclerView Adapter
 * 支持增量插入、批量替换，通过 RecyclerView 保证大量日志下的流畅渲染。
 */
public class LogAdapter extends RecyclerView.Adapter<LogAdapter.LogViewHolder> {

    private final List<String> items = new ArrayList<>();

    /** 追加一条日志 */
    public void appendLog(String log) {
        items.add(log);
        notifyItemInserted(items.size() - 1);
    }

    /** 全量替换（初始化或清空后重新加载） */
    public void setLogs(List<String> logs) {
        int oldSize = items.size();
        items.clear();
        if (oldSize > 0) notifyItemRangeRemoved(0, oldSize);
        items.addAll(logs);
        if (!items.isEmpty()) notifyItemRangeInserted(0, items.size());
    }

    /** 清空所有日志 */
    public void clearLogs() {
        int size = items.size();
        items.clear();
        if (size > 0) notifyItemRangeRemoved(0, size);
    }

    public int getLogCount() {
        return items.size();
    }

    @NonNull
    @Override
    public LogViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_log_entry, parent, false);
        return new LogViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull LogViewHolder holder, int position) {
        holder.tvLogEntry.setText(items.get(position));
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    static class LogViewHolder extends RecyclerView.ViewHolder {
        final TextView tvLogEntry;

        LogViewHolder(@NonNull View itemView) {
            super(itemView);
            tvLogEntry = (TextView) itemView;
        }
    }
}
