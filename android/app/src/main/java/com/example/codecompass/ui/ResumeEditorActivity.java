package com.example.codecompass.ui;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.PopupMenu;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import com.example.codecompass.R;
import com.example.codecompass.api.ApiClient;
import com.example.codecompass.api.TokenManager;
import com.example.codecompass.model.Education;
import com.example.codecompass.model.Experience;
import com.example.codecompass.model.PersonalInfo;
import com.example.codecompass.model.Project;
import com.example.codecompass.model.Resume;
import com.example.codecompass.model.ResumeCertification;
import com.example.codecompass.model.ResumeContent;
import com.example.codecompass.model.ResumeSkills;
import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.android.material.progressindicator.LinearProgressIndicator;
import com.google.android.material.tabs.TabLayout;
import com.google.gson.Gson;
import com.google.gson.JsonObject;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class ResumeEditorActivity extends AppCompatActivity {

    private int resumeId;
    private Resume currentResume;
    private ResumeContent content;

    private LinearProgressIndicator loadingBar;
    private TextView tvTitle;
    private TextView tvSaveStatus;
    private FrameLayout contentContainer;
    private TabLayout tabLayout;

    private int currentTab = 0;
    private boolean isDirty = false;
    private boolean isSaving = false;
    private boolean isHandling401 = false;

    private final Handler saveHandler = new Handler(Looper.getMainLooper());
    private final Runnable saveRunnable = () -> doSave(true);
    private static final long AUTO_SAVE_DELAY = 8000L;

    private final Gson gson = new Gson();

    // Tab indices
    private static final int TAB_INFO = 0;
    private static final int TAB_SUMMARY = 1;
    private static final int TAB_EXPERIENCE = 2;
    private static final int TAB_EDUCATION = 3;
    private static final int TAB_SKILLS = 4;
    private static final int TAB_PROJECTS = 5;
    private static final int TAB_CERTS = 6;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_resume_editor);

        resumeId = getIntent().getIntExtra(ResumeListActivity.EXTRA_RESUME_ID, -1);
        if (resumeId == -1) { finish(); return; }

        loadingBar = findViewById(R.id.loadingBar);
        tvTitle = findViewById(R.id.tvTitle);
        tvSaveStatus = findViewById(R.id.tvSaveStatus);
        contentContainer = findViewById(R.id.contentContainer);
        tabLayout = findViewById(R.id.tabLayout);
        ImageView btnMenu = findViewById(R.id.btnMenu);
        FloatingActionButton fabPreview = findViewById(R.id.fabPreview);

        findViewById(R.id.btnBack).setOnClickListener(v -> onBackPressed());

        // Setup tabs
        String[] tabNames = {
            getString(R.string.resume_tab_info),
            getString(R.string.resume_tab_summary),
            getString(R.string.resume_tab_experience),
            getString(R.string.resume_tab_education),
            getString(R.string.resume_tab_skills),
            getString(R.string.resume_tab_projects),
            getString(R.string.resume_tab_certs)
        };
        for (String name : tabNames) tabLayout.addTab(tabLayout.newTab().setText(name));

        tabLayout.addOnTabSelectedListener(new TabLayout.OnTabSelectedListener() {
            @Override public void onTabSelected(TabLayout.Tab tab) {
                collectCurrentTab();
                currentTab = tab.getPosition();
                showTab(currentTab);
            }
            @Override public void onTabUnselected(TabLayout.Tab tab) {}
            @Override public void onTabReselected(TabLayout.Tab tab) {}
        });

        // Menu
        btnMenu.setOnClickListener(v -> {
            PopupMenu popup = new PopupMenu(this, v);
            popup.getMenu().add(0, 1, 0, R.string.resume_change_template);
            popup.getMenu().add(0, 2, 1, R.string.resume_ats_score);
            popup.getMenu().add(0, 3, 2, R.string.resume_delete_title);
            popup.setOnMenuItemClickListener(item -> {
                if (item.getItemId() == 1) {
                    openTemplatePicker();
                    return true;
                } else if (item.getItemId() == 2) {
                    openAtsScoring();
                    return true;
                } else if (item.getItemId() == 3) {
                    confirmDelete();
                    return true;
                }
                return false;
            });
            popup.show();
        });

        // Preview FAB
        fabPreview.setOnClickListener(v -> {
            collectCurrentTab();
            Intent intent = new Intent(this, ResumePreviewActivity.class);
            intent.putExtra("content_json", gson.toJson(content));
            intent.putExtra("template_name", currentResume != null ? currentResume.getTemplateName() : "modern");
            intent.putExtra("primary_color", content.getStyling() != null ? content.getStyling().getPrimaryColor() : "#1A2F5E");
            intent.putExtra("resume_id", resumeId);
            startActivity(intent);
            overridePendingTransition(R.anim.slide_in_right, R.anim.slide_out_left);
        });

        loadResume();
    }

    private void loadResume() {
        loadingBar.setVisibility(View.VISIBLE);
        String token = TokenManager.getBearerToken(this);

        ApiClient.getService().getResume(token, resumeId).enqueue(new Callback<Resume>() {
            @Override
            public void onResponse(@NonNull Call<Resume> call, @NonNull Response<Resume> response) {
                loadingBar.setVisibility(View.GONE);
                if (response.code() == 401) { handle401(); return; }
                if (response.isSuccessful() && response.body() != null) {
                    currentResume = response.body();
                    content = currentResume.getContent();
                    if (content == null) content = new ResumeContent();
                    tvTitle.setText(currentResume.getTitle());
                    showTab(currentTab);
                }
            }

            @Override
            public void onFailure(@NonNull Call<Resume> call, @NonNull Throwable t) {
                loadingBar.setVisibility(View.GONE);
                Toast.makeText(ResumeEditorActivity.this, R.string.error_network, Toast.LENGTH_SHORT).show();
            }
        });
    }

    // ── Tab display ──────────────────────────────────────────────────────────

    private void showTab(int tab) {
        if (content == null) return;
        contentContainer.removeAllViews();

        switch (tab) {
            case TAB_INFO: showInfoTab(); break;
            case TAB_SUMMARY: showSummaryTab(); break;
            case TAB_EXPERIENCE: showExperienceTab(); break;
            case TAB_EDUCATION: showEducationTab(); break;
            case TAB_SKILLS: showSkillsTab(); break;
            case TAB_PROJECTS: showProjectsTab(); break;
            case TAB_CERTS: showCertsTab(); break;
        }
    }

    // ── INFO TAB ─────────────────────────────────────────────────────────────

    private void showInfoTab() {
        View view = LayoutInflater.from(this).inflate(R.layout.tab_resume_info, contentContainer, false);
        contentContainer.addView(view);
        PersonalInfo pi = content.getPersonalInfo();
        if (pi == null) { pi = new PersonalInfo(); content.setPersonalInfo(pi); }

        setFieldText(view, R.id.etName, pi.getName());
        setFieldText(view, R.id.etJobTitle, pi.getTitle());
        setFieldText(view, R.id.etEmail, pi.getEmail());
        setFieldText(view, R.id.etPhone, pi.getPhone());
        setFieldText(view, R.id.etLocation, pi.getLocation());
        setFieldText(view, R.id.etLinkedin, pi.getLinkedin());
        setFieldText(view, R.id.etGithub, pi.getGithub());
        setFieldText(view, R.id.etWebsite, pi.getWebsite());

        addAutoSaveWatcher(view, R.id.etName);
        addAutoSaveWatcher(view, R.id.etJobTitle);
        addAutoSaveWatcher(view, R.id.etEmail);
        addAutoSaveWatcher(view, R.id.etPhone);
        addAutoSaveWatcher(view, R.id.etLocation);
        addAutoSaveWatcher(view, R.id.etLinkedin);
        addAutoSaveWatcher(view, R.id.etGithub);
        addAutoSaveWatcher(view, R.id.etWebsite);
    }

    private void collectInfoTab() {
        View view = contentContainer.getChildAt(0);
        if (view == null) return;
        PersonalInfo pi = content.getPersonalInfo();
        if (pi == null) { pi = new PersonalInfo(); content.setPersonalInfo(pi); }

        pi.setName(getFieldText(view, R.id.etName));
        pi.setTitle(getFieldText(view, R.id.etJobTitle));
        pi.setEmail(getFieldText(view, R.id.etEmail));
        pi.setPhone(getFieldText(view, R.id.etPhone));
        pi.setLocation(getFieldText(view, R.id.etLocation));
        pi.setLinkedin(getFieldText(view, R.id.etLinkedin));
        pi.setGithub(getFieldText(view, R.id.etGithub));
        pi.setWebsite(getFieldText(view, R.id.etWebsite));
    }

    // ── SUMMARY TAB ──────────────────────────────────────────────────────────

    private void showSummaryTab() {
        View view = LayoutInflater.from(this).inflate(R.layout.tab_resume_summary, contentContainer, false);
        contentContainer.addView(view);

        EditText etSummary = view.findViewById(R.id.etSummary);
        etSummary.setText(content.getSummary());
        addAutoSaveWatcher(view, R.id.etSummary);

        view.findViewById(R.id.btnAiSummary).setOnClickListener(v -> {
            AiSummaryGeneratorBottomSheet sheet = AiSummaryGeneratorBottomSheet.newInstance(
                    resumeId,
                    content.getPersonalInfo() != null ? content.getPersonalInfo().getTitle() : "",
                    content.getSkills() != null ? content.getSkills().getTechnical() : new ArrayList<>());
            sheet.setOnSummarySelectedListener(summary -> {
                etSummary.setText(summary);
                markDirty();
            });
            sheet.show(getSupportFragmentManager(), "ai_summary");
        });
    }

    private void collectSummaryTab() {
        View view = contentContainer.getChildAt(0);
        if (view == null) return;
        content.setSummary(getFieldText(view, R.id.etSummary));
    }

    // ── EXPERIENCE TAB ───────────────────────────────────────────────────────

    private void showExperienceTab() {
        View view = LayoutInflater.from(this).inflate(R.layout.tab_resume_experience, contentContainer, false);
        contentContainer.addView(view);

        LinearLayout container = view.findViewById(R.id.containerExperience);
        TextView tvEmpty = view.findViewById(R.id.tvExperienceEmpty);
        List<Experience> exps = content.getExperience();
        if (exps == null) { exps = new ArrayList<>(); content.setExperience(exps); }

        for (Experience exp : exps) addExperienceItem(container, exp);
        tvEmpty.setVisibility(exps.isEmpty() ? View.VISIBLE : View.GONE);

        view.findViewById(R.id.btnAddExperience).setOnClickListener(v -> {
            Experience newExp = new Experience();
            content.getExperience().add(newExp);
            addExperienceItem(container, newExp);
            tvEmpty.setVisibility(View.GONE);
            markDirty();
        });
    }

    private void addExperienceItem(LinearLayout container, Experience exp) {
        View item = LayoutInflater.from(this).inflate(R.layout.item_experience_form, container, false);
        container.addView(item);

        TextView tvHeader = item.findViewById(R.id.tvExpHeader);
        setFieldText(item, R.id.etExpCompany, exp.getCompany());
        setFieldText(item, R.id.etExpTitle, exp.getTitle());
        setFieldText(item, R.id.etExpLocation, exp.getLocation());
        setFieldText(item, R.id.etExpStartDate, exp.getStartDate());
        setFieldText(item, R.id.etExpEndDate, exp.getEndDate());

        CheckBox cbCurrent = item.findViewById(R.id.cbExpCurrent);
        cbCurrent.setChecked(exp.isCurrent());
        View layoutEndDate = item.findViewById(R.id.layoutExpEndDate);
        layoutEndDate.setVisibility(exp.isCurrent() ? View.GONE : View.VISIBLE);
        cbCurrent.setOnCheckedChangeListener((btn, checked) -> {
            layoutEndDate.setVisibility(checked ? View.GONE : View.VISIBLE);
            markDirty();
        });

        // Update header on text change
        String headerText = (exp.getTitle().isEmpty() ? "" : exp.getTitle()) +
                (exp.getCompany().isEmpty() ? "" : " — " + exp.getCompany());
        tvHeader.setText(headerText.isEmpty() ? getString(R.string.resume_add_experience) : headerText);

        // Bullets
        LinearLayout bulletContainer = item.findViewById(R.id.containerBullets);
        if (exp.getBullets() != null) {
            for (String bullet : exp.getBullets()) addBulletView(bulletContainer, bullet);
        }

        EditText etNewBullet = item.findViewById(R.id.etNewBullet);
        item.findViewById(R.id.btnAddBullet).setOnClickListener(v -> {
            String text = etNewBullet.getText().toString().trim();
            if (!text.isEmpty()) {
                addBulletView(bulletContainer, text);
                etNewBullet.setText("");
                markDirty();
            }
        });

        // AI bullets
        item.findViewById(R.id.btnAiBullets).setOnClickListener(v -> {
            AiBulletGeneratorBottomSheet sheet = AiBulletGeneratorBottomSheet.newInstance(
                    resumeId, exp.getTitle());
            sheet.setOnBulletSelectedListener(bullet -> {
                addBulletView(bulletContainer, bullet);
                markDirty();
            });
            sheet.show(getSupportFragmentManager(), "ai_bullets");
        });

        // Delete
        item.findViewById(R.id.btnDeleteExp).setOnClickListener(v -> {
            container.removeView(item);
            content.getExperience().remove(exp);
            markDirty();
        });

        addAutoSaveWatcher(item, R.id.etExpCompany);
        addAutoSaveWatcher(item, R.id.etExpTitle);
        addAutoSaveWatcher(item, R.id.etExpLocation);
        addAutoSaveWatcher(item, R.id.etExpStartDate);
        addAutoSaveWatcher(item, R.id.etExpEndDate);

        item.setTag(exp);
    }

    private void addBulletView(LinearLayout container, String text) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(android.view.Gravity.CENTER_VERTICAL);

        TextView tv = new TextView(this);
        tv.setText(String.format(Locale.getDefault(), "• %s", text));
        tv.setTextSize(13);
        tv.setTextColor(getColor(R.color.colorTextPrimary));
        LinearLayout.LayoutParams tvParams = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1);
        tv.setLayoutParams(tvParams);
        row.addView(tv);

        ImageView btnRemove = new ImageView(this);
        btnRemove.setImageResource(R.drawable.ic_close);
        btnRemove.setColorFilter(getColor(R.color.colorDanger));
        int size = (int) (24 * getResources().getDisplayMetrics().density);
        btnRemove.setLayoutParams(new LinearLayout.LayoutParams(size, size));
        btnRemove.setOnClickListener(v -> {
            container.removeView(row);
            markDirty();
        });
        row.addView(btnRemove);

        row.setTag(text);
        container.addView(row);
    }

    private void collectExperienceTab() {
        View view = contentContainer.getChildAt(0);
        if (view == null) return;
        LinearLayout container = view.findViewById(R.id.containerExperience);
        List<Experience> exps = content.getExperience();

        for (int i = 0; i < container.getChildCount(); i++) {
            View item = container.getChildAt(i);
            if (i < exps.size()) {
                Experience exp = exps.get(i);
                exp.setCompany(getFieldText(item, R.id.etExpCompany));
                exp.setTitle(getFieldText(item, R.id.etExpTitle));
                exp.setLocation(getFieldText(item, R.id.etExpLocation));
                exp.setStartDate(getFieldText(item, R.id.etExpStartDate));
                exp.setEndDate(getFieldText(item, R.id.etExpEndDate));
                CheckBox cb = item.findViewById(R.id.cbExpCurrent);
                exp.setCurrent(cb.isChecked());

                // Collect bullets
                LinearLayout bulletContainer = item.findViewById(R.id.containerBullets);
                List<String> bullets = new ArrayList<>();
                for (int j = 0; j < bulletContainer.getChildCount(); j++) {
                    Object tag = bulletContainer.getChildAt(j).getTag();
                    if (tag instanceof String) bullets.add((String) tag);
                }
                exp.setBullets(bullets);
            }
        }
    }

    // ── EDUCATION TAB ────────────────────────────────────────────────────────

    private void showEducationTab() {
        View view = LayoutInflater.from(this).inflate(R.layout.tab_resume_education, contentContainer, false);
        contentContainer.addView(view);

        LinearLayout container = view.findViewById(R.id.containerEducation);
        TextView tvEmpty = view.findViewById(R.id.tvEducationEmpty);
        List<Education> edus = content.getEducation();
        if (edus == null) { edus = new ArrayList<>(); content.setEducation(edus); }

        for (Education edu : edus) addEducationItem(container, edu);
        tvEmpty.setVisibility(edus.isEmpty() ? View.VISIBLE : View.GONE);

        view.findViewById(R.id.btnAddEducation).setOnClickListener(v -> {
            Education newEdu = new Education();
            content.getEducation().add(newEdu);
            addEducationItem(container, newEdu);
            tvEmpty.setVisibility(View.GONE);
            markDirty();
        });
    }

    private void addEducationItem(LinearLayout container, Education edu) {
        View item = LayoutInflater.from(this).inflate(R.layout.item_education_form, container, false);
        container.addView(item);

        setFieldText(item, R.id.etEduSchool, edu.getSchool());
        setFieldText(item, R.id.etEduDegree, edu.getDegree());
        setFieldText(item, R.id.etEduField, edu.getField());
        setFieldText(item, R.id.etEduStartDate, edu.getStartDate());
        setFieldText(item, R.id.etEduEndDate, edu.getEndDate());
        setFieldText(item, R.id.etEduGpa, edu.getGpa());

        TextView tvHeader = item.findViewById(R.id.tvEduHeader);
        String header = edu.getSchool().isEmpty() ? getString(R.string.resume_add_education) : edu.getSchool();
        tvHeader.setText(header);

        item.findViewById(R.id.btnDeleteEdu).setOnClickListener(v -> {
            container.removeView(item);
            content.getEducation().remove(edu);
            markDirty();
        });

        addAutoSaveWatcher(item, R.id.etEduSchool);
        addAutoSaveWatcher(item, R.id.etEduDegree);
        addAutoSaveWatcher(item, R.id.etEduField);
        addAutoSaveWatcher(item, R.id.etEduStartDate);
        addAutoSaveWatcher(item, R.id.etEduEndDate);
        addAutoSaveWatcher(item, R.id.etEduGpa);
    }

    private void collectEducationTab() {
        View view = contentContainer.getChildAt(0);
        if (view == null) return;
        LinearLayout container = view.findViewById(R.id.containerEducation);
        List<Education> edus = content.getEducation();

        for (int i = 0; i < container.getChildCount(); i++) {
            View item = container.getChildAt(i);
            if (i < edus.size()) {
                Education edu = edus.get(i);
                edu.setSchool(getFieldText(item, R.id.etEduSchool));
                edu.setDegree(getFieldText(item, R.id.etEduDegree));
                edu.setField(getFieldText(item, R.id.etEduField));
                edu.setStartDate(getFieldText(item, R.id.etEduStartDate));
                edu.setEndDate(getFieldText(item, R.id.etEduEndDate));
                edu.setGpa(getFieldText(item, R.id.etEduGpa));
            }
        }
    }

    // ── SKILLS TAB ───────────────────────────────────────────────────────────

    private void showSkillsTab() {
        View view = LayoutInflater.from(this).inflate(R.layout.tab_resume_skills, contentContainer, false);
        contentContainer.addView(view);

        ResumeSkills skills = content.getSkills();
        if (skills == null) { skills = new ResumeSkills(); content.setSkills(skills); }

        populateChipGroup(view, R.id.chipGroupTechnical, skills.getTechnical());
        populateChipGroup(view, R.id.chipGroupTools, skills.getTools());
        populateChipGroup(view, R.id.chipGroupSoft, skills.getSoft());

        setupSkillAdd(view, R.id.etAddTechnical, R.id.btnAddTechnical, R.id.chipGroupTechnical);
        setupSkillAdd(view, R.id.etAddTools, R.id.btnAddTools, R.id.chipGroupTools);
        setupSkillAdd(view, R.id.etAddSoft, R.id.btnAddSoft, R.id.chipGroupSoft);
    }

    private void populateChipGroup(View parent, int chipGroupId, List<String> items) {
        ChipGroup group = parent.findViewById(chipGroupId);
        group.removeAllViews();
        if (items == null) return;
        for (String item : items) {
            Chip chip = new Chip(this);
            chip.setText(item);
            chip.setCloseIconVisible(true);
            chip.setOnCloseIconClickListener(v -> {
                group.removeView(chip);
                markDirty();
            });
            group.addView(chip);
        }
    }

    private void setupSkillAdd(View parent, int etId, int btnId, int chipGroupId) {
        EditText et = parent.findViewById(etId);
        ChipGroup group = parent.findViewById(chipGroupId);

        parent.findViewById(btnId).setOnClickListener(v -> {
            String text = et.getText().toString().trim();
            if (!text.isEmpty()) {
                Chip chip = new Chip(this);
                chip.setText(text);
                chip.setCloseIconVisible(true);
                chip.setOnCloseIconClickListener(cv -> {
                    group.removeView(chip);
                    markDirty();
                });
                group.addView(chip);
                et.setText("");
                markDirty();
            }
        });

        et.setOnEditorActionListener((v, actionId, event) -> {
            parent.findViewById(btnId).performClick();
            return true;
        });
    }

    private List<String> collectChipGroup(View parent, int chipGroupId) {
        ChipGroup group = parent.findViewById(chipGroupId);
        List<String> items = new ArrayList<>();
        for (int i = 0; i < group.getChildCount(); i++) {
            View child = group.getChildAt(i);
            if (child instanceof Chip) items.add(((Chip) child).getText().toString());
        }
        return items;
    }

    private void collectSkillsTab() {
        View view = contentContainer.getChildAt(0);
        if (view == null) return;
        ResumeSkills skills = content.getSkills();
        if (skills == null) { skills = new ResumeSkills(); content.setSkills(skills); }
        skills.setTechnical(collectChipGroup(view, R.id.chipGroupTechnical));
        skills.setTools(collectChipGroup(view, R.id.chipGroupTools));
        skills.setSoft(collectChipGroup(view, R.id.chipGroupSoft));
    }

    // ── PROJECTS TAB ─────────────────────────────────────────────────────────

    private void showProjectsTab() {
        View view = LayoutInflater.from(this).inflate(R.layout.tab_resume_projects, contentContainer, false);
        contentContainer.addView(view);

        LinearLayout container = view.findViewById(R.id.containerProjects);
        TextView tvEmpty = view.findViewById(R.id.tvProjectsEmpty);
        List<Project> projs = content.getProjects();
        if (projs == null) { projs = new ArrayList<>(); content.setProjects(projs); }

        for (Project proj : projs) addProjectItem(container, proj);
        tvEmpty.setVisibility(projs.isEmpty() ? View.VISIBLE : View.GONE);

        view.findViewById(R.id.btnAddProject).setOnClickListener(v -> {
            Project newProj = new Project();
            content.getProjects().add(newProj);
            addProjectItem(container, newProj);
            tvEmpty.setVisibility(View.GONE);
            markDirty();
        });
    }

    private void addProjectItem(LinearLayout container, Project proj) {
        View item = LayoutInflater.from(this).inflate(R.layout.item_project_form, container, false);
        container.addView(item);

        setFieldText(item, R.id.etProjName, proj.getName());
        setFieldText(item, R.id.etProjDescription, proj.getDescription());
        setFieldText(item, R.id.etProjTech, proj.getTech() != null ? String.join(", ", proj.getTech()) : "");
        setFieldText(item, R.id.etProjLink, proj.getLink());

        TextView tvHeader = item.findViewById(R.id.tvProjHeader);
        tvHeader.setText(proj.getName().isEmpty() ? getString(R.string.resume_add_project) : proj.getName());

        item.findViewById(R.id.btnDeleteProj).setOnClickListener(v -> {
            container.removeView(item);
            content.getProjects().remove(proj);
            markDirty();
        });

        addAutoSaveWatcher(item, R.id.etProjName);
        addAutoSaveWatcher(item, R.id.etProjDescription);
        addAutoSaveWatcher(item, R.id.etProjTech);
        addAutoSaveWatcher(item, R.id.etProjLink);
    }

    private void collectProjectsTab() {
        View view = contentContainer.getChildAt(0);
        if (view == null) return;
        LinearLayout container = view.findViewById(R.id.containerProjects);
        List<Project> projs = content.getProjects();

        for (int i = 0; i < container.getChildCount(); i++) {
            View item = container.getChildAt(i);
            if (i < projs.size()) {
                Project proj = projs.get(i);
                proj.setName(getFieldText(item, R.id.etProjName));
                proj.setDescription(getFieldText(item, R.id.etProjDescription));
                String techStr = getFieldText(item, R.id.etProjTech);
                proj.setTech(techStr.isEmpty() ? new ArrayList<>() :
                        new ArrayList<>(Arrays.asList(techStr.split("\\s*,\\s*"))));
                proj.setLink(getFieldText(item, R.id.etProjLink));
            }
        }
    }

    // ── CERTIFICATIONS TAB ───────────────────────────────────────────────────

    private void showCertsTab() {
        View view = LayoutInflater.from(this).inflate(R.layout.tab_resume_certifications, contentContainer, false);
        contentContainer.addView(view);

        LinearLayout container = view.findViewById(R.id.containerCertifications);
        TextView tvEmpty = view.findViewById(R.id.tvCertsEmpty);
        List<ResumeCertification> certs = content.getCertifications();
        if (certs == null) { certs = new ArrayList<>(); content.setCertifications(certs); }

        for (ResumeCertification cert : certs) addCertItem(container, cert);
        tvEmpty.setVisibility(certs.isEmpty() ? View.VISIBLE : View.GONE);

        view.findViewById(R.id.btnAddCertification).setOnClickListener(v -> {
            ResumeCertification newCert = new ResumeCertification();
            content.getCertifications().add(newCert);
            addCertItem(container, newCert);
            tvEmpty.setVisibility(View.GONE);
            markDirty();
        });
    }

    private void addCertItem(LinearLayout container, ResumeCertification cert) {
        View item = LayoutInflater.from(this).inflate(R.layout.item_resume_cert_form, container, false);
        container.addView(item);

        setFieldText(item, R.id.etCertName, cert.getName());
        setFieldText(item, R.id.etCertIssuer, cert.getIssuer());
        setFieldText(item, R.id.etCertDate, cert.getDate());

        TextView tvHeader = item.findViewById(R.id.tvCertHeader);
        tvHeader.setText(cert.getName().isEmpty() ? getString(R.string.resume_add_certification) : cert.getName());

        item.findViewById(R.id.btnDeleteCert).setOnClickListener(v -> {
            container.removeView(item);
            content.getCertifications().remove(cert);
            markDirty();
        });

        addAutoSaveWatcher(item, R.id.etCertName);
        addAutoSaveWatcher(item, R.id.etCertIssuer);
        addAutoSaveWatcher(item, R.id.etCertDate);
    }

    private void collectCertsTab() {
        View view = contentContainer.getChildAt(0);
        if (view == null) return;
        LinearLayout container = view.findViewById(R.id.containerCertifications);
        List<ResumeCertification> certs = content.getCertifications();

        for (int i = 0; i < container.getChildCount(); i++) {
            View item = container.getChildAt(i);
            if (i < certs.size()) {
                ResumeCertification cert = certs.get(i);
                cert.setName(getFieldText(item, R.id.etCertName));
                cert.setIssuer(getFieldText(item, R.id.etCertIssuer));
                cert.setDate(getFieldText(item, R.id.etCertDate));
            }
        }
    }

    // ── Collect current tab data ─────────────────────────────────────────────

    private void collectCurrentTab() {
        if (content == null) return;
        switch (currentTab) {
            case TAB_INFO: collectInfoTab(); break;
            case TAB_SUMMARY: collectSummaryTab(); break;
            case TAB_EXPERIENCE: collectExperienceTab(); break;
            case TAB_EDUCATION: collectEducationTab(); break;
            case TAB_SKILLS: collectSkillsTab(); break;
            case TAB_PROJECTS: collectProjectsTab(); break;
            case TAB_CERTS: collectCertsTab(); break;
        }
    }

    // ── Auto-save ────────────────────────────────────────────────────────────

    private void markDirty() {
        isDirty = true;
        tvSaveStatus.setText(R.string.resume_save_unsaved);
        tvSaveStatus.setTextColor(getColor(R.color.colorTextSecondary));
        scheduleSave();
    }

    private void scheduleSave() {
        saveHandler.removeCallbacks(saveRunnable);
        saveHandler.postDelayed(saveRunnable, AUTO_SAVE_DELAY);
    }

    private void doSave(boolean isAutoSave) {
        if (!isDirty || isSaving || content == null) return;
        collectCurrentTab();
        isSaving = true;

        tvSaveStatus.setText(R.string.resume_save_saving);
        tvSaveStatus.setTextColor(getColor(R.color.colorPrimary));

        String token = TokenManager.getBearerToken(this);
        JsonObject body = new JsonObject();
        body.addProperty("title", currentResume != null ? currentResume.getTitle() : "");
        body.addProperty("templateName", currentResume != null ? currentResume.getTemplateName() : "modern");
        body.add("content", gson.toJsonTree(content));

        ApiClient.getService().updateResume(token, resumeId, body).enqueue(new Callback<Resume>() {
            @Override
            public void onResponse(@NonNull Call<Resume> call, @NonNull Response<Resume> response) {
                isSaving = false;
                if (response.code() == 401) { handle401(); return; }
                if (response.isSuccessful()) {
                    isDirty = false;
                    tvSaveStatus.setText(R.string.resume_save_saved);
                    tvSaveStatus.setTextColor(getColor(R.color.colorOnlineGreen));
                } else {
                    tvSaveStatus.setText(R.string.resume_save_error);
                    tvSaveStatus.setTextColor(getColor(R.color.colorDanger));
                }
            }

            @Override
            public void onFailure(@NonNull Call<Resume> call, @NonNull Throwable t) {
                isSaving = false;
                tvSaveStatus.setText(R.string.resume_save_error);
                tvSaveStatus.setTextColor(getColor(R.color.colorDanger));
            }
        });
    }

    // ── Menu actions ─────────────────────────────────────────────────────────

    private void openTemplatePicker() {
        TemplatePickerBottomSheet sheet = TemplatePickerBottomSheet.newInstance(
                currentResume != null ? currentResume.getTemplateName() : "modern",
                content.getStyling() != null ? content.getStyling().getPrimaryColor() : "#1A2F5E");
        sheet.setOnTemplateSelectedListener((templateName, color) -> {
            if (currentResume != null) currentResume.setTemplateName(templateName);
            if (content.getStyling() == null) content.setStyling(new com.example.codecompass.model.ResumeStyling());
            content.getStyling().setPrimaryColor(color);
            markDirty();
        });
        sheet.show(getSupportFragmentManager(), "template_picker");
    }

    private void openAtsScoring() {
        collectCurrentTab();
        AtsScoreBottomSheet sheet = AtsScoreBottomSheet.newInstance(resumeId, gson.toJson(content));
        sheet.show(getSupportFragmentManager(), "ats_score");
    }

    private void confirmDelete() {
        new AlertDialog.Builder(this)
                .setTitle(R.string.resume_delete_title)
                .setMessage(R.string.resume_delete_confirm)
                .setPositiveButton(android.R.string.ok, (d, w) -> {
                    String token = TokenManager.getBearerToken(this);
                    ApiClient.getService().deleteResume(token, resumeId).enqueue(new Callback<Void>() {
                        @Override
                        public void onResponse(@NonNull Call<Void> call, @NonNull Response<Void> response) {
                            finish();
                        }
                        @Override
                        public void onFailure(@NonNull Call<Void> call, @NonNull Throwable t) {
                            finish();
                        }
                    });
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    // ── Lifecycle ────────────────────────────────────────────────────────────

    @Override
    protected void onPause() {
        super.onPause();
        saveHandler.removeCallbacks(saveRunnable);
        if (isDirty) doSave(false);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        saveHandler.removeCallbacksAndMessages(null);
    }

    @Override
    public void finish() {
        super.finish();
        overridePendingTransition(R.anim.slide_in_left, R.anim.slide_out_right);
    }

    // ── Utility ──────────────────────────────────────────────────────────────

    private void setFieldText(View parent, int id, String text) {
        EditText et = parent.findViewById(id);
        if (et != null && text != null) et.setText(text);
    }

    private String getFieldText(View parent, int id) {
        EditText et = parent.findViewById(id);
        return et != null ? et.getText().toString().trim() : "";
    }

    private void addAutoSaveWatcher(View parent, int editTextId) {
        EditText et = parent.findViewById(editTextId);
        if (et == null) return;
        et.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {}
            @Override public void afterTextChanged(Editable s) { markDirty(); }
        });
    }

    private void handle401() {
        if (isHandling401) return;
        isHandling401 = true;
        TokenManager.clear(this);
        Intent intent = new Intent(this, LoginActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
    }
}
