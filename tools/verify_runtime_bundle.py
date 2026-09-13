#!/usr/bin/env python3
"""Audit the built, installed Wolfism runtime bundle; Python 3.11+, no dependencies."""
from __future__ import annotations

import argparse
from collections import Counter
import hashlib
from io import BytesIO
import json
import re
from pathlib import Path
import tomllib
import zipfile

BASE = "META-INF/licenses/wolfism-runtime/"
LIBRARY_VERSIONS = {"photon": "26.1.2.2", "ldlib2": "26.1.2.39",
                "kilagraph": "26.1.0.14", "terrablender": "26.1.0.2"}
OPTIONAL_IDS = {"waystones", "balm", "shogi", "shogi_api"}


# These twelve unreferenced, undocumented recordings were explicitly retired
# for publication. Keep their exact paths blocked even if later re-referenced.
RETIRED_SOUND_FILES = frozenset(
    f"assets/wolfism/sounds/entity/{species}/{kind}_{index}.ogg"
    for species, kinds in (("creator_wolf", ("growl", "howl")),
                           ("primordial_wolf", ("howl",)), ("wolf_king", ("howl",)))
    for kind in kinds for index in range(1, 4)
)
SOUND_MAP = "assets/wolfism/sounds.json"
SOUND_PREFIX = "assets/wolfism/sounds/"


def audit_sound_resources(archive: zipfile.ZipFile, errors: list[str]) -> dict:
    """Validate this archive's Wolfism sounds; external namespaces need their own resource packs."""
    names = set(archive.namelist())
    present = {name for name in names if name.startswith(SOUND_PREFIX) and name.endswith(".ogg")}
    references: set[str] = set()
    aliases: dict[str, set[str]] = {}
    external: set[tuple[str, str, str]] = set()
    retired = present.intersection(RETIRED_SOUND_FILES)
    invalid: list[str] = []

    def unique_object(pairs):
        value = {}
        for key, entry in pairs:
            if key in value:
                raise ValueError("Duplicate JSON key: " + key)
            value[key] = entry
        return value

    try:
        events = json.loads(archive.read(SOUND_MAP), object_pairs_hook=unique_object)
        if not isinstance(events, dict) or not events:
            raise ValueError("Expected a nonempty sound-event object")
    except (KeyError, ValueError, UnicodeError) as error:
        invalid.append("Invalid Wolfism sounds.json: " + str(error))
        events = {}

    for event, definition in events.items():
        if not re.fullmatch(r"[a-z0-9/._-]+", event):
            invalid.append("Invalid local sound event ID: " + event)
            continue
        aliases[event] = set()
        if not isinstance(definition, dict) or not isinstance(definition.get("sounds", []), list):
            invalid.append("Invalid sound event definition: " + event)
            continue
        for index, entry in enumerate(definition.get("sounds", [])):
            label = f"{event}[{index}]"
            if isinstance(entry, str):
                sound, kind = entry, "file"
            elif isinstance(entry, dict):
                sound, kind = entry.get("name"), entry.get("type", "file")
            else:
                invalid.append("Invalid sound entry: " + label)
                continue
            if kind not in ("file", "event") or not isinstance(sound, str):
                invalid.append("Invalid sound name/type: " + label)
                continue
            # Minecraft Identifier.parse defaults an unqualified name to minecraft,
            # including entries inside another namespace's sounds.json.
            qualified = sound if ":" in sound else "minecraft:" + sound
            if not re.fullmatch(r"[a-z0-9_.-]+:[a-z0-9/._-]+", qualified):
                invalid.append("Invalid sound reference ID: " + label + " -> " + sound)
                continue
            namespace, resource = qualified.split(":", 1)
            if namespace != "wolfism":
                external.add((event, kind, qualified))
                continue
            if resource.startswith("/") or any(part in ("", ".", "..") for part in resource.split("/")):
                invalid.append("Unsafe local sound reference path: " + label + " -> " + sound)
                continue
            file = SOUND_PREFIX + resource + ".ogg"
            if file in RETIRED_SOUND_FILES:
                retired.add(file)
            if kind == "file":
                references.add(file)
            else:
                aliases[event].add(resource)

    missing_aliases = sorted((event, target) for event, targets in aliases.items()
                             for target in targets if target not in aliases)
    # Iterative DFS keeps long alias chains bounded by the map size, without a
    # Python recursion limit or Minecraft's recursive sound selection looping.
    colors: dict[str, int] = {}
    cycles: list[list[str]] = []
    for event in sorted(aliases):
        if colors.get(event):
            continue
        colors[event] = 1
        stack = [(event, iter(sorted(aliases[event])))]
        while stack:
            current, targets = stack[-1]
            target = next(targets, None)
            if target is None:
                colors[current] = 2
                stack.pop()
            elif target not in aliases:
                continue
            elif colors.get(target) == 1:
                chain = [item[0] for item in stack]
                cycles.append(chain[chain.index(target):] + [target])
            elif not colors.get(target):
                colors[target] = 1
                stack.append((target, iter(sorted(aliases[target]))))

    missing = sorted(references - present)
    unused = sorted(present - references)
    errors.extend(invalid)
    for description, values in (("Retired legacy sound files are forbidden", sorted(retired)),
                                ("Missing local sound files", missing),
                                ("Unused local sound files", unused),
                                ("Missing local sound event aliases", missing_aliases),
                                ("Cyclic local sound event aliases", cycles)):
        if values:
            errors.append(description + ": " + repr(values))
    if not references:
        errors.append("No local Wolfism sound files are referenced")
    return {"event_count": len(events), "local_ogg_count": len(present),
            "referenced_local_ogg_count": len(references),
            "local_event_alias_count": sum(map(len, aliases.values())),
            "missing_files": missing, "unused_files": unused,
            "retired_legacy_files": sorted(retired),
            "missing_event_aliases": [list(pair) for pair in missing_aliases],
            "event_alias_cycles": cycles, "invalid_definitions": invalid,
            "external_references": [dict(zip(("event", "type", "name"), row)) for row in sorted(external)],
            "scope": "Wolfism file and event references in the outer archive; other namespaces are recorded, not resolved."}


def project_properties() -> dict[str, str]:
    path = Path(__file__).resolve().parents[1] / "gradle.properties"
    if not path.is_file():
        return {}
    return {key.strip(): value.strip() for line in path.read_text(encoding="utf-8").splitlines()
            if line.strip() and not line.lstrip().startswith(("#", "!")) and "=" in line
            for key, value in [line.split("=", 1)]}


def audit(jar_path: Path, expected_version: str | None = None) -> dict:
    properties = project_properties()
    version = expected_version or properties.get("mod_version")
    if not version or any(c.isspace() or c in "/\\" for c in version):
        raise ValueError("Supply --expected-version or a valid project mod_version")
    mod_versions = {"wolfism": version, **LIBRARY_VERSIONS}
    errors: list[str] = []
    nested: list[dict] = []
    mods: dict[str, list[dict]] = {}
    root_bytes = jar_path.read_bytes()
    with zipfile.ZipFile(BytesIO(root_bytes)) as root:
        names = root.namelist()
        sound_resources = audit_sound_resources(root, errors)
        if jar_path.name != f"wolfism-{version}.jar":
            errors.append("Unexpected release filename: " + jar_path.name)
        # Check the mod's archive, not the contents of required upstream source ZIPs.
        # Their build scripts and license texts are part of redistribution compliance.
        forbidden_parts = {".cache", ".git", ".gradle", ".idea", ".vscode", "__pycache__"}
        debris = [name for name in names if forbidden_parts.intersection(name.split("/"))
                  or name.startswith(("build/", "run/", "logs/", "crash-reports/", "wolfism/qa/",
                                          "net/ronm19/wolfism/test/"))
                  or name.lower().endswith((".log", ".iml", ".bbmodel", ".pyc"))]
        if debris:
            errors.append("Development debris in release: " + repr(debris))
        bad_crc = root.testzip()
        if bad_crc:
            errors.append("Corrupt ZIP entry: " + bad_crc)
        provenance = json.loads(root.read(BASE + "PROVENANCE.json"))
        expected = provenance["unmodified_runtime_artifacts"]
        for entry in provenance["licenses_and_sources"]:
            name = entry["jar_entry"]
            if name not in root.namelist():
                errors.append("Missing license/source: " + name)
            elif hashlib.sha256(root.read(name)).hexdigest() != entry["sha256"]:
                errors.append("Altered license/source: " + name)
        for name in (BASE + "NOTICE.txt", "wolfism.mixins.json",
                     "net/ronm19/wolfism/mixin/WolfismMixinPlugin.class",
                     "net/ronm19/wolfism/mixin/compat/PhotonServerRegistryMixin.class"):
            if name not in root.namelist():
                errors.append("Missing required integration entry: " + name)
        optional_classes = [name for name in root.namelist() if name.startswith("net/blay09/")]
        if optional_classes:
            errors.append("Optional upstream classes were copied into Wolfism")
        root_metadata = tomllib.loads(root.read("META-INF/neoforge.mods.toml").decode("utf-8"))
        own_license = "META-INF/licenses/wolfism/LICENSE.txt"
        if own_license not in names:
            errors.append("Missing own-project license: " + own_license)
        else:
            project_license = Path(__file__).resolve().parents[1] / "LICENSE"
            if project_license.is_file() and root.read(own_license) != project_license.read_bytes():
                errors.append("Packaged own-project license differs from project LICENSE")
            if properties.get("mod_license") == "MIT" and b"MIT License" not in root.read(own_license):
                errors.append("Own-project license does not contain the declared MIT text")
        if properties.get("mod_license") and root_metadata.get("license", "").split(";", 1)[0] != properties["mod_license"]:
            errors.append("Own-project license metadata differs from gradle.properties")
        wolf_metadata = next((m for m in root_metadata.get("mods", []) if m["modId"] == "wolfism"), {})
        if properties.get("mod_authors") and wolf_metadata.get("authors") != properties["mod_authors"]:
            errors.append("Author metadata differs from gradle.properties")
        for key in ("issueTrackerURL", "displayURL", "updateJSONURL"):
            if key in root_metadata or key in wolf_metadata:
                errors.append("Unexpected URL metadata; this release supplies no support/homepage URL: " + key)
        deps = {d["modId"]: d for d in root_metadata["dependencies"]["wolfism"]}
        for mod_id in LIBRARY_VERSIONS:
            if deps.get(mod_id, {}).get("type") != "required" or deps.get(mod_id, {}).get("side") != "BOTH":
                errors.append("Required both-side dependency metadata missing: " + mod_id)
        waystones = deps.get("waystones", {})
        if any(waystones.get(key) != value for key, value in {
                "type": "optional", "versionRange": "[26.1.2.16,26.1.3)",
                "ordering": "AFTER", "side": "BOTH"}.items()):
            errors.append("Optional Waystones compatibility metadata is missing or unexpected")
        if deps.get("minecraft", {}).get("versionRange") != "[26.1.2]":
            errors.append("Unexpected Minecraft version range")
        if deps.get("neoforge", {}).get("versionRange") != "[26.1.2.94,)":
            errors.append("Unexpected NeoForge minimum version")
        mixins = json.loads(root.read("wolfism.mixins.json"))
        if mixins.get("plugin") != "net.ronm19.wolfism.mixin.WolfismMixinPlugin":
            errors.append("Dedicated-server version gate is missing")
        if "compat.PhotonServerRegistryMixin" not in mixins.get("server", []):
            errors.append("Dedicated-server discovery fix is missing")

    def walk(blob: bytes, chain: str, depth: int = 0) -> None:
        if depth > 5:
            errors.append("Unexpected excessive nesting: " + chain)
            return
        with zipfile.ZipFile(BytesIO(blob)) as archive:
            names = archive.namelist()
            duplicates = [name for name, count in Counter(names).items() if count > 1]
            if duplicates:
                errors.append("Duplicate ZIP entries in " + chain + ": " + repr(duplicates))
            metadata = "META-INF/neoforge.mods.toml"
            if metadata in names:
                info = tomllib.loads(archive.read(metadata).decode("utf-8"))
                for mod in info.get("mods", []):
                    mods.setdefault(mod["modId"], []).append({"version": mod["version"], "archive": chain})
            manifest = "META-INF/jarjar/metadata.json"
            if manifest not in names:
                return
            entries = json.loads(archive.read(manifest))["jars"]
            for entry in entries:
                name = entry["path"]
                if not name.startswith("META-INF/jarjar/") or name not in names:
                    errors.append("Invalid or missing nested jar: " + chain + "!" + name)
                    continue
                data = archive.read(name)
                nested.append({"archive": chain + "!" + name,
                               "filename": Path(name).name,
                               "sha256": hashlib.sha256(data).hexdigest(),
                               "depth": depth + 1,
                               "identifier": entry["identifier"], "version": entry["version"]})
                walk(data, chain + "!" + name, depth + 1)

    walk(root_bytes, jar_path.name)
    for row in expected:
        matches = [found for found in nested if found["sha256"] == row["sha256"]]
        if len(matches) != 1:
            errors.append("Expected one unmodified copy of " + row["filename"] + ", found " + str(len(matches)))
        elif bool(row.get("nested_in")) != (matches[0]["depth"] > 1):
            errors.append("Unexpected nesting for " + row["filename"])
    terra_hash = next(row["sha256"] for row in expected if row["filename"].startswith("terrablender-"))
    terra = next((entry for entry in nested if entry["sha256"] == terra_hash), None)
    if terra and (terra["identifier"] != {"group": "com.github.glitchfiend", "artifact": "TerraBlender-neoforge"}
                  or terra["version"]["artifactVersion"] != "26.1-26.1.0.2"):
        errors.append("TerraBlender must advertise its official Maven identity, not a CurseMaven file ID")
    if len(nested) != len(expected):
        errors.append("Unexpected number of nested libraries: " + str(len(nested)))
    for mod_id, version in mod_versions.items():
        found = mods.get(mod_id, [])
        if len(found) != 1 or found[0]["version"] != version:
            errors.append("Unexpected mod/version: " + mod_id + " " + repr(found))
    for mod_id in OPTIONAL_IDS.intersection(mods):
        errors.append("Optional mod must not be embedded: " + mod_id)
    return {"passed": not errors, "artifact": str(jar_path.resolve()),
            "sha256": hashlib.sha256(root_bytes).hexdigest(), "bytes": len(root_bytes),
            "expected_wolfism_version": mod_versions["wolfism"], "nested_library_count": len(nested), "mods": mods, "nested_libraries": nested,
            "license_source_files_checked": len(provenance["licenses_and_sources"]),
            "sound_resources": sound_resources,
            "errors": errors,
            "scope": "Archive integrity and metadata only; actual installed client/server startup is a separate test."}


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("jar", type=Path)
    parser.add_argument("--report", type=Path)
    parser.add_argument("--expected-version", help="Defaults to mod_version in this project\'s gradle.properties")
    args = parser.parse_args()
    try:
        report = audit(args.jar, args.expected_version)
    except (OSError, KeyError, ValueError, zipfile.BadZipFile) as exc:
        report = {"passed": False, "artifact": str(args.jar), "errors": [str(exc)]}
    text = json.dumps(report, indent=2) + "\n"
    if args.report:
        args.report.parent.mkdir(parents=True, exist_ok=True)
        args.report.write_text(text, encoding="utf-8")
    print(text, end="")
    return 0 if report["passed"] else 1


if __name__ == "__main__":
    raise SystemExit(main())
