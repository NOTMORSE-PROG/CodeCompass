package com.example.codecompass.ui.adapter;

import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.text.format.DateUtils;
import android.view.View;
import android.widget.TextView;

import com.example.codecompass.R;
import com.example.codecompass.model.Resume;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Shared binder for an {@code item_resume.xml} row view. Used by both the list adapter
 * ({@link ResumeAdapter}) and the resume-source bottom sheet so the row rendering stays
 * single-sourced.
 */
public final class ResumeRowBinder {

    private static final Map<String, String> TEMPLATE_COLORS = new HashMap<>();
    private static final Map<String, String> TEMPLATE_LABELS = new HashMap<>();
    static {
        TEMPLATE_COLORS.put("modern",       "#1A2F5E");
        TEMPLATE_COLORS.put("classic",      "#374151");
        TEMPLATE_COLORS.put("minimal",      "#6B7280");
        TEMPLATE_COLORS.put("executive",    "#065F46");
        TEMPLATE_COLORS.put("sidebar-blue", "#3730A3");

        TEMPLATE_LABELS.put("modern",       "Modern");
        TEMPLATE_LABELS.put("classic",      "Classic");
        TEMPLATE_LABELS.put("minimal",      "Minimal");
        TEMPLATE_LABELS.put("executive",    "Executive");
        TEMPLATE_LABELS.put("sidebar-blue", "Blueprint");
    }

    private ResumeRowBinder() { /* no instances */ }

    /**
     * Populate the title / template name / relative-time / color-circle inside an
     * {@code item_resume.xml}-inflated view. Does not attach click listeners — the
     * caller owns those.
     */
    public static void bind(View itemView, Resume resume) {
        TextView tvTemplateInitial = itemView.findViewById(R.id.tvTemplateInitial);
        TextView tvResumeTitle     = itemView.findViewById(R.id.tvResumeTitle);
        TextView tvTemplateName    = itemView.findViewById(R.id.tvTemplateName);
        TextView tvUpdatedAt       = itemView.findViewById(R.id.tvUpdatedAt);

        tvResumeTitle.setText(resume.getTitle());

        String template = resume.getTemplateName() != null ? resume.getTemplateName() : "modern";
        String label    = TEMPLATE_LABELS.getOrDefault(template, "Modern");
        tvTemplateName.setText(label);

        // Template color circle
        String colorHex = TEMPLATE_COLORS.getOrDefault(template, "#1A2F5E");
        GradientDrawable circle = new GradientDrawable();
        circle.setShape(GradientDrawable.OVAL);
        circle.setColor(Color.parseColor(colorHex));
        tvTemplateInitial.setBackground(circle);
        tvTemplateInitial.setText(label.substring(0, 1));

        // Relative time
        if (resume.getUpdatedAt() != null) {
            try {
                SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.US);
                Date date = sdf.parse(resume.getUpdatedAt());
                if (date != null) {
                    CharSequence relative = DateUtils.getRelativeTimeSpanString(
                            date.getTime(), System.currentTimeMillis(),
                            DateUtils.MINUTE_IN_MILLIS);
                    tvUpdatedAt.setText(itemView.getContext().getString(
                            R.string.resume_updated_ago, relative));
                } else {
                    tvUpdatedAt.setText("");
                }
            } catch (ParseException e) {
                tvUpdatedAt.setText("");
            }
        } else {
            tvUpdatedAt.setText("");
        }
    }
}
