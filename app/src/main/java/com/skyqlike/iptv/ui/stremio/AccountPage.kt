package com.skyqlike.iptv.ui.stremio

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Text
import com.skyqlike.iptv.data.stremio.StremioSession
import com.skyqlike.iptv.ui.common.ActionButton
import com.skyqlike.iptv.ui.common.LabeledField
import com.skyqlike.iptv.ui.theme.Palette
import kotlinx.coroutines.launch

/**
 * Sign in to Stremio and add addon links. Used for first-time setup and from Settings.
 * Movies and series in the app come only from these addons.
 */
@Composable
fun AccountPage(
    session: StremioSession,
    actions: StremioActions,
    canClose: Boolean,
    onClose: () -> Unit,
) {
    BackHandler(enabled = canClose) { onClose() }
    val scope = rememberCoroutineScope()
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var link by remember { mutableStateOf("") }
    var signMessage by remember { mutableStateOf<String?>(null) }
    var addMessage by remember { mutableStateOf<String?>(null) }
    var busySign by remember { mutableStateOf(false) }
    var busyAdd by remember { mutableStateOf(false) }

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = com.skyqlike.iptv.ui.common.pagePad(), vertical = 20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Text(
                if (session.hasAnything) "Account and addons" else "Let's get you set up",
                fontSize = 32.sp,
                fontWeight = FontWeight.Bold,
                color = Palette.TextPrimary,
            )
        }
        item {
            Text(
                "Everything you see in this app comes from your Stremio addons. " +
                    "Sign in to bring your addons across, or paste an addon link below.",
                fontSize = 15.sp,
                color = Palette.TextSecondary,
            )
        }

        if (session.signedIn) {
            item {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    Text(
                        "Signed in as ${session.email.ifBlank { "your Stremio account" }}",
                        fontSize = 17.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Palette.TextPrimary,
                        modifier = Modifier.weight(1f),
                    )
                    ActionButton("Sign out", Modifier.width(130.dp)) { actions.onSignOut() }
                }
            }
        } else {
            item { Text("Sign in with your Stremio account", fontSize = 18.sp, fontWeight = FontWeight.SemiBold, color = Palette.TextPrimary) }
            item { LabeledField("Email", email) { email = it } }
            item { LabeledField("Password", password, password = true) { password = it } }
            item {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    ActionButton(if (busySign) "Signing in..." else "Sign in", Modifier.width(160.dp), accent = true) {
                        if (!busySign) {
                            busySign = true
                            signMessage = null
                            scope.launch {
                                signMessage = actions.onSignIn(email, password)
                                password = ""
                                busySign = false
                            }
                        }
                    }
                    signMessage?.let { Text(it, fontSize = 15.sp, color = Palette.Accent, modifier = Modifier.weight(1f)) }
                }
            }
            item {
                Text(
                    "Your password is sent to Stremio once and is not saved here. If you sign in to Stremio with Facebook or Apple, add your addon links below instead.",
                    fontSize = 13.sp,
                    color = Palette.TextSecondary,
                )
            }
        }

        item { Text("Add an addon link", fontSize = 18.sp, fontWeight = FontWeight.SemiBold, color = Palette.TextPrimary) }
        item { LabeledField("Addon link (ends in manifest.json, or starts with stremio://)", link) { link = it } }
        item {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                ActionButton(if (busyAdd) "Checking..." else "Add addon", Modifier.width(160.dp)) {
                    if (!busyAdd) {
                        busyAdd = true
                        addMessage = null
                        scope.launch {
                            val error = actions.onAddAddon(link)
                            if (error == null) {
                                link = ""
                                addMessage = "Added."
                            } else {
                                addMessage = error
                            }
                            busyAdd = false
                        }
                    }
                }
                addMessage?.let { Text(it, fontSize = 15.sp, color = Palette.Accent, modifier = Modifier.weight(1f)) }
            }
        }

        if (session.manualAddons.isNotEmpty()) {
            item { Text("Addons you added", fontSize = 18.sp, fontWeight = FontWeight.SemiBold, color = Palette.TextPrimary) }
            items(session.manualAddons, key = { it }) { url ->
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(vertical = 2.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    // Links can contain a private key, so only the site name is shown.
                    Text(hostOf(url), fontSize = 16.sp, color = Palette.TextPrimary, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    ActionButton("Remove", Modifier.width(120.dp)) { actions.onRemoveAddon(url) }
                }
            }
        }

        item {
            Text(
                "Addon links can contain your debrid key, so they are kept only on this device.",
                fontSize = 13.sp,
                color = Palette.TextSecondary,
            )
        }
        if (canClose) {
            item { ActionButton("Done", Modifier.width(140.dp), accent = true) { onClose() } }
        }
    }
}

private fun hostOf(url: String): String =
    try {
        java.net.URI(url).host ?: "addon"
    } catch (e: Exception) {
        "addon"
    }
