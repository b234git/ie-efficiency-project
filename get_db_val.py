import openpyxl

wb = openpyxl.load_workbook(r'c:\Users\mphat\Desktop\work\IE-Eff\info\EFF FEB V6.xlsx', data_only=True)
sheet = wb['DB']

print("--- DB row for 398846 ---")
for row in sheet.iter_rows(min_row=3, max_row=5000):
    vals = [str(c.value) for c in row if c.value is not None]
    if any('398846' in v for v in vals):
        print(f"Row {row[0].row} | Article (B): {row[1].value} | AH (CT): {row[33].value} | AK (PPH): {row[36].value}")
