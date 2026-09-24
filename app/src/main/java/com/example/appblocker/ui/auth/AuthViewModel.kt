package com.example.appblocker.ui.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.appblocker.data.auth.AuthRepository
import com.example.appblocker.data.auth.AuthUser
import com.google.firebase.auth.FirebaseAuthUserCollisionException
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

import android.content.Context
import android.util.Log
import androidx.credentials.ClearCredentialStateRequest
import androidx.credentials.CredentialManager
import androidx.credentials.GetCredentialRequest
import androidx.credentials.GetCredentialResponse
import androidx.credentials.exceptions.GetCredentialException
import androidx.credentials.exceptions.GetCredentialCancellationException
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import java.security.MessageDigest
import java.util.UUID

private const val TAG = "AuthViewModel"

@HiltViewModel
class AuthViewModel @Inject constructor(
    private val authRepository: AuthRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow<AuthUiState>(AuthUiState.Idle)
    val uiState: StateFlow<AuthUiState> = _uiState.asStateFlow()

    val currentUser = authRepository.currentUser

    fun signInWithGoogle(context: Context) {
        viewModelScope.launch {
            _uiState.value = AuthUiState.Loading
            try {
                val credentialManager = CredentialManager.create(context)
                val rawNonce = UUID.randomUUID().toString()
                val bytes = rawNonce.toByteArray()
                val md = MessageDigest.getInstance("SHA-256")
                val digest = md.digest(bytes)
                val hashedNonce = digest.fold("") { str, it -> str + "%02x".format(it) }

                val googleIdOption = GetGoogleIdOption.Builder()
                    .setFilterByAuthorizedAccounts(false)
                    // TODO: Replace with your actual Web Client ID from Firebase Console -> Authentication -> Google
                    .setServerClientId("627379786348-hlts7879a8sstmgtqpivm63i5hbk1liv.apps.googleusercontent.com")
                    .setNonce(hashedNonce)
                    .build()

                val request = GetCredentialRequest.Builder()
                    .addCredentialOption(googleIdOption)
                    .build()

                val result = credentialManager.getCredential(context, request)
                handleGoogleSignIn(result)
            } catch (e: GetCredentialCancellationException) {
                Log.i(TAG, "Google Sign-In cancelled by user")
                _uiState.value = AuthUiState.Idle
            } catch (e: GetCredentialException) {
                Log.e(TAG, "Credential Manager error: ${e.type} ${e.message}", e)
                _uiState.value = AuthUiState.Error(e.message ?: "Google Sign-In failed")
            } catch (e: Exception) {
                Log.e(TAG, "Unexpected error during Google Sign-In", e)
                _uiState.value = AuthUiState.Error(e.message ?: "Google Sign-In failed")
            }
        }
    }

    private suspend fun handleGoogleSignIn(result: GetCredentialResponse) {
        Log.d(TAG, "handleGoogleSignIn: Received credential")
        val credential = result.credential
        if (credential is GoogleIdTokenCredential) {
            authRepository.signInWithGoogle(credential.idToken)
                .onSuccess { 
                    Log.d(TAG, "Google Sign-In success: ${it.id}")
                    _uiState.value = AuthUiState.Success(it) 
                }
                .onFailure { 
                    Log.e(TAG, "Google Sign-In failure: ${it.message}")
                    _uiState.value = AuthUiState.Error(it.message ?: "Firebase Auth failed") 
                }
        } else {
            Log.e(TAG, "Google Sign-In error: Unexpected credential type")
            _uiState.value = AuthUiState.Error("Unexpected credential type")
        }
    }

    fun signInAnonymously() {
        Log.d(TAG, "signInAnonymously called")
        viewModelScope.launch {
            _uiState.value = AuthUiState.Loading
            authRepository.signInAnonymously()
                .onSuccess { 
                    Log.d(TAG, "signInAnonymously success: ${it.id}")
                    _uiState.value = AuthUiState.Success(it) 
                }
                .onFailure { 
                    Log.e(TAG, "signInAnonymously failure: ${it.message}")
                    _uiState.value = AuthUiState.Error(it.message ?: "Unknown error") 
                }
        }
    }

    fun signOut() {
        viewModelScope.launch {
            authRepository.signOut()
            _uiState.value = AuthUiState.Idle
        }
    }

    fun signInWithEmail(email: String, password: String) {
        Log.d(TAG, "signInWithEmail called for: $email")
        viewModelScope.launch {
            _uiState.value = AuthUiState.Loading
            authRepository.signInWithEmail(email, password)
                .onSuccess { 
                    Log.d(TAG, "signInWithEmail success: ${it.id}")
                    _uiState.value = AuthUiState.Success(it) 
                }
                .onFailure { 
                    Log.e(TAG, "signInWithEmail failure: ${it.message}")
                    _uiState.value = AuthUiState.Error(it.message ?: "Sign in failed") 
                }
        }
    }

    fun signUpWithEmail(email: String, password: String, fullName: String? = null) {
        Log.d(TAG, "signUpWithEmail called for: $email")
        viewModelScope.launch {
            _uiState.value = AuthUiState.Loading
            authRepository.signUpWithEmail(email, password, fullName)
                .onSuccess { 
                    Log.d(TAG, "signUpWithEmail success: ${it.id}")
                    _uiState.value = AuthUiState.Success(it) 
                }
                .onFailure { 
                    Log.e(TAG, "signUpWithEmail failure: ${it.message}")
                    _uiState.value = AuthUiState.Error(it.message ?: "Sign up failed") 
                }
        }
    }

    fun linkWithEmail(email: String, password: String, fullName: String? = null) {
        viewModelScope.launch {
            _uiState.value = AuthUiState.Loading
            authRepository.linkWithEmail(email, password, fullName)
                .onSuccess { _uiState.value = AuthUiState.Success(it) }
                .onFailure {
                    val message = if (it is FirebaseAuthUserCollisionException) {
                        "Account already exists. Logged in and linked your progress."
                    } else {
                        it.message ?: "Email link failed"
                    }
                    _uiState.value = AuthUiState.Error(message)
                }
        }
    }

    fun linkWithGoogle(context: Context, fullName: String? = null) {
        viewModelScope.launch {
            _uiState.value = AuthUiState.Loading
            try {
                val credentialManager = CredentialManager.create(context)
                val rawNonce = UUID.randomUUID().toString()
                val bytes = rawNonce.toByteArray()
                val md = MessageDigest.getInstance("SHA-256")
                val digest = md.digest(bytes)
                val hashedNonce = digest.fold("") { str, it -> str + "%02x".format(it) }

                val googleIdOption = GetGoogleIdOption.Builder()
                    .setFilterByAuthorizedAccounts(false)
                    .setServerClientId("627379786348-hlts7879a8sstmgtqpivm63i5hbk1liv.apps.googleusercontent.com")
                    .setNonce(hashedNonce)
                    .build()

                val request = GetCredentialRequest.Builder()
                    .addCredentialOption(googleIdOption)
                    .build()

                val result = credentialManager.getCredential(context, request)
                handleGoogleLink(result, fullName)
            } catch (e: GetCredentialCancellationException) {
                Log.i(TAG, "Google linking cancelled by user")
                _uiState.value = AuthUiState.Idle
            } catch (e: GetCredentialException) {
                Log.e(TAG, "Credential Manager error during linking: ${e.type} ${e.message}", e)
                _uiState.value = AuthUiState.Error(e.message ?: "Google link failed")
            } catch (e: Exception) {
                Log.e(TAG, "Unexpected error during Google link", e)
                _uiState.value = AuthUiState.Error(e.message ?: "Google link failed")
            }
        }
    }

    private fun handleGoogleLink(response: GetCredentialResponse, fullName: String?) {
        val credential = response.credential
        if (credential is GoogleIdTokenCredential) {
            viewModelScope.launch {
                authRepository.linkWithGoogle(credential.idToken, fullName)
                    .onSuccess { _uiState.value = AuthUiState.Success(it) }
                    .onFailure { _uiState.value = AuthUiState.Error(it.message ?: "Google link failed") }
            }
        } else {
            _uiState.value = AuthUiState.Error("Unexpected credential type")
        }
    }
}

sealed class AuthUiState {
    object Idle : AuthUiState()
    object Loading : AuthUiState()
    data class Success(val user: AuthUser) : AuthUiState()
    data class Error(val message: String) : AuthUiState()
}
