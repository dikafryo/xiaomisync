"""기기 선택 화면용 벡터 일러스트 생성 — 앱(res/drawable/device_*.xml)과 다운로드 페이지(SVG)가 같은 원본을 쓴다.
사용: python3 tools/gen_device_art.py app/src/main/res/drawable [/var/services/web/sw4u/devide/assets/devices]
  두 번째 인자를 주면 같은 그림을 웹용 SVG 로도 쓴다.
7세그먼트 숫자는 사각형으로 그린다. 기기를 추가하면 함수 하나 + 아래 목록 한 줄을 더한다."""
import sys, os

SEGS = {  # a b c d e f g
    "0": "abcdef", "1": "bc", "2": "abged", "3": "abgcd", "4": "fgbc",
    "5": "afgcd", "6": "afgedc", "7": "abc", "8": "abcdefg", "9": "abcdfg",
}

def r(x, y, w, h):
    return f"M{x:.1f},{y:.1f}h{w:.1f}v{h:.1f}h{-w:.1f}z"

def digit(ch, x, y, w, h, t):
    """(x,y) 왼쪽 위, 폭 w 높이 h, 획 두께 t"""
    half = h / 2
    s = {
        "a": r(x + t, y, w - 2 * t, t),
        "g": r(x + t, y + half - t / 2, w - 2 * t, t),
        "d": r(x + t, y + h - t, w - 2 * t, t),
        "f": r(x, y + t, t, half - t * 1.5),
        "b": r(x + w - t, y + t, t, half - t * 1.5),
        "e": r(x, y + half + t / 2, t, half - t * 1.5),
        "c": r(x + w - t, y + half + t / 2, t, half - t * 1.5),
    }
    return "".join(s[k] for k in SEGS[ch])

def advance(ch, w, t, gap):
    if ch == ":":
        return t + gap * 0.8
    if ch == ".":
        return t + gap * 0.5
    if ch == "1":
        return t + gap
    return w + gap

def text(s, x, y, w, h, t, gap, center=False):
    """center=True 면 x 를 가운데 좌표로 본다"""
    if center:
        x -= (sum(advance(c, w, t, gap) for c in s) - gap) / 2
    out = ""
    for ch in s:
        if ch == ":":
            out += r(x + gap * 0.2, y + h * 0.28, t, t) + r(x + gap * 0.2, y + h * 0.62, t, t)
        elif ch == ".":
            out += r(x, y + h - t, t, t)
        elif ch == "1":
            out += digit(ch, x - (w - t), y, w, h, t)   # 오른쪽 획만 있으므로 좁게
        else:
            out += digit(ch, x, y, w, h, t)
        x += advance(ch, w, t, gap)
    return out

def rrect(x, y, w, h, rad):
    return (f"M{x+rad},{y}h{w-2*rad}a{rad},{rad} 0,0 1,{rad},{rad}v{h-2*rad}"
            f"a{rad},{rad} 0,0 1,{-rad},{rad}h{-(w-2*rad)}a{rad},{rad} 0,0 1,{-rad},{-rad}"
            f"v{-(h-2*rad)}a{rad},{rad} 0,0 1,{rad},{-rad}z")

def path(d, fill, stroke=None, sw=2):
    st = f'\n        android:strokeColor="{stroke}"\n        android:strokeWidth="{sw}"' if stroke else ""
    return f'    <path\n        android:fillColor="{fill}"{st}\n        android:pathData="{d}" />\n'

def vector(comment, body):
    return ('<?xml version="1.0" encoding="utf-8"?>\n'
            f'<!-- {comment} (기기 선택 화면용 일러스트, 실제 제품 사진 아님. 생성: 직접 그린 벡터) -->\n'
            '<vector xmlns:android="http://schemas.android.com/apk/res/android"\n'
            '    android:width="120dp"\n    android:height="120dp"\n'
            '    android:viewportWidth="120"\n    android:viewportHeight="120">\n'
            + body + '</vector>\n')

BODY, EDGE, INK, EINK, SHADOW = "#FFFFFF", "#C3C9D1", "#2B3036", "#E4E7E1", "#E3E6EA"

def lywsd02():
    # 실물 모습: 얇은 흰 테두리의 2:1 가로형 전자잉크, 큰 시각 + 아래 줄 습도%·온도°C·표정 (^_^)
    none = "#00000000"
    b = path(rrect(7, 36, 108, 54, 7), SHADOW)                         # 그림자
    b += path(rrect(6, 33, 108, 54, 7), "#FBFBFB", "#D3D8DE", 1.5)      # 얇은 흰 테두리 몸체
    b += path(rrect(11, 38, 98, 44, 2), EINK)                          # 전자잉크 화면
    b += path(text("12:30", 60, 42, 15, 25, 2.2, 4.5, True), INK)      # 큰 시각 (가는 획)
    b += path(text("36", 27, 72, 4.5, 7, 1.1, 1.5, True), INK)         # 습도
    b += path("M33,73.2a0.9,0.9 0,1 0,0.01 0zM36,78.2a0.9,0.9 0,1 0,0.01 0zM32.6,79.2l4,-6.6", INK, INK, 0.6)  # %
    b += path(text("23.5", 59, 72, 4.5, 7, 1.1, 1.5, True), INK)       # 온도
    b += path("M71,72.6a1,1 0,1 0,0.01 0z", none, INK, 0.6)            # °
    b += path("M77,73.2a3,3 0,1 0,0 5.6", none, INK, 1.1)              # C
    b += path("M85,71.6q-1.8,3.4 0,6.8M100,71.6q1.8,3.4 0,6.8"         # ( )
              "M87,75l1.6,-2.2l1.6,2.2M95,75l1.6,-2.2l1.6,2.2M90.6,77.6h3.8",  # ^ ^ _
              none, INK, 1)
    return vector("LYWSD02 블루투스 디지털 시계: 얇은 흰 테두리 가로형 전자잉크, 큰 시각 + 습도·온도·표정", b)

def lywsdcgq():
    # 실물 모습: 지름 약 6.5cm 둥근 흰 몸체, 위쪽 큰 온도 + 아래 작은 습도 LCD, 뒤 받침
    none = "#00000000"
    b = path("M62,21a42,42 0,1 0,0.01 0z", SHADOW)                        # 그림자 (오른쪽 아래로 살짝)
    b += path("M60,18a42,42 0,1 0,0.01 0z", "#FBFBFB", "#D3D8DE", 1.5)   # 둥근 몸체
    b += path("M60,27a33,33 0,1 0,0.01 0z", EINK)                         # 둥근 LCD
    b += path(text("23.5", 58, 40, 10, 19, 2.2, 3, True), INK)           # 온도
    b += path("M81.5,41.5a1.4,1.4 0,1 0,0.01 0z", none, INK, 0.9)          # °
    b += path(text("45", 55, 67, 6, 11, 1.6, 2.2, True), INK)            # 습도
    b += path("M67,67.6a0.8,0.8 0,1 0,0.01 0zM70,76.4a0.8,0.8 0,1 0,0.01 0zM66.6,77.6l3.6,-9.4", INK, INK, 0.5)  # %
    return vector("LYWSDCGQ/01ZM 블루투스 온습도계: 둥근 흰 몸체 LCD, 큰 온도 + 작은 습도", b)

def lywsd03():
    b = path(rrect(29, 29, 66, 66, 10), SHADOW)
    b += path(rrect(26, 25, 66, 66, 10), BODY, EDGE)                   # 작은 정사각 몸체
    b += path(rrect(33, 32, 52, 52, 5), "#DDE3DA")                     # LCD
    b += path(text("24.5", 59, 37, 9, 17, 2.4, 3, True), INK)                # 온도
    b += path(text("45", 70, 61, 6, 11, 1.8, 2.5, True), INK)                # 습도
    b += path("M40,68a5,5 0,1 0,10 0a5,5 0,1 0,-10 0zM42.5,66.5h1.4v1.4h-1.4zM46,66.5h1.4v1.4h-1.4zM42.2,69.5q2.8,2.6 5.6,0l0.6,0.6q-3.4,3.2 -6.8,0z", INK)  # 웃는 얼굴
    return vector("LYWSD03MMC 블루투스 온습도계 2: 작은 정사각 LCD, 온도·습도·표정", b)

def mho_c303():
    b = path("M44,92h32l6,10h-44z", EDGE)                              # 받침대
    b += path(rrect(19, 26, 86, 64, 5), SHADOW)
    b += path(rrect(17, 22, 86, 64, 5), BODY, EDGE)
    b += path(rrect(23, 28, 74, 52, 2), EINK)
    b += path("M23,28h74v9h-74z", "#CFD3CC")                           # 날짜 줄
    b += path(text("10.06", 60, 29.5, 4.5, 7, 1.1, 1.6, True), INK)
    b += path(text("09:41", 60, 41, 11, 21, 2.6, 3.4, True), INK)
    b += path(text("23", 40, 67, 6, 10, 1.5, 2, True), INK)
    b += path(text("52", 80, 67, 6, 10, 1.5, 2, True), INK)
    return vector("MHO-C303 전자잉크 시계: 받침대 있는 가로형, 날짜 줄 + 시각", b)

def scale():
    b = path(rrect(16, 18, 92, 92, 14), SHADOW)
    b += path(rrect(13, 14, 92, 92, 14), BODY, EDGE)                   # 위에서 본 유리 발판
    b += path(rrect(43, 26, 32, 14, 3), "#3A4048")                     # 숫자 표시창
    b += path(text("65.0", 59, 29, 5, 8, 1.2, 1.6, True), "#FFFFFF")
    b += path("M30,58c3,-4 9,-4 11,2c1,6 -1,20 -6,22c-5,1 -8,-10 -5,-24z", SHADOW)   # 왼발 자리
    b += path("M88,58c-3,-4 -9,-4 -11,2c-1,6 1,20 6,22c5,1 8,-10 5,-24z", SHADOW)    # 오른발 자리
    return vector("XMTZC 체중계: 위에서 본 정사각 발판 + 숫자 표시창", b)

def to_svg(android_xml):
    """안드로이드 vector XML(이 파일이 만든 것) → 같은 모양의 SVG"""
    import re
    comment = re.search(r"<!-- (.*?) -->", android_xml).group(1)
    body = ""
    for m in re.finditer(r"<path(.*?)/>", android_xml, re.S):
        a = dict(re.findall(r'android:(\w+)="([^"]*)"', m.group(1)))
        stroke = f' stroke="{a["strokeColor"]}" stroke-width="{a["strokeWidth"]}"' if "strokeColor" in a else ""
        fill = "none" if a["fillColor"] == "#00000000" else a["fillColor"]   # 선만 있는 획
        body += f'<path fill="{fill}"{stroke} d="{a["pathData"]}"/>'
    return (f'<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 120 120" width="120" height="120">'
            f'<!-- {comment} --><title>{comment.split(":")[0]}</title>{body}</svg>\n')


android_dir = sys.argv[1]
svg_dir = sys.argv[2] if len(sys.argv) > 2 else None
for name, fn in [("device_lywsd02", lywsd02), ("device_lywsdcgq", lywsdcgq), ("device_lywsd03mmc", lywsd03),
                 ("device_mho_c303", mho_c303), ("device_mi_scale", scale)]:
    xml = fn()
    with open(os.path.join(android_dir, name + ".xml"), "w", encoding="utf-8") as f:
        f.write(xml)
    if svg_dir:
        with open(os.path.join(svg_dir, name.removeprefix("device_") + ".svg"), "w", encoding="utf-8") as f:
            f.write(to_svg(xml))
print("ok")
