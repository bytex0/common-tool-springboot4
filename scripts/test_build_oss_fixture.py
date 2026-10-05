"""验证测试服务构建器的缓存、依赖检查和源码完整性边界，不依赖网络或 Go。"""

import importlib.util
import io
import os
from pathlib import Path
import platform
import tempfile
import unittest
from unittest.mock import patch


SPEC = importlib.util.spec_from_file_location("oss_fixture_builder", Path(__file__).with_name("build-oss-fixture.py"))
BUILDER = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(BUILDER)


class FixtureBuilderTest(unittest.TestCase):
    """构建缓存和失败分支不触发未经验证的代码执行。"""

    def test_cached_binary_does_not_require_network_or_compiler(self):
        """已有当前平台制品时直接复用，不重复下载或检查 Go。"""
        with tempfile.TemporaryDirectory() as root, patch.dict(os.environ, {"XDG_CACHE_HOME": root}):
            cache = (Path(root) / "common-tool-springboot4" / "minio" / BUILDER.VERSION
                     / f"{platform.system()}-{platform.machine()}")
            cache.mkdir(parents=True)
            binary = cache / ("minio.exe" if os.name == "nt" else "minio")
            binary.write_bytes(b"cached-fixture")
            binary.chmod(0o700)
            with patch.object(BUILDER, "urlopen") as download, patch.object(BUILDER.shutil, "which") as compiler:
                self.assertEqual(BUILDER.build(), binary)
                download.assert_not_called()
                compiler.assert_not_called()

    def test_missing_compiler_fails_before_download(self):
        """缺失编译器时提示已有制品入口，不浪费下载或创建半成品。"""
        with tempfile.TemporaryDirectory() as root, patch.dict(os.environ, {"XDG_CACHE_HOME": root}):
            with patch.object(BUILDER.shutil, "which", return_value=None), patch.object(BUILDER, "urlopen") as download:
                with self.assertRaisesRegex(RuntimeError, "TEST_OSS_BINARY"):
                    BUILDER.build()
                download.assert_not_called()

    def test_bad_archive_digest_is_never_executed(self):
        """源码摘要错误时禁止解压和构建，并清理下载临时目录。"""
        with tempfile.TemporaryDirectory() as root, patch.dict(os.environ, {"XDG_CACHE_HOME": root}):
            with patch.object(BUILDER.shutil, "which", return_value="/test/go"), \
                    patch.object(BUILDER, "urlopen", return_value=io.BytesIO(b"unexpected archive")), \
                    patch.object(BUILDER.subprocess, "run") as compile_source:
                with self.assertRaisesRegex(RuntimeError, "摘要不匹配"):
                    BUILDER.build()
                compile_source.assert_not_called()
                self.assertEqual(list(Path(root).rglob("source.tar.gz")), [])


if __name__ == "__main__":
    unittest.main()
