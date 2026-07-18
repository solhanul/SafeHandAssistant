package kr.co.safehand.assistant

import android.content.Context

/** 앱과 접근성 서비스가 함께 쓰는 간단한 사용자 설정 저장소. */
class AssistantPreferences(context: Context) {
    private val preferences = context.applicationContext.getSharedPreferences("assistant_preferences", Context.MODE_PRIVATE)

    var speechRate: Float
        get() = preferences.getFloat(KEY_SPEECH_RATE, 1.0f)
        set(value) = preferences.edit().putFloat(KEY_SPEECH_RATE, value).apply()

    var speechVolume: Float
        get() = preferences.getFloat(KEY_SPEECH_VOLUME, 1.0f)
        set(value) = preferences.edit().putFloat(KEY_SPEECH_VOLUME, value).apply()

    var floatingButtonEnabled: Boolean
        get() = preferences.getBoolean(KEY_FLOATING_BUTTON, true)
        set(value) = preferences.edit().putBoolean(KEY_FLOATING_BUTTON, value).apply()

    var floatingButtonX: Int
        get() = preferences.getInt(KEY_FLOATING_BUTTON_X, -1)
        set(value) = preferences.edit().putInt(KEY_FLOATING_BUTTON_X, value).apply()

    var floatingButtonY: Int
        get() = preferences.getInt(KEY_FLOATING_BUTTON_Y, -1)
        set(value) = preferences.edit().putInt(KEY_FLOATING_BUTTON_Y, value).apply()

    var onboardingCompleted: Boolean
        get() = preferences.getBoolean(KEY_ONBOARDING_COMPLETED, false)
        set(value) = preferences.edit().putBoolean(KEY_ONBOARDING_COMPLETED, value).apply()

    fun registerListener(listener: android.content.SharedPreferences.OnSharedPreferenceChangeListener) = preferences.registerOnSharedPreferenceChangeListener(listener)
    fun unregisterListener(listener: android.content.SharedPreferences.OnSharedPreferenceChangeListener) = preferences.unregisterOnSharedPreferenceChangeListener(listener)

    companion object {
        const val KEY_FLOATING_BUTTON = "floating_button_enabled"
        private const val KEY_FLOATING_BUTTON_X = "floating_button_x"
        private const val KEY_FLOATING_BUTTON_Y = "floating_button_y"
        private const val KEY_SPEECH_RATE = "speech_rate"
        private const val KEY_SPEECH_VOLUME = "speech_volume"
        private const val KEY_ONBOARDING_COMPLETED = "onboarding_completed"
    }
}
