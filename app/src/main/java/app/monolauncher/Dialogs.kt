package app.monolauncher

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog

@Composable
fun MenuItem(text: String, onClick: () -> Unit) {
    Text(
        text = text,
        color = Ui.fg,
        fontSize = 15.sp,
        fontWeight = FontWeight.Normal,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 24.dp, vertical = 14.dp),
    )
}

@Composable
private fun DarkCard(content: @Composable () -> Unit) {
    Column(
        Modifier
            .clip(Ui.shape)
            .background(Ui.card)
            .border(1.dp, Ui.line, Ui.shape)
            .padding(vertical = 12.dp)
    ) { content() }
}

/** Hosszú nyomás egy alkalmazáson. */
@Composable
fun AppMenuDialog(vm: LauncherViewModel, app: AppInfo) {
    val ctx = LocalContext.current
    val isFav = app.key in vm.favorites

    Dialog(onDismissRequest = { vm.menuApp = null }) {
        DarkCard {
            Text(
                text = app.label,
                color = Ui.fg,
                fontSize = 18.sp,
                fontWeight = FontWeight.Light,
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 14.dp),
            )
            MenuItem(if (isFav) "Eltávolítás a kedvencekből" else "Hozzáadás a kedvencekhez") {
                vm.toggleFavorite(app.key)
                vm.menuApp = null
            }
            if (isFav) {
                MenuItem("Feljebb") {
                    vm.moveFavoriteUp(app.key)
                    vm.menuApp = null
                }
            }
            MenuItem("Elrejtés") {
                vm.hide(app.key)
                vm.menuApp = null
            }
            MenuItem("Alkalmazás infó") {
                ctx.openAppInfo(app.packageName)
                vm.menuApp = null
            }
        }
    }
}

/** Alkalmazásválasztó (gesztusokhoz, elrejtettek visszahozásához). */
@Composable
fun AppPickerDialog(
    title: String,
    apps: List<AppInfo>,
    noneLabel: String?,
    onPick: (AppInfo?) -> Unit,
    onDismiss: () -> Unit,
) {
    Dialog(onDismissRequest = onDismiss) {
        DarkCard {
            Text(
                text = title,
                color = Ui.fg,
                fontSize = 18.sp,
                fontWeight = FontWeight.Light,
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 14.dp),
            )
            LazyColumn(Modifier.heightIn(max = 460.dp)) {
                if (noneLabel != null) {
                    item { MenuItem(noneLabel) { onPick(null) } }
                }
                items(apps, key = { it.key }) { app ->
                    MenuItem(app.label) { onPick(app) }
                }
            }
        }
    }
}
