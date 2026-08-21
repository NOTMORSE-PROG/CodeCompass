"""Job listing models (cached from Careerjet / JSearch APIs)."""
from django.db import models


class JobListing(models.Model):
    class JobType(models.TextChoices):
        FULL_TIME = 'full_time', 'Full Time'
        PART_TIME = 'part_time', 'Part Time'
        INTERNSHIP = 'internship', 'Internship'
        FREELANCE = 'freelance', 'Freelance'
        REMOTE = 'remote', 'Remote'

    external_id = models.CharField(max_length=100, unique=True)
    title = models.CharField(max_length=300)
    company = models.CharField(max_length=200)
    location = models.CharField(max_length=200)
    job_type = models.CharField(max_length=15, choices=JobType.choices, blank=True)
    salary_range = models.CharField(max_length=100, blank=True)
    description = models.TextField()
    apply_url = models.URLField()
    required_skills = models.JSONField(default=list)
    experience_level = models.CharField(max_length=20, blank=True)
    is_philippines_based = models.BooleanField(default=True)
    fetched_at = models.DateTimeField(auto_now=True)
    expires_at = models.DateTimeField()
    is_active = models.BooleanField(default=True)

    class Meta:
        ordering = ['-fetched_at']

    def __str__(self):
        return f'{self.title} @ {self.company}'


class SavedJob(models.Model):
    user = models.ForeignKey('accounts.CustomUser', on_delete=models.CASCADE, related_name='saved_jobs')
    job = models.ForeignKey(JobListing, on_delete=models.CASCADE)
    saved_at = models.DateTimeField(auto_now_add=True)
    notes = models.TextField(blank=True)

    class Meta:
        unique_together = ('user', 'job')
