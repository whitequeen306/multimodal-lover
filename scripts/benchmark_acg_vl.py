#!/usr/bin/env python3
"""
ACG 识图准确率对比：qwen3-vl-flash vs qwen3-vl-plus
用法: python scripts/benchmark_acg_vl.py
需设置环境变量 DASHSCOPE_API_KEY（或项目根目录 .env）
"""
from __future__ import annotations

import base64
import json
import os
import re
import sys
import time
import urllib.error
import urllib.request
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
API_URL = "https://dashscope.aliyuncs.com/compatible-mode/v1/chat/completions"
MODELS = ["qwen3-vl-flash", "qwen3-vl-plus"]

VISION_PROMPT = """客观识图，为后续 AI 对话提供结构化信息。只输出纯 JSON，不要 markdown 代码块。
用户文字意图：识别图中动漫/游戏角色是谁

scene 只能从下列选一个（英文小写）：
anime_game, daily_life, selfie, food, screenshot_text, scenery, other
intent 只能从：identify_character, read_text, describe_scene, evaluate, general
quality 只能从：ok, low, unreadable

description 规则（200 字内）：
1. 动漫/游戏人物 → 作品名+角色名；不确定写「疑似×××」；禁止编造
2. 人物外貌、服装、表情
3. 场景与关键物品
4. 图中文字 OCR
第三人称客观描述，不要角色扮演。

单图 JSON 格式：
{"scene":"anime_game","confidence":0.92,"quality":"ok","intent":"identify_character","description":"..."}
"""

CASES = [
    {
        "id": "junko-portrait",
        "path": ROOT / "frontend/public/avatars/junko-enoshima.jpeg",
        "expected_scene": "anime_game",
        "keywords": ["江之岛", "盾子", "弹丸"],
        "user_hint": "这是谁？",
    },
    {
        "id": "junko-scene-only",
        "path": ROOT / "frontend/public/avatars/junko-enoshima.jpeg",
        "expected_scene": "anime_game",
        "keywords": ["江之岛", "盾子"],
        "user_hint": "",
    },
]


def load_env() -> None:
    env_file = ROOT / ".env"
    if not env_file.exists():
        return
    for line in env_file.read_text(encoding="utf-8").splitlines():
        line = line.strip()
        if not line or line.startswith("#") or "=" not in line:
            continue
        key, _, value = line.partition("=")
        os.environ.setdefault(key.strip(), value.strip())


def guess_mime(path: Path) -> str:
    ext = path.suffix.lower()
    return {
        ".jpg": "image/jpeg",
        ".jpeg": "image/jpeg",
        ".png": "image/png",
        ".webp": "image/webp",
        ".gif": "image/gif",
    }.get(ext, "image/jpeg")


def parse_json_text(text: str) -> dict:
    text = text.strip()
    if text.startswith("```"):
        text = re.sub(r"^```(?:json)?\s*", "", text)
        text = re.sub(r"\s*```$", "", text)
    return json.loads(text)


def call_vl(api_key: str, model: str, image_path: Path, user_hint: str) -> dict:
    raw = image_path.read_bytes()
    mime = guess_mime(image_path)
    data_url = f"data:{mime};base64,{base64.b64encode(raw).decode()}"
    hint_line = f"\n用户文字意图：{user_hint}" if user_hint else ""
    prompt = VISION_PROMPT.replace(
        "用户文字意图：识别图中动漫/游戏角色是谁",
        f"用户文字意图：{user_hint or '识别图中动漫/游戏角色'}",
    )

    body = {
        "model": model,
        "messages": [
            {
                "role": "user",
                "content": [
                    {"type": "text", "text": prompt},
                    {"type": "image_url", "image_url": {"url": data_url}},
                ],
            }
        ],
        "max_tokens": 384,
        "temperature": 0.2,
        "enable_thinking": False,
    }
    req = urllib.request.Request(
        API_URL,
        data=json.dumps(body).encode("utf-8"),
        headers={
            "Authorization": f"Bearer {api_key}",
            "Content-Type": "application/json",
        },
        method="POST",
    )
    with urllib.request.urlopen(req, timeout=90) as resp:
        payload = json.loads(resp.read().decode("utf-8"))
    content = payload["choices"][0]["message"]["content"]
    parsed = parse_json_text(content)
    return {
        "raw": content,
        "scene": parsed.get("scene", ""),
        "intent": parsed.get("intent", ""),
        "quality": parsed.get("quality", ""),
        "confidence": parsed.get("confidence"),
        "description": parsed.get("description", ""),
    }


def score_case(result: dict, case: dict) -> dict:
    scene_ok = result.get("scene") == case["expected_scene"]
    desc = result.get("description", "")
    kw_hits = [k for k in case["keywords"] if k in desc]
    id_ok = len(kw_hits) >= 1
    return {
        "scene_ok": scene_ok,
        "id_ok": id_ok,
        "kw_hits": kw_hits,
    }


def main() -> int:
    load_env()
    api_key = os.environ.get("DASHSCOPE_API_KEY", "").strip()
    if not api_key:
        print("ERROR: DASHSCOPE_API_KEY not set", file=sys.stderr)
        return 1

    results = {m: [] for m in MODELS}
    for model in MODELS:
        print(f"\n=== Model: {model} ===")
        for case in CASES:
            path = case["path"]
            if not path.exists():
                print(f"SKIP {case['id']}: missing {path}")
                continue
            try:
                t0 = time.time()
                out = call_vl(api_key, model, path, case["user_hint"])
                elapsed = round(time.time() - t0, 2)
                scores = score_case(out, case)
                row = {
                    "id": case["id"],
                    "expected_scene": case["expected_scene"],
                    "keywords": case["keywords"],
                    "user_hint": case["user_hint"],
                    **out,
                    **scores,
                    "latency_s": elapsed,
                }
                results[model].append(row)
                print(
                    f"  [{case['id']}] scene={out['scene']} "
                    f"scene_ok={scores['scene_ok']} id_ok={scores['id_ok']} "
                    f"kw={scores['kw_hits']} {elapsed}s"
                )
                print(f"    desc: {out['description'][:120]}...")
            except (urllib.error.HTTPError, urllib.error.URLError, json.JSONDecodeError, KeyError) as e:
                print(f"  [{case['id']}] FAILED: {e}")
                results[model].append({"id": case["id"], "error": str(e)})

    out_path = ROOT / "scripts" / "benchmark_acg_results.json"
    out_path.write_text(json.dumps(results, ensure_ascii=False, indent=2), encoding="utf-8")
    print(f"\nResults written to {out_path}")

    for model in MODELS:
        rows = [r for r in results[model] if "error" not in r]
        if not rows:
            continue
        scene_acc = sum(1 for r in rows if r.get("scene_ok")) / len(rows)
        id_acc = sum(1 for r in rows if r.get("id_ok")) / len(rows)
        avg_lat = sum(r.get("latency_s", 0) for r in rows) / len(rows)
        print(f"{model}: scene={scene_acc:.0%} id={id_acc:.0%} avg_latency={avg_lat:.2f}s")

    return 0


if __name__ == "__main__":
    raise SystemExit(main())
