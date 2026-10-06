"""기기 선택 화면용 벡터 일러스트(res/drawable/device_*.xml) 생성. 사용: python3 tools/gen_device_art.py app/src/main/res/drawable
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
    b = path(rrect(10, 34, 100, 58, 6), SHADOW)                       # 그림자
    b += path(rrect(8, 30, 100, 58, 6), BODY, EDGE)                    # 가로로 긴 흰 몸체
    b += path(rrect(15, 36, 86, 46, 3), EINK)                          # 전자잉크 화면
    b += path(text("12:30", 58, 41, 13, 24, 3, 4, True), INK)                # 큰 시각
    b += path(text("24.5", 36, 69, 6, 10, 1.5, 2, True), INK)          # 온도
    b += path(text("45", 82, 69, 6, 10, 1.5, 2, True), INK)            # 습도
    return vector("LYWSD02 블루투스 디지털 시계: 가로형 전자잉크, 큰 시각 + 온도/습도", b)

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

out = sys.argv[1]
for name, fn in [("device_lywsd02", lywsd02), ("device_lywsd03mmc", lywsd03),
                 ("device_mho_c303", mho_c303), ("device_mi_scale", scale)]:
    with open(os.path.join(out, name + ".xml"), "w", encoding="utf-8") as f:
        f.write(fn())
print("ok")
