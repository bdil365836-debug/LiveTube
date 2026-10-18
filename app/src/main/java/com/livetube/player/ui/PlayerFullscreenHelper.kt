package com.livetube.player.ui

import android.app.Activity
import android.app.Dialog
import android.content.pm.ActivityInfo
import android.view.ViewGroup
import androidx.annotation.OptIn
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.media3.common.util.UnstableApi
import androidx.media3.ui.PlayerView

/**
 * يفعّل زر "ملء الشاشة" في PlayerView (Media3) ويشغّله.
 *
 * - ينقل نفس الـ PlayerView ونفس الـ player إلى نافذة ملء الشاشة، فالتشغيل يكمل
 *   ورابط اليوتيوب ما يتحمّل من جديد.
 * - الفيديو الأفقي (يوتيوب العادي) يفتح landscape، والعمودي (Shorts) يبقى portrait.
 * - زر الرجوع يطلّعك من ملء الشاشة ويرجّع كل شي مثل ما كان.
 *
 * الاستخدام داخل الـ Fragment:
 *   onViewCreated -> helper = PlayerFullscreenHelper(requireActivity(), binding.playerView)
 *   onDestroyView -> helper.release()
 */
@OptIn(UnstableApi::class)
class PlayerFullscreenHelper(
    private val activity: Activity,
    private val playerView: PlayerView
) {

    private var dialog: Dialog? = null
    private lateinit var originalParent: ViewGroup
    private lateinit var originalParams: ViewGroup.LayoutParams
    private var originalIndex = 0
    private var originalOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED

    val isFullscreen: Boolean
        get() = dialog != null

    init {
        // تسجيل الـ listener هو اللي يخلّي زر ملء الشاشة يظهر في الكنترولر
        playerView.setFullscreenButtonClickListener {
            if (isFullscreen) exit() else enter()
        }
    }

    fun enter() {
        if (dialog != null) return
        val parent = playerView.parent as? ViewGroup ?: return

        originalParent = parent
        originalIndex = parent.indexOfChild(playerView)
        originalParams = playerView.layoutParams
        originalOrientation = activity.requestedOrientation

        // يوتيوب العادي أفقي -> landscape. الـ Shorts عمودي -> نخليه portrait
        val size = playerView.player?.videoSize
        val isVertical = size != null && size.height > size.width
        if (!isVertical) {
            activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
        }

        parent.removeView(playerView)

        val newDialog = Dialog(activity, android.R.style.Theme_Black_NoTitleBar_Fullscreen)
        newDialog.setContentView(
            playerView,
            ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        )
        // زر الرجوع يسكّر الـ Dialog، فنرجّع الـ player مكانه
        newDialog.setOnDismissListener { exit() }
        dialog = newDialog
        newDialog.show()

        // إخفاء شريط الحالة وأزرار النظام (تطلع بسحبة من الحافة)
        newDialog.window?.let { window ->
            WindowInsetsControllerCompat(window, window.decorView).apply {
                systemBarsBehavior =
                    WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                hide(WindowInsetsCompat.Type.systemBars())
            }
        }

        playerView.setFullscreenButtonState(true)
    }

    fun exit() {
        val current = dialog ?: return
        dialog = null // نصفّره أول شي عشان الـ OnDismissListener ما يكرر الاستدعاء

        (playerView.parent as? ViewGroup)?.removeView(playerView)
        current.dismiss()

        originalParent.addView(
            playerView,
            originalIndex.coerceAtMost(originalParent.childCount),
            originalParams
        )

        playerView.setFullscreenButtonState(false)
        activity.requestedOrientation = originalOrientation
    }

    /** استدعه من onDestroyView */
    fun release() = exit()
}
