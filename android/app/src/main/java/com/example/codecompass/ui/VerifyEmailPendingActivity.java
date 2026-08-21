package com.example.codecompass.ui;

import android.content.Intent;
import android.os.Bundle;
import android.os.CountDownTimer;
import android.view.View;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.example.codecompass.R;
import com.example.codecompass.api.ApiClient;
import com.example.codecompass.api.TokenManager;
import com.example.codecompass.model.ResendVerificationRequest;
import com.example.codecompass.model.ResendVerificationResponse;
import com.example.codecompass.model.TokenRefreshRequest;
import com.example.codecompass.model.TokenRefreshResponse;
import com.example.codecompass.util.JwtUtils;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.snackbar.Snackbar;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class VerifyEmailPendingActivity extends AppCompatActivity {

    public static final String EXTRA_EMAIL = "extra_email";

    private TextView tvEmail;
    private MaterialButton btnVerified, btnResend;
    private ProgressBar progressVerify;
    private TextView tvBackToLogin;

    private String email;
    private CountDownTimer resendCooldown;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_verify_email_pending);

        ViewCompat.setOnApplyWindowInsetsListener(getWindow().getDecorView(), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        tvEmail        = findViewById(R.id.tvEmail);
        btnVerified    = findViewById(R.id.btnVerified);
        btnResend      = findViewById(R.id.btnResend);
        progressVerify = findViewById(R.id.progressVerify);
        tvBackToLogin  = findViewById(R.id.tvBackToLogin);

        // Get email from intent extra, or decode from stored token
        email = getIntent().getStringExtra(EXTRA_EMAIL);
        if (email == null || email.isEmpty()) {
            String token = TokenManager.getAccessToken(this);
            email = JwtUtils.getEmail(token);
        }
        tvEmail.setText(email);

        btnVerified.setOnClickListener(v -> checkVerificationStatus());
        btnResend.setOnClickListener(v -> resendVerificationEmail());
        tvBackToLogin.setOnClickListener(v -> backToLogin());
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (resendCooldown != null) resendCooldown.cancel();
    }

    // ── Check verification by refreshing token ───────────────────────────────

    private void checkVerificationStatus() {
        String refreshToken = TokenManager.getRefreshToken(this);
        if (refreshToken == null || refreshToken.isEmpty()) {
            backToLogin();
            return;
        }

        setLoading(true);
        ApiClient.getService().refreshToken(new TokenRefreshRequest(refreshToken))
                .enqueue(new Callback<TokenRefreshResponse>() {
                    @Override
                    public void onResponse(Call<TokenRefreshResponse> call,
                                           Response<TokenRefreshResponse> response) {
                        setLoading(false);
                        if (response.isSuccessful() && response.body() != null) {
                            TokenRefreshResponse body = response.body();
                            String newAccess = body.getAccess();

                            // Save the new tokens (refresh is rotated)
                            if (body.getRefresh() != null) {
                                TokenManager.saveTokens(VerifyEmailPendingActivity.this,
                                        newAccess, body.getRefresh());
                            } else {
                                TokenManager.saveAccessToken(VerifyEmailPendingActivity.this,
                                        newAccess);
                            }

                            if (JwtUtils.isEmailVerified(newAccess)) {
                                Snackbar.make(btnVerified, R.string.verify_email_success,
                                        Snackbar.LENGTH_SHORT).show();
                                navigateAfterVerification(newAccess);
                            } else {
                                Snackbar.make(btnVerified, R.string.verify_email_not_yet,
                                        Snackbar.LENGTH_LONG).show();
                            }
                        } else {
                            // Refresh failed — token expired, force re-login
                            backToLogin();
                        }
                    }

                    @Override
                    public void onFailure(Call<TokenRefreshResponse> call, Throwable t) {
                        setLoading(false);
                        Snackbar.make(btnVerified, R.string.error_network,
                                Snackbar.LENGTH_LONG).show();
                    }
                });
    }

    // ── Resend verification email ────────────────────────────────────────────

    private void resendVerificationEmail() {
        if (email == null || email.isEmpty()) return;

        btnResend.setEnabled(false);
        ApiClient.getService().resendVerification(new ResendVerificationRequest(email))
                .enqueue(new Callback<ResendVerificationResponse>() {
                    @Override
                    public void onResponse(Call<ResendVerificationResponse> call,
                                           Response<ResendVerificationResponse> response) {
                        Snackbar.make(btnResend, R.string.verify_email_resent,
                                Snackbar.LENGTH_LONG).show();
                        startResendCooldown();
                    }

                    @Override
                    public void onFailure(Call<ResendVerificationResponse> call, Throwable t) {
                        btnResend.setEnabled(true);
                        Snackbar.make(btnResend, R.string.error_network,
                                Snackbar.LENGTH_LONG).show();
                    }
                });
    }

    private void startResendCooldown() {
        resendCooldown = new CountDownTimer(60000, 1000) {
            @Override
            public void onTick(long millisUntilFinished) {
                int seconds = (int) (millisUntilFinished / 1000);
                btnResend.setText(getString(R.string.verify_email_resend_wait, seconds));
                btnResend.setEnabled(false);
            }

            @Override
            public void onFinish() {
                btnResend.setText(R.string.btn_resend_verification);
                btnResend.setEnabled(true);
            }
        }.start();
    }

    // ── Navigation ───────────────────────────────────────────────────────────

    private void navigateAfterVerification(String accessToken) {
        Intent intent;
        if (JwtUtils.isOnboarded(accessToken)) {
            intent = new Intent(this, DashboardActivity.class);
        } else {
            intent = new Intent(this, OnboardingActivity.class);
        }
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        finish();
    }

    private void backToLogin() {
        TokenManager.clear(this);
        Intent intent = new Intent(this, LoginActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        finish();
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    private void setLoading(boolean loading) {
        btnVerified.setEnabled(!loading);
        btnResend.setEnabled(!loading);
        progressVerify.setVisibility(loading ? View.VISIBLE : View.GONE);
    }
}
