#!/usr/bin/env python3
"""Reproducible source index for ChairX. No third-party dependencies."""
import argparse
import hashlib
import json
import re
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
JAVA = ROOT / "backend/src/main/java/kg/chairx"
SQL = ROOT / "backend/src/main/resources/db/migration"
FE = ROOT / "frontend/src"
OUT = ROOT / "docs/reference/generated"
HEADER = "<!-- Auto-generated; do not edit. See scripts/generate-codebase-reference.py -->\n\n"

def source(path):
    return path.relative_to(ROOT).as_posix()

def link(path):
    return "[" + source(path) + "](../../../" + source(path) + ")"

def balanced(text, start):
    depth, quoted = 0, None
    for idx in range(start, len(text)):
        c = text[idx]
        if quoted:
            if c == quoted:
                quoted = None
        elif c in ("'", '"'):
            quoted = c
        elif c == "(":
            depth += 1
        elif c == ")":
            depth -= 1
            if depth == 0:
                return text[start+1:idx]
    return ""

def split_fields(text):
    fields, depth, start = [], 0, 0
    for idx, c in enumerate(text):
        if c == "(":
            depth += 1
        elif c == ")":
            depth -= 1
        elif c == "," and depth == 0:
            fields.append(text[start:idx].strip())
            start = idx + 1
    fields.append(text[start:].strip())
    return [f for f in fields if f]

def migration_index():
    paths = sorted(SQL.glob("V*__*.sql"), key=lambda p:int(re.match(r"V(\d+)__",p.name).group(1)))
    tables = {}
    for p in paths:
        text = p.read_text(encoding="utf-8")
        for match in re.finditer(r"\bCREATE\s+TABLE\s+(?:IF\s+NOT\s+EXISTS\s+)?(\w+)\s*\(",text,re.I):
            name = match.group(1)
            obj = tables.setdefault(name, {"columns":{}, "relations":[], "sources":[]})
            obj["sources"].append(p)
            for piece in split_fields(balanced(text,match.end()-1)):
                field = re.match(r"^\s*(\w+)\s+(.+)",piece,re.S)
                if field and field.group(1).upper() not in {"CONSTRAINT","PRIMARY","FOREIGN","CHECK","UNIQUE"}:
                    obj["columns"][field.group(1)] = (re.sub(r"\s+"," ",field.group(2)),p)
                for ref in re.finditer(r"\bREFERENCES\s+(\w+)\s*\(([^)]+)\)",piece,re.I):
                    obj["relations"].append((ref.group(1),ref.group(2),p))
        for match in re.finditer(r"\bALTER\s+TABLE\s+(\w+)\s+ADD\s+(?:COLUMN\s+)?(?:IF\s+NOT\s+EXISTS\s+)?(\w+)\s+([^;\n]+)",text,re.I):
            name,col,definition = match.groups()
            if col.upper() in {"CONSTRAINT","CHECK","FOREIGN","PRIMARY","UNIQUE"}:
                continue
            obj = tables.setdefault(name, {"columns":{}, "relations":[], "sources":[]})
            obj["columns"][col] = (definition.strip(),p)
        for match in re.finditer(r"\bALTER\s+TABLE\s+(\w+)\s+DROP\s+COLUMN\s+(?:IF\s+EXISTS\s+)?(\w+)",text,re.I):
            if match.group(1) in tables:
                tables[match.group(1)]["columns"].pop(match.group(2),None)
        for match in re.finditer(r"\bDROP\s+TABLE\s+(?:IF\s+EXISTS\s+)?(\w+)",text,re.I):
            tables.pop(match.group(1),None)
    lines = [HEADER,"# Database data dictionary\n\n",
        "Heuristic reconstruction from Flyway SQL, not a live DB introspection. Inspect migrations for renames, dynamic statements, complex constraints and exact foreign keys. Never treat this index as authorization for data migrations.\n\n",
        "Migration files: "+str(len(paths))+". Detected tables: "+str(len(tables))+".\n\n",
        "## Foreign-key references detected\n\n| Table | Target | Source |\n|---|---|---|\n"]
    for name,obj in sorted(tables.items()):
        for target,col,path in obj["relations"]:
            lines.append("| "+name+" | "+target+"("+col+") | "+link(path)+" |\n")
    lines.append("\n## Columns\n\n")
    for name,obj in sorted(tables.items()):
        lines += ["### "+name+"\n\n","| Column | Last recognized declaration | Source |\n|---|---|---|\n"]
        for col,(definition,path) in sorted(obj["columns"].items()):
            lines.append("| "+col+" | "+definition.replace("|","/")[:220]+" | "+link(path)+" |\n")
        lines.append("\n")
    return "".join(lines), {"migrations":len(paths),"tables_detected":len(tables)}

def java_models():
    rows, enums = [], []
    for p in sorted(JAVA.rglob("*.java")):
        s = p.read_text(encoding="utf-8")
        package = re.search(r"\bpackage\s+([\w.]+);",s)
        ns = package.group(1) if package else ""
        for m in re.finditer(r"\brecord\s+(\w+)\s*\(",s):
            rows.append((ns,m.group(1),split_fields(balanced(s,m.end()-1)),p))
        for m in re.finditer(r"\benum\s+(\w+)\s*\{",s):
            body = s[m.end():].split(";",1)[0].split("}",1)[0]
            vals = re.findall(r"\b[A-Z][A-Z0-9_]*\b",body)
            enums.append((ns,m.group(1),vals,p))
    lines = [HEADER,"# Java records and enums\n\n",
        "These are Java record components and enum literals, not guaranteed JSON response schemas. Read DTO validators and mappers for contract semantics.\n\n",
        "Records: "+str(len(rows))+". Enums: "+str(len(enums))+".\n\n"]
    for ns,name,fields,p in sorted(rows):
        lines.append("### "+ns+"."+name+"\n\nSource: "+link(p)+"\n\n")
        for f in fields:
            lines.append("- "+re.sub(r"\s+"," ",f)+"\n")
        lines.append("\n")
    lines.append("## Enums\n\n| Enum | Values (heuristic) | Source |\n|---|---|---|\n")
    for ns,name,vals,p in sorted(enums):
        lines.append("| "+ns+"."+name+" | "+", ".join(vals)+" | "+link(p)+" |\n")
    return "".join(lines), {"records":len(rows),"enums":len(enums)}

def java_services():
    rows = []
    for p in sorted(JAVA.rglob("*.java")):
        if "/application/" not in p.as_posix():
            continue
        s = p.read_text(encoding="utf-8")
        methods = []
        for m in re.finditer(r"\bpublic\s+(?:static\s+)?(?:[\w<>?,.\[\] ]+?)\s+(\w+)\s*\(([^{};]*?)\)\s*(?:throws\s+[\w, ]+)?\{",s,re.S):
            methods.append((m.group(1),re.sub(r"\s+"," ",m.group(2))[:180]))
        if methods:
            rows.append((p.relative_to(JAVA).parts[0],p,methods))
    lines = [HEADER,"# Application-service public functions\n\n",
        "Static method inventory, not a call graph or transactional safety proof. Inspect code and tests for state transitions, locks, side effects and permissions.\n\n"]
    for module,p,methods in rows:
        lines.append("## "+module+" / "+p.stem+"\n\nSource: "+link(p)+"\n\n")
        for name,args in methods:
            lines.append("- "+name+"("+args+")\n")
        lines.append("\n")
    return "".join(lines), {"service_files":len(rows),"public_functions":sum(len(x[2]) for x in rows)}

def endpoints():
    verbs = {"GetMapping":"GET","PostMapping":"POST","PutMapping":"PUT","PatchMapping":"PATCH","DeleteMapping":"DELETE"}
    rows = []
    for p in sorted(JAVA.rglob("*.java")):
        s = p.read_text(encoding="utf-8")
        if "@RestController" not in s:
            continue
        cls = re.search(r"\bclass\s+(\w+)",s)
        before = s[:cls.start()] if cls else ""
        base = re.findall(r'@RequestMapping\s*\(\s*"([^"]+)"',before)
        prefix = base[-1] if base else ""
        for m in re.finditer(r"@(GetMapping|PostMapping|PutMapping|PatchMapping|DeleteMapping)\s*(?:\(\s*(?:value\s*=\s*)?\"([^\"]+)\"[^)]*\))?",s):
            if cls and m.start() < cls.start():
                continue
            next_text = s[m.end():m.end()+1000]
            method = re.search(r"\bpublic\s+[\w<>?,.\[\] ]+\s+(\w+)\s*\(",next_text)
            if not method or "{" in next_text[:method.start()]:
                continue
            full = "/" + "/".join(z.strip("/") for z in (prefix,m.group(2) or "") if z)
            rows.append((verbs[m.group(1)],full,method.group(1),p))
    lines = [HEADER,"# REST API controller source index\n\n",
        "Detected simple Spring mapping annotations. Complex arrays, composed annotations and dynamic routes may not be captured. Not a compiled OpenAPI spec. Consult SecurityConfig for permissions, DTOs for request/response, and tests for error contracts.\n\n",
        "| Method | URL | Handler | Source |\n|---|---|---|---|\n"]
    for verb,url,method,p in sorted(set(rows)):
        lines.append("| "+verb+" | "+url+" | "+p.stem+"."+method+" | "+link(p)+" |\n")
    return "".join(lines), {"endpoint_rows":len(rows)}

def frontend():
    routes = set()
    for p in [FE/"shared/lib/navigation.ts", FE/"app/router/AppRouter.tsx"]:
        if p.exists():
            routes.update(re.findall(r"\bpath\s*[:=]\s*['\"](/[^'\"]*)['\"]",p.read_text(encoding="utf-8")))
    lines = [HEADER,"# React/TypeScript frontend index\n\n",
        "Routes are recognized statically. Actual gates, redirects, lazy loading and access checks must be inspected in AppRouter.tsx and navigation.ts. UI permissions do not replace backend security.\n\n",
        "## Recognized routes\n\n"]
    lines += ["- "+p+"\n" for p in sorted(routes)]
    lines.append("\n## Feature directories\n\n| Feature | TS/TSX files |\n|---|---:|\n")
    feature_root = FE/"features"
    dirs = sorted([p for p in feature_root.iterdir() if p.is_dir()]) if feature_root.exists() else []
    for p in dirs:
        lines.append("| "+p.name+" | "+str(sum(1 for f in p.rglob("*") if f.suffix in (".ts",".tsx")))+" |\n")
    return "".join(lines), {"routes":len(routes),"features":len(dirs)}

def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--check", action="store_true")
    args = ap.parse_args()
    pages,stats = {},{}
    for name,func in [("database.md",migration_index),("models.md",java_models),("services.md",java_services),("endpoints.md",endpoints),("frontend.md",frontend)]:
        pages[name],stats[name] = func()
    digest = hashlib.sha256()
    paths = list(JAVA.rglob("*.java"))+list(SQL.glob("*.sql"))+list(FE.rglob("*.ts"))+list(FE.rglob("*.tsx"))
    for p in sorted(paths):
        digest.update(source(p).encode()+b"\0"+p.read_bytes()+b"\0")
    pages["manifest.json"] = json.dumps({"source_sha256":digest.hexdigest(),"statistics":stats,
        "generator":"scripts/generate-codebase-reference.py",
        "note":"Heuristic static inventory, not verified OpenAPI or actual live PostgreSQL schema"},
        ensure_ascii=False,indent=2)+"\n"
    missing = []
    for filename,content in pages.items():
        p = OUT/filename
        if not p.exists() or p.read_text(encoding="utf-8") != content:
            missing.append(source(p))
            if not args.check:
                p.parent.mkdir(parents=True,exist_ok=True)
                p.write_text(content,encoding="utf-8")
    if missing:
        print(("STALE: " if args.check else "GENERATED: ")+", ".join(missing))
    else:
        print("Codebase reference is current.")
    if args.check and missing:
        print("Run python3 scripts/generate-codebase-reference.py",file=sys.stderr)
        return 1
    return 0

if __name__ == "__main__":
    sys.exit(main())
