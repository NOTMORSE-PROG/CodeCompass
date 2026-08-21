package com.example.codecompass;

import android.content.Intent;
import android.os.Bundle;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.splashscreen.SplashScreen;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.example.codecompass.api.TokenManager;
import com.example.codecompass.ui.DashboardActivity;
import com.example.codecompass.ui.LoginActivity;
import com.example.codecompass.ui.OnboardingActivity;
import com.example.codecompass.ui.VerifyEmailPendingActivity;
import com.example.codecompass.util.JwtUtils;

/**
 * Entry point. Decides where to route the user:
 *  - No token / expired token → LoginActivity
 *  - Token + onboarded        → DashboardActivity
 *  - Token + !onboarded       → OnboardingActivity
 *
 * Mirrors the ProtectedRoute / OnboardingGuard logic from the web frontend.
 */
public class MainActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        SplashScreen.installSplashScreen(this);
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_main);

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        route();
    }

    private void route() {
        String token = TokenManager.getAccessToken(this);
        if (!JwtUtils.isValid(token)) {
            // null, empty, or expired token — clear stale data and force re-login
            TokenManager.clear(this);
            goTo(LoginActivity.class, true);
        } else if (!JwtUtils.isEmailVerified(token)) {
            goTo(VerifyEmailPendingActivity.class, true);
        } else if (!JwtUtils.isOnboarded(token)) {
            goTo(OnboardingActivity.class, true);
        } else {
            goTo(DashboardActivity.class, true);
        }
    }

    private void goTo(Class<?> target, boolean clearStack) {
        Intent intent = new Intent(this, target);
        if (clearStack) {
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        }
        startActivity(intent);
        finish();
    }

    /** Called by other Activities after successful auth to re-trigger routing. */
    public static Intent createRoutingIntent(android.content.Context context) {
        Intent intent = new Intent(context, MainActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        return intent;
    }
}
