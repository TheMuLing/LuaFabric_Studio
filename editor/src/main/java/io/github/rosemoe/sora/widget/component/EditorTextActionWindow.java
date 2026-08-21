/*
 *    sora-editor - the awesome code editor for Android
 *    https://github.com/Rosemoe/sora-editor
 *    Copyright (C) 2020-2024  Rosemoe
 *
 *     This library is free software; you can redistribute it and/or
 *     modify it under the terms of the GNU Lesser General Public
 *     License as published by the Free Software Foundation; either
 *     version 2.1 of the License, or (at your option) any later version.
 *
 *     This library is distributed in the hope that it will be useful,
 *     but WITHOUT ANY WARRANTY; without even the implied warranty of
 *     MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 *     Lesser General Public License for more details.
 *
 *     You should have received a copy of the GNU Lesser General Public
 *     License along with this library; if not, write to the Free Software
 *     Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301
 *     USA
 *
 *     Please contact Rosemoe by email 2073412493@qq.com if you need
 *     additional information or have any questions
 */
package io.github.rosemoe.sora.widget.component;

import android.annotation.SuppressLint;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.drawable.GradientDrawable;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.List;

import io.github.rosemoe.sora.R;
import io.github.rosemoe.sora.event.ColorSchemeUpdateEvent;
import io.github.rosemoe.sora.event.EditorFocusChangeEvent;
import io.github.rosemoe.sora.event.EditorReleaseEvent;
import io.github.rosemoe.sora.event.EventManager;
import io.github.rosemoe.sora.event.HandleStateChangeEvent;
import io.github.rosemoe.sora.event.InterceptTarget;
import io.github.rosemoe.sora.event.LongPressEvent;
import io.github.rosemoe.sora.event.ScrollEvent;
import io.github.rosemoe.sora.event.SelectionChangeEvent;
import io.github.rosemoe.sora.widget.CodeEditor;
import io.github.rosemoe.sora.widget.EditorTouchEventHandler;
import io.github.rosemoe.sora.widget.base.EditorPopupWindow;
import io.github.rosemoe.sora.widget.schemes.EditorColorScheme;

/**
 * This window will show when selecting text to present text actions.
 *
 * @author Rosemoe
 */
public class EditorTextActionWindow extends EditorPopupWindow implements TextActionAdapter.OnButtonClickListener, EditorBuiltinComponent {
    private final static long DELAY = 200;
    private final static long CHECK_FOR_DISMISS_INTERVAL = 100;
    private final CodeEditor editor;
    private final RecyclerView recyclerView;
    private final TextActionAdapter adapter;
    private final List<TextActionButton> buttons;
    private final LinearLayout rootCardView;
    private final EditorTouchEventHandler handler;
    private final EventManager eventManager;
    private long lastScroll;
    private int lastPosition;
    private int lastCause;
    private boolean enabled = true;

    // Button IDs
    private static final int BTN_SELECT_ALL = 1;
    private static final int BTN_COPY = 2;
    private static final int BTN_PASTE = 3;
    private static final int BTN_LONG_SELECT = 4;
    private static final int BTN_CUT = 5;
    private static final int BTN_AI_REFERENCE = 100;

    public interface OnCustomButtonClickListener {
        void onCustomButtonClick(int buttonId);
    }

    private OnCustomButtonClickListener customButtonClickListener;

    /**
     * Set a custom button click listener for handling custom buttons like AI reference.
     */
    public void setCustomButtonClickListener(OnCustomButtonClickListener listener) {
        this.customButtonClickListener = listener;
    }

    /**
     * Create a panel for the given editor
     *
     * @param editor Target editor
     */
    public EditorTextActionWindow(CodeEditor editor) {
        super(editor, FEATURE_SHOW_OUTSIDE_VIEW_ALLOWED);
        this.editor = editor;
        handler = editor.getEventHandler();
        eventManager = editor.createSubEventManager();

        // Initialize buttons list
        buttons = new ArrayList<>();
        buttons.add(new TextActionButton(BTN_SELECT_ALL, R.drawable.round_select_all_20, android.R.string.selectAll));
        buttons.add(new TextActionButton(BTN_COPY, R.drawable.round_content_copy_20, android.R.string.copy));
        buttons.add(new TextActionButton(BTN_PASTE, R.drawable.round_content_paste_20, android.R.string.paste));
        buttons.add(new TextActionButton(BTN_LONG_SELECT, R.drawable.editor_text_select_start, R.string.sora_editor_long_select));
        buttons.add(new TextActionButton(BTN_CUT, R.drawable.round_content_cut_20, android.R.string.cut));
        buttons.add(new TextActionButton(BTN_AI_REFERENCE, R.drawable.round_flare_20, R.string.sora_editor_ai_reference));

        // Inflate layout
        @SuppressLint("InflateParams")
        View rootView = LayoutInflater.from(editor.getContext()).inflate(R.layout.text_compose_panel, null);
        rootCardView = rootView.findViewById(R.id.panel_root);
        recyclerView = rootView.findViewById(R.id.panel_recycler_view);

        // Setup RecyclerView
        LinearLayoutManager layoutManager = new LinearLayoutManager(editor.getContext(),
                LinearLayoutManager.HORIZONTAL, false);
        recyclerView.setLayoutManager(layoutManager);
        adapter = new TextActionAdapter(buttons, this);
        recyclerView.setAdapter(adapter);

        // 添加 ItemDecoration 来控制间距，确保第一个按钮的左边距与最后一个按钮的右边距相等
        recyclerView.addItemDecoration(new RecyclerView.ItemDecoration() {
            @Override
            public void getItemOffsets(@NonNull Rect outRect, @NonNull View view,
                                       @NonNull RecyclerView parent, @NonNull RecyclerView.State state) {
                int position = parent.getChildAdapterPosition(view);
                int itemCount = parent.getAdapter().getItemCount();

                // 设置统一的边距值
                int sideMargin = (int) (8 * editor.getDpUnit()); // 第一个按钮左边距和最后一个按钮右边距
                int middleMargin = (int) (4 * editor.getDpUnit()); // 中间按钮之间的间距

                if (position == 0) {
                    // 第一个按钮：左边距为sideMargin，右边距为middleMargin/2
                    outRect.left = sideMargin;
                    outRect.right = middleMargin / 2;
                } else if (position == itemCount - 1) {
                    // 最后一个按钮：左边距为middleMargin/2，右边距为sideMargin（与第一个按钮左边距对齐）
                    outRect.left = middleMargin / 2;
                    outRect.right = sideMargin; // 确保与第一个按钮的左边距相等
                } else {
                    // 中间按钮：左右边距各为middleMargin/2
                    outRect.left = middleMargin / 2;
                    outRect.right = middleMargin / 2;
                }
                
                /* 上下边距
                int verticalSpacing = (int) (0 * editor.getDpUnit());
                outRect.top = verticalSpacing;
                outRect.bottom = verticalSpacing;*/
            }
        });

        // Apply color scheme for initial setup
        applyColorScheme();

        setContentView(rootView);
        setSize(0, (int) (editor.getDpUnit() * 50));
        //setSize(0, ViewGroup.LayoutParams.WRAP_CONTENT);

        getPopup().setAnimationStyle(R.style.text_action_popup_animation);

        subscribeEvents();
    }

    /**
     * Apply color scheme to the panel
     */
    protected void applyColorScheme() {
        // Update card view background with border
        GradientDrawable gd = new GradientDrawable();
        gd.setShape(GradientDrawable.RECTANGLE);
        gd.setCornerRadius(16 * editor.getDpUnit());
        gd.setColor(editor.getColorScheme().getColor(EditorColorScheme.TEXT_ACTION_WINDOW_BACKGROUND));

        // Add border
        int strokeWidth = (int) (1 * editor.getDpUnit());
        int strokeColor = editor.getColorScheme().getColor(EditorColorScheme.TEXT_ACTION_WINDOW_STROKE_COLOR);
        gd.setStroke(strokeWidth, strokeColor);

        rootCardView.setBackground(gd);

        // Update icon color
        int iconColor = editor.getColorScheme().getColor(EditorColorScheme.TEXT_ACTION_WINDOW_ICON_COLOR);
        adapter.setIconColor(iconColor);
    }

    protected void subscribeEvents() {
        eventManager.subscribeAlways(SelectionChangeEvent.class, this::onSelectionChange);
        eventManager.subscribeAlways(ScrollEvent.class, this::onEditorScroll);
        eventManager.subscribeAlways(HandleStateChangeEvent.class, this::onHandleStateChange);
        eventManager.subscribeAlways(LongPressEvent.class, this::onEditorLongPress);
        eventManager.subscribeAlways(EditorFocusChangeEvent.class, this::onEditorFocusChange);
        eventManager.subscribeAlways(EditorReleaseEvent.class, this::onEditorRelease);
        eventManager.subscribeAlways(ColorSchemeUpdateEvent.class, this::onColorSchemeUpdate);
    }

    protected void onColorSchemeUpdate(@NonNull ColorSchemeUpdateEvent event) {
        applyColorScheme();
    }

    protected void onEditorFocusChange(@NonNull EditorFocusChangeEvent event) {
        if (!event.isGainFocus()) {
            dismiss();
        }
    }

    protected void onEditorRelease(@NonNull EditorReleaseEvent event) {
        setEnabled(false);
    }

    protected void onEditorLongPress(@NonNull LongPressEvent event) {
        if (editor.getCursor().isSelected() && lastCause == SelectionChangeEvent.CAUSE_SEARCH) {
            var idx = event.getIndex();
            if (idx >= editor.getCursor().getLeft() && idx <= editor.getCursor().getRight()) {
                lastCause = 0;
                displayWindow();
            }
            event.intercept(InterceptTarget.TARGET_EDITOR);
        }
    }

    protected void onEditorScroll(@NonNull ScrollEvent event) {
        var last = lastScroll;
        lastScroll = System.currentTimeMillis();
        if (lastScroll - last < DELAY && lastCause != SelectionChangeEvent.CAUSE_SEARCH) {
            postDisplay();
        }
    }

    protected void onHandleStateChange(@NonNull HandleStateChangeEvent event) {
        if (event.isHeld()) {
            postDisplay();
        }
        if (!event.getEditor().getCursor().isSelected()
                && event.getHandleType() == HandleStateChangeEvent.HANDLE_TYPE_INSERT
                && !event.isHeld()) {
            displayWindow();
            // Also, post to hide the window on handle disappearance
            editor.postDelayedInLifecycle(new Runnable() {
                @Override
                public void run() {
                    if (!editor.getEventHandler().shouldDrawInsertHandle()
                            && !editor.getCursor().isSelected()) {
                        dismiss();
                    } else if (!editor.getCursor().isSelected()) {
                        editor.postDelayedInLifecycle(this, CHECK_FOR_DISMISS_INTERVAL);
                    }
                }
            }, CHECK_FOR_DISMISS_INTERVAL);
        }
    }

    protected void onSelectionChange(@NonNull SelectionChangeEvent event) {
        if (handler.hasAnyHeldHandle()) {
            return;
        }
        lastCause = event.getCause();
        if (event.isSelected()) {
            // Always post show. See #193
            if (event.getCause() != SelectionChangeEvent.CAUSE_SEARCH) {
                editor.postInLifecycle(this::displayWindow);
            } else {
                dismiss();
            }
            lastPosition = -1;
        } else {
            var show = false;
            if (event.getCause() == SelectionChangeEvent.CAUSE_TAP && event.getLeft().index == lastPosition && !isShowing() && !editor.getText().isInBatchEdit() && editor.isEditable()) {
                editor.postInLifecycle(this::displayWindow);
                show = true;
            } else {
                dismiss();
            }
            if (event.getCause() == SelectionChangeEvent.CAUSE_TAP && !show) {
                lastPosition = event.getLeft().index;
            } else {
                lastPosition = -1;
            }
        }
    }

    @Override
    public boolean isEnabled() {
        return enabled;
    }

    @Override
    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
        eventManager.setEnabled(enabled);
        if (!enabled) {
            dismiss();
        }
    }

    /**
     * Get the view root of the panel.
     *
     * @see R.id#panel_root
     */
    public ViewGroup getView() {
        return (ViewGroup) getPopup().getContentView();
    }

    private void postDisplay() {
        if (!isShowing()) {
            return;
        }
        dismiss();
        if (!editor.getCursor().isSelected()) {
            return;
        }
        editor.postDelayedInLifecycle(new Runnable() {
            @Override
            public void run() {
                if (!handler.hasAnyHeldHandle() && !editor.getSnippetController().isInSnippet() && System.currentTimeMillis() - lastScroll > DELAY
                        && editor.getScroller().isFinished()) {
                    displayWindow();
                } else {
                    editor.postDelayedInLifecycle(this, DELAY);
                }
            }
        }, DELAY);
    }

    private int selectTop(@NonNull RectF rect) {
        var rowHeight = editor.getRowHeight();
        if (rect.top - rowHeight * 3 / 2F > getHeight()) {
            return (int) (rect.top - rowHeight * 3 / 2 - getHeight());
        } else {
            return (int) (rect.bottom + rowHeight / 2);
        }
    }

    public void displayWindow() {
        updateBtnState();
        int top;
        var cursor = editor.getCursor();
        if (cursor.isSelected()) {
            var leftRect = editor.getLeftHandleDescriptor().position;
            var rightRect = editor.getRightHandleDescriptor().position;
            var top1 = selectTop(leftRect);
            var top2 = selectTop(rightRect);
            top = Math.min(top1, top2);
        } else {
            top = selectTop(editor.getInsertHandleDescriptor().position);
        }
        top = Math.max(0, Math.min(top, editor.getHeight() - getHeight() - 5));
        float handleLeftX = editor.getOffset(editor.getCursor().getLeftLine(), editor.getCursor().getLeftColumn());
        float handleRightX = editor.getOffset(editor.getCursor().getRightLine(), editor.getCursor().getRightColumn());
        int panelX = (int) ((handleLeftX + handleRightX) / 2f - rootCardView.getMeasuredWidth() / 2f);
        setLocationAbsolutely(panelX, top);
        show();
    }

    /**
     * Update the state of buttons
     */
    private void updateBtnState() {
        boolean hasSelection = editor.getCursor().isSelected();
        boolean isEditable = editor.isEditable();

        for (TextActionButton button : buttons) {
            int buttonId = button.getId();
            if (buttonId == BTN_COPY) {
                button.setVisible(hasSelection);
                button.setEnabled(true);
            } else if (buttonId == BTN_PASTE) {
                button.setVisible(isEditable);
                button.setEnabled(editor.hasClip());
            } else if (buttonId == BTN_CUT) {
                button.setVisible(hasSelection && isEditable);
                button.setEnabled(true);
            } else if (buttonId == BTN_LONG_SELECT) {
                button.setVisible(!hasSelection && isEditable);
                button.setEnabled(true);
            } else if (buttonId == BTN_SELECT_ALL) {
                button.setVisible(true);
                button.setEnabled(true);
            } else if (buttonId == BTN_AI_REFERENCE) {
                button.setVisible(hasSelection);
                button.setEnabled(true);
            }
        }

        adapter.updateButtons(buttons);

        // 测量并更新大小
        rootCardView.measure(
                View.MeasureSpec.makeMeasureSpec(1000000, View.MeasureSpec.AT_MOST),
                View.MeasureSpec.makeMeasureSpec(100000, View.MeasureSpec.AT_MOST)
        );

        // 计算合适的宽度，确保第一个按钮的左边距与最后一个按钮的右边距对齐
        int buttonWidth = (int) (45 * editor.getDpUnit()); // 每个按钮宽度
        int sideMargin = (int) (8 * editor.getDpUnit()); // 第一个按钮左边距和最后一个按钮右边距
        int middleMargin = (int) (4 * editor.getDpUnit()); // 中间按钮之间的间距

        int visibleButtonCount = 0;
        for (TextActionButton button : buttons) {
            if (button.isVisible()) visibleButtonCount++;
        }

        if (visibleButtonCount > 0) {
            // 计算总宽度：按钮宽度 + 中间间距 + 左右边距
            // 注意：sideMargin已经包含了ItemDecoration中的边距
            // 总宽度 = (按钮宽度 × 按钮数量) + (中间间距 × (按钮数量 - 1)) + (侧边距 × 2)
            int calculatedWidth = visibleButtonCount * buttonWidth
                    + (visibleButtonCount - 1) * middleMargin
                    + sideMargin * 2;

            // 限制最大宽度
            int maxWidth = (int) (editor.getDpUnit() * 250);

            setSize(Math.min(calculatedWidth, maxWidth), getHeight());
        } else {
            setSize(0, getHeight());
        }
    }

    @Override
    public void show() {
        if (!enabled || editor.getSnippetController().isInSnippet() || !editor.hasFocus() || editor.isInMouseMode()) {
            return;
        }

        // 确保窗口大小正确
        updateBtnState();
        super.show();
    }

    @Override
    public void onButtonClick(int buttonId) {
        if (buttonId == BTN_SELECT_ALL) {
            editor.selectAll();
        } else if (buttonId == BTN_CUT) {
            if (editor.getCursor().isSelected()) {
                editor.cutText();
            }
        } else if (buttonId == BTN_PASTE) {
            editor.pasteText();
            editor.setSelection(editor.getCursor().getRightLine(), editor.getCursor().getRightColumn());
        } else if (buttonId == BTN_COPY) {
            editor.copyText();
            editor.setSelection(editor.getCursor().getRightLine(), editor.getCursor().getRightColumn());
        } else if (buttonId == BTN_LONG_SELECT) {
            editor.beginLongSelect();
        } else if (buttonId == BTN_AI_REFERENCE) {
            if (customButtonClickListener != null) {
                customButtonClickListener.onCustomButtonClick(buttonId);
            }
            // Don't dismiss - let the caller handle it
            return;
        }
        dismiss();
    }

}