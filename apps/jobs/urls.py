from django.urls import path
from . import views

urlpatterns = [
    path('', views.JobListView.as_view(), name='job-list'),
    path('recommended/', views.RecommendedJobsView.as_view(), name='recommended-jobs'),
    path('saved/', views.SavedJobListView.as_view(), name='saved-jobs'),
    path('<int:pk>/save/', views.save_job, name='save-job'),
]
