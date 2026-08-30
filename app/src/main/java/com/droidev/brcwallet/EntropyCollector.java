package com.droidev.brcwallet;

import android.content.Context;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;

import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.List;

public final class EntropyCollector {

    private EntropyCollector() {
    }

    public interface Callback {
        void onCollected(byte[] entropy);
    }

    public static void collectSensors(Context context, long durationMs, Callback callback) {
        Context app = context.getApplicationContext();
        SensorManager sm = (SensorManager) app.getSystemService(Context.SENSOR_SERVICE);
        Handler main = new Handler(Looper.getMainLooper());

        if (sm == null) {
            main.post(() -> callback.onCollected(fallbackOnly()));
            return;
        }

        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        writeLong(bos, System.nanoTime());
        writeLong(bos, SystemClock.elapsedRealtimeNanos());
        writeLong(bos, System.currentTimeMillis());

        List<Sensor> sensors = sm.getSensorList(Sensor.TYPE_ALL);
        for (Sensor s : sensors) {
            writeInt(bos, s.getType());
            writeInt(bos, s.getVersion());
            writeInt(bos, Float.floatToIntBits(s.getResolution()));
            writeInt(bos, Float.floatToIntBits(s.getMaximumRange()));
            String name = s.getName();
            if (name != null) {
                try {
                    bos.write(name.getBytes(StandardCharsets.UTF_8));
                } catch (Exception ignored) {
                }
            }
        }

        SensorEventListener listener = new SensorEventListener() {
            @Override
            public void onSensorChanged(SensorEvent event) {
                synchronized (bos) {
                    writeInt(bos, event.sensor.getType());
                    writeLong(bos, event.timestamp);
                    writeInt(bos, event.accuracy);
                    for (float v : event.values) {
                        writeInt(bos, Float.floatToIntBits(v));
                    }
                }
            }

            @Override
            public void onAccuracyChanged(Sensor sensor, int accuracy) {
                synchronized (bos) {
                    writeInt(bos, sensor.getType());
                    writeInt(bos, accuracy);
                    writeLong(bos, System.nanoTime());
                }
            }
        };

        for (Sensor s : sensors) {
            try {
                sm.registerListener(listener, s, SensorManager.SENSOR_DELAY_FASTEST);
            } catch (Exception ignored) {
            }
        }

        main.postDelayed(() -> {
            try {
                sm.unregisterListener(listener);
            } catch (Exception ignored) {
            }
            byte[] raw;
            synchronized (bos) {
                raw = bos.toByteArray();
            }
            callback.onCollected(sha256(raw));
        }, Math.max(100L, durationMs));
    }

    public static byte[] merge(byte[]... parts) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            SecureRandom sr = new SecureRandom();
            byte[] sys = new byte[32];
            sr.nextBytes(sys);
            md.update(sys);
            if (parts != null) {
                for (byte[] p : parts) {
                    if (p != null && p.length > 0) md.update(p);
                }
            }
            md.update(ByteBuffer.allocate(8).putLong(System.nanoTime()).array());
            return md.digest();
        } catch (Exception e) {
            return fallbackOnly();
        }
    }

    private static byte[] fallbackOnly() {
        byte[] b = new byte[32];
        new SecureRandom().nextBytes(b);
        return b;
    }

    private static byte[] sha256(byte[] data) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(data);
        } catch (Exception e) {
            return fallbackOnly();
        }
    }

    private static void writeInt(ByteArrayOutputStream bos, int v) {
        bos.write((v >> 24) & 0xFF);
        bos.write((v >> 16) & 0xFF);
        bos.write((v >> 8) & 0xFF);
        bos.write(v & 0xFF);
    }

    private static void writeLong(ByteArrayOutputStream bos, long v) {
        for (int i = 56; i >= 0; i -= 8) {
            bos.write((int) ((v >> i) & 0xFF));
        }
    }
}