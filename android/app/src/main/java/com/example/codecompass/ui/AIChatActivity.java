package com.example.codecompass.ui;

import android.content.Intent;
import androidx.appcompat.view.ContextThemeWrapper;
import android.content.res.ColorStateList;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.MenuItem;
import android.view.View;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.OnBackPressedCallback;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.codecompass.R;
import com.example.codecompass.api.ApiClient;
import com.example.codecompass.api.TokenManager;
import com.example.codecompass.util.JwtUtils;
import com.example.codecompass.model.ChatMessage;
import com.example.codecompass.model.ChatSessionDetail;
import com.example.codecompass.model.CreateSessionRequest;
import com.example.codecompass.model.CreateSessionResponse;
import com.example.codecompass.model.EditProposal;
import com.example.codecompass.model.ResourceLink;
import com.example.codecompass.model.RoadmapSwitchProposal;
import com.example.codecompass.model.RoadmapUpskillProposal;
import com.example.codecompass.model.SessionMessage;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import java.lang.reflect.Type;
import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.button.MaterialButtonToggleGroup;
import com.google.android.material.chip.Chip;
import com.google.android.material.snackbar.Snackbar;
import com.google.android.material.tabs.TabLayout;
import com.google.android.material.textfield.TextInputEditText;

import org.json.JSONArray;
import org.json.JSONObject;

import java.net.SocketTimeoutException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import okhttp3.Request;
import okhttp3.Response;
import okhttp3.WebSocket;
import okhttp3.WebSocketListener;
import okio.ByteString;
import retrofit2.Call;
import retrofit2.Callback;

public class AIChatActivity extends AppCompatActivity {

    public static final String EXTRA_SESSION_ID   = "session_id";
    public static final String EXTRA_CONTEXT_TYPE = "context_type";

    // ── Views ─────────────────────────────────────────────────────────────────
    private RecyclerView rvChat;
    private TextInputEditText etMessage;
    private MaterialButton btnSend;
    private HorizontalScrollView chipScrollView;
    private LinearLayout chipContainer;
    private TabLayout tabModes;
    private MaterialButtonToggleGroup langToggleGroup;
    private ScrollView scrollWelcome;
    private TextView tvWelcomeEmoji;
    private TextView tvWelcomeText;
    private LinearLayout containerPrompts;

    // ── State ─────────────────────────────────────────────────────────────────
    private ChatAdapter chatAdapter;
    private WebSocket webSocket;
    private String sessionId;
    private String currentContextType = "general";
    private String chatLanguage = "english";
    private boolean isStreaming = false;
    private String pendingFirstMessage = null;
    private final StringBuilder streamingBuffer = new StringBuilder();
    private int streamingMsgIndex = -1;
    private int sessionRetryCount = 0;
    private int wsRetryCount = 0;
    private int previousTabPosition = 0;

    // ── Per-mode session memory ───────────────────────────────────────────────
    private final String[]                       modeSessionIds = new String[4];
    private final Map<Integer, List<ChatMessage>> modeMessages   = new HashMap<>();
    private final String[]                       modeTitles     = new String[4];

    // ── Proposal listener ─────────────────────────────────────────────────────
    private final ChatAdapter.OnProposalListener proposalListener = new ChatAdapter.OnProposalListener() {
        @Override
        public void onApply(int messageIndex, List<EditProposal> proposals) {
            applyProposals(messageIndex, proposals, 0);
        }
        @Override
        public void onDismiss(int messageIndex) {
            chatAdapter.markProposalsDismissed(messageIndex);
        }
    };

    // ── Roadmap action listener ───────────────────────────────────────────────
    private final ChatAdapter.OnRoadmapActionListener roadmapActionListener =
            new ChatAdapter.OnRoadmapActionListener() {
        @Override
        public void onSwitchRoadmap(int messageIndex, RoadmapSwitchProposal proposal) {
            callSwitchApi(messageIndex, proposal);
        }
        @Override
        public void onUpskillRoadmap(int messageIndex, RoadmapUpskillProposal proposal) {
            callUpskillApi(messageIndex, proposal);
        }
        @Override
        public void onDismissRoadmapSwitch(int messageIndex) {
            chatAdapter.markSwitchDismissed(messageIndex);
        }
        @Override
        public void onDismissRoadmapUpskill(int messageIndex) {
            chatAdapter.markUpskillDismissed(messageIndex);
        }
    };
    private static final int MAX_RETRIES = 6;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    // ── Mode metadata ─────────────────────────────────────────────────────────

    private static final String[] MODE_CONTEXT_TYPES = { "general", "roadmap", "job", "university" };
    private static final String[] MODE_EMOJIS        = { "💬", "🗺️", "💼", "🏫" };
    private static final int[]    MODE_WELCOME_RES   = {
        R.string.ai_welcome_general,
        R.string.ai_welcome_roadmap,
        R.string.ai_welcome_jobs,
        R.string.ai_welcome_university
    };
    private static final String[][] MODE_PROMPTS = {
        {
            "How do I start a career in software engineering?",
            "What skills should a CCS student focus on?",
            "Which programming language should I learn first?"
        },
        {
            "What should I work on next in my roadmap?",
            "Explain my current topic in simple terms",
            "How long will this roadmap take?"
        },
        {
            "What companies hire fresh grads in the Philippines?",
            "What is the salary range for developers in PH?",
            "How do I prepare for a tech interview?"
        },
        {
            "What is the difference between BSCS and BSIT?",
            "What are the top CCS schools in the Philippines?",
            "What subjects are hardest in a CS degree?"
        }
    };

    // ── Lifecycle ─────────────────────────────────────────────────────────────

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_ai_chat);

        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setTitle(R.string.ai_chat_title);
        }

        rvChat           = findViewById(R.id.rvChat);
        etMessage        = findViewById(R.id.etMessage);
        btnSend          = findViewById(R.id.btnSend);
        chipScrollView   = findViewById(R.id.chipScrollView);
        chipContainer    = findViewById(R.id.chipContainer);
        tabModes         = findViewById(R.id.tabModes);
        langToggleGroup  = findViewById(R.id.langToggleGroup);
        scrollWelcome    = findViewById(R.id.scrollWelcome);
        tvWelcomeEmoji   = findViewById(R.id.tvWelcomeEmoji);
        tvWelcomeText    = findViewById(R.id.tvWelcomeText);
        containerPrompts = findViewById(R.id.containerPrompts);

        chatAdapter = new ChatAdapter(true, proposalListener, roadmapActionListener);
        rvChat.setLayoutManager(new LinearLayoutManager(this));
        rvChat.setAdapter(chatAdapter);

        btnSend.setOnClickListener(v -> sendMessage());

        setupTabModes();
        setupLangToggle();
        setupBottomNav();

        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                finish();
                overridePendingTransition(R.anim.slide_in_left, R.anim.slide_out_right);
            }
        });

        // Check for resume mode (launched from chat history)
        String resumeSessionId   = getIntent().getStringExtra(EXTRA_SESSION_ID);
        String resumeContextType = getIntent().getStringExtra(EXTRA_CONTEXT_TYPE);

        if (resumeSessionId != null) {
            // Resume an existing session
            sessionId = resumeSessionId;
            currentContextType = (resumeContextType != null) ? resumeContextType : "general";
            int tabIndex = tabIndexFor(currentContextType);
            previousTabPosition = tabIndex;

            // Select the correct tab without triggering the tab listener
            tabModes.removeOnTabSelectedListener(tabModes.getTabAt(0) != null
                    ? null : null); // listener added after tabs — safe to select now
            TabLayout.Tab tab = tabModes.getTabAt(tabIndex);
            if (tab != null) tab.select();

            hideWelcomeState();
            btnSend.setEnabled(false);
            loadSessionHistory(resumeSessionId);
        } else {
            // Fresh session
            showWelcomeState(0);
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        mainHandler.removeCallbacksAndMessages(null);
        if (webSocket != null) {
            webSocket.close(1000, null);
            webSocket = null;
        }
    }

    // ── Mode tabs ─────────────────────────────────────────────────────────────

    private void setupTabModes() {
        String role = JwtUtils.getRole(TokenManager.getAccessToken(this));
        boolean showUniversity = "incoming_student".equals(role);

        int[] labelRes = {
            R.string.mode_general, R.string.mode_roadmap,
            R.string.mode_jobs,    R.string.mode_university
        };
        for (int i = 0; i < labelRes.length; i++) {
            if (i == 3 && !showUniversity) continue;
            TabLayout.Tab tab = tabModes.newTab();
            tab.setText(MODE_EMOJIS[i] + " " + getString(labelRes[i]));
            tabModes.addTab(tab);
        }

        tabModes.addOnTabSelectedListener(new TabLayout.OnTabSelectedListener() {
            @Override
            public void onTabSelected(TabLayout.Tab tab) {
                int pos = tab.getPosition();
                if (pos == previousTabPosition) return;

                if (isStreaming || pendingFirstMessage != null) {
                    tabModes.removeOnTabSelectedListener(this);
                    TabLayout.Tab prev = tabModes.getTabAt(previousTabPosition);
                    if (prev != null) tabModes.selectTab(prev);
                    tabModes.addOnTabSelectedListener(this);
                    Snackbar.make(rvChat, R.string.ai_chat_wait_streaming, Snackbar.LENGTH_SHORT).show();
                    return;
                }

                int outgoing        = previousTabPosition;
                previousTabPosition = pos;
                currentContextType  = MODE_CONTEXT_TYPES[pos];
                startNewSession(pos, outgoing);
            }

            @Override public void onTabUnselected(TabLayout.Tab tab)  {}
            @Override public void onTabReselected(TabLayout.Tab tab) {}
        });
    }

    private static int tabIndexFor(String contextType) {
        if (contextType == null) return 0;
        switch (contextType) {
            case "roadmap":    return 1;
            case "job":        return 2;
            case "university": return 3;
            default:           return 0;
        }
    }

    // ── Language toggle ───────────────────────────────────────────────────────

    private void setupLangToggle() {
        langToggleGroup.check(R.id.btnLangEn);
        langToggleGroup.addOnButtonCheckedListener((group, checkedId, isChecked) -> {
            if (!isChecked) return;
            if      (checkedId == R.id.btnLangTl) chatLanguage = "tagalog";
            else if (checkedId == R.id.btnLangTg) chatLanguage = "taglish";
            else                                  chatLanguage = "english";
        });
    }

    // ── Bottom navigation ─────────────────────────────────────────────────────

    private void setupBottomNav() {
        BottomNavigationView bottomNav = findViewById(R.id.bottomNav);
        bottomNav.setSelectedItemId(R.id.nav_chat);
        bottomNav.setOnItemSelectedListener(item -> {
            int id = item.getItemId();
            if (id == R.id.nav_chat) return true;
            if (id == R.id.nav_home) {
                Intent homeIntent = new Intent(this, DashboardActivity.class);
                homeIntent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
                startActivity(homeIntent);
                overridePendingTransition(R.anim.slide_in_left, R.anim.slide_out_right);
                return false;
            }
            if (id == R.id.nav_roadmap) {
                Intent intent = new Intent(this, RoadmapActivity.class);
                intent.addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT);
                startActivity(intent);
                overridePendingTransition(R.anim.slide_in_left, R.anim.slide_out_right);
                return false;
            }
            if (id == R.id.nav_profile) {
                Intent intent = new Intent(this, ProfileActivity.class);
                intent.addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT);
                startActivity(intent);
                overridePendingTransition(R.anim.slide_in_right, R.anim.slide_out_left);
                return false;
            }
            Toast.makeText(this, R.string.coming_soon, Toast.LENGTH_SHORT).show();
            return false;
        });
    }

    // ── Welcome state ─────────────────────────────────────────────────────────

    private void showWelcomeState(int modeIndex) {
        tvWelcomeEmoji.setText(MODE_EMOJIS[modeIndex]);
        tvWelcomeText.setText(MODE_WELCOME_RES[modeIndex]);
        buildPromptButtons(modeIndex);
        scrollWelcome.setVisibility(View.VISIBLE);
    }

    private void hideWelcomeState() {
        scrollWelcome.setVisibility(View.GONE);
    }

    private void buildPromptButtons(int modeIndex) {
        containerPrompts.removeAllViews();
        float dp       = getResources().getDisplayMetrics().density;
        int marginPx   = (int) (8 * dp);
        int strokePx   = Math.max(1, (int) dp);

        for (String prompt : MODE_PROMPTS[modeIndex]) {
            MaterialButton btn = new MaterialButton(
                    this, null, com.google.android.material.R.attr.materialButtonOutlinedStyle);
            btn.setText(prompt);
            btn.setGravity(Gravity.START | Gravity.CENTER_VERTICAL);
            btn.setTextColor(getColor(R.color.colorTextPrimary));
            btn.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f);
            btn.setBackgroundTintList(
                    ColorStateList.valueOf(getColor(R.color.colorBrandYellowPale)));
            btn.setStrokeColor(ColorStateList.valueOf(getColor(R.color.colorPrimary)));
            btn.setStrokeWidth(strokePx);

            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT);
            lp.bottomMargin = marginPx;
            btn.setLayoutParams(lp);

            final String p = prompt;
            btn.setOnClickListener(v -> sendMessageText(p));
            containerPrompts.addView(btn);
        }
    }

    // ── Resume: load session history ──────────────────────────────────────────

    private void loadSessionHistory(String sid) {
        ApiClient.getService()
                .getChatSessionDetail(TokenManager.getBearerToken(this), sid)
                .enqueue(new Callback<ChatSessionDetail>() {
                    @Override
                    public void onResponse(Call<ChatSessionDetail> call,
                                           retrofit2.Response<ChatSessionDetail> response) {
                        if (response.isSuccessful() && response.body() != null) {
                            ChatSessionDetail detail = response.body();
                            if (detail.getTitle() != null && !detail.getTitle().isEmpty()
                                    && getSupportActionBar() != null) {
                                getSupportActionBar().setTitle(detail.getTitle());
                            }
                            List<SessionMessage> msgs = detail.getMessages();
                            if (msgs != null) {
                                for (SessionMessage m : msgs) {
                                    if ("system".equals(m.getRole())) continue;
                                    boolean isUser = "user".equals(m.getRole());
                                    chatAdapter.addMessage(
                                            new ChatMessage(m.getContent(), isUser));
                                }
                            }
                        } else {
                            Snackbar.make(rvChat, R.string.error_network,
                                    Snackbar.LENGTH_SHORT).show();
                        }
                        btnSend.setEnabled(true);
                        connectWebSocket();
                    }

                    @Override
                    public void onFailure(Call<ChatSessionDetail> call, Throwable t) {
                        Snackbar.make(rvChat, R.string.error_network,
                                Snackbar.LENGTH_SHORT).show();
                        btnSend.setEnabled(true);
                        connectWebSocket();
                    }
                });
    }

    // ── Session management ────────────────────────────────────────────────────

    private void startNewSession(int modeIndex, int outgoingIndex) {
        // 1. Save outgoing tab state
        modeSessionIds[outgoingIndex] = sessionId;
        modeMessages.put(outgoingIndex, chatAdapter.getMessages());
        modeTitles[outgoingIndex] = (getSupportActionBar() != null
                && getSupportActionBar().getTitle() != null)
                ? getSupportActionBar().getTitle().toString() : null;

        // 2. Tear down current connection
        if (webSocket != null) {
            webSocket.close(1000, "Mode changed");
            webSocket = null;
        }
        sessionId           = null;
        pendingFirstMessage = null;
        isStreaming         = false;
        streamingMsgIndex   = -1;
        sessionRetryCount   = 0;
        wsRetryCount        = 0;
        streamingBuffer.setLength(0);
        clearSuggestions();

        // 3. Restore incoming tab or start fresh
        String savedId            = modeSessionIds[modeIndex];
        List<ChatMessage> saved   = modeMessages.get(modeIndex);

        chatAdapter = new ChatAdapter(true, proposalListener, roadmapActionListener);
        rvChat.setAdapter(chatAdapter);

        if (savedId != null && saved != null && !saved.isEmpty()) {
            // Restore previous conversation for this tab
            sessionId = savedId;
            chatAdapter.setMessages(saved);
            rvChat.scrollToPosition(saved.size() - 1);
            String savedTitle = modeTitles[modeIndex];
            if (getSupportActionBar() != null) {
                getSupportActionBar().setTitle(
                        savedTitle != null ? savedTitle : getString(R.string.ai_chat_title));
            }
            hideWelcomeState();
            btnSend.setEnabled(true);
            connectWebSocket();
        } else {
            if (getSupportActionBar() != null) {
                getSupportActionBar().setTitle(R.string.ai_chat_title);
            }
            showWelcomeState(modeIndex);
        }
    }

    private void createSession() {
        if (sessionId != null) return;

        ApiClient.getService()
                .createChatSession(TokenManager.getBearerToken(this),
                        new CreateSessionRequest(currentContextType))
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
            removeTypingIndicator();
            isStreaming          = false;
            pendingFirstMessage  = null;
            Snackbar.make(rvChat, R.string.error_server_timeout, Snackbar.LENGTH_INDEFINITE)
                    .setAction(R.string.retry, v -> {
                        sessionRetryCount = 0;
                        sessionId         = null;
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

    // ── WebSocket ─────────────────────────────────────────────────────────────

    private void connectWebSocket() {
        if (sessionId == null) return;
        streamingBuffer.setLength(0);

        String token = TokenManager.getAccessToken(this);
        String url   = ApiClient.WS_BASE_URL + "chat/" + sessionId + "/?token=" + token;
        Request request = new Request.Builder().url(url).build();

        webSocket = ApiClient.getOkHttpClient().newWebSocket(request, new WebSocketListener() {
            @Override
            public void onOpen(WebSocket ws, Response response) {
                mainHandler.post(() -> {
                    wsRetryCount = 0;
                    if (pendingFirstMessage == null) return;

                    String msg = pendingFirstMessage;
                    pendingFirstMessage = null;
                    isStreaming = true;
                    try {
                        JSONObject payload = new JSONObject();
                        payload.put("message", msg);
                        payload.put("language", chatLanguage);
                        ws.send(payload.toString());
                    } catch (Exception e) {
                        removeTypingIndicator();
                        isStreaming = false;
                        Snackbar.make(rvChat, R.string.error_network, Snackbar.LENGTH_SHORT).show();
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
                        // Session no longer exists on server — force recreation on reconnect.
                        if (code == 4004) sessionId = null;
                        if (!isFinishing()) {
                            wsRetryCount++;
                            int msgRes = (wsRetryCount <= 2)
                                    ? R.string.error_server_starting
                                    : R.string.error_connection_lost;
                            Snackbar.make(rvChat, msgRes, Snackbar.LENGTH_LONG).show();
                            long delay = Math.min(3000L * (1L << (wsRetryCount - 1)), 30_000L);
                            mainHandler.postDelayed(() -> {
                                if (!isFinishing()) {
                                    if (sessionId == null) createSession();
                                    else connectWebSocket();
                                }
                            }, delay);
                        }
                    });
                }
            }

            @Override
            public void onFailure(WebSocket ws, Throwable t, Response response) {
                mainHandler.post(() -> {
                    removeTypingIndicator();
                    isStreaming = false;
                    streamingBuffer.setLength(0);
                    if (!isFinishing()) {
                        wsRetryCount++;
                        int msgRes = (wsRetryCount <= 2 || t instanceof SocketTimeoutException)
                                ? R.string.error_server_starting
                                : R.string.error_connection_lost;
                        Snackbar.make(rvChat, msgRes, Snackbar.LENGTH_LONG).show();
                        long delay = Math.min(3000L * (1L << (wsRetryCount - 1)), 30_000L);
                        mainHandler.postDelayed(() -> {
                            if (!isFinishing()) connectWebSocket();
                        }, delay);
                    }
                });
            }
        });
    }

    // ── Server message handling ───────────────────────────────────────────────

    private void handleServerMessage(String raw) {
        try {
            JSONObject obj  = new JSONObject(raw);
            String     type = obj.optString("type", "");

            switch (type) {
                case "stream_chunk":
                    streamingBuffer.append(obj.optString("content", ""));
                    isStreaming = true;
                    updateStreamingBubble(streamingBuffer.toString());
                    break;

                case "stream_end":
                    String clean = obj.optString("clean_content", streamingBuffer.toString());
                    int committedIdx = commitStreamedMessage(clean);
                    streamingBuffer.setLength(0);
                    isStreaming = false;

                    // Attach resource link cards
                    JSONArray resArr = obj.optJSONArray("resources");
                    if (resArr != null && resArr.length() > 0 && committedIdx >= 0) {
                        List<ResourceLink> links = new ArrayList<>();
                        for (int i = 0; i < resArr.length(); i++) {
                            JSONObject r = resArr.optJSONObject(i);
                            if (r != null) {
                                String rTitle = r.optString("title", "");
                                String rUrl   = r.optString("url", "");
                                if (!rUrl.isEmpty()) links.add(new ResourceLink(rTitle, rUrl));
                            }
                        }
                        if (!links.isEmpty()) chatAdapter.setResourcesAt(committedIdx, links);
                    }

                    // Render-time scope guard: roadmap mutation cards (Apply / Confirm
                    // / Upskill) must NEVER appear outside the Roadmap tab. Server-side
                    // _extract_tags_for_scope should already have nulled these payloads
                    // for non-roadmap sessions, but belt-and-braces — an older backend
                    // or a buggy response shouldn't be able to surface an actionable
                    // card in General / Jobs / University chat.
                    boolean canShowRoadmapCards = "roadmap".equals(currentContextType);

                    // Attach roadmap edit proposal card
                    JSONArray propsArr = obj.optJSONArray("edit_proposals");
                    if (canShowRoadmapCards && propsArr != null && propsArr.length() > 0 && committedIdx >= 0) {
                        try {
                            Type proposalListType =
                                    new TypeToken<List<EditProposal>>(){}.getType();
                            List<EditProposal> proposals =
                                    new Gson().fromJson(propsArr.toString(), proposalListType);
                            if (proposals != null && !proposals.isEmpty()) {
                                chatAdapter.setProposalsAt(committedIdx, proposals);
                            }
                        } catch (Exception ignored) {}
                    }

                    // Attach roadmap switch card
                    JSONObject switchObj = obj.optJSONObject("roadmap_switch");
                    if (canShowRoadmapCards && switchObj != null && committedIdx >= 0) {
                        try {
                            RoadmapSwitchProposal sw = new Gson().fromJson(
                                    switchObj.toString(), RoadmapSwitchProposal.class);
                            if (sw != null) chatAdapter.setRoadmapSwitchAt(committedIdx, sw);
                        } catch (Exception ignored) {}
                    }

                    // Attach roadmap upskill card
                    JSONObject upskillObj = obj.optJSONObject("roadmap_upskill");
                    if (canShowRoadmapCards && upskillObj != null && committedIdx >= 0) {
                        try {
                            RoadmapUpskillProposal up = new Gson().fromJson(
                                    upskillObj.toString(), RoadmapUpskillProposal.class);
                            if (up != null) chatAdapter.setRoadmapUpskillAt(committedIdx, up);
                        } catch (Exception ignored) {}
                    }

                    JSONArray suggestions = obj.optJSONArray("suggestions");
                    if (suggestions != null && suggestions.length() > 0) {
                        showSuggestions(suggestions);
                    } else {
                        clearSuggestions();
                    }
                    break;

                case "stream_error":
                    removeTypingIndicator();
                    isStreaming = false;
                    streamingBuffer.setLength(0);
                    Snackbar.make(rvChat,
                            obj.optString("error", getString(R.string.error_network)),
                            Snackbar.LENGTH_LONG).show();
                    break;

                case "session_title_updated":
                    String newTitle = obj.optString("title", "");
                    if (!newTitle.isEmpty() && getSupportActionBar() != null) {
                        getSupportActionBar().setTitle(newTitle);
                    }
                    break;

                default:
                    String fallback = obj.optString("content", obj.optString("message", ""));
                    if (!fallback.isEmpty()) {
                        chatAdapter.addMessage(new ChatMessage(fallback, false));
                    }
                    break;
            }
        } catch (Exception e) {
            if (!raw.trim().isEmpty()) {
                chatAdapter.addMessage(new ChatMessage(raw, false));
            }
        }
    }

    private void updateStreamingBubble(String partialText) {
        if (streamingMsgIndex == -1) {
            chatAdapter.addMessage(new ChatMessage(partialText, false));
            streamingMsgIndex = chatAdapter.getItemCount() - 1;
        } else {
            chatAdapter.updateMessageAt(streamingMsgIndex, partialText);
        }
    }

    private int commitStreamedMessage(String content) {
        if (content.isEmpty()) { streamingMsgIndex = -1; return -1; }
        int committedIndex;
        if (streamingMsgIndex != -1) {
            chatAdapter.updateMessageAt(streamingMsgIndex, content);
            committedIndex = streamingMsgIndex;
        } else {
            chatAdapter.addMessage(new ChatMessage(content, false));
            committedIndex = chatAdapter.getItemCount() - 1;
        }
        streamingMsgIndex = -1;
        return committedIndex;
    }

    // ── Send message ──────────────────────────────────────────────────────────

    private void sendMessage() {
        String text = etMessage.getText() != null
                ? etMessage.getText().toString().trim() : "";
        if (text.isEmpty()) return;
        etMessage.setText("");
        sendMessageText(text);
    }

    private void sendMessageText(String text) {
        if (text.isEmpty()) return;

        if (isStreaming || pendingFirstMessage != null) {
            Snackbar.make(rvChat, R.string.ai_chat_wait_streaming, Snackbar.LENGTH_SHORT).show();
            return;
        }

        clearSuggestions();
        hideWelcomeState();
        chatAdapter.addMessage(new ChatMessage(text, true));

        if (sessionId == null) {
            chatAdapter.addMessage(new ChatMessage(true));
            streamingMsgIndex   = chatAdapter.getItemCount() - 1;
            pendingFirstMessage = text;
            createSession();
            return;
        }

        isStreaming = true;
        chatAdapter.addMessage(new ChatMessage(true));
        streamingMsgIndex = chatAdapter.getItemCount() - 1;
        try {
            JSONObject payload = new JSONObject();
            payload.put("message", text);
            payload.put("language", chatLanguage);
            boolean sent = webSocket != null && webSocket.send(payload.toString());
            if (!sent) {
                removeTypingIndicator();
                isStreaming = false;
                Snackbar.make(rvChat, R.string.error_network, Snackbar.LENGTH_SHORT).show();
            }
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

    // ── Suggestion chips ──────────────────────────────────────────────────────

    private void showSuggestions(JSONArray suggestions) {
        chipContainer.removeAllViews();
        if (suggestions.length() == 0) { chipScrollView.setVisibility(View.GONE); return; }

        float dp     = getResources().getDisplayMetrics().density;
        int marginPx = (int) (8 * dp);

        for (int i = 0; i < suggestions.length(); i++) {
            String option;
            try { option = suggestions.getString(i); } catch (Exception e) { continue; }

            Chip chip = new Chip(new ContextThemeWrapper(this,
                    com.google.android.material.R.style.Widget_MaterialComponents_Chip_Choice), null, 0);
            chip.setText(option);
            chip.setChipBackgroundColor(
                    ColorStateList.valueOf(getColor(R.color.colorBrandYellowPale)));
            chip.setChipStrokeColor(
                    ColorStateList.valueOf(getColor(R.color.colorPrimary)));
            chip.setChipStrokeWidth(dp);
            chip.setTextColor(getColor(R.color.colorTextPrimary));
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

    // ── Apply roadmap edit proposals ──────────────────────────────────────────

    private void applyProposals(int messageIndex, List<EditProposal> proposals, int index) {
        // Scope guard — only the Roadmap tab can apply roadmap mutations.
        // Server-side (FromRoadmapScopedSession) is authoritative; this keeps
        // stale tags from a non-roadmap tab from even issuing the request.
        if (index == 0 && !"roadmap".equals(currentContextType)) {
            Snackbar.make(rvChat, R.string.error_scope_not_roadmap,
                    Snackbar.LENGTH_LONG).show();
            return;
        }

        if (index >= proposals.size()) {
            chatAdapter.markProposalsApplied(messageIndex);
            Snackbar.make(rvChat, getString(R.string.proposal_applied),
                    Snackbar.LENGTH_SHORT).show();
            return;
        }

        EditProposal p      = proposals.get(index);
        String       bearer = TokenManager.getBearerToken(this);
        String       sid    = sessionId;   // current roadmap-tab session
        retrofit2.Call<Void> call = null;

        switch (p.getAction() != null ? p.getAction() : "") {
            case "edit_roadmap":
                call = ApiClient.getService().editRoadmapMeta(
                        bearer, sid, p.getRoadmapId(), p.getChanges());
                break;
            case "edit_node":
                call = ApiClient.getService().editNodeContent(
                        bearer, sid, p.getRoadmapId(), p.getNodeId(), p.getChanges());
                break;
            case "replace_node":
                call = ApiClient.getService().replaceRoadmapNode(
                        bearer, sid, p.getRoadmapId(), p.getNodeId(), p.getChanges());
                break;
        }

        if (call == null) {
            applyProposals(messageIndex, proposals, index + 1);
            return;
        }

        call.enqueue(new Callback<Void>() {
            @Override
            public void onResponse(retrofit2.Call<Void> c,
                                   retrofit2.Response<Void> response) {
                if (response.code() == 429) {
                    Snackbar.make(rvChat, R.string.error_replace_node_rate_limit,
                            Snackbar.LENGTH_LONG).show();
                } else {
                    applyProposals(messageIndex, proposals, index + 1);
                }
            }

            @Override
            public void onFailure(retrofit2.Call<Void> c, Throwable t) {
                Snackbar.make(rvChat, R.string.error_network,
                        Snackbar.LENGTH_SHORT).show();
            }
        });
    }

    // ── Roadmap switch / upskill API calls ───────────────────────────────────

    private void callSwitchApi(int messageIndex, RoadmapSwitchProposal p) {
        if (!"roadmap".equals(currentContextType)) {
            Snackbar.make(rvChat, R.string.error_scope_not_roadmap,
                    Snackbar.LENGTH_LONG).show();
            return;
        }
        com.google.gson.JsonObject body = new com.google.gson.JsonObject();
        body.addProperty("roadmap_id", p.getRoadmapId());
        body.addProperty("new_path", p.getNewPath());
        body.addProperty("career_goal", p.getCareerGoal());
        ApiClient.getService()
                .switchRoadmap(TokenManager.getBearerToken(this), sessionId, body)
                .enqueue(new Callback<Void>() {
                    @Override
                    public void onResponse(retrofit2.Call<Void> c,
                                           retrofit2.Response<Void> response) {
                        if (response.isSuccessful()) {
                            chatAdapter.markSwitchApplied(messageIndex);
                        } else {
                            Snackbar.make(rvChat, R.string.error_network,
                                    Snackbar.LENGTH_SHORT).show();
                        }
                    }
                    @Override
                    public void onFailure(retrofit2.Call<Void> c, Throwable t) {
                        Snackbar.make(rvChat, R.string.error_network,
                                Snackbar.LENGTH_SHORT).show();
                    }
                });
    }

    private void callUpskillApi(int messageIndex, RoadmapUpskillProposal p) {
        if (!"roadmap".equals(currentContextType)) {
            Snackbar.make(rvChat, R.string.error_scope_not_roadmap,
                    Snackbar.LENGTH_LONG).show();
            return;
        }
        com.google.gson.JsonObject body = new com.google.gson.JsonObject();
        body.addProperty("roadmap_id", p.getRoadmapId());
        ApiClient.getService()
                .upskillRoadmap(TokenManager.getBearerToken(this), sessionId, body)
                .enqueue(new Callback<Void>() {
                    @Override
                    public void onResponse(retrofit2.Call<Void> c,
                                           retrofit2.Response<Void> response) {
                        if (response.isSuccessful()) {
                            chatAdapter.markUpskillApplied(messageIndex);
                        } else {
                            Snackbar.make(rvChat, R.string.error_network,
                                    Snackbar.LENGTH_SHORT).show();
                        }
                    }
                    @Override
                    public void onFailure(retrofit2.Call<Void> c, Throwable t) {
                        Snackbar.make(rvChat, R.string.error_network,
                                Snackbar.LENGTH_SHORT).show();
                    }
                });
    }

    // ── Navigation ────────────────────────────────────────────────────────────

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            finish();
            overridePendingTransition(R.anim.slide_in_left, R.anim.slide_out_right);
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

}
