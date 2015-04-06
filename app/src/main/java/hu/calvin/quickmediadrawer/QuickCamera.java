package hu.calvin.quickmediadrawer;

import android.content.Context;
import android.content.res.Configuration;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.BitmapRegionDecoder;
import android.graphics.Canvas;
import android.graphics.Rect;
import android.hardware.Camera;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Environment;
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
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;

public class QuickCamera extends SurfaceView implements SurfaceHolder.Callback {
    public static final int MEDIA_TYPE_IMAGE = 1;
    public static final int MEDIA_TYPE_VIDEO = 2;
    private SurfaceHolder surfaceHolder;
    private Camera camera;
    private boolean started;
    private final String TAG = "QuickCamera";
    private int cameraId = Camera.CameraInfo.CAMERA_FACING_BACK;
    private Callback callback;
    private Camera.Parameters cameraParameters;
    private boolean savingImage;

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
            stopPreview();
        } catch (RuntimeException e) {
            e.printStackTrace();
        }
    }

    public void setCallback(Callback callback) {
        this.callback = callback;
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
    }

    @Override
    public void surfaceChanged(SurfaceHolder holder, int format, int w, int h) {
    }

    @Override
    public void surfaceDestroyed(SurfaceHolder holder) {
        stopPreview();
    }

    public void stopPreview() {
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

    //TODO: crop photo based on viewport and store in ram, not disk
    public void takePicture(final boolean crop, final Rect fullPreviewRect, final Rect croppedPreviewRect) {
        if (camera != null) {
            camera.takePicture(null, null, new Camera.PictureCallback() {
                @Override
                public void onPictureTaken(byte[] data, Camera camera) {
                    new FormatImageAsyncTask().execute(data, crop, fullPreviewRect, croppedPreviewRect);
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
            int rotation = ((WindowManager) getContext().getSystemService(Context.WINDOW_SERVICE)).getDefaultDisplay().getRotation();
            int degrees = 0;
            switch (rotation) {
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

            int derivedRotation;
            if (info.facing == Camera.CameraInfo.CAMERA_FACING_FRONT) {
                derivedRotation = (info.orientation - degrees + 360) % 360;
            } else {
                derivedRotation = (info.orientation + degrees) % 360;
            }
            cameraParameters.setRotation(derivedRotation);
            if (info.orientation == 0 || info.orientation == 180)
                previewSize = getOptimalPreviewSize(cameraParameters.getSupportedPreviewSizes(), width, height);
            else
                previewSize = getOptimalPreviewSize(cameraParameters.getSupportedPreviewSizes(), height, width);
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
                int rotation = (getResources().getConfiguration().orientation == Configuration.ORIENTATION_PORTRAIT ? 90 : 0);
                camera.setParameters(cameraParameters);
                camera.setPreviewDisplay(surfaceHolder);
            }
            camera.startPreview();
            started = true;
        } catch (RuntimeException e) {
            if (callback != null) callback.displayCameraInUseCopy(true);
        } catch (IOException ie) {
            if (callback != null) callback.displayCameraInUseCopy(true);
        }
    }

    private static String getOutputMediaFileName(){
        String timeStamp = new SimpleDateFormat("yyyyMMdd_HHmmss").format(new Date());
        return "IMG_" + timeStamp + ".jpg";
    }

    public boolean isMultipleCameras() {
        return Camera.getNumberOfCameras() > 1;
    }

    public void swapCamera() {
        if (isMultipleCameras()) {
            cameraId = (cameraId == Camera.CameraInfo.CAMERA_FACING_BACK) ? Camera.CameraInfo.CAMERA_FACING_FRONT : Camera.CameraInfo.CAMERA_FACING_BACK;
            stopPreview();
            startPreview();
        }
    }

    public boolean isBackCamera() {
        return cameraId == Camera.CameraInfo.CAMERA_FACING_BACK;
    }

    public interface Callback {
        void displayCameraInUseCopy(boolean inUse);
        void onImageCapture(String imageFilename);
    }

    public class FormatImageAsyncTask extends AsyncTask<Object, Void, String> {
        @Override
        protected String doInBackground(Object... params) {
            if (savingImage)
                return null;
            byte[] data = (byte[]) params[0];
            boolean crop = (Boolean) params[1];
            Rect fullPreviewRect = (Rect) params[2];
            Rect croppedPreviewRect = (Rect) params[3];

            String pictureFileName = getOutputMediaFileName();
            savingImage = true;
            try {
                FileOutputStream fos = getContext().openFileOutput(pictureFileName, Context.MODE_PRIVATE);
                if (crop && android.os.Build.VERSION.SDK_INT >= Build.VERSION_CODES.GINGERBREAD_MR1) {
                    BitmapRegionDecoder decoder = BitmapRegionDecoder.newInstance(data, 0, data.length, false);
                    int realHeight = decoder.getHeight();
                    int realWidth = decoder.getWidth();
                    float widthRatio = (float) realWidth / (float) fullPreviewRect.width();
                    float heightRatio = (float) realHeight / (float) fullPreviewRect.height();

                    float newWidth = widthRatio * (float) croppedPreviewRect.width();
                    float newHeight = heightRatio * (float) croppedPreviewRect.height();

                    float centerX = realWidth / 2;
                    float centerY = realHeight / 2;

                    Rect croppedRect = new Rect();
                    croppedRect.set((int) (centerX - newWidth / 2),
                            (int) (centerY - newHeight / 2),
                            (int) (centerX + newWidth / 2),
                            (int) (centerY + newHeight / 2));

                    Bitmap croppedBitmap = decoder.decodeRegion(croppedRect, null);
                    decoder.recycle();
                    croppedBitmap.compress(Bitmap.CompressFormat.JPEG, 100, fos);
                    croppedBitmap.recycle();
                } else {
                    fos.write(data);
                }
                fos.close();
            } catch (FileNotFoundException e) {
                Log.d(TAG, "File not found: " + e.getMessage());
            } catch (IOException e) {
                Log.d(TAG, "Error accessing file: " + e.getMessage());
            }
            return pictureFileName;
        }

        @Override
        protected void onPostExecute(String resultFilename) {
            if (resultFilename != null) {
                callback.onImageCapture(resultFilename);
                savingImage = false;
            }
        }
    }
}
