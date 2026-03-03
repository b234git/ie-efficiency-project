import openpyxl

wb = openpyxl.load_workbook('c:/Users/mphat/Desktop/work/IE-Eff/info/EFF JAN V6.xlsx', data_only=True)
ws = wb['D']

cols = {'H': 8, 'K': 11, 'L': 12, 'AD': 30, 'AY': 51}

for letter, idx in cols.items():
    print(f"Header {letter}: {ws.cell(2, idx).value}")
