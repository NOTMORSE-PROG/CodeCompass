#!/usr/bin/env bash
set -o errexit

pip install -r requirements.txt
python manage.py collectstatic --noinput
python manage.py migrate

# Write start.sh using the exact python binary path found during build
PYTHON_BIN=$(which python)
cat > start.sh << EOF
#!/bin/bash
exec $PYTHON_BIN -m gunicorn config.asgi:application -k uvicorn.workers.UvicornWorker --bind 0.0.0.0:\$PORT --workers 1
EOF
chmod +x start.sh
