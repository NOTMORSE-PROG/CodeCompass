package com.example.codecompass.ui;

import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;
import com.google.android.material.textfield.TextInputLayout;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.example.codecompass.R;
import com.google.android.material.bottomsheet.BottomSheetDialogFragment;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.textfield.TextInputEditText;

public class ResumeCreateBottomSheet extends BottomSheetDialogFragment {

    public interface OnCreateResumeListener {
        void onCreateResume(String title, String templateId);
    }

    private static final String[] TEMPLATE_IDS = {
            "modern", "classic", "minimal", "executive", "sidebar-blue"
    };
    private static final String[] TEMPLATE_NAMES = {
            "Modern", "Classic", "Minimal", "Executive", "Blueprint"
    };
    private static final String[] TEMPLATE_COLORS = {
            "#1A2F5E", "#374151", "#065F46", "#3730A3", "#2563EB"
    };

    private String selectedTemplateId = "modern";
    private OnCreateResumeListener listener;
    private LinearLayout layoutTemplateCards;

    public static ResumeCreateBottomSheet newInstance() {
        return new ResumeCreateBottomSheet();
    }

    public void setOnCreateResumeListener(OnCreateResumeListener listener) {
        this.listener = listener;
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.bottom_sheet_resume_create, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        TextInputLayout tilTitle = view.findViewById(R.id.tilResumeTitle);
        TextInputEditText etTitle = view.findViewById(R.id.etResumeTitle);
        layoutTemplateCards = view.findViewById(R.id.layoutTemplateCards);
        MaterialButton btnCreate = view.findViewById(R.id.btnCreate);

        buildTemplateCards();

        etTitle.addTextChangedListener(new android.text.TextWatcher() {
            public void beforeTextChanged(CharSequence s, int st, int c, int a) {}
            public void onTextChanged(CharSequence s, int st, int b, int c) {
                if (s.length() > 0) tilTitle.setError(null);
            }
            public void afterTextChanged(android.text.Editable s) {}
        });

        btnCreate.setOnClickListener(v -> {
            String title = etTitle.getText() != null ? etTitle.getText().toString().trim() : "";
            if (title.isEmpty()) {
                tilTitle.setError("Please enter a resume title");
                etTitle.requestFocus();
                return;
            }
            tilTitle.setError(null);
            if (listener != null) {
                listener.onCreateResume(title, selectedTemplateId);
            }
            dismiss();
        });
    }

    private void buildTemplateCards() {
        layoutTemplateCards.removeAllViews();
        int marginEnd = dp(10);

        for (int i = 0; i < TEMPLATE_IDS.length; i++) {
            String id = TEMPLATE_IDS[i];
            String name = TEMPLATE_NAMES[i];
            String color = TEMPLATE_COLORS[i];
            boolean selected = id.equals(selectedTemplateId);

            View card = buildSingleCard(id, name, color, selected);
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT);
            if (i < TEMPLATE_IDS.length - 1) params.setMarginEnd(marginEnd);
            card.setLayoutParams(params);
            layoutTemplateCards.addView(card);
        }
    }

    private View buildSingleCard(String templateId, String templateName,
                                  String colorHex, boolean selected) {
        int cardW = dp(108);
        int previewH = dp(140);
        int parsedColor = Color.parseColor(colorHex);

        // Outer wrapper: preview + name + selection indicator
        LinearLayout wrapper = new LinearLayout(requireContext());
        wrapper.setOrientation(LinearLayout.VERTICAL);
        wrapper.setGravity(Gravity.CENTER_HORIZONTAL);
        wrapper.setClipToPadding(false);

        // ── Card border/shadow container ──
        FrameLayout cardFrame = new FrameLayout(requireContext());
        cardFrame.setLayoutParams(new LinearLayout.LayoutParams(cardW, previewH));

        GradientDrawable cardBg = new GradientDrawable();
        cardBg.setColor(Color.WHITE);
        cardBg.setCornerRadius(dp(8));
        if (selected) {
            cardBg.setStroke(dp(2), parsedColor);
        } else {
            cardBg.setStroke(dp(1), Color.parseColor("#E5E7EB"));
        }
        cardFrame.setBackground(cardBg);
        cardFrame.setElevation(selected ? dp(3) : dp(1));

        // ── Template preview interior ──
        FrameLayout preview = new FrameLayout(requireContext());
        preview.setLayoutParams(new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT));

        switch (templateId) {
            case "modern":
                buildModernPreview(preview, parsedColor, cardW, previewH);
                break;
            case "classic":
                buildClassicPreview(preview, parsedColor, cardW, previewH);
                break;
            case "minimal":
                buildMinimalPreview(preview, parsedColor, cardW, previewH);
                break;
            case "executive":
                buildExecutivePreview(preview, parsedColor, cardW, previewH);
                break;
            case "sidebar-blue":
                buildBlueprintPreview(preview, parsedColor, cardW, previewH);
                break;
        }

        cardFrame.addView(preview);

        // ── Selected checkmark badge ──
        if (selected) {
            TextView badge = new TextView(requireContext());
            badge.setText("✓");
            badge.setTextSize(10);
            badge.setTextColor(Color.WHITE);
            badge.setGravity(Gravity.CENTER);
            badge.setTypeface(null, Typeface.BOLD);
            int badgeSize = dp(20);
            FrameLayout.LayoutParams badgeParams = new FrameLayout.LayoutParams(badgeSize, badgeSize);
            badgeParams.gravity = Gravity.TOP | Gravity.END;
            badgeParams.topMargin = dp(4);
            badgeParams.rightMargin = dp(4);
            GradientDrawable badgeBg = new GradientDrawable();
            badgeBg.setShape(GradientDrawable.OVAL);
            badgeBg.setColor(parsedColor);
            badge.setBackground(badgeBg);
            badge.setLayoutParams(badgeParams);
            cardFrame.addView(badge);
        }

        wrapper.addView(cardFrame);

        // ── Template name ──
        TextView tvName = new TextView(requireContext());
        tvName.setText(templateName);
        tvName.setTextSize(11);
        tvName.setGravity(Gravity.CENTER);
        if (selected) {
            tvName.setTextColor(parsedColor);
            tvName.setTypeface(null, Typeface.BOLD);
        } else {
            tvName.setTextColor(Color.parseColor("#6B7280"));
        }
        LinearLayout.LayoutParams nameParams = new LinearLayout.LayoutParams(
                cardW, LinearLayout.LayoutParams.WRAP_CONTENT);
        nameParams.topMargin = dp(6);
        tvName.setLayoutParams(nameParams);
        wrapper.addView(tvName);

        // Click to select
        wrapper.setOnClickListener(v -> {
            selectedTemplateId = templateId;
            buildTemplateCards();
        });
        wrapper.setClickable(true);
        wrapper.setFocusable(true);

        return wrapper;
    }

    // ── Modern: left color sidebar + right content lines ──
    private void buildModernPreview(FrameLayout parent, int color, int w, int h) {
        int sidebarW = dp(28);

        View sidebar = new View(requireContext());
        FrameLayout.LayoutParams sidebarLp = new FrameLayout.LayoutParams(sidebarW, h);
        sidebarLp.gravity = Gravity.START;
        sidebar.setLayoutParams(sidebarLp);
        sidebar.setBackgroundColor(color);
        parent.addView(sidebar);

        LinearLayout content = new LinearLayout(requireContext());
        content.setOrientation(LinearLayout.VERTICAL);
        content.setBackgroundColor(Color.WHITE);
        FrameLayout.LayoutParams contentLp = new FrameLayout.LayoutParams(w - sidebarW, h);
        contentLp.gravity = Gravity.END;
        content.setLayoutParams(contentLp);
        content.setPadding(dp(6), dp(8), dp(6), dp(6));

        addPreviewLine(content, dp(40), dp(4), color, 1.0f);
        addPreviewLine(content, dp(28), dp(3), Color.parseColor("#9CA3AF"), 0.7f);
        addPreviewSpace(content, dp(6));
        for (int i = 0; i < 5; i++) {
            addPreviewLine(content, i % 3 == 2 ? dp(32) : dp(50), dp(2), Color.parseColor("#D1D5DB"), 1.0f);
            addPreviewSpace(content, dp(3));
        }
        addPreviewSpace(content, dp(5));
        addPreviewLine(content, dp(35), dp(3), color, 0.9f);
        addPreviewSpace(content, dp(4));
        for (int i = 0; i < 4; i++) {
            addPreviewLine(content, i % 2 == 1 ? dp(40) : dp(50), dp(2), Color.parseColor("#D1D5DB"), 1.0f);
            addPreviewSpace(content, dp(3));
        }
        parent.addView(content);
    }

    // ── Classic: full-width colored header + lines below ──
    private void buildClassicPreview(FrameLayout parent, int color, int w, int h) {
        int headerH = dp(36);

        View header = new View(requireContext());
        FrameLayout.LayoutParams headerLp = new FrameLayout.LayoutParams(w, headerH);
        headerLp.gravity = Gravity.TOP;
        header.setLayoutParams(headerLp);
        header.setBackgroundColor(color);
        parent.addView(header);

        LinearLayout nameArea = new LinearLayout(requireContext());
        nameArea.setOrientation(LinearLayout.VERTICAL);
        nameArea.setGravity(Gravity.CENTER_HORIZONTAL);
        FrameLayout.LayoutParams naLp = new FrameLayout.LayoutParams(w, headerH);
        naLp.gravity = Gravity.TOP;
        nameArea.setLayoutParams(naLp);
        nameArea.setPadding(dp(6), dp(8), dp(6), dp(4));
        addPreviewLine(nameArea, dp(48), dp(4), Color.WHITE, 1.0f);
        addPreviewSpace(nameArea, dp(3));
        addPreviewLine(nameArea, dp(34), dp(2), Color.argb(180, 255, 255, 255), 1.0f);
        parent.addView(nameArea);

        LinearLayout body = new LinearLayout(requireContext());
        body.setOrientation(LinearLayout.VERTICAL);
        body.setBackgroundColor(Color.WHITE);
        FrameLayout.LayoutParams bodyLp = new FrameLayout.LayoutParams(w, h - headerH);
        bodyLp.gravity = Gravity.BOTTOM;
        body.setLayoutParams(bodyLp);
        body.setPadding(dp(8), dp(8), dp(8), dp(6));

        for (int i = 0; i < 3; i++) {
            addPreviewLine(body, dp(36), dp(3), color, 0.9f);
            addPreviewSpace(body, dp(3));
            addPreviewLine(body, dp(52), dp(2), Color.parseColor("#D1D5DB"), 1.0f);
            addPreviewSpace(body, dp(2));
            addPreviewLine(body, dp(40), dp(2), Color.parseColor("#D1D5DB"), 1.0f);
            addPreviewSpace(body, dp(5));
        }
        parent.addView(body);
    }

    // ── Minimal: white bg, thin color top bar, clean lines ──
    private void buildMinimalPreview(FrameLayout parent, int color, int w, int h) {
        parent.setBackgroundColor(Color.WHITE);

        View topBar = new View(requireContext());
        FrameLayout.LayoutParams barLp = new FrameLayout.LayoutParams(w, dp(3));
        barLp.gravity = Gravity.TOP;
        topBar.setLayoutParams(barLp);
        topBar.setBackgroundColor(color);
        parent.addView(topBar);

        LinearLayout content = new LinearLayout(requireContext());
        content.setOrientation(LinearLayout.VERTICAL);
        FrameLayout.LayoutParams contentLp = new FrameLayout.LayoutParams(w, h - dp(3));
        contentLp.gravity = Gravity.BOTTOM;
        content.setLayoutParams(contentLp);
        content.setPadding(dp(10), dp(10), dp(10), dp(8));

        addPreviewLine(content, dp(52), dp(5), Color.parseColor("#111827"), 1.0f);
        addPreviewSpace(content, dp(3));
        addPreviewLine(content, dp(38), dp(2), color, 1.0f);
        addPreviewSpace(content, dp(8));

        View divider = new View(requireContext());
        divider.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(1)));
        divider.setBackgroundColor(Color.parseColor("#E5E7EB"));
        content.addView(divider);
        addPreviewSpace(content, dp(6));

        for (int i = 0; i < 6; i++) {
            int lineW = i % 3 == 2 ? dp(32) : (i % 2 == 0 ? dp(52) : dp(44));
            addPreviewLine(content, lineW, dp(2), Color.parseColor("#9CA3AF"), 1.0f);
            addPreviewSpace(content, dp(4));
        }
        parent.addView(content);
    }

    // ── Executive: tall dark header + accent line + lines ──
    private void buildExecutivePreview(FrameLayout parent, int color, int w, int h) {
        int headerH = dp(44);

        View header = new View(requireContext());
        FrameLayout.LayoutParams headerLp = new FrameLayout.LayoutParams(w, headerH);
        headerLp.gravity = Gravity.TOP;
        header.setLayoutParams(headerLp);
        header.setBackgroundColor(color);
        parent.addView(header);

        // Name in header
        LinearLayout headerContent = new LinearLayout(requireContext());
        headerContent.setOrientation(LinearLayout.VERTICAL);
        headerContent.setGravity(Gravity.CENTER_VERTICAL);
        FrameLayout.LayoutParams hcLp = new FrameLayout.LayoutParams(w, headerH);
        hcLp.gravity = Gravity.TOP;
        headerContent.setLayoutParams(hcLp);
        headerContent.setPadding(dp(8), dp(8), dp(8), dp(4));
        addPreviewLine(headerContent, dp(50), dp(4), Color.WHITE, 1.0f);
        addPreviewSpace(headerContent, dp(4));
        addPreviewLine(headerContent, dp(36), dp(2), Color.argb(160, 255, 255, 255), 1.0f);
        parent.addView(headerContent);

        // Accent bar
        View accent = new View(requireContext());
        int accentColor = lightenColor(color, 0.3f);
        FrameLayout.LayoutParams accentLp = new FrameLayout.LayoutParams(w, dp(4));
        accentLp.gravity = Gravity.TOP;
        accentLp.topMargin = headerH;
        accent.setLayoutParams(accentLp);
        accent.setBackgroundColor(accentColor);
        parent.addView(accent);

        LinearLayout body = new LinearLayout(requireContext());
        body.setOrientation(LinearLayout.VERTICAL);
        body.setBackgroundColor(Color.WHITE);
        int bodyH = h - headerH - dp(4);
        FrameLayout.LayoutParams bodyLp = new FrameLayout.LayoutParams(w, bodyH);
        bodyLp.gravity = Gravity.BOTTOM;
        body.setLayoutParams(bodyLp);
        body.setPadding(dp(8), dp(6), dp(8), dp(6));

        for (int i = 0; i < 4; i++) {
            addPreviewLine(body, dp(38), dp(3), color, 0.85f);
            addPreviewSpace(body, dp(3));
            addPreviewLine(body, dp(50), dp(2), Color.parseColor("#D1D5DB"), 1.0f);
            addPreviewSpace(body, dp(2));
            addPreviewLine(body, dp(42), dp(2), Color.parseColor("#D1D5DB"), 1.0f);
            addPreviewSpace(body, dp(4));
        }
        parent.addView(body);
    }

    // ── Blueprint: wider blue sidebar + right content ──
    private void buildBlueprintPreview(FrameLayout parent, int color, int w, int h) {
        int sidebarW = dp(34);
        int darkerColor = darkenColor(color, 0.15f);

        View sidebar = new View(requireContext());
        FrameLayout.LayoutParams sidebarLp = new FrameLayout.LayoutParams(sidebarW, h);
        sidebarLp.gravity = Gravity.START;
        sidebar.setLayoutParams(sidebarLp);
        sidebar.setBackgroundColor(color);
        parent.addView(sidebar);

        // Sidebar content lines (white)
        LinearLayout sideContent = new LinearLayout(requireContext());
        sideContent.setOrientation(LinearLayout.VERTICAL);
        FrameLayout.LayoutParams scLp = new FrameLayout.LayoutParams(sidebarW, h);
        scLp.gravity = Gravity.START;
        sideContent.setLayoutParams(scLp);
        sideContent.setPadding(dp(4), dp(10), dp(4), dp(6));
        for (int i = 0; i < 7; i++) {
            addPreviewLine(sideContent, dp(20), dp(2), Color.argb(150, 255, 255, 255), 1.0f);
            addPreviewSpace(sideContent, dp(5));
        }
        parent.addView(sideContent);

        // Main content area
        LinearLayout content = new LinearLayout(requireContext());
        content.setOrientation(LinearLayout.VERTICAL);
        content.setBackgroundColor(Color.WHITE);
        FrameLayout.LayoutParams contentLp = new FrameLayout.LayoutParams(w - sidebarW, h);
        contentLp.gravity = Gravity.END;
        content.setLayoutParams(contentLp);
        content.setPadding(dp(6), dp(8), dp(6), dp(6));

        addPreviewLine(content, dp(42), dp(4), darkerColor, 1.0f);
        addPreviewSpace(content, dp(3));
        addPreviewLine(content, dp(30), dp(2), Color.parseColor("#9CA3AF"), 0.8f);
        addPreviewSpace(content, dp(6));
        for (int i = 0; i < 6; i++) {
            addPreviewLine(content, i % 2 == 1 ? dp(34) : dp(46), dp(2), Color.parseColor("#D1D5DB"), 1.0f);
            addPreviewSpace(content, dp(4));
        }
        parent.addView(content);
    }

    // ── Helpers ──

    private void addPreviewLine(LinearLayout parent, int width, int height, int color, float alpha) {
        View line = new View(requireContext());
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(width, height);
        line.setLayoutParams(lp);
        line.setAlpha(alpha);
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(color);
        bg.setCornerRadius(height / 2f);
        line.setBackground(bg);
        parent.addView(line);
    }

    private void addPreviewSpace(LinearLayout parent, int height) {
        View spacer = new View(requireContext());
        spacer.setLayoutParams(new LinearLayout.LayoutParams(1, height));
        parent.addView(spacer);
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private static int lightenColor(int color, float factor) {
        float[] hsv = new float[3];
        Color.colorToHSV(color, hsv);
        hsv[1] = Math.max(0f, hsv[1] - factor);
        hsv[2] = Math.min(1f, hsv[2] + factor);
        return Color.HSVToColor(hsv);
    }

    private static int darkenColor(int color, float factor) {
        float[] hsv = new float[3];
        Color.colorToHSV(color, hsv);
        hsv[2] = Math.max(0f, hsv[2] - factor);
        return Color.HSVToColor(hsv);
    }
}
