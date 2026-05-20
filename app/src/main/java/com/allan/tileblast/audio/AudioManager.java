package com.allan.tileblast.audio;

import android.content.Context;
import android.media.AudioAttributes;
import android.media.SoundPool;

import com.allan.tileblast.R;
import com.allan.tileblast.game.PowerUpType;

import java.util.HashSet;
import java.util.Set;

public class AudioManager {
    private static AudioManager instance;
    private SoundPool soundPool;
    private int sndPlace, sndBreak, sndGameover, sndJuiciness;
    private int[] sndCombo = new int[5];
    private float volume = 1.0f;
    private Set<Integer> loadedSoundIds = new HashSet<>();

    public static AudioManager getInstance(Context ctx) {
        if (instance == null) instance = new AudioManager(ctx.getApplicationContext());
        return instance;
    }

    private AudioManager(Context ctx) {
        AudioAttributes attrs = new AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_GAME)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .build();
        soundPool = new SoundPool.Builder().setMaxStreams(6).setAudioAttributes(attrs).build();
        soundPool.setOnLoadCompleteListener((sp, sampleId, status) -> {
            if (status == 0) {
                loadedSoundIds.add(sampleId);
            }
        });

        sndPlace = soundPool.load(ctx, R.raw.place, 1);
        sndBreak = soundPool.load(ctx, R.raw.break_, 1);
        sndGameover = soundPool.load(ctx, R.raw.gameover, 1);
        sndJuiciness = soundPool.load(ctx, R.raw.juiciness, 1);
        sndCombo[0] = soundPool.load(ctx, R.raw.combo1, 1);
        sndCombo[1] = soundPool.load(ctx, R.raw.combo2, 1);
        sndCombo[2] = soundPool.load(ctx, R.raw.combo3, 1);
        sndCombo[3] = soundPool.load(ctx, R.raw.combo4, 1);
        sndCombo[4] = soundPool.load(ctx, R.raw.combo5, 1);
    }

    public void playPlace() {
        if (soundPool != null && loadedSoundIds.contains(sndPlace)) {
            soundPool.play(sndPlace, volume, volume, 1, 0, 1f);
        }
    }

    public void playBreak() {
        if (soundPool != null && loadedSoundIds.contains(sndBreak)) {
            soundPool.play(sndBreak, volume, volume, 1, 0, 1f);
        }
    }

    public void playGameover() {
        if (soundPool != null && loadedSoundIds.contains(sndGameover)) {
            soundPool.play(sndGameover, volume, volume, 1, 0, 1f);
        }
    }

    public void playJuiciness() {
        if (soundPool != null && loadedSoundIds.contains(sndJuiciness)) {
            soundPool.play(sndJuiciness, volume, volume, 1, 0, 1f);
        }
    }

    public void playCombo(int comboLevel) {
        int idx = Math.min(comboLevel - 2, 4);
        if (idx >= 0 && idx < 5) {
            if (soundPool != null && loadedSoundIds.contains(sndCombo[idx])) {
                soundPool.play(sndCombo[idx], volume, volume, 1, 0, 1f);
            }
        }
    }

    public void playPerfectClear() {
        // Reuse juiciness sound for perfect clear celebration
        if (soundPool != null && loadedSoundIds.contains(sndJuiciness)) {
            soundPool.play(sndJuiciness, volume, volume, 1, 0, 1.2f);
        }
    }

    /** Plays a cue when the player earns a power-up (place.mp3 at higher pitch). */
    public void playPowerUpAcquired() {
        if (soundPool != null && loadedSoundIds.contains(sndPlace)) {
            soundPool.play(sndPlace, volume, volume, 1, 0, 1.3f);
        }
    }

    /** Plays the activation sound for the given power-up type. */
    public void playPowerUp(PowerUpType type) {
        if (soundPool == null) return;
        switch (type) {
            case BOMB:
                if (loadedSoundIds.contains(sndJuiciness)) {
                    soundPool.play(sndJuiciness, volume, volume, 1, 0, 1f);
                }
                break;
            case LINE_SWEEP:
                if (loadedSoundIds.contains(sndBreak)) {
                    soundPool.play(sndBreak, volume, volume, 1, 0, 1f);
                }
                break;
            case ROTATE:
                if (loadedSoundIds.contains(sndPlace)) {
                    soundPool.play(sndPlace, volume, volume, 1, 0, 1.5f);
                }
                break;
            case UNDO:
                if (loadedSoundIds.contains(sndPlace)) {
                    soundPool.play(sndPlace, volume, volume, 1, 0, 0.7f);
                }
                break;
        }
    }

    public void setVolume(float v) { this.volume = v; }
    public float getVolume() { return volume; }

    public void release() {
        if (soundPool != null) { soundPool.release(); soundPool = null; }
        loadedSoundIds.clear();
        instance = null;
    }
}
