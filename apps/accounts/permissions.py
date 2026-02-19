"""
Role-based permission classes for DRF views.
Used as permission_classes=[IsStudent] etc. on view classes.
"""
from rest_framework.permissions import BasePermission

from .models import CustomUser


class IsStudent(BasePermission):
    """Allow incoming or undergraduate students."""
    def has_permission(self, request, view):
        return (
            request.user.is_authenticated
            and request.user.role in (
                CustomUser.Role.INCOMING_STUDENT,
                CustomUser.Role.UNDERGRADUATE,
            )
        )


class IsIncomingStudent(BasePermission):
    """Allow only incoming (pre-college) students."""
    def has_permission(self, request, view):
        return (
            request.user.is_authenticated
            and request.user.role == CustomUser.Role.INCOMING_STUDENT
        )


class IsUndergraduate(BasePermission):
    """Allow only currently enrolled undergraduate students."""
    def has_permission(self, request, view):
        return (
            request.user.is_authenticated
            and request.user.role == CustomUser.Role.UNDERGRADUATE
        )


class IsMentor(BasePermission):
    """Allow any mentor (verified or not)."""
    def has_permission(self, request, view):
        return (
            request.user.is_authenticated
            and request.user.role == CustomUser.Role.MENTOR
        )


class IsVerifiedMentor(BasePermission):
    """Allow only admin-verified mentors."""
    def has_permission(self, request, view):
        return (
            request.user.is_authenticated
            and request.user.role == CustomUser.Role.MENTOR
            and hasattr(request.user, 'mentor_profile')
            and request.user.mentor_profile.is_verified
        )


class IsAdminUser(BasePermission):
    """Allow admin role or Django superuser."""
    def has_permission(self, request, view):
        return (
            request.user.is_authenticated
            and (
                request.user.role == CustomUser.Role.ADMIN
                or request.user.is_superuser
            )
        )


class IsStudentOrAdmin(BasePermission):
    """Allow students or admins."""
    def has_permission(self, request, view):
        return (
            request.user.is_authenticated
            and request.user.role in (
                CustomUser.Role.INCOMING_STUDENT,
                CustomUser.Role.UNDERGRADUATE,
                CustomUser.Role.ADMIN,
            )
        )
