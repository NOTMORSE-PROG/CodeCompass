package com.example.codecompass.ui;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.View;
import android.webkit.JavascriptInterface;
import android.webkit.WebChromeClient;
import android.webkit.WebView;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.activity.OnBackPressedCallback;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;

import com.example.codecompass.R;
import com.example.codecompass.api.ApiClient;
import com.example.codecompass.api.TokenManager;

public class VideoLearningActivity extends AppCompatActivity {

    // ── Intent extras / result keys ───────────────────────────────────────────
    public static final String EXTRA_NODE_ID       = "vl_node_id";
    public static final String EXTRA_RESOURCE_ID   = "vl_resource_id";
    public static final String EXTRA_ROADMAP_ID    = "vl_roadmap_id";
    public static final String EXTRA_VIDEO_ID      = "vl_video_id";
    public static final String EXTRA_VIDEO_TITLE   = "vl_video_title";
    public static final String EXTRA_VIDEO_CHANNEL = "vl_video_channel";
    public static final String RESULT_RESOURCE_ID  = "vl_result_resource_id";
    public static final String EXTRA_WATCH_UNLOCKED = "vl_watch_unlocked";
    public static final String EXTRA_RESOURCE_COMPLETED = "vl_resource_completed";

    private static final String PREFS_WATCH      = "cc_watch_progress";
    private static final int    UNLOCK_THRESHOLD = 300; // 5 min in cumulative seconds

    // ── Views ─────────────────────────────────────────────────────────────────
    private WebView      webView;
    private ProgressBar  progressWatch;
    private TextView     tvWatchPercent;
    private TextView     tvVideoTitle;
    private TextView     tvVideoChannel;
    private LinearLayout layoutQuizLocked;
    private LinearLayout layoutWatchProgress;
    private Button       btnTakeQuiz;
    private FrameLayout  fullscreenContainer;

    // ── Fullscreen state ──────────────────────────────────────────────────────
    private View                               fullscreenView;
    private WebChromeClient.CustomViewCallback fullscreenCallback;
    private OnBackPressedCallback              fullscreenBackCallback;

    // ── State ─────────────────────────────────────────────────────────────────
    private int     nodeId;
    private int     resourceId;
    private int     roadmapId;
    private String  videoTitleForQuiz;
    private boolean quizUnlocked = false;

    private ActivityResultLauncher<Intent> quizLauncher;

    // ─────────────────────────────────────────────────────────────────────────

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_video_learning);

        nodeId           = getIntent().getIntExtra(EXTRA_NODE_ID, -1);
        resourceId       = getIntent().getIntExtra(EXTRA_RESOURCE_ID, -1);
        roadmapId        = getIntent().getIntExtra(EXTRA_ROADMAP_ID, -1);
        String videoId   = getIntent().getStringExtra(EXTRA_VIDEO_ID);
        videoTitleForQuiz = getIntent().getStringExtra(EXTRA_VIDEO_TITLE);
        String videoChannel = getIntent().getStringExtra(EXTRA_VIDEO_CHANNEL);

        // Register quiz launcher — must be called before onStart
        quizLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                result -> {
                    if (result.getResultCode() == RESULT_OK) {
                        Intent data = result.getData();
                        int passedId = data != null
                                ? data.getIntExtra(QuizActivity.RESULT_RESOURCE_ID, resourceId)
                                : resourceId;
                        Intent out = new Intent();
                        out.putExtra(RESULT_RESOURCE_ID, passedId);
                        setResult(RESULT_OK, out);
                        finish();
                    }
                    // RESULT_CANCELED: user left quiz without passing — stay on video screen
                });

        // Toolbar
        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setTitle(videoTitleForQuiz != null ? videoTitleForQuiz : "");
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        }
        toolbar.setNavigationOnClickListener(v -> finish());

        // Bind views
        webView             = findViewById(R.id.webViewVideo);
        progressWatch       = findViewById(R.id.progressWatch);
        tvWatchPercent      = findViewById(R.id.tvWatchPercent);
        tvVideoTitle        = findViewById(R.id.tvVideoTitle);
        tvVideoChannel      = findViewById(R.id.tvVideoChannel);
        layoutQuizLocked    = findViewById(R.id.layoutQuizLocked);
        layoutWatchProgress = findViewById(R.id.layoutWatchProgress);
        btnTakeQuiz         = findViewById(R.id.btnTakeQuiz);
        fullscreenContainer = findViewById(R.id.fullscreenContainer);

        // 16:9 aspect ratio for WebView
        int videoHeight = (int) (getResources().getDisplayMetrics().widthPixels * 9.0 / 16.0);
        webView.getLayoutParams().height = videoHeight;

        // Video metadata
        if (videoTitleForQuiz != null) tvVideoTitle.setText(videoTitleForQuiz);
        if (videoChannel != null && !videoChannel.isEmpty()) {
            tvVideoChannel.setText(getString(R.string.video_channel_format, videoChannel));
            tvVideoChannel.setVisibility(View.VISIBLE);
        }

        btnTakeQuiz.setOnClickListener(v -> launchQuiz());

        // Cumulative watch time always resets to 0 on each open (matches web behavior).
        // Unlock is persisted server-side — check the Intent extra instead.
        int savedMaxReached = getSavedMaxReachedSeconds();
        int savedPosition   = getSavedWatchSeconds();

        updateWatchProgressUi(0);

        boolean alreadyUnlocked  = getIntent().getBooleanExtra(EXTRA_WATCH_UNLOCKED,    false);
        boolean alreadyCompleted = getIntent().getBooleanExtra(EXTRA_RESOURCE_COMPLETED, false);
        if (alreadyCompleted) {
            // Re-watch mode: video is already completed, hide all quiz/progress UI
            // and prevent onFiveMinutesReached() from re-firing the unlock flow.
            quizUnlocked = true;
            layoutWatchProgress.setVisibility(View.GONE);
            layoutQuizLocked.setVisibility(View.GONE);
            btnTakeQuiz.setVisibility(View.GONE);
        } else if (alreadyUnlocked) {
            quizUnlocked = true;
            layoutQuizLocked.setVisibility(View.GONE);
            btnTakeQuiz.setVisibility(View.VISIBLE);
        }

        setupWebView(videoId, savedPosition, 0, savedMaxReached);

        fullscreenBackCallback = new OnBackPressedCallback(false) {
            @Override
            public void handleOnBackPressed() {
                exitFullscreen();
            }
        };
        getOnBackPressedDispatcher().addCallback(this, fullscreenBackCallback);
    }

    private void exitFullscreen() {
        if (fullscreenView == null) return;
        fullscreenContainer.removeView(fullscreenView);
        fullscreenContainer.setVisibility(View.GONE);
        fullscreenView = null;
        if (fullscreenCallback != null) {
            fullscreenCallback.onCustomViewHidden();
            fullscreenCallback = null;
        }
        getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_VISIBLE);
        fullscreenBackCallback.setEnabled(false);
    }

    @Override
    public void finish() {
        super.finish();
        overridePendingTransition(R.anim.slide_in_left, R.anim.slide_out_right);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (webView != null) {
            webView.stopLoading();
            webView.destroy();
        }
    }

    // ── WebView ───────────────────────────────────────────────────────────────

    @SuppressLint("SetJavaScriptEnabled")
    private void setupWebView(String videoId, int startSeconds,
                              int savedCumulative, int savedMaxReached) {
        webView.getSettings().setJavaScriptEnabled(true);
        webView.getSettings().setDomStorageEnabled(true);
        webView.setWebChromeClient(new WebChromeClient() {
            @Override
            public void onShowCustomView(View view, CustomViewCallback callback) {
                if (fullscreenView != null) {
                    callback.onCustomViewHidden();
                    return;
                }
                fullscreenView = view;
                fullscreenCallback = callback;
                fullscreenContainer.addView(view);
                fullscreenContainer.setVisibility(View.VISIBLE);
                fullscreenBackCallback.setEnabled(true);
                getWindow().getDecorView().setSystemUiVisibility(
                        View.SYSTEM_UI_FLAG_FULLSCREEN
                        | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                        | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY);
            }

            @Override
            public void onHideCustomView() {
                exitFullscreen();
            }
        });
        webView.addJavascriptInterface(new YouTubeProgressInterface(), "Android");

        String html = buildPlayerHtml(
                videoId != null ? videoId : "",
                startSeconds, savedCumulative, savedMaxReached);
        webView.loadDataWithBaseURL("https://youtube.com", html, "text/html", "UTF-8", null);
    }

    /**
     * Builds the YouTube IFrame HTML with:
     *  - Cumulative wall-clock watch time (only counts while playing, capped at 2 s per tick)
     *  - Seek prevention: snap back if user tries to skip > 3 s ahead
     *  - Reports: Android.onProgress(cumulative, currentTime, duration, maxReached)
     */
    private String buildPlayerHtml(String vid, int startSeconds,
                                   int savedCumulative, int savedMaxReached) {
        return "<!DOCTYPE html><html><head>"
            + "<meta name='viewport' content='width=device-width,initial-scale=1'>"
            + "<style>*{margin:0;padding:0;}body{background:#000;overflow:hidden;}"
            + "#player{width:100vw;height:100vh;}</style>"
            + "</head><body><div id='player'></div><script>"
            + "var tag=document.createElement('script');"
            + "tag.src='https://www.youtube.com/iframe_api';"
            + "document.head.appendChild(tag);"
            + "var player,progressTimer;"
            + "var cumulativeWatchSeconds=" + savedCumulative + ";"
            + "var maxReachedSeconds=" + savedMaxReached + ";"
            + "var lastTickMs=0;"
            + "function onYouTubeIframeAPIReady(){"
            + "  player=new YT.Player('player',{"
            + "    videoId:'" + vid + "',"
            + "    playerVars:{rel:0,modestbranding:1,playsinline:1,"
            + "      origin:'https://youtube.com',start:" + startSeconds + "},"
            + "    events:{onStateChange:onStateChange}"
            + "  });"
            + "}"
            + "function onStateChange(e){"
            + "  if(e.data===YT.PlayerState.PLAYING){"
            + "    progressTimer=setInterval(checkProgress,1000);"
            + "  } else {"
            + "    clearInterval(progressTimer);"
            + "    lastTickMs=0;"
            + "  }"
            + "}"
            + "function checkProgress(){"
            + "  if(!player||typeof player.getPlayerState==='undefined')return;"
            + "  var state=player.getPlayerState();"
            + "  var now=Date.now();"
            + "  var currentTime=player.getCurrentTime();"
            + "  var duration=player.getDuration();"
            + "  if(state===YT.PlayerState.PLAYING){"
            + "    if(lastTickMs>0){"
            + "      var elapsed=Math.min((now-lastTickMs)/1000.0,2.0);"
            + "      cumulativeWatchSeconds+=elapsed;"
            + "    }"
            + "    lastTickMs=now;"
            + "    if(currentTime>maxReachedSeconds+3){"
            + "      player.seekTo(maxReachedSeconds,true);"
            + "    } else {"
            + "      if(currentTime>maxReachedSeconds)maxReachedSeconds=currentTime;"
            + "    }"
            + "  } else {"
            + "    lastTickMs=0;"
            + "  }"
            + "  Android.onProgress("
            + "    Math.floor(cumulativeWatchSeconds),"
            + "    Math.floor(currentTime),"
            + "    Math.floor(duration),"
            + "    Math.floor(maxReachedSeconds)"
            + "  );"
            + "}"
            + "</script></body></html>";
    }

    // ── Quiz launch ───────────────────────────────────────────────────────────

    private void launchQuiz() {
        Intent intent = new Intent(this, QuizActivity.class);
        intent.putExtra(QuizActivity.EXTRA_ROADMAP_ID, roadmapId);
        intent.putExtra(QuizActivity.EXTRA_NODE_ID, nodeId);
        intent.putExtra(QuizActivity.EXTRA_RESOURCE_ID, resourceId);
        intent.putExtra(QuizActivity.EXTRA_VIDEO_TITLE, videoTitleForQuiz);
        quizLauncher.launch(intent);
    }

    // ── Watch progress UI ─────────────────────────────────────────────────────

    private void updateWatchProgressUi(int cumulativeSeconds) {
        int capped = Math.min(cumulativeSeconds, UNLOCK_THRESHOLD);
        progressWatch.setProgress((capped * 100) / UNLOCK_THRESHOLD);
        int mins = cumulativeSeconds / 60;
        int secs = cumulativeSeconds % 60;
        tvWatchPercent.setText(String.format(java.util.Locale.getDefault(), "%d:%02d / 5:00", mins, secs));
    }

    private void onFiveMinutesReached() {
        quizUnlocked = true;
        layoutQuizLocked.setVisibility(View.GONE);
        btnTakeQuiz.setVisibility(View.VISIBLE);
        callUnlockApi();
    }

    private void callUnlockApi() {
        String token = TokenManager.getAccessToken(this);
        if (token == null) return;
        ApiClient.getService().unlockVideoWatch("Bearer " + token, roadmapId, nodeId, resourceId)
                .enqueue(new retrofit2.Callback<Void>() {
                    @Override
                    public void onResponse(retrofit2.Call<Void> call,
                                           retrofit2.Response<Void> response) {}
                    @Override
                    public void onFailure(retrofit2.Call<Void> call, Throwable t) {}
                });
    }

    // ── SharedPreferences ─────────────────────────────────────────────────────

    private SharedPreferences prefs() {
        return getSharedPreferences(PREFS_WATCH, Context.MODE_PRIVATE);
    }

    private int getSavedWatchSeconds()      { return prefs().getInt("sec_"     + resourceId, 0); }
    private int getSavedMaxReachedSeconds() { return prefs().getInt("max_sec_" + resourceId, 0); }

    private void saveWatchState(int currentSeconds, int maxReachedSeconds) {
        prefs().edit()
                .putInt("sec_"     + resourceId, currentSeconds)
                .putInt("max_sec_" + resourceId, maxReachedSeconds)
                .apply();
    }

    // ── JavaScript interface ──────────────────────────────────────────────────

    private class YouTubeProgressInterface {
        /**
         * Called from JS every second while playing.
         *
         * @param cumulativeSeconds total seconds actively watched (wall-clock, not position)
         * @param currentSeconds    current playback position
         * @param totalSeconds      video duration
         * @param maxReachedSeconds furthest playback position reached (seek-prevention high-water mark)
         */
        @JavascriptInterface
        public void onProgress(int cumulativeSeconds, int currentSeconds,
                               int totalSeconds, int maxReachedSeconds) {
            saveWatchState(currentSeconds, maxReachedSeconds);
            runOnUiThread(() -> {
                updateWatchProgressUi(cumulativeSeconds);
                if (!quizUnlocked && cumulativeSeconds >= UNLOCK_THRESHOLD) {
                    onFiveMinutesReached();
                }
            });
        }
    }
}
