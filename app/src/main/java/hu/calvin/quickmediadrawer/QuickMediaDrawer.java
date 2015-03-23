package hu.calvin.quickmediadrawer;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Rect;
import android.support.v4.view.ViewCompat;
import android.support.v4.widget.ViewDragHelper;
import android.util.AttributeSet;
import android.view.View;
import android.view.ViewGroup;

public class QuickMediaDrawer extends ViewGroup implements QuickCamera.Callback {
    //TODO: Make intdef to avoid dex method limit
    public static enum DrawerState {COLLAPSED, HALF_EXPANDED, FULL_EXPANDED, DRAGGING};
    private boolean firstSetup;
    private DrawerState drawerState;
    private View coverView;
    private int slideRange, quickCameraHeight;
    private float slideOffset;
    private QuickMediaDrawerListener listener;
    private final ViewDragHelper dragHelper;
    private final View quickCamera;
    private final View controls;
    private final Rect mTmpRect = new Rect();

    public QuickMediaDrawer(Context context) {
        this(context, null);
    }

    public QuickMediaDrawer(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public QuickMediaDrawer(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
        firstSetup = true;
        drawerState = DrawerState.COLLAPSED;
        dragHelper = ViewDragHelper.create(this, 1f, new ViewDragHelperCallback());
        quickCamera = new QuickCamera(context, null);
        controls = inflate(getContext(), R.layout.quick_camera_controls, null);
        addView(quickCamera);
        addView(controls);
    }

    @Override
    public void displayCameraInUseCopy(boolean inUse) {

    }

    @Override
    protected void onLayout(boolean changed, int l, int t, int r, int b) {
        final int paddingLeft = getPaddingLeft();
        final int paddingTop = getPaddingTop();

        final int childCount = getChildCount();

        if (firstSetup) {
            /*switch (drawerState) {
                case FULL_EXPANDED:
                    slideOffset = 1.0f;
                    break;
                case HALF_EXPANDED:
                    slideOffset = 0.5f;
                    break;
                case COLLAPSED:
                    //int newTop = computePanelTopPosition(0.0f);
                    //slideOffset = computeSlideOffset(newTop);
                    slideOffset = 0.f;
                    break;
            }*/
        }


        for (int i = 0; i < childCount; i++) {
            final View child = getChildAt(i);

            // Always layout the sliding view on the first layout
            /*if (child.getVisibility() == GONE && (i == 0 || mFirstLayout)) {
                continue;
            }*/

            final int childHeight = child.getMeasuredHeight();
            int childTop = paddingTop;
            int childBottom;

            if (child == quickCamera) {
                childTop = computePanelTopPosition(slideOffset);
                childBottom = childTop + childHeight;
            } else if (child == controls){
                childBottom = childTop + childHeight;
            } else {
                childBottom = computePanelTopPosition(slideOffset);
                childTop = childBottom - childHeight;
            }
            final int childLeft = paddingLeft;
            final int childRight = childLeft + child.getMeasuredWidth();

            child.layout(childLeft, childTop, childRight, childBottom);
        }

        firstSetup = false;
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        final int widthMode = MeasureSpec.getMode(widthMeasureSpec);
        final int widthSize = MeasureSpec.getSize(widthMeasureSpec);
        final int heightMode = MeasureSpec.getMode(heightMeasureSpec);
        final int heightSize = MeasureSpec.getSize(heightMeasureSpec);

        if (widthMode != MeasureSpec.EXACTLY) {
            throw new IllegalStateException("Width must have an exact value or MATCH_PARENT");
        } else if (heightMode != MeasureSpec.EXACTLY) {
            throw new IllegalStateException("Height must have an exact value or MATCH_PARENT");
        }

        final int childCount = getChildCount();
        if (childCount != 3)
            throw new IllegalStateException("QuickMediaDrawer layouts may only have 1 child.");

        coverView = getChildAt(2);

        int layoutHeight = heightSize - getPaddingTop() - getPaddingBottom();

        // First pass. Measure based on child LayoutParams width/height.
        for (int i = 0; i < childCount; i++) {
            final View child = getChildAt(i);
            final LayoutParams lp = child.getLayoutParams();

            // We always measure the sliding panel in order to know it's height (needed for show panel)
            if (child.getVisibility() == GONE && i == 0) {
                continue;
            }

            int height = layoutHeight;
            /*if (child == mMainView && !mOverlayContent && mSlideState != PanelState.HIDDEN) {
                height -= mPanelHeight;
            }*/

            int childWidthSpec;
            switch (lp.width) {
                case LayoutParams.WRAP_CONTENT:
                    childWidthSpec = MeasureSpec.makeMeasureSpec(widthSize, MeasureSpec.AT_MOST);
                    break;
                case LayoutParams.MATCH_PARENT:
                    childWidthSpec = MeasureSpec.makeMeasureSpec(widthSize, MeasureSpec.EXACTLY);
                    break;
                default:
                    childWidthSpec = MeasureSpec.makeMeasureSpec(lp.width, MeasureSpec.EXACTLY);
                    break;
            }

            int childHeightSpec;
            switch (lp.height) {
                case LayoutParams.WRAP_CONTENT:
                    childHeightSpec = MeasureSpec.makeMeasureSpec(height, MeasureSpec.AT_MOST);
                    break;
                case LayoutParams.MATCH_PARENT:
                    childHeightSpec = MeasureSpec.makeMeasureSpec(height, MeasureSpec.EXACTLY);
                    break;
                default:
                    childHeightSpec = MeasureSpec.makeMeasureSpec(lp.height, MeasureSpec.EXACTLY);
                    break;
            }

            child.measure(childWidthSpec, childHeightSpec);

            if (child == coverView) {
                slideRange = getMeasuredHeight();
            }
        }

        setMeasuredDimension(widthSize, heightSize);
    }


    @Override
    protected boolean drawChild(Canvas canvas, View child, long drawingTime) {
        boolean result;
        final int save = canvas.save(Canvas.CLIP_SAVE_FLAG);

        canvas.getClipBounds(mTmpRect);
        if (coverView == child) {
            mTmpRect.bottom = Math.min(mTmpRect.bottom, child.getBottom());
            canvas.clipRect(mTmpRect);
        } else {
            canvas.getClipBounds(mTmpRect);
            mTmpRect.top = Math.max(mTmpRect.top, coverView.getBottom());
            canvas.clipRect(mTmpRect);
        }
        result = super.drawChild(canvas, child, drawingTime);
        canvas.restoreToCount(save);

        return result;
    }

    @Override
    public void draw(Canvas c) {
        super.draw(c);
    }

    @Override
    public void computeScroll() {
        if (dragHelper != null && dragHelper.continueSettling(true)) {
            if (!isEnabled()) {
                dragHelper.abort();
                return;
            }
            ViewCompat.postInvalidateOnAnimation(this);
        }
    }

    public DrawerState getDrawerState() {
        return drawerState;
    }

    public void setDrawerState(DrawerState drawerState) {
        this.drawerState = drawerState;
        switch (drawerState) {
            case COLLAPSED:
                smoothSlideTo(0, 0);
                //smoothSlideCoverViewTo(0,0);
                break;
            case HALF_EXPANDED:
                smoothSlideTo(.5f, 0);
                //smoothSlideCoverViewTo(-.5f, 0);
                break;
            case FULL_EXPANDED:
                smoothSlideTo(1.f, 0);
                break;
        }
        requestLayout();
    }

    public void setQuickMediaDrawerListener(QuickMediaDrawerListener listener) {
        this.listener = listener;
    }

    public interface QuickMediaDrawerListener {
        public void onPanelCollapsed();
        public void onPanelExpanded();
        public void onPanelHalfExpanded();
    }

    private class ViewDragHelperCallback extends ViewDragHelper.Callback {

        @Override
        public boolean tryCaptureView(View child, int pointerId) {
            return child == quickCamera;
        }

        @Override
        public void onViewDragStateChanged(int state) {
            if (dragHelper.getViewDragState() == ViewDragHelper.STATE_IDLE) {
                //slideOffset = computeSlideOffset(quickCamera.getTop());
            }
        }

        @Override
        public void onViewCaptured(View capturedChild, int activePointerId) {
        }

        @Override
        public void onViewPositionChanged(View changedView, int left, int top, int dx, int dy) {
            invalidate();
        }

        @Override
        public void onViewReleased(View releasedChild, float xvel, float yvel) {
            int target = 0;
            target = computePanelTopPosition(1.0f);
            dragHelper.settleCapturedViewAt(releasedChild.getLeft(), target);
            invalidate();
        }

        @Override
        public int getViewVerticalDragRange(View child) {
            return slideRange;
        }

        @Override
        public int clampViewPositionVertical(View child, int top, int dy) {
            final int collapsedTop = computePanelTopPosition(0.f);
            final int expandedTop = computePanelTopPosition(1.0f);
            return Math.min(Math.max(top, expandedTop), collapsedTop);
        }
    }

    private int computePanelTopPosition(float slideOffset) {
        int slidingViewHeight = quickCamera != null ? quickCamera.getMeasuredHeight() : 0;
        int slidePixelOffset = (int) (slideOffset * slideRange);
        // Compute the top of the panel if its collapsed
        return getMeasuredHeight() - getPaddingBottom() - slidePixelOffset;
    }

    void smoothSlideTo(float slideOffset, int velocity) {
        this.slideOffset = slideOffset;
        int panelTop = computePanelTopPosition(slideOffset);
        if (dragHelper.smoothSlideViewTo(coverView, coverView.getLeft(), panelTop - coverView.getHeight())
                && dragHelper.smoothSlideViewTo(quickCamera, quickCamera.getLeft(), panelTop))
            ViewCompat.postInvalidateOnAnimation(this);
    }

    private float computeSlideOffset(int topPosition) {
        // Compute the panel top position if the panel is collapsed (offset 0)
        final int topBoundCollapsed = computePanelTopPosition(0);

        // Determine the new slide offset based on the collapsed top position and the new required
        // top position
        return (float) (topBoundCollapsed - topPosition) / slideRange;
    }

}
