# -*- coding: utf-8 -*-
"""
Construye el informe tecnico de la mini-app Axolote (tamagochi) como DOCX
listo para exportar a PDF.

Uso:  python scripts/build-axolote-report.py
"""
import os
import re

from docx import Document
from docx.enum.section import WD_SECTION
from docx.enum.table import WD_TABLE_ALIGNMENT
from docx.enum.text import WD_ALIGN_PARAGRAPH, WD_BREAK, WD_TAB_ALIGNMENT
from docx.oxml import OxmlElement
from docx.oxml.ns import qn
from docx.shared import Mm, Pt, RGBColor

# --------------------------------------------------------------------------- paleta
INK = "10161C"
MUTED = "5D6A78"
LINE = "E4E8F0"
CORAL = "FF6B4A"
CORAL_SOFT = "FFE3EA"
TEAL = "0E7C66"
TEAL_SOFT = "D7F1E9"
AMBER = "B26A00"
AMBER_SOFT = "FFF1D6"
RED = "B3261E"
RED_SOFT = "FDE7E5"
BLUE = "2F5E8F"
BLUE_SOFT = "F3F7FB"
PANEL = "F7F9FC"
CODE_INK = "24303C"

SANS = "Segoe UI"
MONO = "Consolas"

CONTENT_W = 176          # mm utiles
BASE = 9.5               # pt cuerpo

OUT = r".tmp\axolote-report\Axolote-Informe-Tecnico.docx"
ART = r".tmp\axolote-report\axolote.png"


# --------------------------------------------------------------------------- helpers XML
def _el(tag, **attrs):
    e = OxmlElement(tag)
    for k, v in attrs.items():
        e.set(qn("w:" + k), str(v))
    return e


def shade(cell, color):
    tcPr = cell._tc.get_or_add_tcPr()
    for old in tcPr.findall(qn("w:shd")):
        tcPr.remove(old)
    tcPr.append(_el("w:shd", val="clear", color="auto", fill=color))


def cell_border(cell, edge, sz=4, color=LINE, val="single"):
    tcPr = cell._tc.get_or_add_tcPr()
    borders = tcPr.find(qn("w:tcBorders"))
    if borders is None:
        borders = OxmlElement("w:tcBorders")
        tcPr.append(borders)
    old = borders.find(qn("w:" + edge))
    if old is not None:
        borders.remove(old)
    borders.append(_el("w:" + edge, val=val, sz=sz, space=0, color=color))


def cell_margins(cell, top=0.06, bottom=0.06, left=0.16, right=0.16):
    """Margenes en cm."""
    tcPr = cell._tc.get_or_add_tcPr()
    mar = OxmlElement("w:tcMar")
    for edge, cm in (("top", top), ("bottom", bottom), ("start", left), ("end", right)):
        mar.append(_el("w:" + edge, w=int(cm * 567), type="dxa"))
    tcPr.append(mar)


def valign_top(cell):
    tcPr = cell._tc.get_or_add_tcPr()
    tcPr.append(_el("w:vAlign", val="top"))


def no_split(table):
    for row in table.rows:
        trPr = row._tr.get_or_add_trPr()
        trPr.append(_el("w:cantSplit", val="true"))


def header_row(table):
    trPr = table.rows[0]._tr.get_or_add_trPr()
    trPr.append(_el("w:tblHeader", val="true"))


TBLPR_AFTER_W = (
    "w:jc", "w:tblCellSpacing", "w:tblInd", "w:tblBorders", "w:shd", "w:tblLayout",
    "w:tblCellMar", "w:tblLook", "w:tblCaption", "w:tblDescription", "w:tblPrChange",
)

TBLPR_AFTER_BORDERS = (
    "w:shd", "w:tblLayout", "w:tblCellMar", "w:tblLook", "w:tblCaption",
    "w:tblDescription", "w:tblPrChange",
)


def table_borders_none(table):
    tblPr = table._tbl.tblPr
    old = tblPr.find(qn("w:tblBorders"))
    if old is not None:
        tblPr.remove(old)
    borders = OxmlElement("w:tblBorders")
    for edge in ("top", "left", "bottom", "right", "insideH", "insideV"):
        borders.append(_el("w:" + edge, val="nil"))
    tblPr.insert_element_before(borders, *TBLPR_AFTER_BORDERS)


def table_width(table, widths):
    """Fija el ancho de la tabla y el de cada columna en el grid (lo que respeta el renderizador)."""
    tblPr = table._tbl.tblPr
    old = tblPr.find(qn("w:tblW"))
    if old is not None:
        tblPr.remove(old)
    el = _el("w:tblW", w=int(sum(widths) * 56.7), type="dxa")
    tblPr.insert_element_before(el, *TBLPR_AFTER_W)
    for index, width in enumerate(widths):
        table.columns[index].width = Mm(width)


def para_rule(par, edge="bottom", sz=12, color=CORAL, space=4):
    pPr = par._p.get_or_add_pPr()
    pBdr = pPr.find(qn("w:pBdr"))
    if pBdr is None:
        pBdr = OxmlElement("w:pBdr")
        pPr.append(pBdr)
    pBdr.append(_el("w:" + edge, val="single", sz=sz, space=space, color=color))


def letter_space(run, twentieths):
    rPr = run._r.get_or_add_rPr()
    rPr.append(_el("w:spacing", val=twentieths))


def keep_next(par, value=True):
    par.paragraph_format.keep_with_next = value


# --------------------------------------------------------------------------- texto enriquecido
INLINE = re.compile(r"(`[^`]+`|\*[^*\s][^*]*\*)")


def _emit(par, text, size, color, bold, italic, font):
    """Emite un fragmento ya resuelto en cuanto a negrita, y resuelve codigo y cursiva."""
    for chunk in INLINE.split(text):
        if not chunk:
            continue
        if chunk.startswith("`") and chunk.endswith("`") and len(chunk) > 2:
            run = par.add_run(chunk[1:-1])
            run.font.name = MONO
            run.font.size = Pt(size * 0.9)
            run.bold = bold
            run.font.color.rgb = RGBColor.from_string(CODE_INK)
        elif chunk.startswith("*") and chunk.endswith("*") and len(chunk) > 2:
            run = par.add_run(chunk[1:-1])
            run.font.name = font
            run.font.size = Pt(size)
            run.bold = bold
            run.italic = True
            run.font.color.rgb = RGBColor.from_string(color)
        else:
            run = par.add_run(chunk)
            run.font.name = font
            run.font.size = Pt(size)
            run.bold = bold
            run.italic = italic
            run.font.color.rgb = RGBColor.from_string(color)


def rich(par, text, size=BASE, color=INK, bold=False, italic=False, font=SANS):
    """Admite **negrita**, y dentro de ella `codigo` o *cursiva*."""
    position = 0
    for match in re.finditer(r"\*\*([^*]+)\*\*", text):
        if match.start() > position:
            _emit(par, text[position:match.start()], size, color, bold, italic, font)
        _emit(par, match.group(1), size, color, True, italic, font)
        position = match.end()
    if position < len(text):
        _emit(par, text[position:], size, color, bold, italic, font)
    return par


def p(text="", size=BASE, color=INK, bold=False, italic=False, after=4.5, before=0,
      align=None, indent=0.0, spacing=1.16, style=None):
    par = DOC.add_paragraph(style=style)
    pf = par.paragraph_format
    pf.space_after = Pt(after)
    pf.space_before = Pt(before)
    pf.line_spacing = spacing
    if indent:
        pf.left_indent = Mm(indent)
    if align:
        pf.alignment = align
    if text:
        rich(par, text, size=size, color=color, bold=bold, italic=italic)
    return par


def bullet(text, size=BASE, after=2.6, indent=5.0, marker="·"):
    par = DOC.add_paragraph()
    pf = par.paragraph_format
    pf.space_after = Pt(after)
    pf.line_spacing = 1.16
    pf.left_indent = Mm(indent + 3.2)
    pf.first_line_indent = Mm(-3.2)
    run = par.add_run(marker + "  ")
    run.font.name = SANS
    run.font.size = Pt(size)
    run.font.color.rgb = RGBColor.from_string(CORAL)
    run.bold = True
    rich(par, text, size=size)
    return par


def numbered(n, text, size=BASE, after=2.8):
    par = DOC.add_paragraph()
    pf = par.paragraph_format
    pf.space_after = Pt(after)
    pf.line_spacing = 1.16
    pf.left_indent = Mm(8.2)
    pf.first_line_indent = Mm(-5.0)
    run = par.add_run(f"{n}.  ")
    run.font.name = MONO
    run.font.size = Pt(size - 0.4)
    run.bold = True
    run.font.color.rgb = RGBColor.from_string(CORAL)
    rich(par, text, size=size)
    return par


def spacer(pts=4):
    """Silencio vertical exacto: altura de linea fija, sin texto visible."""
    par = DOC.add_paragraph()
    pf = par.paragraph_format
    pf.space_after = Pt(0)
    pf.space_before = Pt(0)
    pf.line_spacing = Pt(pts)
    run = par.add_run("\u00a0")
    run.font.size = Pt(1)
    return par


# --------------------------------------------------------------------------- titulos
def h2(num, title):
    par = DOC.add_paragraph()
    pf = par.paragraph_format
    pf.space_before = Pt(16)
    pf.space_after = Pt(7)
    pf.line_spacing = 1.0
    keep_next(par)
    r1 = par.add_run(num + "  ")
    r1.font.name = MONO
    r1.font.size = Pt(13.5)
    r1.bold = True
    r1.font.color.rgb = RGBColor.from_string(CORAL)
    r2 = par.add_run(title)
    r2.font.name = SANS
    r2.font.size = Pt(15.5)
    r2.bold = True
    r2.font.color.rgb = RGBColor.from_string(INK)
    letter_space(r2, -6)
    para_rule(par, "bottom", sz=12, color=CORAL, space=3)
    return par


def h3(title, before=9):
    par = DOC.add_paragraph()
    pf = par.paragraph_format
    pf.space_before = Pt(before)
    pf.space_after = Pt(3)
    pf.line_spacing = 1.0
    keep_next(par)
    run = par.add_run(title)
    run.font.name = SANS
    run.font.size = Pt(10.6)
    run.bold = True
    run.font.color.rgb = RGBColor.from_string(INK)
    return par


# --------------------------------------------------------------------------- tablas
def table(headers, rows, widths, aligns=None, size=8.5, head_size=7.0, code_head=0.0):
    n = len(widths)
    t = DOC.add_table(rows=1, cols=n)
    t.alignment = WD_TABLE_ALIGNMENT.LEFT
    t.autofit = False
    table_width(t, widths)
    table_borders_none(t)
    no_split(t)
    header_row(t)
    aligns = aligns or ["l"] * n

    hdr = t.rows[0]
    for i, htext in enumerate(headers):
        cell = hdr.cells[i]
        cell.width = Mm(widths[i])
        shade(cell, PANEL)
        cell_margins(cell, 0.10, 0.10, 0.16, 0.16)
        valign_top(cell)
        cell_border(cell, "bottom", sz=12, color="C9D2DE")
        par = cell.paragraphs[0]
        par.paragraph_format.space_after = Pt(0)
        par.paragraph_format.line_spacing = 1.0
        if aligns[i] == "r":
            par.alignment = WD_ALIGN_PARAGRAPH.RIGHT
        run = par.add_run(htext.upper())
        run.font.name = SANS
        run.font.size = Pt(head_size)
        run.bold = True
        run.font.color.rgb = RGBColor.from_string(MUTED)
        letter_space(run, 12)

    for r_index, row in enumerate(rows):
        tr = t.add_row()
        for i, value in enumerate(row):
            cell = tr.cells[i]
            cell.width = Mm(widths[i])
            cell_margins(cell, 0.075, 0.075, 0.16, 0.16)
            valign_top(cell)
            cell_border(cell, "bottom", sz=4, color=LINE)
            if r_index % 2 == 1:
                shade(cell, "FBFCFE")
            par = cell.paragraphs[0]
            par.paragraph_format.space_after = Pt(0)
            par.paragraph_format.line_spacing = 1.06
            if aligns[i] == "r":
                par.alignment = WD_ALIGN_PARAGRAPH.RIGHT
            rich(par, value, size=size)
    spacer(5)
    return t


def callout(kind, label, body, color, bg):
    t = DOC.add_table(rows=1, cols=1)
    t.autofit = False
    table_width(t, [CONTENT_W])
    table_borders_none(t)
    no_split(t)
    cell = t.rows[0].cells[0]
    cell.width = Mm(CONTENT_W)
    shade(cell, bg)
    cell_margins(cell, 0.22, 0.22, 0.30, 0.24)
    valign_top(cell)
    cell_border(cell, "left", sz=18, color=color)

    par = cell.paragraphs[0]
    par.paragraph_format.space_after = Pt(1.6)
    par.paragraph_format.line_spacing = 1.0
    run = par.add_run(label.upper())
    run.font.name = SANS
    run.font.size = Pt(7.0)
    run.bold = True
    run.font.color.rgb = RGBColor.from_string(color)
    letter_space(run, 14)

    for line in body:
        bp = cell.add_paragraph()
        bp.paragraph_format.space_after = Pt(1.5)
        bp.paragraph_format.line_spacing = 1.14
        rich(bp, line, size=8.9)
    spacer(6)


def code_block(lines, size=7.5):
    """Bloque de codigo con sangria preservada (espacios iniciales -> NBSP)."""
    t = DOC.add_table(rows=1, cols=1)
    t.autofit = False
    table_width(t, [CONTENT_W])
    table_borders_none(t)
    no_split(t)
    cell = t.rows[0].cells[0]
    shade(cell, "F7F8FC")
    cell_margins(cell, 0.24, 0.24, 0.30, 0.20)
    valign_top(cell)
    cell_border(cell, "left", sz=10, color="9AA6B4")

    for index, line in enumerate(lines):
        par = cell.paragraphs[0] if index == 0 else cell.add_paragraph()
        par.paragraph_format.space_after = Pt(0)
        par.paragraph_format.space_before = Pt(0)
        par.paragraph_format.line_spacing = 1.06
        stripped = line.lstrip(" ")
        indent = len(line) - len(stripped)
        run = par.add_run("\u00a0" * indent + (stripped or "\u00a0"))
        run.font.name = MONO
        run.font.size = Pt(size)
        run.font.color.rgb = RGBColor.from_string(CODE_INK)
    spacer(6)


def finding(tag, level, title, evidence, body):
    palette = {
        "Alta": (RED, "FFFCFC"),
        "Media": (AMBER, "FFFDF7"),
        "Baja": ("8FA3B8", "FCFDFE"),
    }
    accent, bg = palette[level]
    t = DOC.add_table(rows=1, cols=1)
    t.autofit = False
    table_width(t, [CONTENT_W])
    table_borders_none(t)
    no_split(t)
    cell = t.rows[0].cells[0]
    cell.width = Mm(CONTENT_W)
    shade(cell, bg)
    cell_margins(cell, 0.20, 0.20, 0.30, 0.22)
    valign_top(cell)
    cell_border(cell, "left", sz=16, color=accent)

    head = cell.paragraphs[0]
    head.paragraph_format.space_after = Pt(1.2)
    head.paragraph_format.line_spacing = 1.0
    chip = head.add_run(tag.upper() + "   ")
    chip.font.name = SANS
    chip.font.size = Pt(6.8)
    chip.bold = True
    chip.font.color.rgb = RGBColor.from_string(accent)
    letter_space(chip, 16)
    ttl = head.add_run(title)
    ttl.font.name = SANS
    ttl.font.size = Pt(9.6)
    ttl.bold = True

    ev = cell.add_paragraph()
    ev.paragraph_format.space_after = Pt(1.4)
    ev.paragraph_format.line_spacing = 1.06
    rich(ev, evidence, size=8.1, color=MUTED)

    bp = cell.add_paragraph()
    bp.paragraph_format.space_after = Pt(0)
    bp.paragraph_format.line_spacing = 1.14
    rich(bp, body, size=8.9)
    spacer(5.5)


def arrow_row(text):
    par = DOC.add_paragraph()
    par.paragraph_format.space_after = Pt(1.2)
    par.paragraph_format.space_before = Pt(1.2)
    par.paragraph_format.line_spacing = 1.0
    par.alignment = WD_ALIGN_PARAGRAPH.CENTER
    run = par.add_run(text)
    run.font.name = SANS
    run.font.size = Pt(7.5)
    run.font.color.rgb = RGBColor.from_string("B9C3D0")


def chain_step(n, title, body):
    t = DOC.add_table(rows=1, cols=2)
    t.autofit = False
    table_width(t, [11, CONTENT_W - 11])
    table_borders_none(t)
    no_split(t)
    c0, c1 = t.rows[0].cells
    c0.width = Mm(11)
    c1.width = Mm(CONTENT_W - 11)
    for c in (c0, c1):
        cell_margins(c, 0.13, 0.13, 0.18, 0.18)
        valign_top(c)
        cell_border(c, "bottom", sz=4, color=LINE)
        cell_border(c, "top", sz=4, color=LINE)
    cell_border(c0, "left", sz=4, color=LINE)
    cell_border(c1, "right", sz=4, color=LINE)
    shade(c0, CORAL_SOFT)

    par0 = c0.paragraphs[0]
    par0.alignment = WD_ALIGN_PARAGRAPH.CENTER
    par0.paragraph_format.space_after = Pt(0)
    run = par0.add_run(str(n))
    run.font.name = MONO
    run.font.size = Pt(9.5)
    run.bold = True
    run.font.color.rgb = RGBColor.from_string("A33A20")

    par1 = c1.paragraphs[0]
    par1.paragraph_format.space_after = Pt(0.8)
    par1.paragraph_format.line_spacing = 1.0
    run = par1.add_run(title)
    run.font.name = SANS
    run.font.size = Pt(9.3)
    run.bold = True
    sub = c1.add_paragraph()
    sub.paragraph_format.space_after = Pt(0)
    sub.paragraph_format.line_spacing = 1.1
    rich(sub, body, size=8.4, color=MUTED)


# --------------------------------------------------------------------------- documento
DOC = Document()
sec = DOC.sections[0]
sec.page_width = Mm(210)
sec.page_height = Mm(297)
sec.top_margin = Mm(19)
sec.bottom_margin = Mm(17)
sec.left_margin = Mm(17)
sec.right_margin = Mm(17)
sec.header_distance = Mm(9)
sec.footer_distance = Mm(9)
sec.different_first_page_header_footer = True

normal = DOC.styles["Normal"]
normal.font.name = SANS
normal.font.size = Pt(BASE)
normal.font.color.rgb = RGBColor.from_string(INK)
normal.paragraph_format.space_after = Pt(4.5)
normal.paragraph_format.line_spacing = 1.16
rpr = normal.element.get_or_add_rPr()
rfonts = rpr.find(qn("w:rFonts"))
if rfonts is None:
    rfonts = OxmlElement("w:rFonts")
    rpr.append(rfonts)
for attr in ("ascii", "hAnsi", "cs"):
    rfonts.set(qn("w:" + attr), SANS)

# encabezado y pie (paginas 2+)
hdr = sec.header
hpar = hdr.paragraphs[0]
hpar.alignment = WD_ALIGN_PARAGRAPH.RIGHT
hpar.paragraph_format.space_after = Pt(2)
r = hpar.add_run("AXOLOTE  ·  INFORME TÉCNICO DE LA MINI-APP")
r.font.name = SANS
r.font.size = Pt(6.8)
r.bold = True
r.font.color.rgb = RGBColor.from_string("9AA6B4")
letter_space(r, 24)
para_rule(hpar, "bottom", sz=4, color=LINE, space=2)

ftr = sec.footer
fpar = ftr.paragraphs[0]
fpar.paragraph_format.space_before = Pt(2)
for tab_pos in (Mm(CONTENT_W),):
    fpar.paragraph_format.tab_stops.add_tab_stop(tab_pos, 2)  # 2 = RIGHT
r = fpar.add_run("Nahua 0.16.3  ·  19 de noviembre de 2026")
r.font.name = SANS
r.font.size = Pt(7.2)
r.font.color.rgb = RGBColor.from_string("9AA6B4")
fpar.add_run("\t")
r2 = fpar.add_run("Página ")
r2.font.name = SANS
r2.font.size = Pt(7.2)
r2.font.color.rgb = RGBColor.from_string("9AA6B4")


def field(par, instr, placeholder="1"):
    run = par.add_run()
    run.font.name = SANS
    run.font.size = Pt(7.2)
    run.font.color.rgb = RGBColor.from_string("9AA6B4")
    begin = _el("w:fldChar", fldCharType="begin")
    instr_el = OxmlElement("w:instrText")
    instr_el.set(qn("xml:space"), "preserve")
    instr_el.text = f" {instr} "
    sep = _el("w:fldChar", fldCharType="separate")
    text = OxmlElement("w:t")
    text.text = placeholder
    end = _el("w:fldChar", fldCharType="end")
    run._r.append(begin)
    run._r.append(instr_el)
    run._r.append(sep)
    run._r.append(text)
    run._r.append(end)


field(fpar, "PAGE", "2")
r3 = fpar.add_run(" de ")
r3.font.name = SANS
r3.font.size = Pt(7.2)
r3.font.color.rgb = RGBColor.from_string("9AA6B4")
field(fpar, "NUMPAGES", "14")


# =========================================================== PORTADA
spacer(20)
kick = DOC.add_paragraph()
kick.paragraph_format.space_after = Pt(3)
rk = kick.add_run("NAHUA  ·  SUPER APP DE COLIMA      ·      INFORME TÉCNICO")
rk.font.name = SANS
rk.font.size = Pt(7.4)
rk.bold = True
rk.font.color.rgb = RGBColor.from_string(CORAL)
letter_space(rk, 26)

title = DOC.add_paragraph()
title.paragraph_format.space_after = Pt(1)
title.paragraph_format.line_spacing = 0.95
rt = title.add_run("Axolote")
rt.font.name = SANS
rt.font.size = Pt(46)
rt.bold = True
rt.font.color.rgb = RGBColor.from_string(INK)
letter_space(rt, -24)

sub = DOC.add_paragraph()
sub.paragraph_format.space_after = Pt(6)
sub.paragraph_format.line_spacing = 1.0
rs = sub.add_run("Tecnología actual de la mini-app  ")
rs.font.name = SANS
rs.font.size = Pt(14.5)
rs.font.color.rgb = RGBColor.from_string("3D4A57")
rc = sub.add_run("tamagochi")
rc.font.name = MONO
rc.font.size = Pt(12.5)
rc.font.color.rgb = RGBColor.from_string(CORAL)

rule = DOC.add_paragraph()
rule.paragraph_format.space_after = Pt(10)
rule.paragraph_format.line_spacing = 1.0
para_rule(rule, "bottom", sz=18, color=CORAL, space=0)
rr = rule.add_run("")
rr.font.size = Pt(1)

pic = DOC.add_paragraph()
pic.alignment = WD_ALIGN_PARAGRAPH.CENTER
pic.paragraph_format.space_after = Pt(10)
pic.add_run().add_picture(ART, width=Mm(92))

lede = DOC.add_paragraph()
lede.paragraph_format.space_after = Pt(0)
lede.paragraph_format.line_spacing = 1.3
rich(
    lede,
    "Análisis de ingeniería de la mascota virtual de Nahua: qué se ejecuta hoy, sobre qué "
    "plataforma corre, cómo persiste su estado y qué deuda técnica arrastra. Lectura del código "
    "fuente línea por línea, sin suposiciones.",
    size=10.2,
    color="3A4653",
)

spacer(245)

meta_t = DOC.add_table(rows=2, cols=4)
meta_t.autofit = False
table_width(meta_t, [44, 44, 44, 44])
table_borders_none(meta_t)
labels = ["MINI-APP", "IDENTIFICADOR", "VERSIÓN DE APP", "FECHA"]
values = ["Axolote", "tamagochi", "0.16.3 · 1603", "19 nov 2026"]
for i in range(4):
    top = meta_t.rows[0].cells[i]
    bot = meta_t.rows[1].cells[i]
    for c in (top, bot):
        c.width = Mm(CONTENT_W / 4)
        cell_margins(c, 0.05, 0.05, 0.0, 0.20)
        valign_top(c)
    cell_border(top, "top", sz=8, color="D8DFE9")
    for c, txt, sz, bold, color in (
        (top, labels[i], 6.8, True, MUTED),
        (bot, values[i], 9.4, True, INK),
    ):
        par = c.paragraphs[0]
        par.paragraph_format.space_after = Pt(0.5)
        par.paragraph_format.line_spacing = 1.0
        run = par.add_run(txt)
        run.font.name = SANS
        run.font.size = Pt(sz)
        run.bold = bold
        run.font.color.rgb = RGBColor.from_string(color)
        if sz < 7:
            letter_space(run, 16)

DOC.add_page_break()


# =========================================================== 00 RESUMEN
h2("00", "Resumen ejecutivo")

p(
    "**Axolote** es la mini-app de mascota virtual de Nahua. Por dentro no es una app nativa: es "
    "**un único archivo HTML de 12.7 KB** con CSS y JavaScript clásico embebidos, sin framework, "
    "sin paso de compilación y sin dependencias de terceros, que el anfitrión sirve dentro de un "
    "`WebView` desde un origen HTTPS sintético.",
    size=10.0,
)
p(
    "La inteligencia de la app —barras de comida, ánimo, energía y limpieza; experiencia; cinco "
    "etapas evolutivas— son unas **120 líneas de JavaScript ES5** que se ejecutan en el motor "
    "Blink/V8 del sistema. El estado vive en `localStorage` bajo la clave `nahua.axolote.v1`, y el "
    "único contacto con el teléfono son dos llamadas al puente nativo: `Nahua.user()` para saludar "
    "por tu nombre y `Nahua.close()` para cerrar. **Consume 2 de los 18 métodos que expone el "
    "puente** y no hace ni una petición de red.",
)
p(
    "El resultado es un artefacto pequeño, rápido y perfectamente offline —el más liviano del "
    "catálogo de mini-apps— con una arquitectura acertada para su escala. El análisis también "
    "encontró **un defecto funcional real**: el decaimiento en vivo nunca se aplica, porque el "
    "temporizador de 60 s queda por debajo del umbral de 72 s que exige `applyDecay`, y además "
    "reinicia la marca de tiempo en cada tick. En la práctica, las barras del axolote **solo bajan "
    "si cierras y vuelves a abrir la mini-app**.",
)

callout(
    "Veredicto",
    "Veredicto",
    [
        "Tecnología adecuada y bien elegida para el problema: un bundle estático aislado, servido "
        "por HTTPS con todo el tráfico externo cortado, y una lógica de juego que cabe en la cabeza. "
        "No hay nada que “modernizar” por moda: hay **cuatro correcciones concretas** (decaimiento, "
        "barra de XP, tema oscuro y accesibilidad) que lo dejarían redondo.",
    ],
    TEAL,
    TEAL_SOFT,
)

h3("Contenido del informe", before=6)
toc_rows = [
    ("00", "Resumen ejecutivo", "07", "Persistencia y tiempo"),
    ("01", "Identidad y encuadre en la app", "08", "Capa visual"),
    ("02", "Ficha técnica", "09", "Rendimiento y tamaño"),
    ("03", "Cadena de arranque", "10", "Seguridad y privacidad"),
    ("04", "Runtime del anfitrión", "11", "Hallazgos y deuda técnica"),
    ("05", "El puente Nahua", "12", "Comparativa del catálogo"),
    ("06", "Modelo de juego", "13", "Recomendaciones"),
    ("14", "Anexos", "", ""),
]
for a, b, c, d in toc_rows:
    par = DOC.add_paragraph()
    pf = par.paragraph_format
    pf.space_after = Pt(1.8)
    pf.line_spacing = 1.0
    pf.tab_stops.add_tab_stop(Mm(92), WD_TAB_ALIGNMENT.LEFT)

    def pair(num, text, first):
        rn = par.add_run(num + "   ")
        rn.font.name = MONO
        rn.font.size = Pt(8.6)
        rn.bold = True
        rn.font.color.rgb = RGBColor.from_string(CORAL)
        rt2 = par.add_run(text)
        rt2.font.name = SANS
        rt2.font.size = Pt(9.0)
        rt2.font.color.rgb = RGBColor.from_string(INK if first else "33404D")

    pair(a, b, True)
    if c:
        par.add_run("\t")
        pair(c, d, False)


# =========================================================== 01
h2("01", "Identidad y encuadre en la app")

p(
    "El identificador interno es `tamagochi` —sin la *t* final de *tamagotchi*, una inconsistencia "
    "ortográfica que se conserva a propósito porque es la llave de ruteo, del manifiesto y de las "
    "pruebas—. El nombre que ve el usuario es **Axolote**, elegido por identidad local: el ajolote "
    "es el animal símbolo de Xochimilco y del altiplano, y encaja con el tono de la app."
)

table(
    ["Elemento", "Valor", "Dónde se declara"],
    [
        ["id", "`tamagochi`", "`ServiceCatalog.kt:114` · `index.json:32`"],
        ["Nombre", "Axolote", "`ServiceCatalog.kt:115` · `index.json:33`"],
        ["Tagline", "“Tu mascota virtual”", "`ServiceCatalog.kt:116`"],
        ["Categoría", "`ENTERTAINMENT`", "`ServiceCatalog.kt:117`"],
        ["Icono", "`Icons.Rounded.Pets` (huella)", "`ServiceCatalog.kt:118`"],
        ["Color de acento", "`Coral` · `#FF6B4A`", "`ServiceCatalog.kt:119` · `index.json:37`"],
        ["Destino", "`ServiceTarget.MiniApp(\"tamagochi\")`", "`ServiceCatalog.kt:120`"],
        ["Entrada alterna", "Botón de mascota en Inicio", "`HomeScreen.kt:96` · `NahuaApp.kt:109`"],
        ["Verificación", "El catálogo exige que exista", "`ServiceCatalogTest.kt:63`"],
    ],
    [30, 62, 84],
)

p(
    "Hay dos puertas de entrada y ambas terminan en la misma ruta: "
    "`navController.navigate(Routes.miniApp(\"tamagochi\"))`. La mini-app no tiene código Kotlin "
    "propio: **no existe un `feature/tamagochi`**, ni ViewModel, ni pantalla nativa. Todo lo que no "
    "es HTML lo aporta el *host* genérico de mini-apps."
)

callout(
    "Consecuencia",
    "Consecuencia de diseño",
    [
        "Cualquier cambio en el axolote se hace editando un archivo de texto dentro de `assets/`. "
        "No hace falta tocar Kotlin, ni volver a compilar rutas, ni registrar nada: el manifiesto "
        "`index.json` ya lo declara. Esa es exactamente la promesa del runtime de mini-apps y aquí "
        "se cumple al pie de la letra.",
    ],
    BLUE,
    BLUE_SOFT,
)


# =========================================================== 02
h2("02", "Ficha técnica")

p(
    "Todo el “stack” del axolote, en una tabla. Los números salen de medir los archivos, no de "
    "estimarlos."
)

table(
    ["Dimensión", "Valor actual"],
    [
        ["Archivo", "`app/src/main/assets/miniapps/tamagochi/index.html`"],
        ["Tamaño / líneas", "12,717 B (12.4 KiB) · 274 líneas"],
        ["Tecnología", "HTML5 + CSS3 + JavaScript ES5 clásico (`var`, funciones, `Object.assign`)"],
        ["Framework", "**Ninguno** — sin React, Vue, Svelte ni jQuery"],
        ["Build step", "**Ninguno** — el archivo del repo es el que corre en el teléfono"],
        ["Dependencias", "**Cero** (una sola referencia: el SDK propio `../nahua-sdk.js`)"],
        ["Gráficos", "SVG inline vectorial + animaciones CSS `@keyframes`; sin imágenes ni fuentes externas"],
        ["Iconografía", "Emoji del sistema; sin set de iconos propio"],
        ["Persistencia", "`localStorage`, clave `nahua.axolote.v1`"],
        ["Red", "**0 peticiones**; el host corta cualquier salida externa con un 404 vacío"],
        ["Permiso declarado", "`storage.local`"],
        ["Modo de pantalla", "`chrome` (marco nativo Nahua) · orientación `auto`"],
        ["Origen efectivo", "`https://appassets.androidplatform.net/assets/miniapps/tamagochi/index.html`"],
        ["Versión del bundle", "`1.0.0` · `apiVersion: 1`"],
        ["Plataforma anfitriona", "Android `minSdk 26` / `targetSdk 35` · WebView del sistema"],
        ["App contenedora", "Nahua 0.16.3 · Kotlin 2.2.10 · Compose BOM 2025.02.00 · AGP 9.5.1"],
    ],
    [36, 140],
)

h3("Composición del archivo")
table(
    ["Bloque", "Líneas", "Qué contiene"],
    [
        ["`<style>`", "7–46", "~35 reglas: tokens de color, tarjetas, barras, dos `@keyframes`, toast"],
        ["Marcado", "48–106", "Escenario, axolote SVG, acciones, barra de XP, pie"],
        ["SDK", "108", "`<script src=\"../nahua-sdk.js\">`"],
        ["Lógica", "109–272", "Estado, decaimiento, render, acciones, temporizador"],
    ],
    [30, 22, 124],
    aligns=["l", "r", "l"],
)

callout(
    "Lo que no hay",
    "Lo que no hay",
    [
        "No hay `package.json`, ni bundler, ni minificador, ni `node_modules`, ni transpilador, ni "
        "TypeScript, ni tests del bundle. El ciclo de desarrollo es: editar, `assembleDebug`, "
        "instalar. Para un artefacto de 12 KB eso es una ventaja competitiva, no una carencia.",
    ],
    BLUE,
    BLUE_SOFT,
)


# =========================================================== 03
h2("03", "Cadena de arranque")

p(
    "Seis saltos desde que tocas la tarjeta en el hub hasta que el axolote respira. Cada salto es "
    "una pieza distinta del runtime."
)

chain_step(1, "Catálogo de servicios",
           "`ServiceCatalog.kt:113` declara el servicio con `target = MiniApp(\"tamagochi\")`. Es una lista Kotlin pura.")
arrow_row("▼")
chain_step(2, "Navegación",
           "`NahuaApp.kt:109` → `navController.navigate(Routes.miniApp(\"tamagochi\"))`.")
arrow_row("▼")
chain_step(3, "Resolución del manifiesto",
           "`MiniAppRegistry` lee `assets/miniapps/index.json`, lo cachea y devuelve el `MiniAppManifest`. Si el id no existe: pantalla “no encontrada”.")
arrow_row("▼")
chain_step(4, "URL de entrada",
           "`MiniAppManifest.kt:27`: `entryUrl = \"https://appassets.androidplatform.net/assets/\" + entry`. Nunca `file://`.")
arrow_row("▼")
chain_step(5, "WebView + servidor de assets",
           "`MiniAppWebView.kt:62-70` construye el `WebViewAssetLoader` y un `WebView` endurecido; `shouldInterceptRequest` resuelve la URL al APK.")
arrow_row("▼")
chain_step(6, "Puente y carga",
           "`addJavascriptInterface(bridge, \"NahuaNative\")` y `loadUrl(entryUrl)`. El HTML importa `nahua-sdk.js`, que envuelve el puente en `window.Nahua`.")
spacer(8)

p(
    "La clave conceptual: **el axolote nunca sabe dónde está**. No conoce rutas del teléfono, no ve "
    "el sistema de archivos y no puede distinguir si corre en el APK o en un servidor: solo ve un "
    "origen HTTPS con contenido estático."
)

h3("El manifiesto, completo")
p("La declaración entera del axolote en `index.json` son nueve líneas:")
table(
    ["Campo", "Valor"],
    [
        ["id", "`\"tamagochi\"`"],
        ["name / tagline", "`\"Axolote\"` · `\"Tu mascota virtual\"`"],
        ["version", "`\"1.0.0\"`"],
        ["entry", "`\"miniapps/tamagochi/index.html\"`"],
        ["accent", "`\"#FF6B4A\"`"],
        ["permissions", "`[\"storage.local\"]`"],
        ["display / orientation", "omitidos → `chrome` / `auto`"],
    ],
    [40, 136],
)
p(
    "Los campos `display` y `orientation` tienen valores por defecto tolerantes "
    "(`MiniAppDisplay.from()`): un valor desconocido en el JSON cae en el modo seguro en lugar de "
    "romper la carga. El axolote corre con marco nativo y rotación libre porque así lo omiten, no "
    "porque lo pida."
)


# =========================================================== 04
h2("04", "Runtime del anfitrión")

p(
    "El `WebView` que hospeda al axolote no es el de fábrica. `MiniAppWebView.kt` lo configura con "
    "una política explícita: lo mínimo para que una página funcione, todo lo demás apagado."
)

table(
    ["Ajuste", "Valor", "Por qué"],
    [
        ["`javaScriptEnabled`", "true", "El juego es JS puro."],
        ["`domStorageEnabled`", "true", "**Sin esto el axolote olvidaría todo**: `localStorage` es su única memoria."],
        ["`allowFileAccess`", "false", "Nada de `file://`."],
        ["`allowContentAccess`", "false", "Sin URIs de contenido."],
        ["`allowFileAccessFromFileURLs`", "false", "Corta la lectura cruzada entre orígenes `file`."],
        ["`allowUniversalAccessFromFileURLs`", "false", "Ídem."],
        ["`setGeolocationEnabled`", "false", "La ubicación se pide por el puente, con permiso explícito. El axolote no la usa."],
        ["`cacheMode`", "`LOAD_DEFAULT`", "Assets versionados con el APK: la caché acelera reentrar sin riesgo de datos viejos."],
        ["`mediaPlaybackRequiresUserGesture`", "true (modo chrome)", "Un audio suelto no arranca solo. En inmersivo se desactiva para el emulador."],
        ["`setSupportZoom`", "false", "Zoom de pellizco desactivado, `textZoom = 100`."],
        ["`offscreenPreRaster`", "true", "Renderizado fuera de pantalla: scroll y reflow más baratos (API 24+)."],
    ],
    [58, 30, 88],
)

h3("Aislamiento de red")
p(
    "`shouldInterceptRequest` no deja pasar ninguna petición `http(s)` a un host distinto de "
    "`appassets.androidplatform.net`: responde un **404 vacío e inmediato**, sin tocar la red. "
    "`data:` y `blob:` sí pasan, porque son locales. Es una defensa en el lugar correcto: el "
    "axolote no puede exfiltrar nada aunque su JavaScript fuera malicioso."
)

h3("Ciclo de vida")
p(
    "Al pasar a segundo plano, el anfitrión llama `onPause()` + `pauseTimers()` sobre el WebView. "
    "Eso **congela el `setInterval` de 60 s del axolote** y reanuda al volver. Y si el proceso de "
    "render muere por OOM de GPU, `onRenderProcessGone` devuelve `true` y el anfitrión recrea el "
    "WebView con `key(recreateSignal)` en vez de dejar caer la app."
)

callout(
    "Bien resuelto",
    "Bien resuelto",
    [
        "El par `onPause`/`pauseTimers` es la razón por la que el axolote no consume batería estando "
        "oculto, y el `key(recreateSignal)` es una recuperación de crash poco común en apps WebView "
        "caseras.",
    ],
    TEAL,
    TEAL_SOFT,
)


# =========================================================== 05
h2("05", "El puente Nahua")

p(
    "El contrato entre Kotlin y JavaScript son **18 métodos** anotados con `@JavascriptInterface` "
    "expuestos como `window.NahuaNative`, y un envoltorio de 380 líneas (`nahua-sdk.js`) que los "
    "convierte en una API con promesas: `window.Nahua`."
)

table(
    ["Método del puente", "¿Lo usa el axolote?", "Para qué sirve"],
    [
        ["`getSession()`", "**Sí** · vía `Nahua.user()`", "Perfil de la sesión; el axolote toma el primer nombre para saludar."],
        ["`close()`", "**Sí** · vía `Nahua.close()`", "Botón “Cerrar” del encabezado."],
        ["`toast(message)`", "No", "Aviso nativo; el axolote dibuja su propio toast en CSS."],
        ["`pay` · `valuate` · `compareBarter` · `announceBarter`", "No", "Cartera y bazar (mini-apps ride, bills, bazar)."],
        ["`requestLocation` · `startLocationUpdates` · `stopLocationUpdates` · `openDirections`", "No", "GPS y rutas (mini-app mapa)."],
        ["`pickRom` · `listRoms` · `deleteRom`", "No", "Cartuchos GBA."],
        ["`haptic` · `orientation` · `copy`", "No", "Vibración, giro y portapapeles."],
        ["`log(message)`", "No", "Depuración a logcat."],
    ],
    [62, 40, 74],
)

p(
    "**2 de 18 métodos: 11 % de la superficie del puente.** Es la mini-app con menos acoplamiento al "
    "anfitrión de todo el catálogo, y eso explica por qué podría publicarse como página web "
    "independiente con un shim de dos funciones."
)

h3("Uso concreto en el código")
table(
    ["Línea", "Código", "Efecto"],
    [
        ["51", "`onclick=\"Nahua.close()\"`", "Cierra la mini-app y vuelve al inicio. Sin host: `history.back()`."],
        ["269–271", "`if (Nahua && Nahua.user().name)`", "Saludo personalizado una sola vez, al cargar."],
    ],
    [18, 60, 98],
)

callout(
    "Contrato",
    "Hallazgo de contrato",
    [
        "El encabezado de `nahua-sdk.js` se anuncia como **v1.4**, pero `window.Nahua.version` vale "
        "`'1.3.0'` (línea 105). Cualquier mini-app que negocie capacidades contra ese número leerá "
        "una versión anterior a la real. Es una línea de código de arreglo, pero conviene decidir "
        "cuál es la fuente de verdad: el comentario o el campo.",
    ],
    AMBER,
    AMBER_SOFT,
)


# =========================================================== 06
h2("06", "Modelo de juego")

p(
    "Cuatro variables en el rango `[0, 100]`, siempre enteras. Nada de hambre letal ni muerte: el "
    "axolote se pone triste, nunca se muere. Es una decisión de producto coherente con una demo sin "
    "backend."
)

h3("Variables y decaimiento")
table(
    ["Variable", "Etiqueta", "Decaimiento / hora", "Horas de 80 → 0", "Color"],
    [
        ["`food`", "Comida", "9", "8.9 h", "#F2A100"],
        ["`mood`", "Ánimo", "7", "11.4 h", "#FF6B4A"],
        ["`energy`", "Energía", "5", "16.0 h", "#2F6BFF"],
        ["`clean`", "Limpieza", "4", "20.0 h", "#0E7C66"],
    ],
    [34, 32, 40, 38, 32],
    aligns=["l", "l", "r", "r", "l"],
)
p(
    "El decaimiento es lineal y simultáneo (`DECAY_PER_HOUR`, línea 112), calculado por diferencia "
    "de tiempo real, no por tick. El estado inicial son 80 puntos en cada barra: la mascota empieza "
    "sana pero con margen para que la cuides."
)

h3("Acciones")
table(
    ["Acción", "Efecto en las barras", "XP"],
    [
        ["Alimentar", "`food +24`, `clean −4`", "+6"],
        ["Jugar", "`mood +20`, `energy −10`, `food −5`", "+6"],
        ["Dormir", "`energy +30`, `mood +4`", "+6"],
        ["Bañar", "`clean +34`, `mood +6`", "+6"],
        ["Acariciar (tocar al axolote)", "`mood +5`", "+2"],
    ],
    [58, 92, 26],
    aligns=["l", "l", "r"],
)
p(
    "Hay intercambios deliberados: alimentar ensucia, jugar da ánimo pero quita energía y comida, "
    "dormir no llena la barriga. Sin esa fricción el juego sería un botón único."
)

h3("Progresión")
table(
    ["Nivel", "Etapa", "XP acumulada", "Acciones típicas"],
    [
        ["1", "Cría", "0", "—"],
        ["2", "Explorador", "40", "~7"],
        ["3", "Juvenil", "80", "~13"],
        ["4", "Guardián", "120", "~20"],
        ["5", "Espíritu del lago", "160", "~27"],
    ],
    [26, 70, 44, 36],
    aligns=["r", "l", "r", "r"],
)
p(
    "La fórmula es `level() = min(5, 1 + floor(xp / 40))` con `xpForNextLevel()` fijo en 40 y una "
    "bonificación de **+10 XP** al subir de nivel. El tope es 5: la quinta etapa, “Espíritu del "
    "lago”, es el final del contenido."
)

callout(
    "Curva",
    "Curva de dificultad",
    [
        "Llegar al máximo cuesta unas 27 pulsaciones repartidas en 5 niveles; con las "
        "bonificaciones reales, algo menos. Es una sesión corta a propósito: el axolote es un adorno "
        "cálido del hub, no un juego de retención.",
    ],
    BLUE,
    BLUE_SOFT,
)


# =========================================================== 07
h2("07", "Persistencia y simulación del tiempo")

h3("Dónde vive el estado", before=2)
p(
    "Un solo objeto JSON en `localStorage`, bajo la clave `nahua.axolote.v1` (línea 110), con "
    "memoria de `domStorageEnabled = true`. La clave lleva prefijo y versión: `nahua.axolote.v1` es "
    "una decisión correcta, porque **todas las mini-apps comparten el mismo origen** "
    "(`appassets.androidplatform.net`) y por tanto el mismo `localStorage`."
)

table(
    ["Campo", "Tipo", "Valor por defecto", "Rol"],
    [
        ["`name`", "string", "`\"Xolito\"`", "Nombre mostrado. No editable desde la UI."],
        ["`food` · `mood` · `energy` · `clean`", "int 0–100", "`80`", "Las cuatro barras."],
        ["`xp`", "int ≥ 0", "`0`", "Experiencia acumulada."],
        ["`lastSeen`", "epoch ms", "`Date.now()`", "Base del cálculo de tiempo ausente."],
    ],
    [50, 26, 34, 66],
)

h3("Cómo se recupera el tiempo perdido")
p(
    "`load()` lee el JSON, y `applyDecay()` (145–155) calcula las horas transcurridas desde "
    "`lastSeen` y resta `DECAY_PER_HOUR × horas` a cada barra, con `clamp()` a `[0, 100]`. Si el "
    "JSON está corrupto, `try/catch` devuelve `defaults()`: **nunca hay pantalla en blanco**."
)

callout(
    "Defecto",
    "Defecto funcional: el decaimiento en vivo no ocurre",
    [
        "El temporizador de las líneas 262–266 corre cada **60,000 ms** (0.0167 h) y `applyDecay` "
        "solo actúa si `hours > 0.02` (es decir, más de **72 s**). La condición nunca se cumple: el "
        "tick no decae nada y, peor, reescribe `lastSeen`, así que el tiempo tampoco se acumula.",
        "Consecuencia medible: si el axolote está abierto, sus barras **están congeladas "
        "indefinidamente**. Solo bajan cuando sales de la mini-app más de 72 s y vuelves a entrar. "
        "El `setInterval` existe, se ejecuta y es inútil.",
    ],
    AMBER,
    AMBER_SOFT,
)

p(
    "Hay un segundo efecto del mismo diseño: `clamp()` termina en `Math.round()`, así que cada "
    "aplicación de decaimiento redondea a entero y descarta la fracción. Aun corrigiendo el umbral, "
    "con ticks de 60 s el delta por tick sería de 0.15 puntos para la comida: redondearía a cero "
    "indefinidamente. **El estado debe guardar flotantes o acumular en milésimas**, no redondear en "
    "cada tick."
)

h3("Momentos de escritura")
for item in (
    "En cada acción (`act()` → `save()`).",
    "En la bonificación de nivel (`save()` otra vez, **después de renderizar**).",
    "En el reinicio (`reset`).",
    "En cada tick de 60 s, aunque no haya cambios.",
):
    bullet(item)
p(
    "`save()` envuelve `setItem` en `try/catch` con el comentario *“storage full”*: si el "
    "almacenamiento falla, el juego sigue en memoria. Detalle de robustez barato y correcto."
)


# =========================================================== 08
h2("08", "Capa visual")

p(
    "No hay ni una imagen en el bundle. El axolote es un **SVG de 220 × 180 unidades** dibujado a "
    "mano (líneas 57–80), con 8 elipses, un `path` de cola, tres pares de branquias y una boca que "
    "cambia de forma."
)

table(
    ["Técnica", "Uso", "Coste"],
    [
        ["SVG inline", "Toda la mascota; escala sin pérdida y se recolorea por CSS.", "~1.2 KB"],
        ["`@keyframes float`", "Flotación vertical de 8 px, 3.4 s, `translateY`.", "Composición GPU, 0 JS"],
        ["`@keyframes wiggle`", "Branquias oscilando ±6°, 2.6 s, con `transform-origin: center`.", "Composición GPU"],
        ["Morph de boca", "`setAttribute('d', …)` cambia la curva entre sonrisa y puchero según el ánimo.", "Repintado local"],
        ["`.sleeping`", "Oculta las pupilas y muestra dos arcos de “ojo cerrado”.", "Dos reglas CSS"],
        ["Barras `width`", "Transición de 0.35 s al cambiar el porcentaje.", "Layout, trivial"],
    ],
    [40, 96, 40],
)
p(
    "Dos grupos `<g class=\"gill\">` idénticos en espejo permiten que una sola regla anime las seis "
    "branquias: una simetría explícita, no accidental."
)

h3("Sistema de diseño")
p(
    "Los tokens están en `:root` (línea 8) y coinciden con la paleta Coral de la app: marca "
    "`#FF6B4A`, rosa `#FFB3C7`, tinta `#10161C`, gris `#5D6A78`, línea `#E4E8F0`. Las tarjetas usan "
    "radios de 16–28 px y las sombras son de una sola capa: visualmente es Compose sin serlo."
)

callout(
    "Tema oscuro",
    "Tema oscuro ausente",
    [
        "`:root` declara `color-scheme: light` y el fondo es `#F5F6FA` fijo. Nahua tiene tema "
        "claro/oscuro; al abrir el axolote en modo oscuro, la mini-app **rompe la continuidad "
        "visual** y aparece un panel blanco. Ninguna otra mini-app del catálogo resuelve esto "
        "todavía, pero el axolote es la más visible por ser casi todo superficie clara.",
    ],
    AMBER,
    AMBER_SOFT,
)

h3("Tipografía y escalado")
p(
    "Pila de fuentes del sistema (`-apple-system`, `Segoe UI`, `Roboto`) sin *webfonts*: cero bytes "
    "y cero espera. Con `textZoom = 100` fijo, la mini-app **ignora el tamaño de fuente del "
    "sistema**: un usuario con texto grande verá el mismo axolote en 12 px."
)


# =========================================================== 09
h2("09", "Rendimiento y tamaño")

table(
    ["Métrica", "Axolote", "Contexto"],
    [
        ["Peso del bundle", "12.4 KiB", "El más liviano del catálogo"],
        ["Peso del SDK compartido", "13.1 KiB", "Amortizado entre 5 mini-apps"],
        ["Peticiones de red", "0", "Todo sale del APK"],
        ["Imágenes / fuentes externas", "0", "SVG + fuentes del sistema"],
        ["Trabajo en el hilo principal", "~0", "Animaciones en compositor; JS solo al pulsar"],
        ["Temporizadores activos", "1", "`setInterval` 60 s, congelado en segundo plano"],
        ["Memoria retenida", "mínima", "Un objeto de 7 campos; sin listeners acumulados"],
    ],
    [52, 30, 94],
)
p(
    "El coste real de abrir el axolote no es el bundle: es **instanciar el WebView** (proceso de "
    "render, motor Blink, V8). Eso lo paga igual cualquier mini-app. Dicho de otro modo, el axolote "
    "pesa tan poco que su peso es irrelevante frente al coste fijo del anfitrión."
)

callout(
    "Nativo",
    "¿Y si se hiciera nativo?",
    [
        "Una versión Compose sería más rápida al abrir y no arrastraría WebView, pero costaría unas "
        "400 líneas de Kotlin, un ViewModel, persistencia en DataStore y un ciclo de release propio. "
        "Para este alcance, el HTML gana: **es la decisión correcta y no debería cambiarse**.",
    ],
    BLUE,
    BLUE_SOFT,
)


# =========================================================== 10
h2("10", "Seguridad y privacidad")

for item in (
    "**Origen seguro:** todo se sirve desde `https://appassets.androidplatform.net`, resuelto por el "
    "`WebViewAssetLoader`. El axolote nunca ve `file://`.",
    "**Fuga de red cortada:** cualquier petición a otro host devuelve 404 vacío. No puede llamar a un "
    "CDN, ni a un colector, ni a un webhook.",
    "**Almacenamiento aislado:** `localStorage` por origen; el `InternalStoragePathHandler` deja "
    "vetados `databases/`, `shared_prefs/` y el resto de `filesDir`.",
    "**Datos personales:** el único dato de la sesión que se usa es el **primer nombre** "
    "(`name.split(' ')[0]`). Nada de correo, teléfono ni identificadores.",
    "**Sin permisos peligrosos:** no pide ubicación, cámara ni contactos; no hay `VIBRATE`.",
    "**Superficie de ataque:** el bundle es estático y no acepta entrada externa; no hay "
    "`innerHTML` con datos de usuario (las barras se generan con valores numéricos propios), así "
    "que no hay XSS explotable desde el estado guardado.",
):
    bullet(item, after=3.4)

callout(
    "Matices",
    "Dos matices honestos",
    [
        "**(1)** El permiso `storage.local` del manifiesto es *declarativo*: el host no lo valida ni "
        "lo aplica, como documenta `docs/MINIAPP_SDK.md`. Sirve para auditar intenciones, no para "
        "bloquear. **(2)** No hay `Content-Security-Policy` en el HTML. Con contenido local y la red "
        "cortada el riesgo es bajo, pero una CSP básica sería defensa en profundidad gratis.",
    ],
    AMBER,
    AMBER_SOFT,
)
p(
    "Sobre el dato persistido: `nahua.axolote.v1` queda en el almacenamiento de WebView de la app. "
    "Borrar datos de la app o tocar “empezar de nuevo” lo elimina. No hay exportación ni respaldo, "
    "así que **una limpieza de caché del sistema puede borrar al axolote** sin aviso."
)


# =========================================================== 11
h2("11", "Hallazgos y deuda técnica")

p(
    "Ordenados por impacto real sobre el usuario. La severidad se refiere a la experiencia, no a la "
    "elegancia del código."
)

finding(
    "Alta", "Alta",
    "El decaimiento en vivo nunca se aplica",
    "Líneas 262–266 y 148 · `hours > 0.02` contra un tick de 60 s",
    "El intervalo es de 60 s (0.0167 h) y el umbral exige 72 s. El tick no decae nada y además "
    "reinicia `lastSeen`, impidiendo que el tiempo se acumule. Las barras solo bajan al reabrir la "
    "mini-app tras una ausencia. **Arreglo:** bajar el umbral a `hours > 1/60` y no reescribir "
    "`lastSeen` en el tick, o calcular el decaimiento con acumulador fraccionario.",
)

finding(
    "Alta", "Alta",
    "Redondeo que se come el decaimiento",
    "Línea 157 · `clamp()` termina en `Math.round()`",
    "Guardar enteros y redondear en cada aplicación descarta fracciones. Aun con el umbral "
    "corregido, 9 pts/h repartidos en ticks de un minuto son 0.15 pts por tick: redondea a cero para "
    "siempre. **Arreglo:** persistir las barras como flotantes y redondear solo al pintar.",
)

finding(
    "Media", "Media",
    "La bonificación de nivel no se refleja en la barra de XP",
    "Líneas 229–237 · `save()` y `render()` ocurren *antes* de `state.xp += 10`",
    "Al subir de nivel se muestran el toast y los +10 XP, pero la barra y el texto siguen mostrando "
    "el valor anterior hasta la siguiente acción o hasta el tick de 60 s. Se percibe como un salto "
    "raro. **Arreglo:** mover la bonificación antes de `render()`.",
)

finding(
    "Media", "Media",
    "En el nivel máximo la barra de XP queda clavada en 0",
    "Líneas 161, 194–196 · `xp % 40` con tope de nivel 5",
    "Al llegar a 160 XP el nivel se satura en 5, pero la barra sigue pintando el residuo: `0 / 40`. "
    "El jugador puede seguir pulsando y ver “0 / 40” eternamente, sin señal de que ya terminó el "
    "contenido. **Arreglo:** si `level() === 5`, mostrar “Nivel máximo” y desactivar la barra.",
)

finding(
    "Media", "Media",
    "Tema oscuro inexistente",
    "Línea 8 · `color-scheme: light` y fondo fijo `#F5F6FA`",
    "En modo oscuro la mini-app es un panel blanco dentro de una app oscura. **Arreglo:** "
    "`@media (prefers-color-scheme: dark)` sobre los tokens de `:root`; son unas 10 líneas porque "
    "todo el color ya está centralizado.",
)

finding(
    "Media", "Media",
    "Accesibilidad: sin etiquetas, sin escalado, emoji como iconos",
    "Líneas 91–94, línea 84 y `textZoom = 100`",
    "Los botones de acción no tienen `aria-label` ni estado; el único control táctil del axolote es "
    "un `<svg>` con `onclick`, inalcanzable por teclado o lector de pantalla; el escalado de fuente "
    "del sistema se ignora. Además el emoji de burbujas (U+1FAE7) exige fuentes recientes: en "
    "`minSdk 26` puede renderizarse como tofu. **Arreglo:** botones reales con `aria-label`, un "
    "botón “Acariciar” explícito y un icono SVG para las burbujas.",
)

finding(
    "Baja", "Baja",
    "Reinicio sin confirmación",
    "Líneas 246–252",
    "“Empezar de nuevo” borra el progreso con un solo toque. En un juego con 27 acciones de "
    "inversión afectiva, merece un diálogo de confirmación.",
)

finding(
    "Baja", "Baja",
    "No usa el toast nativo",
    "Líneas 168–174 frente a `Nahua.toast()`",
    "El axolote dibuja su propio toast en CSS mientras el SDK ofrece `Nahua.toast()`. Funciona, pero "
    "rompe la consistencia con el resto de la app (y con la vibración del sistema).",
)

finding(
    "Baja", "Baja",
    "Guardia de SDK incompleta",
    "Línea 269 · `if (Nahua && Nahua.user().name)`",
    "Si `nahua-sdk.js` no cargara, la referencia a `Nahua` lanzaría `ReferenceError` en vez de "
    "degradar. La guarda correcta es `typeof Nahua !== 'undefined'`, como ya hacen otras partes del "
    "SDK.",
)

finding(
    "Baja", "Baja",
    "Sin pruebas del bundle y versión del SDK divergente",
    "`ServiceCatalogTest.kt:63` · `nahua-sdk.js:105`",
    "La única prueba toca que el id exista en el catálogo: la lógica de juego no tiene cobertura (ni "
    "la podría tener sin extraerla a un módulo). Y el SDK anuncia v1.4 en el comentario pero "
    "`version` vale `1.3.0`.",
)

finding(
    "Baja", "Baja",
    "Sin consecuencia final",
    "Líneas 145–157",
    "Un axolote con las cuatro barras a cero sigue vivo y feliz de recibir comida. Es una decisión "
    "de producto válida, pero deja el juego sin tensión: no hay nada que perder.",
)


# =========================================================== 12
h2("12", "Comparativa dentro del catálogo")

p(
    "Poner el axolote al lado de sus vecinas deja ver dónde está el verdadero coste del runtime."
)

table(
    ["Mini-app", "Bundle", "Bridge", "Red", "Modo", "Complejidad"],
    [
        ["Axolote", "12.4 KiB", "2 / 18", "0", "chrome", "Baja — 1 archivo, 120 líneas de lógica"],
        ["Nahua Ride", "~7 KiB", "pay · user", "0", "chrome", "Baja-media"],
        ["Bazar/Trueque", "grande", "pay · valuate · compare · announce · user", "0", "chrome", "Alta — aritmética en Kotlin"],
        ["Mapas", "~10 MB", "watchLocation · openDirections · copy", "0", "chrome", "Alta — teselas + GPS"],
        ["Emulador GBA", "2.47 MB", "pickRom · haptic · orientation", "0", "immersive", "Muy alta — WASM"],
    ],
    [24, 20, 58, 12, 20, 42],
    size=8.2,
)
p(
    "El axolote es el **mínimo viable del runtime**: demuestra que el patrón bundle-aislado funciona "
    "sin pagar nada en complejidad. Es, de hecho, el mejor caso de prueba para validar un cambio en "
    "el host: si el axolote abre, guarda y cierra, el runtime está sano."
)

callout(
    "Banco de pruebas",
    "Uso recomendado como banco de pruebas",
    [
        "Antes de tocar `MiniAppWebView`, `MiniAppAssetLoader` o el `localStorage`, abrir el axolote "
        "y verificar tres cosas: carga desde `https://`, el saludo aparece con tu nombre y el "
        "progreso sobrevive a cerrar la app. Cubre el 80 % de las regresiones posibles del runtime.",
    ],
    BLUE,
    BLUE_SOFT,
)


# =========================================================== 13
h2("13", "Recomendaciones")

h3("Ahora (menos de una hora de trabajo)", before=2)
for i, item in enumerate(
    (
        "Corregir el umbral del decaimiento y dejar de reescribir `lastSeen` en el tick.",
        "Persistir las barras como flotantes y redondear solo al pintar.",
        "Mover la bonificación de +10 XP antes de `render()`.",
        "Mostrar “Nivel máximo” cuando `level() === 5`.",
    ),
    1,
):
    numbered(i, item)

h3("Después (una tarde)")
for i, item in enumerate(
    (
        "Bloque `@media (prefers-color-scheme: dark)` con los tokens invertidos.",
        "`aria-label` en las cuatro acciones, botón “Acariciar” accesible e iconos SVG en lugar de emoji.",
        "Confirmación antes de reiniciar y uso de `Nahua.toast()`.",
        "`typeof Nahua !== 'undefined'` y alinear la versión del SDK.",
    ),
    1,
):
    numbered(i, item)

h3("Si algún día crece")
for i, item in enumerate(
    (
        "Extraer la lógica de juego a `axolote.js` y probarla con Node: hoy es *testeable* pero está "
        "incrustada en el HTML.",
        "Renombrar el id a `tamagotchi` con migración de la clave de `localStorage` (o dejarlo: el "
        "coste supera al beneficio).",
        "Nada más. **La mejor decisión para el axolote es no tocarlo más de lo necesario.**",
    ),
    1,
):
    numbered(i, item)

callout(
    "Conclusión",
    "Conclusión",
    [
        "Axolote es un ejemplo de proporcionalidad tecnológica: el problema es pequeño, la solución "
        "es pequeña. Un HTML de 12 KB bien aislado hace lo que en otros proyectos costaría un módulo "
        "entero con su ViewModel, su base de datos y sus migraciones. Lo único que le falta no es "
        "tecnología: es **que las barras bajen cuando debe**.",
    ],
    TEAL,
    TEAL_SOFT,
)


# =========================================================== 14
h2("14", "Anexos")

h3("A · Inventario de la clave persistida", before=2)
p(
    "`localStorage[\"nahua.axolote.v1\"]` — "
    "`{\"name\":\"Xolito\",\"food\":80,\"mood\":80,\"energy\":80,\"clean\":80,\"xp\":0,\"lastSeen\":1763…}`"
)

h3("B · Superficie del puente consumida")
table(
    ["JS (página)", "JS (SDK)", "Kotlin"],
    [
        ["`Nahua.user()`", "`JSON.parse(NahuaNative.getSession())`", "`MiniAppBridge.getSession()`"],
        ["`Nahua.close()`", "`NahuaNative.close()`", "`MiniAppBridge.close()` → `onClose`"],
    ],
    [36, 72, 68],
)

h3("C · Glosario")
table(
    ["Término", "Definición"],
    [
        ["Bundle", "Conjunto HTML/CSS/JS empaquetado en `assets/`"],
        ["Manifiesto", "Entrada de `index.json` que describe la mini-app"],
        ["Bridge", "Objeto Kotlin expuesto a JavaScript como `NahuaNative`"],
        ["Origen sintético", "Dominio HTTPS que no existe en la red, servido del APK"],
        ["Decaimiento", "Pérdida de puntos por unidad de tiempo"],
        ["Clamp", "Fijar un valor dentro de un rango cerrado"],
    ],
    [40, 136],
)

h3("D · Fuentes consultadas")
table(
    ["Archivo", "Qué se verificó"],
    [
        ["`assets/miniapps/tamagochi/index.html`", "Todo el bundle: 274 líneas"],
        ["`assets/miniapps/nahua-sdk.js`", "API `window.Nahua`, versión"],
        ["`assets/miniapps/index.json`", "Manifiesto `tamagochi`"],
        ["`core/miniapp/MiniAppManifest.kt`", "`entryUrl`, valores por defecto"],
        ["`core/miniapp/MiniAppRegistry.kt`", "Carga y caché del manifiesto"],
        ["`core/miniapp/MiniAppBridge.kt`", "18 métodos nativos"],
        ["`feature/miniapp/MiniAppWebView.kt`", "Política del WebView"],
        ["`feature/miniapp/MiniAppAssetLoader.kt`", "Origen y handlers"],
        ["`core/data/catalog/ServiceCatalog.kt`", "Entrada del hub"],
        ["`app/build.gradle.kts` · `libs.versions.toml`", "SDK, versiones"],
        ["`docs/MINIAPP_SDK.md`", "Permisos declarativos"],
    ],
    [86, 90],
)

h3("E · El bucle que debería decaer (y no decae)")
p(
    "Extracto literal de `index.html` (líneas 145–155 y 262–266). Las dos piezas encajan mal: la "
    "función sólo actúa por encima de **0.02 h** y el temporizador dispara a los **0.0167 h**.",
    size=9.0,
)
code_block([
    "function applyDecay(value) {",
    "  var hours = Math.max(0, (Date.now() - (value.lastSeen || Date.now())) / 3600000);",
    "  var decayed = Object.assign(defaults(), value);",
    "  if (hours > 0.02) {                    // ← umbral: 72 segundos",
    "    Object.keys(DECAY_PER_HOUR).forEach(function (key) {",
    "      decayed[key] = clamp(decayed[key] - DECAY_PER_HOUR[key] * hours);",
    "    });",
    "  }",
    "  decayed.lastSeen = Date.now();         // ← reinicia el reloj en cada tick",
    "  return decayed;",
    "}",
    "",
    "setInterval(function () {",
    "  state = applyDecay(state);             // ← a los 60 s: hours = 0.0167, no entra",
    "  save();",
    "  render();",
    "}, 60000);",
])

h3("F · Cómo reproducir el defecto")
for i, item in enumerate(
    (
        "Abre el axolote y anota el valor de **Comida**.",
        "Déjalo abierto y desbloqueado diez minutos, sin tocar la pantalla. Las cuatro barras no se "
        "mueven ni un punto: el temporizador corre, pero su delta siempre es cero.",
        "Cierra la mini-app, espera **tres minutos** y vuelve a entrar: ahora sí, la comida bajó "
        "alrededor de 0.45 puntos (9 pts/h × 0.05 h).",
    ),
    1,
):
    numbered(i, item, size=9.0)
p(
    "La comprobación es de caja blanca: `hours` en el tick vale 60000 / 3600000 = 0.01667, que nunca "
    "supera 0.02. No hace falta instrumentar nada para verlo.",
    size=9.0,
)

par = DOC.add_paragraph()
par.paragraph_format.space_before = Pt(8)
para_rule(par, "top", sz=4, color=LINE, space=4)
rich(
    par,
    "Informe generado a partir del árbol de trabajo de Nahua 0.16.3, el 19 de noviembre de 2026. "
    "Todas las referencias `archivo:línea` corresponden a esa revisión. Este documento describe el "
    "estado actual; no modifica el proyecto.",
    size=8.0,
    color=MUTED,
)

DOC.core_properties.title = "Axolote · Tecnología actual de la mini-app tamagochi"
DOC.core_properties.subject = "Informe técnico · Nahua 0.16.3"
DOC.core_properties.author = "Nahua · Ingeniería"
DOC.core_properties.keywords = "Android, WebView, mini-app, tamagochi, Kotlin, WebViewAssetLoader"

os.makedirs(os.path.dirname(OUT), exist_ok=True)
DOC.save(OUT)
print("ok", OUT, os.path.getsize(OUT), "bytes")
