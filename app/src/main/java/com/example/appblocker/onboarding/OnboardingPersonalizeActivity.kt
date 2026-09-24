package com.example.appblocker.onboarding

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.Toast
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.example.appblocker.MainActivity
import com.example.appblocker.R
import com.example.appblocker.ui.auth.AuthUiState
import com.example.appblocker.ui.auth.AuthViewModel
import com.google.android.material.textfield.TextInputEditText
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

@AndroidEntryPoint
class OnboardingPersonalizeActivity : AppCompatActivity() {
    private var isUpgradeFlow = false
    private val authViewModel: AuthViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_onboarding_personalize)
        
        isUpgradeFlow = intent.getBooleanExtra("IS_UPGRADE_FLOW", false)
        
        if(isUpgradeFlow) {
            findViewById<Button>(R.id.btnContinueAsGuest).visibility = View.GONE
            supportActionBar?.setDisplayHomeAsUpEnabled(true)
            supportActionBar?.title = "Create Permanent Account"
        }
        
        findViewById<Button>(R.id.btnCreateAccount).setOnClickListener { signUpWithEmail() }
        findViewById<Button>(R.id.btnContinueWithGoogle).setOnClickListener { signInWithGoogle() }
        findViewById<Button>(R.id.btnContinueAsGuest).setOnClickListener { continueAsGuest() }

        observeUiState()
    }
    
    private fun observeUiState() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                authViewModel.uiState.collectLatest { state ->
                    when (state) {
                        is AuthUiState.Success -> onAuthSuccess()
                        is AuthUiState.Error -> {
                            Toast.makeText(this@OnboardingPersonalizeActivity, state.message, Toast.LENGTH_SHORT).show()
                        }
                        else -> {}
                    }
                }
            }
        }
    }

    private fun onAuthSuccess() {
        if(isUpgradeFlow) {
            setResult(RESULT_OK)
            finish()
        } else {
            val intent = Intent(this, MainActivity::class.java)
            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            startActivity(intent)
            finish()
        }
    }
    
    private fun signUpWithEmail() { 
        val email = findViewById<TextInputEditText>(R.id.etEmail)?.text?.toString() ?: ""
        val password = findViewById<TextInputEditText>(R.id.etPassword)?.text?.toString() ?: ""
        val name = findViewById<TextInputEditText>(R.id.etFullName)?.text?.toString() ?: ""
        
        if (email.isNotEmpty() && password.isNotEmpty()) {
            if (isUpgradeFlow) {
                authViewModel.linkWithEmail(email, password, name)
            } else {
                authViewModel.signUpWithEmail(email, password, name)
            }
        } else {
            Toast.makeText(this, "Please fill all fields", Toast.LENGTH_SHORT).show()
        }
    }
    
    private fun signInWithGoogle() { 
        if (isUpgradeFlow) {
            authViewModel.linkWithGoogle(this)
        } else {
            authViewModel.signInWithGoogle(this) 
        }
    }
    
    private fun continueAsGuest() { 
        authViewModel.signInAnonymously()
    }
    
    override fun onBackPressed() {
        if(isUpgradeFlow) setResult(RESULT_CANCELED)
        super.onBackPressed()
    }
}
