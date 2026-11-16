# -*- coding: utf-8 -*-
"""生成《权限模块（RBAC）开发实施步骤说明》DOCX。"""
import os
from docx import Document
from docx.shared import Pt, Cm, RGBColor
from docx.enum.text import WD_ALIGN_PARAGRAPH, WD_LINE_SPACING, WD_BREAK
from docx.enum.section import WD_SECTION
from docx.enum.table import WD_TABLE_ALIGNMENT
from docx.oxml.ns import qn
from docx.oxml import OxmlElement

BUILD = r"D:\app\Group10SE\student-practice-ai-evaluation-system\docs\_rbac_build"
OUT = r"D:\app\Group10SE\student-practice-ai-evaluation-system\docs\权限模块RBAC开发实施步骤说明.docx"
FLOW = os.path.join(BUILD, "flow_login_auth.png")

HEI, SONG, KAI, WEST = "黑体", "宋体", "楷体", "Times New Roman"


THEME_ATTRS = ["w:asciiTheme", "w:hAnsiTheme", "w:eastAsiaTheme", "w:cstheme"]


def _clean_rfonts(rFonts):
    for attr in THEME_ATTRS:
        if rFonts.get(qn(attr)) is not None:
            del rFonts.attrib[qn(attr)]


def set_run_font(run, cn, size, bold=False, color=None):
    run.font.name = WEST
    run.font.size = Pt(size)
    run.font.bold = bold
    run.font.color.rgb = color if color else RGBColor(0, 0, 0)
    rPr = run._element.get_or_add_rPr()
    rFonts = rPr.find(qn("w:rFonts"))
    if rFonts is None:
        rFonts = OxmlElement("w:rFonts")
        rPr.append(rFonts)
    _clean_rfonts(rFonts)
    rFonts.set(qn("w:ascii"), WEST)
    rFonts.set(qn("w:hAnsi"), WEST)
    rFonts.set(qn("w:eastAsia"), cn)


def style_font(style, cn, size, bold=False):
    style.font.name = WEST
    style.font.size = Pt(size)
    style.font.bold = bold
    style.font.color.rgb = RGBColor(0, 0, 0)
    rPr = style.element.get_or_add_rPr()
    rFonts = rPr.find(qn("w:rFonts"))
    if rFonts is None:
        rFonts = OxmlElement("w:rFonts")
        rPr.append(rFonts)
    _clean_rfonts(rFonts)
    rFonts.set(qn("w:ascii"), WEST)
    rFonts.set(qn("w:hAnsi"), WEST)
    rFonts.set(qn("w:eastAsia"), cn)
    # 同时清理主题字体在样式定义中的残留
    _clean_rfonts(rFonts)


doc = Document()

# ---------- 页面 ----------
for s in doc.sections:
    s.page_width, s.page_height = Cm(21), Cm(29.7)
    s.top_margin = s.bottom_margin = s.left_margin = s.right_margin = Cm(2.5)

# ---------- 样式 ----------
styles = doc.styles
normal = styles["Normal"]
style_font(normal, SONG, 12)
pf = normal.paragraph_format
pf.line_spacing_rule = WD_LINE_SPACING.ONE_POINT_FIVE
pf.space_before = Pt(0)
pf.space_after = Pt(0)
pf.first_line_indent = Pt(24)
pf.alignment = WD_ALIGN_PARAGRAPH.JUSTIFY

for name, cn, size, before, after in [
    ("Title", HEI, 18, 0, 18),
    ("Heading 1", HEI, 16, 14, 6),
    ("Heading 2", HEI, 14, 11, 5),
    ("Heading 3", HEI, 12, 9, 4),
]:
    st = styles[name]
    style_font(st, cn, size, bold=(name != "Title") if name.startswith("Heading") else True)
    st.paragraph_format.first_line_indent = Pt(0)
    st.paragraph_format.space_before = Pt(before)
    st.paragraph_format.space_after = Pt(after)
    st.paragraph_format.line_spacing_rule = WD_LINE_SPACING.SINGLE
    st.paragraph_format.alignment = WD_ALIGN_PARAGRAPH.LEFT
    st.paragraph_format.keep_with_next = True
    pPr = st.element.get_or_add_pPr()
    for child in pPr.findall(qn("w:pBdr")):
        pPr.remove(child)

# 表格文字样式
tt = styles.add_style("Table Text", 1)
tt.base_style = styles["Normal"]
style_font(tt, SONG, 10.5)
tt.paragraph_format.first_line_indent = Pt(0)
tt.paragraph_format.left_indent = Pt(0)
tt.paragraph_format.right_indent = Pt(0)
tt.paragraph_format.space_before = Pt(2)
tt.paragraph_format.space_after = Pt(2)
tt.paragraph_format.line_spacing_rule = WD_LINE_SPACING.SINGLE
tt.paragraph_format.alignment = WD_ALIGN_PARAGRAPH.LEFT

cap = styles.add_style("Fig Caption", 1)
cap.base_style = styles["Normal"]
style_font(cap, SONG, 10.5)
cap.paragraph_format.first_line_indent = Pt(0)
cap.paragraph_format.alignment = WD_ALIGN_PARAGRAPH.CENTER
cap.paragraph_format.space_before = Pt(4)
cap.paragraph_format.space_after = Pt(6)
cap.paragraph_format.line_spacing_rule = WD_LINE_SPACING.SINGLE


def H(level, text):
    doc.add_paragraph(text, style="Heading %d" % level)


def P(text, indent=True, bold_prefix=None):
    p = doc.add_paragraph(style="Normal")
    if not indent:
        p.paragraph_format.first_line_indent = Pt(0)
    if bold_prefix:
        r = p.add_run(bold_prefix)
        set_run_font(r, HEI, 12, bold=True)
    r = p.add_run(text)
    set_run_font(r, SONG, 12)
    return p


def CAP(text):
    p = doc.add_paragraph(text, style="Fig Caption")
    for r in p.runs:
        set_run_font(r, SONG, 10.5)
    return p


def FIG(path, width_cm):
    p = doc.add_paragraph()
    p.paragraph_format.first_line_indent = Pt(0)
    p.paragraph_format.alignment = WD_ALIGN_PARAGRAPH.CENTER
    p.paragraph_format.space_before = Pt(6)
    p.paragraph_format.space_after = Pt(0)
    p.paragraph_format.keep_with_next = True
    p.add_run().add_picture(path, width=Cm(width_cm))


ALIGN_MAP = {"l": WD_ALIGN_PARAGRAPH.LEFT, "c": WD_ALIGN_PARAGRAPH.CENTER, "r": WD_ALIGN_PARAGRAPH.RIGHT}


def TBL(caption, headers, rows, aligns=None, widths=None):
    CAP(caption)
    doc.paragraphs[-1].paragraph_format.keep_with_next = True
    t = doc.add_table(rows=1, cols=len(headers))
    t.style = styles["Table Grid"]
    t.alignment = WD_TABLE_ALIGNMENT.CENTER
    t.autofit = True
    aligns = aligns or ["c"] + ["l"] * (len(headers) - 1)
    # 表头
    hdr = t.rows[0]
    for i, h in enumerate(headers):
        cell = hdr.cells[i]
        cell.text = ""
        p = cell.paragraphs[0]
        p.style = tt
        p.alignment = WD_ALIGN_PARAGRAPH.CENTER
        r = p.add_run(str(h))
        set_run_font(r, HEI, 10.5, bold=True)
        shd = OxmlElement("w:shd")
        shd.set(qn("w:val"), "clear")
        shd.set(qn("w:color"), "auto")
        shd.set(qn("w:fill"), "F2F2F2")
        cell._tc.get_or_add_tcPr().append(shd)
    trPr = hdr._tr.get_or_add_trPr()
    th = OxmlElement("w:tblHeader")
    trPr.append(th)
    cs = OxmlElement("w:cantSplit")
    trPr.append(cs)
    # 数据
    for row in rows:
        cells = t.add_row().cells
        for i, val in enumerate(row):
            cells[i].text = ""
            p = cells[i].paragraphs[0]
            p.style = tt
            p.alignment = ALIGN_MAP[aligns[i]] if i < len(aligns) else WD_ALIGN_PARAGRAPH.LEFT
            r = p.add_run(str(val))
            set_run_font(r, SONG, 10.5)
        trPr = t.rows[-1]._tr.get_or_add_trPr()
        cs = OxmlElement("w:cantSplit")
        trPr.append(cs)
    if len(rows) <= 8:
        for row in t.rows[:-1]:
            for cell in row.cells:
                for para in cell.paragraphs:
                    para.paragraph_format.keep_with_next = True
    if widths:
        for i, w in enumerate(widths):
            for row in t.rows:
                row.cells[i].width = Cm(w)
    # 表后空行
    sp = doc.add_paragraph()
    sp.paragraph_format.space_after = Pt(0)
    sp.paragraph_format.first_line_indent = Pt(0)
    sp.paragraph_format.line_spacing_rule = WD_LINE_SPACING.SINGLE
    for r in sp.runs:
        r.font.size = Pt(6)


def page_break():
    doc.add_paragraph().add_run().add_break(WD_BREAK.PAGE)


# =====================================================================
# 封面
# =====================================================================
for _ in range(4):
    p = doc.add_paragraph()
    p.paragraph_format.first_line_indent = Pt(0)

p = doc.add_paragraph(style="Title")
p.alignment = WD_ALIGN_PARAGRAPH.CENTER
r = p.add_run("权限模块（RBAC）开发实施步骤说明")
set_run_font(r, HEI, 18, bold=True)

p = doc.add_paragraph()
p.alignment = WD_ALIGN_PARAGRAPH.CENTER
p.paragraph_format.first_line_indent = Pt(0)
p.paragraph_format.space_after = Pt(12)
r = p.add_run("大学生软件实训教学 AI 检查评价系统 · 第 10 组")
set_run_font(r, KAI, 12)

for _ in range(6):
    p = doc.add_paragraph()
    p.paragraph_format.first_line_indent = Pt(0)

for line in ["适用范围：用户与权限管理（第二至第七步）", "版本：v1.0", "日期：2026 年 11 月 12 日"]:
    p = doc.add_paragraph()
    p.alignment = WD_ALIGN_PARAGRAPH.CENTER
    p.paragraph_format.first_line_indent = Pt(0)
    r = p.add_run(line)
    set_run_font(r, SONG, 12)

# =====================================================================
# 目录（单独一节）
# =====================================================================
sec_toc = doc.add_section(WD_SECTION.NEW_PAGE)
sec_toc.top_margin = sec_toc.bottom_margin = sec_toc.left_margin = sec_toc.right_margin = Cm(2.5)

p = doc.add_paragraph()
p.alignment = WD_ALIGN_PARAGRAPH.CENTER
p.paragraph_format.first_line_indent = Pt(0)
p.paragraph_format.space_after = Pt(12)
r = p.add_run("目　　录")
set_run_font(r, HEI, 16, bold=True)

p = doc.add_paragraph()
p.paragraph_format.first_line_indent = Pt(0)
p.paragraph_format.line_spacing_rule = WD_LINE_SPACING.SINGLE
fld_begin = OxmlElement("w:fldChar")
fld_begin.set(qn("w:fldCharType"), "begin")
instr = OxmlElement("w:instrText")
instr.set(qn("xml:space"), "preserve")
instr.text = 'TOC \\o "1-2" \\h \\z \\u'
fld_sep = OxmlElement("w:fldChar")
fld_sep.set(qn("w:fldCharType"), "separate")
txt = OxmlElement("w:t")
txt.text = "右键选择“更新域”生成目录"
fld_end = OxmlElement("w:fldChar")
fld_end.set(qn("w:fldCharType"), "end")
run = p.add_run()
run._r.append(fld_begin)
run._r.append(instr)
run._r.append(fld_sep)
run._r.append(txt)
run._r.append(fld_end)

# =====================================================================
# 正文（单独一节，页码从 1 开始）
# =====================================================================
sec_body = doc.add_section(WD_SECTION.NEW_PAGE)
sec_body.top_margin = sec_body.bottom_margin = sec_body.left_margin = sec_body.right_margin = Cm(2.5)
sectPr = sec_body._sectPr
pgNum = OxmlElement("w:pgNumType")
pgNum.set(qn("w:start"), "1")
sectPr.append(pgNum)
sec_body.footer.is_linked_to_previous = False
fp = sec_body.footer.paragraphs[0]
fp.alignment = WD_ALIGN_PARAGRAPH.CENTER
fp.paragraph_format.first_line_indent = Pt(0)
fr = fp.add_run()
set_run_font(fr, SONG, 10.5)
f1 = OxmlElement("w:fldChar")
f1.set(qn("w:fldCharType"), "begin")
it = OxmlElement("w:instrText")
it.set(qn("xml:space"), "preserve")
it.text = " PAGE "
f2 = OxmlElement("w:fldChar")
f2.set(qn("w:fldCharType"), "end")
fr._r.append(f1)
fr._r.append(it)
fr._r.append(f2)

exec(open(os.path.join(BUILD, "content.py"), encoding="utf-8").read())

doc.save(OUT)
print("saved:", OUT)
