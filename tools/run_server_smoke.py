"""Start an isolated dedicated server, exercise storm commands through local RCON and stop cleanly."""
import argparse
import json
import os
from pathlib import Path
import shutil
import socket
import struct
import subprocess
import threading
import time

def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--loader', choices=['neoforge', 'forge', 'fabric'], required=True)
    parser.add_argument('--output', type=Path, required=True)
    parser.add_argument('--gradle-arg', action='append', default=[])
    parser.add_argument('--mods', type=Path, help='Other mods to run beside it (their jars)')
    args = parser.parse_args()
    root = Path(__file__).resolve().parent.parent
    directory = args.output.resolve()
    directory.mkdir(parents=True, exist_ok=False)
    with socket.socket() as reservation:
        reservation.bind(('127.0.0.1', 0))
        rcon_port = reservation.getsockname()[1]
    password = 'horizons-unbound-local-test'
    (directory / 'eula.txt').write_text('eula=true\n')
    if args.mods:
        if args.loader == 'fabric': args.gradle_arg.append('-PcompatModsDirectory=' + str(args.mods.resolve()))
        else:
            (directory / 'mods').mkdir()
            for mod in args.mods.glob('*.jar'): shutil.copy2(mod, directory / 'mods' / mod.name)
    (directory / 'server.properties').write_text(f'server-ip=127.0.0.1\nserver-port=0\nonline-mode=false\nenable-rcon=true\nrcon.port={rcon_port}\nrcon.password={password}\nlevel-seed=1234\nview-distance=3\nsimulation-distance=3\nspawn-protection=0\n')
    command = [str(root / ('gradlew.bat' if os.name == 'nt' else 'gradlew')), ':' + args.loader + ':runServer', '--console=plain', '--no-configuration-cache', '-PserverDirectory=' + str(directory), *args.gradle_arg]
    ready = threading.Event()
    lines = []
    log = (directory / 'server.log').open('w', encoding='utf-8')
    process = subprocess.Popen(command, cwd=root, stdout=subprocess.PIPE, stderr=subprocess.STDOUT, text=True, encoding='utf-8', errors='replace')
    def collect():
        for line in process.stdout:
            log.write(line); log.flush(); lines.append(line)
            if 'Done (' in line and 'For help' in line: ready.set()
    reader = threading.Thread(target=collect, daemon=True)
    reader.start()
    def request(sock, identifier, kind, text):
        body = struct.pack('<ii', identifier, kind) + text.encode() + b'\0\0'
        sock.sendall(struct.pack('<i', len(body)) + body)
        def exact(size):
            data = b''
            while len(data) < size:
                piece = sock.recv(size - len(data))
                if not piece: raise EOFError('RCON closed')
                data += piece
            return data
        length = struct.unpack('<i', exact(4))[0]
        packet = exact(length)
        response_id = struct.unpack('<i', packet[:4])[0]
        if response_id == -1: raise RuntimeError('RCON authentication failed')
        return packet[8:-2].decode(errors='replace')
    responses = {}
    try:
        deadline = time.monotonic() + 210
        while not ready.wait(1):
            if process.poll() is not None: raise RuntimeError('Server exited before ready')
            if time.monotonic() > deadline: raise TimeoutError('Server startup timed out')
        with socket.create_connection(('127.0.0.1', rcon_port), timeout=20) as client:
            request(client, 1, 3, password)
            for index, command in enumerate(['storm spawn end', 'storm info', 'storm clear'], 2):
                responses[command] = request(client, index, 2, command)
            if 'Spawned end storm' not in responses['storm spawn end']: raise RuntimeError('Storm command did not work')
            try: request(client, 9, 2, 'stop')
            except EOFError: pass
        code = process.wait(timeout=60)
        if code: raise RuntimeError('Server exit ' + str(code))
        errors = [line for line in lines if 'Horizons Unbound:' in line and any(word in line.lower() for word in ['could not', 'error', 'stopped acting'])]
        if errors: raise RuntimeError('Storm error: ' + '\n'.join(errors))
        result = dict(ready=True, stopped=True, exit=code, commands=responses)
        (directory / 'results.json').write_text(json.dumps(result, indent=2))
        print(json.dumps(result), flush=True)
    finally:
        if process.poll() is None:
            if os.name == 'nt': subprocess.run(['taskkill', '/PID', str(process.pid), '/T', '/F'], stdout=log, stderr=subprocess.STDOUT)
            else: process.kill()
        reader.join(timeout=5)
        log.close()

if __name__ == '__main__':
    main()
