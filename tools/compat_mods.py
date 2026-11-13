"""Expose bundled Fabric dependencies when using local jars through Loom in development."""
import io
import json
import os
from pathlib import Path
import xml.etree.ElementTree as ET
from zipfile import ZipFile


def unpack_fabric_dependencies(project, mods):
    properties = dict(line.split('=', 1) for line in (project / 'gradle.properties').read_text().splitlines()
                      if '=' in line and not line.lstrip().startswith('#'))
    cache = Path(os.environ.get('GRADLE_USER_HOME', Path.home() / '.gradle'))
    api = cache / 'caches/modules-2/files-2.1/net.fabricmc.fabric-api/fabric-api' / properties['fabric_version']
    poms = list(api.glob('*/*.pom'))
    if not poms:
        raise RuntimeError('Build :fabric before testing local mods so Fabric API dependency metadata is available')
    provided = {'fabric-api', 'fabricloader', 'minecraft', 'java', 'forgeconfigapiport', 'modmenu', 'mixinextras'}
    visited = set()
    while poms:
        pom = poms.pop()
        if pom in visited:
            continue
        visited.add(pom)
        for dependency in ET.parse(pom).findall('.//{*}dependency'):
            if dependency.findtext('{*}groupId') == 'net.fabricmc.fabric-api':
                name, version = dependency.findtext('{*}artifactId'), dependency.findtext('{*}version')
                provided.add(name)
                if version:
                    module = api.parent.parent / name / version
                    poms.extend(module.glob('*/*.pom'))
    parents = sorted(mods.glob('*.jar'))
    for parent in parents:
        with ZipFile(parent) as archive:
            provided.add(json.loads(archive.read('fabric.mod.json'))['id'])
    extracted = []

    def nested(data):
        with ZipFile(io.BytesIO(data)) as archive:
            metadata = json.loads(archive.read('fabric.mod.json'))
            for entry in metadata.get('jars', []):
                payload = archive.read(entry['file'])
                with ZipFile(io.BytesIO(payload)) as child:
                    info = json.loads(child.read('fabric.mod.json'))
                if info['id'] in provided:
                    continue
                provided.add(info['id'])
                directory = mods / 'nested'
                directory.mkdir(exist_ok=True)
                # The archive member is used only for its basename, never as an extraction path.
                target = directory / Path(entry['file']).name
                if target.exists() and target.read_bytes() != payload:
                    raise RuntimeError('Conflicting nested compatibility dependency: ' + target.name)
                target.write_bytes(payload)
                extracted.append({'id': info['id'], 'version': info['version'], 'file': target.name})
                nested(payload)

    for parent in parents:
        nested(parent.read_bytes())
    (mods / 'nested-dependencies.json').write_text(json.dumps(extracted, indent=2))
    return extracted
