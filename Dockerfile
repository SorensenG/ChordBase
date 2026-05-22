# syntax=docker/dockerfile:1.7

FROM eclipse-temurin:21-jdk-noble AS build

WORKDIR /workspace

COPY .mvn .mvn
COPY mvnw pom.xml ./
RUN ./mvnw -DskipTests dependency:go-offline

COPY src src
RUN ./mvnw -DskipTests package

FROM eclipse-temurin:21-jre-noble

ENV PYTHONDONTWRITEBYTECODE=1 \
    PYTHONUNBUFFERED=1 \
    DEBIAN_FRONTEND=noninteractive \
    PIP_NO_CACHE_DIR=1 \
    PATH="/opt/extractor-venv/bin:${PATH}"

WORKDIR /app

RUN apt-get update && apt-get install -y --no-install-recommends \
    bash \
    curl \
    python3 \
    python3-pip \
    python3-venv \
    tesseract-ocr \
    tesseract-ocr-por \
    tesseract-ocr-eng \
    poppler-utils \
    libheif1 \
    libglib2.0-0 \
    libgl1 \
    && rm -rf /var/lib/apt/lists/*

COPY extractor/requirements.txt /app/extractor/requirements.txt
RUN python3 -m venv /opt/extractor-venv \
    && pip install --upgrade pip \
    && pip install -r /app/extractor/requirements.txt

COPY --from=build /workspace/target/*.jar /app/app.jar
COPY extractor/app /app/extractor/app
COPY start.sh /app/start.sh

RUN useradd --create-home --shell /usr/sbin/nologin appuser \
    && chmod +x /app/start.sh \
    && chown -R appuser:appuser /app /opt/extractor-venv

USER appuser

EXPOSE 8080

ENTRYPOINT ["/app/start.sh"]
