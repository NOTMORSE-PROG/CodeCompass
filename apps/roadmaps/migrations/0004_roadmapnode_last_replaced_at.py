from django.db import migrations, models


class Migration(migrations.Migration):

    dependencies = [
        ('roadmaps', '0003_videowatchunlock'),
    ]

    operations = [
        migrations.AddField(
            model_name='roadmapnode',
            name='last_replaced_at',
            field=models.DateTimeField(blank=True, null=True),
        ),
    ]
