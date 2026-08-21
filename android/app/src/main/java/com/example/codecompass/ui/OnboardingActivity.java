package com.example.codecompass.ui;

import android.content.Intent;
import androidx.appcompat.view.ContextThemeWrapper;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.TypedValue;
import android.view.View;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.activity.EdgeToEdge;
import androidx.activity.OnBackPressedCallback;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.codecompass.R;
import com.example.codecompass.api.ApiClient;
import com.example.codecompass.api.TokenManager;
import com.example.codecompass.model.ChatMessage;
import com.example.codecompass.model.CompleteOnboardingRequest;
import com.example.codecompass.model.CompleteOnboardingResponse;
import com.example.codecompass.model.CreateSessionRequest;
import com.example.codecompass.model.CreateSessionResponse;
import com.example.codecompass.model.Roadmap;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.chip.Chip;
import com.google.android.material.snackbar.Snackbar;
import com.google.android.material.textfield.TextInputEditText;

import org.json.JSONArray;
import org.json.JSONObject;

import java.net.SocketTimeoutException;
import java.util.Arrays;
import java.util.List;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.WebSocket;
import okhttp3.WebSocketListener;
import okio.ByteString;
import retrofit2.Call;
import retrofit2.Callback;

public class OnboardingActivity extends AppCompatActivity {

    // ── READY_SIGNALS — exact copy from web OnboardingPage.jsx ───────────────
    private static final List<String> READY_SIGNALS = Arrays.asList(
            // Tagalog / Filipino
            "ready na akong gumawa",
            "ready na ako gumawa",
            "gumawa ng roadmap para sa iyo",
            "ready na tayong gumawa",
            "ready na akong i-build",
            "sapat na ang impormasyon",
            "malinaw na ang iyong profile",
            // English
            "i'm ready to build your roadmap",
            "ready to build your roadmap",
            "build your roadmap now",
            "have a good picture now",
            "have everything i need",
            "enough information"
    );
    private static final List<String> NEGATIONS = Arrays.asList(
            "hindi", "not", "haven't", "wala", "di pa", "hindi pa", "wala pa"
    );

    // ── Views ─────────────────────────────────────────────────────────────────
    private LinearLayout layoutChat, layoutCompleting;
    private RecyclerView rvChat;
    private TextInputEditText etMessage;
    private MaterialButton btnSend, btnBuildRoadmap;
    private HorizontalScrollView chipScrollView;
    private LinearLayout chipContainer;

    // Completing screen views
    private ProgressBar stepChatSpinner, stepAnalyzeSpinner, stepBuildSpinner;
    private TextView stepChatIcon, stepAnalyzeIcon, stepBuildIcon;

    // ── State ─────────────────────────────────────────────────────────────────
    private ChatAdapter chatAdapter;
    private WebSocket webSocket;
    private String sessionId;
    private boolean isCompleting = false;
    private final StringBuilder streamingBuffer = new StringBuilder();
    private boolean isStreaming = false;
    private int streamingMsgIndex = -1;

    private int sessionRetryCount = 0;
    private int wsRetryCount = 0;
    private static final int MAX_RETRIES = 6;

    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_onboarding);

        ViewCompat.setOnApplyWindowInsetsListener(getWindow().getDecorView(), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            Insets ime = insets.getInsets(WindowInsetsCompat.Type.ime());
            // Use whichever is taller: nav bar or keyboard — keeps input bar visible above keyboard
            int bottom = Math.max(systemBars.bottom, ime.bottom);
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, bottom);
            return insets;
        });

        layoutChat = findViewById(R.id.layoutChat);
        layoutCompleting = findViewById(R.id.layoutCompleting);
        rvChat = findViewById(R.id.rvChat);
        etMessage = findViewById(R.id.etMessage);
        btnSend = findViewById(R.id.btnSend);
        btnBuildRoadmap = findViewById(R.id.btnBuildRoadmap);
        chipScrollView = findViewById(R.id.chipScrollView);
        chipContainer = findViewById(R.id.chipContainer);

        stepChatSpinner = findViewById(R.id.stepChatSpinner);
        stepAnalyzeSpinner = findViewById(R.id.stepAnalyzeSpinner);
        stepBuildSpinner = findViewById(R.id.stepBuildSpinner);
        stepChatIcon = findViewById(R.id.stepChatIcon);
        stepAnalyzeIcon = findViewById(R.id.stepAnalyzeIcon);
        stepBuildIcon = findViewById(R.id.stepBuildIcon);

        chatAdapter = new ChatAdapter();
        rvChat.setLayoutManager(new LinearLayoutManager(this));
        rvChat.setAdapter(chatAdapter);

        btnSend.setEnabled(false); // enabled once WebSocket opens
        btnSend.setOnClickListener(v -> sendMessage());
        btnBuildRoadmap.setOnClickListener(v -> completeOnboarding());

        // Block back navigation while roadmap is being built
        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                if (isCompleting) return;
                setEnabled(false);
                getOnBackPressedDispatcher().onBackPressed();
            }
        });

        // Step 1: Create REST session → then connect WebSocket
        createSession();
    }

    // ── Session Creation ──────────────────────────────────────────────────────

    private void createSession() {
        // Guard: if we already have a session (possibly from a delayed retry firing late),
        // don't create another — the backend hard-deletes previous onboarding sessions
        // which would crash the old consumer mid-stream.
        if (sessionId != null) return;

        ApiClient.getService()
                .createChatSession(TokenManager.getBearerToken(this),
                        new CreateSessionRequest("onboarding"))
                .enqueue(new Callback<CreateSessionResponse>() {
                    @Override
                    public void onResponse(Call<CreateSessionResponse> call,
                                           retrofit2.Response<CreateSessionResponse> response) {
                        if (response.isSuccessful() && response.body() != null
                                && response.body().getSessionId() != null) {
                            sessionId = response.body().getSessionId();
                            sessionRetryCount = 0;
                            connectWebSocket();
                        } else {
                            retryCreateSession(false);
                        }
                    }

                    @Override
                    public void onFailure(Call<CreateSessionResponse> call, Throwable t) {
                        retryCreateSession(t instanceof SocketTimeoutException);
                    }
                });
    }

    private void retryCreateSession(boolean isTimeout) {
        if (isFinishing()) return;
        sessionRetryCount++;
        if (sessionRetryCount > MAX_RETRIES) {
            Snackbar.make(rvChat, R.string.error_server_timeout, Snackbar.LENGTH_INDEFINITE)
                    .setAction("Retry", v -> {
                        sessionRetryCount = 0;
                        sessionId = null; // allow fresh session creation
                        createSession();
                    })
                    .show();
            return;
        }
        int msg = (isTimeout || sessionRetryCount <= 2)
                ? R.string.error_server_starting : R.string.error_network;
        Snackbar.make(rvChat, msg, Snackbar.LENGTH_LONG).show();
        long delay = Math.min(3000L * (1L << (sessionRetryCount - 1)), 30_000L);
        mainHandler.postDelayed(() -> {
            if (!isFinishing()) createSession();
        }, delay);
    }

    // ── WebSocket Connection ──────────────────────────────────────────────────

    private void connectWebSocket() {
        // Reset streaming state in case this is a reconnect
        streamingBuffer.setLength(0);
        isStreaming = false;
        streamingMsgIndex = -1;

        String token = TokenManager.getAccessToken(this);
        // Correct URL: ws://host/ws/chat/{session_id}/?token={access_token}
        String url = ApiClient.WS_BASE_URL + "chat/" + sessionId + "/?token=" + token;

        Request request = new Request.Builder().url(url).build();

        webSocket = ApiClient.getOkHttpClient().newWebSocket(request, new WebSocketListener() {
            @Override
            public void onOpen(WebSocket ws, Response response) {
                mainHandler.post(() -> {
                    wsRetryCount = 0;
                    clearSuggestions();
                    btnSend.setEnabled(true);
                    // Show typing indicator only on first connect (no messages yet).
                    // On reconnect the server skips the greeting, so we don't add one.
                    if (chatAdapter.getItemCount() == 0) {
                        chatAdapter.addMessage(new ChatMessage(true));
                        streamingMsgIndex = chatAdapter.getItemCount() - 1;
                    }
                });
            }

            @Override
            public void onMessage(WebSocket ws, String text) {
                mainHandler.post(() -> handleServerMessage(text));
            }

            @Override
            public void onMessage(WebSocket ws, ByteString bytes) {
                mainHandler.post(() -> handleServerMessage(bytes.utf8()));
            }

            @Override
            public void onClosing(WebSocket ws, int code, String reason) {
                ws.close(1000, null);
                // Server-initiated close: reset state and reconnect unless it was our own clean close.
                if (code != 1000) {
                    mainHandler.post(() -> {
                        removeTypingIndicator();
                        isStreaming = false;
                        streamingBuffer.setLength(0);
                        if (!isFinishing() && !isCompleting) {
                            wsRetryCount++;
                            int msgRes = (wsRetryCount <= 2)
                                    ? R.string.error_server_starting : R.string.error_connection_lost;
                            Snackbar.make(rvChat, msgRes, Snackbar.LENGTH_LONG).show();
                            long delay = Math.min(3000L * (1L << (wsRetryCount - 1)), 30_000L);
                            mainHandler.postDelayed(() -> {
                                if (!isFinishing() && !isCompleting) connectWebSocket();
                            }, delay);
                        }
                    });
                }
            }

            @Override
            public void onFailure(WebSocket ws, Throwable t, Response response) {
                mainHandler.post(() -> {
                    // Clean up any pending typing indicator and streaming state
                    removeTypingIndicator();
                    isStreaming = false;
                    streamingBuffer.setLength(0);
                    if (!isFinishing() && !isCompleting) {
                        wsRetryCount++;
                        int msgRes = (wsRetryCount <= 2 || t instanceof SocketTimeoutException)
                                ? R.string.error_server_starting : R.string.error_connection_lost;
                        Snackbar.make(rvChat, msgRes, Snackbar.LENGTH_LONG).show();
                        long delay = Math.min(3000L * (1L << (wsRetryCount - 1)), 30_000L);
                        mainHandler.postDelayed(() -> {
                            if (!isFinishing() && !isCompleting) connectWebSocket();
                        }, delay);
                    }
                });
            }
        });
    }

    // ── WebSocket Message Handling ────────────────────────────────────────────

    private void handleServerMessage(String raw) {
        try {
            JSONObject obj = new JSONObject(raw);
            String type = obj.optString("type", "");

            switch (type) {
                case "stream_chunk":
                    // AI is streaming — append to buffer
                    String chunk = obj.optString("content", "");
                    streamingBuffer.append(chunk);
                    isStreaming = true;
                    // Show live partial message (last bubble) — update in place
                    updateStreamingBubble(streamingBuffer.toString());
                    break;

                case "stream_end":
                    // Streaming finished — commit the complete message
                    String cleanContent = obj.optString("clean_content", streamingBuffer.toString());
                    // Remove the streaming placeholder, add final message
                    commitStreamedMessage(cleanContent);
                    streamingBuffer.setLength(0);
                    isStreaming = false;
                    // Check for READY_SIGNALS in committed AI message
                    checkReadySignals(cleanContent.toLowerCase());
                    // Show suggestion chips if backend provided them
                    JSONArray suggestionsArr = obj.optJSONArray("suggestions");
                    if (suggestionsArr != null && suggestionsArr.length() > 0) {
                        showSuggestions(suggestionsArr);
                    } else {
                        clearSuggestions();
                    }
                    break;

                case "stream_error":
                    removeTypingIndicator();
                    isStreaming = false;
                    streamingBuffer.setLength(0);
                    String errMsg = obj.optString("error", getString(R.string.error_network));
                    Snackbar.make(rvChat, errMsg, Snackbar.LENGTH_LONG).show();
                    break;

                default:
                    // Unknown or greeting message — treat as plain AI text if non-empty
                    String fallback = obj.optString("content", obj.optString("message", ""));
                    if (!fallback.isEmpty()) {
                        chatAdapter.addMessage(new ChatMessage(fallback, false));
                        checkReadySignals(fallback.toLowerCase());
                    }
                    break;
            }
        } catch (Exception e) {
            // Plain text (non-JSON) — add as AI message
            if (!raw.trim().isEmpty()) {
                chatAdapter.addMessage(new ChatMessage(raw, false));
            }
        }
    }

    /** Show streaming text live in a single AI bubble, updating it in-place as chunks arrive. */
    private void updateStreamingBubble(String partialText) {
        if (streamingMsgIndex == -1) {
            chatAdapter.addMessage(new ChatMessage(partialText, false));
            streamingMsgIndex = chatAdapter.getItemCount() - 1;
        } else {
            chatAdapter.updateMessageAt(streamingMsgIndex, partialText);
        }
    }

    /** Finalize the streamed bubble with clean server content, then reset the index. */
    private void commitStreamedMessage(String content) {
        if (content.isEmpty()) {
            streamingMsgIndex = -1;
            return;
        }
        if (streamingMsgIndex != -1) {
            // Update existing bubble with the clean final content
            chatAdapter.updateMessageAt(streamingMsgIndex, content);
        } else {
            chatAdapter.addMessage(new ChatMessage(content, false));
        }
        streamingMsgIndex = -1;
    }

    // ── READY_SIGNALS detection (matches web logic exactly) ───────────────────

    private void checkReadySignals(String lowerText) {
        if (btnBuildRoadmap.getVisibility() == View.VISIBLE) return;
        for (String signal : READY_SIGNALS) {
            if (containsSignal(lowerText, signal)) {
                btnBuildRoadmap.setVisibility(View.VISIBLE);
                return;
            }
        }
    }

    /** Returns true if signal is in text AND the 25 chars before it contain no negation. */
    private boolean containsSignal(String text, String signal) {
        int idx = text.indexOf(signal);
        if (idx == -1) return false;
        String before = text.substring(Math.max(0, idx - 25), idx);
        for (String neg : NEGATIONS) {
            if (before.contains(neg)) return false;
        }
        return true;
    }

    // ── Send Message ──────────────────────────────────────────────────────────

    private void sendMessage() {
        String text = etMessage.getText() != null
                ? etMessage.getText().toString().trim() : "";
        if (text.isEmpty()) return;
        etMessage.setText("");
        sendMessageText(text);
    }

    /** Used by both the send button and suggestion chip clicks. */
    private void sendMessageText(String text) {
        if (text.isEmpty() || isStreaming || webSocket == null) return;
        clearSuggestions();
        isStreaming = true;
        chatAdapter.addMessage(new ChatMessage(text, true));
        // Add typing indicator — replaced by first stream chunk via updateStreamingBubble()
        chatAdapter.addMessage(new ChatMessage(true));
        streamingMsgIndex = chatAdapter.getItemCount() - 1;
        try {
            JSONObject payload = new JSONObject();
            payload.put("message", text);
            payload.put("language", "english");
            webSocket.send(payload.toString());
        } catch (Exception e) {
            removeTypingIndicator();
            isStreaming = false;
            Snackbar.make(rvChat, R.string.error_network, Snackbar.LENGTH_SHORT).show();
        }
    }

    private void removeTypingIndicator() {
        if (streamingMsgIndex != -1 && chatAdapter.isTypingAt(streamingMsgIndex)) {
            chatAdapter.removeMessageAt(streamingMsgIndex);
        }
        streamingMsgIndex = -1;
    }

    // ── Suggestion Chips ─────────────────────────────────────────────────────

    private void showSuggestions(JSONArray suggestions) {
        chipContainer.removeAllViews();
        if (suggestions.length() == 0) {
            chipScrollView.setVisibility(View.GONE);
            return;
        }
        float dp = getResources().getDisplayMetrics().density;
        int marginPx = (int) (8 * dp);
        for (int i = 0; i < suggestions.length(); i++) {
            String option;
            try { option = suggestions.getString(i); } catch (Exception e) { continue; }
            Chip chip = new Chip(new ContextThemeWrapper(this,
                    com.google.android.material.R.style.Widget_MaterialComponents_Chip_Choice), null, 0);
            chip.setText(option);
            chip.setChipBackgroundColor(ColorStateList.valueOf(Color.parseColor("#1A1A1A")));
            chip.setChipStrokeColor(ColorStateList.valueOf(Color.parseColor("#80FFFFFF")));
            chip.setChipStrokeWidth(dp);
            chip.setTextColor(Color.WHITE);
            chip.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f);
            chip.setEnsureMinTouchTargetSize(false);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT);
            lp.setMarginEnd(marginPx);
            chip.setLayoutParams(lp);
            final String opt = option;
            chip.setOnClickListener(v -> sendMessageText(opt));
            chipContainer.addView(chip);
        }
        chipScrollView.setVisibility(View.VISIBLE);
    }

    private void clearSuggestions() {
        chipContainer.removeAllViews();
        chipScrollView.setVisibility(View.GONE);
    }

    // ── Complete Onboarding ───────────────────────────────────────────────────

    private void completeOnboarding() {
        if (sessionId == null || isCompleting) return;

        isCompleting = true;
        clearSuggestions();
        btnSend.setEnabled(false);
        btnBuildRoadmap.setVisibility(View.GONE);

        // Close WebSocket — no more chat messages needed
        if (webSocket != null) {
            webSocket.close(1000, "Completing onboarding");
            webSocket = null;
        }

        // Show completing overlay — Step 1 active
        layoutChat.setVisibility(View.GONE);
        layoutCompleting.setVisibility(View.VISIBLE);
        showStep(1);

        String bearer = TokenManager.getBearerToken(this);

        // Step 2: completeFromChat
        ApiClient.getService()
                .completeOnboarding(bearer, new CompleteOnboardingRequest(sessionId))
                .enqueue(new Callback<CompleteOnboardingResponse>() {
                    @Override
                    public void onResponse(Call<CompleteOnboardingResponse> call,
                                           retrofit2.Response<CompleteOnboardingResponse> response) {
                        if (response.isSuccessful() && response.body() != null) {
                            CompleteOnboardingResponse body = response.body();
                            // Save fresh tokens (backend re-issues with is_onboarded=true)
                            String newAccess = body.getAccess();
                            String newRefresh = body.getRefresh();
                            if (newAccess != null) {
                                TokenManager.saveTokens(OnboardingActivity.this,
                                        newAccess, newRefresh);
                            }

                            showStep(2);

                            // Step 3: generate roadmap
                            String newBearer = TokenManager.getBearerToken(
                                    OnboardingActivity.this);
                            ApiClient.getService()
                                    .generateRoadmap(newBearer)
                                    .enqueue(new Callback<Roadmap>() {
                                        @Override
                                        public void onResponse(Call<Roadmap> call2,
                                                               retrofit2.Response<Roadmap> r2) {
                                            showStep(3);
                                            // Brief delay so user sees step 3 complete
                                            mainHandler.postDelayed(
                                                    OnboardingActivity.this::goToDashboard,
                                                    800);
                                        }

                                        @Override
                                        public void onFailure(Call<Roadmap> call2, Throwable t) {
                                            // Even if generation fails, onboarding is done
                                            showStep(3);
                                            mainHandler.postDelayed(
                                                    OnboardingActivity.this::goToDashboard,
                                                    800);
                                        }
                                    });
                        } else {
                            resetAfterError("Could not complete onboarding.");
                        }
                    }

                    @Override
                    public void onFailure(Call<CompleteOnboardingResponse> call, Throwable t) {
                        resetAfterError(getString(R.string.error_network));
                    }
                });
    }

    // ── Completing screen step control ────────────────────────────────────────

    /**
     * step=1 → chat done (spinner visible), analyze pending
     * step=2 → chat done (icon), analyze active (spinner), build pending
     * step=3 → all done (icons)
     */
    private void showStep(int step) {
        runOnUiThread(() -> {
            // Step 1: chat
            stepChatSpinner.setVisibility(step == 1 ? View.VISIBLE : View.GONE);
            stepChatIcon.setVisibility(step > 1 ? View.VISIBLE : View.GONE);
            // Step 2: analyze
            stepAnalyzeSpinner.setVisibility(step == 2 ? View.VISIBLE : View.GONE);
            stepAnalyzeIcon.setVisibility(step > 2 ? View.VISIBLE : View.GONE);
            // Step 3: build
            stepBuildSpinner.setVisibility(step == 3 ? View.VISIBLE : View.GONE);
            stepBuildIcon.setVisibility(step > 3 ? View.VISIBLE : View.GONE);
        });
    }

    private void resetAfterError(String message) {
        runOnUiThread(() -> {
            isCompleting = false;
            layoutCompleting.setVisibility(View.GONE);
            layoutChat.setVisibility(View.VISIBLE);
            btnBuildRoadmap.setVisibility(View.VISIBLE);
            btnSend.setEnabled(true);
            Snackbar.make(rvChat, message, Snackbar.LENGTH_LONG).show();
        });
    }

    private void goToDashboard() {
        Intent intent = new Intent(this, DashboardActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        finish();
    }

    // ── Lifecycle ─────────────────────────────────────────────────────────────

    @Override
    protected void onDestroy() {
        super.onDestroy();
        mainHandler.removeCallbacksAndMessages(null);
        if (webSocket != null) {
            webSocket.close(1000, null);
        }
    }
}
