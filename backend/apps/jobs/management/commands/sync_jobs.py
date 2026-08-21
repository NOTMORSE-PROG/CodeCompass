from django.core.management.base import BaseCommand
from apps.jobs.jobs_client import sync_jobs


class Command(BaseCommand):
    help = 'Fetch and cache job listings from Careerjet / JSearch APIs.'

    def add_arguments(self, parser):
        parser.add_argument('--keywords', default='software developer', help='Search keywords')
        parser.add_argument('--location', default='Philippines', help='Job location')
        parser.add_argument('--count', type=int, default=20, help='Number of jobs to fetch')

    def handle(self, *args, **options):
        self.stdout.write('Syncing jobs...')
        count = sync_jobs(
            keywords=options['keywords'],
            location=options['location'],
            count=options['count'],
        )
        self.stdout.write(self.style.SUCCESS(f'Done — {count} jobs synced.'))
