from django.urls import path
from . import views

urlpatterns = [
    path('', views.RoadmapListView.as_view(), name='roadmap-list'),
    path('generate/', views.generate_roadmap, name='roadmap-generate'),
    path('<int:pk>/', views.RoadmapDetailView.as_view(), name='roadmap-detail'),
    path('<int:roadmap_pk>/edit/', views.edit_roadmap_meta, name='roadmap-edit-meta'),
    path('<int:roadmap_pk>/nodes/add/', views.add_roadmap_node, name='roadmap-node-add'),
    path('<int:roadmap_pk>/nodes/<int:node_pk>/', views.update_node_status, name='node-status-update'),
    path('<int:roadmap_pk>/nodes/<int:node_pk>/edit/', views.edit_node_content, name='roadmap-node-edit'),
    path('<int:roadmap_pk>/nodes/<int:node_pk>/remove/', views.remove_roadmap_node, name='roadmap-node-remove'),
    path('<int:roadmap_pk>/nodes/<int:node_pk>/fetch-resources/', views.fetch_node_resources, name='node-fetch-resources'),
    path('switch/', views.switch_roadmap, name='roadmap-switch'),
    path('upskill/', views.upskill_roadmap, name='roadmap-upskill'),
    path('<int:roadmap_pk>/repair/', views.repair_roadmap, name='roadmap-repair'),
    path('<int:roadmap_pk>/fix-structure/', views.fix_structure, name='roadmap-fix-structure'),
    path('<int:roadmap_pk>/nodes/<int:node_pk>/resources/<int:resource_pk>/assessment/',
         views.start_assessment, name='assessment-start'),
    path('<int:roadmap_pk>/nodes/<int:node_pk>/resources/<int:resource_pk>/assessment/<int:session_pk>/submit/',
         views.submit_assessment, name='assessment-submit'),
    path('<int:roadmap_pk>/nodes/<int:node_pk>/resources/<int:resource_pk>/unlock/',
         views.unlock_video_watch, name='video-watch-unlock'),
]
