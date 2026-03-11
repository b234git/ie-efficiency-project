import openpyxl

file_path = r'c:\Users\mphat\Desktop\work\IE-Eff\info\EFF FEB V6.xlsx'
print(f"Loading {file_path}...")
wb = openpyxl.load_workbook(file_path, data_only=False)
sheet = wb.active

# Assuming Headers are on Row 4
headers = []
for cell in sheet[4]:
    headers.append(str(cell.value).strip() if cell.value else f"Col_{cell.column_letter}")

target_article = '398846'
found = False

for row in sheet.iter_rows(min_row=5):
    for cell in row:
        if cell.value and target_article in str(cell.value):
            found = True
            print(f"--- FOUND ARTICLE {target_article} at ROW {cell.row} ---")
            for c in row:
                col_idx = c.column - 1
                header = headers[col_idx] if col_idx < len(headers) else f"Col_{c.column_letter}"
                if c.value is not None:
                    print(f"{c.column_letter} [{header}]: {c.value}")
            break
    if found:
        break

if not found:
    print(f"Article {target_article} not found in first few hundred rows.")
