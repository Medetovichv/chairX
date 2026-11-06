#!/usr/bin/env python3
"""Offline integrity, local-link and Python syntax checks for vendored skills."""
import ast
import hashlib
import json
from pathlib import Path
import re
from urllib.parse import unquote, urlsplit

root = Path(__file__).resolve().parent
lock = json.loads((root / 'upstream-lock.json').read_text())
errors = []
files = links = patches = 0
for skill in lock['skills']:
    base = root / skill['name']
    expected = dict(skill['upstreamFiles'])
    expected.update(skill['localModifications'])
    expected.update(skill['additionalFiles'])
    patches += len(skill['localModifications'])
    actual = {str(p.relative_to(base)) for p in base.rglob('*') if p.is_file()}
    for name in sorted(actual ^ expected.keys()):
        errors.append(f'{skill["name"]}: unexpected or missing file {name}')
    for name, digest in expected.items():
        p = base / name
        if not p.is_file():
            continue
        files += 1
        if p.is_symlink() or hashlib.sha256(p.read_bytes()).hexdigest() != digest:
            errors.append(f'{p.relative_to(root)}: digest mismatch or symlink')
        if p.suffix == '.py':
            ast.parse(p.read_text(), filename=str(p))
        if p.suffix in ('.md', '.toml'):
            for target in re.findall(r'\[[^\]]*\]\(([^\s)]+)(?:\s+[^)]*)?\)', p.read_text()):
                u = urlsplit(target)
                if u.scheme or u.netloc or not u.path:
                    continue
                links += 1
                if not (p.parent / unquote(u.path)).is_file():
                    errors.append(f'{p.relative_to(root)}: broken local link {target}')
    skill_md = (base / 'SKILL.md').read_text()
    if not skill_md.startswith('---\n') or not re.search(r'^name: .+', skill_md, re.M) or not re.search(r'^description: .+', skill_md, re.M):
        errors.append(f'{skill["name"]}: missing skill metadata')
if errors:
    raise SystemExit('\n'.join(errors))
print(f'OK: {len(lock["skills"])} skills, {files} pinned files, {patches} documented local patches, {links} local links; Python syntax valid')
