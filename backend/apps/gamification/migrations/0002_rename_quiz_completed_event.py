from django.db import migrations


class Migration(migrations.Migration):

    dependencies = [
        ('gamification', '0001_initial'),
    ]

    operations = [
        migrations.AlterField(
            model_name='xpevent',
            name='event_type',
            field=__import__('django.db.models', fromlist=['CharField']).CharField(
                max_length=25,
                choices=[
                    ('node_completed', 'Completed Roadmap Node'),
                    ('roadmap_generated', 'Generated First Roadmap'),
                    ('daily_login', 'Daily Login'),
                    ('mentor_session', 'Completed Mentor Session'),
                    ('badge_earned', 'Earned Badge'),
                    ('cert_earned', 'Earned Certification'),
                    ('profile_completed', 'Completed Profile'),
                    ('onboarding_completed', 'Completed Onboarding'),
                ],
            ),
        ),
    ]
