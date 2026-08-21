package com.example.codecompass.ui;

import android.animation.ObjectAnimator;
import android.animation.ValueAnimator;
import android.content.Intent;
import android.content.res.ColorStateList;
import android.graphics.Typeface;
import android.net.Uri;
import android.text.Spannable;
import android.text.SpannableStringBuilder;
import android.text.method.LinkMovementMethod;
import android.text.style.BackgroundColorSpan;
import android.text.style.StrikethroughSpan;
import android.text.style.StyleSpan;
import android.text.style.TypefaceSpan;
import android.text.util.Linkify;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.AccelerateDecelerateInterpolator;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.TextView;

import android.annotation.SuppressLint;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.codecompass.R;
import com.example.codecompass.model.ChatMessage;
import com.example.codecompass.model.EditProposal;
import com.example.codecompass.model.ResourceLink;
import com.example.codecompass.model.RoadmapSwitchProposal;
import com.example.codecompass.model.RoadmapUpskillProposal;
import com.google.android.material.button.MaterialButton;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class ChatAdapter extends RecyclerView.Adapter<ChatAdapter.MessageViewHolder> {

    private static final int VIEW_TYPE_USER = 0;
    private static final int VIEW_TYPE_AI   = 1;

    private final List<ChatMessage> messages = new ArrayList<>();
    private RecyclerView recyclerView;
    private final boolean useLightBubbles;
    private OnProposalListener    proposalListener;
    private OnRoadmapActionListener roadmapActionListener;

    // ── Listener interfaces ───────────────────────────────────────────────────

    public interface OnProposalListener {
        void onApply(int messageIndex, List<EditProposal> proposals);
        void onDismiss(int messageIndex);
    }

    public interface OnRoadmapActionListener {
        void onSwitchRoadmap(int messageIndex, RoadmapSwitchProposal proposal);
        void onUpskillRoadmap(int messageIndex, RoadmapUpskillProposal proposal);
        void onDismissRoadmapSwitch(int messageIndex);
        void onDismissRoadmapUpskill(int messageIndex);
    }

    // ── Constructors ──────────────────────────────────────────────────────────

    public ChatAdapter() {
        this.useLightBubbles = false; // dark mode — used by OnboardingActivity
    }

    public ChatAdapter(boolean useLightBubbles) {
        this.useLightBubbles = useLightBubbles;
    }

    public ChatAdapter(boolean useLightBubbles, OnProposalListener pl) {
        this.useLightBubbles  = useLightBubbles;
        this.proposalListener = pl;
    }

    public ChatAdapter(boolean useLightBubbles, OnProposalListener pl,
                       OnRoadmapActionListener rl) {
        this.useLightBubbles        = useLightBubbles;
        this.proposalListener       = pl;
        this.roadmapActionListener  = rl;
    }

    // ── RecyclerView.Adapter ──────────────────────────────────────────────────

    @Override
    public void onAttachedToRecyclerView(@NonNull RecyclerView recyclerView) {
        super.onAttachedToRecyclerView(recyclerView);
        this.recyclerView = recyclerView;
    }

    @Override
    public int getItemViewType(int position) {
        return messages.get(position).isUser() ? VIEW_TYPE_USER : VIEW_TYPE_AI;
    }

    @NonNull
    @Override
    public MessageViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_chat_message, parent, false);
        return new MessageViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull MessageViewHolder holder, int position) {
        holder.bind(messages.get(position), useLightBubbles, position,
                proposalListener, roadmapActionListener);
    }

    @Override
    public void onViewRecycled(@NonNull MessageViewHolder holder) {
        super.onViewRecycled(holder);
        holder.stopDotAnimation();
    }

    @Override
    public int getItemCount() {
        return messages.size();
    }

    // ── Mutation helpers ──────────────────────────────────────────────────────

    public void addMessage(ChatMessage message) {
        messages.add(message);
        int position = messages.size() - 1;
        notifyItemInserted(position);
        if (recyclerView != null) recyclerView.scrollToPosition(position);
    }

    /** Update bubble in-place — clears typing state and sets streamed content. */
    public void updateMessageAt(int index, String content) {
        if (index < 0 || index >= messages.size()) return;
        ChatMessage msg = messages.get(index);
        msg.setContent(content);
        msg.setTyping(false);
        notifyItemChanged(index);
        if (recyclerView != null) recyclerView.scrollToPosition(index);
    }

    public boolean isTypingAt(int index) {
        return index >= 0 && index < messages.size() && messages.get(index).isTyping();
    }

    public void removeMessageAt(int index) {
        if (index < 0 || index >= messages.size()) return;
        messages.remove(index);
        notifyItemRemoved(index);
    }

    public void setResourcesAt(int index, List<ResourceLink> resources) {
        if (index < 0 || index >= messages.size()) return;
        messages.get(index).setResources(resources);
        notifyItemChanged(index);
    }

    public void setProposalsAt(int index, List<EditProposal> proposals) {
        if (index < 0 || index >= messages.size()) return;
        messages.get(index).setEditProposals(proposals);
        notifyItemChanged(index);
    }

    public void markProposalsApplied(int index) {
        if (index < 0 || index >= messages.size()) return;
        messages.get(index).setProposalsApplied(true);
        notifyItemChanged(index);
    }

    public void markProposalsDismissed(int index) {
        if (index < 0 || index >= messages.size()) return;
        messages.get(index).setProposalsDismissed(true);
        notifyItemChanged(index);
    }

    public void setRoadmapSwitchAt(int index, RoadmapSwitchProposal proposal) {
        if (index < 0 || index >= messages.size()) return;
        messages.get(index).setRoadmapSwitch(proposal);
        notifyItemChanged(index);
    }

    public void markSwitchApplied(int index) {
        if (index < 0 || index >= messages.size()) return;
        messages.get(index).setSwitchApplied(true);
        notifyItemChanged(index);
    }

    public void markSwitchDismissed(int index) {
        if (index < 0 || index >= messages.size()) return;
        messages.get(index).setSwitchDismissed(true);
        notifyItemChanged(index);
    }

    public void setRoadmapUpskillAt(int index, RoadmapUpskillProposal proposal) {
        if (index < 0 || index >= messages.size()) return;
        messages.get(index).setRoadmapUpskill(proposal);
        notifyItemChanged(index);
    }

    public void markUpskillApplied(int index) {
        if (index < 0 || index >= messages.size()) return;
        messages.get(index).setUpskillApplied(true);
        notifyItemChanged(index);
    }

    public void markUpskillDismissed(int index) {
        if (index < 0 || index >= messages.size()) return;
        messages.get(index).setUpskillDismissed(true);
        notifyItemChanged(index);
    }

    // ── ViewHolder ────────────────────────────────────────────────────────────

    static class MessageViewHolder extends RecyclerView.ViewHolder {

        private final TextView            tvMessage;
        private final TextView            tvAvatar;
        private final View                layoutTypingDots;
        private final View                dot1, dot2, dot3;
        private final LinearLayout        layoutAiContent;
        private final HorizontalScrollView scrollResources;
        private final LinearLayout        containerResources;
        private final FrameLayout         containerProposal;
        private final FrameLayout         containerRoadmapSwitch;
        private final FrameLayout         containerRoadmapUpskill;
        private final List<ObjectAnimator> dotAnimators = new ArrayList<>();

        MessageViewHolder(@NonNull View itemView) {
            super(itemView);
            tvMessage               = itemView.findViewById(R.id.tvMessage);
            tvAvatar                = itemView.findViewById(R.id.tvAvatar);
            layoutTypingDots        = itemView.findViewById(R.id.layoutTypingDots);
            dot1                    = itemView.findViewById(R.id.dot1);
            dot2                    = itemView.findViewById(R.id.dot2);
            dot3                    = itemView.findViewById(R.id.dot3);
            layoutAiContent         = itemView.findViewById(R.id.layoutAiContent);
            scrollResources         = itemView.findViewById(R.id.scrollResources);
            containerResources      = itemView.findViewById(R.id.containerResources);
            containerProposal       = itemView.findViewById(R.id.containerProposal);
            containerRoadmapSwitch  = itemView.findViewById(R.id.containerRoadmapSwitch);
            containerRoadmapUpskill = itemView.findViewById(R.id.containerRoadmapUpskill);
        }

        void bind(ChatMessage message, boolean useLightBubbles, int position,
                  OnProposalListener proposalListener,
                  OnRoadmapActionListener roadmapActionListener) {

            if (message.isTyping()) {
                tvAvatar.setVisibility(View.VISIBLE);
                layoutTypingDots.setVisibility(View.VISIBLE);
                layoutAiContent.setVisibility(View.GONE);
                ((LinearLayout) itemView).setGravity(Gravity.START | Gravity.BOTTOM);
                if (useLightBubbles) {
                    layoutTypingDots.setBackgroundResource(R.drawable.bg_chat_bubble_ai_light);
                } else {
                    layoutTypingDots.setBackgroundResource(R.drawable.bg_chat_bubble);
                }
                int dotColor = useLightBubbles ? 0xFF888888 : 0xCCFFFFFF;
                ColorStateList tint = ColorStateList.valueOf(dotColor);
                dot1.setBackgroundTintList(tint);
                dot2.setBackgroundTintList(tint);
                dot3.setBackgroundTintList(tint);
                startDotAnimation();
                return;
            }

            stopDotAnimation();
            layoutTypingDots.setVisibility(View.GONE);
            layoutAiContent.setVisibility(View.VISIBLE);
            tvMessage.setVisibility(View.VISIBLE);
            tvMessage.setText(renderMarkdown(message.getContent()));
            boolean hasLinks = Linkify.addLinks(tvMessage, Linkify.WEB_URLS);
            if (hasLinks) {
                tvMessage.setMovementMethod(LinkMovementMethod.getInstance());
                tvMessage.setLinkTextColor(0xFF1976D2);
            } else {
                tvMessage.setMovementMethod(null);
            }

            LinearLayout.LayoutParams params =
                    (LinearLayout.LayoutParams) tvMessage.getLayoutParams();

            if (message.isUser()) {
                tvAvatar.setVisibility(View.GONE);
                tvMessage.setBackgroundResource(R.drawable.bg_chat_bubble_user);
                tvMessage.setTextColor(
                        itemView.getContext().getColor(R.color.colorChatUserText));
                params.gravity = Gravity.END;
                ((LinearLayout) itemView).setGravity(Gravity.END | Gravity.BOTTOM);
                scrollResources.setVisibility(View.GONE);
                containerProposal.setVisibility(View.GONE);
                containerRoadmapSwitch.setVisibility(View.GONE);
                containerRoadmapUpskill.setVisibility(View.GONE);
            } else {
                tvAvatar.setVisibility(View.VISIBLE);
                if (useLightBubbles) {
                    tvMessage.setBackgroundResource(R.drawable.bg_chat_bubble_ai_light);
                    tvMessage.setTextColor(
                            itemView.getContext().getColor(R.color.colorChatAiText));
                } else {
                    tvMessage.setBackgroundResource(R.drawable.bg_chat_bubble);
                    tvMessage.setTextColor(
                            itemView.getContext().getColor(R.color.colorChatAiTextDark));
                }
                params.gravity = Gravity.START;
                ((LinearLayout) itemView).setGravity(Gravity.START | Gravity.BOTTOM);

                // ── Resource link cards ──────────────────────────────────────
                List<ResourceLink> resources = message.getResources();
                if (resources != null && !resources.isEmpty()) {
                    scrollResources.setVisibility(View.VISIBLE);
                    containerResources.removeAllViews();
                    LayoutInflater inflater = LayoutInflater.from(itemView.getContext());
                    for (ResourceLink link : resources) {
                        View card = inflater.inflate(
                                R.layout.item_resource_card, containerResources, false);
                        ((TextView) card.findViewById(R.id.tvResourceTitle)).setText(link.title);
                        ((TextView) card.findViewById(R.id.tvResourceHost))
                                .setText(link.getHostname());
                        card.setOnClickListener(v -> {
                            try {
                                v.getContext().startActivity(new Intent(Intent.ACTION_VIEW,
                                        Uri.parse(link.url)));
                            } catch (Exception ignored) {}
                        });
                        containerResources.addView(card);
                    }
                } else {
                    scrollResources.setVisibility(View.GONE);
                    containerResources.removeAllViews();
                }

                // ── Edit proposal card ───────────────────────────────────────
                List<EditProposal> proposals = message.getEditProposals();
                if (proposals != null && !proposals.isEmpty()
                        && !message.isProposalsDismissed()) {
                    containerProposal.setVisibility(View.VISIBLE);
                    containerProposal.removeAllViews();
                    LayoutInflater inf = LayoutInflater.from(itemView.getContext());
                    View card = inf.inflate(
                            R.layout.item_edit_proposal_card, containerProposal, false);
                    int n = proposals.size();
                    ((TextView) card.findViewById(R.id.tvProposalHeader)).setText(
                            itemView.getContext().getString(R.string.proposal_header_format, n, n == 1 ? "" : "s"));
                    LinearLayout items = card.findViewById(R.id.containerProposalItems);
                    items.removeAllViews();
                    boolean hasDangerous = false;
                    for (EditProposal p : proposals) {
                        TextView tv = new TextView(itemView.getContext());
                        tv.setText(itemView.getContext().getString(R.string.proposal_item_format, p.getSummary()));
                        tv.setTextSize(12f);
                        tv.setPadding(0, 2, 0, 2);
                        tv.setTextColor(itemView.getContext().getColor(R.color.colorTextPrimary));
                        items.addView(tv);
                        if (p.isDangerous()) hasDangerous = true;
                    }
                    card.findViewById(R.id.layoutDangerWarning).setVisibility(
                            hasDangerous ? View.VISIBLE : View.GONE);
                    TextView tvApplied      = card.findViewById(R.id.tvApplied);
                    LinearLayout layoutBtns = card.findViewById(R.id.layoutProposalButtons);
                    if (message.isProposalsApplied()) {
                        tvApplied.setVisibility(View.VISIBLE);
                        layoutBtns.setVisibility(View.GONE);
                    } else {
                        tvApplied.setVisibility(View.GONE);
                        layoutBtns.setVisibility(View.VISIBLE);
                        card.findViewById(R.id.btnDismissProposal).setOnClickListener(v -> {
                            if (proposalListener != null) proposalListener.onDismiss(position);
                        });
                        card.findViewById(R.id.btnApplyProposal).setOnClickListener(v -> {
                            if (proposalListener != null)
                                proposalListener.onApply(position, proposals);
                        });
                    }
                    containerProposal.addView(card);
                } else {
                    containerProposal.setVisibility(View.GONE);
                    containerProposal.removeAllViews();
                }

                // ── Roadmap switch card ──────────────────────────────────────
                RoadmapSwitchProposal sw = message.getRoadmapSwitch();
                if (sw != null && !message.isSwitchDismissed()) {
                    containerRoadmapSwitch.setVisibility(View.VISIBLE);
                    containerRoadmapSwitch.removeAllViews();
                    LayoutInflater inf2 = LayoutInflater.from(itemView.getContext());
                    View card = inf2.inflate(
                            R.layout.item_roadmap_switch_card, containerRoadmapSwitch, false);

                    // Details: "New path: X\nCareer goal: Y"
                    ((TextView) card.findViewById(R.id.tvSwitchDetails)).setText(
                            itemView.getContext().getString(R.string.switch_details_format,
                                    sw.getNewPath(), sw.getCareerGoal()));

                    LinearLayout layoutApplied = card.findViewById(R.id.layoutSwitchApplied);
                    LinearLayout layoutButtons = card.findViewById(R.id.layoutSwitchButtons);
                    if (message.isSwitchApplied()) {
                        layoutApplied.setVisibility(View.VISIBLE);
                        layoutButtons.setVisibility(View.GONE);
                        card.findViewById(R.id.btnSwitchGotIt).setOnClickListener(v -> {
                            if (roadmapActionListener != null)
                                roadmapActionListener.onDismissRoadmapSwitch(position);
                        });
                    } else {
                        layoutApplied.setVisibility(View.GONE);
                        layoutButtons.setVisibility(View.VISIBLE);
                        card.findViewById(R.id.btnSwitchConfirm).setOnClickListener(v -> {
                            if (roadmapActionListener != null)
                                roadmapActionListener.onSwitchRoadmap(position, sw);
                        });
                        card.findViewById(R.id.btnSwitchDismiss).setOnClickListener(v -> {
                            if (roadmapActionListener != null)
                                roadmapActionListener.onDismissRoadmapSwitch(position);
                        });
                    }
                    containerRoadmapSwitch.addView(card);
                } else {
                    containerRoadmapSwitch.setVisibility(View.GONE);
                    containerRoadmapSwitch.removeAllViews();
                }

                // ── Roadmap upskill card ─────────────────────────────────────
                RoadmapUpskillProposal up = message.getRoadmapUpskill();
                if (up != null && !message.isUpskillDismissed()) {
                    containerRoadmapUpskill.setVisibility(View.VISIBLE);
                    containerRoadmapUpskill.removeAllViews();
                    LayoutInflater inf3 = LayoutInflater.from(itemView.getContext());
                    View card = inf3.inflate(
                            R.layout.item_roadmap_upskill_card, containerRoadmapUpskill, false);

                    // Plan summary (optional)
                    TextView tvPlan = card.findViewById(R.id.tvUpskillPlan);
                    if (up.getSummary() != null && !up.getSummary().isEmpty()) {
                        tvPlan.setText(itemView.getContext().getString(R.string.upskill_plan_format, up.getSummary()));
                        tvPlan.setVisibility(View.VISIBLE);
                    } else {
                        tvPlan.setVisibility(View.GONE);
                    }

                    LinearLayout layoutApplied = card.findViewById(R.id.layoutUpskillApplied);
                    LinearLayout layoutButtons = card.findViewById(R.id.layoutUpskillButtons);
                    if (message.isUpskillApplied()) {
                        layoutApplied.setVisibility(View.VISIBLE);
                        layoutButtons.setVisibility(View.GONE);
                        card.findViewById(R.id.btnUpskillGotIt).setOnClickListener(v -> {
                            if (roadmapActionListener != null)
                                roadmapActionListener.onDismissRoadmapUpskill(position);
                        });
                    } else {
                        layoutApplied.setVisibility(View.GONE);
                        layoutButtons.setVisibility(View.VISIBLE);
                        card.findViewById(R.id.btnUpskillConfirm).setOnClickListener(v -> {
                            if (roadmapActionListener != null)
                                roadmapActionListener.onUpskillRoadmap(position, up);
                        });
                        card.findViewById(R.id.btnUpskillDismiss).setOnClickListener(v -> {
                            if (roadmapActionListener != null)
                                roadmapActionListener.onDismissRoadmapUpskill(position);
                        });
                    }
                    containerRoadmapUpskill.addView(card);
                } else {
                    containerRoadmapUpskill.setVisibility(View.GONE);
                    containerRoadmapUpskill.removeAllViews();
                }
            }
            tvMessage.setLayoutParams(params);
        }

        void startDotAnimation() {
            stopDotAnimation();
            float bounce = itemView.getResources().getDisplayMetrics().density * -5f;
            View[] dots   = {dot1, dot2, dot3};
            int[]  delays = {0, 150, 300};
            for (int i = 0; i < 3; i++) {
                ObjectAnimator anim = ObjectAnimator.ofFloat(
                        dots[i], View.TRANSLATION_Y, 0f, bounce);
                anim.setDuration(450);
                anim.setStartDelay(delays[i]);
                anim.setRepeatCount(ValueAnimator.INFINITE);
                anim.setRepeatMode(ValueAnimator.REVERSE);
                anim.setInterpolator(new AccelerateDecelerateInterpolator());
                anim.start();
                dotAnimators.add(anim);
            }
        }

        void stopDotAnimation() {
            for (ObjectAnimator a : dotAnimators) a.cancel();
            dotAnimators.clear();
            dot1.setTranslationY(0f);
            dot2.setTranslationY(0f);
            dot3.setTranslationY(0f);
        }
    }

    // ── Tab switch helpers ────────────────────────────────────────────────────

    /** Returns a snapshot of all non-typing messages (safe to cache across tab switches). */
    public List<ChatMessage> getMessages() {
        List<ChatMessage> snapshot = new ArrayList<>();
        for (ChatMessage m : messages) {
            if (!m.isTyping()) snapshot.add(m);
        }
        return snapshot;
    }

    /** Replaces the entire message list (used when restoring a saved tab). */
    @SuppressLint("NotifyDataSetChanged")
    public void setMessages(List<ChatMessage> newMessages) {
        messages.clear();
        messages.addAll(newMessages);
        notifyDataSetChanged();
    }

    // ── Markdown renderer ─────────────────────────────────────────────────────

    private static final Pattern CODE_BLOCK_PATTERN =
            Pattern.compile("```(?:[a-zA-Z]*)\\n([\\s\\S]*?)```", Pattern.DOTALL);
    private static final Pattern INLINE_CODE_PATTERN =
            Pattern.compile("`([^`\\n]+)`");
    private static final Pattern BOLD_PATTERN =
            Pattern.compile("\\*\\*(.+?)\\*\\*", Pattern.DOTALL);
    private static final Pattern STRIKE_PATTERN =
            Pattern.compile("~~(.+?)~~", Pattern.DOTALL);

    static CharSequence renderMarkdown(String text) {
        if (text == null) return null;

        SpannableStringBuilder ssb = new SpannableStringBuilder(text);

        // 1. Code blocks  ``` lang \n code \n ```
        applyPatternForward(ssb, CODE_BLOCK_PATTERN, (sb, start, end, group1) -> {
            // Replace the full ``` fence + optional lang + ``` with just the inner code
            sb.replace(start, end, group1);
            sb.setSpan(new TypefaceSpan("monospace"),
                    start, start + group1.length(), Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
            sb.setSpan(new BackgroundColorSpan(0x0F000000),
                    start, start + group1.length(), Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
        });

        // 2. Inline code  `code`
        applyPatternForward(ssb, INLINE_CODE_PATTERN, (sb, start, end, group1) -> {
            sb.replace(start, end, group1);
            sb.setSpan(new TypefaceSpan("monospace"),
                    start, start + group1.length(), Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
            sb.setSpan(new BackgroundColorSpan(0x1A000000),
                    start, start + group1.length(), Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
        });

        // 3. Bold  **text**
        applyPatternForward(ssb, BOLD_PATTERN, (sb, start, end, group1) -> {
            sb.replace(start, end, group1);
            sb.setSpan(new StyleSpan(Typeface.BOLD),
                    start, start + group1.length(), Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
        });

        // 4. Strikethrough  ~~text~~
        applyPatternForward(ssb, STRIKE_PATTERN, (sb, start, end, group1) -> {
            sb.replace(start, end, group1);
            sb.setSpan(new StrikethroughSpan(),
                    start, start + group1.length(), Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
        });

        return ssb;
    }

    @FunctionalInterface
    interface SpanApplier {
        void apply(SpannableStringBuilder sb, int start, int end, String group1);
    }

    private static void applyPatternForward(SpannableStringBuilder ssb,
                                            Pattern pattern, SpanApplier applier) {
        Matcher m = pattern.matcher(ssb.toString());
        int offset = 0;
        while (m.find()) {
            int start  = m.start() - offset;
            int end    = m.end()   - offset;
            String g1  = m.group(1);
            if (g1 == null) continue;
            int removedChars = (end - start) - g1.length();
            applier.apply(ssb, start, end, g1);
            offset += removedChars;
        }
    }
}
