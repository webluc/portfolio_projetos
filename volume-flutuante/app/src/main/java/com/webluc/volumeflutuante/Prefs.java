package com.webluc.volumeflutuante;

import android.content.Context;
import android.content.SharedPreferences;
import android.media.AudioManager;

/** Configurações compartilhadas entre a tela principal e o serviço flutuante. */
final class Prefs {
    static final String NAME = "volume_flutuante";
    static final String KEY_ALPHA = "alpha";          // 10..100 (%)
    static final String KEY_SIZE = "size";            // dp do botão
    static final String KEY_STREAM = "stream";        // índice em STREAMS
    static final String KEY_SYSTEM_UI = "system_ui";
    static final String KEY_X = "pos_x";
    static final String KEY_Y = "pos_y";

    static final int[] STREAMS = {
            AudioManager.STREAM_MUSIC,
            AudioManager.STREAM_RING,
            AudioManager.STREAM_NOTIFICATION,
            AudioManager.STREAM_ALARM,
            AudioManager.STREAM_VOICE_CALL,
    };
    static final String[] STREAM_LABELS = {"Mídia", "Toque", "Notificações", "Alarme", "Chamada"};

    static final int MIN_ALPHA = 10;
    static final int MIN_SIZE = 32;

    private Prefs() {}

    static SharedPreferences get(Context c) {
        return c.getSharedPreferences(NAME, Context.MODE_PRIVATE);
    }

    static int alpha(SharedPreferences p) { return p.getInt(KEY_ALPHA, 80); }
    static int size(SharedPreferences p) { return p.getInt(KEY_SIZE, 56); }
    static boolean systemUi(SharedPreferences p) { return p.getBoolean(KEY_SYSTEM_UI, false); }

    static int streamIndex(SharedPreferences p) {
        int i = p.getInt(KEY_STREAM, 0);
        return (i >= 0 && i < STREAMS.length) ? i : 0;
    }

    static int stream(SharedPreferences p) { return STREAMS[streamIndex(p)]; }
}
