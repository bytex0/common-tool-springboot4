"""坐标检查器的独立反应堆回归，不依赖 Maven、本地仓库或中间件。"""

import importlib.util
from pathlib import Path
import tempfile
import unittest
import xml.etree.ElementTree as ET
import zipfile


SPEC = importlib.util.spec_from_file_location("coordinates", Path(__file__).with_name("check-coordinates.py"))
COORDINATES = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(COORDINATES)
URI = "http://maven.apache.org/POM/4.0.0"
OLD = "demo-spring-boot-starter"
NEW = "demo-spring-boot4-starter"


def pom(path, artifact, modules=(), dependencies=(), managed=()):
    """用结构化 XML 创建测试 POM，不复用被测检查器的推导逻辑。"""
    project = ET.Element(f"{{{URI}}}project")

    def node(parent, name, value=None):
        child = ET.SubElement(parent, f"{{{URI}}}{name}")
        child.text = value
        return child

    node(project, "groupId", "io.github.bytex0")
    node(project, "artifactId", artifact)
    node(project, "version", "4.0.0-SNAPSHOT")
    if modules:
        container = node(project, "modules")
        for module in modules:
            node(container, "module", module)
    for values, parent in ((dependencies, project), (managed, node(project, "dependencyManagement"))):
        container = node(parent, "dependencies")
        for group, name in values:
            dependency = node(container, "dependency")
            node(dependency, "groupId", group)
            node(dependency, "artifactId", name)
    path.parent.mkdir(parents=True, exist_ok=True)
    ET.ElementTree(project).write(path, encoding="utf-8", xml_declaration=True)


class CoordinateTest(unittest.TestCase):
    """验证只约束本仓库坐标，且覆盖依赖、BOM 和打包边界。"""

    def setUp(self):
        """创建与实际目录保留策略一致的最小反应堆。"""
        self.temporary = tempfile.TemporaryDirectory(prefix="coordinate-test-")
        self.addCleanup(self.temporary.cleanup)
        self.root = Path(self.temporary.name)
        pom(self.root / "pom.xml", "common-tool-springboot4",
            modules=(OLD, "common-tool-springboot4-bom", "examples-starter"))
        pom(self.root / OLD / "pom.xml", NEW)
        pom(self.root / "common-tool-springboot4-bom/pom.xml", "common-tool-springboot4-bom",
            managed=(("io.github.bytex0", NEW),))
        pom(self.root / "examples-starter/pom.xml", "examples-starter", modules=("demo-example",))
        self.example = self.root / "examples-starter/demo-example/pom.xml"
        pom(self.example, "demo-example", dependencies=(("io.github.bytex0", NEW),))

    def test_valid_coordinates_keep_historical_directories_and_vendor_ids(self):
        """历史目录及第三方同名旧 artifactId 不应被误报。"""
        pom(self.example, "demo-example", dependencies=(
            ("io.github.bytex0", NEW), ("example.vendor", OLD),
            ("org.springframework.boot", "spring-boot-starter-webmvc")))
        self.assertEqual(COORDINATES.check(self.root), ([], 1))

    def test_rejects_old_project_artifact(self):
        """本项目库仍使用旧坐标时失败。"""
        pom(self.root / OLD / "pom.xml", OLD)
        self.assertTrue(COORDINATES.check(self.root)[0])

    def test_rejects_old_example_dependency(self):
        """示例依赖不能通过本地旧 JAR 掩盖遗漏。"""
        pom(self.example, "demo-example", dependencies=(("io.github.bytex0", OLD),))
        self.assertTrue(COORDINATES.check(self.root)[0])

    def test_rejects_missing_bom_entry(self):
        """BOM 必须覆盖全部已加入反应堆的库。"""
        pom(self.root / "common-tool-springboot4-bom/pom.xml", "common-tool-springboot4-bom")
        self.assertTrue(COORDINATES.check(self.root)[0])

    def test_verifies_plain_jar_and_rejects_boot_repackage(self):
        """要求新文件名、普通库打包，并拒绝遗留旧文件。"""
        self.assertTrue(COORDINATES.check(self.root, True)[0])
        target = self.root / OLD / "target"
        target.mkdir()
        jar = target / f"{NEW}-4.0.0-SNAPSHOT.jar"
        with zipfile.ZipFile(jar, "w") as archive:
            archive.writestr("META-INF/MANIFEST.MF", "Manifest-Version: 1.0\n")
        self.assertEqual(COORDINATES.check(self.root, True)[0], [])
        with zipfile.ZipFile(jar, "a") as archive:
            archive.writestr("BOOT-INF/classes/Example.class", b"fixture")
        self.assertTrue(COORDINATES.check(self.root, True)[0])

    def test_rejects_stale_artifact_filename(self):
        """新制品存在也不能忽略同目录中的旧坐标构建残留。"""
        target = self.root / OLD / "target"
        target.mkdir()
        with zipfile.ZipFile(target / f"{NEW}-4.0.0-SNAPSHOT.jar", "w") as archive:
            archive.writestr("META-INF/MANIFEST.MF", "Manifest-Version: 1.0\n")
        (target / f"{OLD}-4.0.0-SNAPSHOT.jar").touch()
        self.assertTrue(COORDINATES.check(self.root, True)[0])


if __name__ == "__main__":
    unittest.main()
