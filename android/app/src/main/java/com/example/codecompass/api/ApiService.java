package com.example.codecompass.api;

import com.example.codecompass.model.Badge;
import com.example.codecompass.model.ChangePasswordRequest;
import com.example.codecompass.model.ChangePasswordResponse;
import com.example.codecompass.model.MessageResponse;
import com.example.codecompass.model.ResendVerificationRequest;
import com.example.codecompass.model.ResendVerificationResponse;
import com.example.codecompass.model.AtsScoreRequest;
import com.example.codecompass.model.AtsScoreResponse;
import com.example.codecompass.model.CreateResumeRequest;
import com.example.codecompass.model.GenerateBulletsRequest;
import com.example.codecompass.model.GenerateBulletsResponse;
import com.example.codecompass.model.GenerateSummaryRequest;
import com.example.codecompass.model.GenerateSummaryResponse;
import com.example.codecompass.model.JobListing;
import com.example.codecompass.model.ParseJobRequest;
import com.example.codecompass.model.ParseJobResponse;
import com.example.codecompass.model.Resume;
import com.example.codecompass.model.ResumeListResponse;
import com.example.codecompass.model.ResumeMatchFromIdRequest;
import com.example.codecompass.model.ResumeMatchRequest;
import com.example.codecompass.model.SavedJob;
import com.example.codecompass.model.University;
import com.example.codecompass.model.UniversityRecommendation;
import com.example.codecompass.model.Certification;
import com.example.codecompass.model.ChatSession;
import com.example.codecompass.model.ChatSessionDetail;
import com.example.codecompass.model.PagedResponse;
import com.example.codecompass.model.CompleteOnboardingRequest;
import com.example.codecompass.model.CompleteOnboardingResponse;
import com.example.codecompass.model.CreateSessionRequest;
import com.example.codecompass.model.CreateSessionResponse;
import com.example.codecompass.model.GamificationProfile;
import com.example.codecompass.model.GoogleAuthRequest;
import com.example.codecompass.model.LeaderboardEntry;
import com.example.codecompass.model.UserBadge;
import com.example.codecompass.model.XPEvent;
import com.example.codecompass.model.GoogleAuthResponse;
import com.example.codecompass.model.LoginRequest;
import com.example.codecompass.model.LoginResponse;
import com.example.codecompass.model.RegisterRequest;
import com.example.codecompass.model.RegisterResponse;
import com.example.codecompass.model.Roadmap;
import com.example.codecompass.model.TokenRefreshRequest;
import com.example.codecompass.model.TokenRefreshResponse;
import com.example.codecompass.model.TrackCertRequest;
import com.example.codecompass.model.UpdateCertStatusRequest;
import com.example.codecompass.model.UserCertification;
import com.example.codecompass.model.roadmap.NodeResource;
import com.example.codecompass.model.roadmap.AssessmentResponse;
import com.example.codecompass.model.roadmap.QuizResult;
import com.example.codecompass.model.roadmap.SubmitAnswersRequest;
import com.example.codecompass.model.roadmap.UpdateNodeResponse;
import com.example.codecompass.model.roadmap.UpdateNodeStatusRequest;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.util.List;

import retrofit2.Call;
import retrofit2.http.Body;
import retrofit2.http.DELETE;
import retrofit2.http.GET;
import retrofit2.http.HTTP;
import retrofit2.http.Header;
import retrofit2.http.PATCH;
import retrofit2.http.POST;
import retrofit2.http.Path;
import retrofit2.http.Query;

public interface ApiService {

    // ── Authentication ────────────────────────────────────────────────────────

    @POST("auth/token/refresh/")
    Call<TokenRefreshResponse> refreshToken(@Body TokenRefreshRequest body);

    @POST("auth/login/")
    Call<LoginResponse> login(@Body LoginRequest body);

    @POST("auth/register/")
    Call<RegisterResponse> register(@Body RegisterRequest body);

    @POST("auth/google/")
    Call<GoogleAuthResponse> googleAuth(@Body GoogleAuthRequest body);

    @POST("auth/resend-verification/")
    Call<ResendVerificationResponse> resendVerification(@Body ResendVerificationRequest body);

    @POST("auth/send-change-password-otp/")
    Call<MessageResponse> sendChangePasswordOtp(@Header("Authorization") String bearerToken);

    @POST("auth/change-password/")
    Call<ChangePasswordResponse> changePassword(
            @Header("Authorization") String bearerToken,
            @Body ChangePasswordRequest body
    );

    @HTTP(method = "DELETE", path = "auth/delete-account/", hasBody = true)
    Call<Void> deleteAccount(
            @Header("Authorization") String bearerToken,
            @Body JsonObject body
    );

    // ── Onboarding ────────────────────────────────────────────────────────────

    @POST("onboarding/complete-from-chat/")
    Call<CompleteOnboardingResponse> completeOnboarding(
            @Header("Authorization") String bearerToken,
            @Body CompleteOnboardingRequest body
    );

    // ── Chat sessions ─────────────────────────────────────────────────────────

    @POST("chat/sessions/")
    Call<CreateSessionResponse> createChatSession(
            @Header("Authorization") String bearerToken,
            @Body CreateSessionRequest body
    );

    @GET("chat/sessions/")
    Call<PagedResponse<ChatSession>> getChatSessions(
            @Header("Authorization") String bearerToken
    );

    @GET("chat/sessions/{session_id}/")
    Call<ChatSessionDetail> getChatSessionDetail(
            @Header("Authorization") String bearerToken,
            @Path("session_id") String sessionId
    );

    @DELETE("chat/sessions/{session_id}/")
    Call<Void> deleteChatSession(
            @Header("Authorization") String bearerToken,
            @Path("session_id") String sessionId
    );

    // ── Gamification ──────────────────────────────────────────────────────────

    @GET("gamification/profile/")
    Call<GamificationProfile> getGamificationProfile(
            @Header("Authorization") String bearerToken
    );

    @GET("gamification/badges/")
    Call<PagedResponse<Badge>> getAllBadges(
            @Header("Authorization") String bearerToken
    );

    @GET("gamification/badges/earned/")
    Call<PagedResponse<UserBadge>> getEarnedBadges(
            @Header("Authorization") String bearerToken
    );

    @GET("gamification/xp-history/")
    Call<List<XPEvent>> getXPHistory(
            @Header("Authorization") String bearerToken
    );

    @GET("gamification/leaderboard/")
    Call<List<LeaderboardEntry>> getLeaderboard(
            @Header("Authorization") String bearerToken,
            @Query("period") String period
    );

    // ── Certifications ────────────────────────────────────────────────────────

    @GET("certifications/")
    Call<PagedResponse<Certification>> getCertifications(
            @Header("Authorization") String bearerToken,
            @Query("provider") String provider,
            @Query("track") String track,
            @Query("is_free") Boolean isFree,
            @Query("search") String search,
            @Query("page") Integer page
    );

    @GET("certifications/my/")
    Call<PagedResponse<UserCertification>> getMyCertifications(
            @Header("Authorization") String bearerToken
    );

    @POST("certifications/my/")
    Call<UserCertification> trackCertification(
            @Header("Authorization") String bearerToken,
            @Body TrackCertRequest body
    );

    @PATCH("certifications/my/{id}/")
    Call<UserCertification> updateCertTracking(
            @Header("Authorization") String bearerToken,
            @Path("id") int id,
            @Body UpdateCertStatusRequest body
    );

    @DELETE("certifications/my/{id}/")
    Call<Void> untrackCertification(
            @Header("Authorization") String bearerToken,
            @Path("id") int id
    );

    // ── Roadmaps ──────────────────────────────────────────────────────────────

    /** Returns either a JSON array or a paginated object with "results" key. */
    @GET("roadmaps/")
    Call<JsonElement> getRoadmaps(
            @Header("Authorization") String bearerToken
    );

    @GET("roadmaps/{id}/")
    Call<Roadmap> getRoadmap(
            @Header("Authorization") String bearerToken,
            @Path("id") int id
    );

    @POST("roadmaps/generate/")
    Call<Roadmap> generateRoadmap(
            @Header("Authorization") String bearerToken
    );

    // ── Roadmap node operations ───────────────────────────────────────────────

    @PATCH("roadmaps/{roadmapId}/nodes/{nodeId}/")
    Call<UpdateNodeResponse> updateNodeStatus(
            @Header("Authorization") String bearerToken,
            @Path("roadmapId") int roadmapId,
            @Path("nodeId") int nodeId,
            @Body UpdateNodeStatusRequest body
    );

    @POST("roadmaps/{roadmapId}/nodes/{nodeId}/fetch-resources/")
    Call<List<NodeResource>> fetchNodeResources(
            @Header("Authorization") String bearerToken,
            @Path("roadmapId") int roadmapId,
            @Path("nodeId") int nodeId
    );

    @POST("roadmaps/{roadmapId}/nodes/{nodeId}/resources/{resourceId}/unlock/")
    Call<Void> unlockVideoWatch(
            @Header("Authorization") String bearerToken,
            @Path("roadmapId") int roadmapId,
            @Path("nodeId") int nodeId,
            @Path("resourceId") int resourceId
    );

    @POST("roadmaps/{roadmapId}/nodes/{nodeId}/resources/{resourceId}/assessment/")
    Call<AssessmentResponse> generateQuiz(
            @Header("Authorization") String bearerToken,
            @Path("roadmapId") int roadmapId,
            @Path("nodeId") int nodeId,
            @Path("resourceId") int resourceId
    );

    @POST("roadmaps/{roadmapId}/nodes/{nodeId}/resources/{resourceId}/assessment/{sessionId}/submit/")
    Call<QuizResult> submitQuiz(
            @Header("Authorization") String bearerToken,
            @Path("roadmapId") int roadmapId,
            @Path("nodeId") int nodeId,
            @Path("resourceId") int resourceId,
            @Path("sessionId") int sessionId,
            @Body SubmitAnswersRequest body
    );

    @POST("roadmaps/{roadmapId}/final-assessment/")
    Call<AssessmentResponse> generateFinalAssessment(
            @Header("Authorization") String bearerToken,
            @Path("roadmapId") int roadmapId
    );

    @POST("roadmaps/{roadmapId}/final-assessment/{sessionId}/submit/")
    Call<QuizResult> submitFinalAssessment(
            @Header("Authorization") String bearerToken,
            @Path("roadmapId") int roadmapId,
            @Path("sessionId") int sessionId,
            @Body SubmitAnswersRequest body
    );

    // ── Roadmap AI edit proposals ─────────────────────────────────────────────

    // X-Chat-Session-Id header is sent by AIChatActivity when the mutation
    // originates from chat. Backend's FromRoadmapScopedSession permission
    // verifies the session is scope=roadmap before applying.
    @PATCH("roadmaps/{id}/edit/")
    Call<Void> editRoadmapMeta(
            @Header("Authorization") String bearerToken,
            @Header("X-Chat-Session-Id") String chatSessionId,
            @Path("id") int roadmapId,
            @Body JsonObject body
    );

    @PATCH("roadmaps/{id}/nodes/{nid}/edit/")
    Call<Void> editNodeContent(
            @Header("Authorization") String bearerToken,
            @Header("X-Chat-Session-Id") String chatSessionId,
            @Path("id") int roadmapId,
            @Path("nid") int nodeId,
            @Body JsonObject body
    );

    @PATCH("roadmaps/{id}/nodes/{nid}/replace/")
    Call<Void> replaceRoadmapNode(
            @Header("Authorization") String bearerToken,
            @Header("X-Chat-Session-Id") String chatSessionId,
            @Path("id") int roadmapId,
            @Path("nid") int nodeId,
            @Body JsonObject body
    );

    @POST("roadmaps/{id}/repair/")
    Call<Void> repairRoadmap(
            @Header("Authorization") String bearerToken,
            @Path("id") int roadmapId
    );

    @POST("roadmaps/{id}/fix-structure/")
    Call<Roadmap> fixRoadmapStructure(
            @Header("Authorization") String bearerToken,
            @Path("id") int roadmapId
    );

    // ── Roadmap path change / upskill ─────────────────────────────────────────

    @POST("roadmaps/switch/")
    Call<Void> switchRoadmap(
            @Header("Authorization") String bearerToken,
            @Header("X-Chat-Session-Id") String chatSessionId,
            @Body JsonObject body
    );

    @POST("roadmaps/upskill/")
    Call<Void> upskillRoadmap(
            @Header("Authorization") String bearerToken,
            @Header("X-Chat-Session-Id") String chatSessionId,
            @Body JsonObject body
    );

    // ── Jobs ──────────────────────────────────────────────────────────────────

    @GET("jobs/")
    Call<PagedResponse<JobListing>> getJobs(
            @Header("Authorization") String bearerToken,
            @Query("page") Integer page,
            @Query("search") String search,
            @Query("job_type") String jobType
    );

    @GET("jobs/recommended/")
    Call<List<JobListing>> getRecommendedJobs(
            @Header("Authorization") String bearerToken
    );

    @GET("jobs/saved/")
    Call<PagedResponse<SavedJob>> getSavedJobs(
            @Header("Authorization") String bearerToken
    );

    @POST("jobs/{id}/save/")
    Call<Void> saveJob(
            @Header("Authorization") String bearerToken,
            @Path("id") int jobId
    );

    @DELETE("jobs/{id}/save/")
    Call<Void> unsaveJob(
            @Header("Authorization") String bearerToken,
            @Path("id") int jobId
    );

    @POST("jobs/recommend-from-resume/")
    Call<List<JobListing>> recommendFromResume(
            @Header("Authorization") String bearerToken,
            @Body ResumeMatchRequest body
    );

    @POST("jobs/recommend-from-resume-id/")
    Call<List<JobListing>> recommendFromResumeId(
            @Header("Authorization") String bearerToken,
            @Body ResumeMatchFromIdRequest body
    );

    // ── Universities ──────────────────────────────────────────────────────────

    @GET("universities/")
    Call<PagedResponse<University>> getUniversities(
            @Header("Authorization") String bearerToken,
            @Query("search") String search,
            @Query("university_type") String universityType,
            @Query("page") Integer page
    );

    @GET("universities/recommendations/")
    Call<List<UniversityRecommendation>> getUniversityRecommendations(
            @Header("Authorization") String bearerToken
    );

    @GET("universities/{id}/")
    Call<University> getUniversityDetail(
            @Header("Authorization") String bearerToken,
            @Path("id") int id
    );

    // ── Resumes ──────────────────────────────────────────────────────────────

    @GET("resumes/")
    Call<ResumeListResponse> getResumes(
            @Header("Authorization") String bearerToken
    );

    @POST("resumes/")
    Call<Resume> createResume(
            @Header("Authorization") String bearerToken,
            @Body CreateResumeRequest body
    );

    @GET("resumes/{id}/")
    Call<Resume> getResume(
            @Header("Authorization") String bearerToken,
            @Path("id") int id
    );

    @PATCH("resumes/{id}/")
    Call<Resume> updateResume(
            @Header("Authorization") String bearerToken,
            @Path("id") int id,
            @Body JsonObject body
    );

    @DELETE("resumes/{id}/")
    Call<Void> deleteResume(
            @Header("Authorization") String bearerToken,
            @Path("id") int id
    );

    @POST("resumes/{id}/generate-bullets/")
    Call<GenerateBulletsResponse> generateBullets(
            @Header("Authorization") String bearerToken,
            @Path("id") int id,
            @Body GenerateBulletsRequest body
    );

    @POST("resumes/{id}/generate-summary/")
    Call<GenerateSummaryResponse> generateSummary(
            @Header("Authorization") String bearerToken,
            @Path("id") int id,
            @Body GenerateSummaryRequest body
    );

    @POST("resumes/parse-job/")
    Call<ParseJobResponse> parseJobDescription(
            @Header("Authorization") String bearerToken,
            @Body ParseJobRequest body
    );

    @POST("resumes/{id}/score-ats/")
    Call<AtsScoreResponse> scoreAts(
            @Header("Authorization") String bearerToken,
            @Path("id") int id,
            @Body AtsScoreRequest body
    );
}
