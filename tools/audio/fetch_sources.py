"""Download the credited, hash-pinned source audio for build_voices.py.

Python standard library; extracting .7z and .zip uses bsdtar (Windows includes tar).
Only the named audio members are read to memory; archive paths are never extracted
directly onto disk. No program from an audio archive is executed.
"""
from pathlib import Path
import argparse, hashlib, json, shutil, subprocess, urllib.request

def checksum(data): return hashlib.sha256(data).hexdigest()
def within(root,relative):
    target=(root/relative).resolve()
    if not target.is_relative_to(root): raise ValueError('Source path escapes destination: '+relative)
    return target

def main():
    parser=argparse.ArgumentParser()
    parser.add_argument('--source-dir',type=Path,required=True)
    args=parser.parse_args(); root=args.source_dir.resolve(); root.mkdir(parents=True,exist_ok=True)
    manifest=json.loads(Path(__file__).with_name('audio_sources.json').read_text(encoding='utf-8'))
    downloads={}
    for item in manifest['downloads']:
        target=within(root,item['file'])
        if not target.exists() or checksum(target.read_bytes()) != item['sha256']:
            request=urllib.request.Request(item['url'],headers={'User-Agent':'WolfismAudioSources/1.0'})
            with urllib.request.urlopen(request,timeout=60) as response: data=response.read()
            if checksum(data)!=item['sha256']: raise ValueError('Download checksum changed: '+item['url'])
            target.write_bytes(data)
        downloads[item['id']]=target
    tar=shutil.which('tar')
    for item in manifest['extracted_audio']:
        target=within(root,item['file'])
        if not target.exists() or checksum(target.read_bytes())!=item['sha256']:
            archive=downloads[item['download_id']]
            if archive.suffix not in ('.zip','.7z'): raise ValueError('Unexpected missing direct recording: '+item['file'])
            if not tar: parser.error('Install bsdtar to read the source archives.')
            member=item['file'].split('/',1)[1]
            data=subprocess.run([tar,'-xOf',str(archive),member],capture_output=True,check=True).stdout
            if checksum(data)!=item['sha256']: raise ValueError('Archive member checksum changed: '+member)
            target.parent.mkdir(parents=True,exist_ok=True); target.write_bytes(data)
    print(f'Verified {len(downloads)} downloads and {len(manifest["extracted_audio"])} source audio files in {root}')

if __name__=='__main__': main()
