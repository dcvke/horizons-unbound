"""Run silent scripted client tests in fresh directories and retain every report and frame."""
import argparse
import json
import os
from pathlib import Path
import subprocess
import time
import shutil
from compat_mods import unpack_fabric_dependencies

SCENARIOS = ['birth', 'cutscene', 'discovery', 'death', 'grand', 'wall', 'phrases', 'acting', 'gather', 'cutscene_obstacles', 'baseline']

def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--loader', required=True, choices=['neoforge', 'forge', 'fabric'])
    parser.add_argument('--output', type=Path, required=True)
    parser.add_argument('--options', type=Path)
    parser.add_argument('--scenarios', nargs='+', default=SCENARIOS)
    parser.add_argument('--gradle-arg', action='append', default=[])
    parser.add_argument('--mods', type=Path)
    parser.add_argument('--shaderpack', type=Path)
    parser.add_argument('--kubejs', action='store_true')
    parser.add_argument('--resume', action='store_true')
    parser.add_argument('--language', choices=['en_us', 'ru_ru'])
    parser.add_argument('--production', action='store_true', help='Run the remapped Fabric jar with the normal production loader')
    args = parser.parse_args()
    if args.production and args.loader != 'fabric': parser.error('--production is a Fabric check')
    root = Path(__file__).resolve().parent.parent
    destination = args.output.resolve()
    destination.mkdir(parents=True, exist_ok=args.resume)
    options = args.options.read_text(encoding='utf-8') if args.options else 'soundCategory_master:0.0\nfullscreen:false\npauseOnLostFocus:false\nrenderDistance:12\nsimulationDistance:12\nmaxFps:120\n'
    options = '\n'.join(line for line in options.splitlines() if not line.startswith(('soundCategory_master:', 'fullscreen:')))
    options += '\nsoundCategory_master:0.0\nfullscreen:false\n'
    if args.language:
        options = '\n'.join(line for line in options.splitlines() if not line.startswith('lang:')) + '\nlang:' + args.language + '\n'
    results_file = destination / 'results.json'
    results = json.loads(results_file.read_text()) if args.resume and results_file.exists() else []
    wrapper = root / ('gradlew.bat' if os.name == 'nt' else 'gradlew')
    for scenario in args.scenarios:
        if scenario not in SCENARIOS + ['config', 'claim']:
            parser.error('Unknown scenario: ' + scenario)
        previous = next((result for result in results if result['scenario'] == scenario), None)
        if previous and previous['exit'] == 0 and previous['done'] and not previous['failures'] and not previous['errors']:
            print('SKIP passed ' + scenario, flush=True)
            continue
        results = [result for result in results if result['scenario'] != scenario]
        game = destination / scenario
        if game.exists():
            archived = destination / (scenario + '-previous-' + str(time.time_ns()))
            game.rename(archived)
            previous_log = destination / (scenario + '.log')
            if previous_log.exists(): shutil.copy2(previous_log, archived / 'gradle-previous.log')
        game.mkdir()
        (game / 'options.txt').write_text(options, encoding='utf-8')
        if args.mods:
            mod_directory = game / ('mods' if args.loader == 'neoforge' or args.production else 'compat-input')
            mod_directory.mkdir()
            for mod in args.mods.glob('*.jar'): shutil.copy2(mod, mod_directory / mod.name)
            if args.loader == 'fabric' and not args.production: unpack_fabric_dependencies(root, mod_directory)
        if args.shaderpack:
            (game / 'shaderpacks').mkdir()
            shutil.copy2(args.shaderpack, game / 'shaderpacks' / args.shaderpack.name)
            (game / 'config').mkdir(exist_ok=True)
            shader_config = 'oculus.properties' if args.loader == 'forge' else 'iris.properties'
            (game / 'config' / shader_config).write_text('enableShaders=true\nshaderPack=' + args.shaderpack.name + '\ndisableUpdateMessage=true\n')
        if args.kubejs:
            scripts = game / 'kubejs/startup_scripts'
            scripts.mkdir(parents=True)
            (scripts / 'horizons_unbound_compat.js').write_text("ForgeEvents.onEvent('com.horizonsunbound.api.StormEvent$Born', event => { console.info('[HU_COMPAT] Born event received: ' + event.getStorm().type.id) })\n")
        task = 'runProductionClientTest' if args.production else 'runClientTest'
        command = [str(wrapper), ':' + args.loader + ':' + task, '--console=plain', '--no-configuration-cache', '-Pscenario=' + scenario, '-PtestDirectory=' + str(game), *args.gradle_arg]
        if args.mods and args.loader != 'neoforge' and not args.production: command.append('-PcompatModsDirectory=' + str(mod_directory))
        print('START ' + scenario, flush=True)
        started = time.monotonic()
        with (destination / (scenario + '.log')).open('w', encoding='utf-8') as log:
            process = subprocess.Popen(command, cwd=root, stdout=log, stderr=subprocess.STDOUT)
            try:
                code = process.wait(timeout=390)
            except subprocess.TimeoutExpired:
                if os.name == 'nt':
                    subprocess.run(['taskkill', '/PID', str(process.pid), '/T', '/F'], stdout=log, stderr=subprocess.STDOUT)
                else:
                    process.kill()
                code = 'timeout'
        report_path = game / 'test-report.txt'
        report = report_path.read_text(encoding='utf-8') if report_path.exists() else ''
        log_text = (destination / (scenario + '.log')).read_text(encoding='utf-8', errors='replace')
        errors = [line for line in log_text.splitlines() if 'Horizons Unbound:' in line and any(s in line.lower() for s in ['error', 'hidden', 'could not', 'stopped acting'])]
        if args.kubejs and scenario == 'birth' and '[HU_COMPAT] Born event received:' not in log_text:
            errors.append('KubeJS did not receive StormEvent.Born')
        if args.shaderpack and 'shader active=false' in report:
            errors.append('The requested shader pack is not active')
        if args.shaderpack and 'Using shaderpack: ' + args.shaderpack.name not in log_text:
            errors.append('Iris/Oculus did not select the requested shader pack')
        result = dict(scenario=scenario, runtime='production' if args.production else 'development', exit=code, done='DONE in ' in report,
                      failures=[line for line in report.splitlines() if 'FAIL' in line], errors=errors,
                      screenshots=len(list((game / 'screenshots').glob('*.png'))), seconds=round(time.monotonic() - started, 1))
        results.append(result)
        (destination / 'results.json').write_text(json.dumps(results, indent=2), encoding='utf-8')
        print(json.dumps(result), flush=True)
        if code != 0 or not result['done'] or result['failures'] or errors:
            raise SystemExit('Scenario failed; inspect ' + str(destination))
    print('COMPLETE ' + str(destination), flush=True)

if __name__ == '__main__':
    main()
