import openpyxl
import json

def get_row_data():
    wb = openpyxl.load_workbook('c:\\Users\\mphat\\Desktop\\work\\IE-Eff\\info\\EFF FEB V6.xlsx', data_only=True)
    sheet = wb.active
    
    headers = {}
    for cell in sheet[5]:  # row 5 is header
        headers[cell.column_letter] = str(cell.value)
        
    row6 = {}
    for cell in sheet[6]:
        row6[cell.column_letter] = str(cell.value)
        
    return {"headers": headers, "row6": row6}

out = get_row_data()
with open('c:\\Users\\mphat\\Desktop\\work\\IE-Eff\\info\\eff_row.json', 'w', encoding='utf-8') as f:
    json.dump(out, f, indent=2)
print("Done")
