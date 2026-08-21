from django.core.management.base import BaseCommand
from apps.gamification.models import Badge

BADGES = [
    dict(
        name='First Step', slug='first-step',
        description='Complete your first roadmap node.',
        category='learning', icon_name='academic-cap', icon_color='#FFC300',
        xp_bonus=25, trigger_type='node_completed', trigger_value=1,
    ),
    dict(
        name='Node Master', slug='node-master',
        description='Complete 10 roadmap nodes.',
        category='learning', icon_name='trophy', icon_color='#F97316',
        xp_bonus=50, trigger_type='node_completed', trigger_value=10,
    ),
    dict(
        name='Road Builder', slug='road-builder',
        description='Generate your first learning roadmap.',
        category='learning', icon_name='map', icon_color='#22C55E',
        xp_bonus=25, trigger_type='roadmap_generated', trigger_value=1,
    ),
    dict(
        name='Certified', slug='certified',
        description='Earn your first certification.',
        category='certification', icon_name='badge-check', icon_color='#3B82F6',
        xp_bonus=50, trigger_type='cert_earned', trigger_value=1,
    ),
    dict(
        name='Onboarded', slug='onboarded',
        description='Complete the AI onboarding chat.',
        category='onboarding', icon_name='chat-bubble', icon_color='#A855F7',
        xp_bonus=25, trigger_type='onboarding_completed', trigger_value=1,
    ),
    dict(
        name='Week Warrior', slug='week-warrior',
        description='Log in 7 days in a row.',
        category='streak', icon_name='fire', icon_color='#EF4444',
        xp_bonus=50, trigger_type='streak_days', trigger_value=7,
    ),
    dict(
        name='Consistency King', slug='consistency-king',
        description='Log in 30 days in a row.',
        category='streak', icon_name='star', icon_color='#FBBF24',
        xp_bonus=100, trigger_type='streak_days', trigger_value=30,
    ),
    dict(
        name='Profile Complete', slug='profile-complete',
        description='Fill out your student profile.',
        category='special', icon_name='user-circle', icon_color='#06B6D4',
        xp_bonus=25, trigger_type='profile_completed', trigger_value=1,
    ),
]


class Command(BaseCommand):
    help = 'Seed default badge definitions into the database'

    def handle(self, *args, **options):
        created = 0
        updated = 0
        for data in BADGES:
            slug = data['slug']
            obj, was_created = Badge.objects.get_or_create(slug=slug, defaults=data)
            if was_created:
                created += 1
            else:
                # Update existing badge fields (except slug) in case defaults changed
                for field, value in data.items():
                    if field != 'slug':
                        setattr(obj, field, value)
                obj.save()
                updated += 1

        self.stdout.write(self.style.SUCCESS(
            f'Done: {created} badge(s) created, {updated} badge(s) updated.'
        ))
