"""Build Wolfism v2 voices from licensed, naturally recorded phrases.
Python 3 + numpy + SoundFile >= 0.13 + FFmpeg. No network requests are made.
Original pitch, speed, formants and internal timing are retained.
"""
from pathlib import Path
import argparse, concurrent.futures, hashlib, json, math, re, shutil, subprocess, wave
import numpy as np
import soundfile as sf

PROJECT = Path(__file__).resolve().parents[2]
RATE = 48000
VERSION = 2
ACTIONS = ('ambient1','ambient2','growl1','growl2','whine1','hurt1','death1','howl1')
PREVIEW_SPECIES = ('timber_wolf','zombie_wolf','vampire_wolf','storm_wolf','angel_wolf','christmas_wolf')
# Species, complete wolf phrase, recorded supporting layers.
# Identity comes from source selection and mix, never pitch modulation.
PROFILES = """
timber_wolf,D0,
arctic_wolf,C0,
black_wolf,Y0,
sand_wolf,Y1,
dire_wolf,D0,
fire_wolf,Y2,fire
frost_wolf,C1,bell
storm_wolf,Y1,thunder
water_wolf,Y3,water
earth_wolf,D0,bones
solar_wolf,Y4,bell+fire
lunar_wolf,C2,breath
spirit_wolf,Y5,breath
shadow_wolf,D0,breath
golden_wolf,Y6,bell
cherry_wolf,C0,
violet_wolf,Y0,
gem_wolf,Y1,bell
mushroom_wolf,Y2,
bee_wolf,C1,bee
zombie_wolf,Y1,zombie
skeleton_wolf,Y3,bones
husk_wolf,Y4,zombie+bones
drowned_wolf,C2,zombie+water
phantom_wolf,Y5,bat+breath
blood_wolf,D0,zombie
end_wolf,Y6,breath+bell
sculk_wolf,C3,bones+breath
infernal_wolf,D0,fire+zombie
omen_wolf,Y0,crow+breath
astral_wolf,Y1,bell+breath
angel_wolf,C0,bell
demon_wolf,D0,zombie+fire
grave_wolf,Y2,bones+breath
rift_wolf,Y3,breath+bell
void_wolf,D0,breath
vampire_wolf,C3,bat+breath
spectral_wolf,C1,breath+bell
salva_wolf,Y5,water+bell
wolf_king,D0,
primordial_wolf,C2,thunder+bones
toxic_wolf,Y6,water+breath
magma_wolf,D0,fire+bones
war_wolf,Y0,bones
illager_wolf,Y1,zombie
ancient_wolf,C3,bones
blade_wolf,Y2,bones
raven_wolf,Y3,crow
command_wolf,D0,
ash_wolf,C1,fire+breath
wither_wolf,Y4,bones+zombie
blaze_wolf,Y5,fire
halloween_wolf,C2,bat+bones
creator_wolf,D0,bell+breath
christmas_wolf,C0,bell
saint_patricks_wolf,Y6,bell
new_years_wolf,Y1,fireworks+bell
valentines_wolf,C1,bell
easter_wolf,Y2,
firework_wolf,Y3,fireworks
"""
# Recorded phrase/pause boundaries selected from waveform and spectrogram.
# Chorus recordings contain several wolves. Never replace these with arbitrary
# fixed-length offsets into an ongoing howl.
WOLF_PHRASES = {
    'D0': ('wolf_denali_0.mp3',0.0,8.385),
    'Y0': ('wolf_yellowstone_1.mp3',3.70,12.50),
    'Y1': ('wolf_yellowstone_1.mp3',13.10,19.30),
    'Y2': ('wolf_yellowstone_1.mp3',20.05,29.65),
    'Y3': ('wolf_yellowstone_1.mp3',32.35,41.65),
    'Y4': ('wolf_yellowstone_1.mp3',44.15,54.50),
    'Y5': ('wolf_yellowstone_1.mp3',63.30,72.00),
    'Y6': ('wolf_yellowstone_1.mp3',74.70,83.00),
    'C0': ('wolf_yellowstone_2.mp3',0.0,7.30),
    'C1': ('wolf_yellowstone_2.mp3',15.70,24.30),
    'C2': ('wolf_yellowstone_2.mp3',36.10,45.80),
    'C3': ('wolf_yellowstone_2.mp3',46.65,54.50),
}
GROWLS = (
    ('dog_growl_0.ogg',0.0,0.615),
    ('dog_snarl/dog/dog-grumble.flac',0.0,0.600),
    ('dog_snarl/dog/dog-growl.flac',0.0,0.580),
    ('dog_snarl/dog/dog-snarl.flac',0.0,0.700),
)
# The lower-register alternate Sad Dog.wav is excluded. Use the clearer
# companion take, selecting complete whimpers between its natural pauses.
WHINES = (
    ('dog_collection/Dog/Sad Dog 1.wav',0.72,1.66),
    ('dog_collection/Dog/Sad Dog 1.wav',1.70,2.76),
    ('dog_collection/Dog/Sad Dog 1.wav',3.10,3.88),
)
BARKS = (
    ('dog_collection/Dog/Dog Bark.wav',0.0,0.394),
    ('dog_collection/Dog/Dog Bark 2.wav',0.0,0.333),
    ('dog_collection/Dog/Dog Bark 3.wav',0.0,0.303),
)
ZOMBIE_PHRASES = ((.35,3.20),(3.55,6.25),(6.60,8.80),(9.25,10.25),(10.58,11.28),(14.72,15.81))

def db(value): return round(20*math.log10(max(float(value),1e-12)),3)
def rms(x): return float(np.sqrt(np.mean(np.square(x,dtype=np.float64)))) if len(x) else 0.0

def envelope(x,attack,release):
    x=x.copy(); a=min(round(attack*RATE),len(x)//3); b=min(round(release*RATE),len(x)//2)
    if a: x[:a]*=np.sin(np.linspace(0,np.pi/2,a))**2
    if b: x[-b:]*=np.sin(np.linspace(np.pi/2,0,b))**2
    return x

def gain_to(x,target,ceiling):
    gain=min(10**(target/20)/max(rms(x),1e-9),10**(ceiling/20)/max(float(np.max(np.abs(x))),1e-9))
    return x*gain

class Renderer:
    def __init__(self,args): self.args=args; self.cache={}
    def read(self,name):
        if name not in self.cache:
            # Gentle causal one-pole rumble/DC removal and high-quality sample
            # conversion. No pitch change, FFT EQ, denoiser, gate or chorus.
            command=[self.args.ffmpeg,'-v','error','-i',str(self.args.source_dir/name),
                '-af','highpass=f=45:p=1,aresample=48000:filter_size=64:phase_shift=10:linear_interp=0',
                '-ac','1','-ar',str(RATE),'-f','f32le','-']
            raw=subprocess.run(command,capture_output=True,check=True).stdout
            self.cache[name]=np.frombuffer(raw,dtype='<f4').copy()
        return self.cache[name]
    def phrase(self,selection,attack,release):
        name,start,end=selection; src=self.read(name)
        first=min(round(start*RATE),len(src)-1); last=min(round(end*RATE),len(src))
        assert last>first,(name,start,end)
        x=envelope(src[first:last],attack,release)
        return x,dict(source=name,start_seconds=round(first/RATE,5),source_seconds=round((last-first)/RATE,5),
            rate_ratio=1.0,pitch_shift_semitones=0.0,highpass_hz=45,highpass_poles=1,
            fade_in_seconds=attack,fade_out_seconds=release)
    def theme(self,kind,i,j,long_voice):
        if kind=='fire':
            start=12+(i%8)*2.15; selection=(f'fire_{(i+j)%3}.mp3',start,start+(2.5 if long_voice else .65))
        elif kind=='thunder':
            start=29+(i%3)*11; selection=('thunder_0.mp3',start,start+(3.8 if long_voice else .85))
        elif kind=='water':
            n=(i+j)%3; selection=(f'water/ezwa-water_splash/water_splash-{(1,2,4)[n]:02d}.flac',.06,(1.12,.419,1.315)[n])
        elif kind=='bones': selection=(f'bones/{(i+j)%10}.ogg',.018,.17)
        elif kind=='bell':
            # Begin after the mallet strike, retaining the recorded chime decay.
            n=(i+j)%4; selection=(f'bell_{n}.wav',.16,(2.078,1.440,1.858,2.316)[n])
        elif kind=='breath':
            # Remove the author-slowed ghost effect; use an unshifted canine rasp.
            selection=GROWLS[1]
        elif kind=='zombie':
            intervals=ZOMBIE_PHRASES[:3] if long_voice else ZOMBIE_PHRASES[3:]
            start,end=intervals[(i+j)%len(intervals)]; selection=('zombie_0.ogg',start,end)
        elif kind=='bat':
            n=(i+j)%3; selection=(f'bat/flac/bat_{n+1:02d}.flac',.04,(1.429,1.747,1.361)[n])
        elif kind=='crow': selection=('crow_0.wav',.025,.546)
        elif kind=='bee': selection=('bee/bee.wav',.12,1.45 if long_voice else .75)
        elif kind=='fireworks':
            # Use the quieter recorded decay/fizz, not a bang on the vocal attack.
            selection=(f'fireworks/fw_{(i+j)%6+1:02d}.ogg',.15,1.65)
        else: raise ValueError(kind)
        return self.phrase(selection,.055 if kind in ('bones','breath','crow') else .16,.20)
    def render(self,profile,i):
        species,wolf_phrase,layers=profile
        dest=PROJECT/'src/main/resources/assets/wolfism/sounds/entity'/species
        dest.mkdir(parents=True,exist_ok=True); jobs=[]
        for j,action in enumerate(ACTIONS):
            long_voice=action=='howl1'
            if long_voice: selection=WOLF_PHRASES[wolf_phrase]
            elif action=='ambient1': selection=GROWLS[(i//3)%2]
            elif action in ('ambient2','whine1','death1'): selection=WHINES[(i+j)%len(WHINES)]
            elif action.startswith('growl'): selection=GROWLS[(i+j)%len(GROWLS)]
            else: selection=BARKS[(i//2)%len(BARKS)]
            lead=.028 if action=='hurt1' else .075
            attack=.16 if long_voice else (.025 if action=='hurt1' else .055)
            release=.42 if long_voice else (.09 if action=='hurt1' else .15)
            main,part=self.phrase(selection,attack,release)
            # Small differences in level retain dynamics without changing voice.
            target=(-24 if action.startswith('ambient') else -23 if long_voice else -21.8)
            target+=(((i*7+j*3)%17)-8)*.055
            main=gain_to(main,target,-6); parts=[(main,lead)]
            constituents=[dict(role='wolf_field_voice' if selection[0].startswith('wolf_') else 'natural_canine_close_voice',
                delay_seconds=lead,**part)]
            for k,kind in enumerate(filter(None,layers.split('+'))):
                layer,info=self.theme(kind,i,j,long_voice)
                # Never double the same canine sample against itself: that
                # causes comb filtering and a conspicuously robotic texture.
                if info['source']==selection[0]: continue
                rel=-19 if kind=='zombie' else -22
                if action in ('ambient1','ambient2','hurt1','whine1'): rel-=3
                ceiling=-29 if kind in ('bell','bones','fireworks') else -26
                layer=gain_to(layer,target+rel,ceiling)
                at=lead+(.42 if long_voice else .16)+k*.11
                allowed=round((len(main)/RATE+.45-(at-lead))*RATE)
                layer=envelope(layer[:max(1,allowed)],.04,.18)
                parts.append((layer,at))
                constituents.append(dict(role='natural_canine_rasp' if kind=='breath' else kind,
                    relative_rms_db=rel,peak_ceiling_dbfs=ceiling,delay_seconds=round(at,5),
                    mixed_seconds=round(len(layer)/RATE,5),**info))
            length=max(len(x)+round(at*RATE) for x,at in parts)+round(.085*RATE)
            mix=np.zeros(length,dtype=np.float32)
            for x,at in parts:
                offset=round(at*RATE); mix[offset:offset+len(x)]+=x
            # Constant peak gain only: never compression, clipping or limiting
            # that changes a waveform's shape. Silence protects file boundaries.
            peak=float(np.max(np.abs(mix)))
            if peak>10**(-5.5/20): mix*=10**(-5.5/20)/peak
            path=dest/(action+'.ogg')
            record=dict(species=species,action=action,file=path.relative_to(PROJECT).as_posix(),
                constituents=constituents,target_main_rms_dbfs=round(target,3),
                leading_silence_seconds=lead,trailing_silence_seconds=.085,
                pitch_shift_semitones=0,time_stretch_ratio=1,peak_ceiling_dbfs=-5.5)
            jobs.append((path,mix,record))
        return jobs

def decode(path,ffmpeg):
    raw=subprocess.run([ffmpeg,'-v','error','-i',str(path),'-ac','1','-ar',str(RATE),'-f','f32le','-'],
        capture_output=True,check=True).stdout
    return np.frombuffer(raw,dtype='<f4'),raw

def metrics(x):
    start=x[:round(.1*RATE)]; tail=x[-round(.02*RATE):]
    return dict(sample_frames=len(x),duration_seconds=round(len(x)/RATE,5),rms_dbfs=db(rms(x)),
        peak_dbfs=db(np.max(np.abs(x))),clipped_samples=int(np.count_nonzero(np.abs(x)>=.999)),
        first_sample_abs=float(abs(x[0])),last_sample_abs=float(abs(x[-1])),
        first_15ms_peak_dbfs=db(np.max(np.abs(x[:round(.015*RATE)]))),
        onset_100ms_peak_dbfs=db(np.max(np.abs(start))),
        onset_100ms_max_sample_step_dbfs=db(np.max(np.abs(np.diff(start)))),
        tail_20ms_peak_dbfs=db(np.max(np.abs(tail))))

def write_ogg(job,ffmpeg):
    path,x,record=job
    # Higher quality than the first pass's 24 kHz/q0.40 files.
    # Bounded writes also avoid large native encoder stack allocations on
    # Windows for the complete, longer wolf howls.
    with sf.SoundFile(path,'w',samplerate=RATE,channels=1,format='OGG',subtype='VORBIS',compression_level=.70) as encoded:
        for first in range(0,len(x),4096):
            encoded.write(x[first:first+4096])
    decoded,raw=decode(path,ffmpeg)
    record.update(sha256=hashlib.sha256(path.read_bytes()).hexdigest(),
        decoded_pcm_sha256=hashlib.sha256(raw).hexdigest(),size_bytes=path.stat().st_size,**metrics(decoded))
    return record

def write_preview(jobs,path,ffmpeg):
    chunks=[]; cues=[]; elapsed=0
    for species in PREVIEW_SPECIES:
        ogg=next(p for p,x,r in jobs if r['species']==species and r['action']=='howl1')
        x,_=decode(ogg,ffmpeg)
        cues.append(dict(species=species,action='howl1',start_seconds=round(elapsed,3),duration_seconds=round(len(x)/RATE,3)))
        chunks.extend((x,np.zeros(round(.6*RATE),dtype=np.float32))); elapsed+=len(x)/RATE+.6
    path.parent.mkdir(parents=True,exist_ok=True)
    with wave.open(str(path),'wb') as wav:
        wav.setnchannels(1); wav.setsampwidth(2); wav.setframerate(RATE)
        wav.writeframes((np.concatenate(chunks)*32767).astype('<i2').tobytes())
    path.with_suffix('.json').write_text(json.dumps(cues,indent=2),encoding='utf-8')

def main():
    parser=argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--source-dir',type=Path,required=True)
    parser.add_argument('--ffmpeg',default=shutil.which('ffmpeg'))
    parser.add_argument('--preview',type=Path)
    parser.add_argument('--preview-only',action='store_true',help='Render six palettes first for human feedback.')
    parser.add_argument('--compare-dir',type=Path,help='Previous sounds/entity directory; read only.')
    args=parser.parse_args()
    if not args.ffmpeg: parser.error('Install FFmpeg or supply --ffmpeg PATH')
    profiles=[line.split(',') for line in PROFILES.strip().splitlines()]
    names=re.findall(r'registerEntityType\(\s*"([^"]+)"',(PROJECT/'src/main/java/net/ronm19/wolfism/registry/ModEntities.java').read_text(encoding='utf-8-sig'))
    registered={n for n in names if n.endswith('_wolf') or n=='wolf_king'}
    assert {p[0] for p in profiles}==registered and len(profiles)==60
    renderer=Renderer(args); jobs=[]
    for i,profile in enumerate(profiles):
        if not args.preview_only or profile[0] in PREVIEW_SPECIES: jobs.extend(renderer.render(profile,i))
    with concurrent.futures.ThreadPoolExecutor(max_workers=4) as pool:
        records=list(pool.map(lambda job:write_ogg(job,args.ffmpeg),jobs))
    if args.preview: write_preview(jobs,args.preview,args.ffmpeg)
    if args.preview_only:
        print(f'Preview ready: {args.preview}; {len(records)} revised files; full-production manifest not replaced.')
        return
    out=PROJECT/'tools/audio'
    (out/'audio_recipes.json').write_text(json.dumps(dict(version=VERSION,sample_rate=RATE,channels=1,
        codec='Vorbis; libsndfile compression_level 0.70',
        profiles=[dict(zip(('species','wolf_phrase','layers'),p)) for p in profiles],
        phrase_bank=WOLF_PHRASES,clips=records),indent=2),encoding='utf-8')
    report=dict(version=VERSION,species=len(profiles),files=len(records),sample_rate=RATE,channels=1,
        distinct_decoded_clips=len({r['decoded_pcm_sha256'] for r in records}),
        decoded_source_files=len({c['source'] for r in records for c in r['constituents']}),
        bytes=sum(r['size_bytes'] for r in records),
        duration_range=[min(r['duration_seconds'] for r in records),max(r['duration_seconds'] for r in records)],
        rms_dbfs_range=[min(r['rms_dbfs'] for r in records),max(r['rms_dbfs'] for r in records)],
        decoded_sample_frames=sum(r['sample_frames'] for r in records),
        max_decoded_peak_dbfs=max(r['peak_dbfs'] for r in records),clipped_samples=sum(r['clipped_samples'] for r in records),
        max_first_15ms_peak_dbfs=max(r['first_15ms_peak_dbfs'] for r in records),
        max_onset_100ms_sample_step_dbfs=max(r['onset_100ms_max_sample_step_dbfs'] for r in records),
        max_tail_20ms_peak_dbfs=max(r['tail_20ms_peak_dbfs'] for r in records),
        rate_ratios=sorted({c['rate_ratio'] for r in records for c in r['constituents']}),
        notes=['All voice and theme sources keep original pitch, speed and internal timing.',
            'No artificial chorus, doubled voices, spectral denoiser, gate, FFT equalizer or synthesized voice.',
            'Long howls are real wolf recordings; close everyday cues are natural canine recordings.',
            'Measurements identify digital discontinuities; they cannot prove subjective naturalness.',
            'No auditory QA is claimed; supplied preview requires human listening.'])
    assert report['files']==480 and report['clipped_samples']==0 and report['max_decoded_peak_dbfs']<-4.5
    assert report['max_first_15ms_peak_dbfs']<-75 and report['max_tail_20ms_peak_dbfs']<-80
    if args.compare_dir:
        comparisons=[]
        for r in records:
            old,_=decode(args.compare_dir/r['species']/(r['action']+'.ogg'),args.ffmpeg); before=metrics(old)
            comparisons.append(dict(species=r['species'],action=r['action'],before=before,
                after={k:r[k] for k in before},
                onset_step_change_db=round(r['onset_100ms_max_sample_step_dbfs']-before['onset_100ms_max_sample_step_dbfs'],3)))
        (out/'audio_onset_comparison.json').write_text(json.dumps(comparisons,indent=2),encoding='utf-8')
        changes=[r['onset_step_change_db'] for r in comparisons]
        report['previous_version_comparison']=dict(files=len(changes),median_onset_step_change_db=round(float(np.median(changes)),3),
            onset_step_improved=sum(c<0 for c in changes),
            max_old_onset_step_dbfs=max(r['before']['onset_100ms_max_sample_step_dbfs'] for r in comparisons),
            max_new_onset_step_dbfs=max(r['after']['onset_100ms_max_sample_step_dbfs'] for r in comparisons))
    (out/'audio_qa.json').write_text(json.dumps(report,indent=2),encoding='utf-8')
    print(json.dumps(report,indent=2))

if __name__=='__main__': main()
