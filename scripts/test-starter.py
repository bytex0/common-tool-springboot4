#!/usr/bin/env python3
"""Build an example, exercise its real HTTP API, and clean up test resources."""

import argparse
import base64
import hashlib
import io
import json
import os
from pathlib import Path
import re
import signal
import subprocess
import sys
import tempfile
import time
from urllib.error import HTTPError, URLError
from urllib.parse import parse_qs, urlencode, urlparse
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
}
OPENER = build_opener(ProxyHandler({}))


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


def test_i18n(base, record):
    require(api(base, "i18n/message", query={"name": "Lin"})["message"] == "你好，Lin", "default locale failed")
    headers = {"Accept-Language": "en-US,en;q=0.9,zh-CN;q=0.8"}
    require(api(base, "i18n/message", query={"name": "Lin"}, headers=headers)["message"] == "Hello, Lin",
            "weighted Accept-Language resolution failed")
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
    for path in ("/v3/api-docs", "/v3/api-docs.yaml", "/swagger-ui/index.html"):
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
    record("docs-openapi-generation")
    status, body = request(base + "/swagger-ui/index.html", headers=headers)
    require(status == 200 and b"swagger-ui" in body.lower(), "Swagger UI assets unavailable")
    record("docs-ui")
    require(api(base, "docs/ping")["status"] == "UP", "documentation guard blocked business endpoint")
    record("docs-business-isolation")


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


def test_oss(base, bucket, record):
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

        signed_url = api(base, "oss/url", query={"objectName": unicode_key, "expiresSeconds": 60})["url"]
        # Never print or persist the signed URL. Compare against the configured endpoint before requesting it.
        signed = urlparse(signed_url)
        endpoint = urlparse(os.environ.get("OSS_ENDPOINT", "http://127.0.0.1:19000"))
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
    args = parser.parse_args()
    module = ROOT / "examples-starter" / MODULES[args.starter]
    report_path = module / "target" / "api-test-report.json"
    report = {"starter": args.starter, "passed": False, "checks": []}
    process = None
    started = time.monotonic()

    def record(name):
        report["checks"].append(name)
        print("PASS " + name, flush=True)

    def interrupted(signum, frame):
        raise KeyboardInterrupt()

    signal.signal(signal.SIGTERM, interrupted)
    try:
        if args.starter == "oss":
            require(os.environ.get("OSS_ACCESS_KEY") and os.environ.get("OSS_ACCESS_SECRET"),
                    "OSS_ACCESS_KEY and OSS_ACCESS_SECRET environment variables are required")
        if not args.skip_build:
            subprocess.run(["mvn", "--batch-mode", "--no-transfer-progress", "clean", "verify"],
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
        with tempfile.TemporaryDirectory(prefix="common-tool-api-") as temporary:
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
                    if args.starter == "common":
                        test_common(base, record)
                    elif args.starter == "local-cache":
                        test_local_cache(base, record)
                    elif args.starter == "docs":
                        test_docs(base, record, environment)
                    elif args.starter == "excel":
                        test_excel(base, record)
                    elif args.starter == "i18n":
                        test_i18n(base, record)
                    else:
                        test_oss(base, bucket, record)
                finally:
                    stop_process(process)
        record("application-stopped")
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
        stop_process(process)
        report["durationSeconds"] = round(time.monotonic() - started, 2)
        report_path.parent.mkdir(parents=True, exist_ok=True)
        report_path.write_text(json.dumps(report, indent=2) + "\n")


if __name__ == "__main__":
    sys.exit(main())
