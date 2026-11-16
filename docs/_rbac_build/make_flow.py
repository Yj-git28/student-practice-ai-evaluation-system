# -*- coding: utf-8 -*-
"""流程图（短标签、大字号，插入 Word 后仍清晰）。"""
from PIL import Image, ImageDraw, ImageFont

W, H = 1600, 690
BG = (255, 255, 255)
BOX = (241, 243, 246)
BOX_EDGE = (150, 156, 165)
ACCENT = (253, 236, 236)
ACCENT_EDGE = (190, 98, 98)
TEXT = (32, 36, 42)
LABC = (28, 58, 108)
MUTED = (108, 114, 124)
LINE = (118, 124, 134)

FONT = r"C:\Windows\Fonts\msyh.ttc"
FONTB = r"C:\Windows\Fonts\msyhbd.ttc"
f_sec = ImageFont.truetype(FONTB, 26)
f_lab = ImageFont.truetype(FONTB, 20)
f_box = ImageFont.truetype(FONT, 21)
f_note = ImageFont.truetype(FONT, 18)

img = Image.new("RGB", (W, H), BG)
d = ImageDraw.Draw(img)


def box(x, y, w, h, label, lines, fill=BOX, edge=BOX_EDGE, lab_color=(28, 58, 108)):
    d.rounded_rectangle([x, y, x + w, y + h], radius=9, fill=fill, outline=edge, width=2)
    d.text((x + 12, y + 9), label, font=f_lab, fill=lab_color)
    for i, ln in enumerate(lines):
        d.text((x + 12, y + 40 + i * 25), ln, font=f_box, fill=TEXT)





def arrow(x1, y1, x2, y2, dashed=False):
    if dashed:
        n = 12
        for k in range(n):
            if k % 2 == 0:
                sx = x1 + (x2 - x1) * k / n
                sy = y1 + (y2 - y1) * k / n
                ex = x1 + (x2 - x1) * (k + 1) / n
                ey = y1 + (y2 - y1) * (k + 1) / n
                d.line([sx, sy, ex, ey], fill=LINE, width=2)
    else:
        d.line([x1, y1, x2, y2], fill=LINE, width=2)
    if abs(y2 - y1) >= abs(x2 - x1):
        if y2 > y1:
            d.polygon([(x2, y2), (x2 - 7, y2 - 12), (x2 + 7, y2 - 12)], fill=LINE)
        else:
            d.polygon([(x2, y2), (x2 - 7, y2 + 12), (x2 + 7, y2 + 12)], fill=LINE)
    else:
        if x2 > x1:
            d.polygon([(x2, y2), (x2 - 12, y2 - 7), (x2 - 12, y2 + 7)], fill=LINE)
        else:
            d.polygon([(x2, y2), (x2 + 12, y2 - 7), (x2 + 12, y2 + 7)], fill=LINE)


BW, BH, GAP = 236, 132, 18
X0 = 47

# 第一行：登录
d.text((X0, 26), "一　登录：一次换凭证", font=f_sec, fill=TEXT)
Y1 = 72
row1 = [
    ("1 提交", ["账号 + 密码"]),
    ("2 校验", ["状态启用", "密码匹配"]),
    ("3 取角色", ["sys_user.role_id", "→ sys_role"]),
    ("4 取权限", ["role_permission", "→ perm_code 集"]),
    ("5 缓存与签发", ["Redis 存权限", "JWT 只放 userId"]),
    ("6 返回", ["token", "+ permCodes"]),
]
for i, (lab, lines) in enumerate(row1):
    x = X0 + i * (BW + GAP)
    box(x, Y1, BW, BH, lab, lines)
    if i < len(row1) - 1:
        arrow(x + BW + 3, Y1 + BH // 2, x + BW + GAP - 4, Y1 + BH // 2)

# 第二行：每次请求
d.text((X0, 262), "二　每次请求：只验令牌，再查权限集合", font=f_sec, fill=TEXT)
Y2 = 308
row2 = [
    ("7 取凭证", ["请求头 Bearer", "解析得 userId"]),
    ("8 取权限", ["Redis 读集合", "未命中回源"]),
    ("9 读注解", ["@RequirePermission", "上的 perm_code"]),
    ("10 判定", ["命中→放行", "否则→403"]),
]
for i, (lab, lines) in enumerate(row2):
    x = X0 + i * (BW + GAP)
    box(x, Y2, BW, BH, lab, lines)
    if i < len(row2) - 1:
        arrow(x + BW + 3, Y2 + BH // 2, x + BW + GAP - 4, Y2 + BH // 2)

BY = Y2 + BH + 40
box(X0, BY, BW, 84, "→ 401 未认证", ["令牌缺失、过期"], fill=ACCENT, edge=ACCENT_EDGE, lab_color=ACCENT_EDGE)
arrow(X0 + BW // 2, Y2 + BH + 2, X0 + BW // 2, BY - 5)

x10 = X0 + 3 * (BW + GAP)
box(x10, BY, BW + 40, 84, "→ 403 无权限", ["该码不在集合内"], fill=ACCENT, edge=ACCENT_EDGE, lab_color=ACCENT_EDGE)
arrow(x10 + BW // 2, Y2 + BH + 2, x10 + BW // 2, BY - 5)

# 两行衔接
cx = X0 + 5 * (BW + GAP) + BW // 2
arrow(cx, Y1 + BH + 12, cx, Y2 - 18, dashed=True)
d.text((cx - 300, Y1 + BH + 44), "令牌存前端，后续请求自动携带", font=f_note, fill=MUTED)

d.text((X0, BY + 108), "说明：perm_code 即 sys_permission.perm_code（教程常写作 perm_key）；权限不进令牌。",
       font=f_note, fill=MUTED)

img.save(r"D:\app\Group10SE\student-practice-ai-evaluation-system\docs\_rbac_build\flow_login_auth.png", dpi=(240, 240))
print("saved")
