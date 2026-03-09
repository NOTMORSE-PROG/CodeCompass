"""Roadmap, node, and resource models."""
from django.db import models


class Roadmap(models.Model):
    class Status(models.TextChoices):
        GENERATING = 'generating', 'Generating'
        ACTIVE = 'active', 'Active'
        COMPLETED = 'completed', 'Completed'
        ARCHIVED = 'archived', 'Archived'

    user = models.ForeignKey('accounts.CustomUser', on_delete=models.CASCADE, related_name='roadmaps')
    title = models.CharField(max_length=200)
    career_path = models.CharField(max_length=100)
    description = models.TextField(blank=True)
    status = models.CharField(max_length=15, choices=Status.choices, default=Status.GENERATING)
    ai_model_used = models.CharField(max_length=50, default='llama-3.3-70b-versatile')
    # React Flow node positions and edge data stored as JSON
    flow_layout = models.JSONField(default=dict)
    completion_percentage = models.DecimalField(max_digits=5, decimal_places=2, default=0.00)
    estimated_weeks = models.PositiveSmallIntegerField(default=12)
    is_primary = models.BooleanField(default=True)
    generated_at = models.DateTimeField(null=True, blank=True)
    created_at = models.DateTimeField(auto_now_add=True)
    updated_at = models.DateTimeField(auto_now=True)

    class Meta:
        ordering = ['-created_at']

    def __str__(self):
        return f'{self.title} ({self.user.email})'

    def recalculate_completion(self):
        """Recalculate and save completion percentage based on completed nodes."""
        total = self.nodes.count()
        if total == 0:
            self.completion_percentage = 0
        else:
            completed = self.nodes.filter(status='completed').count()
            self.completion_percentage = round((completed / total) * 100, 2)
        self.save(update_fields=['completion_percentage'])


class RoadmapNode(models.Model):
    class NodeType(models.TextChoices):
        MILESTONE = 'milestone', 'Milestone'
        SKILL = 'skill', 'Skill to Learn'
        PROJECT = 'project', 'Hands-on Project'
        ASSESSMENT = 'assessment', 'Self-Assessment'
        CERTIFICATION = 'certification', 'Certification Goal'

    class Status(models.TextChoices):
        LOCKED = 'locked', 'Locked'
        AVAILABLE = 'available', 'Available'
        IN_PROGRESS = 'in_progress', 'In Progress'
        COMPLETED = 'completed', 'Completed'

    roadmap = models.ForeignKey(Roadmap, on_delete=models.CASCADE, related_name='nodes')
    parent_node = models.ForeignKey(
        'self', null=True, blank=True, on_delete=models.SET_NULL, related_name='children'
    )
    node_type = models.CharField(max_length=15, choices=NodeType.choices)
    title = models.CharField(max_length=200)
    description = models.TextField()
    skill_slug = models.SlugField(max_length=100, blank=True)
    # React Flow canvas coordinates
    position_x = models.FloatField(default=0)
    position_y = models.FloatField(default=0)
    node_order = models.PositiveSmallIntegerField(default=0)
    estimated_hours = models.PositiveSmallIntegerField(default=5)
    difficulty = models.PositiveSmallIntegerField(default=1)
    status = models.CharField(max_length=15, choices=Status.choices, default=Status.LOCKED)
    xp_reward = models.PositiveSmallIntegerField(default=50)
    completed_at = models.DateTimeField(null=True, blank=True)

    class Meta:
        ordering = ['node_order']

    def __str__(self):
        return f'{self.title} ({self.node_type})'


class NodeResource(models.Model):
    class ResourceType(models.TextChoices):
        YOUTUBE_VIDEO = 'youtube_video', 'YouTube Video'
        YOUTUBE_PLAYLIST = 'youtube_playlist', 'YouTube Playlist'
        ARTICLE = 'article', 'Article / Blog'
        DOCUMENTATION = 'documentation', 'Official Documentation'
        GITHUB_REPO = 'github_repo', 'GitHub Repository'
        COURSE = 'course', 'Online Course'

    node = models.ForeignKey(RoadmapNode, on_delete=models.CASCADE, related_name='resources')
    resource_type = models.CharField(max_length=20, choices=ResourceType.choices)
    title = models.CharField(max_length=300)
    url = models.URLField()
    description = models.TextField(blank=True)
    thumbnail_url = models.URLField(blank=True)
    duration_minutes = models.PositiveSmallIntegerField(null=True, blank=True)
    is_free = models.BooleanField(default=True)
    language = models.CharField(max_length=10, default='en')
    order = models.PositiveSmallIntegerField(default=0)
    youtube_video_id = models.CharField(max_length=20, blank=True)
    youtube_channel = models.CharField(max_length=100, blank=True)

    class Meta:
        ordering = ['order']

    def __str__(self):
        return self.title


class NodeProgress(models.Model):
    """Tracks a student's progress on individual nodes."""
    user = models.ForeignKey('accounts.CustomUser', on_delete=models.CASCADE)
    node = models.ForeignKey(RoadmapNode, on_delete=models.CASCADE)
    started_at = models.DateTimeField(auto_now_add=True)
    completed_at = models.DateTimeField(null=True, blank=True)
    notes = models.TextField(blank=True)
    time_spent_minutes = models.PositiveIntegerField(default=0)

    class Meta:
        unique_together = ('user', 'node')


class AssessmentSession(models.Model):
    """
    Stores one attempt at the AI-generated quiz for a YouTube resource.
    questions_json holds correct answers server-side — never sent to the client.
    """
    user = models.ForeignKey(
        'accounts.CustomUser', on_delete=models.CASCADE,
        related_name='assessment_sessions'
    )
    resource = models.ForeignKey(
        NodeResource, on_delete=models.CASCADE,
        related_name='assessment_sessions'
    )
    questions_json = models.JSONField()  # full questions WITH correct answers
    passed = models.BooleanField(null=True)   # null = not yet submitted
    score = models.IntegerField(null=True)    # 0-100
    created_at = models.DateTimeField(auto_now_add=True)

    class Meta:
        ordering = ['-created_at']

    def __str__(self):
        return f'AssessmentSession(user={self.user_id}, resource={self.resource_id}, passed={self.passed})'
