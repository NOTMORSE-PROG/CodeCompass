"""
Resume model — stores a user's resume data as structured JSON.
Supports multiple resumes per user with different templates.
"""
from django.db import models
from django.conf import settings


class Resume(models.Model):
    TEMPLATE_CLASSIC = 'classic'
    TEMPLATE_MODERN = 'modern'
    TEMPLATE_MINIMAL = 'minimal'
    TEMPLATE_CHOICES = [
        (TEMPLATE_CLASSIC, 'Classic'),
        (TEMPLATE_MODERN, 'Modern'),
        (TEMPLATE_MINIMAL, 'Minimal'),
    ]

    user = models.ForeignKey(
        settings.AUTH_USER_MODEL,
        on_delete=models.CASCADE,
        related_name='resumes',
    )
    title = models.CharField(max_length=200, default='My Resume')
    template_name = models.CharField(
        max_length=20,
        choices=TEMPLATE_CHOICES,
        default=TEMPLATE_MODERN,
    )
    # Structured resume content stored as JSON:
    # {
    #   "personalInfo": { name, email, phone, location, linkedin, github, website },
    #   "summary": "",
    #   "experience": [{ id, company, title, location, startDate, endDate, current, bullets[] }],
    #   "education": [{ id, school, degree, field, startDate, endDate, gpa, honors }],
    #   "skills": { technical[], soft[], tools[] },
    #   "projects": [{ id, name, description, tech[], link, startDate, endDate }],
    #   "certifications": [{ id, name, issuer, date, expiryDate, credentialId }]
    # }
    content = models.JSONField(default=dict)
    created_at = models.DateTimeField(auto_now_add=True)
    updated_at = models.DateTimeField(auto_now=True)

    class Meta:
        ordering = ['-updated_at']

    def __str__(self):
        return f'{self.user.email} — {self.title}'
