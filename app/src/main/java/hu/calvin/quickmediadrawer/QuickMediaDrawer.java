package hu.calvin.quickmediadrawer;

import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.Canvas;
import android.graphics.Rect;
import android.support.v4.view.MotionEventCompat;
import android.support.v4.view.ViewCompat;
import android.support.v4.widget.ViewDragHelper;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;

public class QuickMediaDrawer extends ViewGroup implements QuickCamera.Callback {
    //TODO: Make intdef to avoid dex method limit
    public static enum DrawerState {COLLAPSED, HALF_EXPANDED, FULL_EXPANDED, DRAGGING};
    private boolean firstSetup;
    private DrawerState drawerState;
    private View coverView;
    private int slideRange;
    private float slideOffset;
    private QuickMediaDrawerListener listener;
    private final ViewDragHelper dragHelper;
    private final View quickCamera;
    private final View controls;
    private final Rect mTmpRect = new Rect();
    private float initialMotionX, initialMotionY;
    private final float anchorPoint;
    private boolean noDrag;

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
        anchorPoint = 0.5f;
        noDrag = false;
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
                childBottom = getMeasuredHeight();
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
            final LayoutParams lp = (QuickMediaDrawer.LayoutParams) child.getLayoutParams();

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

        if (coverView == child) {
            canvas.getClipBounds(mTmpRect);
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
                smoothSlideTo(anchorPoint, 0);
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
            return child == controls;
        }

        @Override
        public void onViewDragStateChanged(int state) {
            if (dragHelper.getViewDragState() == ViewDragHelper.STATE_IDLE) {
                slideOffset = computeSlideOffset(coverView.getBottom());
            }
        }

        @Override
        public void onViewCaptured(View capturedChild, int activePointerId) {
        }

        @Override
        public void onViewPositionChanged(View changedView, int left, int top, int dx, int dy) {
            if (drawerState == DrawerState.DRAGGING) {
                int newTop = coverView.getTop() + dy;
                final int expandedTop = computePanelTopPosition(1.0f) - coverView.getHeight();
                final int collapsedTop = computePanelTopPosition(0.0f) - coverView.getHeight();
                newTop = Math.min(Math.max(newTop, expandedTop), collapsedTop);
                slideOffset = computeSlideOffset(newTop + coverView.getHeight());
                requestLayout();
            }
        }

        @Override
        public void onViewReleased(View releasedChild, float xvel, float yvel) {
            if (releasedChild == controls) {
                int target = 0;
                float direction = -yvel;

                if (direction > 1000) {
                    // swipe up -> expand
                    target = computePanelTopPosition(1.0f);
                } else if (direction < -1500) {
                    // swipe down -> collapse
                    target = computePanelTopPosition(slideOffset > anchorPoint ? anchorPoint : 0.0f);
                } else if (anchorPoint != 1 && slideOffset >= (1.f + anchorPoint) / 2) {
                    // zero velocity, and far enough from anchor point => expand to the top
                    target = computePanelTopPosition(1.0f);
                } else if (anchorPoint == 1 && slideOffset >= 0.5f) {
                    // zero velocity, and far enough from anchor point => expand to the top
                    target = computePanelTopPosition(1.0f);
                } else if (anchorPoint != 1 && slideOffset >= anchorPoint) {
                    target = computePanelTopPosition(anchorPoint);
                } else if (anchorPoint != 1 && slideOffset >= anchorPoint / 2) {
                    target = computePanelTopPosition(anchorPoint);
                } else {
                    // settle at the bottom
                    target = computePanelTopPosition(0.0f);
                }

                dragHelper.captureChildView(coverView, 0);
                dragHelper.settleCapturedViewAt(releasedChild.getLeft(), target - releasedChild.getHeight());
                invalidate();
            }
            drawerState = DrawerState.DRAGGING;
        }

        @Override
        public int getViewVerticalDragRange(View child) {
            return slideRange;
        }

        @Override
        public int clampViewPositionVertical(View child, int top, int dy) {
            return top;
        }
    }

    @Override
    public boolean onInterceptTouchEvent(MotionEvent event) {
        final int action = MotionEventCompat.getActionMasked(event);

        if (!isEnabled()) {
            dragHelper.cancel();
            return super.onInterceptTouchEvent(event);
        }

        if (action == MotionEvent.ACTION_CANCEL || action == MotionEvent.ACTION_UP) {
            dragHelper.cancel();
            return false;
        }

        final float x = event.getX();
        final float y = event.getY();

        switch (action) {
            case MotionEvent.ACTION_DOWN: {
                initialMotionX = x;
                initialMotionY = y;
                break;
            }

            case MotionEvent.ACTION_MOVE: {
                final float adx = Math.abs(x - initialMotionX);
                final float ady = Math.abs(y - initialMotionY);
                final int dragSlop = dragHelper.getTouchSlop();

                // Handle any horizontal scrolling on the drag view.
                if (adx > dragSlop && ady < dragSlop) {
                    return super.onInterceptTouchEvent(event);
                }

                if ((ady > dragSlop && adx > ady) || !isDragViewUnder((int)initialMotionX, (int)initialMotionY)) {
                    dragHelper.cancel();
                    return false;
                }
                break;
            }
        }
        drawerState = DrawerState.DRAGGING;
        return dragHelper.shouldInterceptTouchEvent(event);
    }


    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (!isEnabled()) {
            return super.onTouchEvent(event);
        }
        dragHelper.processTouchEvent(event);
        return true;
    }

    private boolean isDragViewUnder(int x, int y) {
        int[] viewLocation = new int[2];
        quickCamera.getLocationOnScreen(viewLocation);
        int[] parentLocation = new int[2];
        this.getLocationOnScreen(parentLocation);
        int screenX = parentLocation[0] + x;
        int screenY = parentLocation[1] + y;
        return screenX >= viewLocation[0] && screenX < viewLocation[0] + quickCamera.getWidth() &&
                screenY >= viewLocation[1] && screenY < viewLocation[1] + quickCamera.getHeight();
    }

    private int computePanelTopPosition(float slideOffset) {
        int slidePixelOffset = (int) (slideOffset * slideRange);
        return getMeasuredHeight() - getPaddingBottom() - slidePixelOffset;
    }

    void smoothSlideTo(float slideOffset, int velocity) {
        this.slideOffset = slideOffset;
        int panelTop = computePanelTopPosition(slideOffset);
        noDrag = true;
        if (dragHelper.smoothSlideViewTo(quickCamera, quickCamera.getLeft(), panelTop) &&
            dragHelper.smoothSlideViewTo(coverView, coverView.getLeft(), panelTop - coverView.getHeight()))
            ViewCompat.postInvalidateOnAnimation(this);
        noDrag = false;

    }

    private float computeSlideOffset(int topPosition) {
        final int topBoundCollapsed = computePanelTopPosition(0);
        return (float) (topBoundCollapsed - topPosition) / slideRange;
    }

    @Override
    protected ViewGroup.LayoutParams generateDefaultLayoutParams() {
        return new LayoutParams();
    }

    @Override
    protected ViewGroup.LayoutParams generateLayoutParams(ViewGroup.LayoutParams p) {
        return p instanceof MarginLayoutParams
                ? new LayoutParams((MarginLayoutParams) p)
                : new LayoutParams(p);
    }

    @Override
    protected boolean checkLayoutParams(ViewGroup.LayoutParams p) {
        return p instanceof LayoutParams && super.checkLayoutParams(p);
    }

    @Override
    public ViewGroup.LayoutParams generateLayoutParams(AttributeSet attrs) {
        return new LayoutParams(getContext(), attrs);
    }

    public static class LayoutParams extends ViewGroup.MarginLayoutParams {
        private static final int[] ATTRS = new int[] {
                android.R.attr.layout_weight
        };

        public LayoutParams() {
            super(MATCH_PARENT, MATCH_PARENT);
        }

        public LayoutParams(int width, int height) {
            super(width, height);
        }

        public LayoutParams(android.view.ViewGroup.LayoutParams source) {
            super(source);
        }

        public LayoutParams(MarginLayoutParams source) {
            super(source);
        }

        public LayoutParams(LayoutParams source) {
            super(source);
        }

        public LayoutParams(Context c, AttributeSet attrs) {
            super(c, attrs);

            final TypedArray a = c.obtainStyledAttributes(attrs, ATTRS);
            a.recycle();
        }

    }

}
