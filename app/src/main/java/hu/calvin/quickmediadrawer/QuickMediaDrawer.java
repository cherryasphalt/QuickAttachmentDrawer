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
import android.widget.ImageButton;

public class QuickMediaDrawer extends ViewGroup implements QuickCamera.Callback {
    //TODO: Make intdef to avoid dex method limit
    public static enum DrawerState {COLLAPSED, HALF_EXPANDED, FULL_EXPANDED};
    private DrawerState drawerState;
    private View coverView;
    private int slideRange;
    private float slideOffset;
    private QuickMediaDrawerListener listener;
    private final ViewDragHelper dragHelper;
    private final QuickCamera quickCamera;
    private final View controls;
    private ImageButton fullScreenButton;
    private final Rect mTmpRect = new Rect();
    private float initialMotionX, initialMotionY;
    private float anchorPoint;
    private boolean initialSetup;
    private boolean stopCamera;

    public QuickMediaDrawer(Context context) {
        this(context, null);
    }

    public QuickMediaDrawer(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public QuickMediaDrawer(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
        drawerState = DrawerState.COLLAPSED;
        dragHelper = ViewDragHelper.create(this, 1f, new ViewDragHelperCallback());
        quickCamera = new QuickCamera(context, null);
        controls = inflate(getContext(), R.layout.quick_camera_controls, null);
        anchorPoint = 0f;
        initializeControlsView();
        addView(quickCamera);
        addView(controls);
        initialSetup = true;
        stopCamera = false;
    }

    public void initializeControlsView() {
        controls.findViewById(R.id.shutter_button).setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                if (listener != null) listener.onImageCapture();
            }
        });

        final ImageButton swapCameraButton = (ImageButton) controls.findViewById(R.id.swap_camera_button);
        if (quickCamera.isMultipleCameras()) {
            swapCameraButton.setVisibility(View.VISIBLE);
            swapCameraButton.setOnClickListener(new OnClickListener() {
                @Override
                public void onClick(View v) {
                    quickCamera.swapCamera();
                    swapCameraButton.setImageResource(quickCamera.isBackCamera() ? R.drawable.quick_camera_front : R.drawable.quick_camera_rear);
                }
            });
        }

        fullScreenButton = (ImageButton) controls.findViewById(R.id.fullscreen_button);
        fullScreenButton.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                switch (drawerState) {
                    case HALF_EXPANDED:
                        setDrawerState(DrawerState.FULL_EXPANDED);
                        fullScreenButton.setImageResource(R.drawable.quick_camera_exit_fullscreen);
                        break;
                    case FULL_EXPANDED:
                        setDrawerState(DrawerState.HALF_EXPANDED);
                        fullScreenButton.setImageResource(R.drawable.quick_camera_fullscreen);
                        break;
                }
            }
        });
    }

    @Override
    public void displayCameraInUseCopy(boolean inUse) {

    }

    @Override
    protected void onLayout(boolean changed, int l, int t, int r, int b) {
        final int paddingLeft = getPaddingLeft();
        final int paddingTop = getPaddingTop();

        final int childCount = getChildCount();

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

            if (initialSetup && child == coverView) {
                slideRange = getMeasuredHeight();
                int anchorHeight = slideRange - getResources().getDimensionPixelSize(R.dimen.quick_media_drawer_default_height);
                anchorPoint = computeSlideOffset(anchorHeight);
                initialSetup = false;
            }
        }
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
    public void computeScroll() {
        if (dragHelper != null && dragHelper.continueSettling(true)) {
            if (!isEnabled()) {
                dragHelper.abort();
                return;
            }
            ViewCompat.postInvalidateOnAnimation(this);
        } else if (stopCamera){
            quickCamera.stopPreview();
            stopCamera = false;
        }
    }

    public DrawerState getDrawerState() {
        return drawerState;
    }

    public void setDrawerState(DrawerState drawerState) {
        this.drawerState = drawerState;
        switch (drawerState) {
            case COLLAPSED:
                smoothSlideTo(0);
                stopCamera = true;
                break;
            case HALF_EXPANDED:
                smoothSlideTo(anchorPoint);
                stopCamera = false;
                if (!quickCamera.isStarted())
                    quickCamera.startPreview();
                break;
            case FULL_EXPANDED:
                smoothSlideTo(1.f);
                stopCamera = false;
                if (!quickCamera.isStarted())
                    quickCamera.startPreview();
                break;
        }
    }

    public void setQuickMediaDrawerListener(QuickMediaDrawerListener listener) {
        this.listener = listener;
    }

    public interface QuickMediaDrawerListener {
        public void onPanelCollapsed();
        public void onPanelExpanded();
        public void onPanelHalfExpanded();
        public void onImageCapture();
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
            int newTop = coverView.getTop() + dy;
            final int expandedTop = computePanelTopPosition(1.0f) - coverView.getHeight();
            final int collapsedTop = computePanelTopPosition(0.0f) - coverView.getHeight();
            newTop = Math.min(Math.max(newTop, expandedTop), collapsedTop);
            slideOffset = computeSlideOffset(newTop + coverView.getHeight());
            requestLayout();
        }

        @Override
        public void onViewReleased(View releasedChild, float xvel, float yvel) {
            if (releasedChild == controls) {
                int target = 0;
                float direction = -yvel;

                if (direction > 1000) {
                    // swipe up -> expand
                    target = computePanelTopPosition(1.0f);
                    drawerState = DrawerState.FULL_EXPANDED;
                    fullScreenButton.setImageResource(R.drawable.quick_camera_exit_fullscreen);
                } else if (direction < -1500) {
                    // swipe down -> collapse
                    target = computePanelTopPosition(slideOffset > anchorPoint ? anchorPoint : 0.0f);
                    drawerState = slideOffset > anchorPoint ? DrawerState.HALF_EXPANDED : DrawerState.COLLAPSED;
                    fullScreenButton.setImageResource(R.drawable.quick_camera_fullscreen);
                    if (drawerState == DrawerState.COLLAPSED)
                        stopCamera = true;
                } else if (anchorPoint != 1 && slideOffset >= (1.f + anchorPoint) / 2) {
                    target = computePanelTopPosition(1.0f);
                    drawerState = DrawerState.FULL_EXPANDED;
                    fullScreenButton.setImageResource(R.drawable.quick_camera_exit_fullscreen);
                } else if (anchorPoint == 1 && slideOffset >= 0.5f) {
                    target = computePanelTopPosition(1.0f);
                    drawerState = DrawerState.FULL_EXPANDED;
                    fullScreenButton.setImageResource(R.drawable.quick_camera_exit_fullscreen);
                } else if (anchorPoint != 1 && slideOffset >= anchorPoint) {
                    target = computePanelTopPosition(anchorPoint);
                    drawerState = DrawerState.HALF_EXPANDED;
                    fullScreenButton.setImageResource(R.drawable.quick_camera_fullscreen);
                } else if (anchorPoint != 1 && slideOffset >= anchorPoint / 2) {
                    target = computePanelTopPosition(anchorPoint);
                    drawerState = DrawerState.HALF_EXPANDED;
                    fullScreenButton.setImageResource(R.drawable.quick_camera_fullscreen);
                } else {
                    // settle at the bottom
                    target = computePanelTopPosition(0.0f);
                    drawerState = DrawerState.COLLAPSED;
                    fullScreenButton.setImageResource(R.drawable.quick_camera_fullscreen);
                    quickCamera.stopPreview();
                }

                dragHelper.captureChildView(coverView, 0);
                dragHelper.settleCapturedViewAt(coverView.getLeft(), target - coverView.getHeight());
                dragHelper.captureChildView(quickCamera, 0);
                dragHelper.settleCapturedViewAt(quickCamera.getLeft(), target);
            }
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

    private void smoothSlideTo(float slideOffset) {
        int panelTop = computePanelTopPosition(slideOffset);
        dragHelper.smoothSlideViewTo(coverView, coverView.getLeft(), panelTop - coverView.getHeight());
        dragHelper.smoothSlideViewTo(quickCamera, quickCamera.getLeft(), panelTop);
        ViewCompat.postInvalidateOnAnimation(this);
        this.slideOffset = slideOffset;
    }

    private float computeSlideOffset(int topPosition) {
        final int topBoundCollapsed = computePanelTopPosition(0);
        return (float) (topBoundCollapsed - topPosition) / slideRange;
    }

    public void onPause() {
        quickCamera.stopPreview();
    }

    public void onResume() {
        quickCamera.startPreview();
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
