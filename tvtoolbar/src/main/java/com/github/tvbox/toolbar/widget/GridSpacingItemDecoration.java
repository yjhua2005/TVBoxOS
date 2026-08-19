package com.github.tvbox.toolbar.widget;

import android.graphics.Rect;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

/**
 * 网格间距 ItemDecoration
 * <p>
 * 替代 tv-recyclerview 的 tv_horizontalSpacingWithMargins / tv_verticalSpacingWithMargins 属性。
 */
public class GridSpacingItemDecoration extends RecyclerView.ItemDecoration {

    private final int mSpanCount;
    private final int mSpacing;
    private final boolean mIncludeEdge;

    /**
     * @param spanCount   列数
     * @param spacing     间距（px）
     * @param includeEdge 是否在边缘也绘制间距（使左右/上下 padding 均匀）
     */
    public GridSpacingItemDecoration(int spanCount, int spacing, boolean includeEdge) {
        mSpanCount = spanCount;
        mSpacing = spacing;
        mIncludeEdge = includeEdge;
    }

    @Override
    public void getItemOffsets(@NonNull Rect outRect, @NonNull View view,
                               @NonNull RecyclerView parent, @NonNull RecyclerView.State state) {
        int position = parent.getChildAdapterPosition(view);
        int column = position % mSpanCount;

        if (mIncludeEdge) {
            // 边缘项也有间距，与 RecyclerView 的 padding 配合实现均匀分布
            outRect.left = mSpacing - column * mSpacing / mSpanCount;
            outRect.right = (column + 1) * mSpacing / mSpanCount;

            if (position < mSpanCount) {
                outRect.top = mSpacing;
            }
            outRect.bottom = mSpacing;
        } else {
            outRect.left = column * mSpacing / mSpanCount;
            outRect.right = mSpacing - (column + 1) * mSpacing / mSpanCount;

            if (position >= mSpanCount) {
                outRect.top = mSpacing;
            }
        }
    }
}