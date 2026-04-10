#!/usr/bin/env bash
set -o errexit

VENV=/opt/render/project/src/.venv

# Install into Render's venv explicitly
$VENV/bin/pip install -r requirements.txt
$VENV/bin/python manage.py collectstatic --noinput
$VENV/bin/python manage.py migrate
