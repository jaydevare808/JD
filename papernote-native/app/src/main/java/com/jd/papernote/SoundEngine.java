package com.jd.papernote;

import android.content.Context;
import android.media.AudioAttributes;
import android.media.AudioFormat;
import android.media.AudioTrack;

import java.util.Random;

public final class SoundEngine implements AutoCloseable {
    private final AudioTrack track;
    private final byte[] pcm;
    private volatile boolean enabled = true;
    private long lastTickMs = 0L;

    public SoundEngine(Context context) {
        int sampleRate = 22050;
        int durationMs = 190;
        int sampleCount = sampleRate * durationMs / 1000;
        short[] samples = new short[sampleCount];
        Random random = new Random(7919);
        float filtered = 0f;

        for (int i = 0; i < sampleCount; i++) {
            float n = random.nextFloat() * 2f - 1f;
            filtered = filtered * 0.84f + n * 0.16f;
            float envelope = Math.min(1f, i / (sampleCount * 0.10f))
                    * Math.min(1f, (sampleCount - i) / (sampleCount * 0.18f));
            float scratch = 0.72f * filtered + 0.28f * n;
            samples[i] = (short) (scratch * envelope * 1900f);
        }

        pcm = new byte[samples.length * 2];
        for (int i = 0; i < samples.length; i++) {
            pcm[i * 2] = (byte) (samples[i] & 0xFF);
            pcm[i * 2 + 1] = (byte) ((samples[i] >> 8) & 0xFF);
        }

        AudioAttributes attributes = new AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build();
        AudioFormat format = new AudioFormat.Builder()
                .setSampleRate(sampleRate)
                .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                .build();

        track = new AudioTrack.Builder()
                .setAudioAttributes(attributes)
                .setAudioFormat(format)
                .setBufferSizeInBytes(pcm.length)
                .setTransferMode(AudioTrack.MODE_STATIC)
                .build();

        track.write(pcm, 0, pcm.length);
        track.setVolume(0.23f);
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
        if (!enabled && track.getPlayState() == AudioTrack.PLAYSTATE_PLAYING) {
            track.stop();
        }
    }

    public void tick() {
        if (!enabled) return;
        long now = android.os.SystemClock.uptimeMillis();
        if (now - lastTickMs < 48L) return;
        lastTickMs = now;

        try {
            if (track.getPlayState() == AudioTrack.PLAYSTATE_PLAYING) {
                track.stop();
            }
            track.reloadStaticData();
            track.play();
        } catch (IllegalStateException ignored) {
        }
    }

    @Override
    public void close() {
        try {
            track.stop();
        } catch (Exception ignored) {
        }
        track.release();
    }
}
