import pandas as pd
import json

def get_excel_info(file_path):
    df = pd.read_excel(file_path)
    # convert columns to string list
    cols = list(df.columns)
    # first data row as dict
    row1 = {}
    if not df.empty:
        for k, v in df.iloc[0].items():
            row1[k] = str(v)
    return {"columns": cols, "first_row": row1}

out = {
    "Template": get_excel_info('c:\\Users\\mphat\\Desktop\\work\\IE-Eff\\info\\Production_Template (8).xlsx'),
    "EFF_FEB": get_excel_info('c:\\Users\\mphat\\Desktop\\work\\IE-Eff\\info\\EFF FEB V6.xlsx')
}
with open('c:\\Users\\mphat\\Desktop\\work\\IE-Eff\\info\\excel_info.json', 'w', encoding='utf-8') as f:
    json.dump(out, f, indent=2, ensure_ascii=False)
print("Done")
