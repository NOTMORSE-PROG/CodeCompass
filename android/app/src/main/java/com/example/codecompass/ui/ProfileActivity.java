package com.example.codecompass.ui;

import android.animation.ObjectAnimator;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.View;
import android.view.animation.DecelerateInterpolator;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.example.codecompass.R;
import com.example.codecompass.api.ApiClient;
import com.example.codecompass.ui.AIChatHubActivity;
import com.example.codecompass.api.TokenManager;
import com.example.codecompass.model.ChangePasswordRequest;
import com.example.codecompass.model.ChangePasswordResponse;
import com.example.codecompass.model.MessageResponse;
import com.example.codecompass.model.GamificationProfile;
import com.example.codecompass.model.Roadmap;
import com.example.codecompass.util.JwtUtils;
import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.android.material.card.MaterialCardView;
import com.google.android.material.progressindicator.CircularProgressIndicator;
import com.google.android.material.progressindicator.LinearProgressIndicator;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.util.Locale;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class ProfileActivity extends AppCompatActivity {

    // ── Views — Identity ──────────────────────────────────────────────────────
    private TextView tvProfileInitial;
    private TextView tvProfileName;
    private TextView tvProfileEmail;
    private TextView tvProfileRole;

    // ── Views — Stats ─────────────────────────────────────────────────────────
    private TextView tvProfileXp;
    private TextView tvProfileStreak;
    private TextView tvProfileBadges;

    // ── Views — Roadmap ───────────────────────────────────────────────────────
    private TextView tvProfileRoadmapTitle;
    private TextView tvProfileRoadmapPct;
    private LinearProgressIndicator progressProfileRoadmap;
    private MaterialCardView cardProfileRoadmap;

    // ── Views — Account ───────────────────────────────────────────────────────
    private LinearLayout rowChangePassword;
    private View dividerChangePassword;
    private LinearLayout rowGoogleConnected;
    private LinearLayout rowConnectGoogle;
    private LinearLayout rowGoogleOnly;
    private LinearLayout btnLogout;
    private LinearLayout rowDeleteAccount;

    // ── Misc ──────────────────────────────────────────────────────────────────
    private View loadingBarProfile;
    private int pendingRequests = 0;
    private boolean isHandling401 = false;
    private int currentRoadmapId = -1;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_profile);

        // Bind views
        tvProfileInitial       = findViewById(R.id.tvProfileInitial);
        tvProfileName          = findViewById(R.id.tvProfileName);
        tvProfileEmail         = findViewById(R.id.tvProfileEmail);
        tvProfileRole          = findViewById(R.id.tvProfileRole);
        tvProfileXp            = findViewById(R.id.tvProfileXp);
        tvProfileStreak        = findViewById(R.id.tvProfileStreak);
        tvProfileBadges        = findViewById(R.id.tvProfileBadges);
        tvProfileRoadmapTitle  = findViewById(R.id.tvProfileRoadmapTitle);
        tvProfileRoadmapPct    = findViewById(R.id.tvProfileRoadmapPct);
        progressProfileRoadmap = findViewById(R.id.progressProfileRoadmap);
        cardProfileRoadmap     = findViewById(R.id.cardProfileRoadmap);
        loadingBarProfile      = findViewById(R.id.loadingBarProfile);
        rowChangePassword      = findViewById(R.id.rowChangePassword);
        dividerChangePassword  = findViewById(R.id.dividerChangePassword);
        rowGoogleConnected     = findViewById(R.id.rowGoogleConnected);
        rowConnectGoogle       = findViewById(R.id.rowConnectGoogle);
        rowGoogleOnly          = findViewById(R.id.rowGoogleOnly);
        btnLogout              = findViewById(R.id.btnLogout);
        rowDeleteAccount       = findViewById(R.id.rowDeleteAccount);

        // Back button
        findViewById(R.id.btnBack).setOnClickListener(v -> finish());

        // Logout
        btnLogout.setOnClickListener(v -> logout());

        // Delete account
        rowDeleteAccount.setOnClickListener(v -> showDeleteAccountDialog());

        // Bottom navigation — Profile is the selected tab
        BottomNavigationView bottomNav = findViewById(R.id.bottomNavProfile);
        bottomNav.setSelectedItemId(R.id.nav_profile);
        bottomNav.setOnItemSelectedListener(item -> {
            int id = item.getItemId();
            if (id == R.id.nav_profile) return true;
            if (id == R.id.nav_home) {
                finish();
                return false;
            }
            if (id == R.id.nav_roadmap) {
                openRoadmap();
                return false;
            }
            if (id == R.id.nav_chat) {
                Intent intent = new Intent(this, AIChatHubActivity.class);
                intent.addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT);
                startActivity(intent);
                overridePendingTransition(R.anim.slide_in_left, R.anim.slide_out_right);
                return false;
            }
            Toast.makeText(this, R.string.coming_soon, Toast.LENGTH_SHORT).show();
            return false;
        });

        // Roadmap card taps open RoadmapActivity
        cardProfileRoadmap.setOnClickListener(v -> openRoadmap());

        populateHeaderFromToken();
        setupAccountSection();
        startLoading();
        loadGamificationProfile();
        loadRoadmap();
    }

    // ── Navigation ────────────────────────────────────────────────────────────

    private void openRoadmap() {
        Intent intent = new Intent(this, RoadmapActivity.class);
        intent.putExtra(RoadmapActivity.EXTRA_ROADMAP_ID, currentRoadmapId);
        startActivity(intent);
        overridePendingTransition(R.anim.slide_in_left, R.anim.slide_out_right);
    }

    // ── Smooth tab-switch exit ────────────────────────────────────────────────

    @Override
    public void finish() {
        super.finish();
        overridePendingTransition(R.anim.slide_in_left, R.anim.slide_out_right);
    }

    // ── Token-based header ────────────────────────────────────────────────────

    private void populateHeaderFromToken() {
        String token = TokenManager.getAccessToken(this);

        String fullName = JwtUtils.getFullName(token);
        if (fullName != null && !fullName.trim().isEmpty()) {
            tvProfileName.setText(fullName);
            tvProfileInitial.setText(fullName.substring(0, 1).toUpperCase());
        }

        String email = JwtUtils.getEmail(token);
        if (email != null && !email.isEmpty()) {
            tvProfileEmail.setText(email);
        }

        tvProfileRole.setText(formatRole(JwtUtils.getRole(token)));
    }

    private String formatRole(String role) {
        if (role == null || role.isEmpty()) return getString(R.string.profile_role_student);
        switch (role.toLowerCase()) {
            case "mentor":           return getString(R.string.profile_role_mentor);
            case "admin":            return getString(R.string.profile_role_admin);
            case "incoming_student": return "Incoming Student";
            case "undergraduate":    return "Undergraduate Student";
            default:                 return getString(R.string.profile_role_student);
        }
    }

    // ── Account section ───────────────────────────────────────────────────────

    private void setupAccountSection() {
        boolean hasPwd    = TokenManager.isHasPassword(this);
        boolean googleConn = TokenManager.isGoogleConnected(this);

        rowChangePassword.setVisibility(hasPwd ? View.VISIBLE : View.GONE);
        dividerChangePassword.setVisibility(hasPwd ? View.VISIBLE : View.GONE);
        rowGoogleConnected.setVisibility(googleConn ? View.VISIBLE : View.GONE);
        rowConnectGoogle.setVisibility((hasPwd && !googleConn) ? View.VISIBLE : View.GONE);
        rowGoogleOnly.setVisibility(!hasPwd ? View.VISIBLE : View.GONE);

        rowChangePassword.setOnClickListener(v -> showChangePasswordDialog());
        rowConnectGoogle.setOnClickListener(v ->
                Toast.makeText(this, R.string.coming_soon, Toast.LENGTH_SHORT).show());
    }

    private void showChangePasswordDialog() {
        View dialogView = getLayoutInflater().inflate(R.layout.dialog_change_password, null);

        // Step 1 views
        LinearLayout stepSendOtp      = dialogView.findViewById(R.id.stepSendOtp);
        com.google.android.material.button.MaterialButton btnSendOtp =
                dialogView.findViewById(R.id.btnSendOtp);
        android.widget.ProgressBar progressSendOtp =
                dialogView.findViewById(R.id.progressSendOtp);

        // Step 2 views
        LinearLayout stepVerifyOtp    = dialogView.findViewById(R.id.stepVerifyOtp);
        TextInputEditText etOtp       = dialogView.findViewById(R.id.etOtp);
        TextInputEditText etNew       = dialogView.findViewById(R.id.etNewPassword);
        TextInputEditText etConfirm   = dialogView.findViewById(R.id.etConfirmPassword);
        TextInputLayout   tilOtp      = dialogView.findViewById(R.id.tilOtp);
        TextInputLayout   tilNew      = dialogView.findViewById(R.id.tilNewPassword);
        TextInputLayout   tilConfirm  = dialogView.findViewById(R.id.tilConfirmPassword);
        TextView tvResendCode         = dialogView.findViewById(R.id.tvResendCode);

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle(R.string.profile_pwd_dialog_title)
                .setView(dialogView)
                .setNegativeButton(android.R.string.cancel, null)
                .setPositiveButton(R.string.profile_pwd_update, null)
                .create();

        dialog.show();

        // Hide Update button initially (only visible in Step 2)
        Button btnUpdate = dialog.getButton(AlertDialog.BUTTON_POSITIVE);
        btnUpdate.setVisibility(View.GONE);

        // ── Step 1: Send OTP ─────────────────────────────────────────────
        btnSendOtp.setOnClickListener(v -> {
            btnSendOtp.setEnabled(false);
            btnSendOtp.setText(R.string.profile_pwd_sending);
            progressSendOtp.setVisibility(View.VISIBLE);

            ApiClient.getService()
                    .sendChangePasswordOtp(TokenManager.getBearerToken(this))
                    .enqueue(new Callback<MessageResponse>() {
                        @Override
                        public void onResponse(Call<MessageResponse> call,
                                               Response<MessageResponse> response) {
                            progressSendOtp.setVisibility(View.GONE);
                            if (response.isSuccessful()) {
                                Toast.makeText(ProfileActivity.this,
                                        R.string.profile_pwd_otp_sent, Toast.LENGTH_SHORT).show();
                                // Flip to Step 2
                                stepSendOtp.setVisibility(View.GONE);
                                stepVerifyOtp.setVisibility(View.VISIBLE);
                                btnUpdate.setVisibility(View.VISIBLE);
                            } else if (response.code() == 401) {
                                dialog.dismiss();
                                handle401();
                            } else {
                                btnSendOtp.setEnabled(true);
                                btnSendOtp.setText(R.string.profile_pwd_send_code);
                                Toast.makeText(ProfileActivity.this,
                                        R.string.error_network, Toast.LENGTH_SHORT).show();
                            }
                        }

                        @Override
                        public void onFailure(Call<MessageResponse> call, Throwable t) {
                            progressSendOtp.setVisibility(View.GONE);
                            btnSendOtp.setEnabled(true);
                            btnSendOtp.setText(R.string.profile_pwd_send_code);
                            Toast.makeText(ProfileActivity.this,
                                    R.string.error_network, Toast.LENGTH_SHORT).show();
                        }
                    });
        });

        // ── Resend code link ─────────────────────────────────────────────
        tvResendCode.setOnClickListener(v -> {
            tvResendCode.setEnabled(false);
            ApiClient.getService()
                    .sendChangePasswordOtp(TokenManager.getBearerToken(this))
                    .enqueue(new Callback<MessageResponse>() {
                        @Override
                        public void onResponse(Call<MessageResponse> call,
                                               Response<MessageResponse> response) {
                            tvResendCode.setEnabled(true);
                            if (response.isSuccessful()) {
                                Toast.makeText(ProfileActivity.this,
                                        R.string.profile_pwd_otp_sent, Toast.LENGTH_SHORT).show();
                            }
                        }

                        @Override
                        public void onFailure(Call<MessageResponse> call, Throwable t) {
                            tvResendCode.setEnabled(true);
                        }
                    });
        });

        // ── Step 2: Verify OTP + Change Password ─────────────────────────
        btnUpdate.setOnClickListener(v -> {
            String otp     = text(etOtp);
            String newPwd  = text(etNew);
            String confirm = text(etConfirm);

            tilOtp.setError(null);
            tilNew.setError(null);
            tilConfirm.setError(null);

            if (otp.length() < 6) {
                tilOtp.setError(getString(R.string.error_field_required)); return;
            }
            if (newPwd.length() < 8) {
                tilNew.setError(getString(R.string.error_password_short)); return;
            }
            if (!newPwd.equals(confirm)) {
                tilConfirm.setError(getString(R.string.profile_pwd_mismatch)); return;
            }

            btnUpdate.setEnabled(false);
            ChangePasswordRequest req = new ChangePasswordRequest(
                    otp, newPwd, confirm, TokenManager.getRefreshToken(this));

            ApiClient.getService()
                    .changePassword(TokenManager.getBearerToken(this), req)
                    .enqueue(new Callback<ChangePasswordResponse>() {
                        @Override
                        public void onResponse(Call<ChangePasswordResponse> call,
                                               Response<ChangePasswordResponse> response) {
                            if (response.isSuccessful() && response.body() != null) {
                                ChangePasswordResponse body = response.body();
                                // Save fresh tokens so the session stays valid
                                if (body.getAccess() != null && body.getRefresh() != null) {
                                    TokenManager.saveTokens(ProfileActivity.this,
                                            body.getAccess(), body.getRefresh());
                                }
                                dialog.dismiss();
                                Toast.makeText(ProfileActivity.this,
                                        R.string.profile_pwd_success, Toast.LENGTH_SHORT).show();
                            } else if (response.code() == 400) {
                                btnUpdate.setEnabled(true);
                                // Try to show OTP-specific error
                                try {
                                    String raw = response.errorBody().string();
                                    org.json.JSONObject json = new org.json.JSONObject(raw);
                                    if (json.has("otp")) {
                                        tilOtp.setError(json.getJSONArray("otp").getString(0));
                                    } else if (json.has("new_password")) {
                                        tilNew.setError(json.getJSONArray("new_password").getString(0));
                                    } else if (json.has("detail")) {
                                        tilOtp.setError(json.getString("detail"));
                                    } else {
                                        tilOtp.setError(getString(R.string.profile_pwd_otp_invalid));
                                    }
                                } catch (Exception e) {
                                    tilOtp.setError(getString(R.string.profile_pwd_otp_invalid));
                                }
                            } else if (response.code() == 401) {
                                dialog.dismiss();
                                handle401();
                            } else {
                                btnUpdate.setEnabled(true);
                                tilOtp.setError(getString(R.string.error_network));
                            }
                        }

                        @Override
                        public void onFailure(Call<ChangePasswordResponse> call, Throwable t) {
                            btnUpdate.setEnabled(true);
                            tilOtp.setError(getString(R.string.error_network));
                        }
                    });
        });
    }

    private String text(TextInputEditText et) {
        return et.getText() != null ? et.getText().toString().trim() : "";
    }

    private int dpToPx(float dp) {
        return Math.round(dp * getResources().getDisplayMetrics().density);
    }

    // ── Loading helpers ───────────────────────────────────────────────────────

    private void startLoading() {
        pendingRequests = 2;
        loadingBarProfile.setVisibility(View.VISIBLE);
    }

    private void onRequestDone() {
        pendingRequests = Math.max(0, pendingRequests - 1);
        if (pendingRequests == 0) {
            loadingBarProfile.setVisibility(View.GONE);
        }
    }

    // ── Gamification ──────────────────────────────────────────────────────────

    private void loadGamificationProfile() {
        ApiClient.getService()
                .getGamificationProfile(TokenManager.getBearerToken(this))
                .enqueue(new Callback<GamificationProfile>() {
                    @Override
                    public void onResponse(Call<GamificationProfile> call,
                                           Response<GamificationProfile> response) {
                        if (response.isSuccessful() && response.body() != null) {
                            GamificationProfile profile = response.body();
                            tvProfileXp.setText(String.format(Locale.getDefault(), "%,d", profile.getXpTotal()));
                            tvProfileStreak.setText(String.valueOf(profile.getStreakCount()));
                            tvProfileBadges.setText(String.valueOf(profile.getBadgesEarnedCount()));
                        } else if (response.code() == 401) {
                            handle401();
                            return;
                        }
                        onRequestDone();
                    }

                    @Override
                    public void onFailure(Call<GamificationProfile> call, Throwable t) {
                        onRequestDone();
                    }
                });
    }

    // ── Roadmap ───────────────────────────────────────────────────────────────

    private void loadRoadmap() {
        ApiClient.getService()
                .getRoadmaps(TokenManager.getBearerToken(this))
                .enqueue(new Callback<JsonElement>() {
                    @Override
                    public void onResponse(Call<JsonElement> call, Response<JsonElement> response) {
                        if (!response.isSuccessful() || response.body() == null) {
                            onRequestDone();
                            return;
                        }
                        int roadmapId = extractFirstRoadmapId(response.body());
                        if (roadmapId != -1) {
                            currentRoadmapId = roadmapId;
                            fetchRoadmapDetail(TokenManager.getBearerToken(ProfileActivity.this), roadmapId);
                        } else {
                            onRequestDone();
                        }
                    }

                    @Override
                    public void onFailure(Call<JsonElement> call, Throwable t) {
                        onRequestDone();
                    }
                });
    }

    private int extractFirstRoadmapId(JsonElement body) {
        try {
            JsonArray arr;
            if (body.isJsonArray()) {
                arr = body.getAsJsonArray();
            } else {
                JsonObject obj = body.getAsJsonObject();
                if (!obj.has("results")) return -1;
                arr = obj.getAsJsonArray("results");
            }
            if (arr.size() == 0) return -1;
            return arr.get(0).getAsJsonObject().get("id").getAsInt();
        } catch (Exception e) {
            return -1;
        }
    }

    private void fetchRoadmapDetail(String bearer, int roadmapId) {
        ApiClient.getService()
                .getRoadmap(bearer, roadmapId)
                .enqueue(new Callback<Roadmap>() {
                    @Override
                    public void onResponse(Call<Roadmap> call, Response<Roadmap> response) {
                        if (response.isSuccessful() && response.body() != null) {
                            populateRoadmapCard(response.body());
                        } else if (response.code() == 401) {
                            handle401();
                            return;
                        }
                        onRequestDone();
                    }

                    @Override
                    public void onFailure(Call<Roadmap> call, Throwable t) {
                        onRequestDone();
                    }
                });
    }

    private void populateRoadmapCard(Roadmap roadmap) {
        String title = roadmap.getTitle();
        if (title != null && !title.isEmpty()) {
            tvProfileRoadmapTitle.setText(title);
        }
        int pct = roadmap.getCompletionPercentage();
        tvProfileRoadmapPct.setText(String.format(Locale.getDefault(), "%d%%", pct));

        ObjectAnimator anim = ObjectAnimator.ofInt(progressProfileRoadmap, "progress", 0, pct);
        anim.setDuration(900);
        anim.setInterpolator(new DecelerateInterpolator());
        anim.start();

        cardProfileRoadmap.setVisibility(View.VISIBLE);
    }

    // ── Auth helpers ──────────────────────────────────────────────────────────

    private void showDeleteAccountDialog() {
        int pad = dpToPx(20);

        // ── Warning message (red box) ─────────────────────────────────────────
        TextView tvMessage = new TextView(this);
        tvMessage.setText(R.string.profile_delete_confirm_body);
        tvMessage.setTextSize(13f);
        tvMessage.setTextColor(0xFFB91C1C);
        tvMessage.setBackgroundColor(0x1FEF4444);
        tvMessage.setPadding(dpToPx(12), dpToPx(10), dpToPx(12), dpToPx(10));
        LinearLayout.LayoutParams msgParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        msgParams.setMargins(pad, dpToPx(8), pad, dpToPx(16));
        tvMessage.setLayoutParams(msgParams);

        // ── "Type DELETE to confirm:" label ───────────────────────────────────
        TextView tvHint = new TextView(this);
        tvHint.setText(R.string.profile_delete_type_hint);
        tvHint.setTextSize(13f);
        tvHint.setPadding(pad, 0, pad, dpToPx(4));

        // ── Text input ────────────────────────────────────────────────────────
        TextInputEditText etConfirm = new TextInputEditText(this);
        etConfirm.setHint("DELETE");
        etConfirm.setTextColor(Color.BLACK);
        etConfirm.setHintTextColor(0xFF9E9E9E);  // grey placeholder

        TextInputLayout til = new TextInputLayout(this);
        ColorStateList black = ColorStateList.valueOf(Color.BLACK);
        til.setDefaultHintTextColor(black);
        til.setHintTextColor(black);
        til.setBoxStrokeColorStateList(black);
        til.setBoxStrokeColor(Color.BLACK);

        LinearLayout.LayoutParams tilParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        tilParams.setMarginStart(pad);
        tilParams.setMarginEnd(pad);
        til.setLayoutParams(tilParams);
        til.addView(etConfirm);

        LinearLayout layoutInput = new LinearLayout(this);
        layoutInput.setOrientation(LinearLayout.VERTICAL);
        layoutInput.addView(tvMessage);
        layoutInput.addView(tvHint);
        layoutInput.addView(til);

        // ── Loading section ───────────────────────────────────────────────────
        CircularProgressIndicator spinner = new CircularProgressIndicator(this);
        spinner.setIndeterminate(true);
        spinner.setIndicatorColor(getColor(R.color.colorDanger));
        LinearLayout.LayoutParams spinnerParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        spinnerParams.gravity = Gravity.CENTER_HORIZONTAL;
        spinner.setLayoutParams(spinnerParams);

        TextView tvDeleting = new TextView(this);
        tvDeleting.setText(R.string.profile_delete_deleting);
        tvDeleting.setGravity(Gravity.CENTER);
        tvDeleting.setTextSize(14f);
        tvDeleting.setTextColor(getColor(R.color.colorTextSecondary));
        LinearLayout.LayoutParams tvDeletingParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        tvDeletingParams.topMargin = dpToPx(12);
        tvDeleting.setLayoutParams(tvDeletingParams);

        LinearLayout layoutLoading = new LinearLayout(this);
        layoutLoading.setOrientation(LinearLayout.VERTICAL);
        layoutLoading.setGravity(Gravity.CENTER);
        layoutLoading.setPadding(pad, dpToPx(28), pad, dpToPx(28));
        layoutLoading.setVisibility(View.GONE);
        layoutLoading.addView(spinner);
        layoutLoading.addView(tvDeleting);

        // ── Root container ────────────────────────────────────────────────────
        LinearLayout container = new LinearLayout(this);
        container.setOrientation(LinearLayout.VERTICAL);
        container.addView(layoutInput);
        container.addView(layoutLoading);

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle(R.string.profile_delete_confirm_title)
                .setView(container)
                .setNegativeButton(android.R.string.cancel, null)
                .setPositiveButton(R.string.profile_delete_confirm_btn, null)
                .create();

        dialog.show();

        // White background with rounded corners
        if (dialog.getWindow() != null) {
            GradientDrawable bg = new GradientDrawable();
            bg.setColor(Color.WHITE);
            bg.setCornerRadius(dpToPx(16));
            dialog.getWindow().setBackgroundDrawable(bg);
        }

        Button btnDelete = dialog.getButton(AlertDialog.BUTTON_POSITIVE);
        Button btnCancel = dialog.getButton(AlertDialog.BUTTON_NEGATIVE);
        btnDelete.setTextColor(Color.BLACK);
        btnCancel.setTextColor(Color.BLACK);
        btnDelete.setEnabled(false);

        etConfirm.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int st, int c, int a) {}
            @Override public void afterTextChanged(Editable s) {}
            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                btnDelete.setEnabled("DELETE".equals(s.toString()));
            }
        });

        btnDelete.setOnClickListener(v -> {
            // Switch to loading view
            layoutInput.setVisibility(View.GONE);
            layoutLoading.setVisibility(View.VISIBLE);
            btnDelete.setVisibility(View.GONE);
            btnCancel.setEnabled(false);
            dialog.setCancelable(false);
            rowDeleteAccount.setEnabled(false);

            com.google.gson.JsonObject body = new com.google.gson.JsonObject();
            String refresh = TokenManager.getRefreshToken(ProfileActivity.this);
            if (refresh != null) body.addProperty("refresh", refresh);

            ApiClient.getService()
                    .deleteAccount(TokenManager.getBearerToken(ProfileActivity.this), body)
                    .enqueue(new Callback<Void>() {
                        @Override
                        public void onResponse(Call<Void> call, Response<Void> response) {
                            if (response.isSuccessful()) {
                                dialog.dismiss();
                                Toast.makeText(ProfileActivity.this,
                                        R.string.profile_delete_success, Toast.LENGTH_SHORT).show();
                                TokenManager.clear(ProfileActivity.this);
                                Intent intent = new Intent(ProfileActivity.this, LoginActivity.class);
                                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                                startActivity(intent);
                            } else if (response.code() == 401) {
                                dialog.dismiss();
                                handle401();
                            } else {
                                restoreForm();
                            }
                        }

                        @Override
                        public void onFailure(Call<Void> call, Throwable t) {
                            restoreForm();
                        }

                        private void restoreForm() {
                            layoutLoading.setVisibility(View.GONE);
                            layoutInput.setVisibility(View.VISIBLE);
                            btnDelete.setVisibility(View.VISIBLE);
                            btnCancel.setEnabled(true);
                            dialog.setCancelable(true);
                            rowDeleteAccount.setEnabled(true);
                            Toast.makeText(ProfileActivity.this,
                                    R.string.profile_delete_error, Toast.LENGTH_SHORT).show();
                        }
                    });
        });
    }

    private void logout() {
        TokenManager.clear(this);
        Intent intent = new Intent(this, LoginActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        finish();
    }

    private void handle401() {
        if (isHandling401) return;
        isHandling401 = true;
        TokenManager.clear(this);
        Intent intent = new Intent(this, LoginActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        finish();
    }
}
