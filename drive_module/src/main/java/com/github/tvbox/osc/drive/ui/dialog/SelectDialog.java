package com.github.tvbox.osc.drive.ui.dialog;

import android.content.Context;
import android.os.Bundle;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.github.tvbox.osc.drive.R;
import com.github.tvbox.osc.drive.ui.adapter.SelectDialogAdapter;
import com.github.tvbox.osc.drive.widget.DriveTvRecyclerView;

import org.jetbrains.annotations.NotNull;

import java.util.List;

public class SelectDialog<T> extends DriveBaseDialog {

    private boolean muteCheck = false;

    public SelectDialog(@NonNull @NotNull Context context) {
        super(context);
        setContentView(R.layout.drive_dialog_select);
    }

    public SelectDialog(@NonNull @NotNull Context context, int resId) {
        super(context);
        setContentView(resId);
    }

    public void setItemCheckDisplay(boolean shouldShowCheck) {
        muteCheck = !shouldShowCheck;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
    }

    public void setTip(String tip) {
        ((TextView) findViewById(R.id.title)).setText(tip);
    }

    /**
     * 设置 Adapter 和数据。
     * tvRecyclerView 参数：如果外部已有引用可传入，为 null 时自动从布局中 findViewById。
     */
    public void setAdapter(DriveTvRecyclerView tvRecyclerView, SelectDialogAdapter.SelectDialogInterface<T> selectDialogInterface, DiffUtil.ItemCallback<T> itemCallback, List<T> data, int select) {
        if (select >= data.size() || select < 0) select = 0;
        final int selectIdx = select;
        SelectDialogAdapter<T> adapter = new SelectDialogAdapter<>(selectDialogInterface, itemCallback, muteCheck);
        adapter.setData(data, select);
        if (tvRecyclerView == null) {
            tvRecyclerView = findViewById(R.id.list);
        }
        tvRecyclerView.setLayoutManager(new LinearLayoutManager(getContext(), LinearLayoutManager.VERTICAL, false));
        tvRecyclerView.setAdapter(adapter);
        tvRecyclerView.setSelectedPosition(select);
        if (select < 10) {
            tvRecyclerView.setSelection(select);
        }
        DriveTvRecyclerView finalTvRecyclerView = tvRecyclerView;
        tvRecyclerView.post(new Runnable() {
            @Override
            public void run() {
                if (selectIdx >= 10) {
                    finalTvRecyclerView.smoothScrollToPosition(selectIdx);
                    finalTvRecyclerView.setSelectionWithSmooth(selectIdx);
                }
            }
        });
    }
}