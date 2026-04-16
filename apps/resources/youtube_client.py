"""
YouTube Data API v3 client.
API key read from Django settings (from .env) — never hardcoded.
Free quota: 10,000 units/day. Search = 100 units. Video details = 1 unit.

Diversity + difficulty awareness (see modular-foraging-cat plan, Part B):
- _build_query_variants — deterministic per (user, node), produces different
  queries for different users + different nodes so the same top-5 isn't
  returned for every learner on the same roadmap.
- difficulty-aware suffix pool — beginner nodes get "for beginners", expert
  nodes get "advanced / internals / masterclass" etc. Previously every node
  searched "{title} tutorial" regardless of level — which is why expert nodes
  kept returning beginner tutorials.
- parameter rotation — search order + videoDuration varied per (user, node).
- cross-node dedup — soft channel penalty, hard video-id block so the same
  channel/video can't fill multiple nodes in one roadmap (MMR-inspired).
- retry with jitter on transient 429/5xx; Django-cached responses (Redis) so
  repeat queries across users don't burn quota.
"""
import datetime
import hashlib
import logging
import random
import re
import time

from django.conf import settings
from django.core.cache import cache

logger = logging.getLogger(__name__)

_youtube_client = None

# Feature flag for the diversified pipeline. Falls back to legacy behaviour
# (single "{title} tutorial" query, no dedup) when False — so rollout can be
# gated per environment.
_DIVERSIFY_ENABLED = getattr(settings, 'YOUTUBE_DIVERSIFY', True)

# Response cache TTL (24 h) — each cache hit saves 100 quota units.
_CACHE_TTL_SECONDS = 24 * 60 * 60

# ---------------------------------------------------------------------------
# Credible educational channels — research-backed list organized by CCS track
# Each entry is the lowercase channel name as it appears on YouTube.
# Score: +2 if channel matches (preferred), +1 if topic keyword in title.
# Sources: Pesto Tech, DEV Community, Feedspot, KDnuggets, PlacementPreparation
# ---------------------------------------------------------------------------
PREFERRED_CHANNELS = {
    # ── Web Development ──────────────────────────────────────────────────────
    'freecodecamp.org',          # 8.26M subs — nonprofit, full free courses
    'traversy media',            # ~2M subs — longest-running web dev channel
    'fireship',                  # 2M+ — high signal, always current
    'the net ninja',             # 1M+ — clean structured playlists
    'net ninja',                 # alternate display name
    'web dev simplified',        # 1M+ — clear JS/CSS/React deep dives
    'academind',                 # 841K — React, Vue, Angular, Node.js
    'kevin powell',              # CSS authority — best CSS educator on YouTube
    'programming with mosh',     # Clean beginner-friendly full courses
    'javascript mastery',        # 1M+ — React/Next.js/GSAP/TypeScript; project-driven, modern JS
    'bro code',                  # 3.15M — full-length courses: 12-hr Java/Python/JS, C++, C#
    'codeaesthetic',             # 417K — clean code + software engineering principles
    'dave gray',                 # Full-stack MERN, CSS deep dives, bootcamp-style courses
    'wes bos',                   # 250K+ — modern JS/CSS fundamentals; wesbos.com courses
    'codevolution',              # 1M+ — Vishwas Gopinath; most complete React/Vue/TypeScript series; cited by LearnWithPath
    'akshay saini',              # 2M+ — Namaste JavaScript; JS internals (closures, event loop, async); unique "how it works" angle
    'ben awad',                  # 300K+ — TypeScript + full-stack live coding (GraphQL, React, Docker); cited by Better Stack
    'lama dev',                  # 500K+ — full-stack MERN project builds; fills "build a real production app" gap
    'hyperplexed',               # 600K+ — CSS/JS animation clones; dev.to + RGB Studios top-frontend list
    'matt pocock',               # 150K+ — Total TypeScript; type-system authority; cited by Microsoft Learn + DEV
    'jonas schmedtmann',         # 200K+ — HTML/CSS/JS fundamentals; top-10 frontend lists 2026

    # ── Data Science / ML / AI ───────────────────────────────────────────────
    'statquest with josh starmer',  # Ex-UNC researcher, math/ML visuals
    '3blue1brown',               # 5M+ — best math intuition channel
    'sentdex',                   # Python, ML, NLP, finance projects
    'krish naik',                # 800K — ML/deep learning, popular in SEA
    'corey schafer',             # 1M+ — Python, Django, Pandas
    'pydata',                    # NumFOCUS official, conference-grade content
    'andrej karpathy',           # Ex-Tesla AI Director, GPT from scratch
    'lex fridman',               # 4M+ — MIT researcher; interviews with Karpathy, Hinton, LeCun; big-picture AI perspective
    'jeremy howard',             # 145K — fast.ai founder; top-down practical deep learning; cited by KDnuggets
    'hugging face',              # 250K+ — Official Hugging Face; transformers, open-source models, NLP
    'nicholas renotte',          # 600K+ — computer vision (YOLO), NLP, pose estimation, hands-on AI projects
    'ai jason',                  # 130K+ — Jason Zhou; applied LLM app builds, RAG pipelines, agents
    'data independent',          # 90K — Greg Kamradt; practical LangChain/RAG, referenced in LangChain docs
    'matthew berman',            # 400K+ — LLM benchmarking, local models, agent frameworks
    'all about ai',              # 130K+ — Kris Ograbek; prompt engineering + GPT API
    'james briggs',              # 90K — Pinecone DevRel; LangChain, vector DB (authoritative)
    'sam witteveen',             # 100K+ — LangChain deep dives, agent architectures
    'ai anytime',                # 90K — LlamaIndex + RAG implementations (authoritative)

    # ── Cybersecurity ────────────────────────────────────────────────────────
    'the cyber mentor',          # TCM Security, ethical hacking, OSCP prep
    'hackersploit',              # 918K — pentesting, Linux security, CTFs
    'john hammond',              # Active CTF competitor, Huntress researcher
    'ippsec',                    # Gold standard for Hack The Box walkthroughs
    'liveoverflow',              # Low-level exploit/reverse engineering
    'networkchuck',              # 5.1M — CCNA-certified, great entry point
    'null byte',                 # Ethical hacking, Kali Linux
    'sans internet stormcast',   # SANS Institute — top infosec org
    # ── Cybersecurity — Blue Team / Bug Bounty ───────────────────────────────
    '13cubed',                   # DFIR specialist — forensic artifacts, malware analysis; blue team authority
    'nahamsec',                  # Bug bounty — CTF, live hacking, interviews with top hunters
    'stok',                      # Bug bounty vlogging + real-world vulnerability research
    # ── Application Security / DevSecOps ────────────────────────────────────
    'owasp foundation',          # Official OWASP — Top 10, secure coding, web app security standards
    'portswigger',               # Web Security Academy walkthroughs — SQLi, XSS, SSRF labs
    'snyk',                      # DevSecOps, supply chain, container scanning, OWASP 2025 updates
    'hak5',                      # 1.1M — Darren Kitchen; hardware hacking, WiFi security, physical pentesting tools
    'malwaretech',               # 450K+ — Marcus Hutchins; advanced malware analysis, threat intel, reverse engineering
    'insiderphd',                # 280K+ — Katie Paxton-Fear (PhD); web hacking, bug bounty, OSCP prep; cited by PowerDMARC
    'gynvael coldwind',          # 130K+ — exploit dev, CTF, reverse engineering; r/netsec recurring
    'pwn function',              # 120K+ — crypto / AppSec fundamentals; Feedspot + r/AskNetsec
    's4vitar',                   # 500K+ — Spanish red team / OSCP; r/hacking + r/oscp cited
    'offensive security',        # 90K+ — official OSCP / exploit resources
    'stacksmashing',             # 200K+ — hardware + mobile RE; cited by Hackaday

    # ── Mobile Development ───────────────────────────────────────────────────
    'flutter',                   # Google's official Flutter channel
    'reso coder',                # Flutter/Riverpod, Clean Architecture
    'codewithchris',             # Swift/iOS for beginners
    'let\'s build that app',     # iOS, SwiftUI, UIKit
    'code with andrea',          # 500K+ — Andrea Bizzotto (GDE); production Flutter architecture (Riverpod, clean arch)
    'the flutter way',           # 300K+ — creative Flutter UI design, animations; fills design-focused gap
    'sean allen',                # 170K+ — Silicon Valley iOS engineer; Swift, SwiftUI, career advice; cited by Feedspot
    'kavsoft',                   # 200K+ — SwiftUI creative animations and custom UI; complements Sean Allen
    'simon grimm',               # 100K+ — voted #1 React Native creator; Expo, cross-platform; Class Central top course
    # ── Mobile Development — Android / Kotlin ───────────────────────────────
    'philipp lackner',           # 244K — Kotlin/Jetpack Compose authority; MVVM deep dives; 14+ yrs exp
    'coding with mitch',         # 190K+ — project-based Android from scratch (Instagram, Maps clones)
    'android developers',        # 1M+ — Official Google Android; MAD Skills, Compose, performance
    'stevdza-san',               # Jetpack Compose beginner → advanced; animations, navigation, custom UI
    'paul hudson',               # 180K+ — Hacking With Swift; Swift / SwiftUI standard; Swift.org cited
    'william candillon',         # 136K — "Can it be done in React Native?"; reanimated / skia deep dives
    'james montemagno',          # 45K — Microsoft PM; core .NET MAUI / Xamarin voice (authoritative)
    'azamsharp',                 # 55K — SwiftUI / iOS architecture; r/iOSProgramming resource threads

    # ── Game Development ─────────────────────────────────────────────────────
    'unity',                     # Official Unity Technologies
    'unreal engine',             # Official Epic Games
    'brackeys',                  # Most-subscribed Unity archive (still relevant)
    'gdquest',                   # Official Godot partner, open-source
    'heartbeast',                # Godot, Game Maker — beginner friendly
    'gamefromscratch',           # Multi-engine reviews and tutorials
    'sebastian lague',           # Procedural generation, creative CS
    'code monkey',               # 500K+ — Unity/C# game mechanics; 1200+ videos; solo dev with Steam games
    'game makers toolkit',       # Award-winning design analysis essays (GMTK); best design theory channel
    'blackthornprod',            # Game dev + art/animation combined; creative indie dev perspective
    'the coding train',          # 1.5M+ — Daniel Shiffman (NYU); p5.js, creative coding, algorithms visualized
    'jason weimann',             # 150K+ — Unity C# with design patterns, testing, professional code quality
    'clear code',                # 200K+ — dedicated Godot 4 tutorials; cited by GodotAcademy as top resource
    'acerola',                   # 280K+ — shader programming, graphics techniques; r/GraphicsProgramming cited
    'freya holmer',              # 450K+ — math for games, shader deep dives; Shadertoy + r/gamedev top
    'simondev',                  # 280K+ — Three.js / graphics programming; r/GraphicsProgramming
    'gorka games',               # 320K+ — Unreal 5 tutorials; r/unrealengine wiki
    'ryan laley',                # 80K+ — Unreal Blueprint / C++; r/unrealengine cited
    'blender guru',              # 3M+ — Blender for 3D / game assets; Blender.org cited
    'grant abbitt',              # 600K+ — low-poly game art in Blender; r/blender + r/gamedev

    # ── Backend Development ───────────────────────────────────────────────────
    'amigoscode',                # Spring Boot, Java, microservices, Docker
    'in28minutes - ranga karanam',  # Spring Boot, Spring Cloud, REST APIs
    'telusko',                   # Java, Spring Boot — large beginner base
    'java brains',               # 500K+ — Koushik Kothagal; Spring Boot, Security, reactive Java; Javarevisited #1 Spring educator
    'java techie',               # 186K — Spring WebFlux, Spring Cloud, microservices; reactive/cloud-native backend
    'stephane maarek',           # 250K+ — Apache Kafka authority + AWS SA; cited by Feedspot as definitive Kafka educator
    'confluent developers',      # 60K+ — Tim Berglund; Kafka canonical (authoritative)

    # ── DevOps / Cloud ────────────────────────────────────────────────────────
    'techworld with nana',       # 900K — best DevOps from-scratch channel
    'aws',                       # Official Amazon Web Services
    'devops directive',          # Kubernetes, Helm, Terraform, GitOps
    'kodekloud',                 # Kubernetes, CKA, Ansible — cert prep
    'a cloud guru',              # AWS, Azure, GCP certification
    'kunal kushwaha',            # 400K+ — free DevOps bootcamp, CNCF Ambassador; cloud-native foundations
    'bret fisher',               # Docker Captain — Kubernetes, Docker Swarm, containers; 400+ videos
    'devops toolkit',            # Viktor Farcic — GitOps, ArgoCD, Flux, Kubernetes operators deep dives
    'abhishek veeramalla',       # 600K+ — Kubernetes/DevOps with real-world projects; cited by Microtica blog
    'john savill\'s technical training',  # 250K+ — Microsoft Principal Engineer; deep Azure AD, networking; fills Azure gap
    'cncf',                      # 150K+ — Official Cloud Native Computing Foundation; KubeCon talks, cloud-native roadmap
    'cloud with raj',            # 200K+ — GCP-specific hands-on tutorials; cited as top practical Google Cloud channel
    'hashicorp',                 # 60K+ — official Terraform / Vault / Consul (authoritative)
    'that devops guy',           # 110K+ — Kubernetes / GitOps practical; r/devops weekly cited
    'just me and opensource',    # 100K+ — Kubernetes operators + Go; r/kubernetes cited
    'anton putra',               # 70K+ — cloud benchmarks (EKS vs GKE); r/devops + HN cited (authoritative)

    # ── Algorithms / Data Structures / CS Theory ─────────────────────────────
    'mit opencourseware',        # MIT official — university-level rigour
    'abdul bari',                # 70+ algorithm topics, cited in uni syllabi
    'williamfiset',              # Graph theory, network flow, DSA with Java
    'back to back swe',          # LeetCode, FAANG interview DSA
    'neetcode',                  # 500K — patterns-based LeetCode roadmaps
    'gaurav sen',                # Ex-Google, system design + DSA
    'cs50',                      # Harvard CS50 — gold standard free CS course
    'mycodeschool',              # Linked lists, trees — timeless visual DSA
    'cs50 – computer science courses for everyone',  # alt name
    'bytebytego',                # 500K+ — Alex Xu (ex-Twitter/Apple); system design authority; 1M+ newsletter
    'the primeagen',             # 500K+ — performance, architecture, algorithms; deep software engineering
    'errichto',                  # Competitive programming, algorithmic thinking, Codeforces walkthroughs
    'nick white',                # LeetCode problem breakdowns; clear explanations for interview prep
    'cs dojo',                   # 1.92M — YK Sugi (ex-Google); CS fundamentals + interview prep; cited by AlgoCademy
    'clement mihailescu',        # 275K+ — AlgoExpert creator; FAANG strategies + live walkthroughs
    'colin galen',               # Codeforces IGM; CP editorials (Codeforces blog 78967 — authoritative)
    'secondthread',              # Codeforces IGM; post-contest walkthroughs (authoritative)
    'aditya verma',              # 280K+ — definitive DP playlist; LeetCode discuss cited
    'hello interview',           # 200K+ — ex-FAANG system-design mocks
    'arpit bhayani',             # 250K+ — "Asli Engineering"; system design + DB + Python internals
    'kevin naughton jr',         # 170K+ — LeetCode / CP patterns; interview prep
    'rachit jain',               # 110K+ — CP patterns; Codeforces-adjacent

    # ── UI/UX Design ─────────────────────────────────────────────────────────
    'figma',                     # Official Figma channel
    'designcourse',              # UI design, UX, CSS — veteran instructor
    'flux academy',              # UX strategy, Figma, product design
    'aj&smart',                  # Google Design Sprint partners
    'mizko',                     # 250K+ — practical Figma tutorials; responsive design, auto layout, variants
    'the futur',                 # 2.5M — Chris Do; design business, branding; fills "design as career" angle
    'jesse showalter',           # 440K+ — UX process + frontend implementation; bridges UX and coding

    # ── Networking / Systems / Linux ─────────────────────────────────────────
    'david bombal',              # CCNA/CCNP, GNS3, network automation
    'jeremy\'s it lab',          # Full free CCNA course
    'professor messer',          # CompTIA A+, Network+, Security+ cert prep
    'chris titus tech',          # 602K — Linux, sysadmin, scripting
    'the linux foundation',      # Official Linux Foundation channel
    'cbt nuggets',               # Professional IT certification training
    'learn linux tv',            # 500K+ — Jay LaCroix; Linux admin, server setup, distro reviews; fills sysadmin gap
    'distrotube',                # 300K+ — Derek Taylor; Linux distributions, tiling WMs, daily-use workflows
    'the linux experiment',      # 300K+ — Nick; weekly Linux news, distro comparisons, practical tips
    'luke smith',                # 350K+ — Arch, suckless, shell scripting; r/linux + r/unixporn cited
    'mental outlaw',             # 600K+ — privacy / security Linux; r/linux
    'brodie robertson',          # 180K+ — Linux news / kernel; r/linux daily threads
    'lawrence systems',          # 400K+ — pfSense / FreeBSD / networking; r/homelab + r/PFSENSE

    # ── Filipino / Tagalog educators (PH-focused — aligns with CCS audience) ─
    'sdpt solutions',            # PH — Java/Python/MySQL/mobile in Tagalog
    'tech course ph',            # PH — simplified IT lectures
    'learn computer today',      # PH (Michael Tan) — CS/IT for Filipinos
    'pinoyfreecoder',            # PH — full-stack tutorials in Filipino
    'john carlo franco',         # PH — programming tips in Tagalog
    'dojicreates',               # PH — Tagalog: C++, Web, JS, Python, Java; source code + notes
    'coding kuya',               # PH — IT career advice, industry trends, how to become a programmer
    'programming ph',            # PH — Filipino programming community tutorials and advice
    'rem lampa',                 # PH — FreeCodeCamp Manila Community Leader; live IT discussions for Filipinos
    'kuya chris',                # PH — Tagalog full-stack (Laravel, React, PHP); "Web Developers Philippines" FB
    'code with tadz',            # PH — Flutter, Firebase, mobile dev in Taglish; FilDev Discord
    'jhay-ar de guzman',         # PH — Tagalog Android / Java / Kotlin; "Pinoy Programmers" FB group
    'jreborn ph',                # PH — C# / .NET and game dev in Tagalog; niche but well-regarded in PH indie dev
    'kodego ph',                 # PH bootcamp; structured JS / React in Taglish (TESDA-aligned)

    # ── Deeper DSA / Algorithms ──────────────────────────────────────────────
    'tushar roy - coding made simple',   # DSA, system design, FAANG prep
    'takeuforward',              # Striver's DSA sheet — competitive
    'geeksforgeeks',             # Official GfG — wide coverage

    # ── Systems / low-level / compilers ──────────────────────────────────────
    'tsoding daily',             # live coding, compiler internals, C/Rust
    'jon gjengset',              # Rust advanced — "Crust of Rust"
    'low level learning',        # C, OS internals, security
    'computerphile',             # CS theory — Nottingham academics
    'ben eater',                 # 1.4M — 8-bit CPU + 6502 from scratch; gold-standard digital logic
    'andreas kling',             # 70K — SerenityOS / Ladybird founder (Wikipedia — authoritative)
    'jacob sorber',              # 200K+ — Clemson CS professor; C / systems / embedded
    'mike shah',                 # 60K — Yale lecturer; Modern C++ (isocpp.org endorsed — authoritative)
    'casey muratori',            # 100K+ — Handmade Hero; games-from-scratch, OS-level
    'george hotz',               # 600K+ — tinygrad, ML compilers, reverse engineering
    'nir lichtman',              # 100K+ — build OS / shell / Git from scratch
    'dave\'s garage',            # 900K+ — ex-Microsoft engineer; Windows internals + assembly

    # ── AI / ML / LLM ────────────────────────────────────────────────────────
    'tech with tim',             # Python + AI projects
    'two minute papers',         # AI research summaries
    'yannic kilcher',            # LLM paper deep-dives
    'codebasics',                # data science + ML in Python
    'deeplearningai',            # Andrew Ng (Stanford/Google Brain) — ML/deep learning/MLOps; highest authority
    'tina huang',                # 1M+ — ex-Meta data scientist; AI/ML accessible, bridges theory + practice
    'assemblyai',                # NLP, speech AI, LLMs, audio deep learning; technical company tutorials
    'google cloud tech',         # Official Google Cloud — GenAI, Vertex AI, Gemini; updated weekly

    # ── Backend / system design ──────────────────────────────────────────────
    'arjan codes',               # Python clean architecture + patterns
    'hussein nasser',            # backend, DB internals, networking
    'hitesh choudhary',          # backend, JS, Python (South Asia audience)
    'code with john',            # Java, OOP, Spring

    # ── Cloud / DevOps (expand beyond Nana) ──────────────────────────────────
    'anthony sistilli',          # DevOps, cloud, real-world infra
    'marcel dempers (that devops guy)',  # Kubernetes deep dives
    'cbtnuggets',                # alt slug

    # ── Frontend / UI specific ───────────────────────────────────────────────
    'josh tried coding',         # Josh Comeau — frontend, animations
    'theo - t3.gg',              # modern full-stack JS/TS
    'jack herrington',           # React patterns, Next.js, TS
    'syntax',                    # 180K+ — Wes Bos / Scott Tolinski podcast; heavy modern JS / Svelte coverage
    'tailwind labs',             # 150K+ — official Tailwind; Adam Wathan tutorials
    'sam selikoff',              # 90K+ — Tailwind + design-system patterns (authoritative per Tailwind team)
    'google chrome developers',  # 600K+ — Core Web Vitals, Lighthouse from source
    'vue mastery',               # 90K+ — official Vue partner; cited in Vue docs (authoritative)

    # ── Data Engineering / ETL / Pipelines ──────────────────────────────────
    'learn data engineering',    # 230K+ — Andreas Kretz; Airflow, dbt, Spark, Kafka; whiteboards + cookbook
    'seattle data guy',          # 114K+ — Snowflake, dbt, BigQuery; practical hands-on production content
    'darshil parmar',            # 400K — PySpark, Kafka, end-to-end pipelines; largest DE channel
    'trendytech',                # 30K+ — big data, SQL, AWS, interview prep; 30K+ data engineers trained
    'databricks',                # 100K+ — Official Apache Spark + Delta Lake; highest-authority Spark content

    # ── Embedded Systems / IoT ───────────────────────────────────────────────
    'dronebotworkshop',          # 459K — Arduino/Raspberry Pi/ESP32; 30.6M views; diagrams + code
    'andreas spiess',            # 330K+ — IoT, LoRa, sensors, ESP32/8266; "guy with Swiss accent"
    'jeff geerling',             # 253K+ — Raspberry Pi, SBCs, Ansible, Kubernetes on hardware
    'arduino',                   # Official Arduino channel — hardware/firmware updates, educational builds
    'raspberry pi',              # 300K+ — Official Raspberry Pi Foundation; hardware tutorials, SBC projects
    'eevblog',                   # 900K+ — Dave Jones; electronics teardowns, circuit design; Feedspot #1 electronics
    'phil\'s lab',               # 240K+ — PCB design, STM32, DSP; r/embedded + r/PrintedCircuitBoard top
    'robert feranec',            # 160K+ — high-speed PCB design; Altium + r/PrintedCircuitBoard cited
    'the signal path',           # 230K+ — RF / microwave teardowns; EEVblog forum + r/rfelectronics
    'w2aew',                     # 180K+ — Alan Wolke; analog electronics / oscilloscopes; EEVblog + Hackaday
    'articulated robotics',      # 150K+ — ROS2 robotics; r/ROS pinned resources
    'james bruton',              # 800K+ — robotics builds; Hackaday-featured
    'great scott!',              # 3M+ — electronics fundamentals; r/AskElectronics wiki
    'electroboom',               # 5M+ — electronics theory with humor; r/ElectricalEngineering

    # ── Database Internals ───────────────────────────────────────────────────
    'cmu database group',        # Andy Pavlo (CMU) — graduate-level DB internals, query processing, storage

    # ── SQL / Databases — Practical ──────────────────────────────────────────
    'database star',             # 150K+ — Ben Brumm; practical SQL, DB design, performance tuning; LearnSQL #1 SQL channel
    'techtfq',                   # 200K+ — Thoufiq Mohammed; complex SQL, analytical functions, interview prep
    'mongodb',                   # 300K+ — Official MongoDB; tutorials, aggregation pipelines, Atlas
    'kudvenkat',                 # 1M+ — SQL, SSIS, reporting services; large audience, topic-specific playlists

    # ── Rust / Go / C++ — Systems Languages ──────────────────────────────────
    'lets get rusty',            # 88.9K — most complete beginner-to-advanced Rust channel; cited by Placement Preparation
    'no boilerplate',            # 200K+ — concise high-signal Rust concept videos; Rust subreddit top recommendation
    'tensor programming',        # Rust web + systems; founded by Tauri contributor; practical focus
    'the cherno',                # 700K+ — Yan Chernikov (ex-EA); C++ game engine dev; Feedspot #1 C++ channel
    'caleb curry',               # 500K+ — C, C++, SQL; 1500+ tutorials; Placement Preparation top C++ list
    'neso academy',              # 760K+ — OS fundamentals, compiler design; 5-star rated; fills systems theory gap
    'the go programming language',  # 100K+ — Official Go channel; authoritative, covers language evolution
    'justforfunc',               # 40.1K — Francesc Campoy (ex-Google Go team); idiomatic Go, code reviews

    # ── Software Architecture / Engineering Excellence ────────────────────────
    'codeopinion',               # 125K+ — Derek Comartin; CQRS, event sourcing, microservices; Apiumhub top architecture list
    'dave farley',               # 300K+ — Continuous Delivery co-author; TDD, CI/CD philosophy; ThoughtWorks cited
    'goto conferences',          # 500K+ — Martin Fowler, Sam Newman, Kevlin Henney talks; conference-grade engineering
    'mark richards',             # 150K+ — "Software Architecture Monday" series; enterprise-scale patterns
    'derek banas',               # 2M+ — GoF design patterns series (29 videos); cited by FromDev as clearest implementation

    # ── Web3 / Blockchain / Solidity ─────────────────────────────────────────
    'patrick collins',           # 500K+ — Chainlink developer advocate; Alchemy #1 Solidity educator; free Foundry courses
    'dapp university',           # 100K+ — full-stack dApp development, DeFi protocols, smart contract security
    'eat the blocks',            # 230K+ — Julien Klepatch; Ethereum/DeFi/Solidity; Hardhat + Ethers.js projects
    'smart contract programmer', # 200K+ — Solidity 0.8, Vyper, smart contract exploits; unique security angle
    'nader dabit',               # 150K+ — Web3 + traditional dev bridge; GraphQL, DeFi; cited by Alchemy

    # ── Official Ecosystem Channels ──────────────────────────────────────────
    'google developers',         # 1M+ — Official Google; Flutter, TensorFlow, Web APIs, Firebase, Android
    'jetbrains',                 # 500K+ — Official JetBrains; Kotlin, IntelliJ tips, IDE workflows
    'microsoft developer',       # 500K+ — Official Microsoft; C#/.NET, VS Code, Azure, Copilot

    # ── Python (advanced — beyond Corey Schafer / Sentdex / Tech With Tim) ──
    'mcoding',                   # 220K+ — CPython internals, performance deep dives
    'real python',               # 270K+ — Official Real Python channel
    'anthony writes code',       # 90K — Anthony Sottile; pre-commit / Stripe core maintainer (authoritative)

    # ── Testing / QA / Test Automation ───────────────────────────────────────
    'automation step by step',   # 400K+ — Raghav Pal; Selenium/Cypress/Playwright; testmu.ai #1
    'execute automation',        # 82K+ — Karthik KK; Playwright C#, SpecFlow, API testing (authoritative)
    'the testing academy',       # 80K+ — Pramod Dutta; Cucumber/Playwright + SDET career
    'letcode',                   # 100K+ — Koushik; visually-rich Selenium/Playwright/Cypress

    # ── PHP / Laravel ────────────────────────────────────────────────────────
    'laracasts',                 # 260K+ — Jeffrey Way; #1 in every Laravel listicle (freek.dev, Kinsta)
    'laraveldaily',              # 110K+ — Povilas Korop; Laravel core contributor (authoritative)
    'codecourse',                # 140K+ — modern PHP / Laravel / REST APIs; freek.dev top-25
    'caleb porzio',              # 80K+ — creator of Livewire + AlpineJS (authoritative)
    'andrew schmelyun',          # 80K+ — advanced Laravel + Docker; Ash Allen top-25

    # ── Ruby / Rails ─────────────────────────────────────────────────────────
    'gorails',                   # 75K+ — Chris Oliver; THE Rails screencast; Feedspot/Stackify #1
    'drifting ruby',             # 20K+ — only other credible Rails screencast (authoritative)

    # ── .NET / C# ────────────────────────────────────────────────────────────
    'nick chapsas',              # 230K+ — weekly .NET ecosystem updates; gaudion.dev #1
    'iam tim corey',             # 400K+ — Microsoft MVP; comprehensive C# (every .NET list)
    'raw coding',                # 65K+ — ASP.NET Core deep internals (authoritative)

    # ── Git / Version Control ────────────────────────────────────────────────
    'the missing semester',      # 450K+ — MIT course; Git internals rigorously taught (authoritative)
    'github',                    # 950K+ — official GitHub; Universe talks, Actions, Copilot

    # ── Career / Soft Skills for Engineers ───────────────────────────────────
    'rahul pandey',              # 75K+ — ex-Meta/Pinterest TL, Stanford lecturer, Taro founder (authoritative)

    # ── Functional Programming ───────────────────────────────────────────────
    'funfunfunction',            # 290K+ — Mattias P. Johansson; canonical FP intro in JS

    # ── Data Analytics — Power BI, Tableau, Excel ────────────────────────────
    'guy in a cube',             # 430K+ — canonical Power BI; Microsoft MVPs cited
    'sqlbi',                     # 180K+ — Marco Russo / Alberto Ferrari; DAX canonical (authoritative)
    'tableau tim',               # 80K+ — Tableau-certified; Tableau community cited (authoritative)
    'excel is fun',              # 900K+ — Mike Girvin; canonical advanced Excel
    'leila gharani',             # 2.5M — advanced Excel, Power Query; widely cited
    'my online training hub',    # 700K+ — Mynda Treacy; Excel MVP

    # ── International / Multilingual Educators ───────────────────────────────
    'code with harry',           # 7M+ — Hindi/English MERN, DSA; heavily watched in SEA including PH
    'anuj bhaiya',               # 300K+ — clean DSA / placement prep; PH competitive programming groups
    'love babbar',               # 1M+ — 450 DSA sheet + interview prep; SEA CS community
    'midudev',                   # 900K+ — top Spanish-lang JS / React; auto-translated captions
    'fazt',                      # 800K+ — Peruvian full-stack (Node/Django/React); reliable EN subs
    'nomad coders',              # 700K+ — Korean (Nico); React Native, Flutter, clones with EN subs
    'hongpong',                  # 200K+ — Korean game / graphics (C++, Unity); PH game dev students
    'mayuko',                    # 400K+ — ex-Intuit iOS engineer; Swift / SwiftUI + career
    'ania kubow',                # 300K+ — project-based JS / React / game dev
    'coder coder',               # 300K+ — Jessica Chan; HTML / CSS / JS fundamentals
}


def _parse_iso_duration(duration_str: str) -> int:
    """Parse a YouTube ISO 8601 duration string (e.g. 'PT1H30M20S') into total minutes."""
    match = re.match(r'PT(?:(\d+)H)?(?:(\d+)M)?(?:(\d+)S)?', duration_str or '')
    if not match:
        return 0
    h = int(match.group(1) or 0)
    m = int(match.group(2) or 0)
    s = int(match.group(3) or 0)
    return h * 60 + m + (1 if s >= 30 else 0)


def _fetch_video_details(video_ids: list) -> dict:
    """
    Fetch contentDetails and statistics for a list of video IDs.
    Returns a dict keyed by videoId: {'duration_minutes': int, 'view_count': int}.
    Retried with backoff on transient YouTube errors.
    """
    if not video_ids:
        return {}
    try:
        youtube = _get_youtube_client()
        request = youtube.videos().list(
            part='contentDetails,statistics',
            id=','.join(video_ids),
        )
        response = _retry_youtube(request.execute, label='videos.list')
        result = {}
        for item in response.get('items', []):
            vid = item['id']
            duration_str = item.get('contentDetails', {}).get('duration', '')
            view_count = int(item.get('statistics', {}).get('viewCount', 0) or 0)
            result[vid] = {
                'duration_minutes': _parse_iso_duration(duration_str),
                'view_count': view_count,
            }
        return result
    except Exception as e:
        logger.error('YouTube video details fetch failed: %s', e)
        return {}


# Minimum video length for educational content (filters out intros, clips, YouTube Shorts)
# Research: Udemy avg lecture = 8–15 min; videos < 10 min are typically too shallow for a lesson
MIN_VIDEO_MINUTES = 10

# Titles matching these patterns are opinion/meta/clickbait videos — not actual lessons.
# Score them -3 so they only win if there are literally no other options.
_META_TITLE_PATTERNS = [
    'has changed', 'is dead', 'is dying', 'you should stop', 'stop using',
    'nobody talks about', 'every developer should', 'this will change',
    'changed everything', 'honest opinion', 'my opinion', 'the truth about',
    'why i quit', 'why i left', 'i was wrong', 'unpopular opinion',
    'reaction to', 'responding to', 'what nobody tells you', 'you need to know',
    'tier list', 'ranked', 'vlog', 'q&a', 'ama', 'best of', 'top 10', 'top 5',
    'roadmap 2024', 'roadmap 2025', 'roadmap 2026', 'roadmap 2027',
    # Time-challenge content — always surface-level, rarely a deep tutorial
    'in 1 day', 'in 7 days', 'in 30 days', 'in one day', 'in one week',
    # Comparison / debate (space-padded) — opinion pieces, not tutorials
    ' vs ',
    # Hype / engagement bait
    'watch before', 'before you start', 'changed my life',
    'why most developers', 'most programmers',
    # Roundup listicles — not node-specific tutorials
    'tools every', 'every developer should',
]

# ---------------------------------------------------------------------------
# Difficulty-aware query construction — fixes the "expert nodes still get
# beginner tutorials" bug. RoadmapNode.difficulty is 1-5; for years every
# search was "{title} tutorial" regardless of level. Now each difficulty tier
# has its own suffix pool, and _pick_best_video scores titles by level match.
# ---------------------------------------------------------------------------
_DIFFICULTY_SUFFIX_POOL = {
    1: ['tutorial', 'for beginners', 'crash course', 'explained simply', 'intro'],
    2: ['tutorial', 'walkthrough', 'guide', 'crash course', 'step by step'],
    3: ['intermediate guide', 'deep dive', 'best practices', 'in depth', 'real world examples'],
    4: ['advanced', 'internals', 'deep dive', 'under the hood', 'architecture', 'patterns'],
    5: ['advanced masterclass', 'internals', 'source code walkthrough',
        'production patterns', 'at scale', 'performance optimization'],
}

# Title substrings that strongly signal beginner content. For intermediate+
# nodes (difficulty >= 3) these incur a score penalty.
_BEGINNER_MARKERS = (
    'beginner', 'beginners', 'intro to', 'introduction to',
    'basics', 'getting started', '101', 'for dummies',
)

# Title substrings that signal advanced / production-grade content. For
# advanced nodes these earn a bonus; for beginner nodes (difficulty <= 2)
# they incur a penalty (so we don't throw expert talks at newbies).
_ADVANCED_MARKERS = (
    'advanced', 'deep dive', 'internals', 'masterclass',
    'in depth', 'under the hood', 'at scale', 'production',
)

# Varied YouTube search parameters — rotating these gives cross-user and
# cross-node diversity without changing the query text.
_ORDER_POOL = ('relevance', 'viewCount', 'rating')
_DURATION_POOL = ('medium', 'long', None)  # avoid 'short' (< 4 min = too shallow)


def _deterministic_rng(user_id, node_id) -> random.Random:
    """
    Stable per-(user, node) PRNG. Same (user, node) always produces the same
    variants so re-running populate doesn't flap. Different users on the same
    node get different variants — the core diversity lever.

    Uses SHA-1 instead of Python's builtin hash() because the latter is salted
    per-process, which would make results non-reproducible across restarts.
    """
    key = f'{user_id or 0}:{node_id or 0}'.encode()
    seed = int.from_bytes(hashlib.sha1(key).digest()[:8], 'big')
    return random.Random(seed)


def _clamp_difficulty(d) -> int:
    try:
        return max(1, min(5, int(d)))
    except (TypeError, ValueError):
        return 2


def _enrich_short_title(node_title: str, node_description: str) -> str:
    """
    Widen an ambiguous node title by prepending the first 4 words of the
    description. Prevents over-broad queries for single-word or two-word
    titles like "Arrays", "Git", "SQL" that otherwise return unrelated content.
    Only applies when the title is <= 2 words and a description exists.
    """
    if len((node_title or '').split()) <= 2 and node_description:
        context = ' '.join(node_description.split()[:4])
        return f'{node_title} {context}'
    return node_title


# Current year appended to beginner queries so learners get fresh content
# instead of high-ranking 2016-era tutorials.
_CURRENT_YEAR = str(datetime.date.today().year)


def _build_query_variants(node_title: str, node_description: str,
                          difficulty: int, user_id, node_id) -> list:
    """
    Build 2-3 search query variants for a node, parametrized by difficulty
    and deterministically chosen per (user, node). Returns at least one
    variant even when node_title is a single word.

    Changes vs original:
    - B2: short/ambiguous titles (<= 2 words) are enriched with description context
    - B3: difficulty 1-2 variants include the current year to bias toward fresh content
    """
    # B2 — anchor ambiguous titles with description context
    effective_title = _enrich_short_title(node_title or '', node_description or '')

    d = _clamp_difficulty(difficulty)
    pool = _DIFFICULTY_SUFFIX_POOL[d]
    rng = _deterministic_rng(user_id, node_id)
    picked = rng.sample(pool, k=min(2, len(pool)))
    base = effective_title.strip()
    variants = [f'{base} {suffix}'.strip() for suffix in picked if base]
    if node_description and len(node_description.split()) >= 4:
        variants.append(node_description.strip()[:80])
    if not variants:
        variants = [base or 'programming tutorial']

    # B3 — append current year to one variant for beginner nodes so stale
    # 2016-era tutorials don't dominate (they rank high on "relevance" but
    # their syntax/APIs are outdated).
    if d <= 2 and variants:
        variants[0] = f'{variants[0]} {_CURRENT_YEAR}'

    return variants


def _rotate(pool, user_id, node_id, salt: int = 0):
    """Pick one element from `pool` deterministically per (user, node, salt)."""
    rng = _deterministic_rng(f'{user_id}-{salt}', node_id)
    return rng.choice(tuple(pool))


# ---------------------------------------------------------------------------
# Resilience — retry transient YouTube errors with exponential backoff + jitter.
# Research (johal.in, 2026): exp backoff + jitter improves API success rate
# 68% → 94% on transient failures. We retry 429 / 5xx, fail fast on quota /
# auth errors (retrying those just wastes budget and pressure).
# ---------------------------------------------------------------------------
_RETRIABLE_HTTP_STATUS = frozenset({429, 500, 502, 503, 504})
_BACKOFF_BASE_SECONDS = 1.0
_BACKOFF_MAX_ATTEMPTS = 3


def _http_status(exc) -> int | None:
    """Best-effort status extraction from google-api-python-client HttpError."""
    status = getattr(getattr(exc, 'resp', None), 'status', None)
    try:
        return int(status) if status is not None else None
    except (TypeError, ValueError):
        return None


def _is_quota_or_auth_error(exc) -> bool:
    """403 from YouTube is either quotaExceeded or keyInvalid — both are fatal."""
    return _http_status(exc) == 403


def _retry_youtube(call, *, attempts: int = _BACKOFF_MAX_ATTEMPTS, label: str = 'search'):
    """
    Execute `call()` with retry on transient failures.
    `call` is a zero-arg callable (a `request.execute` thunk).
    """
    last_exc = None
    for attempt in range(1, attempts + 1):
        try:
            return call()
        except Exception as exc:   # noqa: BLE001 — googleapiclient raises HttpError subclasses
            last_exc = exc
            status = _http_status(exc)
            if _is_quota_or_auth_error(exc):
                logger.warning('[YT] %s quota/key error — aborting (attempt %d): %s', label, attempt, exc)
                raise
            if status is not None and status not in _RETRIABLE_HTTP_STATUS:
                # Non-retriable (4xx other than 429) — surface immediately.
                raise
            if attempt >= attempts:
                break
            delay = _BACKOFF_BASE_SECONDS * (2 ** (attempt - 1))
            delay *= 0.8 + random.random() * 0.4   # ±20% jitter
            logger.info('[YT] %s retry attempt=%d delay=%.2fs status=%s',
                        label, attempt, delay, status)
            time.sleep(delay)
    # Exhausted retries.
    raise last_exc if last_exc else RuntimeError('retry loop exited without error')


def _cache_key(*parts) -> str:
    raw = '|'.join(str(p) for p in parts)
    return 'yt:search:' + hashlib.sha1(raw.encode()).hexdigest()


def _title_is_relevant(title: str, search_query: str, topic_keyword: str) -> bool:
    """
    Check if a video title is topically relevant to the lesson.
    Requires at least one significant keyword from the search query or node title to appear in the title.

    Stopword set intentionally narrow: previously stripped 'tutorial', 'course',
    'beginner(s)', 'guide' too, which discarded the level signals we now use
    for difficulty-aware scoring. Keep only true filler words.
    """
    title_lower = title.lower()
    # Extract meaningful words (3+ chars) from search query and topic keyword
    query_words = set(
        w for w in re.split(r'[\s\-_/]+', (search_query + ' ' + topic_keyword).lower())
        if len(w) >= 3 and w not in {'for', 'the', 'and', 'with', 'how', 'what', 'that', 'this',
                                      'from', 'into', 'using', 'learn', 'full', 'free'}
    )
    return any(w in title_lower for w in query_words)


def _pick_best_video(videos: list, topic_keyword: str, search_query: str = '',
                     difficulty: int = 2, used_video_ids: set | None = None,
                     used_channels: set | None = None) -> dict | None:
    """
    Pick the most credible video from a list by scoring channel + title
    relevance + duration, with difficulty awareness and cross-node dedup.

    difficulty    — 1..5 from RoadmapNode.difficulty; biases scoring toward
                    beginner-marked or advanced-marked titles accordingly.
                    Prior to this change every search ignored difficulty and
                    returned "X Tutorial for Beginners" for every node.

    used_video_ids / used_channels — set of already-picked resources in this
                    roadmap. Same video_id is a hard block; same channel is a
                    soft penalty so one channel can't fill every node (MMR
                    cross-node diversification).
    """
    if not videos:
        return None

    used_video_ids = used_video_ids or set()
    used_channels = {c.lower() for c in (used_channels or set()) if c}
    d = _clamp_difficulty(difficulty)

    # Hard-filter: already used in this roadmap — never return the same video twice.
    videos = [v for v in videos if v['youtube_video_id'] not in used_video_ids]
    if not videos:
        return None

    # Exclude videos confirmed shorter than minimum (duration=0 means unknown → keep)
    eligible = [v for v in videos if not (0 < v.get('duration_minutes', 0) < MIN_VIDEO_MINUTES)]
    candidates = eligible if eligible else videos  # fall back to all if every video is too short

    def score(v):
        s = 0
        title_lower = v['title'].lower()
        channel_lower = v['youtube_channel'].lower()

        # Penalise meta/opinion/clickbait titles — they're not lessons
        if any(pat in title_lower for pat in _META_TITLE_PATTERNS):
            s -= 3

        # Channel bonus ONLY applies when the title is actually about the topic.
        # This prevents a famous channel from promoting an off-topic video over
        # a more relevant video from an unknown channel.
        title_relevant = _title_is_relevant(v['title'], search_query, topic_keyword)
        if channel_lower in PREFERRED_CHANNELS and title_relevant:
            s += 2

        if title_relevant:
            s += 1   # title directly covers the topic

        # Difficulty-aware level matching — the part that fixes
        # "intermediate nodes still suggest basics."
        has_beginner_marker = any(m in title_lower for m in _BEGINNER_MARKERS)
        has_advanced_marker = any(m in title_lower for m in _ADVANCED_MARKERS)
        if d >= 3 and has_beginner_marker:
            s -= 3   # intermediate+ nodes shouldn't show "for beginners"
        if d >= 4 and has_advanced_marker:
            s += 2   # reward advanced framing for hard nodes
        if d <= 2 and has_advanced_marker and not has_beginner_marker:
            s -= 2   # don't drop expert talks on beginner nodes either

        dur = v.get('duration_minutes', 0)
        if MIN_VIDEO_MINUTES <= dur <= 20:
            s += 2   # strongly prefer focused lessons (Udemy avg: 8-15 min)
        elif 0 < dur <= 35:
            s += 1   # acceptable — longer but still reasonable
        if d >= 4 and dur >= 20:
            s += 1   # advanced nodes benefit from longer-form coverage

        # Cross-node diversity: soft penalty for reusing a channel that's
        # already supplying videos elsewhere in this roadmap.
        if channel_lower in used_channels:
            s -= 2
        return s

    return max(candidates, key=score)


def _get_youtube_client():
    """Return a cached YouTube API client (avoids re-fetching discovery doc on every call)."""
    global _youtube_client
    if _youtube_client is None:
        from googleapiclient.discovery import build
        _youtube_client = build('youtube', 'v3', developerKey=settings.YOUTUBE_API_KEY)
    return _youtube_client


def search_youtube(query: str, max_results: int = 5, language: str = 'en',
                   order: str = 'relevance', video_duration: str | None = None) -> list:
    """
    Search YouTube for videos matching the query.
    Returns a list of video dicts with title, videoId, thumbnail, channel.

    order         — 'relevance' | 'viewCount' | 'rating' | 'date' | 'title'
                    Rotated per (user, node) in the caller to diversify results.
    video_duration — 'short' | 'medium' | 'long' | None
                    When set, YouTube filters by duration bucket. 'long' is
                    preferred for advanced nodes (deeper coverage).

    Responses are cached in Django cache (Redis backend) keyed by the full
    parameter tuple, so repeat queries across users cost 0 quota units.
    """
    if not settings.YOUTUBE_API_KEY:
        return []

    ckey = _cache_key('q', query, order, video_duration or '-', language, max_results)
    cached = cache.get(ckey)
    if cached is not None:
        logger.debug('[YT] cache hit query=%r order=%s dur=%s', query, order, video_duration)
        return cached

    try:
        youtube = _get_youtube_client()

        list_kwargs = dict(
            part='snippet',
            q=query,
            type='video',
            maxResults=max_results,
            relevanceLanguage=language,
            order=order,
        )
        if video_duration in ('short', 'medium', 'long'):
            list_kwargs['videoDuration'] = video_duration

        request = youtube.search().list(**list_kwargs)
        response = _retry_youtube(request.execute, label='search.list')

        results = []
        for item in response.get('items', []):
            snippet = item['snippet']
            results.append({
                'youtube_video_id': item['id']['videoId'],
                'title': snippet['title'],
                'youtube_channel': snippet['channelTitle'],
                'thumbnail_url': snippet['thumbnails'].get('medium', {}).get('url', ''),
                'description': snippet['description'][:300],
                'url': f'https://www.youtube.com/watch?v={item["id"]["videoId"]}',
                'resource_type': 'youtube_video',
                'is_free': True,
                'language': language,
            })

        # Enrich results with duration and view count from video details endpoint
        video_ids = [r['youtube_video_id'] for r in results]
        details = _fetch_video_details(video_ids)
        for r in results:
            vid_details = details.get(r['youtube_video_id'], {})
            r['duration_minutes'] = vid_details.get('duration_minutes', 0)
            r['view_count'] = vid_details.get('view_count', 0)

        try:
            cache.set(ckey, results, _CACHE_TTL_SECONDS)
        except Exception as cache_err:
            logger.debug('[YT] cache set failed (non-fatal): %s', cache_err)

        return results
    except Exception as e:
        logger.error('YouTube search failed for query %r: %s', query, e)
        return []


def _video_passes_quality(title: str) -> bool:
    """Return False if the video title matches known meta/opinion/clickbait patterns."""
    t = title.lower()
    return not any(pat in t for pat in _META_TITLE_PATTERNS)


def _search_for_node(node, used_video_ids: set, used_channels: set,
                     extra_exclude: set | None = None) -> dict | None:
    """
    Diversified search for a single node. Builds 2-3 query variants (difficulty-
    parametrized, deterministic per user+node), rotates order + videoDuration,
    aggregates all candidates, then picks the best with difficulty + dedup scoring.

    Falls back to legacy "{title} tutorial" when _DIVERSIFY_ENABLED is False.
    """
    user_id = getattr(getattr(node, 'roadmap', None), 'user_id', None)
    node_id = getattr(node, 'pk', None) or getattr(node, 'id', None)
    difficulty = _clamp_difficulty(getattr(node, 'difficulty', 2))

    if not _DIVERSIFY_ENABLED:
        videos = search_youtube(f'{node.title} tutorial', max_results=5)
        if extra_exclude:
            videos = [v for v in videos if v['youtube_video_id'] not in extra_exclude]
        return _pick_best_video(
            videos, node.title, search_query=f'{node.title} tutorial',
            difficulty=difficulty, used_video_ids=used_video_ids,
            used_channels=used_channels,
        )

    variants = _build_query_variants(
        node.title, getattr(node, 'description', ''), difficulty, user_id, node_id,
    )
    # Rotate parameters deterministically — same (user, node) → stable picks,
    # different users → different picks. Bias advanced nodes toward 'long'.
    order = _rotate(_ORDER_POOL, user_id, node_id, salt=1)
    base_duration_pool = ('long', 'medium', None) if difficulty >= 4 else _DURATION_POOL
    video_duration = _rotate(base_duration_pool, user_id, node_id, salt=2)

    aggregated = {}
    for q in variants:
        for v in search_youtube(
            q, max_results=5, order=order, video_duration=video_duration,
        ):
            aggregated[v['youtube_video_id']] = v   # de-dupe across variants

    if extra_exclude:
        for vid_id in extra_exclude:
            aggregated.pop(vid_id, None)

    candidates = list(aggregated.values())
    if not candidates:
        return None
    # Use the first variant as the search-query context for scoring — it's the
    # cleanest reflection of the node title + level suffix.
    return _pick_best_video(
        candidates, node.title, search_query=variants[0],
        difficulty=difficulty, used_video_ids=used_video_ids,
        used_channels=used_channels,
    )


def populate_node_resources(node) -> int:
    """
    For a RoadmapNode:
    1. Fill placeholder resources (url='') with real YouTube videos.
    2. Re-evaluate already-stored videos — if the stored title fails quality check,
       search for a better replacement. This handles bad videos saved before the
       quality filter existed.

    Diversification: queries are difficulty-parametrized and deterministic per
    (user, node), so different users get different videos on the same node and
    re-runs are stable. Cross-node dedup prevents the same channel/video from
    dominating one roadmap.

    Returns count of resources populated or replaced.
    """
    from apps.roadmaps.models import NodeResource

    populated = 0

    # Pull every already-populated (video_id, channel) for this roadmap so
    # _pick_best_video can soft/hard penalize collisions. Done once per call.
    if _DIVERSIFY_ENABLED and getattr(node, 'roadmap_id', None):
        used = NodeResource.objects.filter(
            node__roadmap_id=node.roadmap_id,
            resource_type='youtube_video',
        ).exclude(url__in=['', 'yt:unavailable']).values_list(
            'youtube_video_id', 'youtube_channel',
        )
        used_video_ids = {v for v, _ in used if v}
        used_channels = {c for _, c in used if c}
    else:
        used_video_ids = set()
        used_channels = set()

    # ── Pass 1: fill blank placeholders ────────────────────────────────────────
    placeholders = NodeResource.objects.filter(
        node=node,
        resource_type='youtube_video',
        url='',
    )
    for placeholder in placeholders:
        video = _search_for_node(node, used_video_ids, used_channels)
        if video:
            placeholder.url = video['url']
            placeholder.youtube_video_id = video['youtube_video_id']
            placeholder.youtube_channel = video['youtube_channel']
            placeholder.thumbnail_url = video['thumbnail_url']
            placeholder.title = video['title']
            placeholder.description = ''
            placeholder.duration_minutes = video.get('duration_minutes') or None
            placeholder.save()
            # Accumulate as used for subsequent placeholders on this node.
            used_video_ids.add(video['youtube_video_id'])
            if video.get('youtube_channel'):
                used_channels.add(video['youtube_channel'])
            populated += 1
        else:
            placeholder.url = 'yt:unavailable'
            placeholder.save(update_fields=['url'])

    # ── Pass 2: dynamically replace low-quality existing videos ────────────────
    existing = NodeResource.objects.filter(
        node=node,
        resource_type='youtube_video',
    ).exclude(url__in=['', 'yt:unavailable'])

    for resource in existing:
        if _video_passes_quality(resource.title):
            continue  # already a good video — leave it alone
        # This video's title looks like meta/opinion content — find a better one.
        # Exclude the current bad video from candidates.
        old_title = resource.title
        video = _search_for_node(
            node, used_video_ids, used_channels,
            extra_exclude={resource.youtube_video_id},
        )
        if video:
            resource.url = video['url']
            resource.youtube_video_id = video['youtube_video_id']
            resource.youtube_channel = video['youtube_channel']
            resource.thumbnail_url = video['thumbnail_url']
            resource.title = video['title']
            resource.duration_minutes = video.get('duration_minutes') or None
            resource.save()
            logger.info(
                'Replaced low-quality video "%s" with "%s" for node "%s"',
                old_title, video['title'], node.title,
            )
            used_video_ids.add(video['youtube_video_id'])
            if video.get('youtube_channel'):
                used_channels.add(video['youtube_channel'])
            populated += 1

    return populated
