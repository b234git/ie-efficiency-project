import pandas as pd

file_path = r'c:\Users\mphat\Desktop\work\IE-Eff\info\EFF JAN V6.xlsx'
out_path = r'c:\Users\mphat\Desktop\work\IE-Eff\info\excel_context.md'

xls = pd.ExcelFile(file_path)

with open(out_path, 'w', encoding='utf-8') as f:
    f.write(f'# Excel Context: {file_path}\n\n')
    for sheet_name in xls.sheet_names:
        f.write(f'## Sheet: {sheet_name}\n')
        try:
            df = pd.read_excel(file_path, sheet_name=sheet_name)
            f.write(f'**Shape**: {df.shape}\n\n')
            if not df.empty:
                f.write(df.to_markdown(index=False))
            f.write('\n\n')
        except Exception as e:
            f.write(f'**Error reading sheet**: {e}\n\n')
