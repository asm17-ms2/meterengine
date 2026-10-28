# usage: python3 diff_of_diffs.py <반영한 층의 옛 head> <반영한 층의 새 head> <층>=<옛 head>=<새 head> ...
# 반영한 층의 변경(기준)과 위층마다의 변경을 파일별 +/- 줄로 비교해, 다르거나 빠진 파일을 보인다. 저장소 안에서 돌린다.
import subprocess, sys
def g(*a): return subprocess.run(["git", *a], capture_output=True, text=True, check=True).stdout
def changed(a, b): return [l for l in g("diff", "--name-only", a, b).splitlines() if l]
def pm(a, b, f):
    out = g("diff", "-U0", a, b, "--", f)
    return sorted(l for l in out.splitlines() if l[:1] in "+-" and not l.startswith(("+++", "---")))
base_a, base_b = sys.argv[1], sys.argv[2]
ref = set(changed(base_a, base_b))
for n, old, new in (p.split("=") for p in sys.argv[3:]):
    files = set(changed(old, new))
    flags = []
    for f in sorted(ref | files):
        if f not in files: flags.append(f + " (기준 변경에 있는데 이 층에는 없다)")
        elif f not in ref: flags.append(f + " (기준 변경 밖의 파일이 바뀌었다)")
        elif pm(base_a, base_b, f) != pm(old, new, f): flags.append(f + " (+/- 줄이 기준과 다르다)")
    print(n, old, new, len(flags))
    for x in flags: print("  " + x)
