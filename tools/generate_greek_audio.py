#!/usr/bin/env python3
"""
Генератор аудиофайлов озвучки (MP3/WAV) для курсов с audioText и audioUrl.
Запускается автоматически внутри Dockerfile.web (espeak-ng -> lame MP3), но не
перезаписывает уже существующие непустые файлы (можно положить свои записи в web-catalog/audio/...).
"""

import argparse
import json
import shutil
import subprocess
import sys
import tempfile
from pathlib import Path


def collect_audio_targets(courses_dir: Path, web_root: Path):
    targets = {}
    for path in sorted(courses_dir.glob("*.json")):
        data = json.loads(path.read_text(encoding="utf-8"))
        course_list = data if isinstance(data, list) else data.get("courses", [data])
        for course in course_list:
            speech_lang = course.get("speechLang") or "el-GR"
            for lesson in course.get("lessons", []):
                for concept in lesson.get("concepts", []):
                    _add_target(targets, path.parent, web_root, concept, speech_lang)
                    for question in concept.get("questions", []):
                        _add_target(targets, path.parent, web_root, question, speech_lang)
    return targets


def _add_target(targets: dict, course_dir: Path, web_root: Path, item: dict, speech_lang: str):
    audio_url = (item.get("audioUrl") or "").strip()
    audio_text = (item.get("audioText") or "").strip()
    if not audio_url or audio_url.startswith(("http://", "https://")):
        return
    if not audio_text:
        raise ValueError(f"audioUrl '{audio_url}' does not have a matching non-empty audioText")

    resolved = (course_dir / audio_url).resolve()
    web_root_resolved = web_root.resolve()
    try:
        resolved.relative_to(web_root_resolved)
    except ValueError as exc:
        raise ValueError(f"audioUrl '{audio_url}' escapes web-catalog root: {resolved}") from exc

    if resolved in targets and targets[resolved][0] != audio_text:
        raise ValueError(
            f"Conflicting audioText for {resolved}: '{targets[resolved][0]}' vs '{audio_text}'"
        )
    targets[resolved] = (audio_text, speech_lang)


def espeak_voice_for(speech_lang: str) -> str:
    code = (speech_lang or "el-GR").split("-")[0].lower()
    return "el" if code == "el" else code


def encode_mp3(wav_path: Path, mp3_path: Path, lame: str | None, ffmpeg: str | None):
    if lame:
        subprocess.run(
            [lame, "-V", "4", "--quiet", str(wav_path), str(mp3_path)],
            check=True,
        )
    elif ffmpeg:
        subprocess.run(
            [ffmpeg, "-y", "-loglevel", "error", "-i", str(wav_path), "-codec:a", "libmp3lame", "-qscale:a", "4", str(mp3_path)],
            check=True,
        )
    else:
        raise RuntimeError("Neither 'lame' nor 'ffmpeg' was found in PATH to encode MP3.")


def main() -> int:
    parser = argparse.ArgumentParser(description="Generate missing course audio MP3/WAV files.")
    parser.add_argument(
        "--check",
        action="store_true",
        help="Only validate audioUrl/audioText mappings without running espeak-ng/lame.",
    )
    args = parser.parse_args()

    repo_root = Path(__file__).resolve().parent.parent
    web_root = repo_root / "web-catalog"
    courses_dir = web_root / "courses"

    if not courses_dir.is_dir():
        print(f"ERROR: Missing directory {courses_dir}", file=sys.stderr)
        return 1

    try:
        targets = collect_audio_targets(courses_dir, web_root)
    except ValueError as exc:
        print(f"ERROR: {exc}", file=sys.stderr)
        return 1

    if args.check:
        existing = sum(1 for p in targets if p.is_file() and p.stat().st_size > 0)
        missing = len(targets) - existing
        print(f"AUDIO CHECK: {len(targets)} targets ({existing} existing, {missing} to synthesize)")
        return 0

    espeak = shutil.which("espeak-ng") or shutil.which("espeak")
    if not espeak:
        print(
            "ERROR: Neither 'espeak-ng' nor 'espeak' was found in PATH. Run inside Docker or install espeak-ng.",
            file=sys.stderr,
        )
        return 1

    needs_mp3 = any(p.suffix.lower() == ".mp3" for p in targets)
    lame = shutil.which("lame")
    ffmpeg = shutil.which("ffmpeg")
    if needs_mp3 and not (lame or ffmpeg):
        print(
            "ERROR: MP3 targets exist, but neither 'lame' nor 'ffmpeg' was found in PATH.",
            file=sys.stderr,
        )
        return 1

    generated = 0
    skipped = 0
    with tempfile.TemporaryDirectory() as tmp_dir:
        tmp_wav = Path(tmp_dir) / "tts.wav"
        for out_path, (text, lang) in sorted(targets.items()):
            if out_path.is_file() and out_path.stat().st_size > 0:
                skipped += 1
                continue
            out_path.parent.mkdir(parents=True, exist_ok=True)
            voice = espeak_voice_for(lang)

            if out_path.suffix.lower() == ".mp3":
                subprocess.run(
                    [espeak, "-v", voice, "-s", "135", "-w", str(tmp_wav), text],
                    check=True,
                )
                encode_mp3(tmp_wav, out_path, lame, ffmpeg)
            else:
                subprocess.run(
                    [espeak, "-v", voice, "-s", "135", "-w", str(out_path), text],
                    check=True,
                )
            generated += 1

    print(f"AUDIO GENERATED: {generated} created, {skipped} kept existing (total {len(targets)})")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
