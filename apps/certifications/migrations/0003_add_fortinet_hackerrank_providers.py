from django.db import migrations, models


class Migration(migrations.Migration):

    dependencies = [
        ('certifications', '0002_certification_track_optional_paid_upgrade_providers'),
    ]

    operations = [
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
                    ('fortinet', 'Fortinet'),
                    ('hackerrank', 'HackerRank'),
                    ('other', 'Other'),
                ],
                max_length=20,
            ),
        ),
    ]
