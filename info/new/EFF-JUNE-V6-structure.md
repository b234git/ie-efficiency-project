# EFF JUNE V6 — Structure & Context Map

File: `info/new/Copy of EFF JUNE V6.xlsx` (12 MB, 8 sheets).
**Format = identical to EFF MAY V6** (drop-in compatible with the existing importer).
Data period: **June 2026** (D sheet spans 2026-06-01 → 2026-06-30, 30 days, 1166 line-day rows).

## Sheets

| Sheet | Rows | Cols | Role |
|-------|------|------|------|
| `6S`  | 77 | 17 | 6S score + reproduce/reprocess input per SEW/section/line |
| `NS`  | 39 | 10 | New-style quantity lookup (STYLE×QUANTITY → incentive tier) |
| `S`   | 76 | 24 | Incentive/salary summary per line (Quota/MP/WT/EFF + A/B/C/LL tiers) |
| `D`   | 3829 | 85 | **Master daily grid** — one row per line-day, the import source |
| `%`   | 64 | 18 | Per-line daily dashboard (Output/DL/WT/EFF/RFT/PPH) |
| `DB`  | 1990 | 41 | **Article master** — 443 populated articles × 8 section blocks |
| `R`   | 112 | 19 | Incentive **rate lookup** (section × worker-count × EFF% → rate) |
| `Sheet1` | 30 | 8 | Per-line EFF/RFT/6S scratch summary |

## D sheet (import source) — column map

Header in row 1, time-slot sub-labels in row 2; **data starts row 3**.
Layout matches MAY V6's **shifted** block (Section=3, Line=4, Subline=5 — the
"Line" label sits over col 4 but Section data is in col 3; resolved by content,
already handled by `HeaderResolver`).

| Col | Field | Col | Field |
|----:|-------|----:|-------|
| 0 | REF 1 (`46174SEW1A`) | 28 | ARTICLE (final, authoritative) |
| 1 | REF 2 (`SEW1A`) | 29 | Allowance |
| 2 | Date | 30 | KPI / EFF KPI |
| 3 | **Section** (`SEW`/`ASSY`/`SF`/`BUFF`) | 31 | EFF Salary |
| 4 | **Line** | 32 | PPH |
| 5 | Subline (A/B/…) | 33 | PT |
| 7 | DL | 34 | MH |
| 8 | DLI | 35–49 | Cycle Time per slot |
| 9 | IDL | 50 | TCT |
| 10 | Output | 51–65 | Quota per slot |
| 11 | WT | 66 | Total Quota |
| 12 | RFT | 67–81 | MP per slot |
| 13–27 | **15 time slots** 07:00→22:00 (article per slot) | 82 | Total MP |
| 13 | Article (also slot-1 header) | 83 | REF TIME |
|   |   | 84 | FQ |

Section row counts in D: SEW 390, BUFF 326, ASSY 240, SF 210.

## DB sheet (article master) — 8 section blocks, 4 fields each

Row 1 = section names (merged); row 2 = sub-headers (CT/MP/QUOTA/PPH); data row 3+.

| Cols | Section block |
|------|---------------|
| 0–4 | REF, Article No., Pattern No., Shoe Name, OS Code |
| 5–8 | SEW (CT/MP/QUOTA/PPH) |
| 9–12 | BUFFING 1ST |
| 13–16 | BUFFING 2ND |
| 17–20 | STOCKFIT UV |
| 21–24 | STOCKFIT 1ST |
| 25–28 | STOCKFIT 2ND |
| 29–32 | ASSEMBLY BIG |
| 33–36 | ASSEMBLY SMALL |

Empty section blocks carry `#DIV/0!` formula errors (e.g. STOCKFIT cols 16/20/24/28) — the importer must tolerate these.

## R sheet (rate lookup)

- Cols 0–11: key (`SEW8`, `ASSY10`, …) = Section+WorkerCount, then EFF-tier multipliers for worker counts 1–12.
- Cols 14–18: secondary `ASSY10` block — SEC / WT / % (EFF) / Rate (VND).

## S / 6S / NS summary sheets

- **S** new-style incentive thresholds (row 3): `35600 / 39300 / 43000 / 51500 / 53400 / 55800`; tier columns EFF / A / B / C / LL1 / LL2 / LL3; date rows from 2026-06-01.
- **6S** sections tracked: CP, SEW, BUFF, SF, ASSY, FIRST CUT (C), INYE, HEATSEAL, PRINTING, FIRST CUT (P), EMBROIDERY, SAMPLE, SL, WHSE, MECHANIC, OTHER, HUASEN, COMELZ, EMMA, LAZER, LUXIN.
- **NS** maps STYLE count → QUANTITY (1→30000, 2→60000, 3→90000, …).

## Import implication

No code change needed — JUNE V6 is byte-compatible with the MAY V6 import path
(`HeaderResolver` FACTORY_XLSX, content-based Section detection, slot run 13–27,
ARTICLE@28, helper-grid slot guard). Just import the `D` sheet for June.
