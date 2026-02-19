web: daphne config.asgi:application --port $PORT --bind 0.0.0.0 -v2
worker: celery -A config worker --loglevel=info
beat: celery -A config beat --loglevel=info
