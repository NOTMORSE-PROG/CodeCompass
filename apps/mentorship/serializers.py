from rest_framework import serializers
from .models import MentorshipRequest, MentorshipSession, MentorReview


class MentorshipRequestSerializer(serializers.ModelSerializer):
    student_name = serializers.SerializerMethodField()
    mentor_name = serializers.SerializerMethodField()

    class Meta:
        model = MentorshipRequest
        fields = ['id', 'student', 'mentor', 'student_name', 'mentor_name',
                  'status', 'compatibility_score', 'student_message', 'mentor_response',
                  'requested_at', 'responded_at', 'ended_at']
        read_only_fields = ['id', 'student', 'status', 'compatibility_score',
                             'requested_at', 'responded_at', 'ended_at']

    def get_student_name(self, obj):
        return obj.student.get_full_name()

    def get_mentor_name(self, obj):
        return obj.mentor.get_full_name()


class MentorshipSessionSerializer(serializers.ModelSerializer):
    class Meta:
        model = MentorshipSession
        fields = ['id', 'request', 'scheduled_at', 'duration_minutes', 'status',
                  'meeting_link', 'agenda', 'notes', 'completed_at']
        read_only_fields = ['id', 'completed_at']


class MentorReviewSerializer(serializers.ModelSerializer):
    class Meta:
        model = MentorReview
        fields = ['id', 'session', 'rating', 'review_text', 'created_at']
        read_only_fields = ['id', 'reviewer', 'created_at']
