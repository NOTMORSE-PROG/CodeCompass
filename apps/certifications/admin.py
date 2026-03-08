from django.contrib import admin
from .models import Certification, UserCertification


@admin.register(Certification)
class CertificationAdmin(admin.ModelAdmin):
    list_display = ['name', 'abbreviation', 'provider', 'level', 'is_free', 'is_active']
    list_filter = ['provider', 'level', 'is_free', 'is_active']
    list_editable = ['is_active']
    search_fields = ['name', 'abbreviation', 'description']
    ordering = ['provider', 'level', 'name']


@admin.register(UserCertification)
class UserCertificationAdmin(admin.ModelAdmin):
    list_display = ['user', 'certification', 'status', 'earned_at']
    list_filter = ['status', 'certification__provider']
    search_fields = ['user__email', 'certification__name']
    raw_id_fields = ['user', 'certification']
