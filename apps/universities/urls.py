from django.urls import path
from . import views

urlpatterns = [
    path('', views.UniversityListView.as_view(), name='university-list'),
    path('recommendations/', views.UniversityRecommendationsView.as_view(), name='university-recommendations'),
    path('<int:pk>/', views.UniversityDetailView.as_view(), name='university-detail'),
]
