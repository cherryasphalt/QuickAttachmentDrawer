package hu.calvin.quickattachmentdrawer;

import android.content.Context;
import android.media.MediaRecorder;
import android.os.Handler;
import android.os.PowerManager;

import java.io.File;
import java.io.IOException;

public class QuickAudio {
    private final String TAG = getClass().getSimpleName();
    private MediaRecorder mediaRecorder;
    private QuickAudioListener listener;
    private boolean recording;
    private Handler handler;
    private File audioFile;
    private PowerManager.WakeLock wakeLock;
    private final static int VISUALIZATION_INTERVAL = 1;

    public QuickAudio(Context context) {
        recording = false;
        handler = new Handler();
        PowerManager powerManager = (PowerManager) context.getSystemService(Context.POWER_SERVICE);
        wakeLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, TAG);
    }

    public void setQuickAudioListener(QuickAudioListener listener) {
        this.listener = listener;
    }

    public void startRecording() {
        if (mediaRecorder == null)
            mediaRecorder = new MediaRecorder();
        mediaRecorder.setAudioSource(MediaRecorder.AudioSource.MIC);
        mediaRecorder.setOutputFormat(MediaRecorder.OutputFormat.THREE_GPP);
        mediaRecorder.setAudioEncoder(MediaRecorder.AudioEncoder.AMR_NB);
        wakeLock.acquire();
        if (listener != null) {
            audioFile = listener.createNewAudioFile();
            mediaRecorder.setOutputFile(audioFile.getPath());
            try {
                mediaRecorder.prepare();
            } catch (IOException e) {
            }
            handler.post(new Runnable() {
                @Override
                public void run() {
                    if (recording) {
                        listener.visualizationStep(mediaRecorder.getMaxAmplitude());
                        handler.postDelayed(this, VISUALIZATION_INTERVAL);
                    }
                }
            });
            mediaRecorder.start();
            recording = true;
        }
    }

    public void stopRecording() {
        recording = false;
        mediaRecorder.stop();
        mediaRecorder.release();
        wakeLock.release();
        if (listener != null) listener.onStopRecording(audioFile);
        mediaRecorder = null;
        audioFile = null;
    }

    public boolean isRecording() {
        return recording;
    }

    public interface QuickAudioListener {
        File createNewAudioFile();
        void visualizationStep(int maxAmplitude);
        void onStopRecording(File audioFile);
    }
}
