"""Prepare the LAN Chat project for local development on Windows.

This project does not have Python package dependencies. The script checks the
required Java and Node.js runtimes, downloads Maven dependencies by compiling
the Java edition, and installs the optional web edition's npm dependencies.
"""

from __future__ import annotations

import argparse
import re
import shutil
import subprocess
import sys
from pathlib import Path


ROOT = Path(__file__).resolve().parent
WEB_DIR = ROOT / "web"
LOCAL_MAVEN = ROOT / "tools" / "apache-maven-3.10.0" / "bin" / "mvn.cmd"


def run(command: list[str], cwd: Path = ROOT) -> None:
    print(f"> {' '.join(command)}")
    try:
        subprocess.run(command, cwd=cwd, check=True)
    except FileNotFoundError:
        raise RuntimeError(
            f"Required command was not found: {command[0]}. "
            "Install it and add it to PATH."
        ) from None
    except subprocess.CalledProcessError as error:
        raise RuntimeError(
            f"Command failed with exit code {error.returncode}: {' '.join(command)}"
        ) from None


def command_output(command: list[str]) -> str:
    try:
        result = subprocess.run(
            command,
            cwd=ROOT,
            check=True,
            capture_output=True,
            text=True,
        )
    except FileNotFoundError:
        raise RuntimeError(
            f"Required command was not found: {command[0]}. "
            "Install it and add it to PATH."
        ) from None
    except subprocess.CalledProcessError as error:
        output = (error.stdout or "") + (error.stderr or "")
        raise RuntimeError(
            f"Could not inspect {' '.join(command)}:\n{output.strip()}"
        ) from None
    return f"{result.stdout}\n{result.stderr}"


def version(output: str, pattern: str) -> tuple[int, ...] | None:
    match = re.search(pattern, output)
    return (
        tuple(int(part) for part in match.groups() if part is not None)
        if match
        else None
    )


def check_java() -> None:
    output = command_output(["java", "-version"])
    found = version(output, r'version "(\d+)(?:\.(\d+))?')
    if found is None or found[0] != 25:
        detected = ".".join(str(part) for part in found) if found else "unknown"
        raise RuntimeError(
            f"Java 25 is required, but Java {detected} was detected. "
            "Install JDK 25 and set JAVA_HOME."
        )
    print("Java 25 detected.")


def check_node() -> None:
    output = command_output(["node", "--version"])
    found = version(output, r"v(\d+)\.(\d+)(?:\.(\d+))?")
    if found is None or found < (20, 9, 0):
        detected = ".".join(str(part) for part in found) if found else "unknown"
        raise RuntimeError(
            f"Node.js 20.9 or newer is required, but {detected} was detected."
        )
    print(f"Node.js {'.'.join(str(part) for part in found)} detected.")


def maven_command() -> str:
    if LOCAL_MAVEN.is_file():
        return str(LOCAL_MAVEN)
    maven = shutil.which("mvn")
    if maven is None:
        raise RuntimeError(
            "Maven 3.9 or newer was not found. Install Maven or keep the "
            "bundled tools/apache-maven-3.10.0 directory."
        )
    return maven


def install_java_dependencies() -> None:
    maven = maven_command()
    run(
        [
            maven,
            "-q",
            f"-Dmaven.repo.local={ROOT / '.m2' / 'repository'}",
            "-DskipTests",
            "compile",
        ]
    )
    print("Java dependencies are ready.")


def install_web_dependencies() -> None:
    check_node()
    if not WEB_DIR.is_dir():
        raise RuntimeError(f"Web directory does not exist: {WEB_DIR}")
    npm = shutil.which("npm")
    if npm is None:
        raise RuntimeError("npm was not found. Install Node.js 20.9 or newer.")
    run([npm, "install"], cwd=WEB_DIR)
    print("Web dependencies are ready.")


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument(
        "--skip-java", action="store_true", help="skip Java checks and Maven setup"
    )
    parser.add_argument(
        "--skip-web", action="store_true", help="skip Node.js checks and npm install"
    )
    args = parser.parse_args()

    try:
        if not args.skip_java:
            check_java()
            install_java_dependencies()
        if not args.skip_web:
            install_web_dependencies()
    except RuntimeError as error:
        print(f"Setup failed: {error}", file=sys.stderr)
        return 1

    print("\nSetup complete. Start the Java edition with .\\run-server.bat.")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
