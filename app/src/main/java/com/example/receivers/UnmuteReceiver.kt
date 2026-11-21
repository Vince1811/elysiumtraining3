package com.example.receivers

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.util.Log

class UnmuteReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        Log.d("UnmuteReceiver", "Alarm received, checking if phone is muted")
        
        try {
            if (audioManager.ringerMode != AudioManager.RINGER_MODE_NORMAL) {
                Log.d("UnmuteReceiver", "Phone is muted, unmuting now")
                audioManager.ringerMode = AudioManager.RINGER_MODE_NORMAL
                
                // Fallback adjustments
                audioManager.adjustStreamVolume(AudioManager.STREAM_RING, AudioManager.ADJUST_UNMUTE, 0)
                audioManager.adjustStreamVolume(AudioManager.STREAM_NOTIFICATION, AudioManager.ADJUST_UNMUTE, 0)
                audioManager.adjustStreamVolume(AudioManager.STREAM_SYSTEM, AudioManager.ADJUST_UNMUTE, 0)
            } else {
                Log.d("UnmuteReceiver", "Phone is already in normal mode")
            }
        } catch (e: Exception) {
            Log.e("UnmuteReceiver", "Error in onReceive: ${e.message}")
        }
    }
}
