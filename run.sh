#!/usr/bin/env bash
HTTPBACKEND_HOST=localhost \
HTTPBACKEND_PORT=9090 \
PORT=9096 SERVICE_PORT=9096 \
java  -jar target/call-simple-microservice-1.0-SNAPSHOT-fat.jar
