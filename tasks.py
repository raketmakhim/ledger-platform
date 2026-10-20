"""Project tasks. Developers and CI run the same commands: ./task <name>  (.\\task <name> on Windows).

    test         unit tests, then the coverage gaps
    integration  integration tests (needs Docker)
    coverage     coverage of every file from the last test run, and the lines missing
"""
import argparse
import os
import subprocess
import sys
import xml.etree.ElementTree as ET

ROOT = os.path.dirname(os.path.abspath(__file__))
REPORT = os.path.join(ROOT, "target", "site", "jacoco", "jacoco.xml")
POM = os.path.join(ROOT, "pom.xml")
MVNW = os.path.join(ROOT, "mvnw.cmd" if os.name == "nt" else "mvnw")


def mvn(*args):
    return subprocess.run([MVNW, "-B", *args], cwd=ROOT).returncode


def ranges(numbers):
    """[1, 2, 3, 7] -> '1-3, 7'"""
    parts, start, prev = [], None, None
    for n in numbers + [None]:
        if start is None:
            start = prev = n
        elif n is not None and n == prev + 1:
            prev = n
        else:
            parts.append(str(start) if start == prev else f"{start}-{prev}")
            start = prev = n
    return ", ".join(parts)


def percent(source, kind):
    """Covered percentage for a LINE or BRANCH counter, or None when the file has none of that kind."""
    for counter in source.findall("counter"):
        if counter.get("type") == kind:
            covered, missed = int(counter.get("covered")), int(counter.get("missed"))
            return 100 * covered / (covered + missed)
    return None


def thresholds():
    """The minimums the build enforces, read from the JaCoCo check in pom.xml, as percentages."""
    ns = {"m": "http://maven.apache.org/POM/4.0.0"}
    result = {}
    for limit in ET.parse(POM).getroot().findall(".//m:limit", ns):
        result[limit.find("m:counter", ns).text] = 100 * float(limit.find("m:minimum", ns).text)
    return result


# Colours are skipped when piped to a file, unless it is CI, which shows them. NO_COLOR turns them off.
COLOUR = "NO_COLOR" not in os.environ and (sys.stdout.isatty() or "GITHUB_ACTIONS" in os.environ)
if COLOUR and os.name == "nt":
    os.system("")  # makes the Windows console understand colour codes


def level(value, minimum):
    """0 green (at or above the build minimum), 1 yellow (from 80%), 2 red. Nothing to cover counts as green."""
    if value is None or value >= minimum:
        return 0
    return 1 if value >= 80 else 2


def paint(text, lvl):
    return f"\033[{(32, 33, 31)[lvl]}m{text}\033[0m" if COLOUR else text


def show(value, minimum):
    """Right-aligned percentage, coloured by level."""
    return paint("     -" if value is None else f"{value:5.1f}%", level(value, minimum))


def coverage():
    """Every file's line and branch coverage, grouped by folder, with the lines that are missing."""
    try:
        root = ET.parse(REPORT).getroot()
    except FileNotFoundError:
        print(f"{REPORT} not found. Run the tests first.")
        return 1

    minimum = thresholds()
    incomplete = 0
    print(f"{'':44}{'lines':>6}    {'branches':>6}")
    for package in sorted(root.iter("package"), key=lambda p: p.get("name")):
        printed = []
        for source in sorted(package.iter("sourcefile"), key=lambda s: s.get("name")):
            rows = list(source.iter("line"))
            missed = [int(l.get("nr")) for l in rows if int(l.get("mi")) > 0]
            partial = [int(l.get("nr")) for l in rows if int(l.get("mi")) == 0 and int(l.get("mb")) > 0]
            note = ""
            if missed:
                note += f"  lines not run: {ranges(missed)}"
            if partial:
                note += f"  branches not taken: {ranges(partial)}"
            incomplete += bool(missed or partial)
            lines, branches = percent(source, 'LINE'), percent(source, 'BRANCH')
            if lines is None and branches is None:
                continue  # an interface or annotation: no code to cover
            worst = max(level(lines, minimum['LINE']), level(branches, minimum['BRANCH']))
            name = paint(f"{source.get('name'):42}", worst)
            printed.append(f"  {name}{show(lines, minimum['LINE'])}    {show(branches, minimum['BRANCH'])}{note}")
        if printed:
            print(f"\n{package.get('name').removeprefix('com/raketmakhim/ledger/')}/")
            print("\n".join(printed))

    total = {c.get("type"): c for c in root.findall("counter")}
    def overall(kind):
        c = total.get(kind)
        return None if c is None else 100 * int(c.get("covered")) / (int(c.get("covered")) + int(c.get("missed")))
    print(f"\n{'Total':44}{show(overall('LINE'), minimum['LINE'])}    {show(overall('BRANCH'), minimum['BRANCH'])}")
    print(f"{incomplete} file(s) not fully covered." if incomplete else "Everything is fully covered.")
    return 0


def test():
    # clean: JaCoCo adds to target/jacoco.exec between runs, so old data can hide a coverage drop
    code = mvn("clean", "test", "-DexcludedGroups=integration")
    print()
    coverage()
    return code


def integration():
    # the coverage rule belongs to the unit tests
    return mvn("test", "-Dgroups=integration", "-Djacoco.skip=true")


TASKS = {"test": test, "integration": integration, "coverage": coverage}

if __name__ == "__main__":
    parser = argparse.ArgumentParser(description="Project tasks.")
    parser.add_argument("task", choices=TASKS, help="what to run")
    sys.exit(TASKS[parser.parse_args().task]())
