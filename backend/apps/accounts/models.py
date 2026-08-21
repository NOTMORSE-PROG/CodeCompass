"""
User models for CodeCompass.
CustomUser is the single source of truth for all user types.
StudentProfile extends it with student-specific data.
"""
from django.contrib.auth.models import AbstractUser
from django.db import models


class CustomUser(AbstractUser):
    """
    Extended user model with role-based access.
    Email is used as the login identifier instead of username.
    """

    class Role(models.TextChoices):
        INCOMING_STUDENT = 'incoming_student', 'Incoming Student'
        UNDERGRADUATE = 'undergraduate', 'Undergraduate Student'
        ADMIN = 'admin', 'Admin'

    email = models.EmailField(unique=True)
    role = models.CharField(
        max_length=20,
        choices=Role.choices,
        null=True,
        blank=True,
        default=None,
    )
    avatar = models.ImageField(upload_to='avatars/', null=True, blank=True)
    is_onboarded = models.BooleanField(default=False)
    google_id = models.CharField(max_length=255, null=True, blank=True, db_index=True)
    email_verified = models.BooleanField(default=False)
    created_at = models.DateTimeField(auto_now_add=True)
    updated_at = models.DateTimeField(auto_now=True)

    # Use email as the login field
    USERNAME_FIELD = 'email'
    REQUIRED_FIELDS = ['username', 'first_name', 'last_name']

    class Meta:
        db_table = 'accounts_user'
        verbose_name = 'User'
        verbose_name_plural = 'Users'

    def __str__(self):
        return f'{self.get_full_name()} ({self.email}) — {self.get_role_display()}'

    @property
    def is_student(self):
        return self.role in (self.Role.INCOMING_STUDENT, self.Role.UNDERGRADUATE)

    @property
    def is_admin_user(self):
        return self.role == self.Role.ADMIN or self.is_superuser


class StudentProfile(models.Model):
    """
    Extended profile for incoming and undergraduate students.
    Created automatically via signal when a student user is created.
    """

    class YearLevel(models.TextChoices):
        INCOMING = 'incoming', 'Incoming / Pre-College'
        FIRST = '1st', '1st Year'
        SECOND = '2nd', '2nd Year'
        THIRD = '3rd', '3rd Year'
        FOURTH = '4th', '4th Year'
        FIFTH = '5th', '5th Year or Above'

    class Program(models.TextChoices):
        BSCS = 'BSCS', 'BS Computer Science'
        BSIT = 'BSIT', 'BS Information Technology'
        BSIS = 'BSIS', 'BS Information Systems'
        BSCE = 'BSCE', 'BS Computer Engineering'
        ACT = 'ACT', 'Associate in Computer Technology'
        UNDECIDED = 'undecided', 'Undecided'

    user = models.OneToOneField(
        CustomUser,
        on_delete=models.CASCADE,
        related_name='student_profile',
    )
    year_level = models.CharField(
        max_length=10,
        choices=YearLevel.choices,
        default=YearLevel.INCOMING,
    )
    program = models.CharField(
        max_length=10,
        choices=Program.choices,
        default=Program.UNDECIDED,
    )
    university = models.ForeignKey(
        'universities.University',
        null=True,
        blank=True,
        on_delete=models.SET_NULL,
        related_name='students',
    )
    bio = models.TextField(blank=True)
    linkedin_url = models.URLField(blank=True)
    github_url = models.URLField(blank=True)

    # Skills stored as a JSON list of slug strings e.g. ["python", "html", "css"]
    current_skills = models.JSONField(default=list)
    target_career = models.CharField(max_length=100, blank=True)

    # Gamification
    xp_total = models.PositiveIntegerField(default=0)
    streak_count = models.PositiveIntegerField(default=0)
    last_active_date = models.DateField(null=True, blank=True)

    created_at = models.DateTimeField(auto_now_add=True)
    updated_at = models.DateTimeField(auto_now=True)

    class Meta:
        db_table = 'accounts_student_profile'

    def __str__(self):
        return f'StudentProfile({self.user.email}, {self.year_level})'


