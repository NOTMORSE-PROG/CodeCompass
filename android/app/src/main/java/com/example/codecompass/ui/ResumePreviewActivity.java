package com.example.codecompass.ui;

import android.annotation.SuppressLint;
import android.os.Bundle;
import android.print.PrintAttributes;
import android.print.PrintManager;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;

import androidx.appcompat.app.AppCompatActivity;

import com.example.codecompass.R;

public class ResumePreviewActivity extends AppCompatActivity {

    private WebView wvPreview;
    private String contentJson;
    private String templateName;
    private String primaryColor;
    private int resumeId;

    @SuppressLint("SetJavaScriptEnabled")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_resume_preview);

        contentJson = getIntent().getStringExtra("content_json");
        templateName = getIntent().getStringExtra("template_name");
        primaryColor = getIntent().getStringExtra("primary_color");
        resumeId = getIntent().getIntExtra("resume_id", -1);

        if (contentJson == null) contentJson = "{}";
        if (templateName == null) templateName = "modern";
        if (primaryColor == null) primaryColor = "#1A2F5E";

        wvPreview = findViewById(R.id.wvPreview);
        WebSettings settings = wvPreview.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setLoadWithOverviewMode(true);
        settings.setUseWideViewPort(true);
        settings.setBuiltInZoomControls(true);
        settings.setDisplayZoomControls(false);

        wvPreview.setWebViewClient(new WebViewClient() {
            @Override
            public void onPageFinished(WebView view, String url) {
                super.onPageFinished(view, url);
                injectResumeData();
            }
        });

        wvPreview.loadUrl("file:///android_asset/resume_templates/resume_preview.html");

        // Back
        findViewById(R.id.btnBack).setOnClickListener(v -> finish());

        // Export PDF
        findViewById(R.id.btnExport).setOnClickListener(v -> exportPdf());

        // Template button
        findViewById(R.id.btnTemplate).setOnClickListener(v -> {
            TemplatePickerBottomSheet sheet = TemplatePickerBottomSheet.newInstance(templateName, primaryColor);
            sheet.setOnTemplateSelectedListener((newTemplate, newColor) -> {
                templateName = newTemplate;
                primaryColor = newColor;
                String js = "switchTemplate('" + escapeJs(templateName) + "'); setColor('" + escapeJs(primaryColor) + "');";
                wvPreview.evaluateJavascript(js, null);
            });
            sheet.show(getSupportFragmentManager(), "template_picker");
        });

        // Color button
        findViewById(R.id.btnColor).setOnClickListener(v -> {
            // Open template picker focused on colors
            TemplatePickerBottomSheet sheet = TemplatePickerBottomSheet.newInstance(templateName, primaryColor);
            sheet.setOnTemplateSelectedListener((newTemplate, newColor) -> {
                templateName = newTemplate;
                primaryColor = newColor;
                String js = "switchTemplate('" + escapeJs(templateName) + "'); setColor('" + escapeJs(primaryColor) + "');";
                wvPreview.evaluateJavascript(js, null);
            });
            sheet.show(getSupportFragmentManager(), "color_picker");
        });
    }

    private void injectResumeData() {
        String escaped = escapeJs(contentJson);
        String js = "renderResume('" + escaped + "', '" + escapeJs(templateName) + "', '" + escapeJs(primaryColor) + "');";
        wvPreview.evaluateJavascript(js, null);
    }

    private void exportPdf() {
        PrintManager printManager = (PrintManager) getSystemService(PRINT_SERVICE);
        if (printManager != null) {
            String jobName = "Resume";
            PrintAttributes attributes = new PrintAttributes.Builder()
                    .setMediaSize(PrintAttributes.MediaSize.NA_LETTER)
                    .build();
            printManager.print(jobName, wvPreview.createPrintDocumentAdapter(jobName), attributes);
        }
    }

    private String escapeJs(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\")
                .replace("'", "\\'")
                .replace("\n", "\\n")
                .replace("\r", "\\r");
    }

    @Override
    public void finish() {
        super.finish();
        overridePendingTransition(R.anim.slide_in_left, R.anim.slide_out_right);
    }
}
