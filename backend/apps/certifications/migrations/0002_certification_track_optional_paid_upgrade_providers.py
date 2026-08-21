from django.db import migrations, models


class Migration(migrations.Migration):

    dependencies = [
        ('certifications', '0001_initial'),
    ]

    operations = [
        migrations.AddField(
            model_name='certification',
            name='track',
            field=models.CharField(
                choices=[
                    ('web', 'Web Development'),
                    ('backend', 'Backend Development'),
                    ('data', 'Data Science / AI'),
                    ('cyber', 'Cybersecurity'),
                    ('cloud', 'Cloud / DevOps'),
                    ('mobile', 'Mobile Development'),
                    ('networking', 'Networking / Linux'),
                    ('algorithms', 'Algorithms / CS Fundamentals'),
                    ('marketing', 'Digital Marketing'),
                    ('agile', 'Agile / Project Management'),
                    ('general', 'General IT'),
                ],
                default='general',
                max_length=15,
            ),
        ),
        migrations.AddField(
            model_name='certification',
            name='optional_paid_upgrade',
            field=models.TextField(
                blank=True,
                help_text='Optional paid upgrade path (e.g. exam fee, Coursera certificate)',
            ),
        ),
        # Expand provider max_length from 15 → 20 to accommodate new choices
        migrations.AlterField(
            model_name='certification',
            name='provider',
            field=models.CharField(
                choices=[
                    ('tesda', 'TESDA'),
                    ('google', 'Google'),
                    ('aws', 'Amazon Web Services'),
                    ('comptia', 'CompTIA'),
                    ('microsoft', 'Microsoft'),
                    ('cisco', 'Cisco'),
                    ('meta', 'Meta'),
                    ('oracle', 'Oracle'),
                    ('freecodecamp', 'freeCodeCamp'),
                    ('ibm', 'IBM'),
                    ('mongodb', 'MongoDB'),
                    ('github', 'GitHub'),
                    ('kaggle', 'Kaggle'),
                    ('harvard', 'Harvard / CS50'),
                    ('hubspot', 'HubSpot'),
                    ('salesforce', 'Salesforce'),
                    ('postman', 'Postman'),
                    ('scrum', 'SCRUMstudy / CertiProf'),
                    ('linux_foundation', 'Linux Foundation'),
                    ('other', 'Other'),
                ],
                max_length=20,
            ),
        ),
    ]
