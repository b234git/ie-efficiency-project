import openpyxl
file_path = r'c:\Users\mphat\Desktop\work\IE-Eff\info\EFF FEB V6.xlsx'
wb = openpyxl.load_workbook(file_path, data_only=True)
sheet = wb['D']
found_something = False
for row in sheet.iter_rows(min_row=5, max_row=5000):
    row_vals = [c.value for c in row if c.value is not None]
    row_strs = " ".join(str(v) for v in row_vals)
    if '398846' in row_strs and ('804' in row_strs or '1600' in row_strs):
        print(f"MATCH IN ROW {row[0].row}:")
        for c in row:
            if c.value is not None and (isinstance(c.value, (int, float)) or '398846' in str(c.value)):
                print(f"{c.column_letter}: {c.value}")
        found_something = True

if not found_something:
    print("No matching rows with 398846 AND (804 or 1600) found.")
