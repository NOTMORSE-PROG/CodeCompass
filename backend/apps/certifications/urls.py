from django.urls import path
from . import views

urlpatterns = [
    path('', views.CertificationListView.as_view(), name='certification-list'),
    path('my/', views.UserCertificationListCreateView.as_view(), name='user-cert-list'),
    path('my/<int:pk>/', views.UserCertificationDetailView.as_view(), name='user-cert-detail'),
]
