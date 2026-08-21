package com.example.codecompass.api;

import com.example.codecompass.BuildConfig;

import java.util.concurrent.TimeUnit;

import okhttp3.OkHttpClient;
import okhttp3.logging.HttpLoggingInterceptor;
import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;

public class ApiClient {

    public static final String BASE_URL = BuildConfig.BASE_URL;
    public static final String WS_BASE_URL = BuildConfig.WS_BASE_URL;

    private static Retrofit retrofit;
    private static OkHttpClient okHttpClient;
    private static ApiService apiService;

    public static OkHttpClient getOkHttpClient() {
        if (okHttpClient == null) {
            HttpLoggingInterceptor logging = new HttpLoggingInterceptor();
            logging.setLevel(BuildConfig.DEBUG
                    ? HttpLoggingInterceptor.Level.BODY
                    : HttpLoggingInterceptor.Level.NONE);

            // Render free tier cold-starts take up to 60 s — timeouts must be generous.
            okHttpClient = new OkHttpClient.Builder()
                    .connectTimeout(60, TimeUnit.SECONDS)
                    .readTimeout(60, TimeUnit.SECONDS)
                    .writeTimeout(60, TimeUnit.SECONDS)
                    .pingInterval(25, TimeUnit.SECONDS) // keeps WebSocket alive
                    .addInterceptor(logging)
                    .authenticator(new TokenAuthenticator())
                    .build();
        }
        return okHttpClient;
    }

    public static Retrofit getInstance() {
        if (retrofit == null) {
            retrofit = new Retrofit.Builder()
                    .baseUrl(BASE_URL)
                    .client(getOkHttpClient())
                    .addConverterFactory(GsonConverterFactory.create())
                    .build();
        }
        return retrofit;
    }

    public static ApiService getService() {
        if (apiService == null) {
            apiService = getInstance().create(ApiService.class);
        }
        return apiService;
    }
}
