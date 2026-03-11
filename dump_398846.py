import openpyxl

file_path = r'c:\Users\mphat\Desktop\work\IE-Eff\info\EFF FEB V6.xlsx'
wb = openpyxl.load_workbook(file_path, data_only=True)
sheet = wb['D']

print("--- Entries for 398846 ---")
for row in sheet.iter_rows(min_row=3, max_row=5000):
    vals = [str(c.value) for c in row if c.value is not None]
    if any('398846' in v for v in vals):
        date = row[2].value
        sec = row[3].value
        line = row[5].value
        out = row[10].value
        quota = row[67].value if len(row) > 67 else None
        eff_kpi = row[30].value
        print(f"Row {row[0].row} | Date: {date} | Sec: {sec} | Line: {line} | Output(K): {out} | EFF KPI(AE): {eff_kpi} | Quota(BP): {quota}")
