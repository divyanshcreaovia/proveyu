# Sandbox image for mock-test coding questions. Built once:
#   docker build -t proveyu-code-runner:1 Backend/runner
# Each run uses: --network none --read-only --memory 256m --cpus 1 --pids-limit 64 --user 65534 (see DockerCodeRunner).
FROM alpine:3.20
RUN apk add --no-cache python3 nodejs openjdk21-jdk g++ coreutils bash
ENV PATH="/usr/lib/jvm/java-21-openjdk/bin:${PATH}"
WORKDIR /tmp
