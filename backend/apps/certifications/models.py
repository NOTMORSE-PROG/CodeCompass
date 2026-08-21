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
        FREECODECAMP = 'freecodecamp', 'freeCodeCamp'
        IBM = 'ibm', 'IBM'
        MONGODB = 'mongodb', 'MongoDB'
        GITHUB = 'github', 'GitHub'
        KAGGLE = 'kaggle', 'Kaggle'
        HARVARD = 'harvard', 'Harvard / CS50'
        HUBSPOT = 'hubspot', 'HubSpot'
        SALESFORCE = 'salesforce', 'Salesforce'
        POSTMAN = 'postman', 'Postman'
        SCRUM = 'scrum', 'SCRUMstudy / CertiProf'
        LINUX_FOUNDATION = 'linux_foundation', 'Linux Foundation'
        FORTINET = 'fortinet', 'Fortinet'
        HACKERRANK = 'hackerrank', 'HackerRank'
        OTHER = 'other', 'Other'

    class Level(models.TextChoices):
        BEGINNER = 'beginner', 'Beginner'
        INTERMEDIATE = 'intermediate', 'Intermediate'
        ADVANCED = 'advanced', 'Advanced'
        EXPERT = 'expert', 'Expert'

    class Track(models.TextChoices):
        WEB = 'web', 'Web Development'
        BACKEND = 'backend', 'Backend Development'
        DATA = 'data', 'Data Science / AI'
        CYBER = 'cyber', 'Cybersecurity'
        CLOUD = 'cloud', 'Cloud / DevOps'
        MOBILE = 'mobile', 'Mobile Development'
        NETWORKING = 'networking', 'Networking / Linux'
        ALGORITHMS = 'algorithms', 'Algorithms / CS Fundamentals'
        MARKETING = 'marketing', 'Digital Marketing'
        AGILE = 'agile', 'Agile / Project Management'
        GENERAL = 'general', 'General IT'

    name = models.CharField(max_length=200)
    abbreviation = models.CharField(max_length=30, blank=True)
    provider = models.CharField(max_length=20, choices=Provider.choices)
    level = models.CharField(max_length=15, choices=Level.choices)
    track = models.CharField(max_length=15, choices=Track.choices, default=Track.GENERAL)
    description = models.TextField()
    relevant_skills = models.JSONField(default=list)
    career_paths = models.JSONField(default=list)
    exam_url = models.URLField(blank=True)
    study_guide_url = models.URLField(blank=True)
    tesda_nc_level = models.CharField(max_length=10, blank=True)
    is_free = models.BooleanField(default=False)
    optional_paid_upgrade = models.TextField(
        blank=True,
        help_text='Optional paid upgrade path (e.g. exam fee, Coursera certificate)',
    )
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
