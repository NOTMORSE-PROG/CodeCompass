package com.example.codecompass.ui;

import android.annotation.SuppressLint;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.codecompass.R;
import com.google.android.material.bottomsheet.BottomSheetDialogFragment;

public class TemplatePickerBottomSheet extends BottomSheetDialogFragment {

    public interface OnTemplateSelectedListener {
        void onSelected(String templateName, String color);
    }

    private static final String ARG_CURRENT_TEMPLATE = "current_template";
    private static final String ARG_CURRENT_COLOR = "current_color";

    private static final String[] TEMPLATE_IDS = {"modern", "classic", "minimal", "executive", "sidebar-blue"};
    private static final String[] TEMPLATE_NAMES = {"Modern", "Classic", "Minimal", "Executive", "Blueprint"};
    private static final String[] COLOR_PRESETS = {"#1A2F5E", "#374151", "#065F46", "#3730A3", "#9F1239", "#0A0A0A"};

    private String selectedTemplate;
    private String selectedColor;
    private OnTemplateSelectedListener listener;

    public static TemplatePickerBottomSheet newInstance(String currentTemplate, String currentColor) {
        TemplatePickerBottomSheet f = new TemplatePickerBottomSheet();
        Bundle args = new Bundle();
        args.putString(ARG_CURRENT_TEMPLATE, currentTemplate);
        args.putString(ARG_CURRENT_COLOR, currentColor);
        f.setArguments(args);
        return f;
    }

    public void setOnTemplateSelectedListener(OnTemplateSelectedListener listener) {
        this.listener = listener;
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (getArguments() != null) {
            selectedTemplate = getArguments().getString(ARG_CURRENT_TEMPLATE, "modern");
            selectedColor = getArguments().getString(ARG_CURRENT_COLOR, "#1A2F5E");
        }
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.bottom_sheet_template_picker, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        // Template grid
        RecyclerView rvTemplates = view.findViewById(R.id.rvTemplates);
        rvTemplates.setLayoutManager(new GridLayoutManager(requireContext(), 2));
        rvTemplates.setAdapter(new TemplateAdapter());

        // Color presets
        LinearLayout colorLayout = view.findViewById(R.id.layoutColorPresets);
        int size = (int) (40 * getResources().getDisplayMetrics().density);
        int margin = (int) (8 * getResources().getDisplayMetrics().density);

        for (String hex : COLOR_PRESETS) {
            View circle = new View(requireContext());
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(size, size);
            params.setMarginEnd(margin);
            circle.setLayoutParams(params);

            GradientDrawable bg = new GradientDrawable();
            bg.setShape(GradientDrawable.OVAL);
            bg.setColor(Color.parseColor(hex));
            if (hex.equals(selectedColor)) {
                bg.setStroke((int) (3 * getResources().getDisplayMetrics().density),
                        requireContext().getColor(R.color.colorPrimary));
            }
            circle.setBackground(bg);
            circle.setOnClickListener(v -> {
                selectedColor = hex;
                // Rebuild color circles
                colorLayout.removeAllViews();
                onViewCreated(view, null);
            });
            colorLayout.addView(circle);
        }

        // Apply button
        view.findViewById(R.id.btnApplyTemplate).setOnClickListener(v -> {
            if (listener != null) listener.onSelected(selectedTemplate, selectedColor);
            dismiss();
        });
    }

    private class TemplateAdapter extends RecyclerView.Adapter<TemplateAdapter.VH> {
        @NonNull
        @Override
        public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View v = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_template_preview, parent, false);
            return new VH(v);
        }

        @SuppressLint("NotifyDataSetChanged")
        @Override
        public void onBindViewHolder(@NonNull VH holder, int position) {
            String id = TEMPLATE_IDS[position];
            holder.tvName.setText(TEMPLATE_NAMES[position]);
            holder.tvSelected.setVisibility(id.equals(selectedTemplate) ? View.VISIBLE : View.GONE);

            if (id.equals(selectedTemplate)) {
                holder.itemView.setBackgroundResource(R.drawable.bg_template_selected);
            } else {
                holder.itemView.setBackground(null);
            }

            holder.itemView.setOnClickListener(v -> {
                selectedTemplate = id;
                notifyDataSetChanged();
            });
        }

        @Override
        public int getItemCount() { return TEMPLATE_IDS.length; }

        class VH extends RecyclerView.ViewHolder {
            final TextView tvName;
            final TextView tvSelected;
            VH(@NonNull View itemView) {
                super(itemView);
                tvName = itemView.findViewById(R.id.tvTemplateName);
                tvSelected = itemView.findViewById(R.id.tvSelected);
            }
        }
    }
}
