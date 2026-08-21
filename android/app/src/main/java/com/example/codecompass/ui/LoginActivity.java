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
import com.example.codecompass.MainActivity;
import com.example.codecompass.R;
import com.example.codecompass.api.ApiClient;
import com.example.codecompass.api.TokenManager;
import com.example.codecompass.util.JwtUtils;
import com.example.codecompass.model.GoogleAuthRequest;
import com.example.codecompass.model.GoogleAuthResponse;
import com.example.codecompass.model.LoginRequest;
import com.example.codecompass.model.LoginResponse;
import com.google.android.libraries.identity.googleid.GetGoogleIdOption;
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.snackbar.Snackbar;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;

import org.json.JSONObject;

import java.net.SocketTimeoutException;
import java.util.concurrent.Executors;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class LoginActivity extends AppCompatActivity {

    private TextInputLayout tilEmail, tilPassword;
    private TextInputEditText etEmail, etPassword;
    private MaterialButton btnLogin, btnGoogleSignIn;
    private ProgressBar progressLogin;
    private TextView tvRegisterLink;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_login);

        ViewCompat.setOnApplyWindowInsetsListener(getWindow().getDecorView(), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        tilEmail = findViewById(R.id.tilEmail);
        tilPassword = findViewById(R.id.tilPassword);
        etEmail = findViewById(R.id.etEmail);
        etPassword = findViewById(R.id.etPassword);
        btnLogin = findViewById(R.id.btnLogin);
        btnGoogleSignIn = findViewById(R.id.btnGoogleSignIn);
        progressLogin = findViewById(R.id.progressLogin);
        tvRegisterLink = findViewById(R.id.tvRegisterLink);

        btnLogin.setOnClickListener(v -> attemptLogin());
        if (BuildConfig.GOOGLE_WEB_CLIENT_ID.isEmpty()) {
            btnGoogleSignIn.setVisibility(View.GONE);
        } else {
            btnGoogleSignIn.setOnClickListener(v -> launchGoogleSignIn());
        }
        tvRegisterLink.setOnClickListener(v ->
                startActivity(new Intent(this, RegisterActivity.class)));
    }

    // ── Email / Password Login ────────────────────────────────────────────────

    private void attemptLogin() {
        String email = text(etEmail);
        String password = text(etPassword);

        tilEmail.setError(null);
        tilPassword.setError(null);

        boolean valid = true;
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
        if (!valid) return;

        setLoading(true);
        ApiClient.getService().login(new LoginRequest(email, password))
                .enqueue(new Callback<LoginResponse>() {
                    @Override
                    public void onResponse(Call<LoginResponse> call, Response<LoginResponse> response) {
                        setLoading(false);
                        if (response.isSuccessful() && response.body() != null) {
                            LoginResponse body = response.body();
                            TokenManager.saveTokens(LoginActivity.this,
                                    body.getAccess(), body.getRefresh());
                            if (body.getUser() != null) {
                                TokenManager.saveUserFlags(LoginActivity.this,
                                        body.getUser().isHasPassword(),
                                        body.getUser().isGoogleConnected());
                            }
                            navigateAfterAuth(body.getAccess());
                        } else {
                            showApiError(response);
                        }
                    }

                    @Override
                    public void onFailure(Call<LoginResponse> call, Throwable t) {
                        setLoading(false);
                        int msg = t instanceof SocketTimeoutException
                                ? R.string.error_server_starting : R.string.error_network;
                        Snackbar.make(btnLogin, msg, Snackbar.LENGTH_LONG).show();
                    }
                });
    }

    // ── Google Sign-In ────────────────────────────────────────────────────────

    private void launchGoogleSignIn() {
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
                            Snackbar.make(btnLogin,
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
                                TokenManager.saveTokens(LoginActivity.this,
                                        body.getAccess(), body.getRefresh());
                                if (body.getUser() != null) {
                                    TokenManager.saveUserFlags(LoginActivity.this,
                                            body.getUser().isHasPassword(),
                                            body.getUser().isGoogleConnected());
                                }
                                navigateAfterAuth(body.getAccess());
                            } else {
                                showApiError(response);
                            }
                        }

                        @Override
                        public void onFailure(Call<GoogleAuthResponse> call, Throwable t) {
                            setLoading(false);
                            int msg = t instanceof SocketTimeoutException
                                    ? R.string.error_server_starting : R.string.error_network;
                            Snackbar.make(btnLogin, msg, Snackbar.LENGTH_LONG).show();
                        }
                    });
        } catch (Exception e) {
            setLoading(false);
            Snackbar.make(btnLogin, R.string.error_google_sign_in, Snackbar.LENGTH_LONG).show();
        }
    }

    // ── Navigation (mirrors web getPostLoginRoute) ────────────────────────────

    private void navigateAfterAuth(String accessToken) {
        Intent intent;
        if (!JwtUtils.isEmailVerified(accessToken)) {
            intent = new Intent(this, VerifyEmailPendingActivity.class);
            intent.putExtra(VerifyEmailPendingActivity.EXTRA_EMAIL,
                    JwtUtils.getEmail(accessToken));
        } else if (JwtUtils.isOnboarded(accessToken)) {
            intent = new Intent(this, DashboardActivity.class);
        } else {
            intent = new Intent(this, OnboardingActivity.class);
        }
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        finish();
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private void setLoading(boolean loading) {
        btnLogin.setEnabled(!loading);
        btnGoogleSignIn.setEnabled(!loading);
        progressLogin.setVisibility(loading ? View.VISIBLE : View.GONE);
    }

    private void showApiError(Response<?> response) {
        String message = getString(R.string.error_network);
        if (response.errorBody() != null) {
            try {
                String raw = response.errorBody().string();
                JSONObject json = new JSONObject(raw);
                if (json.has("detail")) {
                    message = json.getString("detail");
                } else if (json.has("non_field_errors") && json.getJSONArray("non_field_errors").length() > 0) {
                    message = json.getJSONArray("non_field_errors").getString(0);
                }
            } catch (Exception ignored) { }
        }
        Snackbar.make(btnLogin, message, Snackbar.LENGTH_LONG).show();
    }

    private String text(TextInputEditText et) {
        return et.getText() != null ? et.getText().toString().trim() : "";
    }
}
