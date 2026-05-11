package com.sahyadrisiri.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sahyadrisiri.data.api.SupabaseClient
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.providers.Google
import io.github.jan.supabase.auth.providers.builtin.Email
import io.github.jan.supabase.auth.providers.builtin.IDToken
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

// ─────────────────────────────────────────────────────────────────────────────
//  Auth state
// ─────────────────────────────────────────────────────────────────────────────

sealed class AuthState {
    object Idle : AuthState()
    object Loading : AuthState()
    data class Success(val userId: String, val userEmail: String?) : AuthState()
    data class Error(val message: String) : AuthState()
}

// ─────────────────────────────────────────────────────────────────────────────
//  ViewModel — powered by Supabase Auth
// ─────────────────────────────────────────────────────────────────────────────

class AuthViewModel : ViewModel() {

    private val _authState = MutableStateFlow<AuthState>(AuthState.Idle)
    val authState: StateFlow<AuthState> = _authState.asStateFlow()

    private val auth get() = SupabaseClient.client.auth

    init {
        // Observe the session status flow. This automatically handles the asynchronous loading
        // of the session from local storage when the app first starts.
        viewModelScope.launch {
            auth.sessionStatus.collect { status ->
                when (status) {
                    is io.github.jan.supabase.auth.status.SessionStatus.Authenticated -> {
                        _authState.value = AuthState.Success(
                            userId = status.session.user?.id ?: "",
                            userEmail = status.session.user?.email
                        )
                    }
                    is io.github.jan.supabase.auth.status.SessionStatus.NotAuthenticated -> {
                        _authState.value = AuthState.Idle
                    }
                    io.github.jan.supabase.auth.status.SessionStatus.Initializing -> {
                        _authState.value = AuthState.Loading
                    }
                    else -> {
                        // RefreshFailure or any other state — keep current state
                    }
                }
            }
        }
    }

    // ── Email / password sign in ──────────────────────────────────────────

    fun signIn(email: String, password: String) {
        viewModelScope.launch {
            _authState.value = AuthState.Loading
            try {
                auth.signInWith(Email) {
                    this.email = email
                    this.password = password
                }
                val user = auth.currentUserOrNull()
                _authState.value = AuthState.Success(
                    userId = user?.id ?: "",
                    userEmail = user?.email
                )
            } catch (e: Exception) {
                _authState.value = AuthState.Error(
                    e.message?.takeIf { it.isNotBlank() }
                        ?: "Sign in failed. Please check your credentials."
                )
            }
        }
    }

    // ── Email / password sign up ──────────────────────────────────────────

    fun signUp(name: String, email: String, password: String) {
        viewModelScope.launch {
            _authState.value = AuthState.Loading
            try {
                auth.signUpWith(Email) {
                    this.email = email
                    this.password = password
                    this.data = kotlinx.serialization.json.buildJsonObject {
                        put("full_name", kotlinx.serialization.json.JsonPrimitive(name))
                    }
                }
                val user = auth.currentUserOrNull()
                _authState.value = AuthState.Success(
                    userId = user?.id ?: "",
                    userEmail = user?.email
                )
            } catch (e: Exception) {
                _authState.value = AuthState.Error(
                    e.message?.takeIf { it.isNotBlank() }
                        ?: "Sign up failed. Please try again."
                )
            }
        }
    }

    // ── Google OAuth (ID Token from Google Sign-In SDK) ───────────────────

    fun signInWithGoogle(idToken: String) {
        viewModelScope.launch {
            _authState.value = AuthState.Loading
            try {
                auth.signInWith(IDToken) {
                    this.provider = Google
                    this.idToken = idToken
                }
                val user = auth.currentUserOrNull()
                _authState.value = AuthState.Success(
                    userId = user?.id ?: "",
                    userEmail = user?.email
                )
            } catch (e: Exception) {
                _authState.value = AuthState.Error(
                    e.message?.takeIf { it.isNotBlank() }
                        ?: "Google sign-in failed."
                )
            }
        }
    }

    // ── Sign out ──────────────────────────────────────────────────────────

    fun signOut() {
        viewModelScope.launch {
            try {
                auth.signOut()
            } catch (_: Exception) { }
            _authState.value = AuthState.Idle
        }
    }

    // ── Reset state ───────────────────────────────────────────────────────

    fun resetState() {
        _authState.value = AuthState.Idle
    }
}
