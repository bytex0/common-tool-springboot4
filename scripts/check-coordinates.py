#!/usr/bin/env python3
"""检查本仓库 Starter 坐标、BOM、示例依赖及可选的构建制品。"""

import argparse
from pathlib import Path
import sys
import xml.etree.ElementTree as ET
import zipfile


ROOT = Path(__file__).resolve().parents[1]
GROUP = "io.github.bytex0"
OLD_SUFFIX = "-spring-boot-starter"
NEW_SUFFIX = "-spring-boot4-starter"
NS = {"m": "http://maven.apache.org/POM/4.0.0"}


def text(element, path, default=""):
    """读取并规范化 Maven XML 文本，不依赖标签排版。"""
    return (element.findtext(path, default, NS) or default).strip()


def projects(root):
    """按真实 modules 递归读取反应堆，仅允许仓库内的 POM。"""
    pending = [root / "pom.xml"]
    found = {}
    while pending:
        path = pending.pop().resolve()
        if not path.is_relative_to(root):
            raise ValueError("module 指向仓库外部")
        if path in found:
            raise ValueError(f"重复或循环 module: {path.relative_to(root)}")
        project = ET.parse(path).getroot()
        found[path] = project
        for module in project.findall("m:modules/m:module", NS):
            pending.append(path.parent / module.text.strip() / "pom.xml")
    return found


def check(root, built_jars=False):
    """返回坐标问题列表和 Starter 数量；不会修改工程或第三方坐标。"""
    root = root.resolve()
    reactor = projects(root)
    root_project = reactor[root / "pom.xml"]
    version = text(root_project, "m:version")
    libraries = {}
    issues = []
    for path, project in reactor.items():
        group = text(project, "m:groupId", text(project, "m:parent/m:groupId"))
        artifact = text(project, "m:artifactId")
        directory = path.parent.name
        if path.parent.parent == root and directory.endswith((OLD_SUFFIX, NEW_SUFFIX)):
            expected = directory.removesuffix(OLD_SUFFIX) + NEW_SUFFIX if directory.endswith(OLD_SUFFIX) else directory
            if group != GROUP or artifact != expected:
                issues.append(f"{path.relative_to(root)}: 应为 {GROUP}:{expected}，实际 {group}:{artifact}")
            if artifact in libraries:
                issues.append(f"重复的 Starter artifactId: {artifact}")
            libraries[artifact] = path
        for dependency in project.findall(".//m:dependency", NS):
            dependency_group = text(dependency, "m:groupId")
            dependency_id = text(dependency, "m:artifactId")
            if dependency_group == GROUP and dependency_id.endswith(OLD_SUFFIX):
                issues.append(f"{path.relative_to(root)}: 本项目依赖仍使用旧坐标 {dependency_id}")

    if not libraries:
        issues.append("反应堆中没有找到 Starter")
    bom_path = root / "common-tool-springboot4-bom/pom.xml"
    if bom_path not in reactor:
        issues.append("BOM 未加入反应堆")
    else:
        managed = [
            text(dependency, "m:artifactId")
            for dependency in reactor[bom_path].findall("m:dependencyManagement/m:dependencies/m:dependency", NS)
            if text(dependency, "m:groupId") == GROUP
        ]
        if set(managed) != set(libraries) or len(managed) != len(set(managed)):
            issues.append("BOM 必须恰好管理全部本项目 Starter，不能遗漏、重复或保留旧坐标")

    for path, project in reactor.items():
        for dependency in project.findall(".//m:dependency", NS):
            artifact = text(dependency, "m:artifactId")
            if text(dependency, "m:groupId") == GROUP and artifact.endswith(NEW_SUFFIX) and artifact not in libraries:
                issues.append(f"{path.relative_to(root)}: 找不到本项目 Starter {artifact}")

    if built_jars:
        for artifact, path in libraries.items():
            artifact_version = text(reactor[path], "m:version", version)
            jar = path.parent / "target" / f"{artifact}-{artifact_version}.jar"
            if not zipfile.is_zipfile(jar):
                issues.append(f"缺少有效库 JAR: {jar.relative_to(root)}")
                continue
            with zipfile.ZipFile(jar) as archive:
                if any(name.startswith("BOOT-INF/") for name in archive.namelist()):
                    issues.append(f"库模块被错误打包为可执行 Boot JAR: {artifact}")
            old_jar = jar.with_name(artifact.removesuffix(NEW_SUFFIX) + OLD_SUFFIX + f"-{artifact_version}.jar")
            if old_jar.exists():
                issues.append(f"残留旧坐标制品，请 clean 后重建: {old_jar.relative_to(root)}")
    return issues, len(libraries)


def main():
    """输出不含凭据的检查结果，任何不一致均返回非零退出码。"""
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--root", type=Path, default=ROOT)
    parser.add_argument("--built-jars", action="store_true")
    arguments = parser.parse_args()
    try:
        issues, count = check(arguments.root, arguments.built_jars)
    except (OSError, ET.ParseError, ValueError) as error:
        print(f"FAIL coordinate check: {error}", file=sys.stderr)
        return 1
    for issue in issues:
        print("FAIL " + issue, file=sys.stderr)
    if issues:
        return 1
    print(f"PASS coordinates: {count} starters, BOM and reactor dependencies consistent")
    return 0


if __name__ == "__main__":
    sys.exit(main())
