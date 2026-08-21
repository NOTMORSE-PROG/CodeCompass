from django.db import migrations


class Migration(migrations.Migration):

    dependencies = [
        ('onboarding', '0003_remove_quiz_models'),
    ]

    operations = [
        migrations.RenameField(
            model_name='onboardingsession',
            old_name='quiz_summary',
            new_name='onboarding_summary',
        ),
    ]
