package dev.knowledge82.swiftyproteins.ui

import androidx.activity.compose.BackHandler
import androidx.annotation.DrawableRes
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CutCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.autofill.ContentType
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.semantics.contentType
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import dev.knowledge82.swiftyproteins.R
import dev.knowledge82.swiftyproteins.auth.AuthController
import dev.knowledge82.swiftyproteins.auth.AuthDialog
import dev.knowledge82.swiftyproteins.auth.AuthScreen
import dev.knowledge82.swiftyproteins.auth.AuthUiState
import dev.knowledge82.swiftyproteins.auth.BiometricButton
import dev.knowledge82.swiftyproteins.auth.PasswordPolicy
import dev.knowledge82.swiftyproteins.auth.UsernamePolicy
import dev.knowledge82.swiftyproteins.ui.theme.SwiftyColors
import dev.knowledge82.swiftyproteins.ui.theme.SwiftyDataText
import dev.knowledge82.swiftyproteins.ui.theme.glowing

/**
 * The sign-in and create-account screens (document 05, section 12), in the cogitator style.
 *
 * Only drawing: every decision (what is valid, what the button does, which message) comes from [AuthUiState], built
 * by the [AuthController] and tested on the JVM. The form scrolls, so it also fits in landscape with the keyboard up.
 */
@Composable
fun AccessScreen(state: AuthUiState, controller: AuthController, modifier: Modifier = Modifier) {
    val create = state.screen == AuthScreen.CREATE
    // System Back on "create account" returns to sign-in when there is an account; otherwise Back leaves the app
    // (there is nothing behind the sign-in screen that may be shown without signing in).
    BackHandler(enabled = create && state.hasAccounts) { controller.showSignIn() }

    CogitatorBackground(modifier) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            HeaderPanel(subtitle = if (create) "ADEPT REGISTRY // NEW ENTRY" else "NOOSPHERE ACCESS // IDENTIFY")
            TitlePlate(
                title = if (create) "Register, Adept" else "Identify, Adept",
                subtitle = if (create) "// CREATE ACCOUNT //" else "// SIGN IN //",
            )
            when (state.screen) {
                AuthScreen.LOADING -> CogitatorPanel(label = "ARCHIVE") {
                    CenteredColumn {
                        BobbingArt(R.drawable.art_servo_skull, size = 120.dp)
                        Text("Reading the archive...", style = MaterialTheme.typography.bodyMedium, color = SwiftyColors.Bone)
                    }
                }
                AuthScreen.SIGN_IN -> SignInForm(state, controller)
                AuthScreen.CREATE -> CreateForm(state, controller)
            }
            StatusFooter(line = "NOOSPHERE LOCK // ENGAGED", detail = "01001100 :: IDENTIFY TO PROCEED")
        }
    }
}

@Composable
private fun SignInForm(state: AuthUiState, controller: AuthController) {
    val focus = LocalFocusManager.current
    CogitatorPanel(label = "ADEPT IDENTIFICATION") { FigureBehindForm(R.drawable.art_servitor) {
        ConsoleField(
            value = state.username,
            onValueChange = controller::onUsernameChange,
            label = "User name",
            enabled = !state.busy,
            contentType = ContentType.Username,
            keyboardOptions = usernameKeyboard,
        )
        Spacer(Modifier.height(10.dp))
        PasswordField(
            value = state.password,
            onValueChange = controller::onPasswordChange,
            label = "Password",
            enabled = !state.busy,
            contentType = ContentType.Password,
            imeAction = ImeAction.Done,
            onDone = {
                focus.clearFocus()
                controller.signIn()
            },
        )
        FormMessage(state)
        Spacer(Modifier.height(14.dp))
        PrimaryButton("Sign in", onClick = { focus.clearFocus(); controller.signIn() }, enabled = state.canSignIn, modifier = Modifier.fillMaxWidth())
        Spacer(Modifier.height(10.dp))
        when (state.biometricButton) {
            BiometricButton.SHOWN -> SecondaryButton(
                "Bio-auspex scan",
                onClick = { focus.clearFocus(); controller.startBiometricSignIn() },
                enabled = !state.busy,
                modifier = Modifier.fillMaxWidth(),
            )
            // Hidden, but never silently: one line says why (the subject: "hidden or disabled").
            BiometricButton.HIDDEN -> state.biometricNote?.let {
                Text(it, style = MaterialTheme.typography.bodySmall, color = SwiftyColors.Ash, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
            }
        }
        Spacer(Modifier.height(4.dp))
        TextButton(onClick = controller::showCreate, enabled = !state.busy, modifier = Modifier.align(Alignment.CenterHorizontally)) {
            Text("Create account", style = MaterialTheme.typography.labelLarge, color = SwiftyColors.Gold)
        }
    } }
}

@Composable
private fun CreateForm(state: AuthUiState, controller: AuthController) {
    val focus = LocalFocusManager.current
    CogitatorPanel(label = "NEW ADEPT REGISTRY") { FigureBehindForm(R.drawable.art_tech_priest) {
        ConsoleField(
            value = state.username,
            onValueChange = controller::onUsernameChange,
            label = "User name",
            enabled = !state.busy,
            contentType = ContentType.Username,
            keyboardOptions = usernameKeyboard,
            isError = state.usernameProblem != null,
        )
        state.usernameProblem?.let { Hint(UsernamePolicy.message(it), ok = false) }
        Spacer(Modifier.height(10.dp))
        PasswordField(
            value = state.password,
            onValueChange = controller::onPasswordChange,
            label = "Password",
            enabled = !state.busy,
            contentType = ContentType.NewPassword,
            imeAction = ImeAction.Next,
            onDone = {},
        )
        // The checklist: each rule lights up while typing. "[OK]" / "[--]" in the TEXT, not only the colour.
        Spacer(Modifier.height(6.dp))
        val unmet = state.unmetRules
        for (rule in PasswordPolicy.Rule.values()) Hint(rule.label, ok = rule !in unmet)
        Spacer(Modifier.height(10.dp))
        PasswordField(
            value = state.confirmation,
            onValueChange = controller::onConfirmationChange,
            label = "Repeat the password",
            enabled = !state.busy,
            contentType = ContentType.NewPassword,
            imeAction = ImeAction.Done,
            onDone = {
                focus.clearFocus()
                controller.create()
            },
            isError = state.confirmationDiffers,
        )
        if (state.confirmationDiffers) Hint("The two passwords are different.", ok = false)
        FormMessage(state)
        Spacer(Modifier.height(14.dp))
        PrimaryButton("Create account", onClick = { focus.clearFocus(); controller.create() }, enabled = state.canCreate, modifier = Modifier.fillMaxWidth())
        if (state.hasAccounts) {
            Spacer(Modifier.height(10.dp))
            SecondaryButton("Back to sign in", onClick = controller::showSignIn, enabled = !state.busy, modifier = Modifier.fillMaxWidth())
        }
    } }
}

/**
 * The painted figure, LARGE and BEHIND the form: the fields are drawn over its lower part (they have an opaque
 * console fill, so they stay readable). A gradient darkens the lower part of the figure, so the checklist and the
 * messages written over it keep their contrast, while the upper part (head, console, staff) is seen whole.
 *
 * The art is about 424 px wide; at [FIGURE_SIZE] on a 2.6x screen it is enlarged about 2x, so a little soft
 * (document 05a, 8.3). Larger art from the generator would fix it.
 */
@Composable
private fun FigureBehindForm(@DrawableRes art: Int, content: @Composable ColumnScope.() -> Unit) {
    Box(Modifier.fillMaxWidth()) {
        ArtImage(art, size = FIGURE_SIZE, modifier = Modifier.align(Alignment.TopCenter))
        Box(
            Modifier
                .align(Alignment.TopCenter)
                .size(FIGURE_SIZE)
                .background(Brush.verticalGradient(0.45f to Color.Transparent, 1f to SwiftyColors.Console.copy(alpha = 0.92f))),
        )
        Column(Modifier.fillMaxWidth().padding(top = FORM_TOP), content = content)
    }
}

private val FIGURE_SIZE = 330.dp
// Where the form starts over the figure: the head and the console (or the hood and the staff) stay above the fields.
private val FORM_TOP = 205.dp

/** "Verifying..." while a hash runs, or the error of the last attempt. */
@Composable
private fun FormMessage(state: AuthUiState) {
    when {
        state.busy -> Row(modifier = Modifier.padding(top = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("> VERIFYING", style = SwiftyDataText.medium.glowing(SwiftyColors.Phosphor), color = SwiftyColors.Phosphor)
            BlinkingCursor()
        }
        state.formError != null -> Text(
            state.formError,
            style = MaterialTheme.typography.bodyMedium.glowing(SwiftyColors.OpticRed, 0.3f),
            color = SwiftyColors.OpticRed,
            modifier = Modifier.padding(top = 10.dp),
        )
    }
}

/** One line of the checklist or a field hint: "[OK] 8 to 64 characters" in phosphor, "[--] ..." in amber. */
@Composable
private fun Hint(text: String, ok: Boolean) {
    Text(
        (if (ok) "[OK] " else "[--] ") + text,
        style = SwiftyDataText.small,
        color = if (ok) SwiftyColors.Phosphor else SwiftyColors.Amber,
        modifier = Modifier.padding(top = 2.dp),
    )
}

// The user name: plain ASCII keyboard, no capital letter, no correction (a "correction" would change the name).
private val usernameKeyboard = KeyboardOptions(
    capitalization = KeyboardCapitalization.None,
    autoCorrectEnabled = false,
    keyboardType = KeyboardType.Ascii,
    imeAction = ImeAction.Next,
)

/** A password field: the Password keyboard (no suggestions, nothing learned), hidden text with a SHOW/HIDE key. */
@Composable
private fun PasswordField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    enabled: Boolean,
    contentType: ContentType,
    imeAction: ImeAction,
    onDone: () -> Unit,
    isError: Boolean = false,
) {
    var visible by remember { mutableStateOf(false) }
    ConsoleField(
        value = value,
        onValueChange = onValueChange,
        label = label,
        enabled = enabled,
        contentType = contentType,
        isError = isError,
        keyboardOptions = KeyboardOptions(autoCorrectEnabled = false, keyboardType = KeyboardType.Password, imeAction = imeAction),
        keyboardActions = KeyboardActions(onDone = { onDone() }),
        visualTransformation = if (visible) VisualTransformation.None else PasswordVisualTransformation(),
        trailing = {
            TextButton(onClick = { visible = !visible }) {
                Text(if (visible) "HIDE" else "SHOW", style = SwiftyDataText.small, color = SwiftyColors.Gold)
            }
        },
    )
}

/** A text field in console style (the same look as the search box of the list). */
@Composable
private fun ConsoleField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    enabled: Boolean,
    contentType: ContentType,
    keyboardOptions: KeyboardOptions,
    isError: Boolean = false,
    keyboardActions: KeyboardActions = KeyboardActions(),
    visualTransformation: VisualTransformation = VisualTransformation.None,
    trailing: (@Composable () -> Unit)? = null,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        enabled = enabled,
        singleLine = true,
        isError = isError,
        // contentType: lets a password manager offer to fill (or save) the name and the password.
        modifier = Modifier.fillMaxWidth().semantics { this.contentType = contentType },
        textStyle = SwiftyDataText.medium.glowing(SwiftyColors.Phosphor),
        label = { Text(label, style = SwiftyDataText.small) },
        keyboardOptions = keyboardOptions,
        keyboardActions = keyboardActions,
        visualTransformation = visualTransformation,
        trailingIcon = trailing,
        shape = CutCornerShape(6.dp),
        colors = OutlinedTextFieldDefaults.colors(
            focusedTextColor = SwiftyColors.Phosphor,
            unfocusedTextColor = SwiftyColors.Phosphor,
            focusedContainerColor = SwiftyColors.Abyss,
            unfocusedContainerColor = SwiftyColors.Abyss,
            disabledContainerColor = SwiftyColors.Abyss,
            focusedBorderColor = SwiftyColors.Phosphor,
            unfocusedBorderColor = SwiftyColors.Bronze,
            errorBorderColor = SwiftyColors.Amber,
            cursorColor = SwiftyColors.Phosphor,
            focusedLabelColor = SwiftyColors.Phosphor,
            unfocusedLabelColor = SwiftyColors.Ash,
            errorLabelColor = SwiftyColors.Amber,
        ),
    )
}

/**
 * The dialogs of the sign-in flow: a message ("Account created", a biometric error...) or the fingerprint offer.
 * Drawn by SwiftyApp OVER EVERYTHING, because some appear right after unlocking, on top of the list.
 */
@Composable
fun AuthDialogs(state: AuthUiState, controller: AuthController) {
    when (val dialog = state.dialog) {
        null -> Unit
        is AuthDialog.Message -> Dialog(onDismissRequest = controller::closeMessage) {
            CogitatorPanel(label = "COGITATOR NOTICE", cut = 11.dp, modifier = Modifier.widthIn(max = 440.dp)) {
                CenteredColumn {
                    Text(dialog.text.title, style = MaterialTheme.typography.titleLarge.glowing(SwiftyColors.Gold, 0.3f), color = SwiftyColors.Gold, textAlign = TextAlign.Center)
                    Spacer(Modifier.height(8.dp))
                    Text(dialog.text.message, style = MaterialTheme.typography.bodyMedium, color = SwiftyColors.Bone, textAlign = TextAlign.Center)
                    Spacer(Modifier.height(18.dp))
                    PrimaryButton("OK", onClick = controller::closeMessage, modifier = Modifier.fillMaxWidth().padding(horizontal = 32.dp))
                }
            }
        }
        is AuthDialog.OfferBiometric -> Dialog(
            onDismissRequest = controller::declineBiometricOffer,
            properties = DialogProperties(dismissOnClickOutside = false),   // a stray tap must not answer for the user
        ) {
            CogitatorPanel(label = "BIO-AUSPEX", cut = 11.dp, modifier = Modifier.widthIn(max = 440.dp)) {
                CenteredColumn {
                    ArtImage(R.drawable.art_servo_skull, size = 110.dp)
                    Spacer(Modifier.height(8.dp))
                    Text("Enable Bio-Auspex?", style = MaterialTheme.typography.titleLarge.glowing(SwiftyColors.Gold, 0.3f), color = SwiftyColors.Gold, textAlign = TextAlign.Center)
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "Sign in as ${dialog.username} with your fingerprint next time. Your password keeps working.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = SwiftyColors.Bone,
                        textAlign = TextAlign.Center,
                    )
                    Spacer(Modifier.height(18.dp))
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        PrimaryButton("Enable", onClick = controller::acceptBiometricOffer, modifier = Modifier.weight(1f))
                        SecondaryButton("Not now", onClick = controller::declineBiometricOffer, modifier = Modifier.weight(1f))
                    }
                }
            }
        }
    }
}
