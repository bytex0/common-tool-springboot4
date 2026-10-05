#!/usr/bin/env python3
"""从固定官方源码构建本地 MinIO 测试服务；标准输出只返回可执行文件路径。"""

import hashlib
import os
from pathlib import Path
import platform
import shutil
import subprocess
import sys
import tarfile
import tempfile
from urllib.request import urlopen


VERSION = "RELEASE.2025-09-07T16-13-09Z"
ARCHIVE_SHA256 = "c9598dcce3440977e79f787f2ba0e7e4d92c8d556bd51e7cef3785bafd6635f3"
SOURCE_URL = f"https://github.com/minio/minio/archive/refs/tags/{VERSION}.tar.gz"


def build():
    """校验归档并构建，仅在用户缓存目录保存生成制品，不修改项目或安装全局服务。"""
    root = Path(os.environ.get("XDG_CACHE_HOME") or Path.home() / ".cache")
    cache = root / "common-tool-springboot4" / "minio" / VERSION / f"{platform.system()}-{platform.machine()}"
    binary = cache / ("minio.exe" if os.name == "nt" else "minio")
    if binary.is_file() and os.access(binary, os.X_OK):
        return binary
    if shutil.which("go") is None:
        raise RuntimeError("本地 OSS 测试需要 Go，或通过 TEST_OSS_BINARY/TEST_OSS_IMAGE 提供已有服务")
    cache.mkdir(parents=True, exist_ok=True)
    with tempfile.TemporaryDirectory(prefix="build-", dir=cache) as temporary:
        work = Path(temporary)
        archive = work / "source.tar.gz"
        digest = hashlib.sha256()
        with urlopen(SOURCE_URL, timeout=60) as response, archive.open("wb") as output:
            while chunk := response.read(1024 * 1024):
                digest.update(chunk)
                output.write(chunk)
        if digest.hexdigest() != ARCHIVE_SHA256:
            raise RuntimeError("MinIO 官方源码归档摘要不匹配，停止构建")
        with tarfile.open(archive) as source:
            source.extractall(work, filter="data")
        source_root = work / f"minio-{VERSION}"
        built = work / binary.name
        subprocess.run(["go", "build", "-trimpath", "-o", str(built), "."], cwd=source_root,
                       stdout=sys.stderr, check=True, timeout=900)
        os.replace(built, binary)
    return binary


if __name__ == "__main__":
    try:
        print(build())
    except (OSError, RuntimeError, subprocess.SubprocessError) as error:
        print(f"OSS 测试服务构建失败: {error}", file=sys.stderr)
        sys.exit(1)
