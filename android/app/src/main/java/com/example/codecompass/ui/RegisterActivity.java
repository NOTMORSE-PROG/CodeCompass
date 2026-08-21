package com.example.codecompass.ui;

import android.content.Intent;
import android.os.Bundle;
import android.util.Patterns;
import android.view.View;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.credentials.CredentialManager;
import androidx.credentials.CredentialManagerCallback;
import androidx.credentials.GetCredentialRequest;
import androidx.credentials.GetCredentialResponse;
import androidx.credentials.exceptions.GetCredentialException;

import com.example.codecompass.BuildConfig;
import com.example.codecompass.R;
import com.example.codecompass.api.ApiClient;
import com.example.codecompass.api.TokenManager;
import com.example.codecompass.util.JwtUtils;
import com.example.codecompass.model.GoogleAuthRequest;
import com.example.codecompass.model.GoogleAuthResponse;
import com.example.codecompass.model.RegisterRequest;
import com.example.codecompass.model.RegisterResponse;
import com.google.android.libraries.identity.googleid.GetGoogleIdOption;
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.snackbar.Snackbar;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;

import org.json.JSONObject;

import java.util.concurrent.Executors;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class RegisterActivity extends AppCompatActivity {

    private TextInputLayout tilFirstName, tilLastName, tilEmail, tilPassword, tilConfirmPassword;
    private TextInputEditText etFirstName, etLastName, etEmail, etPassword, etConfirmPassword;
    private MaterialButton btnRegister, btnGoogleSignUp;
    private ProgressBar progressRegister;
    private TextView tvLoginLink;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_register);

        ViewCompat.setOnApplyWindowInsetsListener(getWindow().getDecorView(), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        tilFirstName = findViewById(R.id.tilFirstName);
        tilLastName = findViewById(R.id.tilLastName);
        tilEmail = findViewById(R.id.tilEmail);
        tilPassword = findViewById(R.id.tilPassword);
        tilConfirmPassword = findViewById(R.id.tilConfirmPassword);
        etFirstName = findViewById(R.id.etFirstName);
        etLastName = findViewById(R.id.etLastName);
        etEmail = findViewById(R.id.etEmail);
        etPassword = findViewById(R.id.etPassword);
        etConfirmPassword = findViewById(R.id.etConfirmPassword);
        btnRegister = findViewById(R.id.btnRegister);
        btnGoogleSignUp = findViewById(R.id.btnGoogleSignUp);
        progressRegister = findViewById(R.id.progressRegister);
        tvLoginLink = findViewById(R.id.tvLoginLink);

        btnRegister.setOnClickListener(v -> attemptRegister());
        if (BuildConfig.GOOGLE_WEB_CLIENT_ID.isEmpty()) {
            btnGoogleSignUp.setVisibility(View.GONE);
        } else {
            btnGoogleSignUp.setOnClickListener(v -> launchGoogleSignUp());
        }
        tvLoginLink.setOnClickListener(v -> finish());
    }

    // ── Email / Password Register ─────────────────────────────────────────────

    private void attemptRegister() {
        String firstName = text(etFirstName);
        String lastName = text(etLastName);
        String email = text(etEmail);
        String password = text(etPassword);
        String confirmPassword = text(etConfirmPassword);

        tilFirstName.setError(null);
        tilLastName.setError(null);
        tilEmail.setError(null);
        tilPassword.setError(null);
        tilConfirmPassword.setError(null);

        boolean valid = true;
        if (firstName.length() < 2) {
            tilFirstName.setError(firstName.isEmpty()
                    ? getString(R.string.error_field_required)
                    : getString(R.string.error_name_short));
            valid = false;
        }
        if (lastName.length() < 2) {
            tilLastName.setError(lastName.isEmpty()
                    ? getString(R.string.error_field_required)
                    : getString(R.string.error_name_short));
            valid = false;
        }
        if (email.isEmpty()) {
            tilEmail.setError(getString(R.string.error_field_required)); valid = false;
        } else if (!Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            tilEmail.setError(getString(R.string.error_invalid_email)); valid = false;
        }
        if (password.isEmpty()) {
            tilPassword.setError(getString(R.string.error_field_required)); valid = false;
        } else if (password.length() < 8) {
            tilPassword.setError(getString(R.string.error_password_short)); valid = false;
        }
        if (confirmPassword.isEmpty()) {
            tilConfirmPassword.setError(getString(R.string.error_field_required)); valid = false;
        } else if (!password.equals(confirmPassword)) {
            tilConfirmPassword.setError(getString(R.string.error_passwords_no_match)); valid = false;
        }
        if (!valid) return;

        // Username derived from email prefix — sanitized to alphanumeric + underscore only
        String username = email.contains("@") ? email.split("@")[0] : email;
        username = username.replaceAll("[^a-zA-Z0-9_]", "").toLowerCase();
        if (username.isEmpty()) username = "user" + System.currentTimeMillis();

        setLoading(true);
        ApiClient.getService().register(
                new RegisterRequest(email, username, firstName, lastName, password, confirmPassword)
        ).enqueue(new Callback<RegisterResponse>() {
            @Override
            public void onResponse(Call<RegisterResponse> call, Response<RegisterResponse> response) {
                setLoading(false);
                if (response.isSuccessful() && response.body() != null) {
                    RegisterResponse body = response.body();
                    TokenManager.saveTokens(RegisterActivity.this,
                            body.getAccess(), body.getRefresh());
                    if (body.getUser() != null) {
                        TokenManager.saveUserFlags(RegisterActivity.this,
                                body.getUser().isHasPassword(),
                                body.getUser().isGoogleConnected());
                    }
                    goToVerifyEmail(text(etEmail));
                } else {
                    showApiError(response);
                }
            }

            @Override
            public void onFailure(Call<RegisterResponse> call, Throwable t) {
                setLoading(false);
                Snackbar.make(btnRegister, R.string.error_network, Snackbar.LENGTH_LONG).show();
            }
        });
    }

    // ── Google Sign-Up ────────────────────────────────────────────────────────

    private void launchGoogleSignUp() {
        String webClientId = BuildConfig.GOOGLE_WEB_CLIENT_ID;

        GetGoogleIdOption googleIdOption = new GetGoogleIdOption.Builder()
                .setFilterByAuthorizedAccounts(false)
                .setServerClientId(webClientId)
                .setAutoSelectEnabled(false)
                .build();

        GetCredentialRequest request = new GetCredentialRequest.Builder()
                .addCredentialOption(googleIdOption)
                .build();

        setLoading(true);
        CredentialManager credentialManager = CredentialManager.create(this);
        credentialManager.getCredentialAsync(
                this,
                request,
                null,
                Executors.newSingleThreadExecutor(),
                new CredentialManagerCallback<GetCredentialResponse, GetCredentialException>() {
                    @Override
                    public void onResult(GetCredentialResponse result) {
                        runOnUiThread(() -> handleGoogleCredential(result));
                    }

                    @Override
                    public void onError(GetCredentialException e) {
                        runOnUiThread(() -> {
                            setLoading(false);
                            Snackbar.make(btnRegister,
                                    R.string.error_google_sign_in, Snackbar.LENGTH_LONG).show();
                        });
                    }
                }
        );
    }

    private void handleGoogleCredential(GetCredentialResponse result) {
        try {
            GoogleIdTokenCredential googleIdToken =
                    GoogleIdTokenCredential.createFrom(result.getCredential().getData());
            String idToken = googleIdToken.getIdToken();

            ApiClient.getService().googleAuth(new GoogleAuthRequest(idToken))
                    .enqueue(new Callback<GoogleAuthResponse>() {
                        @Override
                        public void onResponse(Call<GoogleAuthResponse> call,
                                               Response<GoogleAuthResponse> response) {
                            setLoading(false);
                            if (response.isSuccessful() && response.body() != null) {
                                GoogleAuthResponse body = response.body();
                                TokenManager.saveTokens(RegisterActivity.this,
                                        body.getAccess(), body.getRefresh());
                                if (body.getUser() != null) {
                                    TokenManager.saveUserFlags(RegisterActivity.this,
                                            body.getUser().isHasPassword(),
                                            body.getUser().isGoogleConnected());
                                }
                                if (!JwtUtils.isEmailVerified(body.getAccess())) {
                                    goToVerifyEmail(JwtUtils.getEmail(body.getAccess()));
                                } else if (JwtUtils.isOnboarded(body.getAccess())) {
                                    Intent intent = new Intent(RegisterActivity.this,
                                            DashboardActivity.class);
                                    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK
                                            | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                                    startActivity(intent);
                                } else {
                                    goToOnboarding();
                                }
                                finish();
                            } else {
                                showApiError(response);
                            }
                        }

                        @Override
                        public void onFailure(Call<GoogleAuthResponse> call, Throwable t) {
                            setLoading(false);
                            Snackbar.make(btnRegister,
                                    R.string.error_network, Snackbar.LENGTH_LONG).show();
                        }
                    });
        } catch (Exception e) {
            setLoading(false);
            Snackbar.make(btnRegister, R.string.error_google_sign_in, Snackbar.LENGTH_LONG).show();
        }
    }

    // ── Navigation ────────────────────────────────────────────────────────────

    private void goToVerifyEmail(String email) {
        Intent intent = new Intent(this, VerifyEmailPendingActivity.class);
        intent.putExtra(VerifyEmailPendingActivity.EXTRA_EMAIL, email);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        finish();
    }

    private void goToOnboarding() {
        Intent intent = new Intent(this, OnboardingActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        finish();
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private void setLoading(boolean loading) {
        btnRegister.setEnabled(!loading);
        btnGoogleSignUp.setEnabled(!loading);
        progressRegister.setVisibility(loading ? View.VISIBLE : View.GONE);
    }

    private void showApiError(Response<?> response) {
        String message = getString(R.string.error_network);
        if (response.errorBody() != null) {
            try {
                String raw = response.errorBody().string();
                JSONObject json = new JSONObject(raw);
                if (json.has("detail")) {
                    message = json.getString("detail");
                } else if (json.has("email") && json.getJSONArray("email").length() > 0) {
                    message = json.getJSONArray("email").getString(0);
                } else if (json.has("password") && json.getJSONArray("password").length() > 0) {
                    message = json.getJSONArray("password").getString(0);
                } else if (json.has("non_field_errors") && json.getJSONArray("non_field_errors").length() > 0) {
                    message = json.getJSONArray("non_field_errors").getString(0);
                }
            } catch (Exception ignored) { }
        }
        Snackbar.make(btnRegister, message, Snackbar.LENGTH_LONG).show();
    }

    private String text(TextInputEditText et) {
        return et.getText() != null ? et.getText().toString().trim() : "";
    }
}
