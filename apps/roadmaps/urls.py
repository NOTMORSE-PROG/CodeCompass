from django.urls import path
from . import views

urlpatterns = [
    path('', views.RoadmapListView.as_view(), name='roadmap-list'),
    path('generate/', views.generate_roadmap, name='roadmap-generate'),
    path('<int:pk>/', views.RoadmapDetailView.as_view(), name='roadmap-detail'),
    path('<int:roadmap_pk>/nodes/<int:node_pk>/', views.update_node_status, name='node-status-update'),
]
