"""Mentor-student matching, sessions, and reviews."""
from django.db import models


class MentorshipRequest(models.Model):
    class Status(models.TextChoices):
        PENDING = 'pending', 'Pending'
        ACCEPTED = 'accepted', 'Accepted'
        DECLINED = 'declined', 'Declined'
        ENDED = 'ended', 'Ended'

    student = models.ForeignKey('accounts.CustomUser', on_delete=models.CASCADE, related_name='mentorship_requests')
    mentor = models.ForeignKey('accounts.CustomUser', on_delete=models.CASCADE, related_name='received_requests')
    status = models.CharField(max_length=10, choices=Status.choices, default=Status.PENDING)
    compatibility_score = models.DecimalField(max_digits=5, decimal_places=2, default=0.00)
    student_message = models.TextField(blank=True)
    mentor_response = models.TextField(blank=True)
    requested_at = models.DateTimeField(auto_now_add=True)
    responded_at = models.DateTimeField(null=True, blank=True)
    ended_at = models.DateTimeField(null=True, blank=True)

    class Meta:
        ordering = ['-requested_at']

    def __str__(self):
        return f'{self.student.email} → {self.mentor.email} ({self.status})'


class MentorshipSession(models.Model):
    class Status(models.TextChoices):
        SCHEDULED = 'scheduled', 'Scheduled'
        COMPLETED = 'completed', 'Completed'
        CANCELLED = 'cancelled', 'Cancelled'
        NO_SHOW = 'no_show', 'No Show'

    request = models.ForeignKey(MentorshipRequest, on_delete=models.CASCADE, related_name='sessions')
    scheduled_at = models.DateTimeField()
    duration_minutes = models.PositiveSmallIntegerField(default=60)
    status = models.CharField(max_length=12, choices=Status.choices, default=Status.SCHEDULED)
    meeting_link = models.URLField(blank=True)
    agenda = models.TextField(blank=True)
    notes = models.TextField(blank=True)
    completed_at = models.DateTimeField(null=True, blank=True)

    class Meta:
        ordering = ['-scheduled_at']


class MentorReview(models.Model):
    session = models.ForeignKey(MentorshipSession, on_delete=models.CASCADE, related_name='reviews')
    reviewer = models.ForeignKey('accounts.CustomUser', on_delete=models.CASCADE)
    rating = models.PositiveSmallIntegerField()
    review_text = models.TextField(blank=True)
    created_at = models.DateTimeField(auto_now_add=True)

    class Meta:
        unique_together = ('session', 'reviewer')
