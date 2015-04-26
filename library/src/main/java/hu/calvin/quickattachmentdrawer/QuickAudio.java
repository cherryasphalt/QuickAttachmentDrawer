package hu.calvin.quickattachmentdrawer;

import android.media.MediaRecorder;
import android.os.Handler;

import java.io.File;
import java.io.IOException;

public class QuickAudio {
    private MediaRecorder mediaRecorder;
    private QuickAudioListener listener;
    private boolean recording;
    private Handler handler;
    private final static int VISUALIZATION_INTERVAL = 100;

    public QuickAudio() {
        handler = new Handler();
        recording = false;
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
        if (listener != null) {
            File newFile = listener.createNewAudioFile();
            mediaRecorder.setOutputFile(newFile.getPath());
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
        mediaRecorder = null;
    }

    public boolean isRecording() {
        return recording;
    }

    public interface QuickAudioListener {
        public File createNewAudioFile();
        public void visualizationStep(int maxAmplitude);
    }
}
