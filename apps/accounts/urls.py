"""Auth and profile URL routes."""
from django.urls import path

from . import views

urlpatterns = [
    # Auth
    path('register/', views.RegisterView.as_view(), name='auth-register'),
    path('login/', views.LoginView.as_view(), name='auth-login'),
    path('logout/', views.logout_view, name='auth-logout'),
    path('token/refresh/', views.CustomTokenRefreshView.as_view(), name='token-refresh'),
    path('me/', views.MeView.as_view(), name='auth-me'),
    path('send-change-password-otp/', views.send_change_password_otp_view, name='auth-send-change-password-otp'),
    path('change-password/', views.change_password_view, name='auth-change-password'),

    # Email verification
    path('verify-email/<str:token>/', views.VerifyEmailView.as_view(), name='auth-verify-email'),
    path('resend-verification/', views.ResendVerificationView.as_view(), name='auth-resend-verification'),

    # Password reset
    path('forgot-password/', views.ForgotPasswordView.as_view(), name='auth-forgot-password'),
    path('reset-password/', views.ResetPasswordView.as_view(), name='auth-reset-password'),

    # Google OAuth
    path('google/', views.GoogleOAuthView.as_view(), name='auth-google'),
    path('connect-google/', views.ConnectGoogleView.as_view(), name='auth-connect-google'),
    path('delete-account/', views.delete_account_view, name='auth-delete-account'),

    # Student profile
    path('profiles/student/me/', views.StudentProfileView.as_view(), name='student-profile-me'),
]
