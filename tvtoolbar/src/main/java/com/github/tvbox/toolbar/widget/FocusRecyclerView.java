package com.github.tvbox.toolbar.widget;

import android.content.Context;
import android.util.AttributeSet;
import android.view.KeyEvent;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

/**
 * TV 焦点增强 RecyclerView
 * <p>
 * 替代 com.owen.tvrecyclerview.widget.TvRecyclerView，提供：
 * 1. 焦点选中/取消时的缩放动画回调（OnItemFocusChangeListener）
 * 2. 边界按键事件拦截（OnBorderKeyListener）
 * 3. 焦点移入时自动滚动使选中项居中
 */
public class FocusRecyclerView extends RecyclerView {

    private OnItemFocusChangeListener mOnItemFocusChangeListener;
    private OnBorderKeyListener mOnBorderKeyListener;

    public FocusRecyclerView(@NonNull Context context) {
        super(context);
    }

    public FocusRecyclerView(@NonNull Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
    }

    public FocusRecyclerView(@NonNull Context context, @Nullable AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
    }

    /**
     * 子 View 获得焦点时回调，驱动选中动画 + 自动滚动
     */
    @Override
    public void requestChildFocus(View child, View focused) {
        super.requestChildFocus(child, focused);
        if (mOnItemFocusChangeListener != null) {
            int pos = getChildAdapterPosition(child);
            if (pos != NO_POSITION) {
                mOnItemFocusChangeListener.onItemSelected(this, child, pos);
                smoothScrollToPosition(pos);
            }
        }
    }

    /**
     * 子 View 失去焦点时回调，驱动取消选中动画
     */
    @Override
    public void clearChildFocus(View child) {
        super.clearChildFocus(child);
        if (mOnItemFocusChangeListener != null) {
            int pos = getChildAdapterPosition(child);
            if (pos != NO_POSITION) {
                mOnItemFocusChangeListener.onItemPreSelected(this, child, pos);
            }
        }
    }

    /**
     * 拦截边界按键事件
     * <p>
     * 当焦点在 RecyclerView 边缘项上时，按下方向键会触发此回调。
     * 返回 true 表示已消费该事件，false 表示交由系统处理（焦点移出 RecyclerView）。
     */
    @Override
    public boolean dispatchKeyEvent(KeyEvent event) {
        if (mOnBorderKeyListener != null && event.getAction() == KeyEvent.ACTION_DOWN) {
            View focused = getFocusedChild();
            if (focused != null) {
                int direction = -1;
                switch (event.getKeyCode()) {
                    case KeyEvent.KEYCODE_DPAD_UP:
                        direction = View.FOCUS_UP;
                        break;
                    case KeyEvent.KEYCODE_DPAD_DOWN:
                        direction = View.FOCUS_DOWN;
                        break;
                    case KeyEvent.KEYCODE_DPAD_LEFT:
                        direction = View.FOCUS_LEFT;
                        break;
                    case KeyEvent.KEYCODE_DPAD_RIGHT:
                        direction = View.FOCUS_RIGHT;
                        break;
                    default:
                        break;
                }
                if (direction >= 0 && isAtBorder(focused, direction)) {
                    if (mOnBorderKeyListener.onBorderKey(direction, focused)) {
                        return true;
                    }
                }
            }
        }
        return super.dispatchKeyEvent(event);
    }

    /**
     * 判断当前焦点 View 是否在指定方向的边界
     */
    private boolean isAtBorder(View focused, int direction) {
        int pos = getChildAdapterPosition(focused);
        if (pos == NO_POSITION) return false;
        LayoutManager lm = getLayoutManager();
        if (lm == null) return false;

        int itemCount = getAdapter() != null ? getAdapter().getItemCount() : 0;
        int spanCount = 1;
        if (lm instanceof GridLayoutManager) {
            spanCount = ((GridLayoutManager) lm).getSpanCount();
        }

        switch (direction) {
            case View.FOCUS_UP:
                return pos < spanCount;
            case View.FOCUS_DOWN:
                return pos >= itemCount - spanCount;
            case View.FOCUS_LEFT:
                return pos % spanCount == 0;
            case View.FOCUS_RIGHT:
                return (pos + 1) % spanCount == 0 || pos == itemCount - 1;
            default:
                return false;
        }
    }

    // ========== 公开 API ==========

    public void setOnItemFocusChangeListener(OnItemFocusChangeListener listener) {
        mOnItemFocusChangeListener = listener;
    }

    public void setOnBorderKeyListener(OnBorderKeyListener listener) {
        mOnBorderKeyListener = listener;
    }

    /**
     * 焦点选中/取消选中回调（替代 TvRecyclerView.OnItemListener）
     */
    public interface OnItemFocusChangeListener {
        /** 焦点离开某项时回调 */
        void onItemPreSelected(FocusRecyclerView parent, View itemView, int position);
        /** 焦点到达某项时回调 */
        void onItemSelected(FocusRecyclerView parent, View itemView, int position);
    }

    /**
     * 边界按键回调（替代 TvRecyclerView.OnInBorderKeyEventListener）
     */
    public interface OnBorderKeyListener {
        /**
         * @param direction 方向（View.FOCUS_UP/DOWN/LEFT/RIGHT）
         * @param focused   当前焦点 View
         * @return true 拦截事件，false 允许焦点移出
         */
        boolean onBorderKey(int direction, View focused);
    }
}