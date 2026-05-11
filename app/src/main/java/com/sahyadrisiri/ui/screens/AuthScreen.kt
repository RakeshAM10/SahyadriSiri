package com.sahyadrisiri.ui.screens

import android.app.Activity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.graphics.*
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.*
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.ApiException
import com.sahyadrisiri.ui.glass.*
import com.sahyadrisiri.ui.theme.*
import kotlin.math.sin

// ─────────────────────────────────────────────────────────────────────────────
//  AuthScreen — top-level entry point
// ─────────────────────────────────────────────────────────────────────────────

@Composable
fun AuthScreen(
    onSignIn: (email: String, password: String) -> Unit,
    onSignUp: (name: String, email: String, password: String) -> Unit,
    onGoogleAuthSuccess: (idToken: String) -> Unit,
    isLoading: Boolean = false,
    globalError: String? = null,
    onClearError: () -> Unit = {},
    onGuestAccess: () -> Unit = {}
) {
    var mode by remember { mutableStateOf(AuthMode.SIGN_IN) }

    Box(
        Modifier
            .fillMaxSize()
            .background(Background)
    ) {
        // Subtle water-toned gradient mesh behind everything
        Box(
            Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        listOf(
                            Color(0xFFE8F4FF).copy(0.60f),
                            Background,
                            Color(0xFFE5F5EA).copy(0.30f)
                        )
                    )
                )
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(top = 64.dp, bottom = 40.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // ── Logo ──────────────────────────────────────────────────────
            AuthLogoBox()
            Spacer(Modifier.height(20.dp))
            Text(
                "SahyadriSiri",
                fontSize = 28.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = (-0.5).sp,
                color = Neutral800
            )
            Spacer(Modifier.height(6.dp))
            Text(
                "Guardian of the Western Ghats",
                fontSize = 15.sp,
                color = Neutral400,
                fontWeight = FontWeight.Medium
            )
            Spacer(Modifier.height(36.dp))

            // ── Mode toggle pill ──────────────────────────────────────────
            AuthModePill(mode = mode, onModeChange = {
                mode = it
                onClearError()
            })
            Spacer(Modifier.height(28.dp))

            // ── Form card ─────────────────────────────────────────────────
            GlassPanel(
                modifier = Modifier.fillMaxWidth(),
                cornerRadius = 32.dp,
                tintBrush = Brush.verticalGradient(
                    listOf(Color.White.copy(0.90f), Color.White.copy(0.70f))
                )
            ) {
                Column(Modifier.padding(24.dp)) {
                    AnimatedContent(
                        targetState = mode,
                        transitionSpec = {
                            (fadeIn(tween(220)) + slideInHorizontally(
                                tween(220),
                                initialOffsetX = { if (targetState == AuthMode.SIGN_IN) -it / 4 else it / 4 }
                            )).togetherWith(
                                fadeOut(tween(180)) + slideOutHorizontally(
                                    tween(180),
                                    targetOffsetX = { if (targetState == AuthMode.SIGN_IN) it / 4 else -it / 4 }
                                )
                            )
                        },
                        label = "auth_form"
                    ) { currentMode ->
                        when (currentMode) {
                            AuthMode.SIGN_IN -> SignInForm(
                                isLoading = isLoading,
                                globalError = globalError,
                                onSubmit = { email, password ->
                                    onSignIn(email, password)
                                },
                                onClearError = onClearError
                            )
                            AuthMode.SIGN_UP -> SignUpForm(
                                isLoading = isLoading,
                                globalError = globalError,
                                onSubmit = { name, email, password ->
                                    onSignUp(name, email, password)
                                },
                                onClearError = onClearError
                            )
                        }
                    }
                }
            }

            Spacer(Modifier.height(20.dp))

            // ── Divider ───────────────────────────────────────────────────
            OrDivider()
            Spacer(Modifier.height(20.dp))

            // ── Google button ─────────────────────────────────────────────
            GoogleSignInButton(
                isLoading = isLoading,
                onSuccess = { idToken ->
                    onGoogleAuthSuccess(idToken)
                },
                onError = { /* Let viewmodel handle or handle locally if needed */ }
            )

            Spacer(Modifier.height(24.dp))

            // ── Guest Access ──────────────────────────────────────────────
            Text(
                "Continue as Guest",
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
                color = Blue007AFF,
                modifier = Modifier.glassClickable { onGuestAccess() }
            )

            Spacer(Modifier.height(32.dp))

            // ── Footer ────────────────────────────────────────────────────
            Text(
                "By continuing you agree to our Terms of Service\nand Privacy Policy",
                fontSize = 12.sp,
                color = Neutral400,
                textAlign = TextAlign.Center,
                lineHeight = 18.sp
            )
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
//  Sign In Form
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun SignInForm(
    isLoading: Boolean,
    globalError: String?,
    onSubmit: (email: String, password: String) -> Unit,
    onClearError: () -> Unit
) {
    val focusManager = LocalFocusManager.current
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var emailError by remember { mutableStateOf<String?>(null) }
    var passwordError by remember { mutableStateOf<String?>(null) }
    var passwordVisible by remember { mutableStateOf(false) }

    fun validate(): Boolean {
        emailError = if (!email.isValidEmail()) "Enter a valid email address" else null
        passwordError = if (password.length < 6) "Password must be at least 6 characters" else null
        return emailError == null && passwordError == null
    }

    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Text(
            "Welcome back",
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold,
            color = Neutral800,
            letterSpacing = (-0.3).sp
        )
        Text(
            "Sign in to continue protecting the Sahyadri",
            fontSize = 14.sp,
            color = Neutral500,
            lineHeight = 20.sp
        )
        Spacer(Modifier.height(4.dp))

        // Email
        AuthInputField(
            value = email,
            onValueChange = { email = it; emailError = null; onClearError() },
            label = "Email",
            placeholder = "you@example.com",
            keyboardType = KeyboardType.Email,
            imeAction = ImeAction.Next,
            onImeAction = { focusManager.moveFocus(FocusDirection.Down) },
            leadingIcon = Icons.Filled.Email,
            error = emailError
        )

        // Password
        AuthInputField(
            value = password,
            onValueChange = { password = it; passwordError = null; onClearError() },
            label = "Password",
            placeholder = "••••••••",
            keyboardType = KeyboardType.Password,
            imeAction = ImeAction.Done,
            onImeAction = { focusManager.clearFocus(); if (validate()) onSubmit(email, password) },
            leadingIcon = Icons.Filled.Lock,
            error = passwordError,
            isPassword = true,
            passwordVisible = passwordVisible,
            onTogglePassword = { passwordVisible = !passwordVisible }
        )

        // Forgot password
        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.CenterEnd) {
            Text(
                "Forgot password?",
                fontSize = 13.sp,
                color = Blue007AFF,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.glassClickable { /* TODO: forgot password flow */ }
            )
        }

        // Global error
        globalError?.let { ErrorBanner(it) }

        Spacer(Modifier.height(4.dp))

        // Submit button
        AuthPrimaryButton(
            text = if (isLoading) "Signing in…" else "Sign In",
            isLoading = isLoading,
            onClick = { if (validate()) onSubmit(email, password) }
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────────
//  Sign Up Form
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun SignUpForm(
    isLoading: Boolean,
    globalError: String?,
    onSubmit: (name: String, email: String, password: String) -> Unit,
    onClearError: () -> Unit
) {
    val focusManager = LocalFocusManager.current
    var name by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var confirmPassword by remember { mutableStateOf("") }
    var nameError by remember { mutableStateOf<String?>(null) }
    var emailError by remember { mutableStateOf<String?>(null) }
    var passwordError by remember { mutableStateOf<String?>(null) }
    var confirmError by remember { mutableStateOf<String?>(null) }
    var passwordVisible by remember { mutableStateOf(false) }
    var confirmVisible by remember { mutableStateOf(false) }

    fun validate(): Boolean {
        nameError = if (name.isBlank()) "Name is required" else null
        emailError = if (!email.isValidEmail()) "Enter a valid email address" else null
        passwordError = when {
            password.length < 8 -> "Minimum 8 characters"
            !password.any { it.isDigit() } -> "Include at least one number"
            else -> null
        }
        confirmError = if (password != confirmPassword) "Passwords do not match" else null
        return listOf(nameError, emailError, passwordError, confirmError).all { it == null }
    }

    // Password strength
    val strength = passwordStrength(password)

    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Text(
            "Create account",
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold,
            color = Neutral800,
            letterSpacing = (-0.3).sp
        )
        Text(
            "Join the community of stream guardians",
            fontSize = 14.sp,
            color = Neutral500,
            lineHeight = 20.sp
        )
        Spacer(Modifier.height(4.dp))

        // Name
        AuthInputField(
            value = name,
            onValueChange = { name = it; nameError = null; onClearError() },
            label = "Full Name",
            placeholder = "Arjun Patil",
            keyboardType = KeyboardType.Text,
            imeAction = ImeAction.Next,
            onImeAction = { focusManager.moveFocus(FocusDirection.Down) },
            leadingIcon = Icons.Filled.Person,
            error = nameError
        )

        // Email
        AuthInputField(
            value = email,
            onValueChange = { email = it; emailError = null; onClearError() },
            label = "Email",
            placeholder = "you@example.com",
            keyboardType = KeyboardType.Email,
            imeAction = ImeAction.Next,
            onImeAction = { focusManager.moveFocus(FocusDirection.Down) },
            leadingIcon = Icons.Filled.Email,
            error = emailError
        )

        // Password
        AuthInputField(
            value = password,
            onValueChange = { password = it; passwordError = null; onClearError() },
            label = "Password",
            placeholder = "Min. 8 characters",
            keyboardType = KeyboardType.Password,
            imeAction = ImeAction.Next,
            onImeAction = { focusManager.moveFocus(FocusDirection.Down) },
            leadingIcon = Icons.Filled.Lock,
            error = passwordError,
            isPassword = true,
            passwordVisible = passwordVisible,
            onTogglePassword = { passwordVisible = !passwordVisible }
        )

        // Password strength bar
        if (password.isNotEmpty()) {
            PasswordStrengthBar(strength)
        }

        // Confirm password
        AuthInputField(
            value = confirmPassword,
            onValueChange = { confirmPassword = it; confirmError = null; onClearError() },
            label = "Confirm Password",
            placeholder = "••••••••",
            keyboardType = KeyboardType.Password,
            imeAction = ImeAction.Done,
            onImeAction = { focusManager.clearFocus(); if (validate()) onSubmit(name, email, password) },
            leadingIcon = Icons.Filled.LockOpen,
            error = confirmError,
            isPassword = true,
            passwordVisible = confirmVisible,
            onTogglePassword = { confirmVisible = !confirmVisible }
        )

        // Global error
        globalError?.let { ErrorBanner(it) }

        Spacer(Modifier.height(4.dp))

        // Submit button
        AuthPrimaryButton(
            text = if (isLoading) "Creating account…" else "Create Account",
            isLoading = isLoading,
            onClick = { if (validate()) onSubmit(name, email, password) }
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────────
//  Sub-components
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun AuthLogoBox() {
    val infiniteTransition = rememberInfiniteTransition(label = "logo_wave")
    val wavePhase by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 2f * Math.PI.toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(3000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "wave_phase"
    )

    Box(
        modifier = Modifier
            .size(88.dp)
            .clip(RoundedCornerShape(28.dp))
            .background(Color.White.copy(0.90f))
            .border(1.dp, Glass.borderLight, RoundedCornerShape(28.dp))
            .drawBehind {
                val w = size.width
                val h = size.height
                val baseY = h * 0.52f
                val amplitude = h * 0.06f
                val path = Path().apply {
                    moveTo(0f, h); lineTo(w, h); lineTo(w, baseY + amplitude * sin(wavePhase + 2f))
                    val steps = 32
                    for (i in steps downTo 0) {
                        val x = w * i / steps
                        val y = baseY + amplitude * sin(wavePhase + x / w * 4f)
                        lineTo(x, y)
                    }
                    close()
                }
                drawPath(path, Blue007AFF.copy(alpha = 0.25f))
            },
        contentAlignment = Alignment.Center
    ) {
        Icon(
            Icons.Filled.WaterDrop,
            contentDescription = "SahyadriSiri logo",
            tint = Blue007AFF,
            modifier = Modifier.size(40.dp)
        )
    }
}

@Composable
private fun AuthModePill(mode: AuthMode, onModeChange: (AuthMode) -> Unit) {
    GlassPanel(
        modifier = Modifier
            .fillMaxWidth()
            .height(48.dp),
        cornerRadius = 16.dp,
        surfaceColor = Color(0xFFF2F2F7),
        showSpecular = false,
        showBorder = false
    ) {
        Row(Modifier.padding(4.dp)) {
            AuthMode.values().forEach { m ->
                val selected = mode == m
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .clip(RoundedCornerShape(12.dp))
                        .background(
                            if (selected)
                                Brush.verticalGradient(listOf(Color.White, Blue007AFF.copy(0.08f)))
                            else
                                Brush.verticalGradient(listOf(Color.Transparent, Color.Transparent))
                        )
                        .then(
                            if (selected)
                                Modifier.border(0.5.dp, Glass.borderLight, RoundedCornerShape(12.dp))
                            else Modifier
                        )
                        .glassClickable { onModeChange(m) },
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        m.label,
                        fontSize = 15.sp,
                        fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                        color = if (selected) Blue007AFF else Neutral400
                    )
                }
            }
        }
    }
}

@Composable
private fun AuthInputField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    placeholder: String,
    keyboardType: KeyboardType,
    imeAction: ImeAction,
    onImeAction: () -> Unit,
    leadingIcon: androidx.compose.ui.graphics.vector.ImageVector,
    error: String? = null,
    isPassword: Boolean = false,
    passwordVisible: Boolean = false,
    onTogglePassword: (() -> Unit)? = null
) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
            label,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            color = Neutral400,
            letterSpacing = 0.5.sp
        )
        Box(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(
                    Brush.verticalGradient(
                        listOf(Color.White.copy(0.80f), Color.White.copy(0.50f))
                    )
                )
                .border(
                    width = if (error != null) 1.dp else 0.5.dp,
                    color = if (error != null) RedFF3B30.copy(0.60f) else Glass.borderLight,
                    shape = RoundedCornerShape(16.dp)
                )
        ) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    leadingIcon,
                    contentDescription = null,
                    tint = if (error != null) RedFF3B30.copy(0.70f) else Neutral400,
                    modifier = Modifier.size(18.dp)
                )
                BasicTextField_Wrapper(
                    value = value,
                    onValueChange = onValueChange,
                    placeholder = placeholder,
                    keyboardType = keyboardType,
                    imeAction = imeAction,
                    onImeAction = onImeAction,
                    isPassword = isPassword && !passwordVisible,
                    modifier = Modifier
                        .weight(1f)
                        .padding(horizontal = 10.dp, vertical = 16.dp)
                )
                if (isPassword && onTogglePassword != null) {
                    Icon(
                        if (passwordVisible) Icons.Filled.VisibilityOff else Icons.Filled.Visibility,
                        contentDescription = if (passwordVisible) "Hide password" else "Show password",
                        tint = Neutral400,
                        modifier = Modifier
                            .size(20.dp)
                            .glassClickable { onTogglePassword() }
                    )
                }
            }
        }
        if (error != null) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Icon(
                    Icons.Filled.Error,
                    contentDescription = null,
                    tint = RedFF3B30,
                    modifier = Modifier.size(12.dp)
                )
                Text(error, fontSize = 12.sp, color = RedFF3B30, fontWeight = FontWeight.Medium)
            }
        }
    }
}

@Composable
private fun BasicTextField_Wrapper(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    keyboardType: KeyboardType,
    imeAction: ImeAction,
    onImeAction: () -> Unit,
    isPassword: Boolean,
    modifier: Modifier
) {
    androidx.compose.foundation.text.BasicTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier,
        singleLine = true,
        textStyle = androidx.compose.ui.text.TextStyle(
            fontSize = 15.sp,
            color = Neutral800,
            fontWeight = FontWeight.Normal
        ),
        keyboardOptions = KeyboardOptions(
            keyboardType = keyboardType,
            imeAction = imeAction
        ),
        keyboardActions = KeyboardActions(
            onNext = { onImeAction() },
            onDone = { onImeAction() }
        ),
        visualTransformation = if (isPassword) PasswordVisualTransformation() else VisualTransformation.None,
        decorationBox = { innerTextField ->
            Box {
                if (value.isEmpty()) {
                    Text(
                        placeholder,
                        fontSize = 15.sp,
                        color = Neutral400.copy(0.70f),
                        fontWeight = FontWeight.Normal
                    )
                }
                innerTextField()
            }
        }
    )
}

@Composable
private fun AuthPrimaryButton(text: String, isLoading: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(54.dp)
            .clip(RoundedCornerShape(18.dp))
            .background(
                Brush.verticalGradient(
                    listOf(Blue007AFF.copy(0.92f), Blue007AFF)
                )
            )
            .then(if (!isLoading) Modifier.glassClickable(onClick) else Modifier),
        contentAlignment = Alignment.Center
    ) {
        if (isLoading) {
            CircularProgressIndicator(
                color = Color.White,
                modifier = Modifier.size(22.dp),
                strokeWidth = 2.5.dp
            )
        } else {
            Text(
                text,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White,
                letterSpacing = (-0.2).sp
            )
        }
    }
}

@Composable
private fun GoogleSignInButton(
    isLoading: Boolean,
    onSuccess: (idToken: String) -> Unit,
    onError: (String) -> Unit
) {
    val context = LocalContext.current

    val googleSignInLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val task = GoogleSignIn.getSignedInAccountFromIntent(result.data)
        try {
            val account = task.getResult(ApiException::class.java)
            val idToken = account.idToken
            if (idToken != null) {
                onSuccess(idToken)
            } else {
                onError("Google sign-in failed: no ID token returned")
            }
        } catch (e: ApiException) {
            onError("Google sign-in failed (code ${e.statusCode})")
        }
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(54.dp)
            .clip(RoundedCornerShape(18.dp))
            .background(
                Brush.verticalGradient(
                    listOf(Color.White.copy(0.92f), Color.White.copy(0.80f))
                )
            )
            .border(0.5.dp, Glass.borderLight, RoundedCornerShape(18.dp))
            .then(if (!isLoading) Modifier.glassClickable {
                val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
                    .requestIdToken("172225820538-flparr97bi6t51lr28soqv5ubb7hvrp3.apps.googleusercontent.com")
                    .requestEmail()
                    .build()
                val client = GoogleSignIn.getClient(context, gso)
                googleSignInLauncher.launch(client.signInIntent)
            } else Modifier),
        contentAlignment = Alignment.Center
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // Google "G" logo rendered as coloured circles — no asset needed
            GoogleGIcon()
            Text(
                "Continue with Google",
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
                color = Neutral800
            )
        }
    }
}

@Composable
private fun GoogleGIcon() {
    // Minimal Google G drawn with compose Canvas — no image asset required
    Canvas(Modifier.size(20.dp)) {
        val cx = size.width / 2f
        val cy = size.height / 2f
        val r = size.width / 2f
        // Outer circle segments (simplified approximation)
        drawArc(
            color = Color(0xFF4285F4),
            startAngle = -30f, sweepAngle = 120f, useCenter = false,
            size = androidx.compose.ui.geometry.Size(r * 2, r * 2),
            topLeft = androidx.compose.ui.geometry.Offset(cx - r, cy - r),
            style = androidx.compose.ui.graphics.drawscope.Stroke(width = 3.5f)
        )
        drawArc(
            color = Color(0xFF34A853),
            startAngle = 90f, sweepAngle = 90f, useCenter = false,
            size = androidx.compose.ui.geometry.Size(r * 2, r * 2),
            topLeft = androidx.compose.ui.geometry.Offset(cx - r, cy - r),
            style = androidx.compose.ui.graphics.drawscope.Stroke(width = 3.5f)
        )
        drawArc(
            color = Color(0xFFFBBC04),
            startAngle = 180f, sweepAngle = 90f, useCenter = false,
            size = androidx.compose.ui.geometry.Size(r * 2, r * 2),
            topLeft = androidx.compose.ui.geometry.Offset(cx - r, cy - r),
            style = androidx.compose.ui.graphics.drawscope.Stroke(width = 3.5f)
        )
        drawArc(
            color = Color(0xFFEA4335),
            startAngle = 270f, sweepAngle = 60f, useCenter = false,
            size = androidx.compose.ui.geometry.Size(r * 2, r * 2),
            topLeft = androidx.compose.ui.geometry.Offset(cx - r, cy - r),
            style = androidx.compose.ui.graphics.drawscope.Stroke(width = 3.5f)
        )
        // White center fill
        drawCircle(Color.White, radius = r * 0.55f, center = androidx.compose.ui.geometry.Offset(cx, cy))
        // Horizontal bar of the "G"
        drawRect(
            color = Color(0xFF4285F4),
            topLeft = androidx.compose.ui.geometry.Offset(cx, cy - 1.5f),
            size = androidx.compose.ui.geometry.Size(r * 0.90f, 3f)
        )
    }
}

@Composable
private fun OrDivider() {
    Row(
        Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Divider(Modifier.weight(1f), color = Neutral200, thickness = 0.5.dp)
        Text("or", fontSize = 13.sp, color = Neutral400, fontWeight = FontWeight.Medium)
        Divider(Modifier.weight(1f), color = Neutral200, thickness = 0.5.dp)
    }
}

@Composable
private fun ErrorBanner(message: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(RedFF3B30.copy(0.08f))
            .border(0.5.dp, RedFF3B30.copy(0.25f), RoundedCornerShape(12.dp))
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Icon(Icons.Filled.Error, null, tint = RedFF3B30, modifier = Modifier.size(16.dp))
        Text(message, fontSize = 13.sp, color = RedFF3B30, fontWeight = FontWeight.Medium, lineHeight = 18.sp)
    }
}

@Composable
private fun PasswordStrengthBar(strength: PasswordStrength) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            repeat(4) { i ->
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(3.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(
                            if (i < strength.segments) strength.color
                            else Neutral200
                        )
                )
            }
        }
        Text(
            strength.label,
            fontSize = 11.sp,
            color = strength.color,
            fontWeight = FontWeight.SemiBold
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────────
//  Helpers / models
// ─────────────────────────────────────────────────────────────────────────────

enum class AuthMode(val label: String) {
    SIGN_IN("Sign In"),
    SIGN_UP("Sign Up")
}

data class PasswordStrength(val segments: Int, val label: String, val color: Color)

fun passwordStrength(password: String): PasswordStrength {
    if (password.length < 4) return PasswordStrength(1, "Weak", RedFF3B30)
    var score = 0
    if (password.length >= 8) score++
    if (password.any { it.isUpperCase() }) score++
    if (password.any { it.isDigit() }) score++
    if (password.any { !it.isLetterOrDigit() }) score++
    return when (score) {
        1 -> PasswordStrength(1, "Weak", RedFF3B30)
        2 -> PasswordStrength(2, "Fair", OrangeFF9500)
        3 -> PasswordStrength(3, "Good", Color(0xFF32ADE6))
        else -> PasswordStrength(4, "Strong", Green34C759)
    }
}

fun String.isValidEmail(): Boolean =
    android.util.Patterns.EMAIL_ADDRESS.matcher(this).matches()
