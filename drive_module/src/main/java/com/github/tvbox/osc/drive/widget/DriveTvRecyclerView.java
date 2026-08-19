package com.github.tvbox.osc.drive.widget;

import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.Rect;
import android.util.AttributeSet;
import android.view.KeyEvent;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.github.tvbox.osc.drive.R;

/**
 * 替代 TvRecyclerView 的轻量实现。
 * <p>
 * 保留了原 TvRecyclerView 在 DriveActivity 中实际使用到的功能：
 * <ul>
 *   <li>OnItemListener（onItemPreSelected / onItemSelected / onItemClick）</li>
 *   <li>onFocusChange / onClick 透传给 adapter 层</li>
 *   <li>setSelection / setSelectedPosition / smoothScrollToPosition / setSelectionWithSmooth</li>
 *   <li>setSpacingWithMargins</li>
 *   <li>XML 属性：tv_verticalSpacingWithMargins / tv_horizontalSpacingWithMargins / tv_layoutManager</li>
 * </ul>
 *
 * [修复] 新增：
 * <ul>
 *   <li>D-pad 确认键（DPAD_CENTER / ENTER）→ 自动对焦点子 View 执行 performClick()，
 *       兼容不自动派发 click 事件的电视盒子/车机 ROM</li>
 *   <li>onRequestChildFocus → 焦点子项自动滚动到可见区域（原 TvRecyclerView 核心行为）</li>
 * </ul>
 */
public class DriveTvRecyclerView extends RecyclerView {

    private int mVerticalSpacing = 0;
    private int mHorizontalSpacing = 0;
    private String mLayoutManagerType = "LinearLayoutManager";
    private int mSelectedPosition = -1;
    private OnItemListener mOnItemListener;

    /** [修复] 记录 ACTION_DOWN 时的焦点 View，用于 ACTION_UP 时匹配 */
    private View mConfirmKeyDownFocus = null;

    public interface OnItemListener {
        void onItemPreSelected(DriveTvRecyclerView recyclerView, View view, int position);
        void onItemSelected(DriveTvRecyclerView recyclerView, View view, int position);
        void onItemClick(DriveTvRecyclerView recyclerView, View itemView, int position);
    }

    public DriveTvRecyclerView(@NonNull Context context) {
        super(context);
        init(context, null);
    }

    public DriveTvRecyclerView(@NonNull Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        init(context, attrs);
    }

    public DriveTvRecyclerView(@NonNull Context context, @Nullable AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
        init(context, attrs);
    }

    private void init(Context context, AttributeSet attrs) {
        if (attrs != null) {
            TypedArray a = context.obtainStyledAttributes(attrs, R.styleable.DriveTvRecyclerView);
            mVerticalSpacing = a.getDimensionPixelSize(R.styleable.DriveTvRecyclerView_tv_verticalSpacingWithMargins, 0);
            mHorizontalSpacing = a.getDimensionPixelSize(R.styleable.DriveTvRecyclerView_tv_horizontalSpacingWithMargins, 0);
            mLayoutManagerType = a.getString(R.styleable.DriveTvRecyclerView_tv_layoutManager);
            if (mLayoutManagerType == null) mLayoutManagerType = "LinearLayoutManager";
            a.recycle();
        }
        setLayoutManager(createLayoutManager());
        addItemDecoration(new SpacingItemDecoration(mVerticalSpacing, mHorizontalSpacing));
        setDescendantFocusability(FOCUS_AFTER_DESCENDANTS);
        setItemViewCacheSize(0);
        setOnFocusChangeListener(null);
    }

    private LayoutManager createLayoutManager() {
        if ("V7LinearLayoutManager".equals(mLayoutManagerType)) {
            return new LinearLayoutManager(getContext(), LinearLayoutManager.VERTICAL, false);
        }
        if ("GridLayoutManager".equals(mLayoutManagerType)) {
            return new GridLayoutManager(getContext(), 1);
        }
        return new LinearLayoutManager(getContext());
    }

    // ==================== [修复] D-pad 确认键处理 ====================

    /**
     * [修复] 拦截 D-pad 确认键，确保在所有电视 ROM 上都能触发 item 点击。
     *
     * 原理：
     * 1. ACTION_DOWN 时记录当前焦点 View
     * 2. ACTION_UP 时先让系统正常派发（super.dispatchKeyEvent）
     * 3. 如果系统未消费该事件（返回 false），手动对焦点 View 调用 performClick()
     *
     * 这样在标准 ROM 上不会重复触发（系统已处理时 handled=true），
     * 在定制 ROM 上提供兜底（系统未处理时手动 performClick）。
     */
    @Override
    public boolean dispatchKeyEvent(KeyEvent event) {
        if (isConfirmKey(event.getKeyCode())) {
            if (event.getAction() == KeyEvent.ACTION_DOWN) {
                mConfirmKeyDownFocus = getFocusedChild();
            } else if (event.getAction() == KeyEvent.ACTION_UP) {
                View target = mConfirmKeyDownFocus;
                mConfirmKeyDownFocus = null;
                // 先让系统正常处理
                boolean handled = super.dispatchKeyEvent(event);
                if (!handled && target != null && target.isClickable()) {
                    target.performClick();
                    return true;
                }
                return handled;
            }
        }
        return super.dispatchKeyEvent(event);
    }

    private static boolean isConfirmKey(int keyCode) {
        return keyCode == KeyEvent.KEYCODE_DPAD_CENTER
                || keyCode == KeyEvent.KEYCODE_ENTER
                || keyCode == KeyEvent.KEYCODE_NUMPAD_ENTER;
    }

    // ==================== [修复] 焦点跟随滚动 ====================

    /**
     * [修复] 确保指定位置的 item 可见（自动滚动）。
     * 由 onFocusChange() 在子项获得焦点时调用，替代原版
     * TvRecyclerView 的 onRequestChildFocus 行为。
     * 标准 RecyclerView 不会自动滚动到焦点子项，此方法弥补该差距。
     *
     * 使用 requestChildRectangleOnScreen 而非 scrollToPosition，
     * 可以根据方向进行增量滚动（更平滑），并且兼容所有 API 级别。
     */
    private void ensureChildVisible(View child) {
        if (child == null) return;
        // 优先使用 requestChildRectangleOnScreen，兼容所有 API
        // 传入 child 自身 Rect，让 RecyclerView 判断是否需要滚动
        requestChildRectangleOnScreen(child, new Rect(0, 0, child.getWidth(), child.getHeight()), false);
    }

    // ==================== 公开 API（兼容 TvRecyclerView 调用方式） ====================

    public void setOnItemListener(OnItemListener listener) {
        this.mOnItemListener = listener;
    }

    public void setSelectedPosition(int position) {
        mSelectedPosition = position;
    }

    public void setSelection(int position) {
        mSelectedPosition = position;
        if (getLayoutManager() != null && getAdapter() != null
                && position >= 0 && position < getAdapter().getItemCount()) {
            getLayoutManager().scrollToPosition(position);
        }
    }

    public void setSelectionWithSmooth(int position) {
        mSelectedPosition = position;
        if (getLayoutManager() != null && getAdapter() != null
                && position >= 0 && position < getAdapter().getItemCount()) {
            getLayoutManager().smoothScrollToPosition(this, null, position);
        }
    }

    /**
     * 由 Adapter 的 item 焦点变化时调用。
     * [修复] 不再通过 post 延迟 notifyItemChanged，改为同步更新数据模型；
     * 焦点视觉效果由 adapter 的 FocusChangeListener 直接更新 itemView 背景，
     * 不触发 rebind，避免重建 click listener 导致遥控器确认键事件丢失。
     */
    public void onFocusChange(View child, boolean gainFocus) {
        int pos = getChildAdapterPosition(child);
        if (pos == NO_POSITION) return;
        if (gainFocus) {
            // [修复] 焦点跟随滚动：自动滚动到可见区域
            ensureChildVisible(child);
            int oldPos = mSelectedPosition;
            mSelectedPosition = pos;
            if (mOnItemListener != null) {
                // [修复] 仅更新数据模型，不触发 notifyItemChanged
                // 避免 rebind 重建 click listener 导致遥控器确认键丢失
                if (oldPos >= 0 && oldPos != pos) {
                    ViewHolder oldHolder = findViewHolderForAdapterPosition(oldPos);
                    if (oldHolder != null) {
                        mOnItemListener.onItemPreSelected(this, oldHolder.itemView, oldPos);
                    }
                }
                mOnItemListener.onItemSelected(this, child, pos);
            }
        }
    }

    /**
     * 由 Adapter 的 item click 时调用。
     */
    public void onClick(View child) {
        int pos = getChildAdapterPosition(child);
        if (pos == NO_POSITION) return;
        if (mOnItemListener != null) {
            mOnItemListener.onItemClick(this, child, pos);
        }
    }

    /**
     * 兼容原 TvRecyclerView 的 setSpacingWithMargins。
     */
    public void setSpacingWithMargins(int vertical, int horizontal) {
        mVerticalSpacing = vertical;
        mHorizontalSpacing = horizontal;
        for (int i = getItemDecorationCount() - 1; i >= 0; i--) {
            if (getItemDecorationAt(i) instanceof SpacingItemDecoration) {
                removeItemDecorationAt(i);
            }
        }
        addItemDecoration(new SpacingItemDecoration(mVerticalSpacing, mHorizontalSpacing));
    }

    // ==================== 内部 ItemDecoration ====================

    private static class SpacingItemDecoration extends ItemDecoration {
        private final int verticalSpacing;
        private final int horizontalSpacing;

        SpacingItemDecoration(int verticalSpacing, int horizontalSpacing) {
            this.verticalSpacing = verticalSpacing;
            this.horizontalSpacing = horizontalSpacing;
        }

        @Override
        public void getItemOffsets(@NonNull Rect outRect, @NonNull View view,
                                   @NonNull RecyclerView parent, @NonNull State state) {
            outRect.top = verticalSpacing;
            outRect.bottom = verticalSpacing;
            outRect.left = horizontalSpacing;
            outRect.right = horizontalSpacing;
        }
    }
}