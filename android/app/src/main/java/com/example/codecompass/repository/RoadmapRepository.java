package com.example.codecompass.repository;

import com.example.codecompass.api.ApiClient;
import com.example.codecompass.api.ApiService;
import com.example.codecompass.api.TokenManager;
import com.example.codecompass.model.Roadmap;
import com.example.codecompass.model.roadmap.AssessmentResponse;
import com.example.codecompass.model.roadmap.NodeResource;
import com.example.codecompass.model.roadmap.QuizResult;
import com.example.codecompass.model.roadmap.SubmitAnswersRequest;
import com.example.codecompass.model.roadmap.UpdateNodeResponse;
import com.example.codecompass.model.roadmap.UpdateNodeStatusRequest;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.util.List;
import java.util.Map;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class RoadmapRepository {

    public interface Callback1<T> {
        void onSuccess(T data);
        void onError(String message);
    }

    private final ApiService api;
    private final android.content.Context context;

    public RoadmapRepository(android.content.Context context) {
        this.context = context.getApplicationContext();
        this.api = ApiClient.getService();
    }

    // ── Load roadmap ──────────────────────────────────────────────────────────

    /**
     * Fetches the list of roadmaps, takes the first id, then fetches full detail.
     * Uses the passed roadmapId if >= 0, otherwise discovers it from the list.
     */
    public void loadRoadmap(int roadmapId, Callback1<Roadmap> cb) {
        if (roadmapId >= 0) {
            fetchDetail(roadmapId, cb);
        } else {
            discoverAndFetch(cb);
        }
    }

    private void discoverAndFetch(Callback1<Roadmap> cb) {
        api.getRoadmaps(bearer()).enqueue(new Callback<JsonElement>() {
            @Override
            public void onResponse(Call<JsonElement> call, Response<JsonElement> response) {
                if (!response.isSuccessful() || response.body() == null) {
                    cb.onError("Failed to load roadmap list");
                    return;
                }
                int id = extractFirstId(response.body());
                if (id >= 0) {
                    fetchDetail(id, cb);
                } else {
                    cb.onSuccess(null); // No roadmap yet
                }
            }

            @Override
            public void onFailure(Call<JsonElement> call, Throwable t) {
                cb.onError(t.getMessage());
            }
        });
    }

    private void fetchDetail(int id, Callback1<Roadmap> cb) {
        api.getRoadmap(bearer(), id).enqueue(new Callback<Roadmap>() {
            @Override
            public void onResponse(Call<Roadmap> call, Response<Roadmap> response) {
                if (response.isSuccessful() && response.body() != null) {
                    cb.onSuccess(response.body());
                } else {
                    cb.onError("Error " + response.code());
                }
            }

            @Override
            public void onFailure(Call<Roadmap> call, Throwable t) {
                cb.onError(t.getMessage());
            }
        });
    }

    // ── Node status update ────────────────────────────────────────────────────

    public void updateNodeStatus(int roadmapId, int nodeId, String newStatus,
                                  Callback1<UpdateNodeResponse> cb) {
        api.updateNodeStatus(bearer(), roadmapId, nodeId,
                new UpdateNodeStatusRequest(newStatus))
                .enqueue(new Callback<UpdateNodeResponse>() {
                    @Override
                    public void onResponse(Call<UpdateNodeResponse> call,
                                           Response<UpdateNodeResponse> response) {
                        if (response.isSuccessful() && response.body() != null) {
                            cb.onSuccess(response.body());
                        } else {
                            cb.onError("Error " + response.code());
                        }
                    }

                    @Override
                    public void onFailure(Call<UpdateNodeResponse> call, Throwable t) {
                        cb.onError(t.getMessage());
                    }
                });
    }

    // ── Fetch resources ───────────────────────────────────────────────────────

    public void fetchNodeResources(int roadmapId, int nodeId,
                                    Callback1<List<NodeResource>> cb) {
        api.fetchNodeResources(bearer(), roadmapId, nodeId)
                .enqueue(new Callback<List<NodeResource>>() {
                    @Override
                    public void onResponse(Call<List<NodeResource>> call,
                                           Response<List<NodeResource>> response) {
                        if (response.isSuccessful() && response.body() != null) {
                            cb.onSuccess(response.body());
                        } else {
                            cb.onError("Error " + response.code());
                        }
                    }

                    @Override
                    public void onFailure(Call<List<NodeResource>> call, Throwable t) {
                        cb.onError(t.getMessage());
                    }
                });
    }

    // ── Assessment (quiz) ─────────────────────────────────────────────────────

    public void generateQuiz(int roadmapId, int nodeId, int resourceId,
                              Callback1<AssessmentResponse> cb) {
        api.generateQuiz(bearer(), roadmapId, nodeId, resourceId)
                .enqueue(new Callback<AssessmentResponse>() {
                    @Override
                    public void onResponse(Call<AssessmentResponse> call,
                                           Response<AssessmentResponse> response) {
                        if (response.isSuccessful() && response.body() != null) {
                            cb.onSuccess(response.body());
                        } else {
                            cb.onError("Error " + response.code());
                        }
                    }

                    @Override
                    public void onFailure(Call<AssessmentResponse> call, Throwable t) {
                        cb.onError(t.getMessage());
                    }
                });
    }

    public void submitQuiz(int roadmapId, int nodeId, int resourceId, int sessionId,
                           Map<String, String> answers, Callback1<QuizResult> cb) {
        api.submitQuiz(bearer(), roadmapId, nodeId, resourceId, sessionId,
                new SubmitAnswersRequest(answers))
                .enqueue(new Callback<QuizResult>() {
                    @Override
                    public void onResponse(Call<QuizResult> call,
                                           Response<QuizResult> response) {
                        if (response.isSuccessful() && response.body() != null) {
                            cb.onSuccess(response.body());
                        } else {
                            cb.onError("Error " + response.code());
                        }
                    }

                    @Override
                    public void onFailure(Call<QuizResult> call, Throwable t) {
                        cb.onError(t.getMessage());
                    }
                });
    }

    // ── Final Assessment (roadmap-wide 10-question quiz) ──────────────────────

    public void generateFinalAssessment(int roadmapId, Callback1<AssessmentResponse> cb) {
        api.generateFinalAssessment(bearer(), roadmapId)
                .enqueue(new Callback<AssessmentResponse>() {
                    @Override
                    public void onResponse(Call<AssessmentResponse> call,
                                           Response<AssessmentResponse> response) {
                        if (response.isSuccessful() && response.body() != null) {
                            cb.onSuccess(response.body());
                        } else {
                            cb.onError("Error " + response.code());
                        }
                    }

                    @Override
                    public void onFailure(Call<AssessmentResponse> call, Throwable t) {
                        cb.onError(t.getMessage());
                    }
                });
    }

    public void submitFinalAssessment(int roadmapId, int sessionId,
                                      Map<String, String> answers, Callback1<QuizResult> cb) {
        api.submitFinalAssessment(bearer(), roadmapId, sessionId,
                new SubmitAnswersRequest(answers))
                .enqueue(new Callback<QuizResult>() {
                    @Override
                    public void onResponse(Call<QuizResult> call,
                                           Response<QuizResult> response) {
                        if (response.isSuccessful() && response.body() != null) {
                            cb.onSuccess(response.body());
                        } else {
                            cb.onError("Error " + response.code());
                        }
                    }

                    @Override
                    public void onFailure(Call<QuizResult> call, Throwable t) {
                        cb.onError(t.getMessage());
                    }
                });
    }

    // ── Fix structure + load (called on initial roadmap open) ─────────────────

    /**
     * Calls fix-structure (which reorders nodes: skills before projects/certs),
     * then returns the updated roadmap. Falls back to a normal load if fix-structure fails.
     * If roadmapId is unknown (-1), discovers it first.
     */
    public void fixStructureAndLoad(int roadmapId, Callback1<Roadmap> cb) {
        if (roadmapId >= 0) {
            fixThenLoad(roadmapId, cb);
        } else {
            api.getRoadmaps(bearer()).enqueue(new Callback<JsonElement>() {
                @Override
                public void onResponse(Call<JsonElement> call, Response<JsonElement> response) {
                    if (!response.isSuccessful() || response.body() == null) {
                        cb.onError("Failed to load roadmap list");
                        return;
                    }
                    int id = extractFirstId(response.body());
                    if (id >= 0) {
                        fixThenLoad(id, cb);
                    } else {
                        cb.onSuccess(null); // No roadmap yet
                    }
                }

                @Override
                public void onFailure(Call<JsonElement> call, Throwable t) {
                    cb.onError(t.getMessage());
                }
            });
        }
    }

    private void fixThenLoad(int id, Callback1<Roadmap> cb) {
        api.fixRoadmapStructure(bearer(), id).enqueue(new Callback<Roadmap>() {
            @Override
            public void onResponse(Call<Roadmap> call, Response<Roadmap> response) {
                if (response.isSuccessful() && response.body() != null) {
                    cb.onSuccess(response.body());
                } else {
                    fetchDetail(id, cb); // fallback
                }
            }

            @Override
            public void onFailure(Call<Roadmap> call, Throwable t) {
                fetchDetail(id, cb); // fallback on network error
            }
        });
    }

    // ── Repair ────────────────────────────────────────────────────────────────

    public void repairRoadmap(int roadmapId, Callback1<Void> cb) {
        api.repairRoadmap(bearer(), roadmapId).enqueue(new Callback<Void>() {
            @Override
            public void onResponse(Call<Void> call, Response<Void> response) {
                if (response.isSuccessful()) cb.onSuccess(null);
                else cb.onError("Error " + response.code());
            }

            @Override
            public void onFailure(Call<Void> call, Throwable t) {
                cb.onError(t.getMessage());
            }
        });
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private String bearer() {
        return TokenManager.getBearerToken(context);
    }

    private int extractFirstId(JsonElement body) {
        try {
            JsonArray arr;
            if (body.isJsonArray()) {
                arr = body.getAsJsonArray();
            } else {
                JsonObject obj = body.getAsJsonObject();
                if (!obj.has("results")) return -1;
                arr = obj.getAsJsonArray("results");
            }
            if (arr.size() == 0) return -1;
            return arr.get(0).getAsJsonObject().get("id").getAsInt();
        } catch (Exception e) {
            return -1;
        }
    }
}
