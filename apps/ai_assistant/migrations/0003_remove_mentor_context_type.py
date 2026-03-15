from django.db import migrations, models


class Migration(migrations.Migration):

    dependencies = [
        ('ai_assistant', '0002_alter_chatsession_context_type'),
    ]

    operations = [
        # Migrate any existing mentor sessions to general before removing the choice
        migrations.RunSQL(
            sql="UPDATE ai_assistant_chatsession SET context_type = 'general' WHERE context_type = 'mentor';",
            reverse_sql=migrations.RunSQL.noop,
        ),
        migrations.AlterField(
            model_name='chatsession',
            name='context_type',
            field=models.CharField(
                choices=[
                    ('general', 'General Career Advice'),
                    ('roadmap', 'Roadmap Discussion'),
                    ('job', 'Job Search'),
                    ('university', 'University Selection'),
                    ('onboarding', 'Onboarding Interview'),
                ],
                default='general',
                max_length=15,
            ),
        ),
    ]
