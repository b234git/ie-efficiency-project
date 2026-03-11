import openpyxl

file_path = r'c:\Users\mphat\Desktop\work\IE-Eff\info\EFF FEB V6.xlsx'
wb = openpyxl.load_workbook(file_path, data_only=True)
sheet = wb['D']
headers = []
for cell in sheet[4]:
    val = str(cell.value).strip() if cell.value else f'Col_{cell.column_letter}'
    headers.append(f'{cell.column_letter}: {val}')

with open('headers.txt', 'w', encoding='utf-8') as f:
    f.write('\n'.join(headers[:100]))
