package hu.calvin.quickmediadrawer;

import android.content.Context;
import android.content.res.Configuration;
import android.graphics.Canvas;
import android.graphics.ImageFormat;
import android.graphics.Rect;
import android.graphics.YuvImage;
import android.hardware.Camera;
import android.os.Build;
import android.support.v4.view.ViewCompat;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.Surface;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.FrameLayout;

import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class QuickCamera extends SurfaceView implements SurfaceHolder.Callback {
    private SurfaceHolder surfaceHolder;
    private Camera camera;
    private boolean started, savingImage;
    private final String TAG = "QuickCamera";
    private int cameraId = Camera.CameraInfo.CAMERA_FACING_BACK;
    private Callback callback;
    private Camera.Parameters cameraParameters;
    private int rotation;

    public QuickCamera(Context context, Callback callback) {
        super(context);
        this.callback = callback;
        started = false;
        savingImage = false;
        try {
            ViewGroup.LayoutParams layoutParams;
            camera = getCameraInstance(cameraId);
            initializeCamera();
            if (cameraParameters != null) {
                if (getResources().getConfiguration().orientation == Configuration.ORIENTATION_PORTRAIT)
                    layoutParams = new FrameLayout.LayoutParams(cameraParameters.getPreviewSize().height, cameraParameters.getPreviewSize().width);
                else
                    layoutParams = new FrameLayout.LayoutParams(cameraParameters.getPreviewSize().width, cameraParameters.getPreviewSize().height);
            } else {
                layoutParams = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
            }
            setLayoutParams(layoutParams);
            surfaceHolder = getHolder();
            surfaceHolder.addCallback(this);
            surfaceHolder.setType(SurfaceHolder.SURFACE_TYPE_PUSH_BUFFERS);
            stopPreviewAndReleaseCamera();
        } catch (RuntimeException e) {
            e.printStackTrace();
        }
    }

    public void setCallback(Callback callback) {
        this.callback = callback;
    }

    public int getCameraRotation() {
        return rotation;
    }

    private Camera getCameraInstance(int cameraId) throws RuntimeException {
        return Camera.open(cameraId);
    }

    private Camera.Size getOptimalPreviewSize(List<Camera.Size> sizes, int width, int height) {
        final double ASPECT_TOLERANCE = 0.1;
        double targetRatio=(double)height / width;

        if (sizes == null) return null;

        Camera.Size optimalSize = null;
        double minDiff = Double.MAX_VALUE;

        int targetHeight = height;

        for (Camera.Size size : sizes) {
            double ratio = (double) size.width / size.height;
            if (Math.abs(ratio - targetRatio) > ASPECT_TOLERANCE) continue;
            if (Math.abs(size.height - targetHeight) < minDiff) {
                optimalSize = size;
                minDiff = Math.abs(size.height - targetHeight);
            }
        }

        if (optimalSize == null) {
            minDiff = Double.MAX_VALUE;
            for (Camera.Size size : sizes) {
                if (Math.abs(size.height - targetHeight) < minDiff) {
                    optimalSize = size;
                    minDiff = Math.abs(size.height - targetHeight);
                }
            }
        }
        return optimalSize;
    }

    @Override
    public void surfaceCreated(SurfaceHolder holder) {}

    @Override
    public void surfaceChanged(SurfaceHolder holder, int format, int w, int h) {}

    @Override
    public void surfaceDestroyed(SurfaceHolder holder) {
        stopPreviewAndReleaseCamera();
    }

    public void stopPreviewAndReleaseCamera() {
        ViewCompat.setAlpha(QuickCamera.this, 0.f);
        if (camera != null) {
            camera.stopPreview();
            camera.release();
            camera = null;
            started = false;
        }
    }

    @Override
    protected void onDraw (Canvas canvas) {

    }

    public void takePicture(final boolean crop, final Rect fullPreviewRect, final Rect croppedPreviewRect) {
        if (camera != null) {
            //use preview frame for faster capture/less memory usage
            camera.setOneShotPreviewCallback(new Camera.PreviewCallback() {
                @Override
                public void onPreviewFrame(byte[] data, Camera camera) {
                    if (savingImage) return;
                    String pictureFileName = getOutputMediaFileName();
                    savingImage = true;
                    try {
                        FileOutputStream fileOutputStream = getContext().openFileOutput(pictureFileName, Context.MODE_PRIVATE);
                        int previewWidth = cameraParameters.getPreviewSize().width;
                        int previewHeight = cameraParameters.getPreviewSize().height;
                        YuvImage previewImage = new YuvImage(data, ImageFormat.NV21, previewWidth, previewHeight, null);

                        if (crop && android.os.Build.VERSION.SDK_INT >= Build.VERSION_CODES.GINGERBREAD_MR1) {
                            float newWidth, newHeight;
                            if (rotation == 90 || rotation == 270) {
                                newWidth = croppedPreviewRect.height();
                                newHeight = croppedPreviewRect.width();
                            } else {
                                newWidth = croppedPreviewRect.width();
                                newHeight = croppedPreviewRect.height();
                            }
                            float centerX = previewWidth / 2;
                            float centerY = previewHeight / 2;
                            croppedPreviewRect.set((int) (centerX - newWidth / 2),
                                    (int) (centerY - newHeight / 2),
                                    (int) (centerX + newWidth / 2),
                                    (int) (centerY + newHeight / 2));
                            previewImage.compressToJpeg(croppedPreviewRect, 100, fileOutputStream);
                        } else {
                            if (rotation == 90 || rotation == 270)
                                fullPreviewRect.set(0, 0, fullPreviewRect.height(), fullPreviewRect.width());
                            previewImage.compressToJpeg(fullPreviewRect, 100, fileOutputStream);
                        }
                        fileOutputStream.close();
                    } catch (FileNotFoundException e) {
                        Log.d(TAG, "File not found: " + e.getMessage());
                    } catch (IOException e) {
                        Log.d(TAG, "Error accessing file: " + e.getMessage());
                    }
                    callback.onImageCapture(pictureFileName, rotation);
                    savingImage = false;
                }
            });
        }
    }

    private void initializeCamera() {
        DisplayMetrics displayMetrics = getResources().getDisplayMetrics();
        int width = displayMetrics.widthPixels;
        int height = displayMetrics.heightPixels;
        if (camera != null) {
            Camera.CameraInfo info = new Camera.CameraInfo();
            android.hardware.Camera.getCameraInfo(cameraId, info);
            cameraParameters = camera.getParameters();

            List<String> focusModes = cameraParameters.getSupportedFocusModes();
            if (focusModes.contains(Camera.Parameters.FOCUS_MODE_AUTO))
               cameraParameters.setFocusMode(Camera.Parameters.FOCUS_MODE_AUTO);

            Camera.Size previewSize;
            int windowRotation = ((WindowManager) getContext().getSystemService(Context.WINDOW_SERVICE)).getDefaultDisplay().getRotation();
            int degrees = 0;
            switch (windowRotation) {
                case Surface.ROTATION_0: degrees = 0; break;
                case Surface.ROTATION_90: degrees = 90; break;
                case Surface.ROTATION_180: degrees = 180; break;
                case Surface.ROTATION_270: degrees = 270; break;
            }
            int derivedOrientation;
            if (info.facing == Camera.CameraInfo.CAMERA_FACING_FRONT) {
                derivedOrientation = (info.orientation + degrees) % 360;
                derivedOrientation = (360 - derivedOrientation) % 360;  // compensate the mirror
            } else {
                derivedOrientation = (info.orientation - degrees + 360) % 360;
            }
            camera.setDisplayOrientation(derivedOrientation);

            if (info.facing == Camera.CameraInfo.CAMERA_FACING_FRONT) {
                rotation = (info.orientation - degrees + 360) % 360;
            } else {
                rotation = (info.orientation + degrees) % 360;
            }
            cameraParameters.setRotation(rotation);
            if (getResources().getConfiguration().orientation == Configuration.ORIENTATION_PORTRAIT)
                previewSize = getOptimalPreviewSize(cameraParameters.getSupportedPreviewSizes(), height, width);
            else
                previewSize = getOptimalPreviewSize(cameraParameters.getSupportedPreviewSizes(), width, height);
            if (previewSize != null)
                cameraParameters.setPreviewSize(previewSize.width, previewSize.height);
        }
    }

    public boolean isStarted() {
        return started;
    }

    public void startPreview() {
        try {
            if (camera == null) {
                camera = getCameraInstance(cameraId);
                initializeCamera();
                camera.setParameters(cameraParameters);
                camera.setPreviewDisplay(surfaceHolder);
                ViewCompat.setAlpha(this, 0.f);
                camera.setOneShotPreviewCallback(new Camera.PreviewCallback() {
                    @Override
                    public void onPreviewFrame(byte[] data, Camera camera) {
                        ViewCompat.setAlpha(QuickCamera.this, 1.f);
                    }
                });
            }
            camera.startPreview();
            started = true;
        } catch (RuntimeException | IOException e) {
            if (callback != null) callback.displayCameraUnavailableError();
        }
    }

    private static String getOutputMediaFileName(){
        String timeStamp = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(new Date());
        return "IMG_" + timeStamp + ".jpg";
    }

    public boolean isMultipleCameras() {
        return Camera.getNumberOfCameras() > 1;
    }

    public void swapCamera() {
        if (isMultipleCameras()) {
            cameraId = (cameraId == Camera.CameraInfo.CAMERA_FACING_BACK) ? Camera.CameraInfo.CAMERA_FACING_FRONT : Camera.CameraInfo.CAMERA_FACING_BACK;
            stopPreviewAndReleaseCamera();
            startPreview();
        }
    }

    public boolean isBackCamera() {
        return cameraId == Camera.CameraInfo.CAMERA_FACING_BACK;
    }

    public interface Callback {
        void displayCameraUnavailableError();
        void onImageCapture(final String imageFilename, final int rotation);
    }
}