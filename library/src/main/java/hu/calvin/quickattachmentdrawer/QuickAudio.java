package hu.calvin.quickattachmentdrawer;

import android.content.Context;
import android.graphics.Rect;
import android.graphics.drawable.ScaleDrawable;
import android.media.MediaRecorder;
import android.os.Handler;
import android.os.PowerManager;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.widget.RelativeLayout;

import java.io.File;
import java.io.IOException;

public class QuickAudio extends RelativeLayout {
    private final String TAG = getClass().getSimpleName();
    private MediaRecorder mediaRecorder;
    private QuickAudioListener listener;
    private boolean recording;
    private Handler handler;
    private File audioFile;
    private PowerManager.WakeLock wakeLock;
    private View audioFeedbackDisplay;
    private ScaleDrawable audioFeedbackBackground;
    private final static int VISUALIZATION_INTERVAL = 1;

    public QuickAudio(Context context) {
        this(context, null);
    }

    public QuickAudio(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public QuickAudio(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
        recording = false;
        handler = new Handler();
        PowerManager powerManager = (PowerManager) context.getSystemService(Context.POWER_SERVICE);
        wakeLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, TAG);
    }

    @Override
    protected void onFinishInflate() {
        super.onFinishInflate();
        initializeAudioControls();
    }

    private void initializeAudioControls() {
        findViewById(R.id.audio_record_button).setOnTouchListener(new OnTouchListener() {
            @Override
            public boolean onTouch(View view, MotionEvent motionEvent) {
                final Rect hitRect = new Rect();
                view.getHitRect(hitRect);
                final float x = motionEvent.getX() + hitRect.left;
                final float y = motionEvent.getY() + hitRect.top;
                switch (motionEvent.getAction()) {
                    case MotionEvent.ACTION_DOWN:
                        if (!recording) startRecording();
                        return false;
                    case MotionEvent.ACTION_CANCEL:
                    case MotionEvent.ACTION_UP:
                        if (recording) stopRecording();
                        return false;
                }
                return false;
            }
        });

        audioFeedbackBackground = (ScaleDrawable) getResources().getDrawable(R.drawable.quick_audio_feedback_scalable);
        audioFeedbackDisplay = findViewById(R.id.audio_record_display);
        audioFeedbackDisplay.setBackgroundDrawable(audioFeedbackBackground);
    }

    public void setQuickAudioListener(QuickAudioListener listener) {
        this.listener = listener;
    }

    public void startRecording() {
        if (listener != null)
            audioFile = listener.createNewAudioFile();
        if (audioFile != null) {
            if (mediaRecorder == null)
                mediaRecorder = new MediaRecorder();
            mediaRecorder.setAudioSource(MediaRecorder.AudioSource.MIC);
            mediaRecorder.setOutputFormat(MediaRecorder.OutputFormat.THREE_GPP);
            mediaRecorder.setAudioEncoder(MediaRecorder.AudioEncoder.AMR_NB);
            mediaRecorder.setOutputFile(audioFile.getPath());
            wakeLock.acquire();
            try {
                mediaRecorder.prepare();
            } catch (IOException e) {
            }
            handler.post(new Runnable() {
                @Override
                public void run() {
                    if (recording) {
                        visualizationStep(mediaRecorder.getMaxAmplitude());
                        handler.postDelayed(this, VISUALIZATION_INTERVAL);
                    }
                }
            });
            mediaRecorder.start();
            recording = true;
        }
    }

    public void stopRecording() {
        mediaRecorder.stop();
        mediaRecorder.release();
        wakeLock.release();
        audioFeedbackBackground.setLevel(0);
        mediaRecorder = null;
        audioFile = null;
        if (listener != null) listener.onStopRecording(audioFile);
        recording = false;
    }

    public boolean isRecording() {
        return recording;
    }

    public void visualizationStep(int maxAmplitude) {
        if (audioFeedbackDisplay != null && audioFeedbackBackground != null) {
            int clampedAmplitude = Math.min(maxAmplitude  * 10/4 + 5000, 10000);
            //clampedAmplitude = (int) (1.f - (float)(Math.log(10000-clampedAmplitude)/Math.log(10000))) * clampedAmplitude;
            audioFeedbackBackground.setLevel(clampedAmplitude);
        }
    }

    public interface QuickAudioListener {
        File createNewAudioFile();
        void onStopRecording(File audioFile);
    }
}
