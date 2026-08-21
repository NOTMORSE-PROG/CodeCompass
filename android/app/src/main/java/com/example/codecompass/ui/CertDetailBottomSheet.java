package com.example.codecompass.ui;

import android.content.Intent;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.example.codecompass.R;
import com.example.codecompass.api.ApiClient;
import com.example.codecompass.api.TokenManager;
import com.example.codecompass.model.Certification;
import com.example.codecompass.model.TrackCertRequest;
import com.example.codecompass.model.UpdateCertStatusRequest;
import com.example.codecompass.model.UserCertification;
import com.google.android.material.bottomsheet.BottomSheetDialogFragment;
import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;
import com.google.gson.Gson;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class CertDetailBottomSheet extends BottomSheetDialogFragment {

    public interface OnTrackChangeListener {
        void onTracked(UserCertification uc);
        void onStatusUpdated(UserCertification uc);
        void onUntracked(int certId);
    }

    private static final String ARG_CERT   = "cert_json";
    private static final String ARG_TRACKING = "tracking_json";

    // ── How-to guides (same content as web frontend) ──────────────────────────
    private static final Map<String, String[]> HOW_TO_GUIDES = new HashMap<>();
    static {
        HOW_TO_GUIDES.put("freecodecamp", new String[]{
            "Go to freecodecamp.org — create a free account to save progress.",
            "Navigate to the certification curriculum (e.g., Responsive Web Design, JavaScript).",
            "Complete all required projects and challenges in order.",
            "Submit your projects — they are auto-verified.",
            "Once all projects pass, claim your verified certificate from your profile.",
        });
        HOW_TO_GUIDES.put("google", new String[]{
            "Visit Google Cloud Skills Boost or Google Career Certificates on Coursera.",
            "Sign in with your Google account — no new account needed.",
            "Enroll in the free learning path or audit the course for free.",
            "Complete all modules, quizzes, and hands-on labs.",
            "Pass the final assessment to earn your certificate.",
        });
        HOW_TO_GUIDES.put("harvard", new String[]{
            "Visit cs50.harvard.edu — the course is completely free and open to everyone.",
            "Create a free edX account or sign up on the CS50 site.",
            "Watch lecture videos and complete the weekly problem sets.",
            "Submit your problem sets through the CS50 submission system (GitHub required).",
            "Complete the final project to earn the free CS50 certificate.",
        });
        HOW_TO_GUIDES.put("ibm", new String[]{
            "Visit IBM Skills Network (cognitiveclass.ai) or IBM courses on Coursera.",
            "Create a free IBM Skills Network account.",
            "Enroll in the course — most are free to audit.",
            "Complete all videos, labs, and quizzes.",
            "Pass the final exam to earn your digital badge via Credly.",
        });
        HOW_TO_GUIDES.put("cisco", new String[]{
            "Visit Cisco Networking Academy at netacad.com.",
            "Create a free Cisco NetAcad account.",
            "Enroll in a free course (e.g., Intro to Networks, Cybersecurity Essentials).",
            "Complete all modules and chapter quizzes at your own pace.",
            "Pass the final course assessment to earn your completion certificate.",
        });
        HOW_TO_GUIDES.put("microsoft", new String[]{
            "Visit Microsoft Learn at learn.microsoft.com — completely free.",
            "Sign in with any Microsoft account.",
            "Browse learning paths for your target role (e.g., Azure Developer).",
            "Complete modules and earn XP points on Microsoft Learn.",
            "For official certs (AZ-900 etc.), the study materials are free; exams have a fee.",
        });
        HOW_TO_GUIDES.put("aws", new String[]{
            "Visit AWS Skill Builder at skillbuilder.aws — create a free account.",
            "Browse free digital training courses (hundreds are free).",
            "Complete the free training content including videos and labs.",
            "Use AWS Free Tier to practice with real AWS services while studying.",
            "Note: AWS certification exams require a paid exam fee.",
        });
        HOW_TO_GUIDES.put("comptia", new String[]{
            "CompTIA certifications require a paid proctored exam.",
            "Use free study resources: Professor Messer's free CompTIA courses on YouTube.",
            "Download the free exam objectives PDF from CompTIA's website.",
            "Practice with free CompTIA CertMaster Practice trial or online practice tests.",
            "Schedule your exam at a Pearson VUE or Certiport testing center.",
        });
        HOW_TO_GUIDES.put("mongodb", new String[]{
            "Visit MongoDB University at learn.mongodb.com — create a free account.",
            "Browse free courses (MongoDB for developers, DBA path, etc.).",
            "Complete the course videos, quizzes, and hands-on labs.",
            "Pass the final assessment to earn a free MongoDB completion badge.",
            "For MongoDB Associate certifications, a paid exam is required.",
        });
        HOW_TO_GUIDES.put("kaggle", new String[]{
            "Visit kaggle.com and create a free Kaggle account.",
            "Go to kaggle.com/learn — all courses are completely free.",
            "Complete the short, practical courses (Python, ML, Deep Learning, etc.).",
            "Each course has exercises you run directly in Kaggle Notebooks.",
            "Finish all exercises to earn a Kaggle Certificate of Completion.",
        });
        HOW_TO_GUIDES.put("hackerrank", new String[]{
            "Visit hackerrank.com and create a free account.",
            "Go to the Skills Certification section.",
            "Take the free online skill assessment test (timed, multiple choice + coding).",
            "No prior study required — the test measures your existing skill level.",
            "Pass the assessment to earn a verified HackerRank certificate.",
        });
        HOW_TO_GUIDES.put("github", new String[]{
            "Visit skills.github.com — you need a free GitHub account.",
            "Sign in with your GitHub account — all courses use GitHub repositories.",
            "Start any course — it automatically creates a repository in your account.",
            "Follow the step-by-step instructions in the repo issues/discussions.",
            "Complete all steps to get your completion badge on your GitHub profile.",
        });
        HOW_TO_GUIDES.put("fortinet", new String[]{
            "Visit training.fortinet.com — create a free NSE Training Institute account.",
            "Enroll in NSE 1, NSE 2, and NSE 3 — all completely free.",
            "Complete the online self-paced modules (videos + quizzes).",
            "NSE 1 covers info security fundamentals (~2 hours).",
            "Pass each level's exam to earn your free NSE certificate.",
        });
        HOW_TO_GUIDES.put("linux_foundation", new String[]{
            "Visit training.linuxfoundation.org — create a free account.",
            "Browse free courses (Introduction to Linux, Open Source, etc.).",
            "Complete the free course on edX by auditing for free.",
            "Finish all modules and pass the final assessment.",
            "Note: LFCS/CKA certifications require paid exams.",
        });
        HOW_TO_GUIDES.put("tesda", new String[]{
            "Visit tesda.gov.ph or a TESDA-accredited training center near you.",
            "Create a free TESDA online account at portal.tesda.gov.ph.",
            "Enroll in a free online course (TVET programs are government-funded).",
            "Complete all course modules and practical sessions.",
            "Pass the competency assessment to earn your National Certificate (NC I or NC II).",
        });
        HOW_TO_GUIDES.put("hubspot", new String[]{
            "Visit academy.hubspot.com — create a free HubSpot Academy account.",
            "Browse the free certifications (Marketing, Sales, SEO, etc.).",
            "Complete the free course lessons and videos.",
            "Take the free certification exam (multiple choice, open book style).",
            "Pass the exam to earn your HubSpot certification (valid 1 year).",
        });
        HOW_TO_GUIDES.put("postman", new String[]{
            "Visit learning.postman.com — create a free Postman account.",
            "Enroll in the free Postman API Fundamentals Student Expert program.",
            "Complete the workspace exercises and submit via a Postman collection.",
            "Fork the provided collection and follow the guided challenges.",
            "Submit your completed collection to receive your free digital badge.",
        });
        HOW_TO_GUIDES.put("salesforce", new String[]{
            "Visit trailhead.salesforce.com — create a free Trailhead account.",
            "Complete free Trails and Modules at your own pace.",
            "Earn free Trailhead Badges and Superbadges by completing challenges.",
            "Superbadges are scenario-based and verify real skills.",
            "Note: Official Salesforce certifications require a paid exam.",
        });
        HOW_TO_GUIDES.put("scrum", new String[]{
            "Visit scrumstudyguide.com or certiprof.com for free Scrum certifications.",
            "Create a free account on either platform.",
            "Download the free Scrum Body of Knowledge (SBOK) study guide.",
            "Study the Scrum framework: roles, events, artifacts.",
            "Pass the free online exam with 70%+ to receive your free certificate.",
        });
    }

    private static final String[] STATUS_VALUES  = {"interested", "studying", "passed", "expired"};
    private static final String[] STATUS_LABELS  = {"Interested", "Currently Studying", "Earned / Passed", "Expired"};

    private Certification cert;
    private UserCertification userCert;
    private OnTrackChangeListener listener;

    public static CertDetailBottomSheet newInstance(Certification cert, @Nullable UserCertification uc) {
        CertDetailBottomSheet f = new CertDetailBottomSheet();
        Bundle args = new Bundle();
        Gson gson = new Gson();
        args.putString(ARG_CERT, gson.toJson(cert));
        if (uc != null) args.putString(ARG_TRACKING, gson.toJson(uc));
        f.setArguments(args);
        return f;
    }

    public void setOnTrackChangeListener(OnTrackChangeListener l) {
        this.listener = l;
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Gson gson = new Gson();
        if (getArguments() != null) {
            cert = gson.fromJson(getArguments().getString(ARG_CERT), Certification.class);
            String ucJson = getArguments().getString(ARG_TRACKING);
            if (ucJson != null) userCert = gson.fromJson(ucJson, UserCertification.class);
        }
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.bottom_sheet_cert_detail, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        if (cert == null) { dismiss(); return; }

        // ── Provider header ────────────────────────────────────────────────────
        TextView tvInitial       = view.findViewById(R.id.tvDetailProviderInitial);
        TextView tvName          = view.findViewById(R.id.tvDetailName);
        TextView tvAbbr          = view.findViewById(R.id.tvDetailAbbreviation);
        TextView tvTrack         = view.findViewById(R.id.tvDetailTrackBadge);
        TextView tvLevel         = view.findViewById(R.id.tvDetailLevelBadge);
        TextView tvFree          = view.findViewById(R.id.tvDetailFreeBadge);

        String colorHex = getProviderColor(cert.getProvider());
        GradientDrawable circle = new GradientDrawable();
        circle.setShape(GradientDrawable.OVAL);
        circle.setColor(Color.parseColor(colorHex));
        tvInitial.setBackground(circle);
        String initial = cert.getAbbreviation() != null && !cert.getAbbreviation().isEmpty()
                ? cert.getAbbreviation().substring(0, 1).toUpperCase(java.util.Locale.getDefault())
                : cert.getName().substring(0, 1).toUpperCase(java.util.Locale.getDefault());
        tvInitial.setText(initial);

        tvName.setText(cert.getName());

        if (cert.getAbbreviation() != null && !cert.getAbbreviation().isEmpty()) {
            tvAbbr.setText(cert.getAbbreviation());
            tvAbbr.setVisibility(View.VISIBLE);
        }

        tvTrack.setText(cert.getTrackDisplay() != null ? cert.getTrackDisplay() : cert.getTrack());
        tvLevel.setText(cert.getLevelDisplay() != null ? cert.getLevelDisplay() : cert.getLevel());
        tvFree.setVisibility(cert.isFree() ? View.VISIBLE : View.GONE);

        // ── Quick stats ────────────────────────────────────────────────────────
        TextView tvHours = view.findViewById(R.id.tvDetailStudyHours);
        TextView tvCost  = view.findViewById(R.id.tvDetailCost);
        tvHours.setText(cert.getEstimatedStudyHours() != null && cert.getEstimatedStudyHours() > 0
                ? getString(R.string.cert_study_hours_format, cert.getEstimatedStudyHours()) : getString(R.string.cert_study_varies));
        if (cert.isFree()) {
            tvCost.setText(R.string.cert_free_label);
        } else if (cert.getEstimatedCostPhp() != null) {
            tvCost.setText(String.format(Locale.getDefault(), "₱%,d", cert.getEstimatedCostPhp()));
        } else {
            tvCost.setText(R.string.cert_paid_label);
        }

        // ── Description ────────────────────────────────────────────────────────
        view.<TextView>findViewById(R.id.tvDetailDescription).setText(cert.getDescription());

        // ── How-to guide ───────────────────────────────────────────────────────
        LinearLayout containerHowTo = view.findViewById(R.id.containerHowTo);
        String[] steps = HOW_TO_GUIDES.getOrDefault(cert.getProvider(),
                new String[]{"Visit the provider's official website.", "Sign up for a free account.",
                        "Enroll in the course or certification program.",
                        "Complete all required modules and assessments.",
                        "Claim your certificate upon successful completion."});
        for (int i = 0; i < steps.length; i++) {
            View stepView = LayoutInflater.from(requireContext())
                    .inflate(R.layout.item_how_to_step, containerHowTo, false);
            TextView tvNum  = stepView.findViewById(R.id.tvStepNumber);
            TextView tvText = stepView.findViewById(R.id.tvStepText);
            GradientDrawable numCircle = new GradientDrawable();
            numCircle.setShape(GradientDrawable.OVAL);
            numCircle.setColor(Color.parseColor(colorHex));
            tvNum.setBackground(numCircle);
            tvNum.setText(String.valueOf(i + 1));
            tvText.setText(steps[i]);
            containerHowTo.addView(stepView);
        }

        // ── Optional paid upgrade ──────────────────────────────────────────────
        LinearLayout layoutUpgrade = view.findViewById(R.id.layoutPaidUpgrade);
        TextView tvUpgrade         = view.findViewById(R.id.tvPaidUpgrade);
        if (cert.getOptionalPaidUpgrade() != null && !cert.getOptionalPaidUpgrade().isEmpty()) {
            tvUpgrade.setText(cert.getOptionalPaidUpgrade());
            layoutUpgrade.setVisibility(View.VISIBLE);
        }

        // ── Skills ─────────────────────────────────────────────────────────────
        TextView tvSkillsLabel = view.findViewById(R.id.tvSkillsLabel);
        ChipGroup chipSkills   = view.findViewById(R.id.chipGroupSkills);
        if (cert.getRelevantSkills() != null && !cert.getRelevantSkills().isEmpty()) {
            tvSkillsLabel.setVisibility(View.VISIBLE);
            for (String skill : cert.getRelevantSkills()) {
                Chip chip = new Chip(requireContext());
                chip.setText(skill);
                chip.setClickable(false);
                chip.setCheckable(false);
                chipSkills.addView(chip);
            }
        }

        // ── Career paths ───────────────────────────────────────────────────────
        TextView tvCareerLabel = view.findViewById(R.id.tvCareerLabel);
        ChipGroup chipCareers  = view.findViewById(R.id.chipGroupCareers);
        if (cert.getCareerPaths() != null && !cert.getCareerPaths().isEmpty()) {
            tvCareerLabel.setVisibility(View.VISIBLE);
            for (String path : cert.getCareerPaths()) {
                Chip chip = new Chip(requireContext());
                chip.setText(path);
                chip.setClickable(false);
                chip.setCheckable(false);
                chipCareers.addView(chip);
            }
        }

        // ── External links ─────────────────────────────────────────────────────
        Button btnOfficial   = view.findViewById(R.id.btnVisitOfficial);
        Button btnStudyGuide = view.findViewById(R.id.btnStudyGuide);

        if (cert.getExamUrl() != null && !cert.getExamUrl().isEmpty()) {
            btnOfficial.setVisibility(View.VISIBLE);
            btnOfficial.setOnClickListener(v -> openUrl(cert.getExamUrl()));
        }
        if (cert.getStudyGuideUrl() != null && !cert.getStudyGuideUrl().isEmpty()) {
            btnStudyGuide.setVisibility(View.VISIBLE);
            btnStudyGuide.setOnClickListener(v -> openUrl(cert.getStudyGuideUrl()));
        }

        // ── Footer: tracking actions ───────────────────────────────────────────
        bindFooter(view);
    }

    private void bindFooter(View view) {
        Button btnTrackCert          = view.findViewById(R.id.btnTrackCert);
        LinearLayout layoutTracked   = view.findViewById(R.id.layoutTrackedActions);
        Spinner spinnerStatus        = view.findViewById(R.id.spinnerStatus);
        Button btnRemove             = view.findViewById(R.id.btnRemoveTracking);
        TextView tvEarned            = view.findViewById(R.id.tvEarnedState);

        if (userCert == null) {
            // Not tracked
            btnTrackCert.setVisibility(View.VISIBLE);
            btnTrackCert.setOnClickListener(v -> doTrack(view));
        } else if ("passed".equals(userCert.getStatus())) {
            tvEarned.setVisibility(View.VISIBLE);
        } else {
            layoutTracked.setVisibility(View.VISIBLE);
            ArrayAdapter<String> adapter = new ArrayAdapter<>(
                    requireContext(), android.R.layout.simple_spinner_item, STATUS_LABELS) {
                @Override
                public android.view.View getView(int position, android.view.View convertView,
                                                 android.view.ViewGroup parent) {
                    android.view.View v = super.getView(position, convertView, parent);
                    if (v instanceof android.widget.TextView)
                        ((android.widget.TextView) v).setTextColor(0xFF0A0A0A);
                    return v;
                }
                @Override
                public android.view.View getDropDownView(int position, android.view.View convertView,
                                                         android.view.ViewGroup parent) {
                    android.view.View v = super.getDropDownView(position, convertView, parent);
                    if (v instanceof android.widget.TextView)
                        ((android.widget.TextView) v).setTextColor(0xFF0A0A0A);
                    return v;
                }
            };
            adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
            spinnerStatus.setAdapter(adapter);
            int currentIdx = indexOfStatus(userCert.getStatus());
            spinnerStatus.setSelection(currentIdx >= 0 ? currentIdx : 0);

            btnRemove.setOnClickListener(v -> doRemove(view));

            view.findViewById(R.id.layoutTrackedActions).setTag(spinnerStatus);
            // Update status on button-less confirmation — user picks spinner + taps "Update" label
            // Use the remove button area; add an Update button dynamically or use spinner onItemSelected
            spinnerStatus.post(() -> spinnerStatus.setOnItemSelectedListener(
                    new android.widget.AdapterView.OnItemSelectedListener() {
                        boolean firstCall = true;
                        @Override
                        public void onItemSelected(android.widget.AdapterView<?> parent, View v2, int pos, long id) {
                            if (firstCall) { firstCall = false; return; }
                            String newStatus = STATUS_VALUES[pos];
                            doUpdateStatus(view, newStatus);
                        }
                        @Override
                        public void onNothingSelected(android.widget.AdapterView<?> parent) {}
                    }));
        }
    }

    private void doTrack(View root) {
        String token = TokenManager.getBearerToken(requireContext());
        ApiClient.getService()
                .trackCertification(token, new TrackCertRequest(cert.getId(), "interested"))
                .enqueue(new Callback<UserCertification>() {
                    @Override
                    public void onResponse(@NonNull Call<UserCertification> call,
                                           @NonNull Response<UserCertification> response) {
                        if (!isAdded()) return;
                        if (response.isSuccessful() && response.body() != null) {
                            userCert = response.body();
                            if (listener != null) listener.onTracked(userCert);
                            rebindFooter(root);
                        } else {
                            Toast.makeText(requireContext(), "Failed to track. Try again.", Toast.LENGTH_SHORT).show();
                        }
                    }
                    @Override
                    public void onFailure(@NonNull Call<UserCertification> call, @NonNull Throwable t) {
                        if (isAdded()) Toast.makeText(requireContext(), "Network error.", Toast.LENGTH_SHORT).show();
                    }
                });
    }

    private void doUpdateStatus(View root, String newStatus) {
        if (userCert == null) return;
        String token = TokenManager.getBearerToken(requireContext());
        ApiClient.getService()
                .updateCertTracking(token, userCert.getId(), new UpdateCertStatusRequest(newStatus))
                .enqueue(new Callback<UserCertification>() {
                    @Override
                    public void onResponse(@NonNull Call<UserCertification> call,
                                           @NonNull Response<UserCertification> response) {
                        if (!isAdded()) return;
                        if (response.isSuccessful() && response.body() != null) {
                            userCert = response.body();
                            if (listener != null) listener.onStatusUpdated(userCert);
                            if ("passed".equals(userCert.getStatus())) rebindFooter(root);
                        }
                    }
                    @Override
                    public void onFailure(@NonNull Call<UserCertification> call, @NonNull Throwable t) {}
                });
    }

    private void doRemove(View root) {
        if (userCert == null) return;
        String token = TokenManager.getBearerToken(requireContext());
        int certId = cert.getId();
        ApiClient.getService()
                .untrackCertification(token, userCert.getId())
                .enqueue(new Callback<Void>() {
                    @Override
                    public void onResponse(@NonNull Call<Void> call, @NonNull Response<Void> response) {
                        if (!isAdded()) return;
                        if (response.isSuccessful()) {
                            userCert = null;
                            if (listener != null) listener.onUntracked(certId);
                            rebindFooter(root);
                        }
                    }
                    @Override
                    public void onFailure(@NonNull Call<Void> call, @NonNull Throwable t) {}
                });
    }

    private void rebindFooter(View root) {
        Button btnTrackCert        = root.findViewById(R.id.btnTrackCert);
        LinearLayout layoutTracked = root.findViewById(R.id.layoutTrackedActions);
        TextView tvEarned          = root.findViewById(R.id.tvEarnedState);

        btnTrackCert.setVisibility(View.GONE);
        layoutTracked.setVisibility(View.GONE);
        tvEarned.setVisibility(View.GONE);
        bindFooter(root);
    }

    private void openUrl(String url) {
        try {
            startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url)));
        } catch (Exception e) {
            Toast.makeText(requireContext(), "Could not open link.", Toast.LENGTH_SHORT).show();
        }
    }

    private int indexOfStatus(String status) {
        for (int i = 0; i < STATUS_VALUES.length; i++) {
            if (STATUS_VALUES[i].equals(status)) return i;
        }
        return 0;
    }

    private String getProviderColor(String provider) {
        Map<String, String> colors = new HashMap<>();
        colors.put("tesda",           "#0066CC");
        colors.put("google",          "#4285F4");
        colors.put("aws",             "#FF9900");
        colors.put("comptia",         "#C8202F");
        colors.put("microsoft",       "#00A4EF");
        colors.put("cisco",           "#1BA0D7");
        colors.put("meta",            "#0081FB");
        colors.put("oracle",          "#F80000");
        colors.put("freecodecamp",    "#0A0A23");
        colors.put("ibm",             "#054ADA");
        colors.put("mongodb",         "#00ED64");
        colors.put("github",          "#24292F");
        colors.put("kaggle",          "#20BEFF");
        colors.put("harvard",         "#A51C30");
        colors.put("hubspot",         "#FF7A59");
        colors.put("salesforce",      "#00A1E0");
        colors.put("postman",         "#FF6C37");
        colors.put("scrum",           "#009FDA");
        colors.put("linux_foundation","#003366");
        colors.put("fortinet",        "#EE3124");
        colors.put("hackerrank",      "#2EC866");
        return colors.getOrDefault(provider, "#6B7280");
    }
}
