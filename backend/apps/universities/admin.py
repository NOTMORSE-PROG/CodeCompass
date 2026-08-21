from django.contrib import admin
from .models import University, CCSProgram


class CCSProgramInline(admin.TabularInline):
    model = CCSProgram
    extra = 1


@admin.register(University)
class UniversityAdmin(admin.ModelAdmin):
    list_display = ['name', 'abbreviation', 'city', 'region', 'ched_coe', 'ched_cod', 'is_active']
    list_filter = ['university_type', 'region', 'ched_coe', 'ched_cod', 'is_active']
    search_fields = ['name', 'abbreviation', 'city']
    inlines = [CCSProgramInline]
