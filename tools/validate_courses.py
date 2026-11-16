#!/usr/bin/env python3
"""
Валидатор курсов и веб-ресурсов AllTestsAreUs для CI (Jenkins).
Проверяет:
1. Корректность web-catalog/catalog.json и наличие всех перечисленных JSON-файлов.
2. Отсутствие предзагруженных тестов в APK (app/src/main/assets/courses должно быть пусто — на старте сервис пустой).
3. Отсутствие дубликатов courseId, conceptId и questionId.
4. Полноту базовых тестов (20 условных знаков карты у КП, 20 греческих глаголов группы А).
"""
import json
import os
import sys
from pathlib import Path

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
WEB_DIR = os.path.join(ROOT, "web-catalog")
ASSETS_DIR = os.path.join(ROOT, "app", "src", "main", "assets", "courses")
CATALOG_PATH = os.path.join(WEB_DIR, "catalog.json")


def main() -> int:
    if os.path.isdir(ASSETS_DIR) and any(f.endswith(".json") for f in os.listdir(ASSETS_DIR)):
        print(f"ERROR: Found preloaded courses in {ASSETS_DIR}; APK must start empty!", file=sys.stderr)
        return 1

    if not os.path.isfile(CATALOG_PATH):
        print(f"ERROR: Missing catalog.json at {CATALOG_PATH}", file=sys.stderr)
        return 1

    with open(CATALOG_PATH, "r", encoding="utf-8") as f:
        catalog = json.load(f)

    courses_refs = catalog.get("courses", [])
    if not courses_refs:
        print("ERROR: catalog.json has no courses", file=sys.stderr)
        return 1

    seen_courses = set()
    seen_concepts = set()
    seen_questions = set()

    for ref in courses_refs:
        rel_url = ref["url"]
        course_path = os.path.join(WEB_DIR, rel_url.replace("/", os.sep))
        if not os.path.isfile(course_path):
            print(f"ERROR: Course file missing: {course_path}", file=sys.stderr)
            return 1

        with open(course_path, "r", encoding="utf-8") as cf:
            course = json.load(cf)

        cid = course["courseId"]
        if cid in seen_courses:
            print(f"ERROR: Duplicate courseId: {cid}", file=sys.stderr)
            return 1
        seen_courses.add(cid)

        concept_count = 0
        course_dir = Path(course_path).resolve().parent
        web_root_path = Path(WEB_DIR).resolve()
        audio_map = {}

        def check_audio_fields(owner_label: str, item: dict) -> bool:
            a_url = (item.get("audioUrl") or "").strip()
            a_text = (item.get("audioText") or "").strip()
            if a_url:
                if not a_text:
                    print(f"ERROR: {owner_label} defines audioUrl '{a_url}' without audioText", file=sys.stderr)
                    return False
                if not a_url.startswith(("http://", "https://")):
                    resolved = (course_dir / a_url).resolve()
                    try:
                        resolved.relative_to(web_root_path)
                    except ValueError:
                        print(f"ERROR: {owner_label} audioUrl '{a_url}' escapes web-catalog root", file=sys.stderr)
                        return False
                    if resolved in audio_map and audio_map[resolved] != a_text:
                        print(f"ERROR: Conflicting audioText for {resolved}", file=sys.stderr)
                        return False
                    audio_map[resolved] = a_text
            return True

        for lesson in course.get("lessons", []):
            for concept in lesson.get("concepts", []):
                concept_count += 1
                con_id = concept["conceptId"]
                if con_id in seen_concepts:
                    print(f"ERROR: Duplicate conceptId: {con_id}", file=sys.stderr)
                    return 1
                seen_concepts.add(con_id)

                if not check_audio_fields(f"concept {con_id}", concept):
                    return 1
                if cid == "languages_greek_verbs_a_top20":
                    if not (concept.get("audioText") or "").strip() or not (concept.get("audioUrl") or "").strip():
                        print(f"ERROR: Greek concept {con_id} must define both audioText and audioUrl", file=sys.stderr)
                        return 1

                for q in concept.get("questions", []):
                    qid = q["questionId"]
                    if qid in seen_questions:
                        print(f"ERROR: Duplicate questionId: {qid}", file=sys.stderr)
                        return 1
                    seen_questions.add(qid)

                    if not check_audio_fields(f"question {qid}", q):
                        return 1

                    if q["type"] == "SINGLE_CHOICE":
                        correct_opts = [o for o in q.get("options", []) if o.get("isCorrect")]
                        if len(correct_opts) != 1:
                            print(f"ERROR: SINGLE_CHOICE {qid} must have exactly 1 correct option", file=sys.stderr)
                            return 1
                    elif q["type"] == "MULTI_CHOICE":
                        opts = q.get("options", [])
                        correct_opts = [o for o in opts if o.get("isCorrect")]
                        if len(opts) < 3 or len(correct_opts) < 2:
                            print(f"ERROR: MULTI_CHOICE {qid} must have >= 3 options and >= 2 correct options", file=sys.stderr)
                            return 1
                    elif q["type"] == "TRUE_FALSE":
                        if not isinstance(q.get("correctBool"), bool):
                            print(f"ERROR: TRUE_FALSE {qid} must define boolean 'correctBool'", file=sys.stderr)
                            return 1

        if cid == "languages_greek_verbs_a_top20":
            if course.get("speechLang") != "el-GR":
                print("ERROR: Greek verbs course must set speechLang to 'el-GR'", file=sys.stderr)
                return 1
            if concept_count < 20:
                print(f"ERROR: Greek verbs course must have >= 20 concepts, found {concept_count}", file=sys.stderr)
                return 1

        print(f"OK: {cid} (v{course['version']}, concepts={concept_count})")

    print(f"SUCCESS: Validated {len(seen_courses)} courses, {len(seen_concepts)} concepts, {len(seen_questions)} questions.")
    return 0


if __name__ == "__main__":
    sys.exit(main())
