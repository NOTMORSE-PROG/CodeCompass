from django.contrib import admin
from .models import Resume


@admin.register(Resume)
class ResumeAdmin(admin.ModelAdmin):
    list_display = ['id', 'user', 'title', 'template_name', 'created_at', 'updated_at']
    list_filter = ['template_name']
    search_fields = ['user__email', 'title']
    readonly_fields = ['created_at', 'updated_at']
