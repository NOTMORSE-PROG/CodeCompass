from rest_framework import serializers
from .models import University, CCSProgram


class CCSProgramSerializer(serializers.ModelSerializer):
    class Meta:
        model = CCSProgram
        fields = ['id', 'name', 'abbreviation', 'description', 'specializations',
                  'duration_years', 'has_board_exam', 'curriculum_url']


class UniversitySerializer(serializers.ModelSerializer):
    programs = CCSProgramSerializer(many=True, read_only=True)

    class Meta:
        model = University
        fields = ['id', 'name', 'abbreviation', 'university_type', 'region', 'province',
                  'city', 'website_url', 'logo_url', 'accreditation_level',
                  'ched_coe', 'ched_cod', 'tuition_range_min', 'tuition_range_max',
                  'programs']
