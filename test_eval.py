import openpyxl

file_path = r'c:\Users\mphat\Desktop\work\IE-Eff\info\EFF FEB V6.xlsx'
wb = openpyxl.load_workbook(file_path, data_only=True) # GET VALUES, NOT FORMULAS
try:
    sheet = wb['D']
except:
    sheet = wb.active

headers = [str(c.value).strip() if c.value else c.column_letter for c in sheet[4]]

with open('debug_values.txt', 'w', encoding='utf-8') as f:
    for row in sheet.iter_rows(min_row=16, max_row=16):
        for c in row:
            col_idx = c.column - 1
            header = headers[col_idx] if col_idx < len(headers) else c.column_letter
            val = repr(c.value)
            f.write(f"{c.column_letter} [{header}]: {val}\n")
    print("Written to debug_values.txt")
