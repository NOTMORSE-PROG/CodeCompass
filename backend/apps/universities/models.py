"""Philippine university and CCS program models."""
from django.db import models


class University(models.Model):
    class UniversityType(models.TextChoices):
        STATE = 'state', 'State University / SUC'
        PRIVATE = 'private', 'Private University'
        LOCAL = 'local', 'Local College'

    name = models.CharField(max_length=200)
    abbreviation = models.CharField(max_length=20, blank=True)
    university_type = models.CharField(max_length=10, choices=UniversityType.choices)
    region = models.CharField(max_length=50)
    province = models.CharField(max_length=100, blank=True)
    city = models.CharField(max_length=100)
    website_url = models.URLField(blank=True)
    logo_url = models.URLField(blank=True)

    # CHED recognition
    accreditation_level = models.PositiveSmallIntegerField(null=True, blank=True)
    ched_coe = models.BooleanField(default=False, verbose_name='CHED Center of Excellence')
    ched_cod = models.BooleanField(default=False, verbose_name='CHED Center of Development')

    # Tuition range (PHP per semester)
    tuition_range_min = models.PositiveIntegerField(null=True, blank=True)
    tuition_range_max = models.PositiveIntegerField(null=True, blank=True)

    is_active = models.BooleanField(default=True)

    class Meta:
        verbose_name_plural = 'Universities'
        ordering = ['name']

    def __str__(self):
        return f'{self.abbreviation or self.name} — {self.city}'


class CCSProgram(models.Model):
    """A CCS program offered by a university."""
    university = models.ForeignKey(University, on_delete=models.CASCADE, related_name='programs')
    name = models.CharField(max_length=200)
    abbreviation = models.CharField(max_length=15)
    description = models.TextField(blank=True)
    specializations = models.JSONField(default=list)
    duration_years = models.PositiveSmallIntegerField(default=4)
    has_board_exam = models.BooleanField(default=False)
    curriculum_url = models.URLField(blank=True)

    class Meta:
        ordering = ['abbreviation']

    def __str__(self):
        return f'{self.abbreviation} @ {self.university.abbreviation}'
