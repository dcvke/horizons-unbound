"""Seed a world, reopen it and check player memory after death, or import an older test world."""
import argparse
import json
import os
from pathlib import Path
import shutil
import struct
import subprocess
import uuid
from read_nbt import read_nbt

def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--loader', choices=['neoforge', 'forge', 'fabric'], required=True)
    parser.add_argument('--output', type=Path, required=True)
    parser.add_argument('--old-world', type=Path)
    parser.add_argument('--gradle-arg', action='append', default=[])
    args = parser.parse_args()
    root = Path(__file__).resolve().parent.parent
    directory = args.output.resolve()
    directory.mkdir(parents=True, exist_ok=False)
    (directory / 'options.txt').write_text('soundCategory_master:0.0\nfullscreen:false\npauseOnLostFocus:false\nrenderDistance:12\nsimulationDistance:12\nmaxFps:120\n')
    if args.old_world:
        world = args.old_world.resolve()
        shutil.copytree(world, directory / 'saves/hutest')
        storms = read_nbt(world / 'data/horizons_unbound.dat')['data']['Storms']
        expected = {}
        for storm in storms:
            key = str(uuid.UUID(bytes=b''.join(struct.pack('>i', n) for n in storm['Id'])))
            expected['storm.' + key] = storm['Type']
        player_file = next((world / 'playerdata').glob('*.dat'))
        player = read_nbt(player_file)
        def persistent(value):
            if isinstance(value, dict):
                if any(key.startswith('horizons_unbound_') for key in value): return value
                for child in value.values():
                    found = persistent(child)
                    if found is not None: return found
            return None
        memory = persistent(player) or {}
        for key in ['horizons_unbound_met', 'horizons_unbound_finale', 'horizons_unbound_deep_dark']:
            value = memory.get(key, False if key.endswith('deep_dark') else '')
            expected[key] = str(bool(value)).lower() if key.endswith('deep_dark') else value
        (directory / 'old-world.properties').write_text('\n'.join(key + '=' + value for key, value in expected.items()) + '\n')
        steps = [('old_world', True)]
    else:
        steps = [('persistence_seed', False), ('persistence_reload', True)]
    results = []
    for scenario, reuse in steps:
        command = [str(root / ('gradlew.bat' if os.name == 'nt' else 'gradlew')), ':' + args.loader + ':runClientTest', '--console=plain', '--no-configuration-cache', '-Pscenario=' + scenario, '-PtestDirectory=' + str(directory), '-PreuseWorld=' + str(reuse).lower(), *args.gradle_arg]
        with (directory / (scenario + '.log')).open('w', encoding='utf-8') as log:
            process = subprocess.Popen(command, cwd=root, stdout=log, stderr=subprocess.STDOUT)
            try: code = process.wait(timeout=390)
            except subprocess.TimeoutExpired:
                if os.name == 'nt': subprocess.run(['taskkill', '/PID', str(process.pid), '/T', '/F'], stdout=log, stderr=subprocess.STDOUT)
                else: process.kill()
                code = 'timeout'
        report_path = directory / 'test-report.txt'
        report = report_path.read_text(encoding='utf-8') if report_path.exists() else ''
        if report_path.exists(): shutil.copy2(report_path, directory / (scenario + '-report.txt'))
        result = dict(scenario=scenario, exit=code, done='DONE in ' in report, failures=[line for line in report.splitlines() if 'FAIL' in line], checks=[line for line in report.splitlines() if 'PASS persistence' in line])
        results.append(result)
        (directory / 'results.json').write_text(json.dumps(results, indent=2))
        print(json.dumps(result), flush=True)
        if code != 0 or not result['done'] or result['failures'] or not result['checks']:
            raise SystemExit('Persistence check failed: ' + str(directory))

if __name__ == '__main__':
    main()
