import openpyxl
wb = openpyxl.load_workbook('c:/Users/mphat/Desktop/work/IE-Eff/info/EFF JAN V6.xlsx', data_only=False)
ws = wb['%']
eff_col = None
for c in range(1, 100):
    val = ws.cell(2, c).value
    if val and 'EFF' == str(val).strip():
        eff_col = c
        print(f"EFF column found at {c}: {val}")
        break

if eff_col:
    print(f"EFF Formula at row 3: {ws.cell(3, eff_col).value}")
    print(f"EFF Formula at row 4: {ws.cell(4, eff_col).value}")
else:
    print("EFF column not found")
