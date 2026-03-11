import openpyxl
import json

def read_eff_file():
    wb = openpyxl.load_workbook('c:\\Users\\mphat\\Desktop\\work\\IE-Eff\\info\\EFF FEB V6.xlsx', data_only=False)
    sheet = wb.active # default to active sheet
    
    # We want to find the row/col where EFF is calculated and get the formula
    formulas = []
    for row in sheet.iter_rows(min_row=1, max_row=20, min_col=1, max_col=20):
        for cell in row:
            if cell.value and isinstance(cell.value, str):
                if cell.value.startswith('='):
                    formulas.append(f"{cell.coordinate}: {cell.value}")
                elif "EFF" in cell.value.upper() or "TARGET" in cell.value.upper():
                    formulas.append(f"{cell.coordinate} (HEADER): {cell.value}")

    return {"sheet_name": sheet.title, "formulas": formulas}

out = read_eff_file()
with open('c:\\Users\\mphat\\Desktop\\work\\IE-Eff\\info\\eff_formulas.json', 'w', encoding='utf-8') as f:
    json.dump(out, f, indent=2)
print("Done")
