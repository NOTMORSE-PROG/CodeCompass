package com.example.codecompass.ui;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import com.example.codecompass.R;
import com.example.codecompass.api.ApiClient;
import com.example.codecompass.api.TokenManager;
import com.example.codecompass.model.CreateResumeRequest;
import com.example.codecompass.model.Resume;
import com.example.codecompass.model.ResumeListResponse;
import com.example.codecompass.ui.adapter.ResumeAdapter;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.android.material.progressindicator.LinearProgressIndicator;

import java.util.ArrayList;
import java.util.List;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class ResumeListActivity extends AppCompatActivity implements ResumeAdapter.OnResumeActionListener {

    public static final String EXTRA_RESUME_ID = "resume_id";

    private LinearProgressIndicator loadingBar;
    private SwipeRefreshLayout swipeRefresh;
    private RecyclerView rvResumes;
    private LinearLayout layoutEmpty;
    private ResumeAdapter adapter;

    private final List<Resume> resumes = new ArrayList<>();
    private boolean isHandling401 = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_resume_list);

        loadingBar = findViewById(R.id.loadingBar);
        swipeRefresh = findViewById(R.id.swipeRefresh);
        rvResumes = findViewById(R.id.rvResumes);
        layoutEmpty = findViewById(R.id.layoutEmpty);
        FloatingActionButton fabNewResume = findViewById(R.id.fabNewResume);

        findViewById(R.id.btnBack).setOnClickListener(v -> finish());

        adapter = new ResumeAdapter(this);
        rvResumes.setLayoutManager(new LinearLayoutManager(this));
        rvResumes.setAdapter(adapter);

        swipeRefresh.setColorSchemeColors(getColor(R.color.colorPrimary));
        swipeRefresh.setOnRefreshListener(this::loadResumes);

        fabNewResume.setOnClickListener(v -> showCreateDialog());

        loadResumes();
    }

    @Override
    protected void onResume() {
        super.onResume();
        loadResumes();
    }

    private void loadResumes() {
        loadingBar.setVisibility(View.VISIBLE);
        String token = TokenManager.getBearerToken(this);

        ApiClient.getService().getResumes(token).enqueue(new Callback<ResumeListResponse>() {
            @Override
            public void onResponse(@NonNull Call<ResumeListResponse> call,
                                   @NonNull Response<ResumeListResponse> response) {
                loadingBar.setVisibility(View.GONE);
                swipeRefresh.setRefreshing(false);
                if (response.code() == 401) { handle401(); return; }
                if (response.isSuccessful() && response.body() != null) {
                    resumes.clear();
                    resumes.addAll(response.body().getResults());
                    adapter.setData(resumes);
                }
                updateEmptyState();
            }

            @Override
            public void onFailure(@NonNull Call<ResumeListResponse> call, @NonNull Throwable t) {
                loadingBar.setVisibility(View.GONE);
                swipeRefresh.setRefreshing(false);
                updateEmptyState();
            }
        });
    }

    private void showCreateDialog() {
        ResumeCreateBottomSheet sheet = ResumeCreateBottomSheet.newInstance();
        sheet.setOnCreateResumeListener((title, templateId) -> createResume(title, templateId));
        sheet.show(getSupportFragmentManager(), "resume_create");
    }

    private void createResume(String title, String templateId) {
        loadingBar.setVisibility(View.VISIBLE);
        String token = TokenManager.getBearerToken(this);

        ApiClient.getService().createResume(token, new CreateResumeRequest(title, templateId))
                .enqueue(new Callback<Resume>() {
                    @Override
                    public void onResponse(@NonNull Call<Resume> call,
                                           @NonNull Response<Resume> response) {
                        loadingBar.setVisibility(View.GONE);
                        if (response.code() == 401) { handle401(); return; }
                        if (response.isSuccessful() && response.body() != null) {
                            Intent intent = new Intent(ResumeListActivity.this, ResumeEditorActivity.class);
                            intent.putExtra(EXTRA_RESUME_ID, response.body().getId());
                            startActivity(intent);
                            overridePendingTransition(R.anim.slide_in_right, R.anim.slide_out_left);
                        }
                    }

                    @Override
                    public void onFailure(@NonNull Call<Resume> call, @NonNull Throwable t) {
                        loadingBar.setVisibility(View.GONE);
                        Toast.makeText(ResumeListActivity.this, R.string.error_network, Toast.LENGTH_SHORT).show();
                    }
                });
    }

    private void deleteResume(Resume resume) {
        loadingBar.setVisibility(View.VISIBLE);
        String token = TokenManager.getBearerToken(this);

        ApiClient.getService().deleteResume(token, resume.getId()).enqueue(new Callback<Void>() {
            @Override
            public void onResponse(@NonNull Call<Void> call, @NonNull Response<Void> response) {
                loadingBar.setVisibility(View.GONE);
                if (response.code() == 401) { handle401(); return; }
                if (response.isSuccessful()) {
                    resumes.remove(resume);
                    adapter.setData(resumes);
                    updateEmptyState();
                    Toast.makeText(ResumeListActivity.this, R.string.resume_deleted, Toast.LENGTH_SHORT).show();
                }
            }

            @Override
            public void onFailure(@NonNull Call<Void> call, @NonNull Throwable t) {
                loadingBar.setVisibility(View.GONE);
            }
        });
    }

    private void updateEmptyState() {
        boolean empty = resumes.isEmpty();
        layoutEmpty.setVisibility(empty ? View.VISIBLE : View.GONE);
        swipeRefresh.setVisibility(empty ? View.GONE : View.VISIBLE);
    }

    @Override
    public void onResumeTap(Resume resume) {
        Intent intent = new Intent(this, ResumeEditorActivity.class);
        intent.putExtra(EXTRA_RESUME_ID, resume.getId());
        startActivity(intent);
        overridePendingTransition(R.anim.slide_in_right, R.anim.slide_out_left);
    }

    @Override
    public void onResumeDelete(Resume resume) {
        new AlertDialog.Builder(this)
                .setTitle(R.string.resume_delete_title)
                .setMessage(R.string.resume_delete_confirm)
                .setPositiveButton(android.R.string.ok, (d, w) -> deleteResume(resume))
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    private void handle401() {
        if (isHandling401) return;
        isHandling401 = true;
        TokenManager.clear(this);
        Intent intent = new Intent(this, LoginActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
    }

    @Override
    public void finish() {
        super.finish();
        overridePendingTransition(R.anim.slide_in_left, R.anim.slide_out_right);
    }
}
