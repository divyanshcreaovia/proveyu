# Piper text-to-speech as a small HTTP service: POST / {"text": "..."} -> audio/wav
FROM python:3.12-slim
ARG VOICE=en_US-lessac-medium
ENV VOICE=${VOICE}
RUN pip install --no-cache-dir "piper-tts[http]==1.3.0"
WORKDIR /voices
RUN python -m piper.download_voices ${VOICE}
USER 65534
EXPOSE 5000
CMD ["sh", "-c", "python -m piper.http_server --host 0.0.0.0 --port 5000 --data-dir /voices -m ${VOICE}"]
