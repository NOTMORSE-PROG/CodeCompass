"""Auth and profile URL routes."""
from django.urls import path
from rest_framework_simplejwt.views import TokenRefreshView

from . import views

urlpatterns = [
    # Auth
    path('register/', views.RegisterView.as_view(), name='auth-register'),
    path('login/', views.LoginView.as_view(), name='auth-login'),
    path('logout/', views.logout_view, name='auth-logout'),
    path('token/refresh/', TokenRefreshView.as_view(), name='token-refresh'),
    path('me/', views.MeView.as_view(), name='auth-me'),
    path('change-password/', views.change_password_view, name='auth-change-password'),

    # Google OAuth
    path('google/', views.GoogleOAuthView.as_view(), name='auth-google'),
    path('connect-google/', views.ConnectGoogleView.as_view(), name='auth-connect-google'),

    # Student profile
    path('profiles/student/me/', views.StudentProfileView.as_view(), name='student-profile-me'),
]
