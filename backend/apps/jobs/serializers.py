from rest_framework import serializers
from .models import JobListing, SavedJob


class JobListingSerializer(serializers.ModelSerializer):
    class Meta:
        model = JobListing
        fields = ['id', 'title', 'company', 'location', 'job_type', 'salary_range',
                  'description', 'apply_url', 'required_skills', 'experience_level',
                  'is_philippines_based', 'fetched_at']


class SavedJobSerializer(serializers.ModelSerializer):
    job = JobListingSerializer(read_only=True)

    class Meta:
        model = SavedJob
        fields = ['id', 'job', 'saved_at', 'notes']
