#!/usr/bin/env python3
"""Build an example, exercise its real HTTP API, and clean up test resources."""

import argparse
import base64
from concurrent.futures import ThreadPoolExecutor
import hashlib
from html.parser import HTMLParser
import io
import json
import os
import random
from pathlib import Path
import re
import signal
import socket
import subprocess
import sys
import tempfile
import time
from urllib.error import HTTPError, URLError
from urllib.parse import parse_qs, urlencode, urljoin, urlparse
from urllib.request import ProxyHandler, Request, build_opener
import uuid
import zipfile
import xml.etree.ElementTree as ET


ROOT = Path(__file__).resolve().parents[1]
MODULES = {
    "common": "common-tool-example",
    "oss": "oss-upload-examples",
    "local-cache": "local-cache-example",
    "docs": "docs-example",
    "excel": "excel-example",
    "i18n": "i18n-example",
    "desensitize": "desensitize-example",
    "dict": "dict-example",
    "multi-redis": "multi-redis-example",
    "lock": "lock-example",
    "rate-limiter": "rate-limiter-example",
    "idempotent": "idempotent-example",
    "ip2region": "ip2region-example",
    "sensitive-word": "sensitive-word-example",
    "disruptor": "disruptor-example",
    "sftp": "sftp-example",
    "script": "script-example",
    "dynamic-threadpool": "dynamic-threadpool-example",
}
OPENER = build_opener(ProxyHandler({}))


class HtmlAssets(HTMLParser):
    """提取真实页面的本地脚本和样式链接，不执行 HTML 中的代码。"""

    def __init__(self):
        super().__init__()
        self.urls = []

    def handle_starttag(self, tag, attributes):
        values = dict(attributes)
        if tag == "script" and values.get("src"):
            self.urls.append(values["src"])
        if tag == "link" and values.get("rel") == "stylesheet" and values.get("href"):
            self.urls.append(values["href"])


def require(condition, message):
    if not condition:
        raise AssertionError(message)


def request(url, method="GET", data=None, headers=None):
    req = Request(url, data=data, headers=headers or {}, method=method)
    try:
        with OPENER.open(req, timeout=90) as response:
            return response.status, response.read()
    except HTTPError as error:
        with error:
            return error.code, error.read()


def api(base, path, method="GET", payload=None, query=None, expected=200, headers=None):
    url = base + "/api/" + path
    if query:
        url += "?" + urlencode(query)
    data = None if payload is None else json.dumps(payload).encode()
    status, body = request(url, method, data, {"Content-Type": "application/json", **(headers or {})})
    require(status == expected, f"{method} {path}: expected HTTP {expected}, got {status}")
    document = json.loads(body)
    require(document["code"] == (0 if expected == 200 else expected), f"{path}: unexpected business code")
    return document.get("data")


def upload(base, path, content, query, method="POST", prefix="oss"):
    boundary = "common-tool-" + uuid.uuid4().hex
    body = (
        f"--{boundary}\r\n"
        'Content-Disposition: form-data; name="file"; filename="fixture.bin"\r\n'
        "Content-Type: application/octet-stream\r\n\r\n"
    ).encode() + content + f"\r\n--{boundary}--\r\n".encode()
    status, result = request(
        base + "/api/" + prefix + "/" + path + "?" + urlencode(query), method, body,
        {"Content-Type": f"multipart/form-data; boundary={boundary}"},
    )
    require(status == 200, f"{method} {path}: expected HTTP 200, got {status}")
    document = json.loads(result)
    require(document["code"] == 0, f"{path}: upload failed")
    return document["data"]


def check_download(base, key, expected_content):
    status, actual = request(base + "/api/oss/download?" + urlencode({"objectName": key}))
    require(status == 200, "download: unexpected HTTP status")
    require(hashlib.sha256(actual).digest() == hashlib.sha256(expected_content).digest(),
            "download: SHA-256 mismatch")


def test_common(base, record):
    data = api(base, "demo/ping")
    require(data == {"application": "common-tool-example", "status": "UP"}, "ping: invalid response")
    record("common-response")


def test_ip2region(base, record):
    require(api(base, "ip/search", query={"ip": "8.8.8.8"})["country"] == "美国", "XDB lookup mismatch")
    record("ip-real-xdb-query")
    with ThreadPoolExecutor(max_workers=8) as pool:
        results = list(pool.map(lambda _: api(base, "ip/search", query={"ip": "8.8.8.8"}), range(40)))
    require(all(result == results[0] for result in results), "concurrent IP results differ")
    record("ip-concurrent-queries")
    require(api(base, "ip/search", query={"ip": "008.8.8.8"})["country"] == "美国",
            "original decimal IPv4 spelling changed")
    for value in ("localhost", "::1", "256.0.0.1", ""):
        api(base, "ip/search", query={"ip": value}, expected=400)
    record("ip-invalid-input")


def test_sensitive_word(base, record):
    status, body = request(base + "/api/sensitive/process", "POST", " badge B A D! 😀".encode(),
                           {"Content-Type": "text/plain; charset=utf-8"})
    result = json.loads(body)
    require(status == 200 and result["data"]["text"] == " badge *****! 😀", "sensitive replacement mismatch")
    require(result["data"]["matches"] == [{"word": "B A D", "startIndex": 7, "endIndex": 12}],
            "original match indices differ")
    record("sensitive-whitelist-whitespace-unicode")
    for text, expected in (("BAD", 400), ("badge", 200), ("x" * 65537, 400)):
        status, body = request(base + "/api/sensitive/reject", "POST", text.encode(), {"Content-Type": "text/plain"})
        require(status == expected and json.loads(body)["code"] == (0 if expected == 200 else expected),
                "sensitive rejection policy failed")
    record("sensitive-rejection-and-bounds")


def test_disruptor(base, record):
    require(api(base, "disruptor/send", "POST", query={"value": 2})["total"] == 2, "consumer did not run")
    api(base, "disruptor/send", "POST", query={"value": -1}, expected=409)
    require(api(base, "disruptor/send", "POST", query={"value": 3})["total"] == 5, "consumer did not recover")
    record("disruptor-consume-failure-recovery")
    with ThreadPoolExecutor(max_workers=8) as pool:
        list(pool.map(lambda _: api(base, "disruptor/send", "POST", query={"value": 1}), range(50)))
    require(api(base, "disruptor/send", "POST", query={"value": 0})["total"] == 55, "concurrent messages lost")
    record("disruptor-multiple-producers")
    api(base, "disruptor/send", "POST", query={"queue": "missing", "value": 1}, expected=400)
    record("disruptor-invalid-queue")


def test_script(base, record):
    require(api(base, "script/run", query={"a": 2, "b": 3})["value"] == 5, "Groovy sum incorrect")
    require(api(base, "script/run", query={"name": "product", "a": 2, "b": 3})["value"] == 6, "Groovy product incorrect")
    with ThreadPoolExecutor(max_workers=4) as pool:
        results = list(pool.map(lambda n: api(base, "script/run", query={"a": n, "b": 1})["value"], range(12)))
    require(results == list(range(1, 13)), "script parameters leaked across calls")
    record("script-real-execution-and-isolation")
    api(base, "script/run", query={"name": "timeout"}, expected=504)
    require(api(base, "script/run", query={"a": 20, "b": 22})["value"] == 42, "script worker failed after timeout")
    api(base, "script/run", query={"name": "untrusted"}, expected=400)
    record("script-timeout-recovery-and-allowlist")


def test_threadpool(base, record):
    require(api(base, "pools/run")["thread"].startswith("dynamic-demo-"), "task did not use named pool")
    with ThreadPoolExecutor(max_workers=10) as clients:
        results = list(clients.map(lambda _: request(base + "/api/pools/run?delay=400")[0], range(10)))
    require(429 in results and 200 in results and set(results) <= {200, 429}, "bounded pool did not reject overload")
    require(api(base, "pools/stats")["rejected"] > 0, "rejections were not counted")
    record("threadpool-execution-and-backpressure")
    grown = api(base, "pools/resize", "POST", query={"core": 4, "max": 4})
    require(grown["core"] == grown["max"] == 4 and grown["capacity"] == 2, "pool expansion failed")
    shrunk = api(base, "pools/resize", "POST", query={"core": 1, "max": 1})
    require(shrunk["core"] == shrunk["max"] == 1, "pool shrink failed")
    api(base, "pools/resize", "POST", query={"core": 4, "max": 1}, expected=400)
    require(api(base, "pools/stats")["core"] == 1, "invalid update changed configuration")
    record("threadpool-resize-and-invalid-update")


def start_sftp(jar, environment, temporary):
    root = Path(temporary)
    environment.update(TEST_SFTP_ROOT=str(root), TEST_SFTP_USERNAME="tester", TEST_SFTP_PASSWORD=uuid.uuid4().hex)
    with (root / "sftp-server.log").open("w") as log:
        server = subprocess.Popen(["java", "-jar", str(jar), "--spring.profiles.active=sftp-fixture",
                                   "--spring.main.web-application-type=none", "--spring.main.keep-alive=true",
                                   "--sftp-pool.enable=false"],
                                  env=environment, stdout=log, stderr=subprocess.STDOUT)
    try:
        deadline = time.monotonic() + 60
        while time.monotonic() < deadline:
            require(server.poll() is None, "SFTP fixture process exited")
            port_file = root / "port"
            if port_file.exists() and port_file.read_text().isdigit():
                environment.update(TEST_SFTP_ENABLED="true", TEST_SFTP_HOST="127.0.0.1",
                                   TEST_SFTP_PORT=port_file.read_text(),
                                   TEST_SFTP_KNOWN_HOSTS=str(root / "known_hosts"))
                return server
            time.sleep(0.2)
        raise AssertionError("SFTP fixture startup timed out")
    except BaseException:
        stop_process(server)
        raise


def test_sftp(base, record, environment):
    name = uuid.uuid4().hex + ".bin"
    path = "/api/sftp/file/" + name
    content = bytes(range(256)) * 8192
    created = False
    try:
        status, body = request(base + path, "PUT", content, {"Content-Type": "application/octet-stream"})
        require(status == 200, f"SFTP upload returned HTTP {status}")
        created = True
        require(json.loads(body)["data"]["active"] == 0, "upload leaked SFTP lease")
        status, received = request(base + path)
        require(status == 200 and hashlib.sha256(received).digest() == hashlib.sha256(content).digest(),
                "SFTP roundtrip content mismatch")
        require(name in api(base, "sftp/files")["names"], "SFTP listing lost upload")
        record("sftp-real-stream-roundtrip")
        test_sftp_named_pools(base, name, content, record)
        for _ in range(5):
            api(base, "sftp/file/missing.bin", expected=404)
        require(api(base, "sftp/files")["active"] == 0, "SFTP failures exhausted pool")
        record("sftp-failure-invalidation-and-recovery")
        api(base, "sftp/file/missing.bin", expected=404)
        known_hosts = Path(environment["TEST_SFTP_KNOWN_HOSTS"])
        trusted = known_hosts.read_text()
        try:
            known_hosts.write_text("")
            require(request(base + "/api/sftp/files")[0] == 500, "untrusted SFTP host was accepted")
        finally:
            known_hosts.write_text(trusted)
        require(name in api(base, "sftp/files")["names"], "SFTP did not recover after trust restoration")
        record("sftp-host-key-enforcement")
        status, _ = request(base + path, "PUT", b"", {"Content-Type": "application/octet-stream"})
        require(status == 200 and request(base + path) == (200, b""), "empty SFTP file failed")
        api(base, "sftp/file/invalid.txt", expected=400)
        record("sftp-empty-file-and-validation")
    finally:
        if created:
            api(base, "sftp/file/" + name, "DELETE")
            require(name not in api(base, "sftp/files")["names"], "SFTP test file remains")
            record("sftp-file-cleanup")


def test_sftp_named_pools(base, filename, content, record):
    """验证原命名池、借还入口和完整通道作用域，不修改本次测试目录之外的资源。"""
    pool = "pool-" + uuid.uuid4().hex
    target = uuid.uuid4().hex + ".bin"
    path = "sftp/pools/" + pool
    renamed = False
    try:
        api(base, path, "POST")
        api(base, path, "POST")
        stats = api(base, path)
        require(stats["maxTotal"] == stats["maxIdle"] == 1 and stats["minIdle"] == 0, "named pool configuration ignored")
        original_directory = api(base, path + "/directory")["directory"]
        changed = api(base, path + "/directory", query={"change": "true"})["directory"]
        require(changed.endswith("/upload") and changed != original_directory, "channel callback did not change directory")
        require(api(base, path + "/directory")["directory"] == original_directory, "pool leaked remote working directory")
        api(base, path + "/rename", "POST", query={"source": filename, "target": target})
        renamed = True
        status, received = request(base + "/api/sftp/file/" + target)
        require(status == 200 and hashlib.sha256(received).digest() == hashlib.sha256(content).digest(),
                "legacy borrowed-channel rename changed content")
        api(base, path + "/rename", "POST", query={"source": target, "target": filename})
        renamed = False
        record("sftp-named-pool-legacy-channel-and-directory-reset")

        result = api(base, path + "/disconnect", "POST")
        require(not result["sessionConnected"] and result["active"] == 0, "disconnected return leaked SSH session")
        require(api(base, path + "/directory")["directory"] == original_directory, "pool did not recover disconnected channel")
        record("sftp-disconnect-closes-session-and-recovers")

        with ThreadPoolExecutor(max_workers=1) as executor:
            holding = executor.submit(api, base, path + "/hold", query={"delay": 800})
            deadline = time.monotonic() + 5
            while api(base, path)["active"] == 0:
                require(time.monotonic() < deadline, "named pool holder did not enter")
                time.sleep(0.01)
            api(base, path + "/directory", expected=429)
            api(base, path, "DELETE")
            require(pool not in api(base, "sftp/pools")["names"], "closed pool stayed registered")
            api(base, path, "POST")
            require(holding.result()["directory"] == original_directory, "closing pool broke its active borrower")
        require(api(base, path)["active"] == 0, "old borrower was returned to recreated pool")
        require(api(base, path + "/directory")["directory"] == original_directory, "recreated pool unusable")
        record("sftp-bounded-wait-and-inflight-pool-rebuild")
    finally:
        if renamed:
            api(base, path + "/rename", "POST", query={"source": target, "target": filename})
        if pool in api(base, "sftp/pools")["names"]:
            api(base, path, "DELETE")
        record("sftp-named-pool-cleanup")


def test_idempotent(base, peer, record):
    key = "idempotent-" + uuid.uuid4().hex

    def submit(target):
        status, body = request(target + "/api/idempotent/run?" + urlencode({"key": key, "delay": 100}), "POST")
        require(status in (200, 409), f"unexpected idempotent response {status}")
        require(json.loads(body)["code"] == (0 if status == 200 else 409), "incorrect idempotent business code")
        return status

    with ThreadPoolExecutor(max_workers=8) as pool:
        responses = list(pool.map(submit, [base, peer] * 4))
    require(responses.count(200) == 1, "duplicate work ran across application instances")
    require(api(peer, "idempotent/state", query={"key": key})["count"] == 1, "shared work count was not one")
    record("idempotent-cross-instance-single-execution")

    retry_key = "retry-" + uuid.uuid4().hex
    api(base, "idempotent/run", "POST", query={"key": retry_key, "fail": "true"}, expected=500)
    require(api(peer, "idempotent/run", "POST", query={"key": retry_key})["count"] == 1, "failed work prevented retry")
    api(base, "idempotent/run", "POST", query={"key": retry_key}, expected=409)
    record("idempotent-failure-retry")

    long_key = "long-" + uuid.uuid4().hex
    with ThreadPoolExecutor(max_workers=1) as pool:
        running = pool.submit(api, base, "idempotent/run", "POST", query={"key": long_key, "delay": 2500})
        deadline = time.monotonic() + 5
        while api(peer, "idempotent/state", query={"key": long_key})["active"] == 0:
            require(time.monotonic() < deadline, "long-running request did not start")
            time.sleep(0.02)
        time.sleep(1.3)
        api(peer, "idempotent/run", "POST", query={"key": long_key}, expected=409)
        require(running.result()["count"] == 1, "long-running request failed")
    time.sleep(1.2)
    require(api(peer, "idempotent/run", "POST", query={"key": long_key})["count"] == 2, "completed window did not expire")
    record("idempotent-processing-lock-and-success-window")
    api(base, "idempotent/run", "POST", query={"key": ""}, expected=400)
    record("idempotent-key-validation")
    api(base, "idempotent/run", "POST", query={"key": key + "-invalid-delay", "delay": -1}, expected=400)
    api(base, "idempotent/reserve", "POST", query={"key": ""}, expected=400)
    record("idempotent-reservation-and-delay-validation")

    reserved = key + "-reserved"
    require(api(base, "idempotent/reserve", "POST", query={"key": reserved})["reserved"], "reservation failed")
    api(peer, "idempotent/reserve", "POST", query={"key": reserved}, expected=409)
    api(peer, "idempotent/run", "POST", query={"key": reserved}, expected=409)
    require(api(peer, "idempotent/state", query={"key": reserved})["count"] == 0, "reservation executed business")
    time.sleep(1.15)
    require(api(peer, "idempotent/run", "POST", query={"key": reserved})["count"] == 1, "reservation never expired")
    api(base, "idempotent/reserve", "POST", query={"key": reserved}, expected=409)
    record("idempotent-standalone-scoped-coordination-and-expiry")


def test_rate_limiter(base, peer, record):
    def attempt(target, key, kind, **parameters):
        query = {"key": key, "type": kind, **parameters}
        status, body = request(target + "/api/rate/acquire?" + urlencode(query))
        require(status in (200, 429), f"{kind}: unexpected response {status}")
        require(json.loads(body)["code"] == (0 if status == 200 else 429), "incorrect rate business code")
        return status

    for kind in ("LOCAL", "REDISSON", "REDIS_LUA_FIXED_WINDOW", "REDIS_LUA_SLIDING_WINDOW"):
        key = "rate-" + uuid.uuid4().hex
        targets = [base] if kind == "LOCAL" else [base, peer]
        with ThreadPoolExecutor(max_workers=6) as executor:
            futures = [executor.submit(attempt, targets[index % len(targets)], key, kind) for index in range(6)]
            statuses = [future.result() for future in futures]
        require(statuses.count(200) == 3, f"{kind}: shared quota was not enforced")
    record("rate-local-native-and-window-quotas")
    for kind in ("REDIS_LUA_TOKEN_BUCKET", "REDIS_LUA_LEAKY_BUCKET"):
        key = "bucket-" + uuid.uuid4().hex
        statuses = [attempt((base, peer)[index % 2], key, kind) for index in range(5)]
        require(statuses.count(200) == 3, f"{kind}: bucket capacity incorrect")
        if kind.endswith("LEAKY_BUCKET"):
            retry = []
            for _ in range(5):
                time.sleep(0.3)
                retry.append(attempt(peer, key, kind))
            require(200 in retry, "leaky bucket lost drain progress on rejected requests")
        else:
            time.sleep(1.1)
            require(attempt(peer, key, kind) == 200, "token bucket did not refill")
    record("rate-token-refill-and-leaky-drain")
    key = "weighted-" + uuid.uuid4().hex
    require(attempt(base, key, "REDIS_LUA_FIXED_WINDOW", permits=2) == 200, "weighted acquisition failed")
    require(attempt(peer, key, "REDIS_LUA_FIXED_WINDOW", permits=2) == 429, "weighted quota exceeded")
    time.sleep(2.1)
    require(attempt(peer, key, "REDIS_LUA_FIXED_WINDOW", permits=2) == 200, "window did not expire")
    record("rate-weighted-permits-and-expiry")
    key = "guava-" + uuid.uuid4().hex
    require(attempt(base, key, "GUAVA") == 200, "Guava initial permit failed")
    require(attempt(base, key, "GUAVA") == 429, "Guava limiter did not throttle")
    key = "annotation-" + uuid.uuid4().hex
    api(base, "rate/annotated", query={"key": key})
    api(peer, "rate/annotated", query={"key": key})
    api(base, "rate/annotated", query={"key": key}, expected=429)
    api(base, "rate/acquire", query={"key": key, "permits": 0}, expected=400)
    record("rate-guava-annotation-and-validation")
    for kind in ("REDIS_LUA_FIXED_WINDOW", "REDIS_LUA_SLIDING_WINDOW",
                 "REDIS_LUA_TOKEN_BUCKET", "REDIS_LUA_LEAKY_BUCKET"):
        key = "mixed-backend-" + uuid.uuid4().hex
        policy = {"capacity": 30, "rate": 1, "max": 30, "window": 30}
        statuses = [attempt(base, key, kind, backend="REDIS_TEMPLATE", permits=2, **policy),
                    attempt(peer, key, kind, backend="REDISSON", permits=29, **policy),
                    attempt(peer, key, kind, backend="REDIS_TEMPLATE", permits=28, **policy),
                    attempt(base, key, kind, backend="REDISSON", permits=29, **policy)]
        require(statuses == [200, 429, 200, 429], f"{kind}: backend quotas diverged")
    record("rate-all-lua-backends-share-weighted-quota")
    key = "spring-annotation-" + uuid.uuid4().hex
    api(base, "rate/annotated-spring", query={"key": key})
    api(peer, "rate/annotated", query={"key": key})
    api(base, "rate/annotated-spring", query={"key": key}, expected=429)
    time.sleep(2.1)
    api(peer, "rate/annotated-spring", query={"key": key})
    record("rate-spring-annotation-and-window-expiry")
    for kind in ("REDIS_LUA_FIXED_WINDOW", "REDIS_LUA_SLIDING_WINDOW",
                 "REDIS_LUA_TOKEN_BUCKET", "REDIS_LUA_LEAKY_BUCKET"):
        key = "legacy-rate-" + uuid.uuid4().hex
        policy = {"legacy": "true", "window": 30, "max": 2, "capacity": 30}
        require(attempt(base, key, kind, backend="REDIS_TEMPLATE", **policy) == 200,
                "legacy Spring script invocation failed")
        require(attempt(peer, key, kind, backend="REDISSON", **policy) == 200,
                "legacy Redisson script invocation failed")
        if kind in ("REDIS_LUA_FIXED_WINDOW", "REDIS_LUA_SLIDING_WINDOW"):
            require(attempt(base, key, kind, backend="REDIS_TEMPLATE", **policy) == 429,
                    "legacy script did not share quota")
    record("rate-original-lua-arguments-and-strategy-classes")


def test_lock(base, peer, record):
    kinds = ("REENTRANT_LOCK", "SEMAPHORE", "REDISSON_LOCK", "REDISSON_FAIR_LOCK",
             "REDISSON_SPIN_LOCK", "REDISSON_WRITE_LOCK", "REDISSON_SEMAPHORE", "REDIS_TEMPLATE_SEMAPHORE")
    for kind in kinds:
        key = "lock-" + uuid.uuid4().hex
        permits = 2 if "SEMAPHORE" in kind else 1
        targets = [base] if kind in ("REENTRANT_LOCK", "SEMAPHORE") else [base, peer]
        with ThreadPoolExecutor(max_workers=8) as executor:
            results = [executor.submit(api, targets[index % len(targets)], "lock/run",
                                       query={"key": key, "type": kind, "permits": permits, "delay": 80}) for index in range(8)]
            active = [future.result()["active"] for future in results]
        require(max(active) <= permits, f"{kind}: concurrency limit exceeded")
    record("lock-local-and-cross-instance-mutual-exclusion")
    key = "rw-" + uuid.uuid4().hex
    with ThreadPoolExecutor(max_workers=6) as executor:
        results = [executor.submit(api, (base, peer)[index % 2], "lock/run",
                                   query={"key": key, "type": "REDISSON_READ_LOCK", "delay": 150}) for index in range(6)]
        require(max(future.result()["active"] for future in results) > 1, "read locks did not permit concurrent readers")
    record("lock-read-sharing")
    for holding, competing in (("REDISSON_READ_LOCK", "REDISSON_WRITE_LOCK"),
                               ("REDISSON_WRITE_LOCK", "REDISSON_READ_LOCK")):
        key = "rw-exclusive-" + uuid.uuid4().hex
        with ThreadPoolExecutor(max_workers=1) as executor:
            held = executor.submit(api, base, "lock/run", query={"key": key, "type": holding, "delay": 500})
            deadline = time.monotonic() + 5
            while api(peer, "lock/active", query={"key": key})["active"] == 0:
                require(time.monotonic() < deadline, "read/write holder did not enter")
                time.sleep(0.01)
            api(peer, "lock/run", query={"key": key, "type": competing, "wait": 0}, expected=423)
            require(held.result()["active"] == 1, "read/write exclusion failed")
    record("lock-read-write-shared-namespace")
    for kind in ("REDISSON_SEMAPHORE", "REDIS_TEMPLATE_SEMAPHORE"):
        key = "semaphore-" + uuid.uuid4().hex
        with ThreadPoolExecutor(max_workers=1) as executor:
            held = executor.submit(api, base, "lock/run", query={"key": key, "type": kind, "delay": 3500})
            deadline = time.monotonic() + 5
            while api(peer, "lock/active", query={"key": key})["active"] == 0:
                require(time.monotonic() < deadline, "semaphore holder did not enter")
                time.sleep(0.02)
            time.sleep(1.8)
            for _ in range(2):
                api(peer, "lock/run", query={"key": key, "type": kind, "wait": 0}, expected=423)
            require(held.result()["active"] == 1, "semaphore holder result invalid")
        require(api(peer, "lock/run", query={"key": key, "type": kind})["active"] == 1, "permit leaked")
        record(f"lock-{kind.lower()}-ownership-and-renewal")
    api(base, "lock/run", query={"key": key, "fail": "true"}, expected=500)
    require(api(peer, "lock/run", query={"key": key})["active"] == 1, "failed business invocation leaked lock")
    require(api(peer, "lock/annotated", query={"key": key})["active"] == 1, "annotation/SpEL invocation failed")
    api(base, "lock/run", query={"key": key, "permits": 0}, expected=400)
    record("lock-business-failure-annotation-and-validation")
    for kind in kinds:
        key = "factory-" + uuid.uuid4().hex
        with ThreadPoolExecutor(max_workers=6) as executor:
            targets = [base] if kind in ("REENTRANT_LOCK", "SEMAPHORE") else [base, peer]
            results = [executor.submit(api, targets[index % len(targets)], "lock/factory",
                                       query={"key": key, "type": kind}) for index in range(6)]
            require(all(future.result()["active"] == 1 for future in results), f"{kind}: original factory lost exclusion")
    record("lock-original-factory-all-backends")
    key = "dynamic-" + uuid.uuid4().hex
    with ThreadPoolExecutor(max_workers=8) as executor:
        results = [executor.submit(api, (base, peer)[index % 2], "lock/dynamic",
                                   query={"key": key, "permits": 2}) for index in range(8)]
        require(max(future.result()["active"] for future in results) <= 2, "dynamic full rule did not override disabled annotation")
    api(base, "lock/dynamic", query={"key": key, "permits": 3}, expected=400)
    api(base, "lock/dynamic", query={"key": key + "-invalid", "permits": 0}, expected=400)
    record("lock-dynamic-rule-backend-and-capacity-validation")
    key = "permits-" + uuid.uuid4().hex
    with ThreadPoolExecutor(max_workers=8) as executor:
        results = [executor.submit(api, base, "lock/permits", query={"key": key, "permits": 2}) for _ in range(8)]
        require(max(future.result()["active"] for future in results) <= 2, "dynamic permits did not apply")
    api(base, "lock/permits", query={"key": key, "permits": 0}, expected=400)
    record("lock-permits-expression")


def test_multi_redis(base, record):
    require(set(api(base, "redis/names")) == {"main", "secondary", "cluster"}, "named clients missing")
    key = "test-" + uuid.uuid4().hex
    data = {"text": "中文", "count": 1, "@class": "java.lang.Runtime"}
    api(base, "redis/value", "PUT", "main-value", {"client": "main", "key": key})
    api(base, "redis/value", "PUT", data, {"client": "secondary", "key": key})
    require(api(base, "redis/value", query={"client": "main", "key": key})["value"] == "main-value", "database isolation failed")
    require(api(base, "redis/value", query={"client": "secondary", "key": key})["value"] == data, "JSON roundtrip failed")
    record("redis-named-isolation-and-json")
    for index in range(12):
        cluster_key = key + "-" + str(index)
        api(base, "redis/value", "PUT", cluster_key, {"client": "cluster", "key": cluster_key})
        require(api(base, "redis/value", query={"client": "cluster", "key": cluster_key})["value"] == cluster_key,
                "cluster routing failed")
        api(base, "redis/value", "DELETE", query={"client": "cluster", "key": cluster_key})
    record("redis-cluster-routing")
    api(base, "redis/value", "PUT", "expires", {"client": "main", "key": key, "ttl": 1})
    time.sleep(1.2)
    require(not api(base, "redis/value", query={"client": "main", "key": key})["present"], "Redis TTL did not expire")
    api(base, "redis/value", query={"client": "unknown", "key": key}, expected=400)
    api(base, "redis/value", "DELETE", query={"client": "secondary", "key": key})
    require(not api(base, "redis/value", query={"client": "secondary", "key": key})["present"], "Redis deletion failed")
    record("redis-ttl-validation-and-cleanup")


def redis_ports(count):
    for _ in range(100):
        ports = random.SystemRandom().sample(range(20000, 40000), count)
        required = ports + [port + 10000 for port in ports[1:]]
        if len(set(required)) != len(required):
            continue
        sockets = []
        try:
            for port in required:
                listener = socket.socket()
                sockets.append(listener)
                listener.bind(("127.0.0.1", port))
            return ports
        except OSError:
            pass
        finally:
            for listener in sockets:
                listener.close()
    raise AssertionError("unable to reserve Redis test ports")


def start_redis(resources, environment, cluster):
    ports = redis_ports(4 if cluster else 1)
    name = "common-tool-test-" + uuid.uuid4().hex
    resources.append(name)
    commands = [f"redis-server --port {ports[0]} --protected-mode no --save '' --appendonly no --daemonize yes"]
    if cluster:
        for index, port in enumerate(ports[1:]):
            commands.append(
                f"redis-server --port {port} --protected-mode no --save '' --appendonly no --daemonize yes "
                f"--cluster-enabled yes --cluster-config-file /tmp/n{index}.conf "
                f"--cluster-node-timeout 5000 --cluster-announce-ip 127.0.0.1 "
                f"--cluster-announce-port {port} --cluster-announce-bus-port {port + 10000}")
        for port in ports[1:]:
            commands.append(f"until redis-cli -p {port} PING >/dev/null 2>&1; do sleep 0.1; done")
        commands.append("redis-cli --cluster create " + " ".join(f"127.0.0.1:{port}" for port in ports[1:])
                        + " --cluster-replicas 0 --cluster-yes")
    commands.append("exec tail -f /dev/null")
    command = ["docker", "run", "-d", "--name", name, "--label", "common-tool.test=true"]
    for port in ports:
        command.extend(["-p", f"127.0.0.1:{port}:{port}"])
    command.extend([os.environ.get("TEST_REDIS_IMAGE", "redis:8-alpine"), "sh", "-ec", "\n".join(commands)])
    subprocess.run(command, check=True, capture_output=True, timeout=120)
    deadline = time.monotonic() + 60
    while time.monotonic() < deadline:
        state = subprocess.run(["docker", "inspect", "--format", "{{.State.Running}}", name],
                               capture_output=True, timeout=10)
        if state.returncode != 0 or state.stdout.strip() != b"true":
            break
        probe = subprocess.run(["docker", "exec", name, "redis-cli", "-p", str(ports[0]), "PING"],
                               capture_output=True, timeout=10)
        ready = probe.returncode == 0 and b"PONG" in probe.stdout
        if ready and cluster:
            probe = subprocess.run(["docker", "exec", name, "redis-cli", "-p", str(ports[1]), "CLUSTER", "INFO"],
                                   capture_output=True, timeout=10)
            ready = probe.returncode == 0 and b"cluster_state:ok" in probe.stdout
        if ready:
            environment["TEST_REDIS_ADDRESS"] = f"redis://127.0.0.1:{ports[0]}"
            environment["TEST_REDIS_CLUSTER_ENABLED"] = "true" if cluster else "false"
            environment["TEST_REDIS_CLUSTER_NODES"] = ",".join(f"redis://127.0.0.1:{port}" for port in ports[1:])
            return
        time.sleep(0.3)
    logs = subprocess.run(["docker", "logs", "--tail", "30", name], capture_output=True, timeout=10)
    print(logs.stdout.decode(errors="replace") + logs.stderr.decode(errors="replace"), file=sys.stderr)
    raise AssertionError("dedicated Redis test service did not become ready")


def stop_services(resources):
    for name in list(resources):
        result = subprocess.run(["docker", "rm", "-fv", name], capture_output=True, timeout=20)
        require(result.returncode == 0 or b"No such container" in result.stderr, "test container cleanup failed")
        resources.remove(name)


def start_oss(resources, environment, temporary):
    """启动本次测试专用的 S3 服务，随机凭据仅通过子进程环境传递。"""
    image = environment.get("TEST_OSS_IMAGE")
    if not image:
        binary = environment.get("TEST_OSS_BINARY")
        if not binary:
            built = subprocess.run([sys.executable, str(ROOT / "scripts/build-oss-fixture.py")],
                                   capture_output=True, check=True, timeout=960)
            binary = built.stdout.decode().strip()
        return start_oss_binary(binary, environment, temporary)
    name = "common-tool-oss-test-" + uuid.uuid4().hex
    resources.append(name)
    access_key = "test" + uuid.uuid4().hex
    secret_key = uuid.uuid4().hex + uuid.uuid4().hex
    container_environment = environment.copy()
    rustfs = "rustfs" in image.lower()
    variables = {"RUSTFS_ACCESS_KEY": access_key, "RUSTFS_SECRET_KEY": secret_key,
                 "RUSTFS_ADDRESS": "0.0.0.0:9000", "RUSTFS_CONSOLE_ENABLE": "false",
                 "RUSTFS_VOLUMES": "/data"} if rustfs else {
                     "MINIO_ROOT_USER": access_key, "MINIO_ROOT_PASSWORD": secret_key, "MINIO_BROWSER": "off"}
    container_environment.update(variables)
    command = ["docker", "run", "-d", "--name", name, "--label", "common-tool.test=true",
               "-p", "127.0.0.1::9000"]
    for variable in variables:
        command.extend(["-e", variable])
    command.append(image)
    if not rustfs:
        command.extend(["server", "/data", "--address", ":9000"])
    subprocess.run(command, env=container_environment, check=True, capture_output=True, timeout=120)
    published = subprocess.run(["docker", "port", name, "9000/tcp"], capture_output=True, check=True, timeout=10)
    endpoint = "http://" + published.stdout.decode().strip()
    deadline = time.monotonic() + 90
    while time.monotonic() < deadline:
        running = subprocess.run(["docker", "inspect", "--format", "{{.State.Running}}", name],
                                 capture_output=True, check=True, timeout=10)
        if running.stdout.strip() != b"true":
            break
        try:
            status, _ = request(endpoint)
            if status in (200, 403):
                environment.update({"OSS_ENDPOINT": endpoint, "OSS_ACCESS_KEY": access_key,
                                    "OSS_ACCESS_SECRET": secret_key, "OSS_REGION": "us-east-1"})
                return
        except (URLError, TimeoutError, ConnectionError):
            pass
        time.sleep(0.3)
    # 服务启动日志可能包含凭据信息，不输出日志正文。
    raise AssertionError("dedicated S3 test service did not become ready")


def start_oss_binary(binary, environment, temporary):
    """以随机凭据与临时数据目录启动本地 MinIO，不安装常驻服务。"""
    require(Path(binary).is_file() and os.access(binary, os.X_OK), "TEST_OSS_BINARY is not an executable file")
    with socket.socket() as listener:
        listener.bind(("127.0.0.1", 0))
        port = listener.getsockname()[1]
    data = Path(temporary) / "oss-data"
    data.mkdir()
    access_key = "test" + uuid.uuid4().hex
    secret_key = uuid.uuid4().hex + uuid.uuid4().hex
    server_environment = environment.copy()
    server_environment.update(MINIO_ROOT_USER=access_key, MINIO_ROOT_PASSWORD=secret_key, MINIO_BROWSER="off")
    endpoint = f"http://127.0.0.1:{port}"
    with (Path(temporary) / "oss-server.log").open("w") as log:
        server = subprocess.Popen([binary, "server", str(data), "--address", f"127.0.0.1:{port}"],
                                  env=server_environment, stdout=log, stderr=subprocess.STDOUT)
    try:
        deadline = time.monotonic() + 90
        while time.monotonic() < deadline:
            require(server.poll() is None, "dedicated S3 binary exited before readiness")
            try:
                status, _ = request(endpoint + "/minio/health/ready")
                if status == 200:
                    environment.update(OSS_ENDPOINT=endpoint, OSS_ACCESS_KEY=access_key,
                                       OSS_ACCESS_SECRET=secret_key, OSS_REGION="us-east-1")
                    return server
            except (URLError, TimeoutError, ConnectionError):
                pass
            time.sleep(0.2)
        raise AssertionError("dedicated S3 binary did not become ready")
    except BaseException:
        stop_process(server)
        raise


def test_dict(base, record):
    sample = api(base, "dict/sample")
    require(sample["state"] == "1" and sample["stateText"] == "启用", "renamed dictionary property failed")
    require(sample["numeric"] == 1 and sample["numericText"] == "启用", "numeric dictionary failed")
    record("dict-code-and-text")
    api(base, "dict/status", "PUT", {"1": "已更新", "0": "停用"})
    require(api(base, "dict/sample")["stateText"] == "已更新", "dictionary replacement failed")
    api(base, "dict/source", "PUT", {"1": "完整刷新", "0": "停用"})
    require(api(base, "dict/sample")["stateText"] == "已更新", "source update bypassed explicit refresh")
    api(base, "dict/refresh", "POST")
    require(api(base, "dict/sample")["stateText"] == "完整刷新", "full dictionary refresh failed")
    record("dict-refresh")
    unknown = api(base, "dict/sample", query={"status": "unknown"})
    require(unknown["state"] == "unknown" and "stateText" not in unknown, "unknown code changed or invented text")
    record("dict-unknown-code")
    department = api(base, "dict/department", query={"code": "D1"})
    require(department == {"code": "D1", "codeText": "Engineering"}, "table field fallback failed")
    require(api(base, "dict/legacy", query={"value": "Engineering"}) == {"found": True, "text": "Engineering"},
            "original four-argument lookup failed")
    record("dict-table-field-and-legacy-lookup")
    injected = "x' OR '1'='1"
    require(api(base, "dict/department", query={"code": injected}) == {"code": injected}, "SQL input changed query semantics")
    require(api(base, "dict/legacy", query={"value": injected}) == {"found": False}, "legacy lookup did not bind parameters")
    api(base, "dict/status", "PUT", {"1": None}, expected=400)
    require(api(base, "dict/sample")["stateText"] == "完整刷新", "invalid update destroyed cache")
    record("dict-bound-parameters-and-invalid-update")


def test_desensitize(base, record):
    profile = api(base, "desensitize/profile")
    require(profile["phone"] == "138****8000" and profile["name"] == "张*", "built-in masking failed")
    require(profile["email"] == "a****@example.com", "email masking failed")
    require(profile["ordinary"] == "public", "unannotated property was changed")
    record("desensitize-field-isolation")
    require(profile["range"] == "A####" and profile["custom"] == "managed", "range or managed handler failed")
    record("desensitize-range-and-custom-handler")
    nested = api(base, "desensitize/list")
    require(len(nested) == 2 and all(item == profile for item in nested), "nested collection masking failed")
    record("desensitize-nested-output")


def test_i18n(base, record):
    require(api(base, "i18n/message", query={"name": "Lin"})["message"] == "你好，Lin", "default locale failed")
    headers = {"Accept-Language": "en-US,en;q=0.9,zh-CN;q=0.8"}
    require(api(base, "i18n/message", query={"name": "Lin"}, headers=headers)["message"] == "Hello, Lin",
            "weighted Accept-Language resolution failed")
    require(api(base, "i18n/message", query={"name": "Lin"}, headers={"Accept-Language": "zh_CN"})["message"]
            == "你好，Lin", "original underscore language header failed")
    record("i18n-locale-negotiation")
    api(base, "i18n/message", "PUT", query={"language": "en_US", "code": "dynamic", "text": "Updated {0}"})
    require(api(base, "i18n/message", query={"code": "dynamic", "name": "Lin"}, headers=headers)["message"] == "Updated Lin",
            "dynamic translation failed")
    api(base, "i18n/refresh", "POST")
    require(api(base, "i18n/message", query={"code": "dynamic", "name": "Lin"}, headers=headers)["message"] == "Updated Lin",
            "refresh erased memory messages")
    record("i18n-dynamic-refresh")
    api(base, "i18n/message", "DELETE", query={"language": "en-US", "code": "dynamic"})
    require(api(base, "i18n/message", query={"code": "dynamic"}, headers=headers)["message"] == "dynamic", "code fallback failed")
    api(base, "i18n/message", "PUT", query={"language": "en;bad", "code": "x", "text": "y"}, expected=400)
    record("i18n-removal-and-validation")
    require(api(base, "i18n/default", query={"code": "missing", "language": "en_US", "name": "Lin",
                                           "fallback": "Fallback {0}"})["message"] == "Fallback Lin",
            "original default-message overload failed")
    require(api(base, "i18n/default", query={"code": "hello", "language": "", "name": "Lin"},
                headers=headers)["message"] == "Hello, Lin", "blank language lost request locale")
    record("i18n-original-overloads-and-blank-locale")
    api(base, "i18n/message", "PUT", query={"language": "", "code": "root-test", "text": "Root value"})
    require(api(base, "i18n/message", query={"code": "root-test"}, headers=headers)["message"] == "Root value",
            "ROOT memory locale failed")
    api(base, "i18n/messages", "DELETE", query={"language": ""})
    require(api(base, "i18n/message", query={"code": "root-test"}, headers=headers)["message"] == "root-test",
            "language clear failed")
    api(base, "i18n/messages", "DELETE")
    require(api(base, "i18n/message", query={"code": "hello"}, headers=headers)["message"] == "hello",
            "explicit global clear failed")
    record("i18n-explicit-language-and-global-clear")


def test_excel(base, record):
    status, workbook = request(base + "/api/excel/export?count=11&rowsPerSheet=5")
    require(status == 200 and zipfile.is_zipfile(io.BytesIO(workbook)), "response is not an XLSX file")
    all_ids = []
    for index, expected in enumerate((5, 5, 1)):
        result = upload(base, "import", workbook, {"sheet": index}, prefix="excel")
        require(result["total"] == expected and result["failed"] == 0, "sheet boundary count incorrect")
        all_ids.extend(result["ids"])
    require(all_ids == list(range(1, 12)), "sheet splitting lost or reordered data")
    record("excel-multi-sheet-roundtrip")
    status, content = request(base + "/api/excel/export?count=11&rowsPerSheet=5&zip=true")
    require(status == 200, "ZIP export failed")
    with zipfile.ZipFile(io.BytesIO(content)) as archive:
        require(len(archive.namelist()) == 3, "incorrect workbook count in ZIP")
        ids = []
        for name in archive.namelist():
            ids.extend(upload(base, "import", archive.read(name), {}, prefix="excel")["ids"])
        require(ids == list(range(1, 12)), "ZIP partition data mismatch")
    record("excel-zip-roundtrip")
    status, empty = request(base + "/api/excel/export?count=0")
    require(status == 200, "empty export failed")
    require(upload(base, "import", empty, {}, prefix="excel")["total"] == 0, "empty workbook is invalid")
    record("excel-empty-workbook")
    api(base, "excel/export", query={"rowsPerSheet": 0}, expected=400)
    record("excel-invalid-limit")


def test_docs(base, record, environment):
    for path in ("/v3/api-docs", "/v3/api-docs.yaml", "/swagger-ui/index.html", "/doc.html"):
        status, _ = request(base + path)
        require(status == 401, "documentation is accessible without credentials")
    status, _ = request(base + "/v3/api-docs", headers={"Authorization": "Basic invalid"})
    require(status == 401, "malformed credentials were accepted")
    record("docs-access-protection")
    credentials = base64.b64encode(
        (environment["DOCS_USERNAME"] + ":" + environment["DOCS_PASSWORD"]).encode()).decode()
    headers = {"Authorization": "Basic " + credentials}
    status, body = request(base + "/v3/api-docs", headers=headers)
    require(status == 200, "authorized documentation request failed")
    document = json.loads(body)
    require(document["info"]["title"] == "Common Tool Docs", "OpenAPI title mismatch")
    require("/api/docs/ping" in document["paths"], "OpenAPI omitted example controller")
    require(document["components"]["securitySchemes"]["basicAuth"]["scheme"] == "basic",
            "original OpenAPI Basic scheme missing")
    require("x-openapi" in document, "Knife4j enhancement did not run against Springdoc 3")
    record("docs-openapi-generation")
    status, body = request(base + "/v3/api-docs/swagger-config", headers=headers)
    require(status == 200, "documentation group discovery failed")
    groups = json.loads(body).get("urls", [])
    group = next((item for item in groups if item.get("name") == "sample"), None)
    require(group is not None, "original grouped documentation is missing")
    group_url = urljoin(base, group["url"])
    require(urlparse(group_url).netloc == urlparse(base).netloc, "unexpected external group URL")
    status, body = request(group_url, headers=headers)
    require(status == 200 and b"knife4j-boot4-markdown-fixture" in body, "group Markdown content missing")
    require("/api/docs/ping" in json.loads(body)["paths"], "group omitted registered endpoint")
    record("docs-group-discovery-and-content")
    status, body = request(base + "/swagger-ui/index.html", headers=headers)
    require(status == 200 and b"swagger-ui" in body.lower(), "Swagger UI assets unavailable")
    record("docs-ui")
    status, body = request(base + "/doc.html", headers=headers)
    require(status == 200, "original Knife4j entry unavailable")
    assets = HtmlAssets()
    assets.feed(body.decode("utf-8"))
    require(len(assets.urls) >= 2, "Knife4j page is missing its actual assets")
    for source in assets.urls:
        url = urljoin(base + "/doc.html", source)
        require(urlparse(url).netloc == urlparse(base).netloc, "unexpected external documentation asset")
        require(request(url)[0] == 401, "Knife4j asset bypassed documentation authentication")
        status, content = request(url, headers=headers)
        require(status == 200 and len(content) > 0 and not content.lstrip().startswith(b"<!DOCTYPE"),
                "Knife4j asset did not load")
    record("docs-knife4j-entry-assets-and-enhancement")
    require(api(base, "docs/ping")["status"] == "UP", "documentation guard blocked business endpoint")
    record("docs-business-isolation")


def test_docs_modes(jar, module, temporary, environment, record):
    """通过独立进程验证 CORS 和生产保护，避免只在模拟过滤链中测试。"""
    modes = {
        "cors": ["--knife4j.cors=true", "--swagger.cors-allowed-origins=https://docs.example.test",
                 "--swagger.cors-allow-credentials=true"],
        "production": ["--knife4j.production=true", "--knife4j.cors=false"],
    }
    for mode, arguments in modes.items():
        process = None
        log_path = Path(temporary) / f"docs-{mode}.log"
        try:
            with log_path.open("w") as log:
                process = subprocess.Popen(
                    ["java", "-jar", str(jar), "--server.port=0", "--server.address=127.0.0.1",
                     "--spring.output.ansi.enabled=never", *arguments],
                    cwd=module, env=environment, stdout=log, stderr=subprocess.STDOUT)
            base = wait_for_application(process, log_path)
            if mode == "cors":
                headers = {"Origin": "https://docs.example.test", "Access-Control-Request-Method": "GET",
                           "Access-Control-Request-Headers": "authorization"}
                with OPENER.open(Request(base + "/v3/api-docs", method="OPTIONS", headers=headers), timeout=20) as response:
                    require(response.status == 200, "documentation CORS preflight failed")
                    require(response.headers.get("Access-Control-Allow-Origin") == "https://docs.example.test",
                            "documentation Origin allowlist failed")
                    require(response.headers.get("Access-Control-Allow-Credentials") == "true",
                            "credentialed CORS setting did not apply")
                require(request(base + "/v3/api-docs", headers={"Origin": "https://docs.example.test"})[0] == 401,
                        "CORS bypassed document authentication")
                headers["Origin"] = "https://untrusted.example.test"
                require(request(base + "/api/docs/ping", "OPTIONS", headers=headers)[0] == 403,
                        "CORS accepted an untrusted Origin")
                record("docs-real-cors-allowlist-and-authentication")
            else:
                for path in ("/doc.html", "/swagger-ui/index.html", "/v3/api-docs",
                             "/v3/api-docs.yaml", "/v3/api-docs/sample"):
                    require(request(base + path)[0] == 403, "production mode exposed documentation")
                require(api(base, "docs/ping")["status"] == "UP", "production mode blocked business endpoint")
                record("docs-real-production-protection")
        finally:
            stop_process(process)


def test_local_cache(base, record):
    key = "cache-" + uuid.uuid4().hex
    try:
        require(not api(base, "cache/entry", query={"key": key})["present"], "cache initially contains key")
        api(base, "cache/entry", "PUT", query={"key": key, "value": "中文 value"})
        require(api(base, "cache/entry", query={"key": key})["value"] == "中文 value", "cache value mismatch")
        api(base, "cache/entry", "DELETE", query={"key": key})
        require(not api(base, "cache/entry", query={"key": key})["present"], "cache delete failed")
        record("cache-crud")

        first = api(base, "cache/load", query={"key": key, "value": "first"})
        second = api(base, "cache/load", query={"key": key, "value": "second"})
        require(first["value"] == second["value"] == "first", "loader replaced an existing cached value")
        require(first["loadCount"] == second["loadCount"], "loader ran for a cache hit")
        record("cache-load-once")

        stats = api(base, "cache/stats")["demoCache"]
        require(stats["hitCount"] >= 2 and stats["missCount"] >= 2, "cache statistics were not recorded")
        require(stats["loadSuccessCount"] == 1, "incorrect successful load count")
        record("cache-real-statistics")
        typed = api(base, "cache/type-stats")["DemoCache"]
        require(typed["hitCount"] == stats["hitCount"] and typed["loadSuccessCount"] == 1,
                "class-name statistics differ from named statistics")
        record("cache-original-type-statistics")

        api(base, "cache/entry", "PUT", query={"key": "expiry", "value": "short-lived"})
        time.sleep(2.3)
        require(not api(base, "cache/entry", query={"key": "expiry"})["present"], "cache access expiry failed")
        record("cache-expiration")

        api(base, "cache/entry", query={"key": " "}, expected=400)
        record("cache-invalid-key")
    finally:
        api(base, "cache/all", "DELETE")
        require(api(base, "cache/stats")["demoCache"]["size"] == 0, "cache cleanup left entries")
        record("cache-cleanup")


def test_oss(base, bucket, record, environment):
    # The application is configured with one UUID-named bucket; no arbitrary bucket is accepted by its API.
    api(base, "oss/bucket", expected=404)
    created = False
    uploads = []
    primary_error = None
    try:
        api(base, "oss/bucket", "POST")
        created = True
        require(api(base, "oss/bucket")["bucketName"] == bucket, "bucket: wrong test resource")
        require(bucket in api(base, "oss/buckets"), "bucket: missing from list")
        api(base, "oss/bucket", "POST")
        record("bucket-create-list-idempotence")

        payloads = {
            "stream": b"boot4-stream\x00\xff" * 8192,
            "progress": bytes(range(256)) * 4096,
            "file": b"file-upload" * 15000,
        }
        for mode, content in payloads.items():
            key = f"objects/{mode}.bin"
            result = upload(base, "objects", content, {"objectName": key, "mode": mode})
            require(result["size"] == len(content) and result["eTag"], f"{mode}: invalid upload result")
            if mode != "stream":
                require(result["progress"] == 100, f"{mode}: progress did not complete")
            check_download(base, key, content)
            metadata = api(base, "oss/metadata", query={"objectName": key})
            require(metadata["size"] == len(content), f"{mode}: incorrect metadata length")
            record(f"{mode}-upload-download-content")

        unicode_key = "objects/中文 空格+测试.txt"
        upload(base, "objects", b"utf8-key", {"objectName": unicode_key})
        check_download(base, unicode_key, b"utf8-key")
        upload(base, "objects", b"", {"objectName": "objects/empty"})
        check_download(base, "objects/empty", b"")
        upload(base, "objects", b"nested", {"objectName": "objects/nested/file"})
        shallow = api(base, "oss/objects", query={"prefix": "objects/", "recursive": "false"})
        recursive = api(base, "oss/objects", query={"prefix": "objects/", "recursive": "true"})
        require("objects/nested/file" not in {item["key"] for item in shallow}, "non-recursive listing is incorrect")
        require("objects/nested/file" in {item["key"] for item in recursive}, "recursive listing is incomplete")
        record("empty-unicode-and-recursive-list")

        api(base, "oss/metadata", "PATCH", {"source": "api-test"},
            {"objectName": unicode_key, "contentType": "text/plain"})
        metadata = api(base, "oss/metadata", query={"objectName": unicode_key})
        require(metadata["metadata"].get("source") == "api-test", "metadata replacement failed")
        require(metadata["contentType"] == "text/plain", "content type replacement failed")
        check_download(base, unicode_key, b"utf8-key")
        record("metadata-copy-preserves-content")
        replacement = {"metadata": {"source": "standard-headers"}, "contentType": "text/plain",
                       "cacheControl": "private, max-age=77", "contentDisposition": 'attachment; filename="test.txt"',
                       "contentEncoding": "identity", "contentLanguage": "en", "expires": "2030-01-01T00:00:00Z"}
        api(base, "oss/metadata", "PUT", replacement, {"objectName": unicode_key})
        metadata = api(base, "oss/metadata", query={"objectName": unicode_key})
        for field in ("cacheControl", "contentDisposition", "contentEncoding", "contentLanguage", "contentType"):
            require(metadata[field] == replacement[field], f"metadata header {field} was not updated")
        api(base, "oss/metadata", "PATCH", {"source": "preserved"}, {"objectName": unicode_key})
        require(api(base, "oss/metadata", query={"objectName": unicode_key})["cacheControl"] == replacement["cacheControl"],
                "user metadata replacement removed standard headers")
        check_download(base, unicode_key, b"utf8-key")
        record("metadata-standard-headers-and-retention")

        automatic = bytes(range(256)) * (20 * 4096)
        for mode in ("stream", "progress", "file"):
            key = f"automatic/{mode}.bin"
            result = upload(base, "objects", automatic, {"objectName": key, "mode": mode})
            require(result["eTag"].strip('"').endswith("-3"), "large upload did not use three automatic parts")
            if mode != "stream":
                require(result["progress"] == 100, "automatic multipart progress did not complete")
            check_download(base, key, automatic)
        record("automatic-multipart-all-upload-overloads")

        signed_url = api(base, "oss/url", query={"objectName": unicode_key, "expiresSeconds": 60})["url"]
        # Never print or persist the signed URL. Compare against the configured endpoint before requesting it.
        signed = urlparse(signed_url)
        endpoint = urlparse(environment.get("OSS_ENDPOINT", "http://127.0.0.1:19000"))
        require(signed.scheme == endpoint.scheme and signed.netloc == endpoint.netloc,
                "presigned URL endpoint mismatch")
        require(parse_qs(signed.query).get("X-Amz-Expires") == ["60"], "presigned duration mismatch")
        status, content = request(signed_url)
        require(status == 200 and content == b"utf8-key", "presigned download failed")
        record("sigv4-presigned-download")

        report = api(base, "oss/download-progress", query={"objectName": "objects/progress.bin"})
        require(report["progress"] == 100, "download progress did not complete")
        require(report["sha256"] == hashlib.sha256(payloads["progress"]).hexdigest(),
                "progress download hash mismatch")
        record("progress-download")

        key = "multipart/complete.bin"
        upload_id = api(base, "oss/multipart", "POST", query={"objectName": key})["uploadId"]
        uploads.append((key, upload_id))
        first = b"a" * (5 * 1024 * 1024)
        second = b"last-part" * 100
        part1 = upload(base, "multipart/parts", first,
                       {"objectName": key, "uploadId": upload_id, "partNumber": 1}, "PUT")
        saved = api(base, "oss/multipart/parts", query={"objectName": key, "uploadId": upload_id})
        require(len(saved) == 1 and saved[0]["partNumber"] == 1, "resume listing failed")
        part2 = upload(base, "multipart/parts", second,
                       {"objectName": key, "uploadId": upload_id, "partNumber": 2}, "PUT")
        api(base, "oss/multipart/complete", "POST",
            {"objectName": key, "uploadId": upload_id, "parts": [part2, part1]})
        uploads.remove((key, upload_id))
        check_download(base, key, first + second)
        record("multipart-resume-sort-complete")

        key = "multipart/abort.bin"
        upload_id = api(base, "oss/multipart", "POST", query={"objectName": key})["uploadId"]
        uploads.append((key, upload_id))
        upload(base, "multipart/parts", b"discard",
               {"objectName": key, "uploadId": upload_id, "partNumber": 1}, "PUT")
        api(base, "oss/multipart", "DELETE", query={"objectName": key, "uploadId": upload_id})
        uploads.remove((key, upload_id))
        api(base, "oss/multipart/parts", query={"objectName": key, "uploadId": upload_id}, expected=404)
        api(base, "oss/metadata", query={"objectName": key}, expected=404)
        record("multipart-abort")

        api(base, "oss/url", query={"objectName": "objects/stream.bin", "expiresSeconds": 0}, expected=400)
        api(base, "oss/multipart/complete", "POST",
            {"objectName": "bad", "uploadId": "invalid", "parts": []}, expected=400)
        api(base, "oss/metadata", query={"objectName": "missing-object"}, expected=404)
        record("invalid-parameters-and-not-found")

        api(base, "oss/objects", "DELETE", query={"objectName": "objects/stream.bin"})
        api(base, "oss/metadata", query={"objectName": "objects/stream.bin"}, expected=404)
        keys = [item["key"] for item in api(base, "oss/objects")]
        api(base, "oss/objects/delete-batch", "POST", keys)
        require(api(base, "oss/objects") == [], "batch delete left objects behind")
        api(base, "oss/objects/delete-batch", "POST", [])
        record("single-and-batch-delete")
    except BaseException as error:
        primary_error = error
        raise
    finally:
        cleanup_errors = []
        if created:
            for key, upload_id in uploads:
                try:
                    api(base, "oss/multipart", "DELETE", query={"objectName": key, "uploadId": upload_id})
                except Exception:
                    cleanup_errors.append("multipart")
            try:
                remaining = [item["key"] for item in api(base, "oss/objects")]
                api(base, "oss/objects/delete-batch", "POST", remaining)
                api(base, "oss/bucket", "DELETE")
                api(base, "oss/bucket", expected=404)
            except Exception:
                cleanup_errors.append("bucket")
            if not cleanup_errors:
                record("test-resource-cleanup")
        if cleanup_errors:
            print(f"Cleanup failed for test bucket {bucket}; manual cleanup required.", file=sys.stderr)
            if primary_error is None:
                raise AssertionError("test resource cleanup failed")


def stop_process(process):
    if process is None or process.poll() is not None:
        return
    process.terminate()
    try:
        process.wait(timeout=15)
    except subprocess.TimeoutExpired:
        process.kill()
        process.wait(timeout=5)


def wait_for_application(process, log_path):
    deadline = time.monotonic() + 90
    while time.monotonic() < deadline:
        require(process.poll() is None, "example exited before startup; check service configuration")
        text = log_path.read_text(errors="replace")
        match = re.search(r"Tomcat started on port (\d+)", text)
        if match:
            base = "http://127.0.0.1:" + match.group(1)
            try:
                status, body = request(base + "/actuator/health")
                if status == 200 and json.loads(body)["status"] == "UP":
                    return base
            except (URLError, TimeoutError):
                pass
        time.sleep(0.2)
    raise AssertionError("example did not become healthy within 90 seconds")


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("starter", choices=MODULES)
    parser.add_argument("--skip-build", action="store_true", help="use the already-verified current JAR")
    parser.add_argument("--local-oss", action="store_true", help="create a dedicated local S3 service with random credentials")
    args = parser.parse_args()
    module = ROOT / "examples-starter" / MODULES[args.starter]
    report_path = module / "target" / "api-test-report.json"
    report = {"starter": args.starter, "passed": False, "checks": []}
    process = None
    peer = None
    resources = []
    started = time.monotonic()

    def record(name):
        report["checks"].append(name)
        print("PASS " + name, flush=True)

    def interrupted(signum, frame):
        raise KeyboardInterrupt()

    signal.signal(signal.SIGTERM, interrupted)
    try:
        require(not args.local_oss or args.starter == "oss", "--local-oss is only valid for oss")
        if args.starter == "oss" and not args.local_oss:
            require(os.environ.get("OSS_ACCESS_KEY") and os.environ.get("OSS_ACCESS_SECRET"),
                    "OSS_ACCESS_KEY and OSS_ACCESS_SECRET environment variables are required")
        subprocess.run([sys.executable, str(ROOT / "scripts/check-coordinates.py")], cwd=ROOT, check=True)
        if not args.skip_build:
            subprocess.run(["mvn", "--batch-mode", "--no-transfer-progress", "clean", "verify"],
                           cwd=ROOT, check=True)
        subprocess.run([sys.executable, str(ROOT / "scripts/check-coordinates.py"), "--built-jars"],
                       cwd=ROOT, check=True)
        version = ET.parse(ROOT / "pom.xml").findtext("{http://maven.apache.org/POM/4.0.0}version")
        jar = module / "target" / f"{MODULES[args.starter]}-{version}.jar"
        require(jar.is_file(), "example JAR is missing; run without --skip-build")
        environment = os.environ.copy()
        bucket = "common-tool-it-" + uuid.uuid4().hex
        environment["OSS_BUCKET"] = bucket
        if args.starter == "docs":
            environment["DOCS_USERNAME"] = "test-" + uuid.uuid4().hex
            environment["DOCS_PASSWORD"] = uuid.uuid4().hex
        if args.starter in ("multi-redis", "lock", "rate-limiter", "idempotent"):
            start_redis(resources, environment, cluster=args.starter == "multi-redis")
            record("redis-test-service-ready")
        with tempfile.TemporaryDirectory(prefix="common-tool-api-") as temporary:
            if args.local_oss:
                peer = start_oss(resources, environment, temporary)
                record("oss-test-service-ready")
            if args.starter == "sftp":
                peer = start_sftp(jar, environment, temporary)
                record("sftp-test-service-ready")
            log_path = Path(temporary) / "application.log"
            with log_path.open("w") as log:
                process = subprocess.Popen(
                    ["java", "-jar", str(jar), "--server.port=0", "--server.address=127.0.0.1",
                     "--spring.output.ansi.enabled=never"],
                    cwd=module, env=environment, stdout=log, stderr=subprocess.STDOUT,
                )
                try:
                    base = wait_for_application(process, log_path)
                    record("application-health")
                    peer_base = None
                    if args.starter in ("lock", "rate-limiter", "idempotent"):
                        peer_path = Path(temporary) / "peer.log"
                        with peer_path.open("w") as peer_log:
                            peer = subprocess.Popen(
                                ["java", "-jar", str(jar), "--server.port=0", "--server.address=127.0.0.1",
                                 "--spring.output.ansi.enabled=never"],
                                cwd=module, env=environment, stdout=peer_log, stderr=subprocess.STDOUT)
                        peer_base = wait_for_application(peer, peer_path)
                        record("peer-application-health")
                    if args.starter == "common":
                        test_common(base, record)
                    elif args.starter == "ip2region":
                        test_ip2region(base, record)
                    elif args.starter == "sensitive-word":
                        test_sensitive_word(base, record)
                    elif args.starter == "disruptor":
                        test_disruptor(base, record)
                    elif args.starter == "sftp":
                        test_sftp(base, record, environment)
                    elif args.starter == "script":
                        test_script(base, record)
                    elif args.starter == "dynamic-threadpool":
                        test_threadpool(base, record)
                    elif args.starter == "local-cache":
                        test_local_cache(base, record)
                    elif args.starter == "docs":
                        test_docs(base, record, environment)
                        test_docs_modes(jar, module, temporary, environment, record)
                    elif args.starter == "excel":
                        test_excel(base, record)
                    elif args.starter == "i18n":
                        test_i18n(base, record)
                    elif args.starter == "desensitize":
                        test_desensitize(base, record)
                    elif args.starter == "dict":
                        test_dict(base, record)
                    elif args.starter == "multi-redis":
                        test_multi_redis(base, record)
                    elif args.starter == "lock":
                        test_lock(base, peer_base, record)
                    elif args.starter == "rate-limiter":
                        test_rate_limiter(base, peer_base, record)
                    elif args.starter == "idempotent":
                        test_idempotent(base, peer_base, record)
                    else:
                        test_oss(base, bucket, record, environment)
                finally:
                    stop_process(peer)
                    stop_process(process)
        record("application-stopped")
        if resources:
            stop_services(resources)
            record("test-service-cleanup")
        elif args.local_oss:
            record("oss-test-service-cleanup")
        report["passed"] = True
        print(f"PASS {args.starter}: {len(report['checks'])} checks", flush=True)
        return 0
    except (Exception, KeyboardInterrupt) as error:
        # Do not print arbitrary exception strings: they can contain signed URLs or credentials.
        message = str(error) if isinstance(error, AssertionError) else type(error).__name__
        report["error"] = message
        print("FAIL " + message, file=sys.stderr)
        return 1
    finally:
        stop_process(peer)
        stop_process(process)
        if resources:
            try:
                stop_services(resources)
            except Exception:
                report["passed"] = False
                report["cleanupFailed"] = True
                print("FAIL dedicated Redis test service cleanup", file=sys.stderr)
        report["durationSeconds"] = round(time.monotonic() - started, 2)
        report_path.parent.mkdir(parents=True, exist_ok=True)
        report_path.write_text(json.dumps(report, indent=2) + "\n")


if __name__ == "__main__":
    sys.exit(main())
