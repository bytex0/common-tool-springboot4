"""验证 Ivy 测试仓库与配置内容，不启动 JVM，不访问网络。"""

import importlib.util
from pathlib import Path
import tempfile
import unittest
import zipfile
import xml.etree.ElementTree as ET


SPEC = importlib.util.spec_from_file_location("starter_automation", Path(__file__).with_name("test-starter.py"))
AUTOMATION = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(AUTOMATION)


class ScriptFixtureTest(unittest.TestCase):
    """确保依赖解析只允许访问当前临时测试仓库。"""

    def test_fixture_contains_real_jar_and_local_only_resolvers(self):
        """断言 JAR 内容、Maven 坐标及解析器地址，不以文件存在代替内容校验。"""
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            config, payload = AUTOMATION.create_ivy_fixture(root)
            artifact = root / "ivy-repository/io/github/bytex0/fixture/script-payload/1.0"
            with zipfile.ZipFile(artifact / "script-payload-1.0.jar") as archive:
                self.assertEqual(archive.read("bytex0-fixture/value.txt").decode(), payload)
            namespace = {"m": "http://maven.apache.org/POM/4.0.0"}
            pom = ET.parse(artifact / "script-payload-1.0.pom")
            self.assertEqual(pom.findtext("m:groupId", namespaces=namespace), "io.github.bytex0.fixture")
            settings = ET.parse(config)
            resolvers = settings.findall("./resolvers/chain/ibiblio")
            self.assertEqual(len(resolvers), 2)
            for resolver in resolvers:
                self.assertEqual(resolver.get("root"), (root / "ivy-repository").as_uri() + "/")
                self.assertEqual(resolver.get("m2compatible"), "true")


if __name__ == "__main__":
    unittest.main()
