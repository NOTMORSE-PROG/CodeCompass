package com.example.codecompass.viewmodel;

import android.app.Application;

import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.example.codecompass.model.Roadmap;
import com.example.codecompass.model.RoadmapNode;
import com.example.codecompass.model.roadmap.AssessmentResponse;
import com.example.codecompass.model.roadmap.NodeResource;
import com.example.codecompass.model.roadmap.QuizResult;
import com.example.codecompass.model.roadmap.UpdateNodeResponse;
import com.example.codecompass.repository.RoadmapRepository;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class RoadmapViewModel extends AndroidViewModel {

    // ── Live data ─────────────────────────────────────────────────────────────

    private final MutableLiveData<Roadmap> roadmapLive = new MutableLiveData<>();
    private final MutableLiveData<List<RoadmapDisplayItem>> displayItemsLive = new MutableLiveData<>();
    private final MutableLiveData<Boolean> loadingLive = new MutableLiveData<>(false);
    private final MutableLiveData<String> errorLive = new MutableLiveData<>();
    /** Fires after a node is completed; carries the XP awarded. */
    private final MutableLiveData<Integer> xpGainEvent = new MutableLiveData<>();
    private final MutableLiveData<AssessmentResponse> quizReadyEvent = new MutableLiveData<>();
    private final MutableLiveData<QuizResult> quizResultEvent = new MutableLiveData<>();
    private final MutableLiveData<List<NodeResource>> resourcesFetchedEvent = new MutableLiveData<>();

    private final RoadmapRepository repo;

    /** The roadmap ID passed from Dashboard (-1 means discover automatically). */
    private int roadmapId = -1;

    public RoadmapViewModel(@NonNull Application application) {
        super(application);
        repo = new RoadmapRepository(application);
    }

    public void setRoadmapId(int id) { this.roadmapId = id; }

    // ── Public API ────────────────────────────────────────────────────────────

    /**
     * Initial load: calls fix-structure first (silently reorders nodes), then loads the roadmap.
     * Only call this on first open, not after status updates.
     */
    public void loadInitial() {
        loadingLive.setValue(true);
        repo.fixStructureAndLoad(roadmapId, new RoadmapRepository.Callback1<Roadmap>() {
            @Override
            public void onSuccess(Roadmap data) {
                loadingLive.postValue(false);
                roadmapLive.postValue(data);
                if (data != null) {
                    displayItemsLive.postValue(flatten(data.getNodes()));
                    if (roadmapId < 0) roadmapId = data.getId();
                }
            }

            @Override
            public void onError(String message) {
                loadingLive.postValue(false);
                errorLive.postValue(message);
            }
        });
    }

    public void load() {
        loadingLive.setValue(true);
        repo.loadRoadmap(roadmapId, new RoadmapRepository.Callback1<Roadmap>() {
            @Override
            public void onSuccess(Roadmap data) {
                loadingLive.postValue(false);
                roadmapLive.postValue(data);
                if (data != null) {
                    displayItemsLive.postValue(flatten(data.getNodes()));
                    // Store the discovered id for future calls
                    if (roadmapId < 0) roadmapId = data.getId();
                }
            }

            @Override
            public void onError(String message) {
                loadingLive.postValue(false);
                errorLive.postValue(message);
            }
        });
    }

    public void refresh() { load(); }

    public void updateNodeStatus(int nodeId, String newStatus) {
        if (roadmapId < 0) return;
        repo.updateNodeStatus(roadmapId, nodeId, newStatus,
                new RoadmapRepository.Callback1<UpdateNodeResponse>() {
                    @Override
                    public void onSuccess(UpdateNodeResponse data) {
                        // Immediately update header completion % without waiting for full reload
                        Roadmap current = roadmapLive.getValue();
                        if (current != null) {
                            current.setCompletionPercentage(data.getCompletionPercentageInt());
                            roadmapLive.postValue(current);
                        }
                        if (data.isXpAwarded()) {
                            xpGainEvent.postValue(data.getNode().getXpReward());
                        }
                        // Full reload to get updated node statuses
                        load();
                    }

                    @Override
                    public void onError(String message) {
                        errorLive.postValue(message);
                    }
                });
    }

    public void fetchResourcesForNode(int nodeId, RoadmapRepository.Callback1<List<NodeResource>> cb) {
        if (roadmapId < 0) return;
        repo.fetchNodeResources(roadmapId, nodeId, cb);
    }

    public void generateQuiz(int nodeId, int resourceId) {
        if (roadmapId < 0) return;
        repo.generateQuiz(roadmapId, nodeId, resourceId,
                new RoadmapRepository.Callback1<AssessmentResponse>() {
                    @Override
                    public void onSuccess(AssessmentResponse data) {
                        quizReadyEvent.postValue(data);
                    }

                    @Override
                    public void onError(String message) {
                        errorLive.postValue(message);
                    }
                });
    }

    public void submitQuiz(int nodeId, int resourceId, int sessionId,
                           Map<String, String> answers) {
        if (roadmapId < 0) return;
        repo.submitQuiz(roadmapId, nodeId, resourceId, sessionId, answers,
                new RoadmapRepository.Callback1<QuizResult>() {
                    @Override
                    public void onSuccess(QuizResult data) {
                        quizResultEvent.postValue(data);
                    }

                    @Override
                    public void onError(String message) {
                        errorLive.postValue(message);
                    }
                });
    }

    public void generateFinalAssessment() {
        if (roadmapId < 0) return;
        repo.generateFinalAssessment(roadmapId,
                new RoadmapRepository.Callback1<AssessmentResponse>() {
                    @Override
                    public void onSuccess(AssessmentResponse data) {
                        quizReadyEvent.postValue(data);
                    }

                    @Override
                    public void onError(String message) {
                        errorLive.postValue(message);
                    }
                });
    }

    public void submitFinalAssessment(int sessionId, Map<String, String> answers) {
        if (roadmapId < 0) return;
        repo.submitFinalAssessment(roadmapId, sessionId, answers,
                new RoadmapRepository.Callback1<QuizResult>() {
                    @Override
                    public void onSuccess(QuizResult data) {
                        quizResultEvent.postValue(data);
                        // After a passing final assessment, reload the roadmap so
                        // cert nodes appear unlocked and the final_assessment is marked done.
                        if (data != null && data.isPassed()) {
                            load();
                        }
                    }

                    @Override
                    public void onError(String message) {
                        errorLive.postValue(message);
                    }
                });
    }

    public void repairRoadmap() {
        if (roadmapId < 0) return;
        repo.repairRoadmap(roadmapId, new RoadmapRepository.Callback1<Void>() {
            @Override
            public void onSuccess(Void data) { load(); }

            @Override
            public void onError(String message) {
                errorLive.postValue(message);
            }
        });
    }

    public int getRoadmapId() { return roadmapId; }

    /** Call before attaching a new quiz observer to drop any stale cached value. */
    public void clearQuizEvents() {
        quizReadyEvent.setValue(null);
        quizResultEvent.setValue(null);
    }

    // ── Accessors ─────────────────────────────────────────────────────────────

    public LiveData<Roadmap> getRoadmap() { return roadmapLive; }
    public LiveData<List<RoadmapDisplayItem>> getDisplayItems() { return displayItemsLive; }
    public LiveData<Boolean> isLoading() { return loadingLive; }
    public LiveData<String> getError() { return errorLive; }
    public LiveData<Integer> getXpGainEvent() { return xpGainEvent; }
    public LiveData<AssessmentResponse> getQuizReadyEvent() { return quizReadyEvent; }
    public LiveData<QuizResult> getQuizResultEvent() { return quizResultEvent; }

    // ── Flatten node tree → display list ─────────────────────────────────────

    /**
     * Converts the flat node list from the API (sorted by nodeOrder) into
     * a RecyclerView-friendly list with milestone headers and subsection dividers.
     *
     * New roadmap structure:
     *   MilestoneHeader (Phase 1..3)
     *     SkillNode / AssessmentNode …
     *     [PROJECTS divider]
     *     ProjectNode …
     *   [FINAL ASSESSMENT divider]       ← global, after all phases
     *   FinalAssessmentNode
     *   [CERTIFICATIONS divider]         ← global, after Final Assessment
     *   CertificationNode …
     *
     * Backward compat: legacy roadmaps have certifications under a per-phase
     * milestone. When a roadmap has NO final_assessment node, certifications
     * stay inline under their milestone (old per-phase layout) and no global
     * "CERTIFICATIONS" divider is emitted.
     */
    private List<RoadmapDisplayItem> flatten(List<RoadmapNode> nodes) {
        if (nodes == null || nodes.isEmpty()) return new ArrayList<>();

        List<RoadmapNode> sorted = new ArrayList<>(nodes);
        Collections.sort(sorted, (a, b) -> a.getNodeOrder() - b.getNodeOrder());

        Map<Integer, Integer> milestoneTotal = new HashMap<>();
        Map<Integer, Integer> milestoneDone  = new HashMap<>();
        Map<Integer, Integer> milestoneXP    = new HashMap<>();

        for (RoadmapNode node : sorted) {
            if (!node.isMilestone() && node.getParentNode() != null) {
                int pid = node.getParentNode();
                milestoneTotal.merge(pid, 1, Integer::sum);
                if (node.isCompleted()) {
                    milestoneDone.merge(pid, 1, Integer::sum);
                } else {
                    milestoneXP.merge(pid, node.getXpReward(), Integer::sum);
                }
            }
        }

        boolean hasFinalAssessment = false;
        for (RoadmapNode node : sorted) {
            if (RoadmapNode.TYPE_FINAL_ASSESSMENT.equals(node.getNodeType())) {
                hasFinalAssessment = true;
                break;
            }
        }

        List<RoadmapDisplayItem> items = new ArrayList<>();
        List<RoadmapNode> finalAssessmentNodes = new ArrayList<>();
        List<RoadmapNode> certNodes            = new ArrayList<>();
        boolean projectDividerAdded = false;
        boolean certDividerInPhase  = false;

        for (RoadmapNode node : sorted) {
            String type = node.getNodeType();

            if (node.isMilestone()) {
                projectDividerAdded = false;
                certDividerInPhase  = false;
                int total = milestoneTotal.getOrDefault(node.getId(), 0);
                int done  = milestoneDone.getOrDefault(node.getId(), 0);
                int xp    = milestoneXP.getOrDefault(node.getId(), 0);
                items.add(RoadmapDisplayItem.milestone(node, total, done, xp));
                continue;
            }

            if (RoadmapNode.TYPE_FINAL_ASSESSMENT.equals(type)) {
                finalAssessmentNodes.add(node);
                continue;
            }

            if (RoadmapNode.TYPE_CERTIFICATION.equals(type) && hasFinalAssessment) {
                certNodes.add(node);
                continue;
            }

            if (RoadmapNode.TYPE_PROJECT.equals(type) && !projectDividerAdded) {
                items.add(RoadmapDisplayItem.divider("PROJECTS"));
                projectDividerAdded = true;
            } else if (RoadmapNode.TYPE_CERTIFICATION.equals(type) && !certDividerInPhase) {
                items.add(RoadmapDisplayItem.divider("CERTIFICATIONS"));
                certDividerInPhase = true;
            }

            items.add(RoadmapDisplayItem.nodeCard(node));
        }

        if (!finalAssessmentNodes.isEmpty()) {
            int faTotal = finalAssessmentNodes.size();
            int faDone  = 0;
            int faXp    = 0;
            for (RoadmapNode n : finalAssessmentNodes) {
                if (n.isCompleted()) faDone++;
                else faXp += n.getXpReward();
            }
            boolean faCompleted = faDone == faTotal;
            items.add(RoadmapDisplayItem.sectionHeader(
                    "Phase 4: Final Assessment", faTotal, faDone, faXp, faCompleted));
            for (RoadmapNode n : finalAssessmentNodes) {
                items.add(RoadmapDisplayItem.nodeCard(n));
            }
        }
        if (!certNodes.isEmpty()) {
            int cTotal = certNodes.size();
            int cDone  = 0;
            int cXp    = 0;
            for (RoadmapNode n : certNodes) {
                if (n.isCompleted()) cDone++;
                else cXp += n.getXpReward();
            }
            boolean cCompleted = cDone == cTotal;
            items.add(RoadmapDisplayItem.sectionHeader(
                    "Phase 5: Certifications", cTotal, cDone, cXp, cCompleted));
            for (RoadmapNode n : certNodes) {
                items.add(RoadmapDisplayItem.nodeCard(n));
            }
        }

        return items;
    }
}
