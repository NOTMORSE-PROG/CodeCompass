"""Seed real Philippine universities with CCS programs.

Tuition figures are per-semester PHP estimates based on AY 2025-2026 published rates.
State universities (SUCs) are covered by RA 10931 (Universal Access to Quality
Tertiary Education Act) — qualified students pay ₱0; the max shown is the
non-scholarship / non-resident rate.

Data verified and updated April 2026. Sources: official university websites,
PAASCU/PACUCOA/AACCUP accreditation records, and CHED announcements.

Usage:
    python manage.py seed_universities
    python manage.py seed_universities --clear   # wipe and re-seed
"""
from django.core.management.base import BaseCommand
from apps.universities.models import University, CCSProgram

UNIVERSITIES = [
    # ══════════════════════════════════════════════════════════════════════
    # NCR — STATE
    # ══════════════════════════════════════════════════════════════════════
    {
        'name': 'University of the Philippines Diliman',
        'abbreviation': 'UP Diliman',
        'university_type': 'state',
        'region': 'NCR',
        'province': '',
        'city': 'Quezon City',
        'website_url': 'https://upd.edu.ph',
        'ched_coe': True,
        'ched_cod': False,
        'accreditation_level': 4,
        # Free under RA 10931; ₱1,500/unit socialized rate for non-qualified
        'tuition_range_min': 0,
        'tuition_range_max': 33000,
        'programs': [
            {
                'name': 'BS Computer Science',
                'abbreviation': 'BSCS',
                'description': (
                    'Flagship CS program in the Philippines. Covers algorithms, '
                    'systems, AI/ML, theory of computation, and software engineering. '
                    'CHED Center of Excellence. Accredited Level 4 (PAASCU).'
                ),
                'specializations': ['Algorithms & Theory', 'AI / Machine Learning',
                                    'Systems & Networks', 'Software Engineering'],
                'duration_years': 4,
                'has_board_exam': False,
                'curriculum_url': (
                    'https://our.upd.edu.ph/files/Checklist/UG/COE/'
                    'COE_Bachelor%20of%20Science%20in%20Computer%20Science.pdf'
                ),
            },
        ],
    },
    {
        'name': 'Polytechnic University of the Philippines',
        'abbreviation': 'PUP',
        'university_type': 'state',
        'region': 'NCR',
        'province': '',
        'city': 'Manila',
        'website_url': 'https://www.pup.edu.ph',
        'ched_coe': False,
        'ched_cod': True,
        'accreditation_level': 3,
        # Free under RA 10931
        'tuition_range_min': 0,
        'tuition_range_max': 5000,
        'programs': [
            {
                'name': 'BS Information Technology',
                'abbreviation': 'BSIT',
                'description': (
                    'Covers web/mobile development, networking, database systems, '
                    'and IT infrastructure management. CHED Center of Development.'
                ),
                'specializations': ['Web Development', 'Network Technology',
                                    'Database Systems'],
                'duration_years': 4,
                'has_board_exam': False,
                'curriculum_url': 'https://www.pup.edu.ph/academic/programs',
            },
            {
                'name': 'BS Computer Science',
                'abbreviation': 'BSCS',
                'description': (
                    'Focuses on computing theory, software development, '
                    'and data structures. Offered at PUP Main Campus in Manila.'
                ),
                'specializations': [],
                'duration_years': 4,
                'has_board_exam': False,
                'curriculum_url': 'https://www.pup.edu.ph/academic/programs',
            },
        ],
    },
    {
        'name': 'Technological University of the Philippines',
        'abbreviation': 'TUP',
        'university_type': 'state',
        'region': 'NCR',
        'province': '',
        'city': 'Manila',
        'website_url': 'https://www.tup.edu.ph',
        'ched_coe': False,
        'ched_cod': False,
        'accreditation_level': 2,
        # Free under RA 10931
        'tuition_range_min': 0,
        'tuition_range_max': 5000,
        'programs': [
            {
                'name': 'BS Information Technology',
                'abbreviation': 'BSIT',
                'description': (
                    'Technology-focused IT curriculum with strong emphasis on '
                    'applied computing, networking, and system administration.'
                ),
                'specializations': ['Networking', 'System Administration'],
                'duration_years': 4,
                'has_board_exam': False,
                'curriculum_url': 'https://tup.edu.ph/pages/admission/courses',
            },
            {
                'name': 'BS Computer Engineering',
                'abbreviation': 'BSCE',
                'description': (
                    'Combines electrical engineering fundamentals with computer '
                    'science — hardware design, embedded systems, and IoT.'
                ),
                'specializations': ['Embedded Systems', 'Hardware Design', 'IoT'],
                'duration_years': 4,
                'has_board_exam': True,
                'curriculum_url': 'https://tup.edu.ph/pages/admission/courses',
            },
        ],
    },
    # ══════════════════════════════════════════════════════════════════════
    # NCR — PRIVATE
    # ══════════════════════════════════════════════════════════════════════
    {
        'name': 'De La Salle University',
        'abbreviation': 'DLSU',
        'university_type': 'private',
        'region': 'NCR',
        'province': '',
        'city': 'Manila',
        'website_url': 'https://www.dlsu.edu.ph',
        'ched_coe': True,
        'ched_cod': False,
        'accreditation_level': 4,
        # ₱4,175/unit; 20-unit sem ≈ ₱83,500 + 3% increase; ~₱82,000–₱95,000/sem
        'tuition_range_min': 82000,
        'tuition_range_max': 95000,
        'programs': [
            {
                'name': 'BS Computer Science',
                'abbreviation': 'BSCS',
                'description': (
                    'One of the top CS programs in the Philippines offered at the '
                    'College of Computer Studies. Specializations in Software Technology, '
                    'Network Engineering, and Computer Systems Engineering. '
                    'CHED Center of Excellence. PAASCU Level 4.'
                ),
                'specializations': ['Software Technology', 'Network Engineering',
                                    'Computer Systems Engineering'],
                'duration_years': 4,
                'has_board_exam': False,
                'curriculum_url': 'https://www.dlsu.edu.ph/colleges/ccs/undergraduate-degree-programs/',
            },
            {
                'name': 'BS Information Systems',
                'abbreviation': 'BSIS',
                'description': (
                    'Bridges business and technology — covers enterprise systems, '
                    'business analytics, and IT management. Offered at the CCS.'
                ),
                'specializations': ['Business Analytics', 'Enterprise Systems'],
                'duration_years': 4,
                'has_board_exam': False,
                'curriculum_url': 'https://www.dlsu.edu.ph/colleges/ccs/undergraduate-degree-programs/',
            },
        ],
    },
    {
        'name': 'Ateneo de Manila University',
        'abbreviation': 'ADMU',
        'university_type': 'private',
        'region': 'NCR',
        'province': '',
        'city': 'Quezon City',
        'website_url': 'https://www.ateneo.edu',
        'ched_coe': True,
        'ched_cod': False,
        'accreditation_level': 4,
        # ₱5,200/unit × 20 units + ₱16,574 basic fees ≈ ₱120,574 (AY 2025-2026)
        'tuition_range_min': 104000,
        'tuition_range_max': 125000,
        'programs': [
            {
                'name': 'BS Computer Science',
                'abbreviation': 'BSCS',
                'description': (
                    'Rigorous, research-oriented CS program at the School of '
                    'Science and Engineering. Specializations in Data Science, '
                    'Software Engineering, and Intelligent Systems. '
                    'CHED CoE; PAASCU Level 4.'
                ),
                'specializations': ['Data Science', 'Software Engineering',
                                    'Intelligent Systems', 'Human-Computer Interaction'],
                'duration_years': 4,
                'has_board_exam': False,
                'curriculum_url': 'https://www.ateneo.edu/sites/default/files/2023-05/BS%20CS%20Program%20of%20Study.pdf',
            },
            {
                'name': 'BS Management Information Systems',
                'abbreviation': 'BSMIS',
                'description': (
                    'Combines business management and IT. Prepares students for '
                    'systems analysis, IT consulting, and business intelligence roles.'
                ),
                'specializations': ['Business Intelligence', 'IT Consulting',
                                    'Systems Analysis'],
                'duration_years': 4,
                'has_board_exam': False,
                'curriculum_url': 'https://www.ateneo.edu/sose/discs/academics/undergraduate',
            },
        ],
    },
    {
        'name': 'Far Eastern University',
        'abbreviation': 'FEU',
        'university_type': 'private',
        'region': 'NCR',
        'province': '',
        'city': 'Manila',
        'website_url': 'https://www.feu.edu.ph',
        'ched_coe': False,
        'ched_cod': True,
        'accreditation_level': 3,
        # ₱1,958/unit; 20 units = ₱39,160 + fees ≈ ₱43,000
        'tuition_range_min': 35000,
        'tuition_range_max': 45000,
        'programs': [
            {
                'name': 'BS Information Technology',
                'abbreviation': 'BSIT',
                'description': (
                    'Offered at the FEU Institute of Technology. Strong industry '
                    'linkages in web/mobile development and network administration. '
                    'CHED Center of Development. ABET accreditation in progress.'
                ),
                'specializations': ['Web Development', 'Network Technology',
                                    'Mobile Development'],
                'duration_years': 4,
                'has_board_exam': False,
                'curriculum_url': 'https://www.feutech.edu.ph/programs/it',
            },
            {
                'name': 'BS Computer Science',
                'abbreviation': 'BSCS',
                'description': (
                    'Covers algorithms, data structures, systems programming, '
                    'and applied software development.'
                ),
                'specializations': ['Software Development', 'Data Analytics'],
                'duration_years': 4,
                'has_board_exam': False,
                'curriculum_url': 'https://www.feutech.edu.ph/programs/cs',
            },
        ],
    },
    {
        'name': 'Technological Institute of the Philippines',
        'abbreviation': 'TIP Manila',
        'university_type': 'private',
        'region': 'NCR',
        'province': '',
        'city': 'Manila',
        'website_url': 'https://www.tip.edu.ph',
        'ched_coe': True,
        'ched_cod': False,
        'accreditation_level': 4,
        # ~₱1,900/unit; 20 units = ₱38,000 + fees ≈ ₱45,000
        'tuition_range_min': 35000,
        'tuition_range_max': 52000,
        'programs': [
            {
                'name': 'BS Information Technology',
                'abbreviation': 'BSIT',
                'description': (
                    'CHED Center of Excellence in IT Education. ABET CAC-accredited. '
                    'PACUCOA Level IV First Reaccredited. Covers software development, '
                    'IT infrastructure, and cybersecurity. Autonomous CHED status '
                    '(2024–2027). Strong industry ties with IBM, Cisco, and Microsoft.'
                ),
                'specializations': ['Cybersecurity', 'Software Development',
                                    'Cloud Computing', 'Network Administration'],
                'duration_years': 4,
                'has_board_exam': False,
                'curriculum_url': 'https://tip.edu.ph/what-we-offer/undergraduate-programs/tip-manila/college-of-computer-studies/information-technology/',
            },
            {
                'name': 'BS Computer Science',
                'abbreviation': 'BSCS',
                'description': (
                    'ABET CAC-accredited. PACUCOA Level III. Prepares students for '
                    'research and industry roles in software systems, AI, and data '
                    'engineering. Offered at the College of Computer Studies.'
                ),
                'specializations': ['Artificial Intelligence', 'Software Systems'],
                'duration_years': 4,
                'has_board_exam': False,
                'curriculum_url': 'https://tip.edu.ph/what-we-offer/undergraduate-programs/tip-manila/college-of-computer-studies/',
            },
            {
                'name': 'BS Computer Engineering',
                'abbreviation': 'BSCE',
                'description': (
                    'Covers digital systems, embedded systems, VLSI design, '
                    'and computer architecture. Board exam program.'
                ),
                'specializations': ['Embedded Systems', 'Digital Design'],
                'duration_years': 4,
                'has_board_exam': True,
                'curriculum_url': 'https://tip.edu.ph/what-we-offer/undergraduate-programs/tip-manila/college-of-engineering-and-architecture/',
            },
        ],
    },
    {
        'name': 'Mapúa University',
        'abbreviation': 'Mapúa',
        'university_type': 'private',
        'region': 'NCR',
        'province': '',
        'city': 'Manila',
        'website_url': 'https://www.mapua.edu.ph',
        'ched_coe': True,
        'ched_cod': False,
        'accreditation_level': 3,
        # Trimester system (3 terms/year starting AY 2024-2025); ₱50,000–₱68,000/trimester
        'tuition_range_min': 50000,
        'tuition_range_max': 68000,
        'programs': [
            {
                'name': 'BS Computer Science',
                'abbreviation': 'BSCS',
                'description': (
                    'Offered under the School of Information Technology (SoIT). '
                    'Specializations in AI, cybersecurity, and data science. '
                    'CHED Center of Development. PACUCOA Level III. QS-ranked university.'
                ),
                'specializations': ['Artificial Intelligence', 'Cybersecurity',
                                    'Data Science', 'Software Engineering'],
                'duration_years': 4,
                'has_board_exam': False,
                'curriculum_url': 'https://www.mapua.edu.ph/pages/academics/undergraduate/makati-campus/school-of-information-technology/bachelor-of-science-in-computer-science',
            },
            {
                'name': 'BS Information Technology',
                'abbreviation': 'BSIT',
                'description': (
                    'Hands-on program covering web/mobile dev, IT project management, '
                    'cloud platforms, and enterprise systems. Industry-aligned curriculum.'
                ),
                'specializations': ['Web & Mobile Development', 'Cloud Platforms',
                                    'IT Project Management'],
                'duration_years': 4,
                'has_board_exam': False,
                'curriculum_url': 'https://www.mapua.edu.ph/pages/academics/undergraduate/makati-campus/school-of-information-technology/bachelor-of-science-in-information-technology',
            },
            {
                'name': 'BS Computer Engineering',
                'abbreviation': 'BSCE',
                'description': (
                    'ABET-accredited program. Covers computer architecture, '
                    'embedded systems, signal processing, and VLSI design.'
                ),
                'specializations': ['Computer Architecture', 'Embedded Systems',
                                    'Signal Processing'],
                'duration_years': 4,
                'has_board_exam': True,
                'curriculum_url': 'https://www.mapua.edu.ph/pages/academics/undergraduate/intramuros-campus/school-of-electrical-electronic-and-computer-engineering',
            },
        ],
    },
    {
        'name': 'AMA Computer University',
        'abbreviation': 'AMACU',
        'university_type': 'private',
        'region': 'NCR',
        'province': '',
        'city': 'Quezon City',
        'website_url': 'https://www.ama.edu.ph',
        'ched_coe': False,
        'ched_cod': False,
        'accreditation_level': 4,
        # ~₱1,300/unit; 20 units ≈ ₱26,000/sem
        'tuition_range_min': 22000,
        'tuition_range_max': 35000,
        'programs': [
            {
                'name': 'BS Information Technology',
                'abbreviation': 'BSIT',
                'description': (
                    'PACUCOA Level IV accredited. ABET CAC-accredited. '
                    'Pioneering IT program from one of the Philippines\' first '
                    'tech-focused universities. Covers application development, '
                    'networking, and IT support across multiple campuses nationwide.'
                ),
                'specializations': ['Application Development', 'Networking',
                                    'IT Support'],
                'duration_years': 4,
                'has_board_exam': False,
                'curriculum_url': 'https://www.ama.edu.ph/programs/bsit',
            },
            {
                'name': 'BS Computer Science',
                'abbreviation': 'BSCS',
                'description': (
                    'PACUCOA Level IV accredited. ABET CAC-accredited. '
                    'Covers programming fundamentals, data structures, '
                    'database management, and software development lifecycle.'
                ),
                'specializations': [],
                'duration_years': 4,
                'has_board_exam': False,
                'curriculum_url': 'https://www.ama.edu.ph/programs/bscs',
            },
        ],
    },
    {
        'name': 'University of Santo Tomas',
        'abbreviation': 'UST',
        'university_type': 'private',
        'region': 'NCR',
        'province': '',
        'city': 'Manila',
        'website_url': 'https://www.ust.edu.ph',
        'ched_coe': False,
        'ched_cod': True,
        'accreditation_level': 2,
        # ₱53,860–₱79,343/sem depending on college/year level
        'tuition_range_min': 54000,
        'tuition_range_max': 80000,
        'programs': [
            {
                'name': 'BS Information Technology',
                'abbreviation': 'BSIT',
                'description': (
                    'Offered at the College of Information and Computing Sciences. '
                    'PAASCU Level II Re-Accredited. Three specialization tracks: '
                    'Network & Security, IT Automation, and Web & Mobile App Dev. '
                    'CHED Center of Development.'
                ),
                'specializations': ['Network & Security', 'IT Automation',
                                    'Web & Mobile Application Development'],
                'duration_years': 4,
                'has_board_exam': False,
                'curriculum_url': 'https://www.ust.edu.ph/information-and-computing-sciences/department-of-information-technology/',
            },
            {
                'name': 'BS Information Systems',
                'abbreviation': 'BSIS',
                'description': (
                    'Prepares graduates for roles in IT consulting, business analysis, '
                    'enterprise systems, and digital transformation.'
                ),
                'specializations': ['Business Analysis', 'Enterprise Systems',
                                    'Digital Transformation'],
                'duration_years': 4,
                'has_board_exam': False,
                'curriculum_url': 'https://www.ust.edu.ph/information-and-computing-sciences/',
            },
        ],
    },
    {
        'name': 'National University Philippines',
        'abbreviation': 'NU',
        'university_type': 'private',
        'region': 'NCR',
        'province': '',
        'city': 'Manila',
        'website_url': 'https://national-u.edu.ph',
        'ched_coe': False,
        'ched_cod': False,
        'accreditation_level': 2,
        # ~₱1,700/unit; 20 units ≈ ₱34,000/sem + fees
        'tuition_range_min': 32000,
        'tuition_range_max': 45000,
        'programs': [
            {
                'name': 'BS Computer Science',
                'abbreviation': 'BSCS',
                'description': (
                    'Offered at the College of Computing and Information Technologies. '
                    'Covers core CS theory, software engineering, and electives '
                    'in data science and cybersecurity.'
                ),
                'specializations': ['Data Science', 'Cybersecurity',
                                    'Software Engineering'],
                'duration_years': 4,
                'has_board_exam': False,
                'curriculum_url': 'https://national-u.edu.ph/colleges/college-of-computing-and-information-technologies/',
            },
            {
                'name': 'BS Information Technology',
                'abbreviation': 'BSIT',
                'description': (
                    'Practical IT curriculum with a focus on application development, '
                    'infrastructure management, and emerging technologies.'
                ),
                'specializations': ['Application Development', 'Cloud Infrastructure'],
                'duration_years': 4,
                'has_board_exam': False,
                'curriculum_url': 'https://national-u.edu.ph/colleges/college-of-computing-and-information-technologies/',
            },
        ],
    },
    {
        'name': 'Pamantasan ng Lungsod ng Maynila',
        'abbreviation': 'PLM',
        'university_type': 'local',
        'region': 'NCR',
        'province': '',
        'city': 'Manila',
        'website_url': 'https://www.plm.edu.ph',
        'ched_coe': False,
        'ched_cod': False,
        'accreditation_level': 2,
        # Tuition-free: funded by City of Manila. Minimal miscellaneous fees only.
        'tuition_range_min': 0,
        'tuition_range_max': 2000,
        'programs': [
            {
                'name': 'BS Computer Science',
                'abbreviation': 'BSCS',
                'description': (
                    'Tuition-free CS program at the Philippines\' first city-funded '
                    'autonomous university, located in historic Intramuros, Manila. '
                    'Covers algorithms, data structures, and software engineering. '
                    'Prioritizes underprivileged but talented Manila residents.'
                ),
                'specializations': ['Software Engineering', 'Data Structures',
                                    'Algorithms'],
                'duration_years': 4,
                'has_board_exam': False,
                'curriculum_url': 'https://plm.edu.ph/colleges',
            },
            {
                'name': 'BS Information Technology',
                'abbreviation': 'BSIT',
                'description': (
                    'Free IT program for Manila city residents. Covers web development, '
                    'networking, database management, and IT project management. '
                    'Strong practitioner-focused curriculum.'
                ),
                'specializations': ['Web Development', 'Networking',
                                    'Database Management'],
                'duration_years': 4,
                'has_board_exam': False,
                'curriculum_url': 'https://plm.edu.ph/colleges',
            },
        ],
    },
    {
        'name': 'University of the East',
        'abbreviation': 'UE',
        'university_type': 'private',
        'region': 'NCR',
        'province': '',
        'city': 'Manila',
        'website_url': 'https://www.ue.edu.ph',
        'ched_coe': True,
        'ched_cod': False,
        'accreditation_level': 3,
        # ~₱1,800/unit; 20 units ≈ ₱36,000/sem + misc fees
        'tuition_range_min': 33000,
        'tuition_range_max': 44000,
        'programs': [
            {
                'name': 'BS Information Technology',
                'abbreviation': 'BSIT',
                'description': (
                    'Offered at the College of Computer Studies and Systems — '
                    'CHED Center of Excellence in IT. Specializations available in '
                    'Network & Systems Engineering, Software Engineering, '
                    'Information Systems Administration, and Telecommunications.'
                ),
                'specializations': ['Network & Systems Engineering',
                                    'Software Engineering',
                                    'Information Systems Administration',
                                    'Telecommunications'],
                'duration_years': 4,
                'has_board_exam': False,
                'curriculum_url': 'https://www.ue.edu.ph/mla/curricular-offerings-of-ccss/',
            },
            {
                'name': 'BS Computer Science',
                'abbreviation': 'BSCS',
                'description': (
                    'Covers computing concepts, algorithms, software systems design, '
                    'and new trends in computing. Project-based learning approach '
                    'with strong industry connections in Manila.'
                ),
                'specializations': ['Software Systems', 'Algorithms',
                                    'Data Engineering'],
                'duration_years': 4,
                'has_board_exam': False,
                'curriculum_url': 'https://www.ue.edu.ph/mla/curricular-offerings-of-ccss/',
            },
        ],
    },
    {
        'name': 'Adamson University',
        'abbreviation': 'AdU',
        'university_type': 'private',
        'region': 'NCR',
        'province': '',
        'city': 'Manila',
        'website_url': 'https://www.adamson.edu.ph',
        'ched_coe': False,
        'ched_cod': False,
        'accreditation_level': 2,
        # BSIT: ₱40,166/sem; BSCS: ₱48,356/sem (AY 2024-2025 official rates)
        'tuition_range_min': 40000,
        'tuition_range_max': 50000,
        'programs': [
            {
                'name': 'BS Information Technology',
                'abbreviation': 'BSIT',
                'description': (
                    'Offered at the College of Computing and Information Technology '
                    'in Ermita, Manila. Total semestral fee: ₱40,166. '
                    'Covers web/mobile development, networks, and IT service management.'
                ),
                'specializations': ['Web & Mobile Development',
                                    'Network Administration', 'IT Service Management'],
                'duration_years': 4,
                'has_board_exam': False,
                'curriculum_url': 'https://www.adamson.edu.ph/v1/?page=college-computing-information-technology',
            },
            {
                'name': 'BS Computer Science',
                'abbreviation': 'BSCS',
                'description': (
                    'Total semestral fee: ₱48,356 (AY 2024-2025). '
                    'Covers algorithms, data structures, software engineering, '
                    'and computing theory. Vincentian university tradition.'
                ),
                'specializations': ['Algorithms', 'Software Engineering',
                                    'Data Science'],
                'duration_years': 4,
                'has_board_exam': False,
                'curriculum_url': 'https://www.adamson.edu.ph/v1/?page=college-computing-information-technology',
            },
        ],
    },
    {
        'name': 'Lyceum of the Philippines University – Manila',
        'abbreviation': 'LPU Manila',
        'university_type': 'private',
        'region': 'NCR',
        'province': '',
        'city': 'Manila',
        'website_url': 'https://manila.lpu.edu.ph',
        'ched_coe': False,
        'ched_cod': False,
        'accreditation_level': 3,
        # BSIT 17 units = ₱54,435.80 confirmed AY 2025-2026; BSCS similar range
        'tuition_range_min': 50000,
        'tuition_range_max': 58000,
        'programs': [
            {
                'name': 'BS Information Technology',
                'abbreviation': 'BSIT',
                'description': (
                    'PACUCOA Level III accredited. Located in Intramuros, Manila. '
                    'Offered at the College of Technology (COT). Covers web/mobile '
                    'development, cloud, and IT project management. '
                    'Strong OJT program with industry partners.'
                ),
                'specializations': ['Web & Mobile Development', 'Cloud Computing',
                                    'IT Project Management'],
                'duration_years': 4,
                'has_board_exam': False,
                'curriculum_url': 'https://manila.lpu.edu.ph/academics/technology/cot-program-offerings/',
            },
            {
                'name': 'BS Computer Science',
                'abbreviation': 'BSCS',
                'description': (
                    'PACUCOA Level III accredited. Covers core computing fundamentals, '
                    'software systems, and data engineering. Offered at the College of '
                    'Technology (COT) at LPU\'s main Manila campus in Intramuros.'
                ),
                'specializations': ['Software Systems', 'Data Engineering'],
                'duration_years': 4,
                'has_board_exam': False,
                'curriculum_url': 'https://manila.lpu.edu.ph/academics/technology/cot-program-offerings/',
            },
        ],
    },
    {
        'name': 'Philippine Normal University',
        'abbreviation': 'PNU',
        'university_type': 'state',
        'region': 'NCR',
        'province': '',
        'city': 'Manila',
        'website_url': 'https://www.pnu.edu.ph',
        'ched_coe': False,
        'ched_cod': False,
        'accreditation_level': 2,
        # Free under RA 10931 (state university)
        'tuition_range_min': 0,
        'tuition_range_max': 5000,
        'programs': [
            {
                'name': 'BS Information Technology',
                'abbreviation': 'BSIT',
                'description': (
                    'Offered at the College of Flexible Learning and eTechnology. '
                    'Philippines\' national teacher training university with a '
                    'technology-education focus. Covers educational technology, '
                    'web development, and IT systems.'
                ),
                'specializations': ['Educational Technology', 'Web Development',
                                    'IT Systems'],
                'duration_years': 4,
                'has_board_exam': False,
                'curriculum_url': 'https://www.pnu.edu.ph/colleges/college-of-flexible-learning-and-etechnology/',
            },
        ],
    },
    # ══════════════════════════════════════════════════════════════════════
    # REGION I
    # ══════════════════════════════════════════════════════════════════════
    {
        'name': 'University of Northern Philippines',
        'abbreviation': 'UNP',
        'university_type': 'state',
        'region': 'Region I',
        'province': 'Ilocos Sur',
        'city': 'Vigan City',
        'website_url': 'https://www.unp.edu.ph',
        'ched_coe': False,
        'ched_cod': False,
        'accreditation_level': 2,
        'tuition_range_min': 0,
        'tuition_range_max': 5000,
        'programs': [
            {
                'name': 'BS Information Technology',
                'abbreviation': 'BSIT',
                'description': (
                    'Affordable state university IT program serving Ilocos Region '
                    'students. Covers networking, software development, and IT support.'
                ),
                'specializations': ['Networking', 'Web Development'],
                'duration_years': 4,
                'has_board_exam': False,
                'curriculum_url': 'https://www.unp.edu.ph/academics',
            },
        ],
    },
    # ══════════════════════════════════════════════════════════════════════
    # REGION II
    # ══════════════════════════════════════════════════════════════════════
    {
        'name': 'Cagayan State University',
        'abbreviation': 'CSU',
        'university_type': 'state',
        'region': 'Region II',
        'province': 'Cagayan',
        'city': 'Tuguegarao City',
        'website_url': 'https://www.csu.edu.ph',
        'ched_coe': False,
        'ched_cod': False,
        'accreditation_level': 2,
        'tuition_range_min': 0,
        'tuition_range_max': 5000,
        'programs': [
            {
                'name': 'BS Information Technology',
                'abbreviation': 'BSIT',
                'description': (
                    'IT program serving Cagayan Valley students. Covers programming, '
                    'networking, and database management systems.'
                ),
                'specializations': ['Programming', 'Database Management'],
                'duration_years': 4,
                'has_board_exam': False,
                'curriculum_url': 'https://www.csu.edu.ph/academics/programs',
            },
            {
                'name': 'BS Computer Science',
                'abbreviation': 'BSCS',
                'description': (
                    'Theoretical and applied CS curriculum covering data structures, '
                    'algorithms, and software engineering principles.'
                ),
                'specializations': [],
                'duration_years': 4,
                'has_board_exam': False,
                'curriculum_url': 'https://www.csu.edu.ph/academics/programs',
            },
        ],
    },
    # ══════════════════════════════════════════════════════════════════════
    # REGION III (CENTRAL LUZON) — near Metro Manila
    # ══════════════════════════════════════════════════════════════════════
    {
        'name': 'Bulacan State University',
        'abbreviation': 'BulSU',
        'university_type': 'state',
        'region': 'Region III',
        'province': 'Bulacan',
        'city': 'Malolos City',
        'website_url': 'https://www.bulsu.edu.ph',
        'ched_coe': False,
        'ched_cod': False,
        'accreditation_level': 2,
        # Free under RA 10931
        'tuition_range_min': 0,
        'tuition_range_max': 5000,
        'programs': [
            {
                'name': 'BS Information Technology',
                'abbreviation': 'BSIT',
                'description': (
                    'Free 4-year IT program at the major state university of Bulacan — '
                    'just 45 minutes from Manila. Covers software development, '
                    'networking, database systems, and systems analysis and design.'
                ),
                'specializations': ['Software Development', 'Networking',
                                    'Database Systems'],
                'duration_years': 4,
                'has_board_exam': False,
                'curriculum_url': 'https://www.bulsu.edu.ph/academics',
            },
            {
                'name': 'BS Computer Science',
                'abbreviation': 'BSCS',
                'description': (
                    'Affordable CS program for Bulacan and nearby NCR students. '
                    'Covers algorithms, data structures, and computing theory.'
                ),
                'specializations': [],
                'duration_years': 4,
                'has_board_exam': False,
                'curriculum_url': 'https://www.bulsu.edu.ph/academics',
            },
        ],
    },
    {
        'name': 'Holy Angel University',
        'abbreviation': 'HAU',
        'university_type': 'private',
        'region': 'Region III',
        'province': 'Pampanga',
        'city': 'Angeles City',
        'website_url': 'https://www.hau.edu.ph',
        'ched_coe': False,
        'ched_cod': True,
        'accreditation_level': 3,
        # ~₱1,600/unit; 20 units ≈ ₱32,000/sem + fees
        'tuition_range_min': 28000,
        'tuition_range_max': 42000,
        'programs': [
            {
                'name': 'BS Computer Science',
                'abbreviation': 'BSCS',
                'description': (
                    'CHED Center of Development for IT Education in Region III. '
                    'AUN-QA certified (2023). Covers software engineering, algorithms, '
                    'and data science at the School of Computing.'
                ),
                'specializations': ['Software Engineering', 'Data Analytics'],
                'duration_years': 4,
                'has_board_exam': False,
                'curriculum_url': 'https://www.hau.edu.ph/academics/school-of-computing',
            },
            {
                'name': 'BS Information Technology',
                'abbreviation': 'BSIT',
                'description': (
                    'PAASCU Level III accredited. AUN-QA certified (2021). CHED CoD. '
                    'Practical IT curriculum with strong industry exposure through '
                    'internship programs and tech company tie-ups in Clark Freeport Zone.'
                ),
                'specializations': ['Web Development', 'IT Infrastructure'],
                'duration_years': 4,
                'has_board_exam': False,
                'curriculum_url': 'https://www.hau.edu.ph/academics/school-of-computing',
            },
        ],
    },
    # ══════════════════════════════════════════════════════════════════════
    # REGION IV-A (CALABARZON)
    # ══════════════════════════════════════════════════════════════════════
    {
        'name': 'University of the Philippines Los Baños',
        'abbreviation': 'UPLB',
        'university_type': 'state',
        'region': 'Region IV-A',
        'province': 'Laguna',
        'city': 'Los Baños',
        'website_url': 'https://uplb.edu.ph',
        'ched_coe': True,
        'ched_cod': False,
        'accreditation_level': 3,
        # Free under RA 10931; same ₱1,500/unit socialized rate as UP System
        'tuition_range_min': 0,
        'tuition_range_max': 33000,
        'programs': [
            {
                'name': 'BS Computer Science',
                'abbreviation': 'BSCS',
                'description': (
                    'Offered at the Institute of Computer Science, UPLB. '
                    'Strong research culture in scientific computing, bioinformatics, '
                    'and AI. CHED Center of Excellence.'
                ),
                'specializations': ['Scientific Computing', 'Bioinformatics',
                                    'Artificial Intelligence', 'Computational Biology'],
                'duration_years': 4,
                'has_board_exam': False,
                'curriculum_url': 'https://ics.uplb.edu.ph/programs/bscs/',
            },
        ],
    },
    {
        'name': 'De La Salle University – Dasmariñas',
        'abbreviation': 'DLSU-D',
        'university_type': 'private',
        'region': 'Region IV-A',
        'province': 'Cavite',
        'city': 'Dasmariñas',
        'website_url': 'https://www.dlsud.edu.ph',
        'ched_coe': False,
        'ched_cod': False,
        'accreditation_level': 2,
        # ~₱1,700/unit; ≈ ₱34,000/sem + fees
        'tuition_range_min': 32000,
        'tuition_range_max': 48000,
        'programs': [
            {
                'name': 'BS Information Technology',
                'abbreviation': 'BSIT',
                'description': (
                    'Part of the De La Salle system. Covers enterprise systems, '
                    'web/mobile development, and IT project management in CALABARZON.'
                ),
                'specializations': ['Web & Mobile Development', 'Enterprise Systems'],
                'duration_years': 4,
                'has_board_exam': False,
                'curriculum_url': 'https://www.dlsud.edu.ph/programs/cics/bsit.htm',
            },
            {
                'name': 'BS Computer Science',
                'abbreviation': 'BSCS',
                'description': (
                    'Provides strong foundations in algorithms, software development, '
                    'and data management with Lasallian values integration.'
                ),
                'specializations': [],
                'duration_years': 4,
                'has_board_exam': False,
                'curriculum_url': 'https://www.dlsud.edu.ph/programs/cics/bcs.htm',
            },
        ],
    },
    {
        'name': 'Batangas State University',
        'abbreviation': 'BatStateU',
        'university_type': 'state',
        'region': 'Region IV-A',
        'province': 'Batangas',
        'city': 'Batangas City',
        'website_url': 'https://batstateu.edu.ph',
        'ched_coe': False,
        'ched_cod': False,
        'accreditation_level': 2,
        # Free under RA 10931; ₱250/unit without scholarship
        'tuition_range_min': 0,
        'tuition_range_max': 5000,
        'programs': [
            {
                'name': 'BS Information Technology',
                'abbreviation': 'BSIT',
                'description': (
                    'The National Engineering University (RA 11694, 2022). '
                    'ABET CAC-accredited — first state university in the Philippines '
                    'with ABET-accredited computing programs. Three official tracks: '
                    'Network Technology, Business Analytics, and Service Management.'
                ),
                'specializations': ['Network Technology', 'Business Analytics',
                                    'Service Management'],
                'duration_years': 4,
                'has_board_exam': False,
                'curriculum_url': 'https://batstateu.edu.ph/programs/informatics-and-computing-sciences/',
            },
            {
                'name': 'BS Computer Science',
                'abbreviation': 'BSCS',
                'description': (
                    'ABET CAC-accredited. Part of BatStateU — The National Engineering '
                    'University. Covers CS fundamentals including algorithms, data '
                    'structures, software engineering, and applied computing.'
                ),
                'specializations': [],
                'duration_years': 4,
                'has_board_exam': False,
                'curriculum_url': 'https://batstateu.edu.ph/bachelor-of-science-in-computer-science-bscs/',
            },
        ],
    },
    {
        'name': 'Cavite State University',
        'abbreviation': 'CavSU',
        'university_type': 'state',
        'region': 'Region IV-A',
        'province': 'Cavite',
        'city': 'Indang',
        'website_url': 'https://www.cvsu.edu.ph',
        'ched_coe': False,
        'ched_cod': False,
        'accreditation_level': 2,
        # Free under RA 10931
        'tuition_range_min': 0,
        'tuition_range_max': 5000,
        'programs': [
            {
                'name': 'BS Information Technology',
                'abbreviation': 'BSIT',
                'description': (
                    'Free state university IT program serving Cavite province '
                    'near Metro Manila. Covers web development, networking, '
                    'and enterprise system development.'
                ),
                'specializations': ['Web Development', 'Enterprise Systems'],
                'duration_years': 4,
                'has_board_exam': False,
                'curriculum_url': 'https://www.cvsu.edu.ph/academics/programs',
            },
            {
                'name': 'BS Computer Science',
                'abbreviation': 'BSCS',
                'description': (
                    'Covers core CS theory, programming, and software development. '
                    'Offers affordable quality CS education in CALABARZON.'
                ),
                'specializations': [],
                'duration_years': 4,
                'has_board_exam': False,
                'curriculum_url': 'https://www.cvsu.edu.ph/academics/programs',
            },
        ],
    },
    # ══════════════════════════════════════════════════════════════════════
    # REGION V (BICOL)
    # ══════════════════════════════════════════════════════════════════════
    {
        'name': 'Bicol University',
        'abbreviation': 'BU',
        'university_type': 'state',
        'region': 'Region V',
        'province': 'Albay',
        'city': 'Legazpi City',
        'website_url': 'https://www.bicol-u.edu.ph',
        'ched_coe': False,
        'ched_cod': False,
        'accreditation_level': 2,
        'tuition_range_min': 0,
        'tuition_range_max': 5000,
        'programs': [
            {
                'name': 'BS Information Technology',
                'abbreviation': 'BSIT',
                'description': (
                    'State university IT program for Bicol Region students. '
                    'Covers software development, IT infrastructure, and web technologies.'
                ),
                'specializations': ['Software Development', 'Web Technologies'],
                'duration_years': 4,
                'has_board_exam': False,
                'curriculum_url': 'https://www.bicol-u.edu.ph/colleges/cict',
            },
            {
                'name': 'BS Computer Science',
                'abbreviation': 'BSCS',
                'description': (
                    'Offered at the College of Science (CSIT department). '
                    'Covers fundamental CS — algorithms, programming, and computing systems.'
                ),
                'specializations': [],
                'duration_years': 4,
                'has_board_exam': False,
                'curriculum_url': 'https://bicol-u.edu.ph/category/college-of-science/',
            },
        ],
    },
    # ══════════════════════════════════════════════════════════════════════
    # REGION VI (WESTERN VISAYAS)
    # ══════════════════════════════════════════════════════════════════════
    {
        'name': 'West Visayas State University',
        'abbreviation': 'WVSU',
        'university_type': 'state',
        'region': 'Region VI',
        'province': 'Iloilo',
        'city': 'Iloilo City',
        'website_url': 'https://www.wvsu.edu.ph',
        'ched_coe': False,
        'ched_cod': False,
        'accreditation_level': 2,
        'tuition_range_min': 0,
        'tuition_range_max': 5000,
        'programs': [
            {
                'name': 'BS Information Technology',
                'abbreviation': 'BSIT',
                'description': (
                    'Affordable state university IT program for Western Visayas. '
                    'Covers web technologies, database systems, and IT management. '
                    'Offered at the Institute of Information and Communications Technology.'
                ),
                'specializations': ['Web Technologies', 'Database Systems'],
                'duration_years': 4,
                'has_board_exam': False,
                'curriculum_url': 'https://www.wvsu.edu.ph/academics/programs',
            },
        ],
    },
    # ══════════════════════════════════════════════════════════════════════
    # REGION VII (CENTRAL VISAYAS)
    # ══════════════════════════════════════════════════════════════════════
    {
        'name': 'Cebu Institute of Technology – University',
        'abbreviation': 'CIT-U',
        'university_type': 'private',
        'region': 'Region VII',
        'province': 'Cebu',
        'city': 'Cebu City',
        'website_url': 'https://www.cit.edu',
        'ched_coe': True,
        'ched_cod': False,
        'accreditation_level': 3,
        # ~₱1,700/unit; ≈ ₱34,000/sem + fees; 2024-2025 tuition increase approved
        'tuition_range_min': 30000,
        'tuition_range_max': 50000,
        'programs': [
            {
                'name': 'BS Computer Science',
                'abbreviation': 'BSCS',
                'description': (
                    'CHED Center of Excellence. Leading CS program in Visayas. '
                    'Covers algorithms, data science, and software systems. '
                    'Strong industry ties in the Cebu IT-BPM corridor. '
                    '11-time PACUCOA awardee for highest number of accredited programs in Region VII.'
                ),
                'specializations': ['Data Science', 'Software Systems',
                                    'AI & Machine Learning'],
                'duration_years': 4,
                'has_board_exam': False,
                'curriculum_url': 'https://www.cit.edu/ccs/programs/bscs',
            },
            {
                'name': 'BS Information Technology',
                'abbreviation': 'BSIT',
                'description': (
                    'Practical IT program with strong industry immersion in Cebu\'s '
                    'booming IT-BPO industry. Covers web/mobile development, '
                    'cloud computing, and UX design.'
                ),
                'specializations': ['Web & Mobile Development', 'Cloud Computing',
                                    'UX Design'],
                'duration_years': 4,
                'has_board_exam': False,
                'curriculum_url': 'https://www.cit.edu/ccs/programs/bsit',
            },
            {
                'name': 'BS Computer Engineering',
                'abbreviation': 'BSCE',
                'description': (
                    'Board exam program combining hardware and software engineering. '
                    'Covers embedded systems, digital circuits, and computer architecture.'
                ),
                'specializations': ['Embedded Systems', 'Computer Architecture'],
                'duration_years': 4,
                'has_board_exam': True,
                'curriculum_url': 'https://www.cit.edu/coe/programs/bscpe',
            },
        ],
    },
    {
        'name': 'University of San Carlos',
        'abbreviation': 'USC',
        'university_type': 'private',
        'region': 'Region VII',
        'province': 'Cebu',
        'city': 'Cebu City',
        'website_url': 'https://www.usc.edu.ph',
        'ched_coe': False,
        'ched_cod': True,
        'accreditation_level': 3,
        # 6.7% tuition increase announced for 2025-2026; ₱40,000–₱65,000/sem
        'tuition_range_min': 42000,
        'tuition_range_max': 65000,
        'programs': [
            {
                'name': 'BS Computer Science',
                'abbreviation': 'BSCS',
                'description': (
                    'Research-oriented CS program at the School of Engineering, '
                    'Architecture, Fine Arts and Computing. CHED Center of Development. '
                    'Offers specialization in Data Science and Computing Systems.'
                ),
                'specializations': ['Data Science', 'Computing Systems',
                                    'Computational Intelligence'],
                'duration_years': 4,
                'has_board_exam': False,
                'curriculum_url': 'https://www.usc.edu.ph/academics/departments/school-of-engineering-architecture-fine-arts-and-computing/',
            },
            {
                'name': 'BS Information Technology',
                'abbreviation': 'BSIT',
                'description': (
                    'Industry-aligned program producing IT professionals for '
                    'Cebu\'s thriving IT-BPO sector. Covers enterprise systems, '
                    'networking, and software quality assurance.'
                ),
                'specializations': ['Enterprise Systems', 'Software QA',
                                    'Business Intelligence'],
                'duration_years': 4,
                'has_board_exam': False,
                'curriculum_url': 'https://www.usc.edu.ph/academics/departments/school-of-engineering-architecture-fine-arts-and-computing/',
            },
        ],
    },
    {
        'name': 'Silliman University',
        'abbreviation': 'SU',
        'university_type': 'private',
        'region': 'Region VII',
        'province': 'Negros Oriental',
        'city': 'Dumaguete City',
        'website_url': 'https://www.su.edu.ph',
        'ched_coe': False,
        'ched_cod': True,
        'accreditation_level': 3,
        # ~8% increase AY 2024-2025; estimated ₱33,000–₱65,000/sem for 2025-2026
        'tuition_range_min': 33000,
        'tuition_range_max': 65000,
        'programs': [
            {
                'name': 'BS Computer Science',
                'abbreviation': 'BSCS',
                'description': (
                    'CHED Center of Development. Offered at the College of Computer '
                    'Studies, Silliman University — the first Protestant university '
                    'in the Philippines and Southeast Asia. PAASCU re-accredited until '
                    '2027. Strong liberal arts integration with computing fundamentals.'
                ),
                'specializations': ['Software Development', 'Data Analytics'],
                'duration_years': 4,
                'has_board_exam': False,
                'curriculum_url': 'https://su.edu.ph/schools-colleges/college-of-computer-studies/',
            },
            {
                'name': 'BS Information Technology',
                'abbreviation': 'BSIT',
                'description': (
                    'CHED Center of Development. PAASCU re-accredited until 2027. '
                    'Prepares students for IT careers with a focus on systems '
                    'development, networking, and digital media.'
                ),
                'specializations': ['Systems Development', 'Digital Media'],
                'duration_years': 4,
                'has_board_exam': False,
                'curriculum_url': 'https://su.edu.ph/schools-colleges/college-of-computer-studies/',
            },
        ],
    },
    # ══════════════════════════════════════════════════════════════════════
    # REGION VIII (EASTERN VISAYAS)
    # ══════════════════════════════════════════════════════════════════════
    {
        'name': 'Eastern Visayas State University',
        'abbreviation': 'EVSU',
        'university_type': 'state',
        'region': 'Region VIII',
        'province': 'Leyte',
        'city': 'Tacloban City',
        'website_url': 'https://www.evsu.edu.ph',
        'ched_coe': False,
        'ched_cod': False,
        'accreditation_level': 2,
        'tuition_range_min': 0,
        'tuition_range_max': 5000,
        'programs': [
            {
                'name': 'BS Information Technology',
                'abbreviation': 'BSIT',
                'description': (
                    'IT program for Eastern Visayas students. Covers software '
                    'development, web technologies, and systems administration.'
                ),
                'specializations': ['Software Development', 'Systems Administration'],
                'duration_years': 4,
                'has_board_exam': False,
                'curriculum_url': 'https://www.evsu.edu.ph/academics',
            },
            {
                'name': 'BS Computer Engineering',
                'abbreviation': 'BSCE',
                'description': (
                    'Board exam-bound program covering digital systems, '
                    'microprocessors, and communications technology.'
                ),
                'specializations': ['Digital Systems', 'Microprocessors'],
                'duration_years': 4,
                'has_board_exam': True,
                'curriculum_url': 'https://www.evsu.edu.ph/academics',
            },
        ],
    },
    # ══════════════════════════════════════════════════════════════════════
    # REGION IX (ZAMBOANGA PENINSULA)
    # ══════════════════════════════════════════════════════════════════════
    {
        'name': 'Western Mindanao State University',
        'abbreviation': 'WMSU',
        'university_type': 'state',
        'region': 'Region IX',
        'province': 'Zamboanga del Sur',
        'city': 'Zamboanga City',
        'website_url': 'https://www.wmsu.edu.ph',
        'ched_coe': False,
        'ched_cod': False,
        'accreditation_level': 2,
        'tuition_range_min': 0,
        'tuition_range_max': 5000,
        'programs': [
            {
                'name': 'BS Computer Science',
                'abbreviation': 'BSCS',
                'description': (
                    'Serving Zamboanga Peninsula with an affordable state-funded '
                    'CS program covering algorithms, systems, and applied computing.'
                ),
                'specializations': [],
                'duration_years': 4,
                'has_board_exam': False,
                'curriculum_url': 'https://www.wmsu.edu.ph/academics/programs',
            },
            {
                'name': 'BS Information Technology',
                'abbreviation': 'BSIT',
                'description': (
                    'Covers web development, database management, and IT project '
                    'management with a focus on regional industry needs.'
                ),
                'specializations': ['Web Development', 'Database Management'],
                'duration_years': 4,
                'has_board_exam': False,
                'curriculum_url': 'https://www.wmsu.edu.ph/academics/programs',
            },
        ],
    },
    # ══════════════════════════════════════════════════════════════════════
    # REGION X (NORTHERN MINDANAO)
    # ══════════════════════════════════════════════════════════════════════
    {
        'name': 'Mindanao State University – Iligan Institute of Technology',
        'abbreviation': 'MSU-IIT',
        'university_type': 'state',
        'region': 'Region X',
        'province': 'Lanao del Norte',
        'city': 'Iligan City',
        'website_url': 'https://www.msuiit.edu.ph',
        'ched_coe': False,
        'ched_cod': True,
        'accreditation_level': 4,
        # Free under RA 10931; otherwise ₱3,500–₱9,000/sem
        'tuition_range_min': 0,
        'tuition_range_max': 9000,
        'programs': [
            {
                'name': 'BS Computer Science',
                'abbreviation': 'BSCS',
                'description': (
                    'CHED Center of Development — the premier CS program in Mindanao. '
                    'AACCUP Level IV accredited (all programs). Strong in algorithms, '
                    'AI/ML, operating systems, and systems programming. '
                    'Offered at the College of Computer Studies.'
                ),
                'specializations': ['Artificial Intelligence', 'Systems Programming',
                                    'Theory of Computation', 'Machine Learning'],
                'duration_years': 4,
                'has_board_exam': False,
                'curriculum_url': 'https://www.msuiit.edu.ph/academics/colleges/ccs/programs/cs/index.php',
            },
            {
                'name': 'BS Information Technology',
                'abbreviation': 'BSIT',
                'description': (
                    'Covers networking, web development, IT project management, '
                    'and database administration. CHED-recognized program.'
                ),
                'specializations': ['Networking', 'Database Administration',
                                    'Web Development'],
                'duration_years': 4,
                'has_board_exam': False,
                'curriculum_url': 'https://msuiit.edu.ph/academics/colleges/ccs/programs/it/bsit-prospectus.pdf',
            },
        ],
    },
    {
        'name': 'Xavier University – Ateneo de Cagayan',
        'abbreviation': 'XU',
        'university_type': 'private',
        'region': 'Region X',
        'province': 'Misamis Oriental',
        'city': 'Cagayan de Oro',
        'website_url': 'https://www.xu.edu.ph',
        'ched_coe': False,
        'ched_cod': False,
        'accreditation_level': 3,
        # ~₱2,000/unit; 20 units ≈ ₱40,000/sem + fees
        'tuition_range_min': 38000,
        'tuition_range_max': 58000,
        'programs': [
            {
                'name': 'BS Computer Science',
                'abbreviation': 'BSCS',
                'description': (
                    'Jesuit-run university offering a values-centered CS program '
                    'in Northern Mindanao. Covers software engineering, data science, '
                    'and IT for social development.'
                ),
                'specializations': ['Software Engineering', 'Data Science',
                                    'IT for Social Development'],
                'duration_years': 4,
                'has_board_exam': False,
                'curriculum_url': 'https://www.xu.edu.ph/academics/programs/college-of-computer-studies',
            },
            {
                'name': 'BS Information Technology',
                'abbreviation': 'BSIT',
                'description': (
                    'Practical IT program with industry immersion opportunities '
                    'in Cagayan de Oro\'s growing IT/BPO sector.'
                ),
                'specializations': ['Web Development', 'IT Management'],
                'duration_years': 4,
                'has_board_exam': False,
                'curriculum_url': 'https://www.xu.edu.ph/academics/programs/college-of-computer-studies',
            },
        ],
    },
    # ══════════════════════════════════════════════════════════════════════
    # REGION XI (DAVAO REGION)
    # ══════════════════════════════════════════════════════════════════════
    {
        'name': 'Ateneo de Davao University',
        'abbreviation': 'ADDU',
        'university_type': 'private',
        'region': 'Region XI',
        'province': 'Davao del Sur',
        'city': 'Davao City',
        'website_url': 'https://www.addu.edu.ph',
        'ched_coe': False,
        'ched_cod': False,
        'accreditation_level': 3,
        # 4% increase approved AY 2025-2026; ~₱48,000–₱68,000/sem
        'tuition_range_min': 48000,
        'tuition_range_max': 68000,
        'programs': [
            {
                'name': 'BS Computer Science',
                'abbreviation': 'BSCS',
                'description': (
                    'PAASCU Level III accredited. Jesuit-run university offering CS '
                    'in one of the safest cities in Southeast Asia. Covers algorithms, '
                    'data analytics, and human-computer interaction.'
                ),
                'specializations': ['Data Analytics', 'Human-Computer Interaction',
                                    'Software Development'],
                'duration_years': 4,
                'has_board_exam': False,
                'curriculum_url': 'https://www.addu.edu.ph/computer-science/',
            },
            {
                'name': 'BS Information Technology',
                'abbreviation': 'BSIT',
                'description': (
                    'Industry-aligned IT program with focus on web/mobile development '
                    'and IT infrastructure for Davao\'s growing tech scene.'
                ),
                'specializations': ['Web & Mobile Development', 'IT Infrastructure'],
                'duration_years': 4,
                'has_board_exam': False,
                'curriculum_url': 'https://www.addu.edu.ph/information-technology/',
            },
        ],
    },
    {
        'name': 'University of Mindanao',
        'abbreviation': 'UM',
        'university_type': 'private',
        'region': 'Region XI',
        'province': 'Davao del Sur',
        'city': 'Davao City',
        'website_url': 'https://umindanao.edu.ph',
        'ched_coe': False,
        'ched_cod': True,
        'accreditation_level': 4,
        # ~₱1,200/unit; 20 units ≈ ₱24,000/sem + fees
        'tuition_range_min': 20000,
        'tuition_range_max': 32000,
        'programs': [
            {
                'name': 'BS Information Technology',
                'abbreviation': 'BSIT',
                'description': (
                    'PACUCOA Level IV accredited. CHED Center of Development. '
                    'PICAB-accredited. One of the largest private universities in '
                    'Mindanao. Offered at the College of Computing Education (CCE). '
                    'Covers software development, networking, and IT service management.'
                ),
                'specializations': ['Software Development', 'Networking'],
                'duration_years': 4,
                'has_board_exam': False,
                'curriculum_url': 'https://umindanao.edu.ph/colleges/cce',
            },
            {
                'name': 'BS Computer Science',
                'abbreviation': 'BSCS',
                'description': (
                    'PACUCOA Level IV accredited. CHED Center of Development. '
                    'Covers core CS fundamentals — programming, data structures, '
                    'algorithms, and software engineering. Offered at the CCE.'
                ),
                'specializations': [],
                'duration_years': 4,
                'has_board_exam': False,
                'curriculum_url': 'https://umindanao.edu.ph/colleges/cce',
            },
        ],
    },
    # ══════════════════════════════════════════════════════════════════════
    # CAR (CORDILLERA ADMINISTRATIVE REGION)
    # ══════════════════════════════════════════════════════════════════════
    {
        'name': 'Benguet State University',
        'abbreviation': 'BSU',
        'university_type': 'state',
        'region': 'CAR',
        'province': 'Benguet',
        'city': 'La Trinidad',
        'website_url': 'https://www.bsu.edu.ph',
        'ched_coe': False,
        'ched_cod': False,
        'accreditation_level': 3,
        'tuition_range_min': 0,
        'tuition_range_max': 5000,
        'programs': [
            {
                'name': 'BS Information Technology',
                'abbreviation': 'BSIT',
                'description': (
                    'Affordable state university IT program serving the Cordillera '
                    'region. Covers programming, web technologies, and IT systems.'
                ),
                'specializations': ['Programming', 'Web Technologies'],
                'duration_years': 4,
                'has_board_exam': False,
                'curriculum_url': 'https://www.bsu.edu.ph/academics/programs',
            },
        ],
    },
]


class Command(BaseCommand):
    help = 'Seed Philippine universities and their CCS programs (data verified April 2026).'

    def add_arguments(self, parser):
        parser.add_argument(
            '--clear',
            action='store_true',
            help='Delete all existing universities before seeding.',
        )

    def handle(self, *args, **options):
        if options['clear']:
            University.objects.all().delete()
            self.stdout.write(self.style.WARNING('Cleared all universities.'))

        created_unis = 0
        created_programs = 0

        for data in UNIVERSITIES:
            programs_data = data.pop('programs', [])
            uni, uni_created = University.objects.get_or_create(
                name=data['name'],
                defaults=data,
            )
            if uni_created:
                created_unis += 1

            for prog in programs_data:
                _, prog_created = CCSProgram.objects.get_or_create(
                    university=uni,
                    abbreviation=prog['abbreviation'],
                    defaults=prog,
                )
                if prog_created:
                    created_programs += 1

        self.stdout.write(
            self.style.SUCCESS(
                f'Done. {created_unis} universities and {created_programs} programs created.'
            )
        )
