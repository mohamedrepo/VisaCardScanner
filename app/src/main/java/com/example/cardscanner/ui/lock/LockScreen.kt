package com.example.cardscanner.ui.lock

import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import com.example.cardscanner.R
import com.example.cardscanner.security.SessionManager

/**
 * Lock screen (spec §5): biometric unlock when available, PIN fallback,
 * and first-run PIN setup.
 */
@Composable
fun LockScreen(
    session: SessionManager,
    onUnlocked: () -> Unit,
) {
    val context = LocalContext.current
    var pin by remember { mutableStateOf("") }
    var error by remember { mutableStateOf(false) }
    var confirmPin by remember { mutableStateOf("") }
    val isSetup = session.needsSetup
    val canBiometric = rememberBiometricAvailable()

    // Offer biometric unlock immediately when the app resumes locked.
    LaunchedEffect(isSetup, canBiometric) {
        if (!isSetup && canBiometric) {
            showBiometricPrompt(context) { ok -> if (ok) { session.unlock(); onUnlocked() } }
        }
    }

    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(
            modifier = Modifier.fillMaxSize().padding(32.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = stringResource(R.string.app_name),
                style = MaterialTheme.typography.headlineMedium,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = if (isSetup) stringResource(R.string.lock_setup_pin) else stringResource(R.string.lock_enter_pin),
                style = MaterialTheme.typography.bodyLarge,
            )
            Spacer(Modifier.height(24.dp))

            OutlinedTextField(
                value = pin,
                onValueChange = { pin = it.filter(Char::isDigit).take(12) },
                label = { Text(stringResource(R.string.lock_pin)) },
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                isError = error,
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )

            if (isSetup) {
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = confirmPin,
                    onValueChange = { confirmPin = it.filter(Char::isDigit).take(12) },
                    label = { Text(stringResource(R.string.lock_confirm_pin)) },
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                    isError = error,
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            Spacer(Modifier.height(16.dp))

            Button(
                onClick = {
                    if (isSetup) {
                        if (pin == confirmPin && session.setupPin(pin.toCharArray())) {
                            session.unlock()
                            onUnlocked()
                        } else {
                            error = true
                        }
                    } else if (session.verifyPin(pin.toCharArray())) {
                        session.unlock()
                        onUnlocked()
                    } else {
                        error = true
                    }
                },
                enabled = pin.length >= 4 && (!isSetup || confirmPin.length >= 4),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(stringResource(if (isSetup) R.string.lock_set_pin else R.string.lock_unlock))
            }

            if (!isSetup && canBiometric) {
                Spacer(Modifier.height(12.dp))
                Button(
                    onClick = {
                        showBiometricPrompt(context) { ok -> if (ok) { session.unlock(); onUnlocked() } }
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(stringResource(R.string.lock_biometric))
                }
            }

            if (error) {
                Spacer(Modifier.height(8.dp))
                Text(
                    text = stringResource(if (isSetup) R.string.lock_pin_mismatch else R.string.lock_pin_wrong),
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
    }
}

/** Returns true when device biometrics are enrolled and usable. */
@Composable
private fun rememberBiometricAvailable(): Boolean {
    val context = LocalContext.current
    return remember {
        val bm = BiometricManager.from(context)
        bm.canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_WEAK) ==
            BiometricManager.BIOMETRIC_SUCCESS
    }
}

/** Shows the system BiometricPrompt on a FragmentActivity host. */
private fun showBiometricPrompt(context: android.content.Context, onResult: (Boolean) -> Unit) {
    val activity = context as? FragmentActivity ?: return
    val executor = ContextCompat.getMainExecutor(context)
    val prompt = BiometricPrompt(
        activity,
        executor,
        object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                onResult(true)
            }

            override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                onResult(false)
            }
        },
    )
    prompt.authenticate(
        BiometricPrompt.PromptInfo.Builder()
            .setTitle(context.getString(R.string.lock_biometric_title))
            .setNegativeButtonText(context.getString(R.string.lock_biometric_negative))
            .build(),
    )
}
