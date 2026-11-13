package com.zwz.offlineledger.ui.lock

import android.content.Context
import android.content.ContextWrapper
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import com.zwz.offlineledger.core.prefs.LedgerPrefs
import com.zwz.offlineledger.ui.theme.LedgerColors

/** 解锁前不渲染任何账目：这里只有锁屏本身，没有盖在 LedgerApp 上的遮罩。 */
@Composable
fun AppLockScreen(
    prefs: LedgerPrefs,
    onUnlocked: () -> Unit,
    onPrefsChanged: () -> Unit = {}
) {
    val context = LocalContext.current
    val activity = remember(context) { context.findFragmentActivity() }
    var pin by remember { mutableStateOf("") }
    var error by remember { mutableStateOf("") }

    val canBiometric = remember(context) {
        activity != null && BiometricManager.from(context).canAuthenticate(
            BiometricManager.Authenticators.BIOMETRIC_STRONG
        ) == BiometricManager.BIOMETRIC_SUCCESS
    }

    Column(
        modifier = Modifier.fillMaxSize().padding(28.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(text = "离线账本", style = MaterialTheme.typography.headlineMedium)
        Text(
            text = "账本已锁定。验证身份后查看账目。",
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(top = 8.dp, bottom = 20.dp)
        )

        OutlinedTextField(
            value = pin,
            onValueChange = { pin = it.filter(Char::isDigit); error = "" },
            label = { Text("PIN") },
            singleLine = true,
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                keyboardType = KeyboardType.NumberPassword
            ),
            modifier = Modifier.fillMaxWidth()
        )

        Button(
            onClick = {
                if (prefs.verifyPin(pin)) {
                    onUnlocked()
                } else {
                    error = "PIN 不正确"
                }
            },
            modifier = Modifier.fillMaxWidth().padding(top = 12.dp)
        ) { Text("用 PIN 解锁") }

        if (canBiometric && activity != null) {
            Button(
                onClick = {
                    error = ""
                    val prompt = BiometricPrompt(
                        activity,
                        ContextCompat.getMainExecutor(context),
                        object : BiometricPrompt.AuthenticationCallback() {
                            override fun onAuthenticationSucceeded(
                                result: BiometricPrompt.AuthenticationResult
                            ) {
                                onUnlocked()
                            }

                            override fun onAuthenticationError(
                                errorCode: Int, errString: CharSequence
                            ) {
                                error = errString.toString()
                            }

                            override fun onAuthenticationFailed() {
                                error = "验证未通过，请重试"
                            }
                        }
                    )
                    val info = BiometricPrompt.PromptInfo.Builder()
                        .setTitle("解锁离线账本")
                        .setSubtitle("验证身份后查看账目")
                        .setNegativeButtonText("改用 PIN")
                        .setAllowedAuthenticators(
                            BiometricManager.Authenticators.BIOMETRIC_STRONG
                        )
                        .build()
                    prompt.authenticate(info)
                },
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
            ) { Text("使用生物识别") }
        }

        if (error.isNotBlank()) {
            Text(
                text = error,
                color = LedgerColors.ExpenseRed,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(top = 12.dp)
            )
        }

        Text(
            text = "本锁用于防止旁人瞥视或手机丢失后被随手翻看；" +
                "账本文件本身由 SQLCipher 与 Android Keystore 加密保护。",
            style = MaterialTheme.typography.labelSmall,
            color = LedgerColors.Neutral,
            modifier = Modifier.padding(top = 28.dp)
        )
    }
}

/** LocalContext 可能是 ContextThemeWrapper，向上遍历找到 FragmentActivity。 */
private fun Context.findFragmentActivity(): FragmentActivity? {
    var current: Context? = this
    while (current != null) {
        if (current is FragmentActivity) return current
        current = (current as? ContextWrapper)?.baseContext ?: return null
    }
    return null
}
