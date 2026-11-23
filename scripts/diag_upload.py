"""临时诊断：单测失败时把关键日志回写 commit comment（用完即删）。"""
import json, os, sys, urllib.request

KEYWORDS = (
    "e: ", "error:", "FAILURE", "FAILED", "Caused by:", "AssertionError",
    "Unresolved reference", "What went wrong", "> Task", "AAPT",
    "Compilation error", "was not found", "expected:", "actual:",
    "java.lang", "org.gradle", "tests completed", "Execution failed",
    "Duplicate", "R.drawable", "Exception", "None of the following",
    "Could not", "Cannot", "Unresolved", "not found", "Task :",
)


def main():
    log = open(sys.argv[1], errors="replace").read()
    lines = log.splitlines()
    picked = [l for l in lines if any(k in l for k in KEYWORDS)]
    merged = picked[-350:]
    for l in lines[-120:]:
        if l not in merged:
            merged.append(l)
    body = (
        "临时诊断日志（sha %s / run %s）\n\n```text\n%s\n```"
        % (os.environ.get("GITHUB_SHA", "")[:12],
           os.environ.get("GITHUB_RUN_ID", ""),
           "\n".join(merged)[:58000])
    )
    repo = os.environ["GITHUB_REPOSITORY"]
    sha = os.environ["GITHUB_SHA"]
    tok = os.environ.get("DIAG_TOKEN", "")
    req = urllib.request.Request(
        "https://api.github.com/repos/%s/commits/%s/comments" % (repo, sha),
        data=json.dumps({"body": body}).encode(),
        headers={
            "Authorization": "Bearer " + tok,
            "Accept": "application/vnd.github+json",
            "Content-Type": "application/json",
            "User-Agent": "offline-ledger-diag",
        },
        method="POST",
    )
    try:
        with urllib.request.urlopen(req, timeout=30) as x:
            print("diag posted", x.status)
    except Exception as e:
        print("diag failed", e)
        try:
            print("diag response", e.read().decode(errors="replace")[:300])
        except Exception:
            pass


if __name__ == "__main__":
    main()
