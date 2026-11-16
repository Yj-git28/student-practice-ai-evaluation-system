# -*- coding: utf-8 -*-
"""让表题与表格同页、短表不跨页。"""
import io

p = r"D:\app\Group10SE\student-practice-ai-evaluation-system\docs\_rbac_build\build_docx.py"
s = io.open(p, encoding="utf-8").read()

old = """def TBL(caption, headers, rows, aligns=None, widths=None):
    CAP(caption)"""
new = """def TBL(caption, headers, rows, aligns=None, widths=None):
    CAP(caption)
    doc.paragraphs[-1].paragraph_format.keep_with_next = True"""
assert old in s
s = s.replace(old, new, 1)

old2 = """    if widths:
        for i, w in enumerate(widths):"""
new2 = """    if len(rows) <= 8:
        for row in t.rows[:-1]:
            for cell in row.cells:
                for para in cell.paragraphs:
                    para.paragraph_format.keep_with_next = True
    if widths:
        for i, w in enumerate(widths):"""
assert old2 in s
s = s.replace(old2, new2, 1)

io.open(p, "w", encoding="utf-8").write(s)
print("patched")
