# -*- coding: utf-8 -*-
"""补齐标题样式去边框、并把流程图字体放大（短标签版）。"""
import io, os, re

p = r"D:\app\Group10SE\student-practice-ai-evaluation-system\docs\_rbac_build\build_docx.py"
s = io.open(p, encoding="utf-8").read()

old = '    st.paragraph_format.keep_with_next = True\n'
new = ('    st.paragraph_format.keep_with_next = True\n'
       '    pPr = st.element.get_or_add_pPr()\n'
       '    for child in pPr.findall(qn("w:pBdr")):\n'
       '        pPr.remove(child)\n')
if "pBdr" not in s:
    assert old in s
    s = s.replace(old, new, 1)

io.open(p, "w", encoding="utf-8").write(s)
print("patched build_docx.py")
