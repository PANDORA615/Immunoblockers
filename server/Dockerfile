FROM python:3.13-slim
WORKDIR /app
COPY relay.py .
RUN useradd --uid 10001 --create-home relay && mkdir /data && chown relay /data
USER relay
ENV PAIR_DB=/data/pairs.sqlite3
EXPOSE 8080
CMD ["python", "relay.py"]
