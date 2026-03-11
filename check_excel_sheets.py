import pandas as pd
file_path = r"c:\Users\mphat\Desktop\work\IE-Eff\info\EFF FEB V6.xlsx"
try:
    xl = pd.ExcelFile(file_path)
    print("Sheets:", xl.sheet_names)
except Exception as e:
    print(e)
