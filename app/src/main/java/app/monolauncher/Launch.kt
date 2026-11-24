package app.monolauncher

import android.app.ActivityOptions
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import android.view.View
import androidx.compose.ui.geometry.Rect

/**
 * Alkalmazás indítása. Ha ismerjük az ikon helyét, az app onnan "nyílik ki" (kinagyodó animáció),
 * különben sima áttűnés. Igaz értéket ad vissza, ha az indítás sikerült.
 */
fun Context.launchApp(key: String, view: View? = null, from: Rect? = null): Boolean {
    val cn = ComponentName.unflattenFromString(key) ?: return false
    val intent = Intent(Intent.ACTION_MAIN)
        .addCategory(Intent.CATEGORY_LAUNCHER)
        .setComponent(cn)
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)
    return safeStart(intent, view, from)
}

fun Context.openAppInfo(packageName: String) {
    val intent = Intent(
        Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
        Uri.fromParts("package", packageName, null)
    ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    safeStart(intent)
}

fun Context.safeStart(intent: Intent, view: View? = null, from: Rect? = null): Boolean {
    return try {
        val options = if (view != null && from != null) {
            ActivityOptions.makeScaleUpAnimation(
                view,
                from.left.toInt(),
                from.top.toInt(),
                from.width.toInt().coerceAtLeast(1),
                from.height.toInt().coerceAtLeast(1),
            )
        } else {
            ActivityOptions.makeCustomAnimation(this, android.R.anim.fade_in, android.R.anim.fade_out)
        }
        startActivity(intent, options.toBundle())
        true
    } catch (e: Exception) {
        false
    }
}
