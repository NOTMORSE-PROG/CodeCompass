"""
Onboarding quiz — discovery-oriented, not assumption-based.

PURPOSE OF THIS QUIZ:
  Students don't know their IT path yet — that's exactly why CodeCompass exists.
  Questions should DISCOVER what suits them (interests, strengths, working style),
  not ask them to already know answers like "what's your dream career."
  The AI reads the quiz_summary and generates a personalized roadmap from it.

INCOMING STUDENTS — discovery flow:
  The student likely has zero IT background. Ask what they enjoy, how they
  think, what problems excite them. No assumptions about IT knowledge.

UNDERGRADUATE / SHIFTER — skill assessment flow:
  They're already in CCS or shifting into it. Ask what they know,
  what they're struggling with, and what direction they're leaning.
  The AI then fills in the gaps and strengthens weak areas.

─── Question flow ──────────────────────────────────────────────────────────────

INCOMING (audience='incoming'):
  Order 1  │ tried_coding       │ Have you tried coding before? (gates tech questions)
  Order 5  │ enjoy_activities   │ What kinds of activities do you naturally enjoy?
  Order 10 │ it_excites_you     │ What excites you about working in IT?
  Order 15 │ problem_you_want   │ What kind of problems do you want to solve someday?
  Order 30 │ learning_style     │ (shared) How do you prefer to learn?
  Order 40 │ tech_tried         │ [has experience] What tech have you already tried?
  Order 60 │ shs_subjects       │ Which SHS subjects did you enjoy most?
  Order 70 │ excitement         │ How excited are you? (scale 1-5)

UNDERGRADUATE (audience='undergraduate'):
  Order 5  │ year_level         │ What year level are you?
  Order 8  │ prior_cs_courses   │ [shifter/transferee] Which CS subjects have you done?
  Order 15 │ it_focus           │ Which IT area are you currently most drawn to?
  Order 30 │ learning_style     │ (shared) How do you prefer to learn?
  Order 40 │ tech_web/mobile/   │ Tech stack branched by it_focus
           │ data/security/other│
  Order 50 │ sub_web/mobile/    │ Sub-interest focus branched by it_focus
           │ data/security      │
  Order 55 │ confidence         │ Confidence in programming (scale 1-5)
  Order 60 │ skills_improve     │ What skills do you want to improve most?
  Order 65 │ experience_text    │ Describe your projects / internships

SHARED (audience='both'):
  Order 30 │ learning_style     │ How do you prefer to learn?

─── Branching ──────────────────────────────────────────────────────────────────
  {}                          → always show
  {"key":"k","values":["v"]}  → show if question keyed 'k' was answered with 'v'
  {"all":[...]}               → AND
  {"any":[...]}               → OR
  "ref not found → true"      → undergrads auto-pass incoming-only gates
"""
from django.core.management.base import BaseCommand
from apps.onboarding.models import QuizQuestion


def has_experience():
    """Incoming gate: only show tech questions if they've tried coding.
    Undergrads: tried_coding key not found → condition passes automatically."""
    return {'key': 'tried_coding', 'values': ['yes_html', 'yes_python', 'yes_scratch']}


def focus_and_experience(focus_values):
    """Show tech/sub questions if: IT focus matches AND has some experience.
    Undergrads always pass the experience gate (key not found → true)."""
    return {
        'all': [
            {'key': 'it_focus', 'values': focus_values},
            has_experience(),
        ]
    }


QUESTIONS = [

    # ─────────────────────────────────────────────────────────────────────────
    # INCOMING STUDENTS — Discovery questions
    # Goal: surface interests, strengths, and working style so the AI can
    # map them to the right IT path. No IT knowledge assumed.
    # ─────────────────────────────────────────────────────────────────────────

    {
        'key': 'tried_coding',
        'order': 1, 'audience': 'incoming', 'category': 'background',
        'question_type': 'single_choice',
        'question_text': 'Have you tried any programming or coding before?',
        'question_text_tagalog': 'Nasubukan mo na bang mag-code o mag-program kahit paano?',
        'conditions': {},
        'options': [
            {'label': 'Yes — I know some HTML / CSS basics', 'value': 'yes_html'},
            {'label': 'Yes — I have tried Python or JavaScript', 'value': 'yes_python'},
            {'label': 'Yes — I used Scratch or block-based coding', 'value': 'yes_scratch'},
            {'label': "No — I'm a complete beginner, starting from zero!", 'value': 'no_never'},
        ],
    },
    {
        'key': 'enjoy_activities',
        'order': 5, 'audience': 'incoming', 'category': 'interest',
        'question_type': 'multi_choice',
        'question_text': 'What kinds of activities do you naturally enjoy? (Select all that apply)',
        'question_text_tagalog': 'Anong mga aktibidad ang natural mong na-eenjoy? (Pumili ng lahat ng applicable)',
        'conditions': {},
        'options': [
            {'label': 'Creating visual things — art, design, layouts', 'value': 'visual_creative'},
            {'label': 'Solving logical puzzles and math problems', 'value': 'logic_math'},
            {'label': 'Building, tinkering, and figuring out how things work', 'value': 'building_tinkering'},
            {'label': 'Writing stories, creating characters, or game worlds', 'value': 'storytelling_games'},
            {'label': 'Spotting patterns in data and numbers', 'value': 'data_patterns'},
            {'label': 'Finding security flaws or breaking rules (ethically!)', 'value': 'security_hacking'},
            {'label': 'Organizing teams, planning, and leading projects', 'value': 'organizing_leading'},
            {'label': 'Helping and communicating with people', 'value': 'helping_people'},
        ],
    },
    {
        'key': 'it_excites_you',
        'order': 10, 'audience': 'incoming', 'category': 'interest',
        'question_type': 'multi_choice',
        'question_text': 'When you think about working in IT, what sounds most exciting to you? (Select all that apply)',
        'question_text_tagalog': 'Kapag naiisip mo ang trabaho sa IT, ano ang pinaka-exciting para sa iyo? (Pumili ng lahat ng applicable)',
        'conditions': {},
        'options': [
            {'label': 'Building apps or websites that millions of people will use', 'value': 'build_apps'},
            {'label': 'Creating games or interactive digital experiences', 'value': 'games'},
            {'label': 'Training AI that can think, learn, and solve problems', 'value': 'ai_ml'},
            {'label': 'Turning raw data into insights that drive decisions', 'value': 'data_insights'},
            {'label': 'Protecting systems and stopping hackers', 'value': 'cybersecurity'},
            {'label': 'Designing beautiful, easy-to-use interfaces', 'value': 'uiux'},
            {'label': 'Managing cloud infrastructure that powers the internet', 'value': 'cloud_infra'},
            {'label': "I'm not sure yet — I want to explore everything", 'value': 'not_sure'},
        ],
    },
    {
        'key': 'problem_you_want',
        'order': 15, 'audience': 'incoming', 'category': 'goal',
        'question_type': 'single_choice',
        'question_text': 'What kind of impact do you want to have with your IT career?',
        'question_text_tagalog': 'Anong uri ng epekto ang gusto mong gawin sa pamamagitan ng iyong IT career?',
        'conditions': {},
        'options': [
            {'label': 'Build products people use every day (apps, platforms, tools)', 'value': 'build_products'},
            {'label': 'Protect people and organizations from cyber threats', 'value': 'protect_cyber'},
            {'label': 'Use data and AI to solve real-world problems', 'value': 'data_ai_impact'},
            {'label': 'Create immersive games and digital entertainment', 'value': 'entertainment'},
            {'label': 'Design experiences that are simple, beautiful, and accessible', 'value': 'design_experience'},
            {'label': 'Power the infrastructure that the internet runs on', 'value': 'infrastructure'},
            {'label': "I don't know yet — I just know I love technology", 'value': 'explore'},
        ],
    },

    # ─────────────────────────────────────────────────────────────────────────
    # SHARED — Learning style (shown to both incoming and undergraduate)
    # ─────────────────────────────────────────────────────────────────────────

    {
        'key': 'learning_style',
        'order': 30, 'audience': 'both', 'category': 'learning_style',
        'question_type': 'multi_choice',
        'question_text': 'How do you prefer to learn new skills? (Select all that apply)',
        'question_text_tagalog': 'Paano ka mas gusto mag-aral ng bagong mga kasanayan? (Pumili ng lahat ng applicable)',
        'conditions': {},
        'options': [
            {'label': 'Watching video tutorials (YouTube, Udemy)', 'value': 'video'},
            {'label': 'Reading documentation and articles', 'value': 'reading'},
            {'label': 'Building hands-on projects', 'value': 'projects'},
            {'label': 'Taking structured online courses (Coursera, edX)', 'value': 'courses'},
            {'label': 'Pair programming or study groups', 'value': 'collaborative'},
        ],
    },

    # ─────────────────────────────────────────────────────────────────────────
    # INCOMING — Tech (only if they've already tried coding)
    # ─────────────────────────────────────────────────────────────────────────

    {
        'key': 'tech_tried',
        'order': 40, 'audience': 'incoming', 'category': 'background',
        'question_type': 'multi_choice',
        'question_text': 'What technologies or tools have you already tried? (Select all that apply)',
        'question_text_tagalog': 'Anong mga technology o tools na ang nasubukan mo? (Pumili ng lahat ng applicable)',
        'conditions': has_experience(),
        'options': [
            {'label': 'HTML / CSS (web structure and styling)', 'value': 'html_css'},
            {'label': 'JavaScript (web interactivity)', 'value': 'javascript'},
            {'label': 'Python (scripting, data, or AI basics)', 'value': 'python'},
            {'label': 'Java or C++ (school programming subjects)', 'value': 'java_cpp'},
            {'label': 'Scratch or block-based coding', 'value': 'scratch'},
            {'label': 'SQL or databases', 'value': 'sql'},
            {'label': 'Mobile apps (Android Studio, etc.)', 'value': 'mobile_basics'},
            {'label': 'None of these specifically', 'value': 'none'},
        ],
    },

    # ─────────────────────────────────────────────────────────────────────────
    # INCOMING — Closing questions
    # ─────────────────────────────────────────────────────────────────────────

    {
        'key': 'shs_subjects',
        'order': 60, 'audience': 'incoming', 'category': 'aptitude',
        'question_type': 'multi_choice',
        'question_text': 'Which subjects did you enjoy most in Senior High School? (Select all that apply)',
        'question_text_tagalog': 'Anong mga subject ang pinaka-enjoy mo sa Senior High? (Pumili ng isa o higit pa)',
        'conditions': {},
        'options': [
            {'label': 'Mathematics', 'value': 'math'},
            {'label': 'Science / Physics / Chemistry', 'value': 'science'},
            {'label': 'Programming / ICT', 'value': 'programming'},
            {'label': 'English / Communication', 'value': 'english'},
            {'label': 'Research and Writing', 'value': 'research'},
            {'label': 'Arts / Multimedia Design', 'value': 'arts'},
            {'label': 'Statistics / Data Handling', 'value': 'statistics'},
        ],
    },
    {
        'key': 'excitement',
        'order': 70, 'audience': 'incoming', 'category': 'interest',
        'question_type': 'scale',
        'question_text': 'How excited are you about starting your IT journey? (1 = I have no idea where to start, 5 = I am ready to go!)',
        'question_text_tagalog': 'Gaano ka ka-excited magsimula ng iyong IT journey? (1 = Wala pa akong ideya, 5 = Handa na ako!)',
        'conditions': {},
        'options': [],
    },

    # ─────────────────────────────────────────────────────────────────────────
    # UNDERGRADUATE / SHIFTER — Skill assessment flow
    # They're already in CCS or shifting in. Ask what they know,
    # what direction they're leaning, and where they need to improve.
    # ─────────────────────────────────────────────────────────────────────────

    {
        'key': 'year_level',
        'order': 5, 'audience': 'undergraduate', 'category': 'background',
        'question_type': 'single_choice',
        'question_text': 'What year level are you currently in?',
        'question_text_tagalog': 'Anong year level ka na ngayon?',
        'conditions': {},
        'options': [
            {'label': '1st Year', 'value': '1st'},
            {'label': '2nd Year', 'value': '2nd'},
            {'label': '3rd Year', 'value': '3rd'},
            {'label': '4th Year', 'value': '4th'},
            {'label': '5th Year / Irregular', 'value': '5th'},
            {'label': 'Shifter (new to CCS)', 'value': 'shifter'},
            {'label': 'Transferee (from another school)', 'value': 'transferee'},
        ],
    },
    {
        'key': 'prior_cs_courses',
        'order': 8, 'audience': 'undergraduate', 'category': 'background',
        'question_type': 'multi_choice',
        'question_text': 'Which programming / CS subjects have you already completed? (Select all that apply)',
        'question_text_tagalog': 'Anong mga programming o CS subject na ang natapos mo? (Pumili ng lahat ng applicable)',
        'conditions': {'key': 'year_level', 'values': ['shifter', 'transferee']},
        'options': [
            {'label': 'Programming Fundamentals / Intro to CS', 'value': 'prog_fundamentals'},
            {'label': 'Object-Oriented Programming (OOP)', 'value': 'oop'},
            {'label': 'Data Structures & Algorithms', 'value': 'dsa'},
            {'label': 'Web Development / HTML-CSS-JS', 'value': 'web_dev'},
            {'label': 'Database Management / SQL', 'value': 'databases'},
            {'label': 'Networking / Computer Networks', 'value': 'networking'},
            {'label': 'Software Engineering / Systems Analysis', 'value': 'software_eng'},
            {'label': 'None of these yet', 'value': 'none'},
        ],
    },
    {
        'key': 'it_focus',
        'order': 15, 'audience': 'undergraduate', 'category': 'interest',
        'question_type': 'single_choice',
        'question_text': 'Which area of IT are you currently most drawn to or focusing on?',
        'question_text_tagalog': 'Anong larangan ng IT ang pinaka-naaakit sa iyo ngayon o pinagtu-tuunan mo?',
        'conditions': {},
        'options': [
            {'label': 'Web Development (frontend, backend, or full stack)', 'value': 'web_dev'},
            {'label': 'Mobile App Development (Android, iOS, or cross-platform)', 'value': 'mobile_dev'},
            {'label': 'Data Science / Analytics', 'value': 'data_science'},
            {'label': 'AI / Machine Learning', 'value': 'ai_ml'},
            {'label': 'Cybersecurity', 'value': 'cybersecurity'},
            {'label': 'Game Development', 'value': 'game_dev'},
            {'label': 'Networking / Cloud Computing / DevOps', 'value': 'networking_cloud'},
            {'label': 'UI/UX Design', 'value': 'uiux'},
            {'label': "I'm not sure yet — help me figure it out", 'value': 'not_sure'},
        ],
    },

    # ── Undergraduate tech stack questions (order 40, branched by it_focus) ──

    {
        'key': 'tech_web',
        'order': 40, 'audience': 'undergraduate', 'category': 'background',
        'question_type': 'multi_choice',
        'question_text': 'Which web technologies do you already know? (Select all that apply)',
        'question_text_tagalog': 'Anong mga web technology na ang alam mo? (Pumili ng lahat ng applicable)',
        'conditions': {'key': 'it_focus', 'values': ['web_dev']},
        'options': [
            {'label': 'HTML / CSS', 'value': 'html_css'},
            {'label': 'JavaScript', 'value': 'javascript'},
            {'label': 'React / Vue / Angular', 'value': 'frontend_framework'},
            {'label': 'Node.js / Express', 'value': 'nodejs'},
            {'label': 'PHP / Laravel', 'value': 'php'},
            {'label': 'Python / Django / Flask', 'value': 'python_web'},
            {'label': 'SQL / MySQL / PostgreSQL', 'value': 'sql'},
            {'label': 'None yet — still learning', 'value': 'none'},
        ],
    },
    {
        'key': 'tech_mobile',
        'order': 40, 'audience': 'undergraduate', 'category': 'background',
        'question_type': 'multi_choice',
        'question_text': 'Which mobile technologies do you already know? (Select all that apply)',
        'question_text_tagalog': 'Anong mga mobile technology na ang alam mo? (Pumili ng lahat ng applicable)',
        'conditions': {'key': 'it_focus', 'values': ['mobile_dev']},
        'options': [
            {'label': 'Java (Android)', 'value': 'java_android'},
            {'label': 'Kotlin (Android)', 'value': 'kotlin'},
            {'label': 'Swift / Objective-C (iOS)', 'value': 'swift'},
            {'label': 'Dart / Flutter (cross-platform)', 'value': 'flutter'},
            {'label': 'React Native', 'value': 'react_native'},
            {'label': 'None yet — still learning', 'value': 'none'},
        ],
    },
    {
        'key': 'tech_data',
        'order': 40, 'audience': 'undergraduate', 'category': 'background',
        'question_type': 'multi_choice',
        'question_text': 'Which data / AI tools do you already know? (Select all that apply)',
        'question_text_tagalog': 'Anong mga data / AI tools na ang alam mo? (Pumili ng lahat ng applicable)',
        'conditions': {'key': 'it_focus', 'values': ['data_science', 'ai_ml']},
        'options': [
            {'label': 'Python', 'value': 'python'},
            {'label': 'R', 'value': 'r_lang'},
            {'label': 'SQL / Database querying', 'value': 'sql'},
            {'label': 'Pandas / NumPy', 'value': 'pandas_numpy'},
            {'label': 'TensorFlow / PyTorch / Keras', 'value': 'deep_learning'},
            {'label': 'Scikit-learn / ML libraries', 'value': 'sklearn'},
            {'label': 'Tableau / Power BI', 'value': 'visualization'},
            {'label': 'None yet — still learning', 'value': 'none'},
        ],
    },
    {
        'key': 'tech_security',
        'order': 40, 'audience': 'undergraduate', 'category': 'background',
        'question_type': 'multi_choice',
        'question_text': 'Which cybersecurity tools or skills do you already have? (Select all that apply)',
        'question_text_tagalog': 'Anong mga cybersecurity tools o skills na ang mayroon ka? (Pumili ng lahat ng applicable)',
        'conditions': {'key': 'it_focus', 'values': ['cybersecurity']},
        'options': [
            {'label': 'Python (scripting / automation)', 'value': 'python'},
            {'label': 'C / C++ (low-level)', 'value': 'c_cpp'},
            {'label': 'Linux / Bash scripting', 'value': 'linux_bash'},
            {'label': 'Networking fundamentals (TCP/IP, DNS)', 'value': 'networking'},
            {'label': 'Kali Linux / pentesting tools', 'value': 'kali'},
            {'label': 'CTF competitions', 'value': 'ctf'},
            {'label': 'None yet — still learning', 'value': 'none'},
        ],
    },
    {
        'key': 'tech_other',
        'order': 40, 'audience': 'undergraduate', 'category': 'background',
        'question_type': 'multi_choice',
        'question_text': 'Which technologies or tools do you already know? (Select all that apply)',
        'question_text_tagalog': 'Anong mga technology o tools na ang alam mo? (Pumili ng lahat ng applicable)',
        'conditions': {'key': 'it_focus', 'values': ['game_dev', 'networking_cloud', 'uiux']},
        'options': [
            {'label': 'Python', 'value': 'python'},
            {'label': 'C# / Unity (game dev)', 'value': 'csharp_unity'},
            {'label': 'C++ / Unreal Engine', 'value': 'cpp_unreal'},
            {'label': 'AWS / Azure / GCP (cloud)', 'value': 'cloud_platforms'},
            {'label': 'Docker / Kubernetes', 'value': 'devops_tools'},
            {'label': 'Figma / Adobe XD (UI/UX design)', 'value': 'design_tools'},
            {'label': 'Blender / 3D tools', 'value': 'blender'},
            {'label': 'None yet — still learning', 'value': 'none'},
        ],
    },

    # ── Undergraduate sub-interest (order 50) ─────────────────────────────────

    {
        'key': 'sub_web',
        'order': 50, 'audience': 'undergraduate', 'category': 'interest',
        'question_type': 'single_choice',
        'question_text': 'Which side of web development excites you more?',
        'question_text_tagalog': 'Aling bahagi ng web development ang mas exciting para sa iyo?',
        'conditions': {'key': 'it_focus', 'values': ['web_dev']},
        'options': [
            {'label': 'Frontend — building beautiful, interactive UIs', 'value': 'frontend'},
            {'label': 'Backend — servers, APIs, and databases', 'value': 'backend'},
            {'label': 'Full Stack — I want to do both!', 'value': 'fullstack'},
            {'label': 'DevOps / Cloud — deployment and infrastructure', 'value': 'devops'},
        ],
    },
    {
        'key': 'sub_mobile',
        'order': 50, 'audience': 'undergraduate', 'category': 'interest',
        'question_type': 'single_choice',
        'question_text': 'What type of mobile development are you most interested in?',
        'question_text_tagalog': 'Anong uri ng mobile development ang pinaka-interesante mo?',
        'conditions': {'key': 'it_focus', 'values': ['mobile_dev']},
        'options': [
            {'label': 'Android development (Kotlin / Java)', 'value': 'android'},
            {'label': 'iOS development (Swift)', 'value': 'ios'},
            {'label': 'Cross-platform (Flutter / React Native)', 'value': 'cross_platform'},
            {'label': "I'm open to any platform", 'value': 'open'},
        ],
    },
    {
        'key': 'sub_data',
        'order': 50, 'audience': 'undergraduate', 'category': 'interest',
        'question_type': 'single_choice',
        'question_text': 'Which data / AI track excites you most?',
        'question_text_tagalog': 'Anong data / AI track ang pinaka-exciting para sa iyo?',
        'conditions': {'key': 'it_focus', 'values': ['data_science', 'ai_ml']},
        'options': [
            {'label': 'Machine Learning / AI Engineering', 'value': 'ml_ai'},
            {'label': 'Data Analytics / Business Intelligence', 'value': 'analytics'},
            {'label': 'Data Engineering / Big Data pipelines', 'value': 'data_engineering'},
            {'label': 'Computer Vision / NLP / Generative AI', 'value': 'advanced_ai'},
            {'label': "I'm still exploring", 'value': 'exploring'},
        ],
    },
    {
        'key': 'sub_security',
        'order': 50, 'audience': 'undergraduate', 'category': 'interest',
        'question_type': 'single_choice',
        'question_text': 'Which area of cybersecurity interests you most?',
        'question_text_tagalog': 'Anong larangan ng cybersecurity ang pinaka-interesante para sa iyo?',
        'conditions': {'key': 'it_focus', 'values': ['cybersecurity']},
        'options': [
            {'label': 'Ethical Hacking / Penetration Testing', 'value': 'pentesting'},
            {'label': 'Network Security / Firewall / IDS', 'value': 'network_security'},
            {'label': 'Cloud Security / DevSecOps', 'value': 'cloud_security'},
            {'label': 'Digital Forensics / Incident Response', 'value': 'forensics'},
            {'label': "I'm still exploring", 'value': 'exploring'},
        ],
    },

    # ── Undergraduate closing ─────────────────────────────────────────────────

    {
        'key': 'confidence',
        'order': 55, 'audience': 'undergraduate', 'category': 'aptitude',
        'question_type': 'scale',
        'question_text': 'How confident are you in your current programming skills? (1 = Still a beginner, 5 = Very confident)',
        'question_text_tagalog': 'Gaano ka ka-confident sa iyong kasalukuyang programming skills? (1 = Baguhan pa rin, 5 = Very confident)',
        'conditions': {},
        'options': [],
    },
    {
        'key': 'skills_improve',
        'order': 60, 'audience': 'undergraduate', 'category': 'aptitude',
        'question_type': 'multi_choice',
        'question_text': 'What skills do you most want to strengthen? (Select up to 3)',
        'question_text_tagalog': 'Anong mga kasanayan ang gusto mong palakasin? (Pumili ng hanggang 3)',
        'conditions': {},
        'options': [
            {'label': 'Programming fundamentals', 'value': 'fundamentals'},
            {'label': 'Data structures & algorithms', 'value': 'dsa'},
            {'label': 'Web / mobile development', 'value': 'web_mobile_dev'},
            {'label': 'Database design & SQL', 'value': 'database'},
            {'label': 'System design & architecture', 'value': 'system_design'},
            {'label': 'Soft skills / communication', 'value': 'soft_skills'},
            {'label': 'Version control (Git)', 'value': 'git'},
            {'label': 'DevOps / cloud deployment', 'value': 'devops'},
            {'label': 'AI / Machine Learning basics', 'value': 'ai_basics'},
        ],
    },
    {
        'key': 'experience_text',
        'order': 65, 'audience': 'undergraduate', 'category': 'background',
        'question_type': 'text',
        'question_text': "Briefly describe any projects, internships, or work experience you've had in IT (or write 'None yet').",
        'question_text_tagalog': "Maikling ilarawan ang anumang projects, internship, o work experience mo sa IT (o isulat ang 'Wala pa').",
        'conditions': {},
        'options': [],
    },
]


class Command(BaseCommand):
    help = 'Seed onboarding quiz questions.'

    def add_arguments(self, parser):
        parser.add_argument('--clear', action='store_true',
                            help='Delete all existing questions before seeding.')

    def handle(self, *args, **options):
        if options['clear']:
            deleted, _ = QuizQuestion.objects.all().delete()
            self.stdout.write(self.style.WARNING(f'Deleted {deleted} existing questions.'))

        created_count = 0
        for q in QUESTIONS:
            obj, created = QuizQuestion.objects.get_or_create(
                key=q['key'],
                audience=q['audience'],
                defaults={
                    'order': q['order'],
                    'category': q['category'],
                    'question_type': q['question_type'],
                    'question_text': q['question_text'],
                    'question_text_tagalog': q['question_text_tagalog'],
                    'options': q['options'],
                    'conditions': q['conditions'],
                    'is_active': True,
                },
            )
            if created:
                created_count += 1

        total = QuizQuestion.objects.count()
        self.stdout.write(
            self.style.SUCCESS(
                f'Done. Created {created_count} new questions. Total in DB: {total}.'
            )
        )
