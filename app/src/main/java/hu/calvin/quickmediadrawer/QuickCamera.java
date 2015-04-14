package hu.calvin.quickmediadrawer;

import android.content.Context;
import android.content.res.Configuration;
import android.graphics.Canvas;
import android.graphics.ImageFormat;
import android.graphics.Rect;
import android.graphics.YuvImage;
import android.hardware.Camera;
import android.net.Uri;
import android.support.v4.view.ViewCompat;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.Surface;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.FrameLayout;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.List;

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
    public void surfaceCreated(SurfaceHolder holder) {
        startPreview();
    }

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
                    File tempDirectory = getContext().getDir("tmp", Context.MODE_PRIVATE);
                    savingImage = true;
                    try {
                        File tempFile = File.createTempFile("image", ".jpg", tempDirectory);
                        FileOutputStream fileOutputStream = new FileOutputStream(tempFile);
                        int previewWidth = cameraParameters.getPreviewSize().width;
                        int previewHeight = cameraParameters.getPreviewSize().height;
                        YuvImage previewImage = new YuvImage(data, ImageFormat.NV21, previewWidth, previewHeight, null);

                        if (crop) {
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
                            Log.d("Quick Camera", "Full Rect: (" + previewWidth + ", " + previewHeight + "), Cropped Preview Rect: " + croppedPreviewRect);
                            previewImage.compressToJpeg(croppedPreviewRect, 100, fileOutputStream);
                        } else {
                            if (rotation == 90 || rotation == 270)
                                fullPreviewRect.set(0, 0, fullPreviewRect.height(), fullPreviewRect.width());
                            Log.d("Quick Camera", "Full Rect: (" + previewWidth + ", " + previewHeight + "), Full Preview Rect: " + fullPreviewRect);
                            previewImage.compressToJpeg(fullPreviewRect, 100, fileOutputStream);
                        }
                        fileOutputStream.close();
                        callback.onImageCapture(Uri.fromFile(tempFile), rotation);
                    } catch (FileNotFoundException e) {
                        Log.d(TAG, "File not found: " + e.getMessage());
                    } catch (IOException e) {
                        Log.d(TAG, "Error accessing file: " + e.getMessage());
                    }
                    savingImage = false;
                }
            });
        }
    }

    private void initializeCamera() {
        DisplayMetrics displayMetrics = getResources().getDisplayMetrics();
        int width = displayMetrics.widthPixels;
        int height = displayMetrics.heightPixels;
        if (camera == null)
            camera = getCameraInstance(cameraId);
        Camera.CameraInfo info = new Camera.CameraInfo();
        android.hardware.Camera.getCameraInfo(cameraId, info);
        cameraParameters = camera.getParameters();

        List<String> focusModes = cameraParameters.getSupportedFocusModes();
        if (focusModes.contains(Camera.Parameters.FOCUS_MODE_AUTO))
            cameraParameters.setFocusMode(Camera.Parameters.FOCUS_MODE_AUTO);

        int windowRotation = ((WindowManager) getContext().getSystemService(Context.WINDOW_SERVICE)).getDefaultDisplay().getRotation();
        int degrees = 0;
        switch (windowRotation) {
            case Surface.ROTATION_0: degrees = 0; break;
            case Surface.ROTATION_90: degrees = 90; break;
            case Surface.ROTATION_180: degrees = 180; break;
            case Surface.ROTATION_270: degrees = 270; break;
        }

        if (info.facing == Camera.CameraInfo.CAMERA_FACING_FRONT) {
            rotation = (info.orientation + degrees) % 360;
            rotation = (360 - rotation) % 360;  // compensate the mirror
        } else {
            rotation = (info.orientation - degrees + 360) % 360;
        }
        camera.setDisplayOrientation(rotation);

        Camera.Size previewSize;
        if (rotation == 0 || rotation == 180)
            previewSize = getOptimalPreviewSize(cameraParameters.getSupportedPreviewSizes(), width, height);
        else
            previewSize = getOptimalPreviewSize(cameraParameters.getSupportedPreviewSizes(), height, width);
        if (previewSize != null)
            cameraParameters.setPreviewSize(previewSize.width, previewSize.height);
    }

    public boolean isStarted() {
        return started;
    }

    public boolean startPreview() {
        try {
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
            camera.startPreview();
            started = true;
        } catch (RuntimeException | IOException e) {
            if (callback != null) callback.displayCameraUnavailableError();
            return false;
        }
        return true;
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
        void onImageCapture(final Uri imageFileUri, final int rotation);
    }
}