package com.example.codecompass.api;

import com.example.codecompass.model.TokenRefreshRequest;
import com.example.codecompass.model.TokenRefreshResponse;

import java.io.IOException;
import java.util.concurrent.TimeUnit;

import okhttp3.Authenticator;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.Route;
import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;

/**
 * OkHttp Authenticator that transparently refreshes expired access tokens.
 *
 * Flow:
 *   1. A request returns 401.
 *   2. Authenticator is invoked; it reads the stored refresh token.
 *   3. A synchronous POST to /auth/token/refresh/ is made with a dedicated client
 *      (no Authenticator) to avoid infinite recursion.
 *   4. On success → save new access token, return the original request with the
 *      new Authorization header so OkHttp retries it automatically.
 *   5. On failure → return null.  The 401 is handed back to the original Callback
 *      so existing handle401() logic (clear tokens + redirect to login) still fires.
 *
 * The "X-Auth-Retry" header prevents re-entry: if a retry already happened once
 * for this request we stop trying to avoid an infinite loop.
 */
public class TokenAuthenticator implements Authenticator {

    private static final String RETRY_HEADER = "X-Auth-Retry";

    // Synchronised so concurrent 401s don't race to refresh simultaneously
    private final Object lock = new Object();

    @Override
    public Request authenticate(Route route, Response response) throws IOException {
        // Abort if we already retried once for this request
        if (response.request().header(RETRY_HEADER) != null) return null;

        String refreshToken = TokenManager.getRefreshToken(
                com.example.codecompass.CodeCompassApp.getInstance());
        if (refreshToken == null || refreshToken.isEmpty()) return null;

        synchronized (lock) {
            // Another thread may have already refreshed while we were waiting
            String currentAccess = TokenManager.getAccessToken(
                    com.example.codecompass.CodeCompassApp.getInstance());

            String sentAuth = response.request().header("Authorization");
            String sentToken = (sentAuth != null && sentAuth.startsWith("Bearer "))
                    ? sentAuth.substring(7) : "";

            if (currentAccess != null && !currentAccess.equals(sentToken)) {
                // Already refreshed — just retry with the current access token
                return response.request().newBuilder()
                        .header("Authorization", "Bearer " + currentAccess)
                        .header(RETRY_HEADER, "1")
                        .build();
            }

            // Perform the token refresh synchronously using a clean client
            OkHttpClient refreshClient = new OkHttpClient.Builder()
                    .connectTimeout(30, TimeUnit.SECONDS)
                    .readTimeout(30, TimeUnit.SECONDS)
                    .build();

            Retrofit refreshRetrofit = new Retrofit.Builder()
                    .baseUrl(ApiClient.BASE_URL)
                    .client(refreshClient)
                    .addConverterFactory(GsonConverterFactory.create())
                    .build();

            ApiService authService = refreshRetrofit.create(ApiService.class);

            try {
                retrofit2.Response<TokenRefreshResponse> refreshResp =
                        authService.refreshToken(new TokenRefreshRequest(refreshToken)).execute();

                if (refreshResp.isSuccessful() && refreshResp.body() != null) {
                    String newAccessToken = refreshResp.body().getAccess();
                    String newRefreshToken = refreshResp.body().getRefresh();
                    if (newRefreshToken != null) {
                        TokenManager.saveTokens(
                                com.example.codecompass.CodeCompassApp.getInstance(),
                                newAccessToken, newRefreshToken);
                    } else {
                        TokenManager.saveAccessToken(
                                com.example.codecompass.CodeCompassApp.getInstance(),
                                newAccessToken);
                    }
                    return response.request().newBuilder()
                            .header("Authorization", "Bearer " + newAccessToken)
                            .header(RETRY_HEADER, "1")
                            .build();
                }
                // Refresh token is expired/invalid — let the 401 propagate
                return null;

            } catch (Exception e) {
                return null;
            }
        }
    }
}
