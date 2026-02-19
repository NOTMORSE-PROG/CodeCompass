"""IT certification models and user tracker."""
from django.db import models


class Certification(models.Model):
    class Provider(models.TextChoices):
        TESDA = 'tesda', 'TESDA'
        GOOGLE = 'google', 'Google'
        AWS = 'aws', 'Amazon Web Services'
        COMPTIA = 'comptia', 'CompTIA'
        MICROSOFT = 'microsoft', 'Microsoft'
        CISCO = 'cisco', 'Cisco'
        META = 'meta', 'Meta'
        ORACLE = 'oracle', 'Oracle'
        OTHER = 'other', 'Other'

    class Level(models.TextChoices):
        BEGINNER = 'beginner', 'Beginner'
        INTERMEDIATE = 'intermediate', 'Intermediate'
        ADVANCED = 'advanced', 'Advanced'
        EXPERT = 'expert', 'Expert'

    name = models.CharField(max_length=200)
    abbreviation = models.CharField(max_length=30, blank=True)
    provider = models.CharField(max_length=15, choices=Provider.choices)
    level = models.CharField(max_length=15, choices=Level.choices)
    description = models.TextField()
    relevant_skills = models.JSONField(default=list)
    career_paths = models.JSONField(default=list)
    exam_url = models.URLField(blank=True)
    study_guide_url = models.URLField(blank=True)
    tesda_nc_level = models.CharField(max_length=10, blank=True)
    is_free = models.BooleanField(default=False)
    estimated_cost_php = models.PositiveIntegerField(null=True, blank=True)
    estimated_study_hours = models.PositiveSmallIntegerField(null=True, blank=True)
    is_active = models.BooleanField(default=True)

    class Meta:
        ordering = ['provider', 'level', 'name']

    def __str__(self):
        return f'{self.provider.upper()} — {self.name}'


class UserCertification(models.Model):
    class Status(models.TextChoices):
        INTERESTED = 'interested', 'Interested'
        STUDYING = 'studying', 'Currently Studying'
        PASSED = 'passed', 'Passed / Earned'
        EXPIRED = 'expired', 'Expired'

    user = models.ForeignKey('accounts.CustomUser', on_delete=models.CASCADE, related_name='certifications')
    certification = models.ForeignKey(Certification, on_delete=models.CASCADE)
    status = models.CharField(max_length=12, choices=Status.choices, default=Status.INTERESTED)
    started_studying_at = models.DateField(null=True, blank=True)
    earned_at = models.DateField(null=True, blank=True)
    expires_at = models.DateField(null=True, blank=True)
    certificate_url = models.URLField(blank=True)
    notes = models.TextField(blank=True)

    class Meta:
        unique_together = ('user', 'certification')
