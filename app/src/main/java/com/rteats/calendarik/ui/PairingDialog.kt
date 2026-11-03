package com.rteats.calendarik.ui

import android.graphics.Bitmap
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.unit.dp
import com.google.zxing.BarcodeFormat
import com.google.zxing.MultiFormatWriter
import com.journeyapps.barcodescanner.ScanContract
import com.journeyapps.barcodescanner.ScanOptions
import com.rteats.calendarik.data.Authors
import com.rteats.calendarik.sync.JoinRequest
import com.rteats.calendarik.sync.LocalPair
import com.rteats.calendarik.sync.PairManager
import kotlinx.coroutines.launch

/** QR is generated and scanned locally: it contains the sensitive pair key. */
@Composable
fun PairingDialog(
    manager: PairManager,
    onClose: () -> Unit,
    onPairChanged: (LocalPair?) -> Unit,
) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    var signedIn by remember { mutableStateOf(manager.auth.currentUser != null) }
    var pair by remember { mutableStateOf(manager.current()) }
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var creatorRole by remember { mutableIntStateOf(Authors.SASHA) }
    var invitation by remember { mutableStateOf("") }
    var joinText by remember { mutableStateOf("") }
    var requests by remember { mutableStateOf(emptyList<JoinRequest>()) }
    var busy by remember { mutableStateOf(false) }
    var feedback by remember { mutableStateOf("") }

    val scanLauncher = rememberLauncherForActivityResult(ScanContract()) { result ->
        if (!result.contents.isNullOrBlank()) joinText = result.contents
    }

    fun action(task: suspend () -> Unit) {
        if (busy) return
        scope.launch {
            busy = true
            feedback = ""
            try {
                task()
                signedIn = manager.auth.currentUser != null
                pair = manager.current()
                onPairChanged(pair)
            } catch (error: Exception) {
                feedback = error.localizedMessage ?: "Не удалось выполнить действие"
            } finally {
                busy = false
            }
        }
    }

    AlertDialog(
        onDismissRequest = onClose,
        title = { Text("Синхронизация календарей") },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth().heightIn(max = 520.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(9.dp),
            ) {
                if (!signedIn) {
                    Text("Войдите или создайте отдельный аккаунт на каждом телефоне.")
                    OutlinedTextField(
                        value = email,
                        onValueChange = { email = it },
                        label = { Text("Электронная почта") },
                        singleLine = true,
                    )
                    OutlinedTextField(
                        value = password,
                        onValueChange = { password = it },
                        label = { Text("Пароль (не менее 6 символов)") },
                        singleLine = true,
                        visualTransformation = androidx.compose.ui.text.input.PasswordVisualTransformation(),
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(enabled = !busy && email.isNotBlank() && password.isNotBlank(), onClick = {
                            action { manager.signIn(email, password) }
                        }) { Text("Войти") }
                        TextButton(enabled = !busy && email.isNotBlank() && password.length >= 6, onClick = {
                            action { manager.register(email, password) }
                        }) { Text("Создать") }
                    }
                } else {
                    Text("Аккаунт: " + (manager.auth.currentUser?.email ?: "без почты"))
                    TextButton(onClick = {
                        val uid = manager.auth.currentUser?.uid.orEmpty()
                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        clipboard.setPrimaryClip(ClipData.newPlainText("Firebase UID", uid))
                    }) { Text("Скопировать мой Firebase UID") }
                    if (pair == null) {
                        Text("На первом телефоне создайте пару. На втором отсканируйте QR.")
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            for (role in listOf(Authors.SASHA, Authors.NASTYA)) {
                                FilterChip(
                                    selected = creatorRole == role,
                                    onClick = { creatorRole = role },
                                    label = { Text(Authors.label(role)) },
                                )
                            }
                        }
                        Button(enabled = !busy, onClick = {
                            action { invitation = manager.create(creatorRole) }
                        }) { Text("Создать пару") }
                        Text("Или присоединитесь по приглашению:")
                        Button(enabled = !busy, onClick = {
                            scanLauncher.launch(
                                ScanOptions().setDesiredBarcodeFormats(ScanOptions.QR_CODE)
                                    .setPrompt("Наведите камеру на QR-код другого телефона")
                                    .setBeepEnabled(false).setOrientationLocked(false)
                            )
                        }) { Text("Сканировать QR") }
                        OutlinedTextField(
                            value = joinText,
                            onValueChange = { joinText = it },
                            label = { Text("Код приглашения (можно вставить)") },
                            maxLines = 3,
                        )
                        Button(enabled = !busy && joinText.isNotBlank(), onClick = {
                            action { manager.requestJoin(joinText) }
                        }) { Text("Отправить запрос") }
                    } else {
                        Text(
                            if (pair?.active == true) "Два устройства связаны. Синхронизация включена."
                            else "Ожидается подтверждение пары на другом телефоне."
                        )
                        val localPair = pair
                        if (localPair != null && localPair.isCreator && !localPair.active) {
                            val inviteCode = if (invitation.isBlank()) manager.invitation() else invitation
                            Text("Покажите этот QR-код только Насте или Саше. Он содержит ключ шифрования.")
                            val image = remember(inviteCode) { qrBitmap(inviteCode) }
                            Image(
                                bitmap = image.asImageBitmap(),
                                contentDescription = "QR-код приглашения с ключом шифрования",
                                modifier = Modifier.size(224.dp),
                            )
                            TextButton(enabled = !busy, onClick = {
                                action { requests = manager.requests() }
                            }) { Text("Проверить запросы на подключение") }
                            for (request in requests) {
                                Text("Запрос: " + request.email + " (" + request.uid.take(8) + "…)")
                                Button(enabled = !busy, onClick = {
                                    action { manager.approve(request.uid); requests = emptyList() }
                                }) { Text("Подтвердить этого пользователя") }
                            }
                        }
                        if (localPair != null && !localPair.isCreator && !localPair.active) {
                            Text("После подтверждения нажмите «Проверить подключение».")
                        }
                        TextButton(enabled = !busy, onClick = {
                            action { manager.refresh() }
                        }) { Text("Проверить подключение") }
                    }
                    TextButton(enabled = !busy, onClick = {
                        manager.signOut()
                        signedIn = false
                        pair = null
                        onPairChanged(null)
                    }) { Text("Выйти из аккаунта") }
                }
                if (busy) CircularProgressIndicator()
                if (feedback.isNotBlank()) Text(feedback, color = androidx.compose.material3.MaterialTheme.colorScheme.error)
                Text("Ключ хранится на этих телефонах. При потере обоих устройств восстановление зашифрованных данных невозможно.")
            }
        },
        confirmButton = { TextButton(onClick = onClose) { Text("Закрыть") } },
    )
}

private fun qrBitmap(text: String): Bitmap {
    val size = 512
    val matrix = MultiFormatWriter().encode(text, BarcodeFormat.QR_CODE, size, size)
    val colors = IntArray(size * size) { offset ->
        val x = offset % size
        val y = offset / size
        if (matrix.get(x, y)) android.graphics.Color.BLACK else android.graphics.Color.WHITE
    }
    return Bitmap.createBitmap(colors, size, size, Bitmap.Config.ARGB_8888)
}
