"""
Management command: seed_free_certs
Populates the Certification table with curated free certifications
for Filipino CCS students — researched and verified as of 2025/2026.

Usage:
    python manage.py seed_free_certs           # insert only new
    python manage.py seed_free_certs --update  # also update existing
    python manage.py seed_free_certs --clear   # wipe table first, then insert
"""
from django.core.management.base import BaseCommand

from apps.certifications.models import Certification

P = Certification.Provider
L = Certification.Level
T = Certification.Track

# ---------------------------------------------------------------------------
# Master list — all confirmed free certifications (2025/2026)
# ---------------------------------------------------------------------------
FREE_CERTS = [

    # ── freeCodeCamp ─────────────────────────────────────────────────────────
    {
        'name': 'Responsive Web Design',
        'abbreviation': 'FCC-RWD',
        'provider': P.FREECODECAMP,
        'level': L.BEGINNER,
        'track': T.WEB,
        'description': (
            'Learn HTML5 and CSS3 from scratch. Build accessible, mobile-first '
            'websites using Flexbox and CSS Grid. Earn a free verified certificate '
            'after completing all projects and the final exam. ~300 self-paced hours.'
        ),
        'relevant_skills': ['HTML5', 'CSS3', 'Flexbox', 'CSS Grid', 'Responsive Design', 'Accessibility'],
        'career_paths': ['Web Development', 'Frontend Development', 'UI/UX'],
        'exam_url': 'https://www.freecodecamp.org/learn/2022/responsive-web-design/',
        'is_free': True,
        'estimated_study_hours': 300,
    },
    {
        'name': 'JavaScript Algorithms and Data Structures',
        'abbreviation': 'FCC-JSADS',
        'provider': P.FREECODECAMP,
        'level': L.BEGINNER,
        'track': T.WEB,
        'description': (
            'Learn JavaScript fundamentals, ES6+, regular expressions, debugging, '
            'data structures, algorithm scripting, and OOP. Earn a free certificate '
            'after the final exam (v10 curriculum, 2025). ~300 self-paced hours.'
        ),
        'relevant_skills': ['JavaScript', 'ES6+', 'Algorithms', 'Data Structures', 'OOP', 'Functional Programming'],
        'career_paths': ['Web Development', 'Frontend Development', 'Backend Development'],
        'exam_url': 'https://www.freecodecamp.org/learn/javascript-algorithms-and-data-structures-v8/',
        'is_free': True,
        'estimated_study_hours': 300,
    },
    {
        'name': 'Python',
        'abbreviation': 'FCC-PY',
        'provider': P.FREECODECAMP,
        'level': L.BEGINNER,
        'track': T.BACKEND,
        'description': (
            'Learn Python from scratch: data types, functions, loops, OOP, '
            'file handling, and real projects. Earn a free certificate after the '
            'final exam (v10 curriculum, 2025). ~300 self-paced hours.'
        ),
        'relevant_skills': ['Python', 'OOP', 'File I/O', 'Data Types', 'Functions'],
        'career_paths': ['Backend Development', 'Data Science', 'Automation', 'AI/ML'],
        'exam_url': 'https://www.freecodecamp.org/learn/scientific-computing-with-python/',
        'is_free': True,
        'estimated_study_hours': 300,
    },
    {
        'name': 'Data Analysis with Python',
        'abbreviation': 'FCC-DAP',
        'provider': P.FREECODECAMP,
        'level': L.INTERMEDIATE,
        'track': T.DATA,
        'description': (
            'Learn NumPy, Pandas, Matplotlib, Seaborn, and Jupyter Notebooks. '
            'Analyze real datasets and build 5 data analysis projects to earn '
            'your free certificate (legacy curriculum).'
        ),
        'relevant_skills': ['Python', 'NumPy', 'Pandas', 'Matplotlib', 'Jupyter', 'Data Wrangling'],
        'career_paths': ['Data Science', 'Data Analysis', 'AI/ML'],
        'exam_url': 'https://www.freecodecamp.org/learn/data-analysis-with-python/',
        'is_free': True,
        'estimated_study_hours': 300,
    },
    {
        'name': 'Machine Learning with Python',
        'abbreviation': 'FCC-MLP',
        'provider': P.FREECODECAMP,
        'level': L.INTERMEDIATE,
        'track': T.DATA,
        'description': (
            'Learn TensorFlow, neural networks, NLP, and reinforcement learning. '
            'Build and train ML models. Pass 5 machine learning challenges '
            'to earn your free certificate (legacy curriculum).'
        ),
        'relevant_skills': ['Python', 'TensorFlow', 'Neural Networks', 'NLP', 'Scikit-learn'],
        'career_paths': ['Data Science', 'AI/ML', 'Machine Learning Engineering'],
        'exam_url': 'https://www.freecodecamp.org/learn/machine-learning-with-python/',
        'is_free': True,
        'estimated_study_hours': 300,
    },
    {
        'name': 'Back End Development and APIs',
        'abbreviation': 'FCC-BE',
        'provider': P.FREECODECAMP,
        'level': L.INTERMEDIATE,
        'track': T.BACKEND,
        'description': (
            'Learn Node.js, Express, and MongoDB. Build RESTful APIs, '
            'work with databases, and complete 5 backend projects '
            'to earn your free certificate.'
        ),
        'relevant_skills': ['Node.js', 'Express.js', 'MongoDB', 'REST APIs', 'npm'],
        'career_paths': ['Backend Development', 'Full-Stack Development'],
        'exam_url': 'https://www.freecodecamp.org/learn/back-end-development-and-apis/',
        'is_free': True,
        'estimated_study_hours': 300,
    },
    {
        'name': 'Relational Database',
        'abbreviation': 'FCC-RDB',
        'provider': P.FREECODECAMP,
        'level': L.INTERMEDIATE,
        'track': T.BACKEND,
        'description': (
            'Learn SQL, PostgreSQL, and Bash scripting in a Linux terminal. '
            'Complete 5 database projects including Celestial Bodies and World Cup '
            'to earn your free certificate (uses VS Code in the browser).'
        ),
        'relevant_skills': ['SQL', 'PostgreSQL', 'Bash', 'Linux', 'Database Design'],
        'career_paths': ['Backend Development', 'Data Engineering', 'Full-Stack Development'],
        'exam_url': 'https://www.freecodecamp.org/learn/relational-database/',
        'is_free': True,
        'estimated_study_hours': 300,
    },

    # ── Harvard CS50 ─────────────────────────────────────────────────────────
    {
        'name': 'CS50x: Introduction to Computer Science',
        'abbreviation': 'CS50x',
        'provider': P.HARVARD,
        'level': L.BEGINNER,
        'track': T.ALGORITHMS,
        'description': (
            'Harvard\'s legendary intro CS course. Covers C, Python, SQL, HTML/CSS/JS, '
            'algorithms, data structures, memory management, and a final project. '
            'Score 70%+ on all problem sets to earn a FREE certificate directly '
            'from Harvard (not the paid edX verified certificate). ~100–200 hours.'
        ),
        'relevant_skills': ['C', 'Python', 'SQL', 'HTML', 'CSS', 'JavaScript', 'Algorithms', 'Data Structures'],
        'career_paths': ['Software Engineering', 'Backend Development', 'Web Development'],
        'exam_url': 'https://cs50.harvard.edu/x/',
        'is_free': True,
        'optional_paid_upgrade': 'edX verified certificate: ~$219 USD (not required — the cs50.harvard.edu certificate is free and equally recognized)',
        'estimated_study_hours': 150,
    },
    {
        'name': 'CS50P: Introduction to Programming with Python',
        'abbreviation': 'CS50P',
        'provider': P.HARVARD,
        'level': L.BEGINNER,
        'track': T.BACKEND,
        'description': (
            'Harvard\'s Python-focused programming course. Covers functions, exceptions, '
            'file I/O, regular expressions, OOP, and a final project. '
            'Earn a free certificate from Harvard after passing all problem sets. ~50–80 hours.'
        ),
        'relevant_skills': ['Python', 'OOP', 'Regular Expressions', 'File I/O', 'Unit Testing'],
        'career_paths': ['Backend Development', 'Data Science', 'Automation'],
        'exam_url': 'https://cs50.harvard.edu/python/',
        'is_free': True,
        'estimated_study_hours': 65,
    },
    {
        'name': 'CS50W: Web Programming with Python and JavaScript',
        'abbreviation': 'CS50W',
        'provider': P.HARVARD,
        'level': L.INTERMEDIATE,
        'track': T.WEB,
        'description': (
            'Build web apps with Django, JavaScript, Git, SQL, and APIs. '
            'Covers databases, security, scalability, and a final project. '
            'Free certificate from Harvard. ~80–120 hours.'
        ),
        'relevant_skills': ['Django', 'JavaScript', 'HTML/CSS', 'SQL', 'Git', 'REST APIs'],
        'career_paths': ['Web Development', 'Full-Stack Development', 'Backend Development'],
        'exam_url': 'https://cs50.harvard.edu/web/',
        'is_free': True,
        'estimated_study_hours': 100,
    },

    # ── Cisco NetAcad ─────────────────────────────────────────────────────────
    {
        'name': 'Introduction to Cybersecurity',
        'abbreviation': 'NetAcad-IntroSec',
        'provider': P.CISCO,
        'level': L.BEGINNER,
        'track': T.CYBER,
        'description': (
            'Cisco NetAcad course covering cybersecurity threats, vulnerabilities, '
            'online safety, and career opportunities in security. '
            'Complete the course to earn a free digital badge. ~15 hours.'
        ),
        'relevant_skills': ['Cybersecurity Fundamentals', 'Threat Awareness', 'Online Safety', 'Security Careers'],
        'career_paths': ['Cybersecurity', 'IT Support', 'Networking'],
        'exam_url': 'https://www.netacad.com/courses/introduction-to-cybersecurity',
        'is_free': True,
        'estimated_study_hours': 15,
    },
    {
        'name': 'Ethical Hacker',
        'abbreviation': 'NetAcad-EH',
        'provider': P.CISCO,
        'level': L.INTERMEDIATE,
        'track': T.CYBER,
        'description': (
            'Cisco NetAcad ethical hacking course covering penetration testing, '
            'network scanning, exploitation, and responsible disclosure. '
            'Earn a free digital badge on completion. ~20 hours.'
        ),
        'relevant_skills': ['Penetration Testing', 'Network Scanning', 'Exploitation Basics', 'Ethical Hacking'],
        'career_paths': ['Cybersecurity', 'Ethical Hacking', 'SOC Analyst'],
        'exam_url': 'https://www.netacad.com/courses/ethical-hacker',
        'is_free': True,
        'estimated_study_hours': 20,
    },
    {
        'name': 'Python Essentials 1',
        'abbreviation': 'NetAcad-PE1',
        'provider': P.CISCO,
        'level': L.BEGINNER,
        'track': T.BACKEND,
        'description': (
            'Cisco NetAcad Python course: variables, data types, conditionals, '
            'loops, functions, and basic OOP. Completion earns a free digital badge. '
            '~75 hours. Prepares for PCEP (Python Certified Entry-level Programmer) exam.'
        ),
        'relevant_skills': ['Python', 'OOP Basics', 'Functions', 'Loops', 'Data Types'],
        'career_paths': ['Backend Development', 'Data Science', 'Automation'],
        'exam_url': 'https://www.netacad.com/courses/python-essentials-1',
        'is_free': True,
        'optional_paid_upgrade': 'PCEP – Certified Entry-Level Python Programmer exam: ~$59 USD (Python Institute)',
        'estimated_study_hours': 75,
    },
    {
        'name': 'Networking Basics',
        'abbreviation': 'NetAcad-NB',
        'provider': P.CISCO,
        'level': L.BEGINNER,
        'track': T.NETWORKING,
        'description': (
            'Cisco NetAcad intro to networking: OSI model, IP addressing, '
            'switches, routers, protocols, and basic troubleshooting. '
            'Completion earns a free digital badge. ~22.5 hours.'
        ),
        'relevant_skills': ['OSI Model', 'IP Addressing', 'Subnetting', 'Protocols', 'Network Troubleshooting'],
        'career_paths': ['Networking', 'IT Support', 'Cloud/DevOps'],
        'exam_url': 'https://www.netacad.com/courses/networking-basics',
        'is_free': True,
        'estimated_study_hours': 23,
    },
    {
        'name': 'NDG Linux Unhatched',
        'abbreviation': 'NetAcad-Linux',
        'provider': P.CISCO,
        'level': L.BEGINNER,
        'track': T.NETWORKING,
        'description': (
            'Intro to Linux command line: basic commands, file system navigation, '
            'permissions, and shell scripting basics. Free digital badge on completion. '
            '~8 hours. Great starting point before any DevOps or backend path.'
        ),
        'relevant_skills': ['Linux CLI', 'File System', 'Permissions', 'Basic Shell'],
        'career_paths': ['DevOps', 'Backend Development', 'Cybersecurity', 'Cloud'],
        'exam_url': 'https://www.netacad.com/courses/ndg-linux-unhatched',
        'is_free': True,
        'estimated_study_hours': 8,
    },
    {
        'name': 'JavaScript Essentials 1',
        'abbreviation': 'NetAcad-JSE1',
        'provider': P.CISCO,
        'level': L.BEGINNER,
        'track': T.WEB,
        'description': (
            'Cisco NetAcad JavaScript course: variables, operators, functions, '
            'arrays, objects, and DOM manipulation. Free digital badge on completion. ~30 hours.'
        ),
        'relevant_skills': ['JavaScript', 'DOM', 'Functions', 'Arrays', 'Objects'],
        'career_paths': ['Web Development', 'Frontend Development'],
        'exam_url': 'https://www.netacad.com/courses/javascript-essentials-1',
        'is_free': True,
        'estimated_study_hours': 30,
    },
    {
        'name': 'Data Analytics Essentials',
        'abbreviation': 'NetAcad-DAE',
        'provider': P.CISCO,
        'level': L.BEGINNER,
        'track': T.DATA,
        'description': (
            'Intro to data analytics: collecting, cleaning, visualizing, '
            'and interpreting data. Covers basic statistics and real-world '
            'scenarios. Free digital badge on completion. ~30 hours.'
        ),
        'relevant_skills': ['Data Analysis', 'Statistics Basics', 'Data Visualization', 'Excel', 'SQL Basics'],
        'career_paths': ['Data Science', 'Data Analysis', 'Business Intelligence'],
        'exam_url': 'https://www.netacad.com/courses/data-analytics-essentials',
        'is_free': True,
        'estimated_study_hours': 30,
    },

    # ── IBM SkillsBuild / Cognitive Class ─────────────────────────────────────
    {
        'name': 'Cybersecurity Fundamentals',
        'abbreviation': 'IBM-CyberFund',
        'provider': P.IBM,
        'level': L.BEGINNER,
        'track': T.CYBER,
        'description': (
            'IBM SkillsBuild course covering CIA triad, threat types, encryption basics, '
            'network security, and security best practices. '
            'Earn a free Credly digital badge on completion. ~10–15 hours.'
        ),
        'relevant_skills': ['CIA Triad', 'Encryption', 'Network Security', 'Threat Analysis', 'Security Policies'],
        'career_paths': ['Cybersecurity', 'IT Security', 'SOC Analyst'],
        'exam_url': 'https://skillsbuild.org/learners',
        'study_guide_url': 'https://skillsbuild.org/learners',
        'is_free': True,
        'estimated_study_hours': 12,
    },
    {
        'name': 'Artificial Intelligence Fundamentals',
        'abbreviation': 'IBM-AIFund',
        'provider': P.IBM,
        'level': L.BEGINNER,
        'track': T.DATA,
        'description': (
            'IBM SkillsBuild course on AI concepts: machine learning, deep learning, '
            'NLP, computer vision, and ethical AI. '
            'Earn a free Credly digital badge on completion. ~10 hours.'
        ),
        'relevant_skills': ['Machine Learning', 'Deep Learning', 'NLP', 'Computer Vision', 'AI Ethics'],
        'career_paths': ['AI/ML', 'Data Science', 'Software Engineering'],
        'exam_url': 'https://skillsbuild.org/learners',
        'is_free': True,
        'estimated_study_hours': 10,
    },
    {
        'name': 'Machine Learning with Python (Cognitive Class)',
        'abbreviation': 'IBM-MLPY',
        'provider': P.IBM,
        'level': L.INTERMEDIATE,
        'track': T.DATA,
        'description': (
            'IBM Cognitive Class course: supervised/unsupervised learning, '
            'classification, regression, clustering, and recommender systems using Python. '
            'Earn a free Credly badge. ~6 hours.'
        ),
        'relevant_skills': ['Python', 'Scikit-learn', 'Regression', 'Classification', 'Clustering'],
        'career_paths': ['Data Science', 'AI/ML', 'Machine Learning Engineering'],
        'exam_url': 'https://cognitiveclass.ai/courses/machine-learning-with-python',
        'is_free': True,
        'estimated_study_hours': 6,
    },
    {
        'name': 'Data Science Methodology',
        'abbreviation': 'IBM-DSM',
        'provider': P.IBM,
        'level': L.BEGINNER,
        'track': T.DATA,
        'description': (
            'IBM Cognitive Class course on CRISP-DM and the data science process: '
            'problem framing, data collection, modeling, and deployment. '
            'Earn a free Credly badge. ~3 hours.'
        ),
        'relevant_skills': ['CRISP-DM', 'Data Science Process', 'Problem Framing', 'Model Evaluation'],
        'career_paths': ['Data Science', 'Data Analysis'],
        'exam_url': 'https://cognitiveclass.ai/courses/data-science-methodology-2',
        'is_free': True,
        'estimated_study_hours': 3,
    },

    # ── Google ────────────────────────────────────────────────────────────────
    {
        'name': 'Fundamentals of Digital Marketing',
        'abbreviation': 'Google-FDM',
        'provider': P.GOOGLE,
        'level': L.BEGINNER,
        'track': T.MARKETING,
        'description': (
            'IAB Europe-accredited course covering SEO, SEM, social media, '
            'email marketing, analytics, and e-commerce. Pass the exam to earn '
            'a free certificate. ~40 hours across 26 modules.'
        ),
        'relevant_skills': ['SEO', 'Google Ads', 'Social Media Marketing', 'Analytics', 'Email Marketing'],
        'career_paths': ['Digital Marketing', 'Web Development', 'E-commerce'],
        'exam_url': 'https://skillshop.exceedlms.com/student/collection/1830706-fundamentals-of-digital-marketing',
        'is_free': True,
        'estimated_study_hours': 40,
    },
    {
        'name': 'Google Analytics Certification',
        'abbreviation': 'Google-GA4',
        'provider': P.GOOGLE,
        'level': L.BEGINNER,
        'track': T.MARKETING,
        'description': (
            'Google Skillshop certification covering Google Analytics 4: '
            'data collection, event tracking, reporting, and insights. '
            'Free exam + free certificate. ~3–5 hours study.'
        ),
        'relevant_skills': ['Google Analytics 4', 'Data Reporting', 'Conversion Tracking', 'Audience Segmentation'],
        'career_paths': ['Digital Marketing', 'Data Analysis', 'Web Development'],
        'exam_url': 'https://skillshop.withgoogle.com/intl/en_ALL/course/analytics',
        'is_free': True,
        'estimated_study_hours': 4,
    },
    {
        'name': 'Introduction to Generative AI',
        'abbreviation': 'Google-GenAI',
        'provider': P.GOOGLE,
        'level': L.BEGINNER,
        'track': T.DATA,
        'description': (
            'Google Cloud skill badge: intro to generative AI, large language models, '
            'and responsible AI. No lab credits required — just video + quiz. '
            'Earns a free Google Cloud skill badge. ~45 minutes.'
        ),
        'relevant_skills': ['Generative AI', 'LLMs', 'Responsible AI', 'Prompt Engineering Basics'],
        'career_paths': ['AI/ML', 'Data Science', 'Software Engineering'],
        'exam_url': 'https://www.cloudskillsboost.google/paths/118',
        'is_free': True,
        'estimated_study_hours': 1,
    },

    # ── Microsoft ─────────────────────────────────────────────────────────────
    {
        'name': 'Microsoft Applied Skills — Azure AI Language',
        'abbreviation': 'MS-AppliedSkills-AI',
        'provider': P.MICROSOFT,
        'level': L.INTERMEDIATE,
        'track': T.DATA,
        'description': (
            'Free Microsoft Applied Skills credential: build NLP solutions using '
            'Azure AI Language. Lab-based assessment (~2 hours). '
            'Free to take — no exam scheduling fee. Earns a verifiable Microsoft credential.'
        ),
        'relevant_skills': ['Azure AI', 'NLP', 'Text Analytics', 'Azure Cognitive Services'],
        'career_paths': ['AI/ML', 'Cloud', 'Software Engineering'],
        'exam_url': 'https://learn.microsoft.com/en-us/credentials/applied-skills/',
        'is_free': True,
        'estimated_study_hours': 15,
    },
    {
        'name': 'Microsoft Applied Skills — ASP.NET Core Web App',
        'abbreviation': 'MS-AppliedSkills-Web',
        'provider': P.MICROSOFT,
        'level': L.INTERMEDIATE,
        'track': T.WEB,
        'description': (
            'Free Microsoft Applied Skills credential: develop an ASP.NET Core web app '
            'with Blazor or Razor Pages. Lab-based assessment (~2 hours). '
            'Free to take — no scheduling fee.'
        ),
        'relevant_skills': ['ASP.NET Core', 'C#', 'Blazor', 'Web APIs', '.NET'],
        'career_paths': ['Web Development', 'Backend Development', 'Full-Stack Development'],
        'exam_url': 'https://learn.microsoft.com/en-us/credentials/applied-skills/',
        'is_free': True,
        'estimated_study_hours': 20,
    },

    # ── Oracle ────────────────────────────────────────────────────────────────
    {
        'name': 'Oracle Cloud Infrastructure Foundations Associate',
        'abbreviation': 'OCI-Foundations',
        'provider': P.ORACLE,
        'level': L.BEGINNER,
        'track': T.CLOUD,
        'description': (
            'Oracle\'s free Foundations-level cloud certification exam. Covers OCI '
            'architecture, compute, storage, networking, security, and pricing. '
            'The exam itself is FREE — unique among major cloud vendors. '
            '~10–20 hours of study via free Oracle MyLearn learning paths.'
        ),
        'relevant_skills': ['OCI Architecture', 'Cloud Compute', 'Cloud Storage', 'Cloud Networking', 'Cloud Security'],
        'career_paths': ['Cloud/DevOps', 'IT Administration', 'Software Engineering'],
        'exam_url': 'https://education.oracle.com/oracle-cloud-infrastructure-2025-certified-foundations-associate/trackp_OCI25FNDCFA',
        'is_free': True,
        'estimated_study_hours': 15,
    },
    {
        'name': 'Oracle Cloud Infrastructure AI Foundations Associate',
        'abbreviation': 'OCI-AIFoundations',
        'provider': P.ORACLE,
        'level': L.BEGINNER,
        'track': T.DATA,
        'description': (
            'Free Oracle certification exam covering AI/ML on OCI, generative AI, '
            'large language models, and OCI AI services. '
            'Exam is FREE. ~10 hours of study via free learning paths.'
        ),
        'relevant_skills': ['OCI AI Services', 'Generative AI', 'LLMs', 'Machine Learning Basics'],
        'career_paths': ['AI/ML', 'Cloud/DevOps', 'Data Science'],
        'exam_url': 'https://education.oracle.com/oracle-cloud-infrastructure-2025-certified-ai-foundations-associate/trackp_OCI25AIFNDCFA',
        'is_free': True,
        'estimated_study_hours': 10,
    },

    # ── AWS ───────────────────────────────────────────────────────────────────
    {
        'name': 'AWS Educate — Cloud Computing Fundamentals',
        'abbreviation': 'AWS-Educate-Cloud',
        'provider': P.AWS,
        'level': L.BEGINNER,
        'track': T.CLOUD,
        'description': (
            'AWS Educate free badge: cloud concepts, AWS core services, storage, '
            'compute, and networking basics. No AWS account or credit card required. '
            'Earn a free Credly badge after completing the course path. ~5–8 hours.'
        ),
        'relevant_skills': ['AWS Basics', 'Cloud Concepts', 'EC2', 'S3', 'Cloud Storage'],
        'career_paths': ['Cloud/DevOps', 'Software Engineering', 'IT Administration'],
        'exam_url': 'https://aws.amazon.com/education/awseducate/',
        'is_free': True,
        'optional_paid_upgrade': 'AWS Cloud Practitioner (CLF-C02) certification exam: ~$100 USD (optional, prepare via free AWS Skill Builder courses)',
        'estimated_study_hours': 6,
    },

    # ── MongoDB ───────────────────────────────────────────────────────────────
    {
        'name': 'MongoDB and the Document Model',
        'abbreviation': 'MongoDB-DocModel',
        'provider': P.MONGODB,
        'level': L.BEGINNER,
        'track': T.BACKEND,
        'description': (
            'Free MongoDB University course: document model, BSON, collections, '
            'and how MongoDB differs from relational databases. '
            'Earn a free completion badge. ~3 hours.'
        ),
        'relevant_skills': ['MongoDB', 'NoSQL', 'BSON', 'Document Model', 'Collections'],
        'career_paths': ['Backend Development', 'Full-Stack Development', 'Data Engineering'],
        'exam_url': 'https://learn.mongodb.com/learn/course/mongodb-and-the-document-model',
        'is_free': True,
        'optional_paid_upgrade': 'MongoDB Associate Developer certification exam: ~$150 USD (free via GitHub Student Developer Pack)',
        'estimated_study_hours': 3,
    },
    {
        'name': 'MongoDB CRUD Operations with Node.js',
        'abbreviation': 'MongoDB-CRUD-Node',
        'provider': P.MONGODB,
        'level': L.BEGINNER,
        'track': T.BACKEND,
        'description': (
            'Free MongoDB University course: create, read, update, and delete '
            'documents using the Node.js driver. Covers queries, filters, '
            'and cursor methods. ~3–5 hours.'
        ),
        'relevant_skills': ['MongoDB', 'Node.js', 'CRUD', 'NoSQL Queries', 'Aggregation Basics'],
        'career_paths': ['Backend Development', 'Full-Stack Development'],
        'exam_url': 'https://learn.mongodb.com/learn/course/mongodb-crud-operations-in-nodejs',
        'is_free': True,
        'estimated_study_hours': 4,
    },

    # ── GitHub ────────────────────────────────────────────────────────────────
    {
        'name': 'GitHub Foundations',
        'abbreviation': 'GH-Foundations',
        'provider': P.GITHUB,
        'level': L.BEGINNER,
        'track': T.GENERAL,
        'description': (
            'GitHub certification covering Git basics, repositories, branches, '
            'pull requests, GitHub Actions intro, and collaborative workflows. '
            'Exam normally costs $99 USD, but is FREE for verified students via '
            'GitHub Education (education.github.com). '
            'Highly recommended — every developer needs this.'
        ),
        'relevant_skills': ['Git', 'GitHub', 'Branching', 'Pull Requests', 'Collaboration', 'Version Control'],
        'career_paths': ['Web Development', 'Backend Development', 'Data Science', 'DevOps', 'All Paths'],
        'exam_url': 'https://examregistration.github.com/certification/GHF',
        'study_guide_url': 'https://education.github.com/experiences/foundations_certificate',
        'is_free': True,
        'optional_paid_upgrade': 'Standard exam price without student verification: $99 USD',
        'estimated_study_hours': 10,
    },
    {
        'name': 'GitHub Actions',
        'abbreviation': 'GH-Actions',
        'provider': P.GITHUB,
        'level': L.INTERMEDIATE,
        'track': T.CLOUD,
        'description': (
            'GitHub certification covering CI/CD pipelines, workflow syntax, '
            'runners, secrets management, and GitHub Actions marketplace. '
            'Exam costs $99 USD; check GitHub Education for student discounts.'
        ),
        'relevant_skills': ['CI/CD', 'GitHub Actions', 'YAML Workflows', 'Automation', 'DevOps'],
        'career_paths': ['DevOps', 'Cloud/DevOps', 'Software Engineering'],
        'exam_url': 'https://examregistration.github.com/',
        'is_free': False,
        'estimated_cost_php': 5500,
        'optional_paid_upgrade': 'Exam: $99 USD. Free learning paths available at learn.microsoft.com/en-us/training/github/',
        'estimated_study_hours': 15,
    },

    # ── Postman ───────────────────────────────────────────────────────────────
    {
        'name': 'Postman API Fundamentals Student Expert',
        'abbreviation': 'Postman-SE',
        'provider': P.POSTMAN,
        'level': L.BEGINNER,
        'track': T.BACKEND,
        'description': (
            'Hands-on Postman certification: sending requests, writing tests, '
            'collections, environments, and API documentation. '
            'Complete all tasks to earn a free digital badge. ~4–5 hours. '
            'Open to everyone — not just enrolled students despite the name.'
        ),
        'relevant_skills': ['REST APIs', 'Postman', 'API Testing', 'HTTP Methods', 'JSON'],
        'career_paths': ['Backend Development', 'Web Development', 'QA/Testing'],
        'exam_url': 'https://academy.postman.com/path/postman-api-fundamentals-student-expert',
        'is_free': True,
        'estimated_study_hours': 5,
    },

    # ── Kaggle ────────────────────────────────────────────────────────────────
    {
        'name': 'Python (Kaggle Learn)',
        'abbreviation': 'Kaggle-Python',
        'provider': P.KAGGLE,
        'level': L.BEGINNER,
        'track': T.DATA,
        'description': (
            'Kaggle micro-course: Python syntax, functions, data types, loops, '
            'and external libraries. Free completion certificate auto-issued. '
            '~5 hours. Part of the official Kaggle Learn platform.'
        ),
        'relevant_skills': ['Python', 'Functions', 'Loops', 'Lists', 'Dictionaries'],
        'career_paths': ['Data Science', 'AI/ML', 'Backend Development'],
        'exam_url': 'https://www.kaggle.com/learn/python',
        'is_free': True,
        'estimated_study_hours': 5,
    },
    {
        'name': 'Intro to Machine Learning (Kaggle Learn)',
        'abbreviation': 'Kaggle-ML',
        'provider': P.KAGGLE,
        'level': L.BEGINNER,
        'track': T.DATA,
        'description': (
            'Kaggle micro-course: decision trees, model validation, underfitting/overfitting, '
            'and Random Forests using Scikit-learn. Free certificate. ~3 hours.'
        ),
        'relevant_skills': ['Scikit-learn', 'Decision Trees', 'Random Forests', 'Model Validation'],
        'career_paths': ['Data Science', 'AI/ML'],
        'exam_url': 'https://www.kaggle.com/learn/intro-to-machine-learning',
        'is_free': True,
        'estimated_study_hours': 3,
    },
    {
        'name': 'Intro to SQL (Kaggle Learn)',
        'abbreviation': 'Kaggle-SQL',
        'provider': P.KAGGLE,
        'level': L.BEGINNER,
        'track': T.BACKEND,
        'description': (
            'Kaggle micro-course: SELECT, FROM, WHERE, GROUP BY, HAVING, JOIN '
            'using BigQuery. Free certificate. ~3 hours.'
        ),
        'relevant_skills': ['SQL', 'BigQuery', 'SELECT', 'JOIN', 'Aggregation'],
        'career_paths': ['Data Science', 'Backend Development', 'Data Engineering'],
        'exam_url': 'https://www.kaggle.com/learn/intro-to-sql',
        'is_free': True,
        'estimated_study_hours': 3,
    },

    # ── HubSpot ───────────────────────────────────────────────────────────────
    {
        'name': 'Digital Marketing Certification',
        'abbreviation': 'HubSpot-DM',
        'provider': P.HUBSPOT,
        'level': L.BEGINNER,
        'track': T.MARKETING,
        'description': (
            'HubSpot Academy certification covering SEO, content marketing, '
            'social media, email marketing, paid media, and analytics. '
            'Free exam + free certificate. ~5–7 hours.'
        ),
        'relevant_skills': ['SEO', 'Content Marketing', 'Social Media', 'Email Marketing', 'Analytics'],
        'career_paths': ['Digital Marketing', 'Web Development', 'Freelancing'],
        'exam_url': 'https://app.hubspot.com/academy/certifications/digital-marketing',
        'is_free': True,
        'estimated_study_hours': 6,
    },
    {
        'name': 'Inbound Marketing Certification',
        'abbreviation': 'HubSpot-Inbound',
        'provider': P.HUBSPOT,
        'level': L.BEGINNER,
        'track': T.MARKETING,
        'description': (
            'HubSpot Academy certification on the inbound methodology: '
            'attract, engage, and delight customers. '
            'Free exam + certificate. ~4 hours.'
        ),
        'relevant_skills': ['Inbound Marketing', 'Content Strategy', 'Buyer Personas', 'Customer Journey'],
        'career_paths': ['Digital Marketing', 'Business Development'],
        'exam_url': 'https://app.hubspot.com/academy/certifications/inbound',
        'is_free': True,
        'estimated_study_hours': 4,
    },

    # ── Scrum / Agile ─────────────────────────────────────────────────────────
    {
        'name': 'Scrum Fundamentals Certified',
        'abbreviation': 'SFC',
        'provider': P.SCRUM,
        'level': L.BEGINNER,
        'track': T.AGILE,
        'description': (
            'SCRUMstudy free certification: Scrum framework, roles (Scrum Master, '
            'Product Owner, Development Team), ceremonies, and artifacts. '
            'Free course + free exam + free certificate. ~6–8 hours.'
        ),
        'relevant_skills': ['Scrum', 'Agile', 'Sprint Planning', 'Backlog Management', 'Retrospectives'],
        'career_paths': ['Software Engineering', 'Project Management', 'All IT Paths'],
        'exam_url': 'https://www.scrumstudy.com/certification/scrum-fundamentals-certified',
        'is_free': True,
        'estimated_study_hours': 7,
    },

    # ── freeCodeCamp (additional certs) ──────────────────────────────────────
    {
        'name': 'Front End Development Libraries',
        'abbreviation': 'FCC-FEDL',
        'provider': P.FREECODECAMP,
        'level': L.INTERMEDIATE,
        'track': T.WEB,
        'description': (
            'Learn React, Redux, Sass, Bootstrap, and jQuery. Build 5 front-end '
            'projects to earn a free certificate. ~300 self-paced hours.'
        ),
        'relevant_skills': ['React', 'Redux', 'Sass', 'Bootstrap', 'jQuery'],
        'career_paths': ['Web Development', 'Frontend Development'],
        'exam_url': 'https://www.freecodecamp.org/learn/front-end-development-libraries/',
        'is_free': True,
        'estimated_study_hours': 300,
    },
    {
        'name': 'Quality Assurance',
        'abbreviation': 'FCC-QA',
        'provider': P.FREECODECAMP,
        'level': L.INTERMEDIATE,
        'track': T.BACKEND,
        'description': (
            'Learn testing with Chai, Node.js integration tests, and build '
            '5 quality assurance projects. Free certificate. ~300 hours.'
        ),
        'relevant_skills': ['Chai', 'Node.js Testing', 'Integration Tests', 'Unit Tests', 'Mocha'],
        'career_paths': ['Backend Development', 'QA Engineering', 'Full-Stack Development'],
        'exam_url': 'https://www.freecodecamp.org/learn/quality-assurance/',
        'is_free': True,
        'estimated_study_hours': 300,
    },
    {
        'name': 'Information Security',
        'abbreviation': 'FCC-InfoSec',
        'provider': P.FREECODECAMP,
        'level': L.INTERMEDIATE,
        'track': T.CYBER,
        'description': (
            'Learn HelmetJS, TCP/IP, penetration testing basics, and build '
            '5 information security projects. Free certificate. ~300 hours.'
        ),
        'relevant_skills': ['HelmetJS', 'TCP/IP', 'Penetration Testing Basics', 'Cryptography', 'Security Headers'],
        'career_paths': ['Cybersecurity', 'Backend Development', 'Full-Stack Development'],
        'exam_url': 'https://www.freecodecamp.org/learn/information-security/',
        'is_free': True,
        'estimated_study_hours': 300,
    },
    {
        'name': 'Foundational C# with Microsoft',
        'abbreviation': 'FCC-CSharp',
        'provider': P.FREECODECAMP,
        'level': L.BEGINNER,
        'track': T.BACKEND,
        'description': (
            'Joint freeCodeCamp + Microsoft credential. Learn C# fundamentals: '
            'variables, data types, conditionals, loops, methods, and basic OOP. '
            'Earns a Microsoft Learn badge + freeCodeCamp certificate. ~35 hours.'
        ),
        'relevant_skills': ['C#', '.NET', 'OOP Basics', 'Data Types', 'Methods', 'Conditionals'],
        'career_paths': ['Backend Development', 'Game Development', 'Software Engineering'],
        'exam_url': 'https://www.freecodecamp.org/learn/foundational-c-sharp-with-microsoft/',
        'is_free': True,
        'estimated_study_hours': 35,
    },
    {
        'name': 'Data Visualization',
        'abbreviation': 'FCC-DataViz',
        'provider': P.FREECODECAMP,
        'level': L.INTERMEDIATE,
        'track': T.DATA,
        'description': (
            'Learn D3.js for data visualization: bar charts, scatter plots, '
            'heat maps, and choropleth maps. Build 5 visualization projects. '
            'Free certificate. ~300 hours.'
        ),
        'relevant_skills': ['D3.js', 'SVG', 'Data Visualization', 'JavaScript', 'JSON APIs'],
        'career_paths': ['Data Science', 'Web Development', 'Business Intelligence'],
        'exam_url': 'https://www.freecodecamp.org/learn/data-visualization/',
        'is_free': True,
        'estimated_study_hours': 300,
    },

    # ── Harvard CS50 (additional courses) ─────────────────────────────────────
    {
        'name': 'CS50AI: Introduction to Artificial Intelligence with Python',
        'abbreviation': 'CS50AI',
        'provider': P.HARVARD,
        'level': L.INTERMEDIATE,
        'track': T.DATA,
        'description': (
            'Harvard\'s AI course: search algorithms, knowledge representation, '
            'uncertainty, optimization, machine learning, neural networks, and NLP. '
            'Free certificate from Harvard. ~80–150 hours.'
        ),
        'relevant_skills': ['Python', 'Search Algorithms', 'Machine Learning', 'Neural Networks', 'NLP', 'Probabilistic AI'],
        'career_paths': ['AI/ML', 'Data Science', 'Software Engineering'],
        'exam_url': 'https://cs50.harvard.edu/ai/',
        'is_free': True,
        'estimated_study_hours': 120,
    },
    {
        'name': 'CS50SQL: Introduction to Databases with SQL',
        'abbreviation': 'CS50SQL',
        'provider': P.HARVARD,
        'level': L.BEGINNER,
        'track': T.BACKEND,
        'description': (
            'Harvard\'s SQL course: querying, relating, designing, and scaling databases. '
            'Covers SELECT, JOIN, normalization, indexing, and transactions. '
            'Free certificate from Harvard. ~50–80 hours.'
        ),
        'relevant_skills': ['SQL', 'Database Design', 'Normalization', 'Indexing', 'Transactions', 'SQLite'],
        'career_paths': ['Backend Development', 'Data Science', 'Data Engineering'],
        'exam_url': 'https://cs50.harvard.edu/sql/',
        'is_free': True,
        'estimated_study_hours': 65,
    },
    {
        'name': 'CS50 Cybersecurity',
        'abbreviation': 'CS50Cyber',
        'provider': P.HARVARD,
        'level': L.BEGINNER,
        'track': T.CYBER,
        'description': (
            'Harvard\'s cybersecurity course: passwords, encryption, web security, '
            'privacy, and security best practices. Free certificate from Harvard. '
            '~30–50 hours. Great intro before deeper cybersecurity training.'
        ),
        'relevant_skills': ['Encryption', 'Web Security', 'Passwords', 'Privacy', 'Phishing Awareness', 'HTTPS'],
        'career_paths': ['Cybersecurity', 'IT Support', 'Software Engineering'],
        'exam_url': 'https://cs50.harvard.edu/cybersecurity/',
        'is_free': True,
        'estimated_study_hours': 40,
    },

    # ── Cisco NetAcad (additional courses) ────────────────────────────────────
    {
        'name': 'Python Essentials 2',
        'abbreviation': 'NetAcad-PE2',
        'provider': P.CISCO,
        'level': L.BEGINNER,
        'track': T.BACKEND,
        'description': (
            'Cisco NetAcad Python course part 2: OOP in depth, exceptions, '
            'file processing, selected Python modules, and the Python Standard Library. '
            'Free digital badge. ~75 hours. Prepares for PCAP (Associate) exam.'
        ),
        'relevant_skills': ['Python OOP', 'Exception Handling', 'File I/O', 'Modules', 'Python Standard Library'],
        'career_paths': ['Backend Development', 'Data Science', 'Automation'],
        'exam_url': 'https://www.netacad.com/courses/python-essentials-2',
        'is_free': True,
        'optional_paid_upgrade': 'PCAP – Certified Associate in Python Programming: ~$295 USD (Python Institute)',
        'estimated_study_hours': 75,
    },
    {
        'name': 'JavaScript Essentials 2',
        'abbreviation': 'NetAcad-JSE2',
        'provider': P.CISCO,
        'level': L.INTERMEDIATE,
        'track': T.WEB,
        'description': (
            'Cisco NetAcad JavaScript part 2: advanced functions, generators, '
            'closures, prototypes, async programming, and advanced DOM. '
            'Free digital badge. ~50 hours.'
        ),
        'relevant_skills': ['Advanced JavaScript', 'Closures', 'Async/Await', 'Prototypes', 'DOM Manipulation'],
        'career_paths': ['Web Development', 'Frontend Development', 'Full-Stack Development'],
        'exam_url': 'https://www.netacad.com/courses/javascript-essentials-2',
        'is_free': True,
        'estimated_study_hours': 50,
    },
    {
        'name': 'Cybersecurity Essentials',
        'abbreviation': 'NetAcad-CyberEss',
        'provider': P.CISCO,
        'level': L.INTERMEDIATE,
        'track': T.CYBER,
        'description': (
            'Cisco NetAcad cybersecurity course covering threat landscape, '
            'firewalls, IDS/IPS, cryptography, access control, and security '
            'operations. Free digital badge. ~30 hours.'
        ),
        'relevant_skills': ['Firewalls', 'IDS/IPS', 'Cryptography', 'Access Control', 'Threat Analysis'],
        'career_paths': ['Cybersecurity', 'Networking', 'SOC Analyst'],
        'exam_url': 'https://www.netacad.com/courses/cybersecurity-essentials',
        'is_free': True,
        'estimated_study_hours': 30,
    },
    {
        'name': 'Junior Cybersecurity Analyst Career Path',
        'abbreviation': 'NetAcad-JCSA',
        'provider': P.CISCO,
        'level': L.INTERMEDIATE,
        'track': T.CYBER,
        'description': (
            'Cisco NetAcad career path covering OS basics, networking, endpoint '
            'security, network defense, and cyber threat management. '
            'Complete the full path to earn the CyberOps Associate badge. '
            '~120 hours. Leads to CCST Cybersecurity exam readiness.'
        ),
        'relevant_skills': ['Security Operations', 'Network Defense', 'Endpoint Security', 'Threat Analysis', 'Incident Response'],
        'career_paths': ['Cybersecurity', 'SOC Analyst', 'Network Security'],
        'exam_url': 'https://www.netacad.com/career-paths/cybersecurity',
        'is_free': True,
        'optional_paid_upgrade': 'Cisco Certified Support Technician (CCST) Cybersecurity exam: ~$125 USD',
        'estimated_study_hours': 120,
    },
    {
        'name': 'Linux Essentials (NDG)',
        'abbreviation': 'NetAcad-LinuxEss',
        'provider': P.CISCO,
        'level': L.BEGINNER,
        'track': T.NETWORKING,
        'description': (
            'Full NDG Linux Essentials course: command line, file system, text editing, '
            'scripting, processes, networking, and security basics. '
            'Free digital badge. ~70 hours. Prepares for LPI Linux Essentials exam.'
        ),
        'relevant_skills': ['Linux CLI', 'Shell Scripting', 'File Permissions', 'Process Management', 'Networking Commands'],
        'career_paths': ['DevOps', 'Cloud/DevOps', 'Cybersecurity', 'Backend Development'],
        'exam_url': 'https://www.netacad.com/courses/ndg-linux-essentials',
        'is_free': True,
        'optional_paid_upgrade': 'LPI Linux Essentials certification exam: ~$120 USD',
        'estimated_study_hours': 70,
    },

    # ── IBM SkillsBuild (additional) ───────────────────────────────────────────
    {
        'name': 'Cloud Computing Fundamentals',
        'abbreviation': 'IBM-CloudFund',
        'provider': P.IBM,
        'level': L.BEGINNER,
        'track': T.CLOUD,
        'description': (
            'IBM SkillsBuild course: cloud concepts, IaaS/PaaS/SaaS, public/private/hybrid '
            'cloud, microservices, containers, and cloud security basics. '
            'Free Credly badge. ~8–10 hours.'
        ),
        'relevant_skills': ['Cloud Concepts', 'IaaS/PaaS/SaaS', 'Microservices', 'Containers Basics', 'Cloud Security'],
        'career_paths': ['Cloud/DevOps', 'Software Engineering', 'IT Administration'],
        'exam_url': 'https://skillsbuild.org/learners',
        'is_free': True,
        'estimated_study_hours': 9,
    },
    {
        'name': 'Introduction to DevOps',
        'abbreviation': 'IBM-DevOps',
        'provider': P.IBM,
        'level': L.BEGINNER,
        'track': T.CLOUD,
        'description': (
            'IBM SkillsBuild course: DevOps culture, CI/CD pipelines, Agile, '
            'Infrastructure as Code, monitoring, and security in DevOps (DevSecOps). '
            'Free Credly badge. ~8 hours.'
        ),
        'relevant_skills': ['CI/CD', 'Infrastructure as Code', 'DevSecOps', 'Agile', 'Monitoring'],
        'career_paths': ['DevOps', 'Cloud/DevOps', 'Software Engineering'],
        'exam_url': 'https://skillsbuild.org/learners',
        'is_free': True,
        'estimated_study_hours': 8,
    },
    {
        'name': 'Introduction to Containers, Kubernetes, and OpenShift',
        'abbreviation': 'IBM-K8s',
        'provider': P.IBM,
        'level': L.INTERMEDIATE,
        'track': T.CLOUD,
        'description': (
            'IBM SkillsBuild course: Docker containers, Kubernetes orchestration, '
            'OpenShift, Istio service mesh, and serverless computing. '
            'Free Credly badge. ~14 hours.'
        ),
        'relevant_skills': ['Docker', 'Kubernetes', 'OpenShift', 'Containers', 'Microservices', 'Serverless'],
        'career_paths': ['DevOps', 'Cloud/DevOps', 'Software Engineering'],
        'exam_url': 'https://skillsbuild.org/learners',
        'is_free': True,
        'estimated_study_hours': 14,
    },
    {
        'name': 'Web Development Fundamentals',
        'abbreviation': 'IBM-WebFund',
        'provider': P.IBM,
        'level': L.BEGINNER,
        'track': T.WEB,
        'description': (
            'IBM SkillsBuild course: HTML, CSS, JavaScript basics, HTTP, '
            'APIs, and web accessibility. Free Credly badge. ~8–10 hours.'
        ),
        'relevant_skills': ['HTML', 'CSS', 'JavaScript', 'HTTP', 'APIs', 'Web Accessibility'],
        'career_paths': ['Web Development', 'Frontend Development'],
        'exam_url': 'https://skillsbuild.org/learners',
        'is_free': True,
        'estimated_study_hours': 9,
    },

    # ── Google (additional) ────────────────────────────────────────────────────
    {
        'name': 'Google Ads Search Certification',
        'abbreviation': 'Google-Ads-Search',
        'provider': P.GOOGLE,
        'level': L.BEGINNER,
        'track': T.MARKETING,
        'description': (
            'Google Skillshop: paid search advertising, keyword planning, '
            'bidding strategies, ad formats, and quality score optimization. '
            'Free course + free exam + free shareable certificate. ~5–8 hours. '
            'Renews annually.'
        ),
        'relevant_skills': ['Google Ads', 'Keyword Research', 'Bidding Strategies', 'Ad Copywriting', 'Quality Score'],
        'career_paths': ['Digital Marketing', 'E-commerce', 'Freelancing'],
        'exam_url': 'https://skillshop.withgoogle.com/',
        'is_free': True,
        'estimated_study_hours': 6,
    },
    {
        'name': 'Generative AI Fundamentals (Google Cloud)',
        'abbreviation': 'Google-GenAI-Fund',
        'provider': P.GOOGLE,
        'level': L.BEGINNER,
        'track': T.DATA,
        'description': (
            'Google Cloud skill badge covering gen AI, LLMs, responsible AI, '
            'image generation, and LLM architecture. No lab credits needed — '
            'just video + quizzes. Free Google Cloud skill badge. ~2–3 hours.'
        ),
        'relevant_skills': ['Generative AI', 'LLMs', 'Prompt Engineering', 'Responsible AI', 'Transformer Architecture'],
        'career_paths': ['AI/ML', 'Data Science', 'Software Engineering'],
        'exam_url': 'https://www.cloudskillsboost.google/paths/118',
        'is_free': True,
        'estimated_study_hours': 3,
    },

    # ── AWS (additional Knowledge Badges) ─────────────────────────────────────
    {
        'name': 'AWS Knowledge: Cloud Essentials',
        'abbreviation': 'AWS-KB-Cloud',
        'provider': P.AWS,
        'level': L.BEGINNER,
        'track': T.CLOUD,
        'description': (
            'AWS Knowledge Badge: cloud concepts, AWS core services, security '
            'model, and billing fundamentals. Pass an 80-question assessment '
            'to earn a free Credly badge. ~4–6 hours. No AWS account required.'
        ),
        'relevant_skills': ['AWS Core Services', 'Cloud Concepts', 'AWS Security', 'Cloud Billing', 'Shared Responsibility'],
        'career_paths': ['Cloud/DevOps', 'Software Engineering', 'IT Administration'],
        'exam_url': 'https://aws.amazon.com/training/badges/',
        'is_free': True,
        'optional_paid_upgrade': 'AWS Cloud Practitioner exam (CLF-C02): ~$100 USD',
        'estimated_study_hours': 5,
    },
    {
        'name': 'AWS Educate — Machine Learning Foundations',
        'abbreviation': 'AWS-Educate-ML',
        'provider': P.AWS,
        'level': L.BEGINNER,
        'track': T.DATA,
        'description': (
            'AWS Educate free badge: ML concepts, supervised/unsupervised learning, '
            'AWS ML services overview, and ethical AI. No AWS account or credit card. '
            '~5 hours. Free Credly badge.'
        ),
        'relevant_skills': ['Machine Learning Basics', 'AWS ML Services', 'Supervised Learning', 'Ethical AI'],
        'career_paths': ['AI/ML', 'Data Science', 'Cloud/DevOps'],
        'exam_url': 'https://aws.amazon.com/education/awseducate/',
        'is_free': True,
        'estimated_study_hours': 5,
    },

    # ── MongoDB (additional skill badges) ─────────────────────────────────────
    {
        'name': 'MongoDB Aggregation (Skill Badge)',
        'abbreviation': 'MongoDB-Agg',
        'provider': P.MONGODB,
        'level': L.INTERMEDIATE,
        'track': T.BACKEND,
        'description': (
            'Free MongoDB University skill badge: aggregation pipeline stages, '
            '$match, $group, $sort, $project, $lookup, and aggregation with arrays. '
            '~1.5 hours.'
        ),
        'relevant_skills': ['MongoDB Aggregation', 'Pipeline Stages', 'Data Transformation', 'NoSQL'],
        'career_paths': ['Backend Development', 'Data Engineering', 'Full-Stack Development'],
        'exam_url': 'https://learn.mongodb.com/learn/course/mongodb-aggregation',
        'is_free': True,
        'estimated_study_hours': 2,
    },
    {
        'name': 'Indexes in MongoDB (Skill Badge)',
        'abbreviation': 'MongoDB-Idx',
        'provider': P.MONGODB,
        'level': L.INTERMEDIATE,
        'track': T.BACKEND,
        'description': (
            'Free MongoDB University skill badge: single field, compound, and multikey '
            'indexes; query performance analysis with explain(); index strategies. '
            '~1.5 hours.'
        ),
        'relevant_skills': ['MongoDB Indexes', 'Query Optimization', 'Compound Indexes', 'explain()'],
        'career_paths': ['Backend Development', 'Data Engineering'],
        'exam_url': 'https://learn.mongodb.com/learn/course/mongodb-indexes',
        'is_free': True,
        'estimated_study_hours': 2,
    },

    # ── Kaggle Learn (additional courses) ─────────────────────────────────────
    {
        'name': 'Pandas (Kaggle Learn)',
        'abbreviation': 'Kaggle-Pandas',
        'provider': P.KAGGLE,
        'level': L.BEGINNER,
        'track': T.DATA,
        'description': (
            'Kaggle micro-course: DataFrames, Series, indexing, groupby, '
            'merge, reshape, and data cleaning with Pandas. '
            'Free certificate. ~4 hours.'
        ),
        'relevant_skills': ['Pandas', 'DataFrames', 'Data Wrangling', 'GroupBy', 'Merge'],
        'career_paths': ['Data Science', 'Data Analysis', 'AI/ML'],
        'exam_url': 'https://www.kaggle.com/learn/pandas',
        'is_free': True,
        'estimated_study_hours': 4,
    },
    {
        'name': 'Intermediate Machine Learning (Kaggle Learn)',
        'abbreviation': 'Kaggle-IML',
        'provider': P.KAGGLE,
        'level': L.INTERMEDIATE,
        'track': T.DATA,
        'description': (
            'Kaggle micro-course: handling missing values, categorical variables, '
            'pipelines, cross-validation, XGBoost, and data leakage. '
            'Free certificate. ~4 hours.'
        ),
        'relevant_skills': ['XGBoost', 'Cross-Validation', 'Pipelines', 'Feature Engineering', 'Scikit-learn'],
        'career_paths': ['Data Science', 'AI/ML', 'Machine Learning Engineering'],
        'exam_url': 'https://www.kaggle.com/learn/intermediate-machine-learning',
        'is_free': True,
        'estimated_study_hours': 4,
    },
    {
        'name': 'Intro to Deep Learning (Kaggle Learn)',
        'abbreviation': 'Kaggle-DL',
        'provider': P.KAGGLE,
        'level': L.INTERMEDIATE,
        'track': T.DATA,
        'description': (
            'Kaggle micro-course: neural networks, dense layers, SGD, dropout, '
            'batch normalization, and binary/multi-class classification. '
            'Free certificate. ~4 hours.'
        ),
        'relevant_skills': ['Neural Networks', 'Keras', 'TensorFlow', 'Deep Learning', 'Dropout', 'Batch Normalization'],
        'career_paths': ['AI/ML', 'Data Science', 'Machine Learning Engineering'],
        'exam_url': 'https://www.kaggle.com/learn/intro-to-deep-learning',
        'is_free': True,
        'estimated_study_hours': 4,
    },
    {
        'name': 'Feature Engineering (Kaggle Learn)',
        'abbreviation': 'Kaggle-FE',
        'provider': P.KAGGLE,
        'level': L.INTERMEDIATE,
        'track': T.DATA,
        'description': (
            'Kaggle micro-course: mutual information, creating features, '
            'clustering with k-means, PCA, and target encoding. '
            'Free certificate. ~5 hours.'
        ),
        'relevant_skills': ['Feature Engineering', 'PCA', 'Clustering', 'Target Encoding', 'Mutual Information'],
        'career_paths': ['Data Science', 'AI/ML', 'Machine Learning Engineering'],
        'exam_url': 'https://www.kaggle.com/learn/feature-engineering',
        'is_free': True,
        'estimated_study_hours': 5,
    },
    {
        'name': 'Data Visualization (Kaggle Learn)',
        'abbreviation': 'Kaggle-DataViz',
        'provider': P.KAGGLE,
        'level': L.BEGINNER,
        'track': T.DATA,
        'description': (
            'Kaggle micro-course: line charts, bar charts, scatter plots, '
            'distributions, and heat maps using Seaborn. '
            'Free certificate. ~4 hours.'
        ),
        'relevant_skills': ['Seaborn', 'Matplotlib', 'Data Visualization', 'Charts', 'Statistical Plots'],
        'career_paths': ['Data Science', 'Data Analysis', 'Business Intelligence'],
        'exam_url': 'https://www.kaggle.com/learn/data-visualization',
        'is_free': True,
        'estimated_study_hours': 4,
    },
    {
        'name': 'Advanced SQL (Kaggle Learn)',
        'abbreviation': 'Kaggle-AdvSQL',
        'provider': P.KAGGLE,
        'level': L.INTERMEDIATE,
        'track': T.BACKEND,
        'description': (
            'Kaggle micro-course: JOINs, analytic functions (window functions), '
            'nested queries, writing efficient queries with BigQuery. '
            'Free certificate. ~4 hours.'
        ),
        'relevant_skills': ['Advanced SQL', 'Window Functions', 'JOINs', 'BigQuery', 'Nested Queries'],
        'career_paths': ['Data Science', 'Backend Development', 'Data Engineering'],
        'exam_url': 'https://www.kaggle.com/learn/advanced-sql',
        'is_free': True,
        'estimated_study_hours': 4,
    },

    # ── HubSpot (additional) ───────────────────────────────────────────────────
    {
        'name': 'SEO Certification',
        'abbreviation': 'HubSpot-SEO',
        'provider': P.HUBSPOT,
        'level': L.BEGINNER,
        'track': T.MARKETING,
        'description': (
            'HubSpot Academy certification on search engine optimization: '
            'on-page SEO, link building, technical SEO, local SEO, and '
            'content strategy. Free exam + certificate. ~5–6 hours.'
        ),
        'relevant_skills': ['SEO', 'Keyword Research', 'Link Building', 'Technical SEO', 'Content Strategy'],
        'career_paths': ['Digital Marketing', 'Web Development', 'Freelancing'],
        'exam_url': 'https://app.hubspot.com/academy/certifications/seo',
        'is_free': True,
        'estimated_study_hours': 5,
    },
    {
        'name': 'Content Marketing Certification',
        'abbreviation': 'HubSpot-Content',
        'provider': P.HUBSPOT,
        'level': L.BEGINNER,
        'track': T.MARKETING,
        'description': (
            'HubSpot Academy certification: content strategy, SEO content, '
            'storytelling, repurposing content, and content promotion. '
            'Free exam + certificate. ~7–8 hours.'
        ),
        'relevant_skills': ['Content Strategy', 'Storytelling', 'SEO Content', 'Content Promotion', 'Blogging'],
        'career_paths': ['Digital Marketing', 'Copywriting', 'Freelancing'],
        'exam_url': 'https://app.hubspot.com/academy/certifications/content-marketing',
        'is_free': True,
        'estimated_study_hours': 7,
    },

    # ── Fortinet NSE Institute ─────────────────────────────────────────────────
    {
        'name': 'NSE 1: The Threat Landscape',
        'abbreviation': 'Fortinet-NSE1',
        'provider': P.FORTINET,
        'level': L.BEGINNER,
        'track': T.CYBER,
        'description': (
            'Fortinet free cybersecurity course: types of cyber threats, '
            'threat actors, and the cybersecurity landscape. '
            'Earn a free Credly digital badge. ~2 hours. '
            'Widely recognized entry-level cybersecurity credential.'
        ),
        'relevant_skills': ['Threat Awareness', 'Cybersecurity Fundamentals', 'Threat Actors', 'Cyber Attacks Overview'],
        'career_paths': ['Cybersecurity', 'IT Support', 'Networking'],
        'exam_url': 'https://training.fortinet.com/local/staticpage/view.php?page=nse_1',
        'is_free': True,
        'estimated_study_hours': 2,
    },
    {
        'name': 'NSE 2: The Evolution of Cybersecurity',
        'abbreviation': 'Fortinet-NSE2',
        'provider': P.FORTINET,
        'level': L.BEGINNER,
        'track': T.CYBER,
        'description': (
            'Fortinet free cybersecurity course: security products and solutions '
            'that address evolving cyber threats — firewalls, antivirus, '
            'VPNs, web filtering, and sandboxing. Free Credly badge. ~3 hours.'
        ),
        'relevant_skills': ['Firewalls', 'VPN', 'Web Filtering', 'Sandboxing', 'Security Products'],
        'career_paths': ['Cybersecurity', 'Networking', 'IT Support'],
        'exam_url': 'https://training.fortinet.com/local/staticpage/view.php?page=nse_2',
        'is_free': True,
        'estimated_study_hours': 3,
    },
    {
        'name': 'NSE 3: Fortinet Security Fundamentals',
        'abbreviation': 'Fortinet-NSE3',
        'provider': P.FORTINET,
        'level': L.BEGINNER,
        'track': T.CYBER,
        'description': (
            'Fortinet free cybersecurity course: Fortinet product portfolio including '
            'FortiGate, FortiAnalyzer, FortiSIEM, and cloud security solutions. '
            'Free Credly badge. ~3 hours.'
        ),
        'relevant_skills': ['FortiGate', 'SIEM Basics', 'Cloud Security', 'Endpoint Security', 'Fortinet Portfolio'],
        'career_paths': ['Cybersecurity', 'Networking', 'IT Administration'],
        'exam_url': 'https://training.fortinet.com/local/staticpage/view.php?page=nse_3',
        'is_free': True,
        'estimated_study_hours': 3,
    },

    # ── HackerRank ────────────────────────────────────────────────────────────
    {
        'name': 'Python (Basic) — HackerRank',
        'abbreviation': 'HRank-PY-Basic',
        'provider': P.HACKERRANK,
        'level': L.BEGINNER,
        'track': T.BACKEND,
        'description': (
            'HackerRank role certification: passes a 90-minute timed assessment '
            'on Python fundamentals (functions, data types, OOP basics). '
            'Free to take. Shareable certificate used in job applications. ~90 min.'
        ),
        'relevant_skills': ['Python', 'Functions', 'OOP Basics', 'Data Types', 'Problem Solving'],
        'career_paths': ['Backend Development', 'Data Science', 'Software Engineering'],
        'exam_url': 'https://www.hackerrank.com/skills-verification/python_basic',
        'is_free': True,
        'estimated_study_hours': 10,
    },
    {
        'name': 'SQL (Basic) — HackerRank',
        'abbreviation': 'HRank-SQL-Basic',
        'provider': P.HACKERRANK,
        'level': L.BEGINNER,
        'track': T.BACKEND,
        'description': (
            'HackerRank role certification: 90-minute timed SQL assessment '
            'covering SELECT, WHERE, GROUP BY, and basic JOINs. '
            'Free to take. Shareable certificate for job applications. ~90 min.'
        ),
        'relevant_skills': ['SQL', 'SELECT', 'JOIN', 'GROUP BY', 'WHERE', 'Database Queries'],
        'career_paths': ['Backend Development', 'Data Science', 'Data Engineering'],
        'exam_url': 'https://www.hackerrank.com/skills-verification/sql_basic',
        'is_free': True,
        'estimated_study_hours': 10,
    },
    {
        'name': 'JavaScript (Basic) — HackerRank',
        'abbreviation': 'HRank-JS-Basic',
        'provider': P.HACKERRANK,
        'level': L.BEGINNER,
        'track': T.WEB,
        'description': (
            'HackerRank role certification: 90-minute JavaScript assessment '
            'covering functions, closures, ES6, and DOM basics. '
            'Free to take. Shareable certificate for job applications. ~90 min.'
        ),
        'relevant_skills': ['JavaScript', 'ES6', 'Functions', 'Closures', 'DOM'],
        'career_paths': ['Web Development', 'Frontend Development', 'Software Engineering'],
        'exam_url': 'https://www.hackerrank.com/skills-verification/javascript_basic',
        'is_free': True,
        'estimated_study_hours': 10,
    },

    # ── Oracle (additional) ────────────────────────────────────────────────────
    {
        'name': 'Oracle Cloud Data Management Foundations Associate',
        'abbreviation': 'OCI-DataMgmt',
        'provider': P.ORACLE,
        'level': L.BEGINNER,
        'track': T.DATA,
        'description': (
            'Free Oracle certification exam covering Autonomous Database, '
            'Oracle MySQL HeatWave, data integration, and database migration. '
            'Free exam. ~10 hours of study via free Oracle MyLearn paths.'
        ),
        'relevant_skills': ['Oracle Autonomous Database', 'MySQL', 'Data Integration', 'Database Migration', 'Cloud Databases'],
        'career_paths': ['Data Engineering', 'Cloud/DevOps', 'Backend Development'],
        'exam_url': 'https://education.oracle.com/oracle-cloud-data-management-2025-certified-foundations-associate/trackp_OCI25DMDMFNDCFA',
        'is_free': True,
        'estimated_study_hours': 10,
    },

    # ── Microsoft Applied Skills (additional) ─────────────────────────────────
    {
        'name': 'Microsoft Applied Skills — Kubernetes on Azure',
        'abbreviation': 'MS-AppliedSkills-K8s',
        'provider': P.MICROSOFT,
        'level': L.INTERMEDIATE,
        'track': T.CLOUD,
        'description': (
            'Free Microsoft Applied Skills credential: deploy and manage '
            'containers with Azure Kubernetes Service. Lab-based, free to take. '
            'Earns a verifiable Microsoft credential. ~15 hours of prep.'
        ),
        'relevant_skills': ['Kubernetes', 'Azure Kubernetes Service', 'Docker', 'Container Management', 'Azure'],
        'career_paths': ['DevOps', 'Cloud/DevOps', 'Software Engineering'],
        'exam_url': 'https://learn.microsoft.com/en-us/credentials/applied-skills/',
        'is_free': True,
        'estimated_study_hours': 15,
    },

    # ── TESDA ─────────────────────────────────────────────────────────────────
    {
        'name': 'Computer Systems Servicing NC II',
        'abbreviation': 'TESDA-CSS-NC2',
        'provider': P.TESDA,
        'level': L.BEGINNER,
        'track': T.GENERAL,
        'tesda_nc_level': 'NC II',
        'description': (
            'TESDA National Certificate II for Computer Systems Servicing. '
            'Covers hardware assembly/troubleshooting, OS installation, '
            'networking, and basic security. Online course is FREE via e-tesda.gov.ph. '
            'NC assessment fee: ~PHP 550–1,000 (subsidized under TWSP/SeSTA scholarships). '
            'Government-recognized, ASEAN-aligned credential.'
        ),
        'relevant_skills': ['PC Assembly', 'Hardware Troubleshooting', 'OS Installation', 'Networking Basics', 'Safety'],
        'career_paths': ['IT Support', 'Networking', 'General IT'],
        'exam_url': 'https://e-tesda.gov.ph/',
        'study_guide_url': 'https://tesda.gov.ph/',
        'is_free': True,
        'optional_paid_upgrade': 'NC Assessment fee: ~PHP 550–1,000 for the official National Certificate (free under TESDA TWSP/SeSTA scholarships)',
        'estimated_study_hours': 80,
    },
    {
        'name': 'Web Development (TESDA Online)',
        'abbreviation': 'TESDA-WebDev',
        'provider': P.TESDA,
        'level': L.BEGINNER,
        'track': T.WEB,
        'description': (
            'TESDA Online Program free course on web development fundamentals: '
            'HTML, CSS, and basic JavaScript. '
            'Earn a free Certificate of Completion from TESDA. ~40–80 hours.'
        ),
        'relevant_skills': ['HTML', 'CSS', 'JavaScript Basics', 'Web Design'],
        'career_paths': ['Web Development', 'Frontend Development'],
        'exam_url': 'https://e-tesda.gov.ph/',
        'is_free': True,
        'estimated_study_hours': 60,
    },
    {
        'name': 'Animation NC II (TESDA Online)',
        'abbreviation': 'TESDA-Anim-NC2',
        'provider': P.TESDA,
        'level': L.BEGINNER,
        'track': T.MOBILE,
        'tesda_nc_level': 'NC II',
        'description': (
            'TESDA National Certificate II for Animation. Covers 2D/3D animation '
            'principles, digital tools, and media production. '
            'Online course is FREE via e-tesda.gov.ph. '
            'NC assessment: ~PHP 550–1,000 (subsidized under TWSP scholarships).'
        ),
        'relevant_skills': ['2D Animation', 'Digital Media', 'Animation Principles', 'Media Production'],
        'career_paths': ['Game Development', 'Mobile Development', 'UI/UX', 'Media'],
        'exam_url': 'https://e-tesda.gov.ph/',
        'is_free': True,
        'optional_paid_upgrade': 'NC Assessment fee: ~PHP 550–1,000 for the official National Certificate',
        'estimated_study_hours': 120,
    },
]


class Command(BaseCommand):
    help = 'Seed the database with curated free certifications for Filipino CCS students.'

    def add_arguments(self, parser):
        parser.add_argument(
            '--update',
            action='store_true',
            help='Update existing certifications if they already exist (matched by name+provider)',
        )
        parser.add_argument(
            '--clear',
            action='store_true',
            help='Delete all existing Certification records before seeding',
        )

    def handle(self, *args, **options):
        if options['clear']:
            count = Certification.objects.all().count()
            Certification.objects.all().delete()
            self.stdout.write(self.style.WARNING(f'Cleared {count} existing certifications.'))

        created = 0
        updated = 0
        skipped = 0

        for data in FREE_CERTS:
            # Pop the track separately since it may not exist on older DBs
            # (migration must run first)
            track = data.pop('track', Certification.Track.GENERAL)
            optional_paid = data.pop('optional_paid_upgrade', '')

            defaults = {**data, 'track': track, 'optional_paid_upgrade': optional_paid}
            name = defaults['name']
            provider = defaults['provider']

            if options['update']:
                obj, was_created = Certification.objects.update_or_create(
                    name=name, provider=provider,
                    defaults=defaults,
                )
                if was_created:
                    created += 1
                else:
                    updated += 1
            else:
                if Certification.objects.filter(name=name, provider=provider).exists():
                    skipped += 1
                else:
                    Certification.objects.create(**defaults)
                    created += 1

        self.stdout.write(
            self.style.SUCCESS(
                f'Done. Created: {created} | Updated: {updated} | Skipped (already exist): {skipped}'
            )
        )
        self.stdout.write(
            'Run with --update to refresh existing records, or --clear to wipe and re-seed.'
        )
