package hu.calvin.quickmediadrawer;

import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.Canvas;
import android.graphics.Rect;
import android.os.Build;
import android.support.v4.view.MotionEventCompat;
import android.support.v4.view.ViewCompat;
import android.support.v4.widget.ViewDragHelper;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.Surface;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.ImageButton;

public class QuickMediaDrawer extends ViewGroup implements QuickCamera.Callback {
    //TODO: Make intdef to avoid dex method limit
    public enum DrawerState {COLLAPSED, HALF_EXPANDED, FULL_EXPANDED};
    private final ViewDragHelper dragHelper;
    private final QuickCamera quickCamera;
    private final View controls;
    private DrawerState drawerState;
    private View coverView;
    private float slideOffset, initialMotionX, initialMotionY, anchorPoint;
    private boolean initialSetup, startCamera, stopCamera, landscape, belowICS;
    private int slideRange, cameraSlideRange, baseHalfHeight;
    private Rect mTmpRect = new Rect();
    private ImageButton fullScreenButton;
    private QuickMediaDrawerListener listener;

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
        quickCamera = new QuickCamera(context, this);
        controls = inflate(getContext(), R.layout.quick_camera_controls, null);
        anchorPoint = 0f;
        int rotation = ((WindowManager) getContext().getSystemService(Context.WINDOW_SERVICE)).getDefaultDisplay().getRotation();
        landscape = rotation == Surface.ROTATION_90 || rotation == Surface.ROTATION_270;
        belowICS = android.os.Build.VERSION.SDK_INT < Build.VERSION_CODES.ICE_CREAM_SANDWICH;
        initializeControlsView();
        addView(quickCamera);
        addView(controls);
        initialSetup = true;
        startCamera = false;
        stopCamera = false;
        baseHalfHeight = getResources().getDimensionPixelSize(R.dimen.quick_media_drawer_default_height);
    }

    public void initializeControlsView() {
        controls.findViewById(R.id.shutter_button).setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                Rect fullPreviewRect = new Rect();
                fullPreviewRect.set(0, 0, quickCamera.getMeasuredWidth(), quickCamera.getMeasuredHeight());
                Rect croppedPreviewRect = new Rect();
                croppedPreviewRect.set(0, 0, quickCamera.getMeasuredWidth(), baseHalfHeight);
                quickCamera.takePicture(drawerState != DrawerState.FULL_EXPANDED, fullPreviewRect, croppedPreviewRect);
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
                if (drawerState == DrawerState.HALF_EXPANDED || drawerState == DrawerState.COLLAPSED)
                    setDrawerState(DrawerState.FULL_EXPANDED);
                else
                    setDrawerState((landscape || belowICS) ? DrawerState.COLLAPSED : DrawerState.HALF_EXPANDED);
            }
        });
    }

    @Override
    public void displayCameraInUseCopy(boolean inUse) {

    }

    @Override
    public void onImageCapture(String imageFilename, int rotation) {
        if (listener != null) listener.onImageCapture(imageFilename, rotation);
    }

    @Override
    protected void onLayout(boolean changed, int l, int t, int r, int b) {
        final int paddingLeft = getPaddingLeft();
        final int paddingTop = getPaddingTop();

        final int childCount = getChildCount();

        for (int i = 0; i < childCount; i++) {
            final View child = getChildAt(i);

            final int childHeight = child.getMeasuredHeight();
            int childTop = paddingTop;
            int childBottom;
            int childLeft = paddingLeft;

            if (child == quickCamera) {
                childTop = computeCameraTopPosition(slideOffset);
                childBottom = childTop + childHeight;
                if (quickCamera.getMeasuredWidth() < getMeasuredWidth())
                    childLeft = (getMeasuredWidth() - quickCamera.getMeasuredWidth()) / 2 + paddingLeft;
            } else if (child == controls){
                childBottom = getMeasuredHeight();
            } else {
                childBottom = computeCoverBottomPosition(slideOffset);
                childTop = childBottom - childHeight;
            }
            final int childRight = childLeft + child.getMeasuredWidth();

            child.layout(childLeft, childTop, childRight, childBottom);

            if (initialSetup && child == coverView) {
                slideRange = getMeasuredHeight();
                int anchorHeight = slideRange - baseHalfHeight;
                anchorPoint = computeSlideOffsetFromCoverBottom(anchorHeight);
            } else if (initialSetup && child == quickCamera) {
                cameraSlideRange = getMeasuredHeight();
            }
        }
        initialSetup = false;
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

        for (int i = 0; i < childCount; i++) {
            final View child = getChildAt(i);
            final LayoutParams lp = (QuickMediaDrawer.LayoutParams) child.getLayoutParams();

            if (child.getVisibility() == GONE && i == 0) {
                continue;
            }

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
                    childHeightSpec = MeasureSpec.makeMeasureSpec(layoutHeight, MeasureSpec.AT_MOST);
                    break;
                case LayoutParams.MATCH_PARENT:
                    childHeightSpec = MeasureSpec.makeMeasureSpec(layoutHeight, MeasureSpec.EXACTLY);
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

        canvas.getClipBounds(mTmpRect);
        if (child == coverView)
            mTmpRect.bottom = Math.min(mTmpRect.bottom, child.getBottom());
        else
            mTmpRect.top = Math.max(mTmpRect.top, coverView.getBottom());
        canvas.clipRect(mTmpRect);
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
            quickCamera.stopPreviewAndReleaseCamera();
            stopCamera = false;
        } else if (startCamera) {
            quickCamera.startPreview();
            startCamera = false;
        }
    }

    public DrawerState getDrawerState() {
        return drawerState;
    }

    public void setDrawerState(DrawerState drawerState) {
        this.drawerState = drawerState;
        switch (drawerState) {
            case COLLAPSED:
                slideTo(0f);
                stopCamera = true;
                fullScreenButton.setImageResource(R.drawable.quick_camera_fullscreen);
                if (listener != null) listener.onCollapsed();
                break;
            case HALF_EXPANDED:
                slideTo(anchorPoint);
                stopCamera = false;
                fullScreenButton.setImageResource(R.drawable.quick_camera_fullscreen);
                if (!quickCamera.isStarted())
                    startCamera = true;
                if (listener != null) listener.onHalfExpanded();
                break;
            case FULL_EXPANDED:
                slideTo(1.f);
                stopCamera = false;
                fullScreenButton.setImageResource(landscape ? R.drawable.quick_camera_hide : R.drawable.quick_camera_exit_fullscreen);
                if (!quickCamera.isStarted())
                    startCamera = true;
                if (listener != null) listener.onExpanded();
                break;
        }
    }

    public void setQuickMediaDrawerListener(QuickMediaDrawerListener listener) {
        this.listener = listener;
    }

    public interface QuickMediaDrawerListener {
        void onCollapsed();
        void onExpanded();
        void onHalfExpanded();
        void onImageCapture(String imageFilename, int rotation);
    }

    private class ViewDragHelperCallback extends ViewDragHelper.Callback {

        @Override
        public boolean tryCaptureView(View child, int pointerId) {
            return child == controls && !belowICS;
        }

        @Override
        public void onViewDragStateChanged(int state) {
            if (dragHelper.getViewDragState() == ViewDragHelper.STATE_IDLE) {
                switch (drawerState) {
                    case FULL_EXPANDED:
                        slideOffset = 1.f;
                        break;
                    case HALF_EXPANDED:
                        slideOffset = anchorPoint;
                        break;
                    case COLLAPSED:
                        slideOffset = 0.f;
                        break;
                }
                requestLayout();
            }
        }

        @Override
        public void onViewCaptured(View capturedChild, int activePointerId) {
        }

        @Override
        public void onViewPositionChanged(View changedView, int left, int top, int dx, int dy) {
            int newTop = coverView.getTop() + dy;
            final int expandedTop = computeCoverBottomPosition(1.0f) - coverView.getHeight();
            final int collapsedTop = computeCoverBottomPosition(0.0f) - coverView.getHeight();
            newTop = Math.min(Math.max(newTop, expandedTop), collapsedTop);
            slideOffset = computeSlideOffsetFromCoverBottom(newTop + coverView.getHeight());
            requestLayout();
        }

        @Override
        public void onViewReleased(View releasedChild, float xvel, float yvel) {
            if (releasedChild == controls) {
                float offset = 0.f;
                float direction = -yvel;

                if (direction > 1) {
                    drawerState = DrawerState.FULL_EXPANDED;
                } else if (direction < -1) {
                    boolean halfExpand = (slideOffset > anchorPoint && !landscape);
                    drawerState = halfExpand ? DrawerState.HALF_EXPANDED : DrawerState.COLLAPSED;
                } else if (!landscape) {
                    if (anchorPoint != 1 && slideOffset >= (1.f + anchorPoint) / 2) {
                        drawerState = DrawerState.FULL_EXPANDED;
                    } else if (anchorPoint == 1 && slideOffset >= 0.5f) {
                        drawerState = DrawerState.FULL_EXPANDED;
                    } else if (anchorPoint != 1 && slideOffset >= anchorPoint) {
                        drawerState = DrawerState.HALF_EXPANDED;
                    } else if (anchorPoint != 1 && slideOffset >= anchorPoint / 2) {
                        drawerState = DrawerState.HALF_EXPANDED;
                    }
                } else {
                    drawerState = DrawerState.COLLAPSED;
                }

                switch (drawerState) {
                    case COLLAPSED:
                        if (listener != null) listener.onCollapsed();
                        offset = 0.0f;
                        fullScreenButton.setImageResource(R.drawable.quick_camera_fullscreen);
                        stopCamera = true;
                        break;
                    case HALF_EXPANDED:
                        if (listener != null) listener.onHalfExpanded();
                        offset = anchorPoint;
                        fullScreenButton.setImageResource(R.drawable.quick_camera_fullscreen);
                        break;
                    case FULL_EXPANDED:
                        if (listener != null) listener.onExpanded();
                        offset = 1.0f;
                        fullScreenButton.setImageResource(landscape ? R.drawable.quick_camera_hide : R.drawable.quick_camera_exit_fullscreen);
                        break;
                }
                dragHelper.captureChildView(coverView, 0);
                dragHelper.settleCapturedViewAt(coverView.getLeft(), computeCoverBottomPosition(offset) - coverView.getHeight());
                dragHelper.captureChildView(quickCamera, 0);
                dragHelper.settleCapturedViewAt(quickCamera.getLeft(), computeCameraTopPosition(offset));
            }
        }

        @Override
        public int getViewVerticalDragRange(View child) {
            if (child == quickCamera)
                return cameraSlideRange;
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

    private int computeCameraTopPosition(float slideOffset) {
        float clampedOffset = slideOffset - anchorPoint;
        clampedOffset = clampedOffset < 0.f ? 0.f : (clampedOffset / (1.f - anchorPoint));
        int slidePixelOffset = (int) (slideOffset * cameraSlideRange +
                //center the half expanded camera
                (getMeasuredHeight() + baseHalfHeight) / 2 * (1.f - clampedOffset) / 2);
        //center the camera vertically when it's smaller than the whole view
        int marginPixelOffset = (int) ((getMeasuredHeight() - quickCamera.getMeasuredHeight()) / 2 * clampedOffset);
        return getMeasuredHeight() - slidePixelOffset + marginPixelOffset - getPaddingBottom();
    }

    private int computeCoverBottomPosition(float slideOffset) {
        int slidePixelOffset = (int) (slideOffset * slideRange);
        return getMeasuredHeight() - getPaddingBottom() - slidePixelOffset;
    }

    private void slideTo(float slideOffset) {
        this.slideOffset = slideOffset;
        if (!belowICS) {
            dragHelper.smoothSlideViewTo(coverView, coverView.getLeft(), computeCoverBottomPosition(slideOffset) - coverView.getHeight());
            dragHelper.smoothSlideViewTo(quickCamera, quickCamera.getLeft(), computeCameraTopPosition(slideOffset));
            ViewCompat.postInvalidateOnAnimation(this);
        } else {
            invalidate();
        }
    }

    private float computeSlideOffsetFromCoverBottom(int topPosition) {
        final int topBoundCollapsed = computeCoverBottomPosition(0);
        return (float) (topBoundCollapsed - topPosition) / slideRange;
    }

    public void onPause() {
        slideTo(0f);
        quickCamera.stopPreviewAndReleaseCamera();
        fullScreenButton.setImageResource(R.drawable.quick_camera_fullscreen);
        if (listener != null) listener.onCollapsed();
    }

    public void onResume() {
        if (drawerState == DrawerState.HALF_EXPANDED || drawerState == DrawerState.FULL_EXPANDED)
            startCamera = true;
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
