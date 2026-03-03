import pandas as pd
import json

file_path = r'c:\Users\mphat\Desktop\work\IE-Eff\info\EFF JAN V6.xlsx'
xls = pd.ExcelFile(file_path)

for sheet_name in xls.sheet_names:
    print(f'\n--- Sheet: {sheet_name} ---')
    try:
        df = pd.read_excel(file_path, sheet_name=sheet_name)
        print(f'Shape: {df.shape}')
        print('Columns:', df.columns.tolist()[:20])
        if not df.empty:
            print('First 5 rows:')
            print(df.head(5).to_string())
    except Exception as e:
        print(f'Error reading sheet: {e}')
