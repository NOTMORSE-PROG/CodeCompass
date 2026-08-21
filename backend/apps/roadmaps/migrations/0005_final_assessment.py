import django.db.models.deletion
from django.conf import settings
from django.db import migrations, models


class Migration(migrations.Migration):

    dependencies = [
        ('roadmaps', '0004_roadmapnode_last_replaced_at'),
        migrations.swappable_dependency(settings.AUTH_USER_MODEL),
    ]

    operations = [
        migrations.AlterField(
            model_name='roadmapnode',
            name='node_type',
            field=models.CharField(
                choices=[
                    ('milestone', 'Milestone'),
                    ('skill', 'Skill to Learn'),
                    ('project', 'Hands-on Project'),
                    ('assessment', 'Self-Assessment'),
                    ('final_assessment', 'Final Assessment'),
                    ('certification', 'Certification Goal'),
                ],
                max_length=20,
            ),
        ),
        migrations.CreateModel(
            name='FinalAssessmentSession',
            fields=[
                ('id', models.BigAutoField(auto_created=True, primary_key=True, serialize=False, verbose_name='ID')),
                ('questions_json', models.JSONField()),
                ('passed', models.BooleanField(null=True)),
                ('score', models.IntegerField(null=True)),
                ('created_at', models.DateTimeField(auto_now_add=True)),
                ('roadmap', models.ForeignKey(on_delete=django.db.models.deletion.CASCADE, related_name='final_assessment_sessions', to='roadmaps.roadmap')),
                ('user', models.ForeignKey(on_delete=django.db.models.deletion.CASCADE, related_name='final_assessment_sessions', to=settings.AUTH_USER_MODEL)),
            ],
            options={
                'ordering': ['-created_at'],
            },
        ),
    ]
