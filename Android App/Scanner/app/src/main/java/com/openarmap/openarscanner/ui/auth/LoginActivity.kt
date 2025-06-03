package com.openarmap.openarscanner.ui.auth

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import com.openarmap.openarscanner.R
import com.openarmap.openarscanner.databinding.ActivityLoginBinding
import com.openarmap.openarscanner.ui.ARScanActivity
import com.openarmap.openarscanner.ui.auth.AuthViewModel
import com.openarmap.openarscanner.ui.auth.AuthViewModelFactory
import com.openarmap.openarscanner.ui.auth.AuthState
import com.openarmap.openarscanner.data.repository.FirebaseAuthRepository
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInClient
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.ApiException
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar

class LoginActivity : AppCompatActivity() {
    private lateinit var binding: ActivityLoginBinding
    private lateinit var viewModel: AuthViewModel
    private lateinit var googleSignInClient: GoogleSignInClient

    private val googleSignInLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val task = GoogleSignIn.getSignedInAccountFromIntent(result.data)
        try {
            val account = task.getResult(ApiException::class.java)
            account.idToken?.let { token ->
                viewModel.signInWithGoogle(token)
            }
        } catch (e: ApiException) {
            showError("Google sign in failed: ${e.message}")
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityLoginBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupGoogleSignIn()
        setupViewModel()
        setupUI()
        observeAuthState()
    }

    private fun setupGoogleSignIn() {
        val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestIdToken(getString(R.string.default_web_client_id))
            .requestEmail()
            .build()

        googleSignInClient = GoogleSignIn.getClient(this, gso)
    }

    private fun setupViewModel() {
        val repository = FirebaseAuthRepository()
        viewModel = ViewModelProvider(this, AuthViewModelFactory(repository))[AuthViewModel::class.java]
    }

    private fun setupUI() {
        binding.loginButton.setOnClickListener {
            val email = binding.emailEditText.text.toString()
            val password = binding.passwordEditText.text.toString()
            if (validateInput(email, password)) {
                viewModel.signInWithEmail(email, password)
            }
        }

        binding.registerButton.setOnClickListener {
            showRegisterDialog()
        }

        binding.forgotPasswordText.setOnClickListener {
            showForgotPasswordDialog()
        }

        binding.googleSignInButton.setOnClickListener {
            val signInIntent = googleSignInClient.signInIntent
            googleSignInLauncher.launch(signInIntent)
        }

        binding.githubSignInButton.setOnClickListener {
            // TODO: Implement GitHub sign-in
            showError("GitHub sign-in not implemented yet")
        }
    }

    private fun observeAuthState() {
        lifecycleScope.launch {
            viewModel.authState.collect { state ->
                when (state) {
                    is AuthState.Loading -> showLoading(true)
                    is AuthState.Authenticated -> {
                        showLoading(false)
                        startActivity(Intent(this@LoginActivity, ARScanActivity::class.java))
                        finish()
                    }
                    is AuthState.Error -> {
                        showLoading(false)
                        showError(state.message)
                    }
                    is AuthState.PasswordResetSent -> {
                        showLoading(false)
                        showSuccess("Password reset email sent")
                    }
                    else -> showLoading(false)
                }
            }
        }
    }

    private fun validateInput(email: String, password: String): Boolean {
        var isValid = true

        if (email.isEmpty()) {
            binding.emailLayout.error = "Email is required"
            isValid = false
        } else {
            binding.emailLayout.error = null
        }

        if (password.isEmpty()) {
            binding.passwordLayout.error = "Password is required"
            isValid = false
        } else {
            binding.passwordLayout.error = null
        }

        return isValid
    }

    private fun showRegisterDialog() {
        val dialogView = layoutInflater.inflate(R.layout.dialog_register, null)
        val emailInput = dialogView.findViewById<com.google.android.material.textfield.TextInputEditText>(R.id.emailInput)
        val passwordInput = dialogView.findViewById<com.google.android.material.textfield.TextInputEditText>(R.id.passwordInput)
        val nameInput = dialogView.findViewById<com.google.android.material.textfield.TextInputEditText>(R.id.nameInput)

        MaterialAlertDialogBuilder(this)
            .setTitle("Create Account")
            .setView(dialogView)
            .setPositiveButton("Register") { _, _ ->
                val email = emailInput.text.toString()
                val password = passwordInput.text.toString()
                val name = nameInput.text.toString()
                if (validateRegistrationInput(email, password, name)) {
                    viewModel.signUpWithEmail(email, password, name)
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun showForgotPasswordDialog() {
        val dialogView = layoutInflater.inflate(R.layout.dialog_forgot_password, null)
        val emailInput = dialogView.findViewById<com.google.android.material.textfield.TextInputEditText>(R.id.emailInput)

        MaterialAlertDialogBuilder(this)
            .setTitle("Reset Password")
            .setView(dialogView)
            .setPositiveButton("Send") { _, _ ->
                val email = emailInput.text.toString()
                if (email.isNotEmpty()) {
                    viewModel.resetPassword(email)
                } else {
                    showError("Please enter your email")
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun validateRegistrationInput(email: String, password: String, name: String): Boolean {
        if (email.isEmpty() || password.isEmpty() || name.isEmpty()) {
            showError("All fields are required")
            return false
        }
        return true
    }

    private fun showLoading(show: Boolean) {
        binding.progressBar.visibility = if (show) View.VISIBLE else View.GONE
        binding.loginButton.isEnabled = !show
        binding.registerButton.isEnabled = !show
        binding.googleSignInButton.isEnabled = !show
        binding.githubSignInButton.isEnabled = !show
    }

    private fun showError(message: String) {
        Snackbar.make(binding.root, message, Snackbar.LENGTH_LONG).show()
    }

    private fun showSuccess(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }
} 