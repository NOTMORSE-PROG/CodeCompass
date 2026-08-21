from django.urls import path
from . import views

urlpatterns = [
    path('', views.ResumeListCreateView.as_view(), name='resume-list'),
    path('<int:pk>/', views.ResumeDetailView.as_view(), name='resume-detail'),
    path('<int:pk>/generate-bullets/', views.generate_bullets, name='resume-generate-bullets'),
    path('<int:pk>/generate-summary/', views.generate_summary, name='resume-generate-summary'),
    path('parse-job/', views.parse_job_description, name='resume-parse-job'),
    path('<int:pk>/score-ats/', views.score_ats, name='resume-score-ats'),
]
