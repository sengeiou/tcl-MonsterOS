package mst.view.menu;


import com.mst.R;

import android.content.Context;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.content.res.TypedArray;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.text.TextUtils;
import android.util.AttributeSet;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;
import android.widget.ActionMenuView;
import android.widget.ListPopupWindow;
import android.widget.TextView;
import android.widget.Toast;

public class MstActionMenuItemView extends TextView  implements MstMenuView.ItemView, View.OnClickListener, View.OnLongClickListener,
MstActionMenuView.ActionMenuChildView{
	 private static final String TAG = "ActionMenuItemView";

	    private MstMenuItemImpl mItemData;
	    private CharSequence mTitle;
	    private Drawable mIcon;
	    private MstMenuBuilder.ItemInvoker mItemInvoker;
//	    private ForwardingListener mForwardingListener;
	    private PopupCallback mPopupCallback;

	    private boolean mAllowTextWithIcon;
	    private boolean mExpandedFormat;
	    private int mMinWidth;
	    private int mSavedPaddingLeft;

	    private static final int MAX_ICON_SIZE = 32; // dp
	    private int mMaxIconSize;

	    public MstActionMenuItemView(Context context) {
	        this(context, null);
	    }

	    public MstActionMenuItemView(Context context, AttributeSet attrs) {
	        this(context, attrs, 0);
	    }

	    public MstActionMenuItemView(Context context, AttributeSet attrs, int defStyleAttr) {
	        this(context, attrs, defStyleAttr, 0);
	    }

	    public MstActionMenuItemView(Context context, AttributeSet attrs, int defStyleAttr, int defStyleRes) {
	        super(context, attrs, defStyleAttr, defStyleRes);
	        final Resources res = context.getResources();
	        mAllowTextWithIcon = res.getBoolean(
	                R.bool.config_allowActionMenuItemTextWithIcon);
	        final TypedArray a = context.obtainStyledAttributes(attrs,
	                R.styleable.ActionMenuItemView, defStyleAttr, defStyleRes);
	        mMinWidth = a.getDimensionPixelSize(
	                R.styleable.ActionMenuItemView_android_minWidth, 0);
	        a.recycle();

	        final float density = res.getDisplayMetrics().density;
	        mMaxIconSize = (int) (MAX_ICON_SIZE * density + 0.5f);

	        setOnClickListener(this);
	        setOnLongClickListener(this);

	        mSavedPaddingLeft = -1;
	    }

	    @Override
	    public void onConfigurationChanged(Configuration newConfig) {
	        super.onConfigurationChanged(newConfig);

	        mAllowTextWithIcon = getContext().getResources().getBoolean(
	                R.bool.config_allowActionMenuItemTextWithIcon);
	        updateTextButtonVisibility();
	    }

	    @Override
	    public void setPadding(int l, int t, int r, int b) {
	        mSavedPaddingLeft = l;
	        super.setPadding(l, t, r, b);
	    }

	    public MstMenuItemImpl getItemData() {
	        return mItemData;
	    }

	    @Override
	    public void initialize(MstMenuItemImpl itemData, int menuType) {
	        mItemData = itemData;

	        setIcon(itemData.getIcon());
	        setTitle(itemData.getTitleForItemView(this)); // Title only takes effect if there is no icon
	        setId(itemData.getItemId());

	        setVisibility(itemData.isVisible() ? View.VISIBLE : View.GONE);
	        setEnabled(itemData.isEnabled());

	        if (itemData.hasSubMenu()) {
//	            if (mForwardingListener == null) {
//	                mForwardingListener = new ActionMenuItemForwardingListener();
//	            }
	        }
	    }

	    @Override
	    public boolean onTouchEvent(MotionEvent e) {
//	        if (mItemData.hasSubMenu() && mForwardingListener != null
//	                && mForwardingListener.onTouch(this, e)) {
//	            return true;
//	        }
	        return super.onTouchEvent(e);
	    }

	    @Override
	    public void onClick(View v) {
	        if (mItemInvoker != null) {
	            mItemInvoker.invokeItem(mItemData);
	        }
	    }

	    public void setItemInvoker(MstMenuBuilder.ItemInvoker invoker) {
	        mItemInvoker = invoker;
	    }

	    public void setPopupCallback(PopupCallback popupCallback) {
	        mPopupCallback = popupCallback;
	    }

	    public boolean prefersCondensedTitle() {
	        return true;
	    }

	    public void setCheckable(boolean checkable) {
	        // TODO Support checkable action items
	    }

	    public void setChecked(boolean checked) {
	        // TODO Support checkable action items
	    }

	    public void setExpandedFormat(boolean expandedFormat) {
	        if (mExpandedFormat != expandedFormat) {
	            mExpandedFormat = expandedFormat;
	            if (mItemData != null) {
	                mItemData.actionFormatChanged();
	            }
	        }
	    }

	    private void updateTextButtonVisibility() {
	        boolean visible = !TextUtils.isEmpty(mTitle);
	        visible &= mIcon == null ||
	                (mItemData.showsTextAsAction() && (mAllowTextWithIcon || mExpandedFormat));

	        setText(visible ? mTitle : null);
	    }

	    public void setIcon(Drawable icon) {
	        mIcon = icon;
	        if (icon != null) {
	            int width = icon.getIntrinsicWidth();
	            int height = icon.getIntrinsicHeight();
	            if (width > mMaxIconSize) {
	                final float scale = (float) mMaxIconSize / width;
	                width = mMaxIconSize;
	                height *= scale;
	            }
	            if (height > mMaxIconSize) {
	                final float scale = (float) mMaxIconSize / height;
	                height = mMaxIconSize;
	                width *= scale;
	            }
	            icon.setBounds(0, 0, width, height);
	        }
	        setCompoundDrawables(icon, null, null, null);

	        updateTextButtonVisibility();
	    }
	    
	    public Drawable getIcon(){
	    	return mIcon;
	    }
	    

	    public boolean hasText() {
	        return !TextUtils.isEmpty(getText());
	    }

	    public void setShortcut(boolean showShortcut, char shortcutKey) {
	        // Action buttons don't show text for shortcut keys.
	    }

	    public void setTitle(CharSequence title) {
	        mTitle = title;

	        setContentDescription(mTitle);
	        updateTextButtonVisibility();
	    }

	    public boolean dispatchPopulateAccessibilityEventInternal(AccessibilityEvent event) {
	        onPopulateAccessibilityEvent(event);
	        return true;
	    }

	    public void onPopulateAccessibilityEventInternal(AccessibilityEvent event) {
	        final CharSequence cdesc = getContentDescription();
	        if (!TextUtils.isEmpty(cdesc)) {
	            event.getText().add(cdesc);
	        }
	    }

	    @Override
	    public boolean dispatchHoverEvent(MotionEvent event) {
	        // Don't allow children to hover; we want this to be treated as a single component.
	        return onHoverEvent(event);
	    }

	    public boolean showsIcon() {
	        return true;
	    }

	    public boolean needsDividerBefore() {
	        return hasText() && mItemData.getIcon() == null;
	    }

	    public boolean needsDividerAfter() {
	        return hasText();
	    }

	    @Override
	    public boolean onLongClick(View v) {
	        if (hasText()) {
	            // Don't show the cheat sheet for items that already show text.
	            return false;
	        }

	        final int[] screenPos = new int[2];
	        final Rect displayFrame = new Rect();
	        getLocationOnScreen(screenPos);
	        getWindowVisibleDisplayFrame(displayFrame);

	        final Context context = getContext();
	        final int width = getWidth();
	        final int height = getHeight();
	        final int midy = screenPos[1] + height / 2;
	        int referenceX = screenPos[0] + width / 2;
	        if (v.getLayoutDirection() == View.LAYOUT_DIRECTION_LTR) {
	            final int screenWidth = context.getResources().getDisplayMetrics().widthPixels;
	            referenceX = screenWidth - referenceX; // mirror
	        }
	      /*  Toast cheatSheet = Toast.makeText(context, mItemData.getTitle(), Toast.LENGTH_SHORT);
	        if (midy < displayFrame.height()) {
	            // Show along the top; follow action buttons
	            cheatSheet.setGravity(Gravity.TOP | Gravity.END, referenceX,
	                    screenPos[1] + height - displayFrame.top);
	        } else {
	            // Show along the bottom center
	            cheatSheet.setGravity(Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL, 0, height);
	        }
	        cheatSheet.show();*/
	        return true;
	    }

	    @Override
	    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
	        final boolean textVisible = hasText();
	        if (textVisible && mSavedPaddingLeft >= 0) {
	            super.setPadding(mSavedPaddingLeft, getPaddingTop(),
	                    getPaddingRight(), getPaddingBottom());
	        }

	        super.onMeasure(widthMeasureSpec, heightMeasureSpec);

	        final int widthMode = MeasureSpec.getMode(widthMeasureSpec);
	        final int widthSize = MeasureSpec.getSize(widthMeasureSpec);
	        final int oldMeasuredWidth = getMeasuredWidth();
	        final int targetWidth = widthMode == MeasureSpec.AT_MOST ? Math.min(widthSize, mMinWidth)
	                : mMinWidth;

	        if (widthMode != MeasureSpec.EXACTLY && mMinWidth > 0 && oldMeasuredWidth < targetWidth) {
	            // Remeasure at exactly the minimum width.
	            super.onMeasure(MeasureSpec.makeMeasureSpec(targetWidth, MeasureSpec.EXACTLY),
	                    heightMeasureSpec);
	        }

	        if (!textVisible && mIcon != null) {
	            // TextView won't center compound drawables in both dimensions without
	            // a little coercion. Pad in to center the icon after we've measured.
	            final int w = getMeasuredWidth();
	            final int dw = mIcon.getBounds().width();
	            super.setPadding((w - dw) / 2, getPaddingTop(), getPaddingRight(), getPaddingBottom());
	        }
	    }

//	    private class ActionMenuItemForwardingListener extends ForwardingListener {
//	        public ActionMenuItemForwardingListener() {
//	            super(MstActionMenuItemView.this);
//	        }
//
//	        @Override
//	        public ListPopupWindow getPopup() {
//	            if (mPopupCallback != null) {
//	                return mPopupCallback.getPopup();
//	            }
//	            return null;
//	        }
//
//	        @Override
//	        protected boolean onForwardingStarted() {
//	            // Call the invoker, then check if the expected popup is showing.
//	            if (mItemInvoker != null && mItemInvoker.invokeItem(mItemData)) {
//	                final ListPopupWindow popup = getPopup();
//	                return popup != null && popup.isShowing();
//	            }
//	            return false;
//	        }
//
//	        @Override
//	        protected boolean onForwardingStopped() {
//	            final ListPopupWindow popup = getPopup();
//	            if (popup != null) {
//	                popup.dismiss();
//	                return true;
//	            }
//	            return false;
//	        }
//	    }

	    public static abstract class PopupCallback {
	        public abstract ListPopupWindow getPopup();
	    }

		@Override
		public void onInitializeAccessibilityNodeInfoInternal(
				AccessibilityNodeInfo info) {
			// TODO Auto-generated method stub
			
		}

}