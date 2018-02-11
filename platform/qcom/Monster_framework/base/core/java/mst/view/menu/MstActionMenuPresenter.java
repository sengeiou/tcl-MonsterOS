package mst.view.menu;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ObjectAnimator;
import android.animation.PropertyValuesHolder;
import android.content.Context;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.graphics.drawable.Drawable;
import android.os.Parcel;
import android.os.Parcelable;
import android.util.Log;
import android.util.SparseArray;
import android.util.SparseBooleanArray;
import android.view.ActionProvider;
import android.view.Gravity;
import android.view.MenuItem;
import android.view.SoundEffectConstants;
import android.view.View;
import android.view.View.MeasureSpec;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.widget.ImageButton;
import android.widget.ListPopupWindow;

import com.mst.R;

public class MstActionMenuPresenter extends MstBaseMenuPresenter  {
	private static final String TAG = "ActionMenuPresenter";
	private static final int ITEM_ANIMATION_DURATION = 150;
	private static final boolean ACTIONBAR_ANIMATIONS_ENABLED = false;

	private OverflowMenuButton mOverflowButton;
	private Drawable mPendingOverflowIcon;
	private boolean mPendingOverflowIconSet;
	private boolean mReserveOverflow;
	private boolean mReserveOverflowSet;
	private int mWidthLimit;
	private int mActionItemWidthLimit;
	private int mMaxItems;
	private boolean mMaxItemsSet;
	private boolean mStrictWidthLimit;
	private boolean mWidthLimitSet;
	private boolean mExpandedActionViewsExclusive;

	private int mMinCellSize;
	
	
	// Group IDs that have been added as actions - used temporarily, allocated
	// here for reuse.
	private final SparseBooleanArray mActionButtonGroups = new SparseBooleanArray();

	private OverflowPopup mOverflowPopup;
	private ActionButtonSubmenu mActionButtonPopup;

	private OpenOverflowRunnable mPostedOpenRunnable;
	private ActionMenuPopupCallback mPopupCallback;

	final PopupPresenterCallback mPopupPresenterCallback = new PopupPresenterCallback();
	int mOpenSubMenuId;

	// These collections are used to store pre- and post-layout information for
	// menu items,
	// which is used to determine appropriate animations to run for changed
	// items.
	private SparseArray<MenuItemLayoutInfo> mPreLayoutItems = new SparseArray<MenuItemLayoutInfo>();
	private SparseArray<MenuItemLayoutInfo> mPostLayoutItems = new SparseArray<MenuItemLayoutInfo>();

	// The list of currently running animations on menu items.
	private List<ItemAnimationInfo> mRunningItemAnimations = new ArrayList<ItemAnimationInfo>();
	private ViewTreeObserver.OnPreDrawListener mItemAnimationPreDrawListener = new ViewTreeObserver.OnPreDrawListener() {
		@Override
		public boolean onPreDraw() {
			computeMenuItemAnimationInfo(false);
			((View) mMenuView).getViewTreeObserver().removeOnPreDrawListener(
					this);
			runItemAnimations();
			return true;
		}
	};
	private View.OnAttachStateChangeListener mAttachStateChangeListener = new View.OnAttachStateChangeListener() {
		@Override
		public void onViewAttachedToWindow(View v) {
		}

		@Override
		public void onViewDetachedFromWindow(View v) {
			((View) mMenuView).getViewTreeObserver().removeOnPreDrawListener(
					mItemAnimationPreDrawListener);
			mPreLayoutItems.clear();
			mPostLayoutItems.clear();
		}
	};

	public MstActionMenuPresenter(Context context) {
		super(context, R.layout.action_menu_layout,
				R.layout.action_menu_item_layout);
	}

	@Override
	public void initForMenu(Context context, MstMenuBuilder menu) {
		super.initForMenu(context, menu);

		final Resources res = context.getResources();
		
		try{
		Class<?> cls = Class.forName("com.android.internal.view.ActionBarPolicy");
		Method getMethod = cls.getDeclaredMethod("get", Context.class);
		Method showsOverFlowMenuButtonMethod = cls.getDeclaredMethod("showsOverflowMenuButton");
		Method getEmbeddedMenuWidthLimitMethod = cls.getDeclaredMethod("getEmbeddedMenuWidthLimit");
		Method getMaxActionButtonsMethod = cls.getDeclaredMethod("getMaxActionButtons");
		Object instance = null;
		if(getMethod != null){
			getMethod.setAccessible(true);
			instance = getMethod.invoke(null, context);
		}
		if(getEmbeddedMenuWidthLimitMethod != null){
			getEmbeddedMenuWidthLimitMethod.setAccessible(true);
			if (!mReserveOverflowSet) {
				mReserveOverflow = (boolean) /*abp.showsOverflowMenuButton()*/getEmbeddedMenuWidthLimitMethod.invoke(instance);
			}
		}
		
		if(getEmbeddedMenuWidthLimitMethod != null){
			getEmbeddedMenuWidthLimitMethod.setAccessible(true);
			if (!mWidthLimitSet) {
				mWidthLimit = (int) getEmbeddedMenuWidthLimitMethod.invoke(instance)/*abp.getEmbeddedMenuWidthLimit()*/;
			}
		}

		// Measure for initial configuration
		if(getMaxActionButtonsMethod != null){
			getMaxActionButtonsMethod.setAccessible(true);
			if (!mMaxItemsSet) {
				mMaxItems = (int) getMaxActionButtonsMethod.invoke(instance)/*abp.getMaxActionButtons()*/;
			}
		}
		}catch(Exception e){
			
		}
		

		int width = mWidthLimit;
		if (mReserveOverflow) {
			if (mOverflowButton == null) {
				mOverflowButton = new OverflowMenuButton(mSystemContext);
				if (mPendingOverflowIconSet) {
					mOverflowButton.setImageDrawable(mPendingOverflowIcon);
					mPendingOverflowIcon = null;
					mPendingOverflowIconSet = false;
				}
				final int spec = MeasureSpec.makeMeasureSpec(0,
						MeasureSpec.UNSPECIFIED);
				mOverflowButton.measure(spec, spec);
			}
			width -= mOverflowButton.getMeasuredWidth();
		} else {
			mOverflowButton = null;
		}

		mActionItemWidthLimit = width;

		mMinCellSize = (int) (MstActionMenuView.MIN_CELL_SIZE * res
				.getDisplayMetrics().density);
	}

	public void onConfigurationChanged(Configuration newConfig) {
		if (!mMaxItemsSet) {
			mMaxItems = mContext.getResources().getInteger(
					R.integer.max_action_buttons);
		}
		if (mMenu != null) {
			mMenu.onItemsChanged(true);
		}
	}

	public void setWidthLimit(int width, boolean strict) {
		mWidthLimit = width;
		mStrictWidthLimit = strict;
		mWidthLimitSet = true;
	}

	public void setReserveOverflow(boolean reserveOverflow) {
		mReserveOverflow = reserveOverflow;
		mReserveOverflowSet = true;
	}

	public void setItemLimit(int itemCount) {
		mMaxItems = itemCount;
		mMaxItemsSet = true;
	}

	public void setExpandedActionViewsExclusive(boolean isExclusive) {
		mExpandedActionViewsExclusive = isExclusive;
	}

	public void setOverflowIcon(Drawable icon) {
		if (mOverflowButton != null) {
			mOverflowButton.setImageDrawable(icon);
		} else {
			mPendingOverflowIconSet = true;
			mPendingOverflowIcon = icon;
		}
	}

	public Drawable getOverflowIcon() {
		if (mOverflowButton != null) {
			return mOverflowButton.getDrawable();
		} else if (mPendingOverflowIconSet) {
			return mPendingOverflowIcon;
		}
		return null;
	}

	@Override
	public MstMenuView getMenuView(ViewGroup root) {
		MstMenuView oldMenuView = mMenuView;
		MstMenuView result = super.getMenuView(root);
		if (oldMenuView != result) {
			((MstActionMenuView) result).setPresenter(this);
			if (oldMenuView != null) {
				((View) oldMenuView)
						.removeOnAttachStateChangeListener(mAttachStateChangeListener);
			}
			((View) result)
					.addOnAttachStateChangeListener(mAttachStateChangeListener);
		}
		return result;
	}

	@Override
	public View getItemView(final MstMenuItemImpl item, View convertView,
			ViewGroup parent) {
		View actionView = item.getActionView();
		if (actionView == null || item.hasCollapsibleActionView()) {
			actionView = super.getItemView(item, convertView, parent);
		}
		actionView.setVisibility(item.isActionViewExpanded() ? View.GONE
				: View.VISIBLE);

		final MstActionMenuView menuParent = (MstActionMenuView) parent;
		final ViewGroup.LayoutParams lp = actionView.getLayoutParams();
		if (!menuParent.checkLayoutParams(lp)) {
			actionView.setLayoutParams(menuParent.generateLayoutParams(lp));
		}
		return actionView;
	}

	@Override
	public void bindItemView(MstMenuItemImpl item, MstMenuView.ItemView itemView) {
		itemView.initialize(item, 0);

		final MstActionMenuView menuView = (MstActionMenuView) mMenuView;
		final MstActionMenuItemView actionItemView = (MstActionMenuItemView) itemView;
		actionItemView.setItemInvoker(menuView);

		if (mPopupCallback == null) {
			mPopupCallback = new ActionMenuPopupCallback();
		}
		actionItemView.setPopupCallback(mPopupCallback);
	}

	@Override
	public boolean shouldIncludeItem(int childIndex, MstMenuItemImpl item) {
		return item.isActionButton();
	}

	/**
	 * Store layout information about current items in the menu. This is stored
	 * for both pre- and post-layout phases and compared in runItemAnimations()
	 * to determine the animations that need to be run on any item changes.
	 *
	 * @param preLayout
	 *            Whether this is being called in the pre-layout phase. This is
	 *            passed into the MenuItemLayoutInfo structure to store the
	 *            appropriate position values.
	 */
	private void computeMenuItemAnimationInfo(boolean preLayout) {
		final ViewGroup menuView = (ViewGroup) mMenuView;
		final int count = menuView.getChildCount();
		SparseArray items = preLayout ? mPreLayoutItems : mPostLayoutItems;
		for (int i = 0; i < count; ++i) {
			View child = menuView.getChildAt(i);
			final int id = child.getId();
			if (id > 0 && child.getWidth() != 0 && child.getHeight() != 0) {
				MenuItemLayoutInfo info = new MenuItemLayoutInfo(child,
						preLayout);
				items.put(id, info);
			}
		}
	}

	/**
	 * This method is called once both the pre-layout and post-layout steps have
	 * happened. It figures out which views are new (didn't exist prior to
	 * layout), gone (existed pre-layout, but are now gone), or changed (exist
	 * in both, but in a different location) and runs appropriate animations on
	 * those views. Items are tracked by ids, since the underlying views that
	 * represent items pre- and post-layout may be different.
	 */
	private void runItemAnimations() {
		for (int i = 0; i < mPreLayoutItems.size(); ++i) {
			int id = mPreLayoutItems.keyAt(i);
			final MenuItemLayoutInfo menuItemLayoutInfoPre = mPreLayoutItems
					.get(id);
			final int postLayoutIndex = mPostLayoutItems.indexOfKey(id);
			if (postLayoutIndex >= 0) {
				// item exists pre and post: see if it's changed
				final MenuItemLayoutInfo menuItemLayoutInfoPost = mPostLayoutItems
						.valueAt(postLayoutIndex);
				PropertyValuesHolder pvhX = null;
				PropertyValuesHolder pvhY = null;
				if (menuItemLayoutInfoPre.left != menuItemLayoutInfoPost.left) {
					pvhX = PropertyValuesHolder
							.ofFloat(
									View.TRANSLATION_X,
									(menuItemLayoutInfoPre.left - menuItemLayoutInfoPost.left),
									0);
				}
				if (menuItemLayoutInfoPre.top != menuItemLayoutInfoPost.top) {
					pvhY = PropertyValuesHolder.ofFloat(View.TRANSLATION_Y,
							menuItemLayoutInfoPre.top
									- menuItemLayoutInfoPost.top, 0);
				}
				if (pvhX != null || pvhY != null) {
					for (int j = 0; j < mRunningItemAnimations.size(); ++j) {
						ItemAnimationInfo oldInfo = mRunningItemAnimations
								.get(j);
						if (oldInfo.id == id
								&& oldInfo.animType == ItemAnimationInfo.MOVE) {
							oldInfo.animator.cancel();
						}
					}
					ObjectAnimator anim;
					if (pvhX != null) {
						if (pvhY != null) {
							anim = ObjectAnimator.ofPropertyValuesHolder(
									menuItemLayoutInfoPost.view, pvhX, pvhY);
						} else {
							anim = ObjectAnimator.ofPropertyValuesHolder(
									menuItemLayoutInfoPost.view, pvhX);
						}
					} else {
						anim = ObjectAnimator.ofPropertyValuesHolder(
								menuItemLayoutInfoPost.view, pvhY);
					}
					anim.setDuration(ITEM_ANIMATION_DURATION);
					anim.start();
					ItemAnimationInfo info = new ItemAnimationInfo(id,
							menuItemLayoutInfoPost, anim,
							ItemAnimationInfo.MOVE);
					mRunningItemAnimations.add(info);
					anim.addListener(new AnimatorListenerAdapter() {
						@Override
						public void onAnimationEnd(Animator animation) {
							for (int j = 0; j < mRunningItemAnimations.size(); ++j) {
								if (mRunningItemAnimations.get(j).animator == animation) {
									mRunningItemAnimations.remove(j);
									break;
								}
							}
						}
					});
				}
				mPostLayoutItems.remove(id);
			} else {
				// item used to be there, is now gone
				float oldAlpha = 1;
				for (int j = 0; j < mRunningItemAnimations.size(); ++j) {
					ItemAnimationInfo oldInfo = mRunningItemAnimations.get(j);
					if (oldInfo.id == id
							&& oldInfo.animType == ItemAnimationInfo.FADE_IN) {
						oldAlpha = oldInfo.menuItemLayoutInfo.view.getAlpha();
						oldInfo.animator.cancel();
					}
				}
				ObjectAnimator anim = ObjectAnimator.ofFloat(
						menuItemLayoutInfoPre.view, View.ALPHA, oldAlpha, 0);
				// Re-using the view from pre-layout assumes no view recycling
				((ViewGroup) mMenuView).getOverlay().add(
						menuItemLayoutInfoPre.view);
				anim.setDuration(ITEM_ANIMATION_DURATION);
				anim.start();
				ItemAnimationInfo info = new ItemAnimationInfo(id,
						menuItemLayoutInfoPre, anim, ItemAnimationInfo.FADE_OUT);
				mRunningItemAnimations.add(info);
				anim.addListener(new AnimatorListenerAdapter() {
					@Override
					public void onAnimationEnd(Animator animation) {
						for (int j = 0; j < mRunningItemAnimations.size(); ++j) {
							if (mRunningItemAnimations.get(j).animator == animation) {
								mRunningItemAnimations.remove(j);
								break;
							}
						}
						((ViewGroup) mMenuView).getOverlay().remove(
								menuItemLayoutInfoPre.view);
					}
				});
			}
		}
		for (int i = 0; i < mPostLayoutItems.size(); ++i) {
			int id = mPostLayoutItems.keyAt(i);
			final int postLayoutIndex = mPostLayoutItems.indexOfKey(id);
			if (postLayoutIndex >= 0) {
				// item is new
				final MenuItemLayoutInfo menuItemLayoutInfo = mPostLayoutItems
						.valueAt(postLayoutIndex);
				float oldAlpha = 0;
				for (int j = 0; j < mRunningItemAnimations.size(); ++j) {
					ItemAnimationInfo oldInfo = mRunningItemAnimations.get(j);
					if (oldInfo.id == id
							&& oldInfo.animType == ItemAnimationInfo.FADE_OUT) {
						oldAlpha = oldInfo.menuItemLayoutInfo.view.getAlpha();
						oldInfo.animator.cancel();
					}
				}
				ObjectAnimator anim = ObjectAnimator.ofFloat(
						menuItemLayoutInfo.view, View.ALPHA, oldAlpha, 1);
				anim.start();
				anim.setDuration(ITEM_ANIMATION_DURATION);
				ItemAnimationInfo info = new ItemAnimationInfo(id,
						menuItemLayoutInfo, anim, ItemAnimationInfo.FADE_IN);
				mRunningItemAnimations.add(info);
				anim.addListener(new AnimatorListenerAdapter() {
					@Override
					public void onAnimationEnd(Animator animation) {
						for (int j = 0; j < mRunningItemAnimations.size(); ++j) {
							if (mRunningItemAnimations.get(j).animator == animation) {
								mRunningItemAnimations.remove(j);
								break;
							}
						}
					}
				});
			}
		}
		mPreLayoutItems.clear();
		mPostLayoutItems.clear();
	}

	/**
	 * Gets position/existence information on menu items before and after
	 * layout, which is then fed into runItemAnimations()
	 */
	private void setupItemAnimations() {
		computeMenuItemAnimationInfo(true);
		((View) mMenuView).getViewTreeObserver().addOnPreDrawListener(
				mItemAnimationPreDrawListener);
	}

	@Override
	public void updateMenuView(boolean cleared) {
		final ViewGroup menuViewParent = (ViewGroup) ((View) mMenuView)
				.getParent();
		if (menuViewParent != null && ACTIONBAR_ANIMATIONS_ENABLED) {
			setupItemAnimations();
		}
		super.updateMenuView(cleared);

		((View) mMenuView).requestLayout();

		if (mMenu != null) {
			final ArrayList<MstMenuItemImpl> actionItems = mMenu.getActionItems();
			final int count = actionItems.size();
			for (int i = 0; i < count; i++) {
				final ActionProvider provider = actionItems.get(i)
						.getActionProvider();
//				if (provider != null) {
//					provider.setSubUiVisibilityListener(this);
//				}
			}
		}

		final ArrayList<MstMenuItemImpl> nonActionItems = mMenu != null ? mMenu
				.getNonActionItems() : null;

		boolean hasOverflow = false;
		if (mReserveOverflow && nonActionItems != null) {
			final int count = nonActionItems.size();
			if (count == 1) {
				hasOverflow = !nonActionItems.get(0).isActionViewExpanded();
			} else {
				hasOverflow = count > 0;
			}
		}

		if (hasOverflow) {
			if (mOverflowButton == null) {
				mOverflowButton = new OverflowMenuButton(mSystemContext);
			}
			ViewGroup parent = (ViewGroup) mOverflowButton.getParent();
			if (parent != mMenuView) {
				if (parent != null) {
					parent.removeView(mOverflowButton);
				}
				MstActionMenuView menuView = (MstActionMenuView) mMenuView;
				menuView.addView(mOverflowButton,
						menuView.generateOverflowButtonLayoutParams());
			}
		} else if (mOverflowButton != null
				&& mOverflowButton.getParent() == mMenuView) {
			((ViewGroup) mMenuView).removeView(mOverflowButton);
		}

		((MstActionMenuView) mMenuView).setOverflowReserved(mReserveOverflow);
	}

	@Override
	public boolean filterLeftoverView(ViewGroup parent, int childIndex) {
		if (parent.getChildAt(childIndex) == mOverflowButton)
			return false;
		return super.filterLeftoverView(parent, childIndex);
	}

	public boolean onSubMenuSelected(MstSubMenuBuilder subMenu) {
		if (!subMenu.hasVisibleItems())
			return false;

		MstSubMenuBuilder topSubMenu = subMenu;
		while (topSubMenu.getParentMenu() != mMenu) {
			topSubMenu = (MstSubMenuBuilder) topSubMenu.getParentMenu();
		}
		View anchor = findViewForItem(topSubMenu.getItem());
		if (anchor == null) {
			if (mOverflowButton == null)
				return false;
			anchor = mOverflowButton;
		}

		mOpenSubMenuId = subMenu.getItem().getItemId();
		mActionButtonPopup = new ActionButtonSubmenu(mContext, subMenu);
		mActionButtonPopup.setAnchorView(anchor);
		mActionButtonPopup.show();
		super.onSubMenuSelected(subMenu);
		return true;
	}

	private View findViewForItem(MenuItem item) {
		final ViewGroup parent = (ViewGroup) mMenuView;
		if (parent == null)
			return null;

		final int count = parent.getChildCount();
		for (int i = 0; i < count; i++) {
			final View child = parent.getChildAt(i);
			if (child instanceof MstMenuView.ItemView
					&& ((MstMenuView.ItemView) child).getItemData() == item) {
				return child;
			}
		}
		return null;
	}

	/**
	 * Display the overflow menu if one is present.
	 * 
	 * @return true if the overflow menu was shown, false otherwise.
	 */
	public boolean showOverflowMenu() {
		if (mReserveOverflow && !isOverflowMenuShowing() && mMenu != null
				&& mMenuView != null && mPostedOpenRunnable == null
				&& !mMenu.getNonActionItems().isEmpty()) {
			OverflowPopup popup = new OverflowPopup(mContext, mMenu,
					mOverflowButton, true);
			mPostedOpenRunnable = new OpenOverflowRunnable(popup);
			// Post this for later; we might still need a layout for the anchor
			// to be right.
			((View) mMenuView).post(mPostedOpenRunnable);

			// ActionMenuPresenter uses null as a callback argument here
			// to indicate overflow is opening.
			super.onSubMenuSelected(null);

			return true;
		}
		return false;
	}

	/**
	 * Hide the overflow menu if it is currently showing.
	 *
	 * @return true if the overflow menu was hidden, false otherwise.
	 */
	public boolean hideOverflowMenu() {
		if (mPostedOpenRunnable != null && mMenuView != null) {
			((View) mMenuView).removeCallbacks(mPostedOpenRunnable);
			mPostedOpenRunnable = null;
			return true;
		}

		MstMenuPopupHelper popup = mOverflowPopup;
		if (popup != null) {
			popup.dismiss();
			return true;
		}
		return false;
	}

	/**
	 * Dismiss all popup menus - overflow and submenus.
	 * 
	 * @return true if popups were dismissed, false otherwise. (This can be
	 *         because none were open.)
	 */
	public boolean dismissPopupMenus() {
		boolean result = hideOverflowMenu();
		result |= hideSubMenus();
		return result;
	}

	/**
	 * Dismiss all submenu popups.
	 *
	 * @return true if popups were dismissed, false otherwise. (This can be
	 *         because none were open.)
	 */
	public boolean hideSubMenus() {
		if (mActionButtonPopup != null) {
			mActionButtonPopup.dismiss();
			return true;
		}
		return false;
	}

	/**
	 * @return true if the overflow menu is currently showing
	 */
	public boolean isOverflowMenuShowing() {
		return mOverflowPopup != null && mOverflowPopup.isShowing();
	}

	public boolean isOverflowMenuShowPending() {
		return mPostedOpenRunnable != null || isOverflowMenuShowing();
	}

	/**
	 * @return true if space has been reserved in the action menu for an
	 *         overflow item.
	 */
	public boolean isOverflowReserved() {
		return mReserveOverflow;
	}

	public boolean flagActionItems() {
		final ArrayList<MstMenuItemImpl> visibleItems = mMenu.getVisibleItems();
		final int itemsSize = visibleItems.size();
		int maxActions = mMaxItems;
		int widthLimit = mActionItemWidthLimit;
		final int querySpec = MeasureSpec.makeMeasureSpec(0,
				MeasureSpec.UNSPECIFIED);
		final ViewGroup parent = (ViewGroup) mMenuView;

		int requiredItems = 0;
		int requestedItems = 0;
		int firstActionWidth = 0;
		boolean hasOverflow = false;
		for (int i = 0; i < itemsSize; i++) {
			MstMenuItemImpl item = visibleItems.get(i);
			if (item.requiresActionButton()) {
				requiredItems++;
			} else if (item.requestsActionButton()) {
				requestedItems++;
			} else {
				hasOverflow = true;
			}
			if (mExpandedActionViewsExclusive && item.isActionViewExpanded()) {
				// Overflow everything if we have an expanded action view and
				// we're
				// space constrained.
				maxActions = 0;
			}
		}

		// Reserve a spot for the overflow item if needed.
		if (mReserveOverflow
				&& (hasOverflow || requiredItems + requestedItems > maxActions)) {
			maxActions--;
		}
		maxActions -= requiredItems;

		final SparseBooleanArray seenGroups = mActionButtonGroups;
		seenGroups.clear();

		int cellSize = 0;
		int cellsRemaining = 0;
		if (mStrictWidthLimit) {
			cellsRemaining = widthLimit / mMinCellSize;
			final int cellSizeRemaining = widthLimit % mMinCellSize;
			cellSize = mMinCellSize + cellSizeRemaining / cellsRemaining;
		}

		// Flag as many more requested items as will fit.
		for (int i = 0; i < itemsSize; i++) {
			MstMenuItemImpl item = visibleItems.get(i);

			if (item.requiresActionButton()) {
				View v = getItemView(item, null, parent);
				if (mStrictWidthLimit) {
					cellsRemaining -= MstActionMenuView.measureChildForCells(v,
							cellSize, cellsRemaining, querySpec, 0);
				} else {
					v.measure(querySpec, querySpec);
				}
				final int measuredWidth = v.getMeasuredWidth();
				widthLimit -= measuredWidth;
				if (firstActionWidth == 0) {
					firstActionWidth = measuredWidth;
				}
				final int groupId = item.getGroupId();
				if (groupId != 0) {
					seenGroups.put(groupId, true);
				}
				item.setIsActionButton(true);
			} else if (item.requestsActionButton()) {
				// Items in a group with other items that already have an action
				// slot
				// can break the max actions rule, but not the width limit.
				final int groupId = item.getGroupId();
				final boolean inGroup = seenGroups.get(groupId);
				boolean isAction = (maxActions > 0 || inGroup)
						&& widthLimit > 0
						&& (!mStrictWidthLimit || cellsRemaining > 0);

				if (isAction) {
					View v = getItemView(item, null, parent);
					if (mStrictWidthLimit) {
						final int cells = MstActionMenuView.measureChildForCells(
								v, cellSize, cellsRemaining, querySpec, 0);
						cellsRemaining -= cells;
						if (cells == 0) {
							isAction = false;
						}
					} else {
						v.measure(querySpec, querySpec);
					}
					final int measuredWidth = v.getMeasuredWidth();
					widthLimit -= measuredWidth;
					if (firstActionWidth == 0) {
						firstActionWidth = measuredWidth;
					}

					if (mStrictWidthLimit) {
						isAction &= widthLimit >= 0;
					} else {
						// Did this push the entire first item past the limit?
						isAction &= widthLimit + firstActionWidth > 0;
					}
				}

				if (isAction && groupId != 0) {
					seenGroups.put(groupId, true);
				} else if (inGroup) {
					// We broke the width limit. Demote the whole group, they
					// all overflow now.
					seenGroups.put(groupId, false);
					for (int j = 0; j < i; j++) {
						MstMenuItemImpl areYouMyGroupie = visibleItems.get(j);
						if (areYouMyGroupie.getGroupId() == groupId) {
							// Give back the action slot
							if (areYouMyGroupie.isActionButton())
								maxActions++;
							areYouMyGroupie.setIsActionButton(false);
						}
					}
				}

				if (isAction)
					maxActions--;

				item.setIsActionButton(isAction);
			} else {
				// Neither requires nor requests an action button.
				item.setIsActionButton(false);
			}
		}
		return true;
	}

	@Override
	public void onCloseMenu(MstMenuBuilder menu, boolean allMenusAreClosing) {
		dismissPopupMenus();
		super.onCloseMenu(menu, allMenusAreClosing);
	}

	@Override
	public Parcelable onSaveInstanceState() {
		SavedState state = new SavedState();
		state.openSubMenuId = mOpenSubMenuId;
		return state;
	}

	@Override
	public void onRestoreInstanceState(Parcelable state) {
		SavedState saved = (SavedState) state;
		if (saved.openSubMenuId > 0) {
			MenuItem item = mMenu.findItem(saved.openSubMenuId);
			if (item != null) {
				MstSubMenuBuilder subMenu = (MstSubMenuBuilder) item.getSubMenu();
				onSubMenuSelected(subMenu);
			}
		}
	}

//	@Override
//	public void onSubUiVisibilityChanged(boolean isVisible) {
//		if (isVisible) {
//			// Not a submenu, but treat it like one.
//			super.onSubMenuSelected(null);
//		} else {
//			mMenu.close(false);
//		}
//	}

	public void setMenuView(MstActionMenuView menuView) {
		if (menuView != mMenuView) {
			if (mMenuView != null) {
				((View) mMenuView)
						.removeOnAttachStateChangeListener(mAttachStateChangeListener);
			}
			mMenuView = menuView;
			menuView.initialize(mMenu);
			menuView.addOnAttachStateChangeListener(mAttachStateChangeListener);
		}
	}

	private static class SavedState implements Parcelable {
		public int openSubMenuId;

		SavedState() {
		}

		SavedState(Parcel in) {
			openSubMenuId = in.readInt();
		}

		@Override
		public int describeContents() {
			return 0;
		}

		@Override
		public void writeToParcel(Parcel dest, int flags) {
			dest.writeInt(openSubMenuId);
		}

		public static final Parcelable.Creator<SavedState> CREATOR = new Parcelable.Creator<SavedState>() {
			public SavedState createFromParcel(Parcel in) {
				return new SavedState(in);
			}

			public SavedState[] newArray(int size) {
				return new SavedState[size];
			}
		};
	}

	private class OverflowMenuButton extends ImageButton implements
	MstActionMenuView.ActionMenuChildView {
		private final float[] mTempPts = new float[2];

		public OverflowMenuButton(Context context) {
			super(context, null,
					android.R.attr.actionOverflowButtonStyle);

			setClickable(true);
			setFocusable(true);
			setVisibility(VISIBLE);
			setEnabled(true);

//			setOnTouchListener(new ForwardingListener(this) {
//				@Override
//				public ListPopupWindow getPopup() {
//					if (mOverflowPopup == null) {
//						return null;
//					}
//
//					return mOverflowPopup.getPopup();
//				}
//
//				@Override
//				public boolean onForwardingStarted() {
//					showOverflowMenu();
//					return true;
//				}
//
//				@Override
//				public boolean onForwardingStopped() {
//					// Displaying the popup occurs asynchronously, so wait for
//					// the runnable to finish before deciding whether to stop
//					// forwarding.
//					if (mPostedOpenRunnable != null) {
//						return false;
//					}
//
//					hideOverflowMenu();
//					return true;
//				}
//			});
		}

		@Override
		public boolean performClick() {
			if (super.performClick()) {
				return true;
			}

			playSoundEffect(SoundEffectConstants.CLICK);
			showOverflowMenu();
			return true;
		}

		@Override
		public boolean needsDividerBefore() {
			return false;
		}

		@Override
		public boolean needsDividerAfter() {
			return false;
		}

		

		@Override
		protected boolean setFrame(int l, int t, int r, int b) {
			final boolean changed = super.setFrame(l, t, r, b);

			// Set up the hotspot bounds to square and centered on the image.
			final Drawable d = getDrawable();
			final Drawable bg = getBackground();
			if (d != null && bg != null) {
				final int width = getWidth();
				final int height = getHeight();
				final int halfEdge = Math.max(width, height) / 2;
				final int offsetX = getPaddingLeft() - getPaddingRight();
				final int offsetY = getPaddingTop() - getPaddingBottom();
				final int centerX = (width + offsetX) / 2;
				final int centerY = (height + offsetY) / 2;
				bg.setHotspotBounds(centerX - halfEdge, centerY - halfEdge,
						centerX + halfEdge, centerY + halfEdge);
			}

			return changed;
		}
	}

	private class OverflowPopup extends MstMenuPopupHelper {
		public OverflowPopup(Context context, MstMenuBuilder menu,
				View anchorView, boolean overflowOnly) {
			super(context, menu, anchorView, overflowOnly,
					android.R.attr.actionOverflowMenuStyle);
			setGravity(Gravity.END);
			setCallback(mPopupPresenterCallback);
		}

		@Override
		public void onDismiss() {
			super.onDismiss();
			mMenu.close();
			mOverflowPopup = null;
		}
	}

	private class ActionButtonSubmenu extends MstMenuPopupHelper {
		private MstSubMenuBuilder mSubMenu;

		public ActionButtonSubmenu(Context context, MstSubMenuBuilder subMenu) {
			super(context, subMenu, null, false,
					android.R.attr.actionOverflowMenuStyle);
			mSubMenu = subMenu;

			MstMenuItemImpl item = (MstMenuItemImpl) subMenu.getItem();
			if (!item.isActionButton()) {
				// Give a reasonable anchor to nested submenus.
				setAnchorView(mOverflowButton == null ? (View) mMenuView
						: mOverflowButton);
			}

			setCallback(mPopupPresenterCallback);

			boolean preserveIconSpacing = false;
			final int count = subMenu.size();
			for (int i = 0; i < count; i++) {
				MenuItem childItem = subMenu.getItem(i);
				if (childItem.isVisible() && childItem.getIcon() != null) {
					preserveIconSpacing = true;
					break;
				}
			}
			setForceShowIcon(preserveIconSpacing);
		}

		@Override
		public void onDismiss() {
			super.onDismiss();
			mActionButtonPopup = null;
			mOpenSubMenuId = 0;
		}
	}

	private class PopupPresenterCallback implements Callback {

		@Override
		public boolean onOpenSubMenu(MstMenuBuilder subMenu) {
			if (subMenu == null)
				return false;

			mOpenSubMenuId = ((MstSubMenuBuilder) subMenu).getItem().getItemId();
			final Callback cb = getCallback();
			return cb != null ? cb.onOpenSubMenu(subMenu) : false;
		}

		@Override
		public void onCloseMenu(MstMenuBuilder menu, boolean allMenusAreClosing) {
			if (menu instanceof MstSubMenuBuilder) {
				((MstSubMenuBuilder) menu).getRootMenu().close(false);
			}
			final Callback cb = getCallback();
			if (cb != null) {
				cb.onCloseMenu(menu, allMenusAreClosing);
			}
		}
	}

	private class OpenOverflowRunnable implements Runnable {
		private OverflowPopup mPopup;

		public OpenOverflowRunnable(OverflowPopup popup) {
			mPopup = popup;
		}

		public void run() {
			mMenu.changeMenuMode();
			final View menuView = (View) mMenuView;
			if (menuView != null && menuView.getWindowToken() != null
					&& mPopup.tryShow()) {
				mOverflowPopup = mPopup;
			}
			mPostedOpenRunnable = null;
		}
	}

	private class ActionMenuPopupCallback extends
	MstActionMenuItemView.PopupCallback {
		@Override
		public ListPopupWindow getPopup() {
			return mActionButtonPopup != null ? mActionButtonPopup.getPopup()
					: null;
		}
	}

	/**
	 * This class holds layout information for a menu item. This is used to
	 * determine pre- and post-layout information about menu items, which will
	 * then be used to determine appropriate item animations.
	 */
	private static class MenuItemLayoutInfo {
		View view;
		int left;
		int top;

		MenuItemLayoutInfo(View view, boolean preLayout) {
			left = view.getLeft();
			top = view.getTop();
			if (preLayout) {
				// We track translation for pre-layout because a view might be
				// mid-animation
				// and we need this information to know where to animate from
				left += view.getTranslationX();
				top += view.getTranslationY();
			}
			this.view = view;
		}
	}

	/**
	 * This class is used to store information about currently-running item
	 * animations. This is used when new animations are scheduled to determine
	 * whether any existing animations need to be canceled, based on whether the
	 * running animations overlap with any new animations. For example, if an
	 * item is currently animating from location A to B and another change
	 * dictates that it be animated to C, then the current A-B animation will be
	 * canceled and a new animation to C will be started.
	 */
	private static class ItemAnimationInfo {
		int id;
		MenuItemLayoutInfo menuItemLayoutInfo;
		Animator animator;
		int animType;
		static final int MOVE = 0;
		static final int FADE_IN = 1;
		static final int FADE_OUT = 2;

		ItemAnimationInfo(int id, MenuItemLayoutInfo info, Animator anim,
				int animType) {
			this.id = id;
			menuItemLayoutInfo = info;
			animator = anim;
			this.animType = animType;
		}
	}
}
