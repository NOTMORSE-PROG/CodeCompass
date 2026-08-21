"""Learning resource model (YouTube videos, articles, etc.)."""
from django.db import models


class Resource(models.Model):
    class ResourceType(models.TextChoices):
        YOUTUBE_VIDEO = 'youtube_video', 'YouTube Video'
        YOUTUBE_PLAYLIST = 'youtube_playlist', 'YouTube Playlist'
        ARTICLE = 'article', 'Article / Blog'
        DOCUMENTATION = 'documentation', 'Official Documentation'
        GITHUB_REPO = 'github_repo', 'GitHub Repository'
        COURSE = 'course', 'Online Course'

    resource_type = models.CharField(max_length=20, choices=ResourceType.choices)
    title = models.CharField(max_length=300)
    url = models.URLField()
    description = models.TextField(blank=True)
    thumbnail_url = models.URLField(blank=True)
    duration_minutes = models.PositiveSmallIntegerField(null=True, blank=True)
    is_free = models.BooleanField(default=True)
    language = models.CharField(max_length=10, default='en')
    skill_slug = models.SlugField(max_length=100, blank=True)
    youtube_video_id = models.CharField(max_length=20, blank=True)
    youtube_channel = models.CharField(max_length=100, blank=True)
    view_count = models.PositiveBigIntegerField(default=0)
    created_at = models.DateTimeField(auto_now_add=True)

    class Meta:
        ordering = ['-view_count']

    def __str__(self):
        return self.title
