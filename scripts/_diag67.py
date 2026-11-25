#!/usr/bin/env python3
"""临时诊断脚本（用完立即删除）：把单测日志回写到 commit comment。"""
import json
import os
import urllib.request

TOKEN = os.environ["GH_TOKEN"]
SHA = os.environ["GH_SHA"]
REPO = os.environ.get("GITHUB_REPOSITORY", "wzzhuz/offline-ledger")
log = open("/tmp/unit_test.log", errors="replace").read().splitlines()
keys = ("e: ", "error:", "FAILED", "Tests run", "FAILURE", "Caused by",
        "> Task", "expected:", "actual:", "AssertionError", "BUILD FAILED")
picked = [l for l in log if any(k in l for k in keys)]
tail = log[-120:]
body = "## 单测失败诊断（临时）\n\n### 关键行\n```\n" + \
    "\n".join(picked[-80:])[:9000] + "\n```\n\n### 尾部\n```\n" + \
    "\n".join(tail)[:9000] + "\n```"
req = urllib.request.Request(
    f"https://api.github.com/repos/{REPO}/commits/{SHA}/comments",
    headers={"Authorization": "Bearer " + TOKEN,
             "Accept": "application/vnd.github+json",
             "User-Agent": "offline-ledger-diag",
             "Content-Type": "application/json"},
    method="POST",
    data=json.dumps({"body": body}).encode(),
)
with urllib.request.urlopen(req, timeout=30) as r:
    print("comment HTTP", r.status)
