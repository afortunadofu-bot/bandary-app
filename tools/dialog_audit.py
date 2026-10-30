# -*- coding: utf-8 -*-
"""
盤點所有 AlertDialog 的 `text = { }`，找出「內容會溢出但沒有捲動」的那些。

## 為什麼這是真問題
`AlertDialog` 的 text 槽有高度上限，內容超過就被裁掉，而且**被裁掉的是下面，
連帶把「確定」按鈕推出畫面**。使用者看得到對話框卻按不到確定，只能按返回取消，
而且完全不知道為什麼。

這在開發機上幾乎看不到，因為開發機螢幕大、字體是預設大小。真正會踩到的是：
小螢幕、系統字體放大（無障礙設定）、橫向、分割畫面。
也就是說，**最需要這個保護的使用者，最不可能是寫程式的人自己**。

## 怎麼用
    python tools/dialog_audit.py

有高風險（risk >= 2）時回傳 exit code 1，可以直接接進 CI。
`DIALOG_AUDIT_OUT=路徑` 可以另外把報告寫成檔案。

## 修法
把 `text = { Column(verticalArrangement = Arrangement.spacedBy(N.dp)) { … } }`
換成 `text = { DialogBody(spacing = N.dp) { … } }`（見 components/DialogBody.kt）。

## 為什麼也看「有沒有用 DialogBody」
自己寫 `Column(verticalScroll(...))` 也能捲，所以這一項不是「會壞」而是「會漏」：
`DialogBody` 另外帶了 55% 螢幕高的上限，而那個上限正是「按不到確定」的解法。
自己捲就拿不到它，而且下一個人會照抄最近的那一處寫法。

**只看 `verticalScroll`，不看 `LazyColumn`。** 用 Lazy 當對話框內容是另一種正當寫法
（長清單 + 自己的 `heightIn`），而且**絕對不能**包進 `DialogBody`——同方向的捲動疊
兩層會壞。把 Lazy 一起列出來的話，等於在請人去製造那個 bug。

## 誤報與例外
- 圖片、圖表、多行輸入框的**固定高度是對的**（那些東西本來就需要指定高度），
  所以 risk 1 的「固定高」只是提醒，不一定要改。
- `rfind` 往前找 AlertDialog 的範圍有限，偶爾會把不相干的 `text =` 算進來
  （例如某個 Card 的參數）。risk 2 以上請人看一眼再改。
"""
import io
import os
import re
import sys
import glob

ROOT = os.path.join(
    os.path.dirname(os.path.dirname(os.path.abspath(__file__))),
    "app", "src", "main", "java",
)
OUT = os.environ.get("DIALOG_AUDIT_OUT")  # 沒設就只印到畫面

# 粗略估「這個區塊有多少東西」用的樣式
WEIGHT = re.compile(
    r'\b(OutlinedTextField|TextField|Switch|Checkbox|RadioButton|Card\(|Surface\(|Image\(|Icon\(|Text\(|Row\(|Column\()'
)
# DialogBody 自己就是可捲動的容器（見 components/DialogBody.kt），
# 偵測器要認得它，否則已經修好的會一直被重複列出來。
SCROLLABLE = re.compile(r'verticalScroll|LazyColumn|LazyRow|LazyVerticalGrid|DialogBody')
FIXED_H = re.compile(r'\.height\((\d+)\.dp\)')


def blocks(src, marker):
    """找出每個 `marker` 後面配對的大括號區塊。"""
    out = []
    for m in re.finditer(re.escape(marker), src):
        i = src.find('{', m.end() - 1)
        if i < 0:
            continue
        depth, j = 0, i
        while j < len(src):
            if src[j] == '{':
                depth += 1
            elif src[j] == '}':
                depth -= 1
                if depth == 0:
                    break
            j += 1
        out.append((m.start(), src[i:j + 1]))
    return out


def main():
    rows = []
    for path in glob.glob(os.path.join(ROOT, '**', '*.kt'), recursive=True):
        src = io.open(path, encoding='utf-8').read()
        if 'AlertDialog(' not in src:
            continue
        name = os.path.basename(path)
        for pos, body in blocks(src, 'text = '):
            # 只看 AlertDialog 的 text 槽。往前找最近的 AlertDialog( 還不夠——
            # 那樣會把「同一個檔案稍早有 AlertDialog、但這個 text= 其實是別的
            # 元件的參數」也算進來（實際誤報過 FriendsScreen 的一個 Card）。
            # 改成檢查那個 AlertDialog( 的括號到這裡還沒收掉，才算真的在它裡面。
            head = src.rfind('AlertDialog(', max(0, pos - 4000), pos)
            if head < 0:
                continue
            between = src[src.find('(', head):pos]
            if between.count('(') - between.count(')') <= 0:
                continue
            line = src.count('\n', 0, pos) + 1
            weight = len(WEIGHT.findall(body))
            scrollable = bool(SCROLLABLE.search(body))
            uses_dialog_body = 'DialogBody' in body
            raw_scroll = 'verticalScroll' in body
            tall = [int(x) for x in FIXED_H.findall(body) if int(x) >= 120]
            risk, why = 0, []
            if not scrollable and weight >= 8:
                risk += 2
                why.append('內容多(%d)但不能捲' % weight)
            elif not scrollable and weight >= 5:
                risk += 1
                why.append('內容偏多(%d)不能捲' % weight)
            if tall:
                risk += 1
                why.append('固定高 %s' % tall)
            # 自己寫 Column(verticalScroll) vs 用共用的 DialogBody。
            # 兩者都能捲，所以這不是「會壞」而是「會漏」：DialogBody 另外帶了
            # 55% 螢幕高的上限，那個上限正是當初「按不到確定」那個回報的解法。
            #
            # **只看 verticalScroll，不看 Lazy 系列**。LazyColumn 當對話框內容是另一種
            # 正當寫法（長清單 + 自己的 heightIn），而且**絕對不能**包進 DialogBody——
            # 同方向的捲動疊兩層會壞。把 Lazy 也列出來的話，等於在請人去製造那個 bug。
            if raw_scroll and not uses_dialog_body:
                risk += 1
                why.append('自己捲動（沒用 DialogBody，拿不到高度上限）')
            if risk:
                rows.append((risk, name, line, '；'.join(why)))

    rows.sort(key=lambda r: (-r[0], r[1], r[2]))
    report = '風險 檔案:行  原因\n' + ''.join('%d  %s:%d  %s\n' % r for r in rows)
    report += '\n共 %d 個需要看\n' % len(rows)
    print(report)
    if OUT:
        io.open(OUT, 'w', encoding='utf-8').write(report)
    # 高風險（內容多到會把確定鍵推出畫面）存在就讓它失敗，好接進 CI。
    return 1 if any(r[0] >= 2 for r in rows) else 0


if __name__ == '__main__':
    sys.exit(main())
