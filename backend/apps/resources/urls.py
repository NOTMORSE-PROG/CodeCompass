from django.urls import path
from . import views

urlpatterns = [
    path('', views.ResourceListView.as_view(), name='resource-list'),
    path('youtube-search/', views.youtube_search, name='youtube-search'),
]
