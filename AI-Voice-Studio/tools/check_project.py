#!/usr/bin/env python3
"""Offline consistency checker for the Android project (no SDK needed).

Checks: XML well-formedness, resource references from XML and Java (R.x.y), manifest classes,
package/path agreement, import resolution for project classes, balanced braces, and (when a JDK with
the jdk.compiler module is present) Java *syntax* via javac parsing. It does NOT replace a real Gradle build.
"""
import os, re, sys, subprocess, shutil, tempfile
import xml.etree.ElementTree as ET

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
APP = os.path.join(ROOT, 'app', 'src', 'main')
RES = os.path.join(APP, 'res')
JAVA = os.path.join(APP, 'java')
PKG = 'com.asifqureshi.aivoicestudio'
errors, warnings = [], []
err = errors.append

# ---------- resources ----------
defined = {k: set() for k in ['string', 'color', 'dimen', 'style', 'drawable', 'layout', 'menu', 'xml', 'mipmap', 'id', 'attr']}
def collect():
    for d in sorted(os.listdir(RES)):
        p = os.path.join(RES, d)
        if not os.path.isdir(p): continue
        base = d.split('-')[0]
        for f in os.listdir(p):
            name = os.path.splitext(f)[0]
            full = os.path.join(p, f)
            if f.endswith('.xml'):
                try: tree = ET.parse(full)
                except ET.ParseError as e: err(f'XML not well-formed: {full}: {e}'); continue
                if base in ('layout', 'menu', 'xml'): defined[base].add(name)
                if base in ('drawable', 'mipmap'): defined[base].add(name)
                if base == 'values':
                    for el in tree.getroot():
                        n = el.get('name')
                        if not n: continue
                        t = el.tag
                        if t in ('string', 'color', 'dimen'): defined[t].add(n)
                        elif t == 'style': defined['style'].add(n.replace('.', '_'))
                        elif t == 'plurals': defined['string'].add(n)
                for m in re.finditer(r'@\+id/([A-Za-z0-9_]+)', open(full, encoding='utf-8').read()): defined['id'].add(m.group(1))
            elif base in ('drawable', 'mipmap'):
                defined[base].add(name)
collect()

# ---------- XML references ----------
for dp, _, fs in os.walk(RES):
    for f in fs:
        if not f.endswith('.xml'): continue
        full = os.path.join(dp, f)
        txt = open(full, encoding='utf-8').read()
        for m in re.finditer(r'@(string|color|dimen|drawable|layout|menu|xml|mipmap)/([A-Za-z0-9_.]+)', txt):
            t, n = m.group(1), m.group(2)
            if n not in defined[t] and not (t == 'color' and n in ('white',)):
                err(f'{os.path.relpath(full, ROOT)}: missing @{t}/{n}')
        for m in re.finditer(r'@style/([A-Za-z0-9_.]+)', txt):
            n = m.group(1).replace('.', '_')
            if n not in defined['style'] and not n.startswith(('Widget_Material3', 'Theme_Material3', 'TextAppearance', 'Theme_SplashScreen', 'ShapeAppearance_Material3')):
                err(f'{os.path.relpath(full, ROOT)}: missing @style/{m.group(1)}')
        for m in re.finditer(r'@id/([A-Za-z0-9_]+)', txt):
            if m.group(1) not in defined['id']: err(f'{os.path.relpath(full, ROOT)}: missing @id/{m.group(1)}')

# ---------- Java ----------
java_files = []
for dp, _, fs in os.walk(os.path.join(ROOT, 'app', 'src')):
    for f in fs:
        if f.endswith('.java'): java_files.append(os.path.join(dp, f))
classes = {}
for f in java_files:
    rel = os.path.relpath(f, os.path.join(ROOT, 'app', 'src'))
    parts = rel.split(os.sep)
    if len(parts) > 2 and parts[1] == 'java':
        fq = '.'.join(parts[2:])[:-5]
        classes[fq] = f

def strip(src):
    src = re.sub(r'/\*.*?\*/', '', src, flags=re.S)
    src = re.sub(r'"(?:\\.|[^"\\\n])*"', '""', src)
    src = re.sub(r"'(?:\\.|[^'\\\n])'", "''", src)
    src = re.sub(r'//[^\n]*', '', src)
    return src

for f in java_files:
    raw = open(f, encoding='utf-8').read()
    rel = os.path.relpath(f, ROOT)
    s = strip(raw)
    for a, b in ('{}', '()', '[]'):
        if s.count(a) != s.count(b): err(f'{rel}: unbalanced {a}{b} ({s.count(a)} vs {s.count(b)})')
    m = re.search(r'^package\s+([\w.]+);', raw, re.M)
    if not m: err(f'{rel}: no package'); continue
    parts = os.path.relpath(f, os.path.join(ROOT, 'app', 'src')).split(os.sep)
    if len(parts) > 2:
        expected = '.'.join(parts[2:-1])
        if m.group(1) != expected: err(f'{rel}: package {m.group(1)} != path {expected}')
    cn = os.path.splitext(os.path.basename(f))[0]
    if not re.search(r'\b(class|interface|enum)\s+' + cn + r'\b', s): err(f'{rel}: no type named {cn}')
    # R references
    for m in re.finditer(r'(?<![\w.])R\.(string|color|dimen|style|drawable|layout|menu|xml|mipmap|id)\.([A-Za-z0-9_]+)', s):
        t, n = m.group(1), m.group(2)
        if n not in defined[t]: err(f'{rel}: missing R.{t}.{n}')
    # project imports
    for m in re.finditer(r'^import\s+(static\s+)?([\w.]+?)(\.\*)?;', raw, re.M):
        imp = m.group(2)
        if not imp.startswith(PKG) or imp == PKG + '.R' or imp == PKG + '.BuildConfig': continue
        if m.group(3):
            nested = imp in classes or any(c.startswith(imp + '.') for c in classes)
            if not nested: err(f'{rel}: unresolved import {imp}.*')
        else:
            top = imp
            while top and top not in classes and '.' in top: top = top.rsplit('.', 1)[0]
            if top not in classes: err(f'{rel}: unresolved import {imp}')
    # findViewById ids exist somewhere
    for m in re.finditer(r'R\.id\.(\w+)', s):
        pass

# ---------- manifest ----------
mf = open(os.path.join(APP, 'AndroidManifest.xml'), encoding='utf-8').read()
for m in re.finditer(r'android:name="(\.[\w.]+)"', mf):
    fq = PKG + m.group(1)
    if fq not in classes: err(f'AndroidManifest: class {fq} not found')
for m in re.finditer(r'@(mipmap|xml|style|string|drawable)/([\w.]+)', mf):
    t, n = m.group(1), m.group(2).replace('.', '_') if m.group(1) == 'style' else m.group(2)
    if n not in defined[t]: err(f'AndroidManifest: missing @{t}/{m.group(2)}')

# ---------- javac syntax-only parse ----------
def javac_syntax():
    java = shutil.which('java')
    if not java: warnings.append('java not found: skipped javac syntax check'); return
    out = tempfile.mkdtemp()
    cmd = [java, '--module', 'jdk.compiler/com.sun.tools.javac.Main', '-proc:none', '-Xmaxerrs', '5000', '-d', out] + [f for f in java_files if '/test/' not in f]
    p = subprocess.run(cmd, capture_output=True, text=True)
    if 'module jdk.compiler not found' in p.stderr or 'Module jdk.compiler not found' in p.stderr:
        warnings.append('jdk.compiler module unavailable: skipped javac syntax check'); return
    syntax_ok = True
    resolution = re.compile(r'cannot find symbol|does not exist|cannot access|package .* does not exist|static import only|incompatible types|cannot be applied|cannot infer|is not abstract|method does not override|unreported exception|bad operand|no suitable|not applicable|has private access|cannot be dereferenced|does not take parameters|cannot be converted|non-static|may not have been|already defined|might not have been|unexpected type|reference to .* is ambiguous|cannot be referenced|incomparable')
    lines = p.stderr.splitlines()
    for i, l in enumerate(lines):
        if ': error:' in l and not resolution.search(l):
            err('javac syntax: ' + l.replace(ROOT + '/', ''))
            syntax_ok = False
    if syntax_ok: print('javac parse: no syntax errors (symbol-resolution errors against the Android SDK are expected offline and ignored)')
javac_syntax()

for w in warnings: print('WARN', w)
if errors:
    print(f'{len(errors)} problem(s):')
    for e in sorted(set(errors)): print(' -', e)
    sys.exit(1)
print('OK: resources, references, imports, manifest and syntax checks passed')
