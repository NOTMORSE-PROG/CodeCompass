web: gunicorn config.asgi:application -k uvicorn.workers.UvicornWorker --bind 0.0.0.0:$PORT --workers 1
worker: celery -A config worker --loglevel=info
beat: celery -A config beat --loglevel=info
