import openpyxl
from openpyxl.utils import get_column_letter

wb = openpyxl.load_workbook(r'c:\Users\mphat\Desktop\work\IE-Eff\info\EFF FEB V6.xlsx', data_only=False)
sheet = wb['DB']

row = sheet[250]
print("Row 250 DB formulas:")
for cell in row:
    if cell.column_letter in ['AH', 'AI', 'AJ', 'AK']:
        print(f"{cell.column_letter}: {cell.value}")
