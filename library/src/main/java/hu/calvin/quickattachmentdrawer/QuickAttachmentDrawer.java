package hu.calvin.quickattachmentdrawer;

import android.content.Context;
import android.content.pm.PackageManager;
import android.content.res.TypedArray;
import android.graphics.Canvas;
import android.graphics.Rect;
import android.graphics.drawable.ScaleDrawable;
import android.hardware.Camera;
import android.os.Build;
import android.support.annotation.IntDef;
import android.support.annotation.NonNull;
import android.support.v4.view.MotionEventCompat;
import android.support.v4.view.PagerAdapter;
import android.support.v4.view.ViewCompat;
import android.support.v4.view.ViewPager;
import android.support.v4.widget.ViewDragHelper;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.Surface;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.ImageButton;

import java.util.ArrayList;
import java.util.List;

public class QuickAttachmentDrawer extends ViewGroup  {
    @IntDef({COLLAPSED, HALF_EXPANDED, FULL_EXPANDED})
    public @interface DrawerState {}
    public static final int COLLAPSED = 0;
    public static final int HALF_EXPANDED = 1;
    public static final int FULL_EXPANDED = 2;

    private static final float FULL_EXPANDED_ANCHOR_POINT = 1.f;
    private static final float COLLAPSED_ANCHOR_POINT = 0.f;

    private static final int FLAG_CAMERA = 0x01;
    private static final int FLAG_AUDIO = 0x02;

    private final ViewDragHelper dragHelper;
    private final QuickCamera quickCamera;
    private final QuickAudio quickAudio;
    private final ViewPager controls;
    private View coverView, cameraControls;
    private View[] controlViews;
    private ImageButton fullScreenButton;
    private @DrawerState int drawerState;
    private float slideOffset, initialMotionX, initialMotionY, halfExpandedAnchorPoint;
    private boolean initialSetup, hasCamera, hasMic, startCamera, stopCamera, landscape, belowICS, inputCamera, inputAudio;
    private int inputTypes, slideRange, baseHalfHeight;
    private Rect drawChildrenRect = new Rect();
    private QuickAttachmentDrawerListener listener;

    public QuickAttachmentDrawer(Context context) {
        this(context, null);
    }

    public QuickAttachmentDrawer(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public QuickAttachmentDrawer(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
        TypedArray attrArray = context.getTheme().obtainStyledAttributes(attrs, R.styleable.QuickAttachmentDrawer, 0, 0);
        try {
            inputTypes = attrArray.getInt(R.styleable.QuickAttachmentDrawer_inputTypes, 3);
        } finally {
            attrArray.recycle();
        }

        initialSetup = true;
        startCamera = false;
        stopCamera = false;
        drawerState = COLLAPSED;
        baseHalfHeight = getResources().getDimensionPixelSize(R.dimen.quick_media_drawer_default_height);
        halfExpandedAnchorPoint = COLLAPSED_ANCHOR_POINT;
        int rotation = ((WindowManager) getContext().getSystemService(Context.WINDOW_SERVICE)).getDefaultDisplay().getRotation();
        landscape = rotation == Surface.ROTATION_90 || rotation == Surface.ROTATION_270;
        belowICS = android.os.Build.VERSION.SDK_INT < Build.VERSION_CODES.ICE_CREAM_SANDWICH;

        PackageManager packageManager = context.getPackageManager();
        hasCamera = context.getPackageManager().hasSystemFeature(PackageManager.FEATURE_CAMERA) && Camera.getNumberOfCameras() > 0;
        hasMic = packageManager.hasSystemFeature(PackageManager.FEATURE_MICROPHONE);

        inputCamera = 0 != (inputTypes & FLAG_CAMERA) && hasCamera;
        inputAudio = 0 != (inputTypes & FLAG_AUDIO) && hasMic;
        if (inputCamera) {
            setBackgroundResource(android.R.color.black);
            quickCamera = new QuickCamera(context);
            addView(quickCamera);
            initializeCameraControls();
        } else {
            quickCamera = null;
            cameraControls = null;
        }

        if (inputAudio) {
            quickAudio = (QuickAudio) inflate(getContext(), R.layout.quick_audio_controls, null);
        } else {
            quickAudio = null;
        }

        if (inputCamera || inputAudio) {
            dragHelper = ViewDragHelper.create(this, 1.f, new ViewDragHelperCallback());
            controls = (ViewPager) inflate(getContext(), R.layout.quick_attachment_drawer_controls, null);
            initializeControlsView();
            addView(controls);
        } else {
            dragHelper = null;
            controls = null;
            controlViews = null;
        }
    }

    private void initializeHalfExpandedAnchorPoint() {
        if (initialSetup) {
            if (getChildCount() == 3)
                coverView = getChildAt(2);
            else if (!inputCamera && inputAudio)
                coverView = getChildAt(1);
            else
                coverView = getChildAt(0);
            slideRange = getMeasuredHeight();
            int anchorHeight = slideRange - baseHalfHeight;
            halfExpandedAnchorPoint = computeSlideOffsetFromCoverBottom(anchorHeight);
            initialSetup = false;
        }
    }

    private void initializeCameraControls() {
        cameraControls = inflate(getContext(), R.layout.quick_camera_controls, null);
        cameraControls.findViewById(R.id.shutter_button).setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                boolean crop = drawerState != FULL_EXPANDED;
                int imageHeight = crop ? baseHalfHeight : quickCamera.getMeasuredHeight();
                Rect previewRect = new Rect(0, 0, quickCamera.getMeasuredWidth(), imageHeight);
                quickCamera.takePicture(crop, previewRect);
            }
        });

        final ImageButton swapCameraButton = (ImageButton) cameraControls.findViewById(R.id.swap_camera_button);
        if (quickCamera.isMultipleCameras()) {
            swapCameraButton.setVisibility(View.VISIBLE);
            swapCameraButton.setOnClickListener(new OnClickListener() {
                @Override
                public void onClick(View v) {
                    quickCamera.swapCamera();
                    swapCameraButton.setImageResource(quickCamera.isRearCamera() ? R.drawable.quick_camera_front : R.drawable.quick_camera_rear);
                }
            });
        }

        fullScreenButton = (ImageButton) cameraControls.findViewById(R.id.fullscreen_button);
        fullScreenButton.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                if (drawerState == HALF_EXPANDED || drawerState == COLLAPSED)
                    setDrawerStateAndAnimate(FULL_EXPANDED);
                else if (landscape || belowICS)
                    setDrawerStateAndAnimate(COLLAPSED);
                else
                    setDrawerStateAndAnimate(HALF_EXPANDED);
            }
        });
    }

    private void initializeControlsView() {
        List<View> viewList = new ArrayList<>();
        if (inputCamera) viewList.add(cameraControls);
        if (inputAudio) viewList.add(quickAudio);
        controlViews = viewList.toArray(new View[viewList.size()]);
        controls.setAdapter(new ControlPagerAdapter(controlViews));
        controls.setOnPageChangeListener(new ControlsOnPageListener());
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
            } else if (child == controls) {
                /*childTop = computeCoverBottomPosition(slideOffset);
                childBottom = childTop + childHeight;*/
                childBottom = getMeasuredHeight();
                childTop = childBottom - childHeight;
            } else {
                childBottom = computeCoverBottomPosition(slideOffset);
                childTop = childBottom - childHeight;
            }
            final int childRight = childLeft + child.getMeasuredWidth();

            child.layout(childLeft, childTop, childRight, childBottom);
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
        //if (inputCamera && childCount != 3)
        //    throw new IllegalStateException("QuickAttachmentDrawer layouts may only have 1 child.");

        int layoutHeight = heightSize - getPaddingTop() - getPaddingBottom();

        for (int i = 0; i < childCount; i++) {
            final View child = getChildAt(i);
            final LayoutParams lp = child.getLayoutParams();

            if (child.getVisibility() == GONE && i == 0) {
                continue;
            }

            int childWidthSpec, childHeightSpec;
            if (child == controls) {
                childHeightSpec = MeasureSpec.makeMeasureSpec(getMeasuredHeight() - computeCoverBottomPosition(slideOffset), MeasureSpec.EXACTLY);
            } else {
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
            }


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

            child.measure(childWidthSpec, childHeightSpec);
        }

        setMeasuredDimension(widthSize, heightSize);
        initializeHalfExpandedAnchorPoint();
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        if (h != oldh)
            initialSetup = true;
    }

    @Override
    protected boolean drawChild(@NonNull Canvas canvas, @NonNull View child, long drawingTime) {
        boolean result;
        final int save = canvas.save(Canvas.CLIP_SAVE_FLAG);

        canvas.getClipBounds(drawChildrenRect);
        if (child == coverView)
            drawChildrenRect.bottom = Math.min(drawChildrenRect.bottom, child.getBottom());
        else if (coverView != null)
            drawChildrenRect.top = Math.max(drawChildrenRect.top, coverView.getBottom());
        canvas.clipRect(drawChildrenRect);
        result = super.drawChild(canvas, child, drawingTime);
        canvas.restoreToCount(save);
        return result;
    }

    @Override
    public void computeScroll() {
        if (dragHelper != null && dragHelper.continueSettling(true)) {
            ViewCompat.postInvalidateOnAnimation(this);
        } else if (stopCamera && quickCamera != null) {
            quickCamera.stopPreviewAndReleaseCamera();
            stopCamera = false;
        } else if (startCamera && quickCamera != null) {
            startCamera = false;
            if (!quickCamera.startPreview())
                setDrawerStateAndAnimate(COLLAPSED);
        }
    }

    private void setDrawerState(@DrawerState int drawerState) {
        if (inputCamera || inputAudio) {
            switch (drawerState) {
                case COLLAPSED:
                    if (quickCamera != null && quickCamera.isStarted())
                        stopCamera = true;
                    slideOffset = COLLAPSED_ANCHOR_POINT;
                    if (inputCamera) {
                        startCamera = false;
                        fullScreenButton.setImageResource(R.drawable.quick_camera_fullscreen);
                    }
                    if (listener != null) listener.onCollapsed();
                    break;
                case HALF_EXPANDED:
                    if (landscape || belowICS) {
                        setDrawerState(FULL_EXPANDED);
                        return;
                    }
                    if (quickCamera != null && !quickCamera.isStarted())
                        startCamera = true;
                    slideOffset = halfExpandedAnchorPoint;
                    if (inputCamera) {
                        stopCamera = false;
                        fullScreenButton.setImageResource(R.drawable.quick_camera_fullscreen);
                    }
                    if (listener != null) listener.onHalfExpanded();
                    break;
                case FULL_EXPANDED:
                    if (quickCamera != null && !quickCamera.isStarted())
                        startCamera = true;
                    slideOffset = FULL_EXPANDED_ANCHOR_POINT;
                    if (inputCamera) {
                        stopCamera = false;
                        fullScreenButton.setImageResource(landscape || belowICS ? R.drawable.quick_camera_hide : R.drawable.quick_camera_exit_fullscreen);
                    }
                    if (listener != null) listener.onExpanded();
                    break;
            }
            this.drawerState = drawerState;
        }
    }

    public @DrawerState int getDrawerState() {
        return drawerState;
    }

    public void setDrawerStateAndAnimate(@DrawerState int drawerState) {
        setDrawerState(drawerState);
        slideTo(slideOffset);
    }

    public void setQuickAttachmentDrawerListener(QuickAttachmentDrawerListener listener) {
        this.listener = listener;
    }

    public void setQuickCameraListener(QuickCamera.QuickCameraListener listener) {
        if (quickCamera != null) quickCamera.setQuickCameraListener(listener);
    }

    public void setQuickAudioListener(QuickAudio.QuickAudioListener listener) {
        if (quickAudio != null) quickAudio.setQuickAudioListener(listener);
    }

    public interface QuickAttachmentDrawerListener {
        void onCollapsed();
        void onExpanded();
        void onHalfExpanded();
    }

    private class ViewDragHelperCallback extends ViewDragHelper.Callback {

        @Override
        public boolean tryCaptureView(View child, int pointerId) {
            return child == controls && !belowICS;
        }

        @Override
        public void onViewDragStateChanged(int state) {
            if (dragHelper.getViewDragState() == ViewDragHelper.STATE_IDLE) {
                setDrawerState(drawerState);
                requestLayout();
            }
        }

        @Override
        public void onViewCaptured(View capturedChild, int activePointerId) {
        }

        @Override
        public void onViewPositionChanged(View changedView, int left, int top, int dx, int dy) {
            int newTop = coverView.getTop() + dy;
            final int expandedTop = computeCoverBottomPosition(FULL_EXPANDED_ANCHOR_POINT) - coverView.getHeight();
            final int collapsedTop = computeCoverBottomPosition(COLLAPSED_ANCHOR_POINT) - coverView.getHeight();
            newTop = Math.min(Math.max(newTop, expandedTop), collapsedTop);
            slideOffset = computeSlideOffsetFromCoverBottom(newTop + coverView.getHeight());
            requestLayout();
        }

        @Override
        public void onViewReleased(View releasedChild, float xvel, float yvel) {
            if (releasedChild == controls) {
                float direction = -yvel;
                int drawerState = COLLAPSED;

                if (direction > 1) {
                    drawerState = FULL_EXPANDED;
                } else if (direction < -1) {
                    boolean halfExpand = (slideOffset > halfExpandedAnchorPoint && !landscape);
                    drawerState = halfExpand ? HALF_EXPANDED : COLLAPSED;
                } else if (!landscape) {
                    if (halfExpandedAnchorPoint != 1 && slideOffset >= (1.f + halfExpandedAnchorPoint) / 2) {
                        drawerState = FULL_EXPANDED;
                    } else if (halfExpandedAnchorPoint == 1 && slideOffset >= 0.5f) {
                        drawerState = FULL_EXPANDED;
                    } else if (halfExpandedAnchorPoint != 1 && slideOffset >= halfExpandedAnchorPoint) {
                        drawerState = HALF_EXPANDED;
                    } else if (halfExpandedAnchorPoint != 1 && slideOffset >= halfExpandedAnchorPoint / 2) {
                        drawerState = HALF_EXPANDED;
                    }
                }

                setDrawerState(drawerState);
                dragHelper.captureChildView(coverView, 0);
                dragHelper.settleCapturedViewAt(coverView.getLeft(), computeCoverBottomPosition(slideOffset) - coverView.getHeight());
                if (quickCamera != null) {
                    dragHelper.captureChildView(quickCamera, 0);
                    dragHelper.settleCapturedViewAt(quickCamera.getLeft(), computeCameraTopPosition(slideOffset));
                }
                ViewCompat.postInvalidateOnAnimation(QuickAttachmentDrawer.this);
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
        if (dragHelper != null) {
            final int action = MotionEventCompat.getActionMasked(event);

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

                    if (adx > dragSlop && ady < dragSlop) {
                        return super.onInterceptTouchEvent(event);
                    }

                    if ((ady > dragSlop && adx > ady) ){
                        //|| (quickCamera != null
                         //   && !isDragViewUnder((int) initialMotionX, (int) initialMotionY))) {
                        dragHelper.cancel();
                        return false;
                    }
                    break;
                }
            }
            return dragHelper.shouldInterceptTouchEvent(event);
        }
        return super.onInterceptTouchEvent(event);
    }


    @Override
    public boolean onTouchEvent(@NonNull MotionEvent event) {
        if (dragHelper != null) {
            dragHelper.processTouchEvent(event);
            return true;
        }
        return super.onTouchEvent(event);
    }

    private class ControlsOnPageListener extends ViewPager.SimpleOnPageChangeListener {
        private boolean stopCamera;

        public ControlsOnPageListener(){
            super();
            stopCamera = false;
        }

        @Override
        public void onPageScrolled(int position, float positionOffset, int positionOffsetPixels) {
            if (controlViews != null && controlViews[position] == cameraControls && !quickCamera.isStarted()) {
                quickCamera.startPreview();
            }
        }

        @Override
        public void onPageSelected(int position) {
            if (controlViews != null && controlViews[position] != cameraControls){
                stopCamera = true;
            }
        }

        @Override
        public void onPageScrollStateChanged(int state) {
            if (state == ViewPager.SCROLL_STATE_IDLE && stopCamera) {
                if (quickCamera.isStarted())
                    quickCamera.stopPreviewAndReleaseCamera();
                stopCamera = false;
            }
        }
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
        float clampedOffset = slideOffset - halfExpandedAnchorPoint;
        if (clampedOffset < COLLAPSED_ANCHOR_POINT)
            clampedOffset = COLLAPSED_ANCHOR_POINT;
        else
            clampedOffset = clampedOffset / (FULL_EXPANDED_ANCHOR_POINT - halfExpandedAnchorPoint);
        float slidePixelOffset = slideOffset * slideRange +
                (quickCamera.getMeasuredHeight() - baseHalfHeight) / 2 * (FULL_EXPANDED_ANCHOR_POINT - clampedOffset);
        float marginPixelOffset = (getMeasuredHeight() - quickCamera.getMeasuredHeight()) / 2 * clampedOffset;
        return (int) (getMeasuredHeight() - slidePixelOffset + marginPixelOffset);
    }

    private int computeCoverBottomPosition(float slideOffset) {
        int slidePixelOffset = (int) (slideOffset * slideRange);
        return getMeasuredHeight() - getPaddingBottom() - slidePixelOffset;
    }

    private void slideTo(float slideOffset) {
        if (dragHelper != null && !belowICS) {
            dragHelper.smoothSlideViewTo(coverView, coverView.getLeft(), computeCoverBottomPosition(slideOffset) - coverView.getHeight());
            if (quickCamera != null) dragHelper.smoothSlideViewTo(quickCamera, quickCamera.getLeft(), computeCameraTopPosition(slideOffset));
            ViewCompat.postInvalidateOnAnimation(this);
        } else {
            invalidate();
        }
    }

    private float computeSlideOffsetFromCoverBottom(int topPosition) {
        final int topBoundCollapsed = computeCoverBottomPosition(0);
        return (float) (topBoundCollapsed - topPosition) / slideRange;
    }

    public void onStop() {
        if (inputCamera)
            quickCamera.stopPreviewAndReleaseCamera();
        if (quickAudio.isRecording())
            quickAudio.stopRecording();
    }

    public void onStart() {
        if (inputCamera && (drawerState == HALF_EXPANDED || drawerState == FULL_EXPANDED))
            quickCamera.startPreview();
    }
}
